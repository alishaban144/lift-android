package app.lift.audio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;

public final class UpdateFileProvider extends ContentProvider {
    static final String APK_MIME = "application/vnd.android.package-archive";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return LiftUpdate.apkUri(getContext()).equals(uri) ? APK_MIME : null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (!"r".equals(mode) || !LiftUpdate.apkUri(context).equals(uri)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(LiftUpdate.apk(context), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context context = getContext();
        if (!LiftUpdate.apkUri(context).equals(uri)) return null;
        String[] columns = projection == null
            ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("Lift.apk");
            else if (OpenableColumns.SIZE.equals(column)) row.add(LiftUpdate.apk(context).length());
            else row.add(null);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
