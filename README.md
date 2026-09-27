# Beam

**English** | [Русский](README.ru.md)

A home screen (launcher) for the **XGIMI Play 6** projector (Android 11), built with Jetpack Compose.
It replaces XGIMI's stock shell with its Chinese services and ads. The firmware itself is left
alone: everything is installed and rolled back over adb.

The interface is in English and Russian and follows the system language. Voice commands are
Russian only.

> An unofficial project, not affiliated with XGIMI. Made for the XGIMI Play 6 on Android 11 only.
> On other XGIMI models the projector functions (brightness, keystone, focus, battery) may not
> work; other devices are not supported.

## Features

- **Home screen**: a row of app tiles ordered by how often you use them, plus HDMI and USB drive
  tiles. Below it, a second row with other apps' channels (SmartTube subscriptions, recent Spotify,
  “Continue watching”). Also a “Now playing” widget and the projector's battery level.
- **Quick settings panel** over any app, opened with the remote's voice key: picture, sound,
  appearance, Bluetooth (including finding and pairing speakers), screensaver, power and sleep
  timer, projection, keystone and picture size, XGIMI settings.
- **App buttons on the remote**: the four buttons the firmware hard-wires to Chinese video
  services can be assigned to any app, the panel or “home”.
- **Offline voice search** with [Vosk](https://alphacephei.com/vosk/) (small Russian model).
- Look: light and dark themes, backgrounds including an animated one in the style of the PS3 XMB,
  interface sounds.

## Installation

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools) and the
projector on the same network.

1. Turn on developer mode and network debugging (ADB) on the projector. Turn off any VPN on the
   computer, or adb won't reach the projector.
2. Build the APKs (see [Building](#building)). CI builds are kept for a week as artifacts on the
   Actions tab.
3. From the repository root, run (Windows PowerShell):

   ```powershell
   .\tools\restore.ps1 -Device 192.168.1.50 -Language en-US -TimeZone Europe/London -Reboot
   ```

   The script is safe to run again. Steps whose app is missing are skipped. APKs to install along
   the way (SmartTube, LeanKey…) go into `tools\apks\`. The script's own messages are in Russian.

### What `restore.ps1` changes

Read this before running it:

- installs Beam and the remote button stubs (see below);
- **disables** (`pm disable-user`, nothing is uninstalled) the stock launcher `com.xgimi.home`, the
  Sogou keyboard, ads, telemetry, bug report uploads, the Chinese app store, and XGIMI's voice and
  IoT services. The full list is in section 2 of the script;
- makes LeanKey the system keyboard if it is installed;
- grants Beam permissions over adb: usage stats, notification access (for “Now playing”), TV
  channels, `WRITE_SECURE_SETTINGS`, location (for finding Bluetooth devices), changing system
  settings;
- enables Beam's accessibility service: it catches the voice key and draws the panel over apps.
  The firmware resets it on boot; Beam turns it back on by itself;
- makes Beam the home screen;
- sets the system language (`-Language`, default `ru-RU`; Beam's interface follows it), the time
  zone (`-TimeZone`, default `Europe/Moscow`) and the Bluetooth name “XGIMI Play 6”.

### Rolling back

```sh
adb shell pm enable com.xgimi.home          # and the other packages from section 2 of restore.ps1
adb uninstall com.home.tiles
adb uninstall com.cibn.tv                   # the remote button stubs
adb uninstall com.ktcp.tvvideo
adb uninstall com.gitvjimi.video
adb uninstall com.hunantv.license
```

After a reboot the stock launcher is the home screen again.

### Remote button stubs

The firmware handles the remote's four app buttons itself and launches fixed Chinese apps by
package name. The `stub` module builds four tiny APKs **with those package names**
(`com.cibn.tv`, `com.ktcp.tvvideo`, `com.gitvjimi.video`, `com.hunantv.license`). All they do is
pass the press on to Beam. If the real apps with those names are installed on the projector, the
stubs won't install.

### Voice model

The Vosk model (~45 MB) is not in the APK. Beam downloads it from `alphacephei.com` the first time
the voice key is used. Without internet on the projector it can be pushed over adb, as described in
`VoiceModelProvider` (`app/src/main/java/com/home/tiles/Voice.kt`).

## Building

You need JDK 17 and the Android SDK (its path in `ANDROID_HOME` or `local.properties`).

```sh
./gradlew :app:assembleRelease :stub:assembleRelease
```

The APKs end up in `app/build/outputs/apk/release/` and `stub/build/outputs/apk/*/release/`. The
release build is signed with your computer's debug key, so an APK built on another computer (or
downloaded from CI) won't install over yours: uninstall the old one first.

### Quick install while developing

```sh
cp local.env.example local.env   # fill in the projector's IP (and paths if adb isn't on PATH)
./deploy.sh                      # build and install Beam
./deploy.sh --stubs              # also the remote button stubs
./deploy.sh --no-start           # don't bring Beam to the front after installing
```

`local.env` stays out of git. Both `deploy.sh` and `tools/restore.ps1` read it.

### Translations

Interface text lives in `app/src/main/res/values/strings.xml` (English, the default) and
`values-ru/strings.xml` (Russian). Another language is one more `values-xx/strings.xml`.

### How Beam drives the projector

Which XGIMI firmware services, classes and settings Beam calls, and what in the firmware doesn't
work as you'd expect: [docs/xgimi-firmware.md](docs/xgimi-firmware.md).

### Debug commands

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es background XMB --ez bg_animation true
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es bt_name "XGIMI Play 6"
adb shell am start -n com.home.tiles/.MicTestActivity --ei seconds 6   # remote microphone test
```

## License

[MIT](LICENSE). Provided “as is”, without warranty: `restore.ps1` changes the projector's system
settings, run it at your own risk.
