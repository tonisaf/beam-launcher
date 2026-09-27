# XGIMI Play 6 firmware: how Beam calls the stock functions

**English** | [Русский](xgimi-firmware.ru.md)

What we found in the XGIMI Play 6 firmware (Android 11) and how Beam controls the projector without
the stock shell. Everything here was found by taking apart the stock apps and checked on the
projector. Mode numbers are given as XGIMI's settings app sends them.

None of this is a public API: on another XGIMI model or firmware the classes, methods and numbers
may differ. Beam calls all of it through reflection and simply turns a feature off on any error.

## Contents

- [How XGIMI's settings are built](#how-xgimis-settings-are-built)
- [Ways to call them](#ways-to-call-them)
- [Picture](#picture)
- [Sound](#sound)
- [Bluetooth](#bluetooth)
- [HDMI and HDMI-CEC](#hdmi-and-hdmi-cec)
- [Projection, keystone, size](#projection-keystone-size)
- [Focus and sensors](#focus-and-sensors)
- [Power](#power)
- [Other](#other)
- [Remote](#remote)
- [Firmware quirks and pitfalls](#firmware-quirks-and-pitfalls)
- [Permissions granted over adb](#permissions-granted-over-adb)
- [Exploring further](#exploring-further)

## How XGIMI's settings are built

- **There are no settings activities.** XGIMI's settings (`com.android.newsettings`) and its quick
  panel are overlay windows drawn by services. So they are opened with `startService` and the right
  action, not with `startActivity`.
- **The settings logic lives in the `com.xgimi.api` platform library.** It exists only on XGIMI
  firmware and is declared in the manifest like this:
  ```xml
  <uses-library android:name="com.xgimi.api" android:required="false" />
  ```
  It has two layers:
  - `com.xgimi.gmpf.api.*`: hardware managers (`DisplayManager`, `GmTvManager`, `GmAudioManager`,
    `SystemManager`, `MotionDetectionManager`, `PowerManager`, `GmFactoryManager`,
    `ProjectorFocusManager`). They work right away; each is a singleton from `getInstance()`.
  - `com.xgimi.api.*` and `com.xgimi.video.*` (`XgimiAudioManager`, `XgimiCommonManager`,
    `MstPictureManager`) talk over AIDL to the system service `com.xgimi.xgimiservice` and **only
    work after binding to it** (see below).
- **Bluetooth** is a separate class, `com.xgimi.bluetooth.XDBluetoothManager`, on the firmware's
  boot classpath.

## Ways to call them

### 1. An intent to an XGIMI service

Opens stock screens and panels. Code: `Xgimi.kt`, `Actions.kt`, `SleepTimer.kt`.

| What | Intent |
|---|---|
| Full settings | action `com.xgimi.settings.SETTINGS`, package `com.android.newsettings` |
| A settings page | the same + extra `data` = a `Settings://…` route (table below) |
| Quick panel (like the gear key) | action `com.xgimi.misckey.MISCKEY`, package `com.android.newsettings` |
| Power menu (like the power key) | action `com.xgimi.action.WINODWSYSTEM` (XGIMI's typo), package `com.xgimi.systemui` or `com.xgimi.shutdown` |
| Autofocus / manual focus / auto keystone | action `com.xgimi.systemui.action.AF_AK`, package `com.xgimi.systemui`, extra `type` (1 manual focus, 8 autofocus, 10 auto keystone), extra `from` = your package |
| Change picture mode | action `com.xgimi.settings.SETTINGS`, `data` = `changePictureMode`, `pictureModeValue` = mode number |

Routes for `data`:

| Page | Route |
|---|---|
| Picture mode (with the AI settings) | `Settings://com.xgimi.settings.image/mode` |
| Sound output | `Settings://com.xgimi.settings.sound/soundOutput` |
| Bluetooth | `Settings://com.xgimi.settings.bluetooth` |
| Wi-Fi | `Settings://com.xgimi.settings.net/wifi` |
| Keystone | `Settings://com.xgimi.settings.picture/keyStone` |
| Zoom and shift | `Settings://com.xgimi.settings.picture/zoom_displacement` |
| Rotation | `Settings://com.xgimi.settings.picture/rotate` |
| HDMI source (CEC, boot source, plug-and-play) | `Settings://com.xgimi.settings.signalSource/` |
| Picture correction (focus, reset) | `Settings://com.xgimi.settings/projection_screen` |

The `AF_AK` types come from `FocusUIV2.receiveIntent` in the firmware's SystemUI.

### 2. `com.xgimi.gmpf.api` managers through reflection

```kotlin
val cls = Class.forName("com.xgimi.gmpf.api.DisplayManager")
val dm = cls.getMethod("getInstance").invoke(null)
cls.getMethod("getDlpLumensLevel").invoke(dm)
```

Many setters take a `byte`, not an `int`; the tables below say where. Code: `XgimiHardware.kt`.

### 3. The `com.xgimi.xgimiservice` service (binding needed)

`XgimiAudioManager`, `XgimiCommonManager` and `MstPictureManager` only work once the library has
bound to the service. The stock settings do that on start, and Beam does the same:

```kotlin
val cls = Class.forName("com.xgimi.clients.XgimiAidlServiceManager")
val instance = cls.getField("INSTANCE").get(null)
val listener = Class.forName("com.xgimi.clients.XgimiAidlServiceManager\$IAidlConnectListener")
val callback = Proxy.newProxyInstance(listener.classLoader, arrayOf(listener)) { … }
cls.getMethod("init", Context::class.java, listener).invoke(instance, appContext, callback)
```

Binding is asynchronous: the first calls right after it may fail, so Beam retries reads a few
times (`XgimiService.bind`).

`XgimiCommonManager` runs as system. Beam uses it to write system properties an ordinary app can't
(`setSystemProperties(name, value)`).

### 4. Plain Android settings

Some stock switches turned out to be plain `Settings.System/Global/Secure`: the screensaver delay,
key clicks, Bluetooth visibility. Writing them needs permissions granted over adb (see
[below](#permissions-granted-over-adb)).

## Picture

| Function | Call | Values |
|---|---|---|
| Light source brightness | `DisplayManager.getDlpLumensLevel()` / `setDlpLumensLevel(byte)`, mode `getDlpLumensMode()` | 0..10 |
| Eco mode | `SystemManager.getEcoState()` / `setEcoState(boolean)` | |
| Current picture mode | `GmTvManager.getPictureMode(getCurrentInputSource())` | AI mode reads back as **10**, although the settings send **16** |
| Change mode | `MstPictureManager.setPictureMode(int)` (binding needed); fallback: the `changePictureMode` intent | AI 16, Cinema 1, Sport 9, TV 7, Custom 3, Office 25, Performance 5 |
| Brightness, contrast, saturation, sharpness, hue | `MstPictureManager.getPictureItem(item)` / `setPictureItem(item, value)` | item: 0 brightness, 1 contrast, 2 saturation, 3 sharpness, 4 hue |
| Colour temperature | `MstPictureManager.getColorTemp()` / `setColorTemp(int)` | 0 cool, 1 standard, 2 warm |
| Noise reduction | `MstPictureManager.getNoiseReduction()` / `setNoiseReduction(int)` | 0 off … 3 high, 4 auto |
| MEMC (motion smoothing) | `MstPictureManager.getMfcLevel()` / `setMfcLevel(int)` | 0 off … 3 high |
| Gamma | `GmTvManager.getTvGammaLevel(source)` / `setTvGammaLevel(source, int)` | 0 = 1.8 … 4 = 2.2 … 8 = 2.6 |
| Dynamic contrast | `GmTvManager.getTvDynamicContrastEnable(source)` / `setTvDynamicContrastEnable(source, boolean)` | |
| Local contrast | `GmTvManager.getUcdLevel(source)` / `setUcdLevel(source, int)` | 0 off … 3 high |
| HDR | `GmTvManager.getHdrEnable()` / `setHdrEnable(boolean)` | |
| Game mode | `DisplayManager.setGameModeType(int)`, `setGameModeState(int)`; read: `getGameModeProp(com.xgimi.gmpf.rp.GameModeProp)` → fields `type`, `state` | auto: type 1; on: type 0 + state 0; off: type 0 + state 1. Only works with an HDMI signal |
| Game mode level | `DisplayManager.setGameModeOption(int)` | 0 basic, 3 top speed. The driver on this model turns 1 (high frame rate) into 0. There is no getter, so Beam remembers the value itself |

`GmTvManager` calls marked `source` take the current input (`getCurrentInputSource()`) as their
first argument, the way XGIMI's page makes them.

XGIMI keeps the custom mode's parameters only in that mode. Factory values: everything at 50,
colour temperature 1, noise reduction 2, MEMC 3, gamma 4, dynamic contrast on, local contrast 2,
HDR on.

## Sound

| Function | Call | Values |
|---|---|---|
| Choose output | `XgimiAudioManager.setAudioDeviceOn(device, 0)` (binding needed) | 0 speaker, 1 S/PDIF, 2 ARC, 3 Bluetooth |
| Current output | `GmAudioManager.getAudioOutput()` | same numbers |
| Is a device connected | `GmAudioManager.isAudioDeviceConnected(byte)` | |
| Automatic/manual output | `GmAudioManager.getAudioDeviceSwitchMode()` / `setAudioDeviceSwitchMode(byte)` | 0 automatic, 1 manual |
| Sound mode | `GmAudioManager.getSoundeffect()` / `setSoundeffect(byte)` | AI 3, Cinema 1, Music 2, Sport 12, Karaoke 4 |
| eARC | `GmTvManager.getEARCEnableState()` / `setEARCEnable(boolean)` | XGIMI's “Auto” = on |
| Key clicks | `Settings.System.SOUND_EFFECTS_ENABLED` + `AudioManager.load/unloadSoundEffects()` | |

`GmAudioManager.setAudioOutput(byte)` only switches the amplifier: Android's routing stays as it
was, and a Bluetooth speaker keeps playing. The right way is `XgimiAudioManager.setAudioDeviceOn`,
which is what `VoiceHelper.setAudioDevice` in XGIMI's settings does.

## Bluetooth

The plain Android API can't connect audio devices without system privileges, so Beam goes through
XGIMI's service:

```kotlin
val cls = Class.forName("com.xgimi.bluetooth.XDBluetoothManager")
val manager = cls.getConstructor(Context::class.java).newInstance(appContext)
```

| Function | Call |
|---|---|
| Paired devices | `getBondDevices()` → a list of `XDBluetoothDeviceItem` with fields `BName`, `BAddress`, `BType`, `BStatus` |
| Connect / disconnect | `connectDevice(item)` / `disConnectDevice(item)` |
| Constants | static fields of `XDBluetoothDeviceItem`: `CONNECT_STATUS_CONNECTED`, `CONNECT_STATUS_CONNECTING`, `BTYPE_A2DP`, `BTYPE_HEADSET`, `BTYPE_REMOTE_CONTROL_HID` |
| Projector visibility | `Settings.Global` `bluetooth_discoverable` (1/0); XGIMI's service watches it and applies it |
| Absolute volume | the `persist.bluetooth.disableabsvol` property via `XgimiCommonManager.setSystemProperties` |
| Projector name | plain `BluetoothAdapter.setName()` |

XGIMI's scan reports results through an AIDL callback. So Beam finds new devices with Android's
standard discovery (`BluetoothAdapter.startDiscovery()`) and pairs them with plain `createBond()`.
Discovery needs the `ACCESS_FINE_LOCATION` permission, granted over adb. After pairing, a speaker
is connected through `XDBluetoothManager`.

## HDMI and HDMI-CEC

| Function | Call |
|---|---|
| List inputs | `TvInputManager.tvInputList`, type `TYPE_HDMI`. A device that introduces itself over CEC appears as a child input of the port (`parentId`) |
| Open an input | `Intent("com.xgimi.action.hdmiPlayer", TvContract.buildChannelUriForPassthroughInput(id))`, package `com.xgimi.xhplayer` |
| Is something plugged into HDMI 1 | `GmTvManager.getHdmiConnectStatus(byte 1)` |
| Switch to HDMI when plugged in | `SystemManager.getHdmiAutoSwitch()` / `setHdmiAutoSwitch(boolean)` |
| Boot straight into HDMI | properties `persist.sys.hdmi.bootsource` and `persist.sys.bootanim.alwayswait` = `1`/`0`, both via `XgimiCommonManager.setSystemProperties` |
| Control HDMI devices (needed for ARC) | `XgimiCommonManager.isHdmiCecControlEnabled()` / `setHdmiCecControlEnabled(boolean)` |
| Turn on and off with the HDMI device | `GmTvManager.getCecWakeUpState()`, `setCecWakeUp(boolean)` plus `XgimiCommonManager.setHdmiCecAutoDeviceOffEnabled`, `setHdmiCecAutoWakeupEnabled` |

A plain `ACTION_VIEW` of the same URI opens AOSP's stock Live TV app, which shows a black screen.
It has to be XGIMI's player.

When CEC control is turned off, XGIMI's page also turns off waking by CEC. Beam does the same.

## Projection, keystone, size

| Function | Call | Values |
|---|---|---|
| Mounting | `DisplayManager.getProjectorPutMode()` / `setProjectorPutMode(byte)` | 0 table, 1 ceiling, +2 rear projection |
| Auto by the tilt sensor | `MotionDetectionManager.getAutoReverse()` / `setAutoReverse(boolean)` | |
| Fine picture tilt | `SystemManager.setScreenRotation(int, float)` | 5 clockwise, 6 counter-clockwise; 0.5° steps |
| Keystone corners | `DisplayManager.getCorrectKeystone(KeyStoneFullCoordinates)` / `correctKeystone(KeyStoneFullCoordinates)` | see below |
| Digital zoom | `DisplayManager.setDigitalZoomStep(int)`, `getCurrentZoomStep(0)` | 0 = full size … 32 (`ZoomStepRange.zoomOutDigtalMaxNum`) |
| Auto keystone | `ProjectorFocusManager.getInstance().newAutoKst(9)` | 9 is what the settings pass |

`com.xgimi.gmpf.rp.KeyStoneFullCoordinates` has a `coordinates` field: a 9×9 grid of points with
`short` fields `x`, `y` in pixels of the 1920×1080 DLP chip. 4-point mode uses `[0][0]` top left,
`[0][1]` top right, `[1][0]` bottom left and `[1][1]` bottom right. “No correction” puts the
corners at (0,0), (1919,0), (0,1079), (1919,1079).

Shifting the picture means moving all four corners at once. It only works when the picture has
been made smaller with zoom.

`getCurrentZoomStep` doesn't return the step that was set, so Beam stores it itself.

## Focus and sensors

| Function | Call |
|---|---|
| Autofocus | `AF_AK` intent, `type` 8 |
| Manual focus (XGIMI's overlay, driven with the arrows) | `AF_AK` intent, `type` 1 |
| Keystone when the projector is moved | `MotionDetectionManager.getAccTriggerAK()` / `setAccTriggerAK(boolean)` |
| Refocus when tilted | `MotionDetectionManager.getAngTriggerAF()` / `setAngTriggerAF(boolean)` |
| Eye protection (dims when someone is in the beam) | `DisplayManager.getHumanDetectOnOff()` / `setHumanDetectOnOff(boolean)` |
| Auto keystone at power-on | `GmFactoryManager.getPowerOnAKFlag()` / `savePowerOnAKFlag(boolean)` |

## Power

| Function | Call |
|---|---|
| Battery | `com.xgimi.gmpf.api.PowerManager.getBatteryLevel()` (0..100), `isAdapterPowered()` |
| Power menu | the `com.xgimi.action.WINODWSYSTEM` intent |
| Power off (standby, like the remote) | the same intent to package `com.xgimi.shutdown`, sent **twice** ~1.5 s apart: a second request while the menu is open turns the projector off |
| Power-on chime | `SystemManager.isPowerOnMusicEnabled()` / `enablePowerOnMusic(boolean)` |

Android's standard battery service on the projector is a stub: “no battery”, always 100%. The real
charge is only in XGIMI's `PowerManager`. There are no change events, so Beam polls it once a
minute.

XGIMI's sleep timer is only in its Chinese power menu. Beam keeps its own timer (`AlarmManager`)
and turns the projector off through the power menu.

## Other

| Function | Call |
|---|---|
| Screensaver delay | `Settings.System.SCREEN_OFF_TIMEOUT` (ms; “never” = `Int.MAX_VALUE`) |
| “Any Door” (任意门) scenes, also the screensaver | the `com.xgimi.atmosphere` app |
| File manager | `com.xgimi.filemanager` |
| Model, firmware, serial number | properties `ro.boot.xgimi.modelname`, `ro.build.version.incremental`, `ro.boot.serialno` |

## Remote

| Key | Code | What the firmware does and how Beam gets around it |
|---|---|---|
| Voice | `KEYCODE_F5` | Unused by the firmware. Beam catches it system-wide in its accessibility service |
| Gear (settings) | `KEYCODE_MOVE_HOME` | XGIMI's window manager opens its quick panel **before** accessibility services see the key, and the component can't be disabled over adb. Beam closes the panel with an `ACTION_CLOSE_SYSTEM_DIALOGS` broadcast (with any `reason` but XGIMI's own): several times over ~1.5 s, and as soon as a `com.android.newsettings` window appears |
| The four app buttons | — | The firmware takes them before anyone else and launches fixed apps. Beam installs stubs with those package names (the `stub` module) that pass the press on to Beam |
| Volume | `KEYCODE_VOLUME_UP/DOWN` | Android TV handles them in the window manager before apps, and the firmware changes the volume before accessibility services. When Beam takes these keys over (the keystone screen), it puts the volume back |

The apps the firmware launches on the app buttons:

| Button | Package | Activity |
|---|---|---|
| 1 | `com.cibn.tv` | `com.youku.tv.home.activity.HomeActivity` |
| 2 | `com.ktcp.tvvideo` | `com.ktcp.video.activity.HomeActivity` |
| 3 | `com.gitvjimi.video` | `com.gala.video.app.epg.HomeActivity` |
| 4 | `com.hunantv.license` | `com.mgtv.tv.launcher.ChannelHomeActivity` |

Some buttons launch the app by component, others through `getLaunchIntentForPackage`. That's why a
stub has both the exact activity and a launcher entry.

**The remote's microphone.** While the voice key is held, the audio HAL (`telinkhid voice`, ADPCM)
feeds the remote's microphone to any app through a plain `AudioRecord`. With the key released the
input is silent: there is no built-in microphone. The first ~0.3 s of the stream carry the
remote's click.

## Firmware quirks and pitfalls

- **Accessibility services are reset on boot.** Also, if a service's process is killed, it is
  marked as crashed and never bound again. On every start and every return home, Beam restores its
  service with `WRITE_SECURE_SETTINGS`: it removes it from `enabled_accessibility_services`, waits
  ~1.5 s (the system merges two writes in a row) and adds it back. Code: `AccessibilityGuard.kt`.
- **During video the firmware force-stops Beam** to free memory, and the service drops out of the
  list. The only thing that brings Beam back afterwards is its notification listener
  (`NotificationListenerService`): the system rebinds it right away, and it restores the service.
- **`AF_AK` type 10 no longer starts auto keystone.** The command reaches SystemUI, but the ToF
  measurement doesn't start. Only `ProjectorFocusManager.newAutoKst(9)` works.
- **The AI picture mode's number**: reads return 10, writes need 16.
- **The `changePictureMode` intent** leaves XGIMI's picture page open behind the video. Calling
  `MstPictureManager` directly doesn't.
- **The time zone** defaults to Asia/Shanghai, with no SIM to detect it. `restore.ps1` turns off
  automatic detection and sets the zone with `service call alarm 3 s16 <zone>`.
- **`persist.*` system properties** can't be written by an app. Only
  `XgimiCommonManager.setSystemProperties` works, since that service runs as system.
- **A marquee in a full-screen overlay** kept ~40% CPU in Beam and ~17% in SurfaceFlinger: the
  overlay kept being redrawn over the video. Stop animations over video.
- **An overlay window left registered without a surface steals input focus**, and the remote stops
  working until a reboot. Before removing a window, make it `FLAG_NOT_FOCUSABLE` and remove it
  through the same `WindowManager`; in `onUnbind`, close it while its token is still valid.

## Permissions granted over adb

`tools/restore.ps1` does all of this:

| Permission | What for |
|---|---|
| `pm grant … WRITE_SECURE_SETTINGS` | restoring the accessibility service |
| `pm grant … READ_TV_LISTINGS` | other apps' channels the stock launcher never approved |
| `pm grant … ACCESS_FINE_LOCATION` | finding Bluetooth devices |
| `appops set … WRITE_SETTINGS allow` | screensaver delay, key clicks |
| `appops set … GET_USAGE_STATS allow` | tile order |
| `cmd notification allow_listener …/MediaListener` | “Now playing” and the restart after a force stop |
| `settings put secure enabled_accessibility_services …` | the panel over apps and the voice key |
| `cmd package set-home-activity …` | Beam as the home screen |

## Exploring further

`AdbCommandReceiver` has debug commands (only reachable from adb shell: guarded by the `DUMP`
permission). A generic call of any `gmpf` getter:

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "DisplayManager.getHumanDetectOnOff"
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "GmTvManager.getHdmiConnectStatus:1"
```

Arguments are comma-separated integers. They are converted to `byte` or `boolean` when the method
needs it.

Ready-made state dumps:

| Command | Shows |
|---|---|
| `--ez kst_get true` | keystone corners, offsets, zoom range |
| `--ez pic_get true` / `pic_items` / `pic_adv` | picture mode and parameters |
| `--ez sound_get true` | sound output and connected devices |
| `--ez hdmi_get true` | HDMI, boot to HDMI, CEC, input list |
| `--ez sensors_get true` | sensors |
| `--ez game_get true` | game mode |
| `--ez bt_list true` | paired Bluetooth devices |
| `--ez lumens true` | light source brightness |

The easiest way to find mode numbers: switch the value on XGIMI's stock page and read it back with
a getter through `gmpf`. That's how the sound modes and game mode levels were found.
