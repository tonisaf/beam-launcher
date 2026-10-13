# Beam

A launcher for Android TVs, projectors and TV boxes, designed for remote control. Russian and English interface. Built with Kotlin and Jetpack Compose.

[Русская версия](README.md)

## Features

- Two Home layouts: **Tiles** and **Goldovaya** (round app icons and content rows).
- Manual app order directly on Home in both layouts, fixed favorites and an all-apps screen. Open an app menu → Move; arrows move the selected icon in its existing row, OK saves, Back cancels. Favorites remain in their own leading group.
- Preview channels and Continue Watching from installed apps: enable or hide each row, choose above or below apps, and reorder rows in each group.
- Initial setup with language, layout previews and light/dark theme. Choices apply immediately; reopen it from Settings → Initial setup.
- Launcher preferences: theme, backgrounds including animated XMB, language, layout, sounds and voice.
- USB storage, Now Playing, network status and battery level where the device provides it.
- Optional offline Vosk voice search, speech input in apps, app launch and media commands.

Content rows require the source app to publish Android TV channels or Watch Next entries. Beam displays and arranges that content; installing an app alone does not guarantee rows. LAMPA history and recommendations have been verified on the test device; YouTube currently publishes no rows there.

## Compatibility

- Android **8.0 or later** (`minSdk 26`), with installation of APKs allowed.
- The current APK includes native libraries only for **armeabi-v7a**. The OS must support 32-bit ARM apps; ARM64-only and x86 devices are not supported by this build.
- Optimized for landscape TV screens and a D-pad remote. Phones and tablets have not been verified.
- Selecting Beam as the default Home app depends on the firmware. Some manufacturers restrict launcher replacement.
- Channels, USB, media status, voice keys and battery depend on system services, permissions and hardware. TV channels may be absent on ordinary Android firmware.
- Device validation so far: XGIMI Play 6, Android 11. Compatibility with other models still requires testing.

XGIMI APIs are optional. A read-only adapter obtains the battery level on supported XGIMI firmware; other devices use Android battery data. Beam has no projector picture, focus, keystone, HDMI, power or sleep-timer controls. Device settings open through a standard system intent; XGIMI firmware without that activity uses a fallback to the stock settings and quick-panel services. This opens the manufacturer UI without controlling hardware from Beam.

## Installation

