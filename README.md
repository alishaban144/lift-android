# Lift

Lift is a small Android media loudness utility. Pick a target in the app, then tap **Media boost** in Quick Settings to turn the saved target on or off. Touch and hold the tile to open Lift. On Android 13 or later, the app can ask Android to add the tile. On older versions, add it from Quick Settings **Edit**.

Available targets are 125%, 150%, 200%, 300%, and 400% of the source signal amplitude. Actual loudness depends on the media, Android audio routing, and the output hardware. Android may compress peaks, restrict access to the output mix, or route playback around software audio effects. Lift shows when it cannot enable the effect.

Lift runs on Android 10 or later. It uses no internet, account, analytics, microphone, or accessibility service. It keeps only the gain and notification prompt preference locally. When boost is active, the foreground notification includes a stop action.

## Build

Use JDK 17 or later and install Android SDK 37. Run `./gradlew :app:assembleDebug` for a test APK. For distribution, use `./gradlew :app:assembleRelease` with `LIFT_KEYSTORE`, `LIFT_STORE_PASSWORD`, `LIFT_KEY_ALIAS`, and `LIFT_KEY_PASSWORD` set to your release signing configuration.
