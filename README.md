# Lift

Lift is a small Android media loudness utility. Pick a target in the app, then tap **Media boost** in Quick Settings to turn the saved target on or off. Touch and hold the tile to open Lift. On Android 13 or later, the app can ask Android to add the tile. On older versions, add it from Quick Settings **Edit**.

Turn the circular bezel to set a target from 100% to 400% in 5% steps, or tap one of the five presets. The bezel gives stronger haptic ticks as gain increases on devices that support amplitude control. Targets describe source signal amplitude. Actual loudness depends on the media, Android audio routing, and the output hardware. Android may compress peaks, restrict access to the output mix, or route playback around software audio effects. Lift shows when it cannot enable the effect.

Lift runs on Android 10 or later. The app checks its public GitHub releases when opened and shows an update banner when a newer version is available. It verifies the download hash, package name, version, and signing certificate before opening Android’s installer. Android can ask to allow installs from Lift and confirm the update.

Lift uses the network for GitHub updates. Gain and update preferences stay on the device. Lift has no accounts, analytics, microphone, or accessibility service. When boost is active, the foreground notification includes a stop action.

## Build

Use JDK 17 or later and install Android SDK 37. Run `./gradlew :app:assembleDebug` for a test APK. For distribution, use `./gradlew :app:assembleRelease` with `LIFT_KEYSTORE`, `LIFT_STORE_PASSWORD`, `LIFT_KEY_ALIAS`, and `LIFT_KEY_PASSWORD` set to your release signing configuration.
