package app.lift.audio;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LiftUpdate {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String REPOSITORY = "alishaban144/lift-android";
    private static final String LATEST = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final String ASSET = "app-release.apk";
    private static final int MAX_APK_BYTES = 40 * 1024 * 1024;
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    static final class Release {
        final String version;
        final String url;
        final long size;
        final String digest;

        Release(String version, String url, long size, String digest) {
            this.version = version;
            this.url = url;
            this.size = size;
            this.digest = digest;
        }
    }

    static Release check(Context context, boolean force) throws Exception {
        var prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE);
        String releaseJson = prefs.getString("release", "");
        long age = System.currentTimeMillis() - prefs.getLong("checkedAt", 0);
        if (force || age < 0 || age >= CHECK_INTERVAL_MS || releaseJson.isEmpty()) {
            HttpURLConnection connection = connect(LATEST, "application/vnd.github+json");
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (body.size() + count > 256 * 1024) throw new IOException("Release metadata is too large");
                    body.write(buffer, 0, count);
                }
                releaseJson = new String(body.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                connection.disconnect();
            }
        }

        JSONObject json = new JSONObject(releaseJson);
        Release release = parseRelease(json, installed(context).versionName);
        if (force || age < 0 || age >= CHECK_INTERVAL_MS || prefs.getString("release", "").isEmpty()) {
            prefs.edit().putString("release", releaseJson).putLong("checkedAt", System.currentTimeMillis()).apply();
        }
        return release;
    }

    private static Release parseRelease(JSONObject release, String current) throws Exception {
        String tag = release.optString("tag_name");
        if (release.optBoolean("draft") || release.optBoolean("prerelease")
            || !tag.matches("v[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) return null;
        String version = tag.substring(1);
        if (!newerVersion(version, current)) return null;

        JSONArray assets = release.getJSONArray("assets");
        for (int index = 0; index < assets.length(); index++) {
            JSONObject apk = assets.getJSONObject(index);
            if (!ASSET.equals(apk.optString("name"))) continue;
            String expectedUrl = "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + ASSET;
            long size = apk.optLong("size");
            String digest = apk.optString("digest").toLowerCase(Locale.ROOT);
            if (!expectedUrl.equals(apk.optString("browser_download_url")) || size <= 0 || size > MAX_APK_BYTES
                || !digest.matches("sha256:[0-9a-f]{64}")) throw new IOException("Invalid release asset metadata");
            return new Release(version, expectedUrl, size, digest.substring(7));
        }
        return null;
    }

    private static boolean newerVersion(String candidate, String current) {
        if (current == null || !current.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) return false;
        String[] a = candidate.split("\\.");
        String[] b = current.split("\\.");
        for (int part = 0; part < 3; part++) {
            int comparison = Integer.compare(Integer.parseInt(a[part]), Integer.parseInt(b[part]));
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    static void download(Context context, Release release) throws Exception {
        File pending = File.createTempFile("lift-update-", ".download", context.getCacheDir());
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            HttpURLConnection connection = connect(release.url, "application/octet-stream");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(pending)) {
                byte[] buffer = new byte[16384];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > release.size) throw new IOException("APK larger than expected");
                    sha256.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                if (total != release.size || !release.digest.equals(hex(sha256.digest()))) {
                    throw new IOException("APK integrity check failed");
                }
            } finally {
                connection.disconnect();
            }
            verifyPackage(context, pending, release.version);
            Files.move(pending.toPath(), apk(context).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            pending.delete();
        }
    }

    private static void verifyPackage(Context context, File file, String version) throws Exception {
        PackageInfo current = installed(context);
        PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(
            file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !current.packageName.equals(candidate.packageName)
            || candidate.getLongVersionCode() <= current.getLongVersionCode()
            || !version.equals(candidate.versionName) || current.signingInfo == null || candidate.signingInfo == null) {
            throw new IOException("APK identity check failed");
        }
        Signature[] expected = current.signingInfo.getApkContentsSigners();
        Signature[] found = candidate.signingInfo.getApkContentsSigners();
        if (expected.length == 0 || expected.length != found.length
            || !Arrays.asList(expected).containsAll(Arrays.asList(found))) {
            throw new IOException("APK signing certificate changed");
        }
    }

    private static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    }

    static File apk(Context context) { return new File(context.getCacheDir(), "verified-lift-update.apk"); }

    static Uri apkUri(Context context) {
        return Uri.parse("content://" + context.getPackageName() + ".update/verified-lift-update.apk");
    }

    private static HttpURLConnection connect(String address, String accept) throws IOException {
        URL url = new URL(address);
        for (int redirect = 0; redirect <= 4; redirect++) {
            if (!"https".equals(url.getProtocol()) || !(url.getPort() == -1 || url.getPort() == 443)
                || !allowedHost(url.getHost())) throw new IOException("Unexpected update destination");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("User-Agent", "Lift-Android");
            int code = connection.getResponseCode();
            if (code == 200) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (code < 300 || code > 399 || location == null) throw new IOException("GitHub responded with HTTP " + code);
            url = new URL(url, location);
        }
        throw new IOException("Too many GitHub redirects");
    }

    private static boolean allowedHost(String host) {
        return host.equals("api.github.com") || host.equals("github.com")
            || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")
            || host.equals("github-releases.githubusercontent.com");
    }

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(Character.forDigit((value & 0xff) >>> 4, 16));
            output.append(Character.forDigit(value & 0xf, 16));
        }
        return output.toString();
    }
}
