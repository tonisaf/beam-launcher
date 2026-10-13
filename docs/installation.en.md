# Setting up Beam

[Русская версия](installation.md) · [Features and compatibility](../README.en.md)

Install a compatible APK and select Beam as Home in Android settings. For ADB installation: `adb install -r app-release.apk`. On first launch choose language, layout and theme; these remain available in Beam settings.

## Home controls

Hold OK on an app (or use the remote menu button) to open its menu. Move changes order directly on Home: arrows move, OK saves, Back cancels. Favorites stay at the front. Configure individual content rows above or below apps in Beam settings.

HDMI tiles require a connected source reported by Android and a stock input player. Show HDMI controls their visibility. USB tiles appear for mounted removable drives when USB tile is enabled; Automatic mode tries system root browsing, a file-category app, then the XGIMI file manager as a compatibility fallback. Settings → USB app lets you choose any installed launcher app, including file managers without a standard category. The choice persists; if the app is removed, automatic mode resumes. Launching an app may require selecting the drive within its own UI.

## Updating Beam

Beam Settings → Beam update → Check for updates → Download update → Install. Confirm in Android’s installer. Automatic checks run at most daily when Home opens; updates are not installed silently. Settings are preserved with the same package and signing key.

## Optional features

Custom image: Beam Settings → Choose image. The system document picker normally opens, including available USB drives and providers. Firmware without a picker uses the Android gallery; Beam requests photo access when needed. The gallery shows the latest 300 MediaStore images (with thumbnails on Android 10+). If an image is missing, save it in Pictures or Download and wait for Android media indexing.

Selecting an image opens a preview behind real Home icons and content cards. Use left/right arrows to adjust dimming from 0 to 80%, then choose Apply. Cancel and Back preserve the previous image and dimming. Reopen Background preview for the saved image, or adjust Dimming in settings. The gallery displays a three-column image grid.

Images up to 32 MB are copied into Beam storage and downsampled to at most 2048 pixels on the longest side. The static background crops to fill the screen. Both the image and dimming survive restarts. Restore preset background or select a color preset to disable the image. XMB and a custom image are mutually exclusive.

The launcher runs without granting every optional permission. The separate “Beam: missing setup” dialog lists missing permissions on your device. Grant only those needed for the features you use:

| Permission / access | Purpose |
| --- | --- |
| READ_TV_LISTINGS | Read installed apps’ TV channels |
| Notification access for MediaListener | Now Playing and available media-session controls |
| GET_USAGE_STATS | Sort by recent usage when no manual order is set |
| Location | Wi-Fi network name; both permissions are requested on modern Android |

Commands from Beam’s setup implementation (run on a computer with ADB connected to the device):

```sh
adb shell pm grant com.home.tiles android.permission.READ_TV_LISTINGS
adb shell cmd notification allow_listener com.home.tiles/com.home.tiles.MediaListener
adb shell appops set com.home.tiles GET_USAGE_STATS allow
adb shell pm grant com.home.tiles android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.home.tiles android.permission.ACCESS_FINE_LOCATION
```

Permissions cannot provide missing services: content rows need a TV provider and entries published by apps. Notification access may also be enabled in system settings.

## Home app

Use the system Home-app selector when available. Another option:

```sh
adb shell cmd package set-home-activity com.home.tiles/.MainActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Check both the resolved HOME activity and an actual HOME press. Firmware may restrict replacement. Generic installation does not require disabling stock apps, translating firmware or installing remote-button helpers.

`tools/restore.ps1` targets the former XGIMI Play 6 setup and changes system settings and services. Do not use it as an installer for arbitrary devices.

## Update installation permission

If the firmware has no standard unknown-source settings screen, allow APK installation from Beam once via ADB. Android still asks you to confirm each installation.

```sh
adb shell appops set com.home.tiles REQUEST_INSTALL_PACKAGES allow
```

## Upgrading from the former voice assistant

Beam 0.4.4 removes the voice assistant. Upgrading deletes the stored model and voice preferences while preserving appearance, app order and channel settings. Microphone and WRITE_SECURE_SETTINGS permissions are no longer needed.

If Beam was manually selected as the system speech recognizer, select another available recognizer in Android settings. If `adb shell settings get secure voice_recognition_service` points to `com.home.tiles/.VoskRecognitionService` or `com.home.tiles/com.home.tiles.VoskRecognitionService`, remove only that obsolete selection with `adb shell settings delete secure voice_recognition_service`. Firmware without another recognizer will have no speech input in apps.
