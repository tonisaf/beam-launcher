# Setting up Beam

[Русская версия](installation.md) · [Features and compatibility](../README.en.md)

Install a compatible APK and select Beam as Home in Android settings. For ADB installation: `adb install -r app-release.apk`. On first launch choose language, layout and theme; these remain available in Beam settings.

## Optional features

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