Download an APK from [Releases](https://github.com/tonisaf/beam-launcher/releases) or build the current source, install it and select Beam as Home in Android settings where supported.

**Versions:** v0.4.3 contains the universal launcher and GitHub Releases updater. v0.3 predates these changes. Development source is on [codex/remove-xgimi](https://github.com/tonisaf/beam-launcher/tree/codex/remove-xgimi); see [PR #11](https://github.com/tonisaf/beam-launcher/pull/11) for integration into `main`.

```sh
adb install -r app-release.apk
adb shell cmd package set-home-activity com.home.tiles/.MainActivity
```

Check what HOME actually opens: a successful ADB response alone does not prove that the firmware accepted the change. On first launch choose language, layout and theme. A separate **Beam: missing setup** dialog explains optional feature permissions and provides ADB commands. The voice model is installed separately.

[Setup and optional permissions](docs/installation.en.md). `tools/restore.ps1` is a legacy XGIMI-specific script, not a generic installer.

### Voice model

The Vosk model (~45 MB) is not part of the APK and is not downloaded automatically: speech recognition requires it. Install it once over adb (the microphone permission is
needed; see the setup guide):

```sh
# the device downloads the model itself (https only; add --es voice_sha256 <archive SHA-256> to verify):
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver \
    --es voice_url https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
# or push the file from the computer (see `VoiceModelProvider` in `Voice.kt`):
adb exec-in "content write --uri content://com.home.tiles.voicemodel/model.zip" < vosk-model-small-ru-0.22.zip
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --ez voice_install true
```

Voice commands open SmartTube, Spotify and Jellyfin (whichever of the known builds is installed;
apps that are not installed are not recognized). Your own apps are added over adb:

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver \
    --es voice_app "Kodi|org.xbmc.kodi|коди;открой коди"   # name | packages, comma-separated | phrases, separated by ;
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --ez voice_apps true          # list the added ones
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es voice_app_remove Kodi    # remove one
```

The Vosk model is Russian, so write the phrases in Russian (the way they are pronounced). The
interface language doesn't affect voice: the commands are always Russian.

Beam also answers the standard speech recognition requests from other apps (`RECOGNIZE_SPEECH`,
`RecognitionService`). That means any installed app can get a transcript of what you say into the
remote's microphone while you hold the voice button, even if the app itself has no microphone
permission: that is how this Android mechanism works. Nothing is recorded unless the button is
held. If that is unacceptable, don't make Beam the recognizer and don't install the model.

To check: `--ez voice_status true`. To make the microphone buttons in other apps (for example
SmartTube) use Beam as well, set it as the system recognizer (optional):
`adb shell settings put secure voice_recognition_service com.home.tiles/com.home.tiles.VoskRecognitionService`.


## Updates

Beam checks the latest stable [GitHub Release](https://github.com/tonisaf/beam-launcher/releases/latest) when Home opens, at most once per day. In Settings → Beam updates you can check manually or disable automatic checks. Selecting Later suppresses automatic offers for that release; manual checks remain available. Failed checks retry after one hour. There is no background silent installation.

Select Download update, then Install. Beam verifies asset size and GitHub SHA-256 digest, package identity, the installed signing certificate and a strictly higher versionCode. Android asks for installation confirmation. If prompted, permit APK installation from Beam and select Install again. Downloads interrupted by closing the dialog are discarded; reopen it and retry.

Publish stable numeric tags (`v0.4`, `v0.5`, `v1.0.1`) with an asset named `app-release.apk`. Preserve the release signing key and increase both versionName and versionCode. The asset must have the GitHub `sha256:` digest; APKs without it are rejected. The app contains no GitHub token and downloads only public releases. See [release procedure](docs/releases.md).

## Building

You need JDK 17 and the Android SDK (the path in `ANDROID_HOME` or `local.properties`).

```sh
./gradlew :app:assembleRelease
```

The launcher APK appears in `app/build/outputs/apk/release/`. The optional `:stub:assembleRelease` task builds legacy remote-button helpers for specific firmware; they are not required for a normal installation.

### Signing

Releases are signed with a permanent key that is kept outside the repository. Because of that, a
new version can be installed over an old one (`adb install -r`, settings are kept). Gradle takes the
key from `~/.beam/release.properties` (or from the file whose path is in `BEAM_RELEASE_PROPS`):

```properties
storeFile=C:/Users/you/.beam/beam-release.jks
storePassword=...
keyAlias=beam
keyPassword=...
```

The key is created once: `keytool -genkeypair -alias beam -keyalg RSA -keysize 4096 -validity 36500
-keystore beam-release.jks`. Keep a copy: if the key is lost, everyone has to uninstall Beam and
install it again. Without this file (in CI, for example) the build is signed with the computer's
debug key: it installs, but can't update a Beam signed with the real key, and vice versa. The
SHA-256 fingerprint of the release certificate:
`B0:A3:37:70:07:0C:3C:84:00:29:E1:D4:B3:4F:F3:CF:05:F0:A3:4B:97:41:7D:62:22:38:3E:95:9E:8C:20:F9`.


## Legacy XGIMI integration

The former projector controls are preserved in the [archive branch](https://github.com/tonisaf/beam-launcher/tree/codex/archive-xgimi-2026-10-13). [Firmware research](docs/xgimi-firmware.en.md), `tools/restore.ps1`, `skin/` and `russifier/` describe the previous integration and are not current universal-launcher features.

## License

MIT. See [LICENSE](LICENSE).
