# Beam

Android TV launcher with Switch and Google TV layouts, in Russian and English.

## Features

- Apps, favorites, USB storage, preview channels, Continue Watching and Now Playing.
- Network status and battery level (the read-only XGIMI API on the projector, standard Android API elsewhere).
- Launcher settings from Home: theme, XMB background, language, layout, channels, sounds and voice.
- Offline Vosk voice search, speech input in apps, app launch and media commands.

The floating quick panel and XGIMI controls have been removed: picture, projector audio,
focus, keystone, HDMI, sensors, power and sleep timer. Beam no longer intercepts the settings key
or closes stock settings. A short voice-key press has no panel action; holding it still records speech.
System settings use the standard Android intent.

The previous state is saved in the [archive branch](https://github.com/tonisaf/beam-launcher/tree/codex/archive-xgimi-2026-10-13).
XGIMI instructions in `docs/xgimi-firmware.en.md`, `tools/restore.ps1`, `skin/` and `russifier/`
describe the previous integration, not verified behavior of the current launcher.

## Installation

Install the built APK and select Beam as the Home app. The initial setup dialog lists required
permissions and ADB commands. Install the voice model separately. Native libraries are still built
for `armeabi-v7a`; other ABIs have not been verified.

### Voice model

The Vosk model (~45 MB) is not part of the APK and is not downloaded automatically: speech recognition requires it. Install it once over adb (the microphone permission is
needed; `restore.ps1` grants it):

```sh
# the projector downloads the model itself (https only; add --es voice_sha256 <archive SHA-256> to verify):
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


## Building

You need JDK 17 and the Android SDK (the path in `ANDROID_HOME` or `local.properties`).

```sh
./gradlew :app:assembleRelease :stub:assembleRelease
```

The APKs appear in `app/build/outputs/apk/release/` and `stub/build/outputs/apk/*/release/`.

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


## License

MIT. See [LICENSE](LICENSE).
