# XGIMI Play 6 firmware: how Beam calls the stock functions

[Русская версия](xgimi-firmware.md)

A report on what was found in the firmware of the XGIMI Play 6 (Android 11) and how Beam controls
the projector without the stock shell. Everything was found by taking the stock apps apart and was
checked on the projector. Mode codes are written the way XGIMI's settings pass them.

None of this is a public API: on another model or firmware version the classes, methods and
numbers may differ. Beam's code reaches all of it through reflection and simply disables a feature
on any error.

## Contents

- [How XGIMI's settings are built](#how-xgimis-settings-are-built)
- [Ways to call it](#ways-to-call-it)
- [Picture](#picture)
- [Sound](#sound)
- [Bluetooth](#bluetooth)
- [HDMI and HDMI-CEC](#hdmi-and-hdmi-cec)
- [Projection, keystone, size](#projection-keystone-size)
- [Focus and sensors](#focus-and-sensors)
- [Power](#power)
- [Other](#other)
- [Remote](#remote)
- [Network control: the XGIMI app's protocol](#network-control-the-xgimi-apps-protocol)
- [The Russian skin and other XGIMI apps](#the-russian-skin-and-other-xgimi-apps)
- [Firmware quirks and pitfalls](#firmware-quirks-and-pitfalls)
- [Permissions granted over adb](#permissions-granted-over-adb)
- [How to research further](#how-to-research-further)

## How XGIMI's settings are built

- **There is no activity for the settings.** XGIMI's settings (`com.android.newsettings`) and the
  quick panel are overlay windows drawn by services. That's why they are opened with
  `startService` and the right action, not with `startActivity`.
- **The settings logic lives in the platform library `com.xgimi.api`.** It exists only on XGIMI
  firmware and is declared in the manifest like this:
  ```xml
  <uses-library android:name="com.xgimi.api" android:required="false" />
  ```
  It has two layers:
  - `com.xgimi.gmpf.api.*` — hardware managers (`DisplayManager`, `GmTvManager`, `GmAudioManager`,
    `SystemManager`, `MotionDetectionManager`, `PowerManager`, `GmFactoryManager`,
    `ProjectorFocusManager`). They work right away; a singleton via `getInstance()`.
  - `com.xgimi.api.*` and `com.xgimi.video.*` (`XgimiAudioManager`, `XgimiCommonManager`,
    `MstPictureManager`) talk over AIDL to the system service `com.xgimi.xgimiservice` and
    **only work after binding** (see below).
- **Bluetooth** is a separate class, `com.xgimi.bluetooth.XDBluetoothManager`, on the firmware's
  boot classpath.

## Ways to call it

### 1. An intent to the XGIMI service

Opens the stock screens and panels. Code: `Xgimi.kt`, `Actions.kt`, `SleepTimer.kt`.

| What | Intent |
|---|---|
| Full settings | action `com.xgimi.settings.SETTINGS`, package `com.android.newsettings` |
| A settings page | the same + extra `data` = a `Settings://…` route (table below) |
| Quick panel (like the gear button) | action `com.xgimi.misckey.MISCKEY`, package `com.android.newsettings` |
| Power menu (like the power button) | action `com.xgimi.action.WINODWSYSTEM` (XGIMI's typo), package `com.xgimi.systemui` or `com.xgimi.shutdown` |
| Autofocus / manual focus / auto keystone | action `com.xgimi.systemui.action.AF_AK`, package `com.xgimi.systemui`, extra `type` (1 manual focus, 8 autofocus, 10 auto keystone), extra `from` = your own package |
| Change the picture mode | action `com.xgimi.settings.SETTINGS`, `data` = `changePictureMode`, `pictureModeValue` = the mode number |

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
| HDMI source (CEC, boot, plug-and-play) | `Settings://com.xgimi.settings.signalSource/` |
| Picture correction (focus, reset) | `Settings://com.xgimi.settings/projection_screen` |

The `AF_AK` types are taken from `FocusUIV2.receiveIntent` in the firmware's SystemUI.

### 2. The `com.xgimi.gmpf.api` managers through reflection

```kotlin
val cls = Class.forName("com.xgimi.gmpf.api.DisplayManager")
val dm = cls.getMethod("getInstance").invoke(null)
cls.getMethod("getDlpLumensLevel").invoke(dm)
```

Many setters take a `byte`, not an `int`: this is noted in the tables below. Code:
`XgimiHardware.kt`.

### 3. The `com.xgimi.xgimiservice` service (needs binding)

`XgimiAudioManager`, `XgimiCommonManager` and `MstPictureManager` only work if the library has
bound to the service. The stock settings do this at startup, and so does Beam:

```kotlin
val cls = Class.forName("com.xgimi.clients.XgimiAidlServiceManager")
val instance = cls.getField("INSTANCE").get(null)
val listener = Class.forName("com.xgimi.clients.XgimiAidlServiceManager\$IAidlConnectListener")
val callback = Proxy.newProxyInstance(listener.classLoader, arrayOf(listener)) { … }
cls.getMethod("init", Context::class.java, listener).invoke(instance, appContext, callback)
```

Binding is asynchronous: the first calls right after it may return an error, so Beam repeats the
read a few times (`XgimiService.bind`).

If the service restarted and reported it through the listener (a method name with `disconnect`,
`died`, `unbind` or `lost`), `XgimiService` binds again: after 2, 4, 8 … 30 s, at most eight times.
The listener's method names aren't described in the library's documentation, so on the first
binding Beam writes them to the log (tag `XgimiService`). Whether `init` may be called again hasn't
been verified: if retrying doesn't help, look in the log at what the library reported and what the
retry did.

`XgimiCommonManager` runs as system. Through it Beam writes system properties that an ordinary app
can't (`setSystemProperties(name, value)`).

### 4. Plain Android settings

Some of the stock switches turned out to be just `Settings.System/Global/Secure`: the screensaver
delay, click sounds, Bluetooth visibility. Writing them needs permissions granted over adb (see
[below](#permissions-granted-over-adb)).

## Picture

| Function | Call | Values |
|---|---|---|
| Light source brightness | `DisplayManager.getDlpLumensLevel()` / `setDlpLumensLevel(byte)`, mode `getDlpLumensMode()` | 0..10 |
| Eco mode | `SystemManager.getEcoState()` / `setEcoState(boolean)` | |
| Current picture mode | `GmTvManager.getPictureMode(getCurrentInputSource())` | The AI mode is returned as **10**, although the settings pass **16** |
| Change the mode | `MstPictureManager.setPictureMode(int)` (needs binding); fallback — the `changePictureMode` intent | AI 16, Movie 1, Sport 9, TV 7, Custom 3, Office 25, Performance 5 |
| Brightness, contrast, saturation, sharpness, hue | `MstPictureManager.getPictureItem(item)` / `setPictureItem(item, value)` | item: 0 brightness, 1 contrast, 2 saturation, 3 sharpness, 4 hue |
| Colour temperature | `MstPictureManager.getColorTemp()` / `setColorTemp(int)` | 0 cool, 1 normal, 2 warm |
| Noise reduction | `MstPictureManager.getNoiseReduction()` / `setNoiseReduction(int)` | 0 off … 3 high, 4 auto |
| MEMC (motion smoothing) | `MstPictureManager.getMfcLevel()` / `setMfcLevel(int)` | 0 off … 3 strong |
| Gamma | `GmTvManager.getTvGammaLevel(source)` / `setTvGammaLevel(source, int)` | 0 = 1.8 … 4 = 2.2 … 8 = 2.6 |
| Dynamic contrast | `GmTvManager.getTvDynamicContrastEnable(source)` / `setTvDynamicContrastEnable(source, boolean)` | |
| Local contrast | `GmTvManager.getUcdLevel(source)` / `setUcdLevel(source, int)` | 0 off … 3 high |
| HDR | `GmTvManager.getHdrEnable()` / `setHdrEnable(boolean)` | |
| Game mode | `DisplayManager.setGameModeType(int)`, `setGameModeState(int)`; reading: `getGameModeProp(com.xgimi.gmpf.rp.GameModeProp)` → fields `type`, `state` | auto: type 1; on: type 0 + state 0; off: type 0 + state 1. Works only with an HDMI signal |
| Game mode level | `DisplayManager.setGameModeOption(int)` | 0 basic, 3 maximum speed. The driver of this model turns the value 1 (high frame rate) into 0. There is no getter; Beam remembers the value itself |

`GmTvManager` parameters marked `source` take the current input as the first argument
(`getCurrentInputSource()`), the way XGIMI's page does it.

XGIMI stores the custom-mode parameters only in that mode. The factory values: everything at 50,
temperature 1, noise reduction 2, MEMC 3, gamma 4, dynamic contrast on, local contrast 2, HDR on.

## Sound

| Function | Call | Values |
|---|---|---|
| Choosing the output | `XgimiAudioManager.setAudioDeviceOn(device, 0)` (needs binding) | 0 speaker, 1 S/PDIF, 2 ARC, 3 Bluetooth |
| Current output | `GmAudioManager.getAudioOutput()` | the same numbers |
| Whether a device is connected | `GmAudioManager.isAudioDeviceConnected(byte)` | |
| Auto/manual output selection | `GmAudioManager.getAudioDeviceSwitchMode()` / `setAudioDeviceSwitchMode(byte)` | 0 auto, 1 manual |
| Sound mode | `GmAudioManager.getSoundeffect()` / `setSoundeffect(byte)` | AI 3, Movie 1, Music 2, Sport 12, Karaoke 4 |
| eARC | `GmTvManager.getEARCEnableState()` / `setEARCEnable(boolean)` | XGIMI's "Auto" = on |
| Click sounds | `Settings.System.SOUND_EFFECTS_ENABLED` + `AudioManager.load/unloadSoundEffects()` | |

`GmAudioManager.setAudioOutput(byte)` only switches the amplifier: Android's routing stays as it
was, and the Bluetooth speaker keeps playing. The right way is `XgimiAudioManager.setAudioDeviceOn`;
that's what `VoiceHelper.setAudioDevice` does in XGIMI's settings.

## Bluetooth

Connecting audio devices through the ordinary Android API isn't possible without system
permissions. So Beam works through the XGIMI service:

```kotlin
val cls = Class.forName("com.xgimi.bluetooth.XDBluetoothManager")
val manager = cls.getConstructor(Context::class.java).newInstance(appContext)
```

| Function | Call |
|---|---|
| Paired devices | `getBondDevices()` → a list of `XDBluetoothDeviceItem` with the fields `BName`, `BAddress`, `BType`, `BStatus` |
| Connect / disconnect | `connectDevice(item)` / `disConnectDevice(item)` |
| Constants | static fields of `XDBluetoothDeviceItem`: `CONNECT_STATUS_CONNECTED`, `CONNECT_STATUS_CONNECTING`, `BTYPE_A2DP`, `BTYPE_HEADSET`, `BTYPE_REMOTE_CONTROL_HID` |
| The projector's visibility | `Settings.Global` `bluetooth_discoverable` (1/0); the XGIMI service watches it and applies it itself |
| Absolute volume | the property `persist.bluetooth.disableabsvol` through `XgimiCommonManager.setSystemProperties` |
| The projector's name | the ordinary `BluetoothAdapter.setName()` |

XGIMI's search returns results through an AIDL callback. So Beam looks for new devices with
Android's standard discovery (`BluetoothAdapter.startDiscovery()`) and pairs them with the ordinary
`createBond()`. Discovery needs the `ACCESS_FINE_LOCATION` permission, which is granted over adb.
After pairing, the speaker is connected through `XDBluetoothManager`.

## HDMI and HDMI-CEC

| Function | Call |
|---|---|
| List of inputs | `TvInputManager.tvInputList`, type `TYPE_HDMI`. A device that introduced itself over CEC appears as a child input of the port (`parentId`) |
| Open an input | `Intent("com.xgimi.action.hdmiPlayer", TvContract.buildChannelUriForPassthroughInput(id))`, package `com.xgimi.xhplayer` |
| Something is connected to the HDMI port | `GmTvManager.getHdmiConnectStatus(byte port)`, ports start at 1 in the order of the input list (Beam polls all of its ports; `--ez hdmi_get true` prints `connectedPorts`) |
| Switch to HDMI on connect | `SystemManager.getHdmiAutoSwitch()` / `setHdmiAutoSwitch(boolean)` |
| Boot straight into HDMI | the properties `persist.sys.hdmi.bootsource` and `persist.sys.bootanim.alwayswait` = `1`/`0`, both through `XgimiCommonManager.setSystemProperties` |
| Control of HDMI devices (needed for ARC) | `XgimiCommonManager.isHdmiCecControlEnabled()` / `setHdmiCecControlEnabled(boolean)` |
| Turning on and off together with an HDMI device | `GmTvManager.getCecWakeUpState()`, `setCecWakeUp(boolean)` plus `XgimiCommonManager.setHdmiCecAutoDeviceOffEnabled`, `setHdmiCecAutoWakeupEnabled` |

A plain `ACTION_VIEW` with the same URI opens the stock AOSP Live TV app, which shows a black
screen. The XGIMI player is what's needed.

When CEC control is turned off, XGIMI's page also turns off the CEC wake-up. Beam repeats that
behaviour.

## Projection, keystone, size

| Function | Call | Values |
|---|---|---|
| Projector placement | `DisplayManager.getProjectorPutMode()` / `setProjectorPutMode(byte)` | 0 table, 1 ceiling, +2 rear projection |
| Auto from the tilt sensor | `MotionDetectionManager.getAutoReverse()` / `setAutoReverse(boolean)` | |
| Fine picture tilt | `SystemManager.setScreenRotation(int, float)` | 5 clockwise, 6 counter-clockwise; step 0.5° |
| Keystone corners | `DisplayManager.getCorrectKeystone(KeyStoneFullCoordinates)` / `correctKeystone(KeyStoneFullCoordinates)` | see below |
| Digital zoom | `DisplayManager.zoomStart(int)`, `setDigitalZoomStep(int)`, `getCurrentZoomStep(0)`, `zoomFinish(int)` | 0 = full size … 32; see "Zoom session" below |
| Auto keystone | `ProjectorFocusManager.getInstance().newAutoKst(9)` | 9 — the way the settings call it |

`com.xgimi.gmpf.rp.KeyStoneFullCoordinates` contains the field `coordinates` — a 9×9 grid of points
with `short` fields `x`, `y` in pixels of the 1920×1080 DLP chip. In the 4-point mode `[0][0]` is
the top-left, `[0][1]` the top-right, `[1][0]` the bottom-left and `[1][1]` the bottom-right
corner. "No correction" is the corners at (0,0), (1919,0), (0,1079), (1919,1079).

Shifting the picture is moving all four corners at once. It only works when the picture has been
reduced with the zoom.

### Zoom session

`setDigitalZoomStep` is only accepted inside a session: `zoomStart(1)` … `zoomFinish(1)` (1 is
the type XGIMI's own "Zoom and shift" page opens its session with). Outside one the firmware logs
`XGIMI_ZOOM_CTL: steps is ERR` to logcat while the call itself returns normally, so the refusal
can't be seen from the return value.

Inside a session the firmware moves the picture **one step per call** toward the requested one: a
call with step 8 from zero gives step 1. Beam therefore repeats the call until
`getCurrentZoomStep(0)` returns the number it wants (the getter is reliable for that, also after
`zoomFinish`, so the step doesn't have to be stored separately). The calls are fast: five in a row
take under a second.

Opening a session moves the top corners of the correction about (11, 6) pixels from "no
correction". "No correction" (writing the corners) puts the exact values back.

## Focus and sensors

| Function | Call |
|---|---|
| Autofocus | the `AF_AK` intent, `type` 8 |
| Manual focus (XGIMI overlay, controlled with the arrows) | the `AF_AK` intent, `type` 1 |
| Keystone when the projector is moved | `MotionDetectionManager.getAccTriggerAK()` / `setAccTriggerAK(boolean)` |
| Focus on tilt | `MotionDetectionManager.getAngTriggerAF()` / `setAngTriggerAF(boolean)` |
| Eye protection (dimming when someone is in the beam) | `DisplayManager.getHumanDetectOnOff()` / `setHumanDetectOnOff(boolean)` |
| Auto keystone on power-on | `GmFactoryManager.getPowerOnAKFlag()` / `savePowerOnAKFlag(boolean)` |

## Power

| Function | Call |
|---|---|
| Battery | `com.xgimi.gmpf.api.PowerManager.getBatteryLevel()` (0..100), `isAdapterPowered()` |
| Power menu | the `com.xgimi.action.WINODWSYSTEM` intent |
| Power off (standby, like from the remote) | accessibility service: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. The system logs `Going to sleep due to power_button`, the projector goes into standby and drops off the network, no menu on screen |
| The same without the accessibility service | the `WINODWSYSTEM` intent to the package `com.xgimi.shutdown`, sent **twice** with a pause of ~1.5 s: a repeated request while the menu is open turns the projector off |
| Restart without the menu | the broadcast `com.xgimi.systemui.OPEN_RESTART_DEVICE` with the permission `com.xgimi.permission.system` (protection level `normal`, granted at install). The power menu handles it as a tap on "Restart" |
| Android's own power menu | `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`: a "Lock / Power off / Restart" window in the system language. "Power off" there most likely cuts the power completely; not tried |
| Power-on chime | `SystemManager.isPowerOnMusicEnabled()` / `enablePowerOnMusic(boolean)` |

Android's standard battery service on the projector is a stub: "no battery", always 100%. The real
charge exists only in XGIMI's `PowerManager`. There are no change events, so Beam polls it once a
minute.

XGIMI's sleep timer exists only in the Chinese power menu. Beam keeps its own timer
(`AlarmManager`) and turns the projector off by locking the screen (fallback: the power menu).

The command `com.xgimi.action.shutdown` with `action=com.xgimi.action.strScreenOff` does not turn the
screen off: the power menu only resets its own state. Turning off just the screen, without putting the
projector into standby, was not found possible.

## Other

| Function | Call |
|---|---|
| Screensaver delay | `Settings.System.SCREEN_OFF_TIMEOUT` (ms; "never" = `Int.MAX_VALUE`) |
| "Any Door" (任意门) scenes | the app `com.xgimi.atmosphere` 6.0.0.33: launcher activity `.ScreensaverActivity`; settings via `com.xgimi.atmosphere.action.SETTING_MANAGE` / `SCREENSAVER_MANAGE` / `SCREENSAVER_CAROUSEL`. Ships its own DreamService `.view.AmbientService`, but it is not the selected one |
| System screensaver (DreamService) | `com.xgimi.screensaver/.service.ScreenSaverDreamService` (6.0.1_lite), stored in `Settings.Secure.screensaver_components`; `screensaver_enabled=1` |
| File manager | `com.xgimi.filemanager` |
| Model, firmware, serial number | the properties `ro.boot.xgimi.modelname`, `ro.build.version.incremental`, `ro.boot.serialno` |

## Remote

| Button | Code | What the firmware does and how Beam gets around it |
|---|---|---|
| Voice | `KEYCODE_F5` | The firmware doesn't use it. Beam catches it in the accessibility service across the whole system |
| Gear (settings) | `KEYCODE_MOVE_HOME` | XGIMI's window manager opens the quick panel **before** the accessibility services see the button, and the component can't be disabled over adb. Beam closes the panel with an `ACTION_CLOSE_SYSTEM_DIALOGS` broadcast (with any `reason` except XGIMI's own): several times within ~1.5 s and right when the `com.android.newsettings` window appears |
| The four app buttons | — | The firmware eats them before anyone else and launches fixed apps. Beam installs stubs with those package names (the `stub` module), which pass the press on to Beam |
| Volume | `KEYCODE_VOLUME_UP/DOWN` | Android TV handles it in the window manager before the apps, and the firmware changes the volume before the accessibility services. When Beam intercepts these buttons (the keystone screen), it puts the volume back |

Which apps the firmware launches on the app buttons:

| Button | Package | Activity |
|---|---|---|
| 1 | `com.cibn.tv` | `com.youku.tv.home.activity.HomeActivity` |
| 2 | `com.ktcp.tvvideo` | `com.ktcp.video.activity.HomeActivity` |
| 3 | `com.gitvjimi.video` | `com.gala.video.app.epg.HomeActivity` |
| 4 | `com.hunantv.license` | `com.mgtv.tv.launcher.ChannelHomeActivity` |

Some buttons launch the app by component, others through `getLaunchIntentForPackage`. That's why
the stub has both the exact activity and a launcher entry.

**The remote's microphone.** While the voice button is held, the audio HAL (`telinkhid voice`,
ADPCM) gives the sound from the remote's microphone to any app through the ordinary `AudioRecord`.
When the button is released the input is silent: there is no built-in microphone. A click from the
remote is audible in the first ~0.3 s of the stream.

## Network control: the XGIMI app's protocol

I took apart the official "XGIMI" app (`com.xgimi.zhushou` 4.10.32, decompiled with jadx, class
`com.xgimi.control.manager.ConnectManagerV2`) and tested it on the Play 6. Controlling the
projector over Wi-Fi needs no Chinese account: the protocol is local, and only the app's own UI
asks for the cloud or WeChat (`isLogin()` blocks auto-connect and the device list).

| Port | What it is | Result on the Play 6 |
|---|---|---|
| UDP **16735** | Keys: the string `KEYPRESSES:<code>` | Works without pairing, the projector answers `XGIMI_UCONTROL` |
| UDP 16752 | Discovery: `{"action":9998,"msgid":"1","appid":"1"}` | Port closed, no reply |
| UDP 16750 | JSON commands (`VcontrolCmd`: picture mode, HDMI, app launch) | Port closed |
| TCP 13145 | The same plus pairing (`AuthCode`, action 50001) | Connection refused |
| UDP 16737 | Not mentioned in the app | Open, but silent to any packet. The owner can't be found without root |

Key codes for `KEYPRESSES:<code>`:

| Button | Code | Button | Code |
|---|---|---|---|
| Left / Right | 50 / 37 | Home / Back | 35 / 48 |
| Up / Down | 36 / 38 | Menu | 139 |
| OK | 49 | Volume + / − | 115 / 114 |
| Power (off) | 116 | Quick menu | 251 |

Holding: `KEYSSTATUS:<code>+1` on press and `KEYSSTATUS:<code>+0` on release (that's how the app does
a long press on the arrows, OK and Home).

**Focus** goes through the same channel: `KEYSSTATUS:253+1` … `KEYSSTATUS:253+0` in one direction
and `254` in the other. Checked in logcat: the motor steps (`MOTOR_CUR_STEP`) and the firmware
sends `electromotion focus stop`. Which direction is nearer and which is farther can only be seen
in the picture.

Volume was checked with `dumpsys audio`: `KEYPRESSES:115` raised the speaker volume by 2 (16 → 18)
and `114` brought it back.

What this doesn't give you: the picture and HDMI commands from `VcontrolCmd` go to closed ports,
so they can't be sent over the network on this firmware. For those, use the ways to call it from
the start of this report.

Test script: [`tools/xgimi_probe.py`](../tools/xgimi_probe.py) (`search`, `key`, `cmd`, `tcp`).

## The Russian skin and other XGIMI apps

Translating the stock screens without root rests on a "skin". Every XGIMI app embeds
`com.xgimi.gimiskin.sdk.SkinEngine`: it loads the APK named by the property
`persist.sys.xgimi.themeV2.url` into a separate `AssetManager` and replaces strings **by resource name**.
This is not an RRO and not an id merge. The skin (resources only, no code) lives in `skin/` of the separate [xgimi-russifier](https://github.com/tonisaf/xgimi-russifier) project and is built by `skin/build.ps1`.

The replacement happens in two places:
- `DynamicResource.getString(id)` in the cell library, called by `TextComponent.setText(int)`. If the name is not in
  the skin, the app's own string is used.
- `SkinEngine.getString(int)` in XGIMI's buttons and dialogs.

When inflating XML, `SkinFactory` replaces colours, images and styles, but not `android:text`. Text taken through
`Context.getString`, `TextView.setText(int)` or XML is not changed by the skin. That is why the engine is in
almost every app but works on text in far fewer. The left column of the settings, the quick-panel tiles and the
power menu are not translated by the skin.

**Checking an app without translating it.** Build a probe skin in which every Chinese string of the app is
`RU|` plus the original, put it into `themeV2.url`, stop the app (`am force-stop`) and open it. A marker on
screen means the text goes through the skin; no marker means a translation would change nothing. Control: the
same markers in the settings app, where the skin is known to work. Afterwards point the property back at the
real skin. A static smali scan gives only an upper bound: it counted `current_system_version` as replaceable,
and on screen "当前版本" stayed Chinese.

Checked (Chinese strings by default):

| App | Strings | Via the skin (by code) | Not in the skin | On the device |
|---|---|---|---|---|
| System update `upgrade` | 106 | 5 | 1 | main screen, no markers |
| Wireless casting `wirelessscreen` | 190 | 6 | 3 | main screen, no markers |
| File manager `filemanager` | 23 | 4 | 0 | main screen, no markers |
| HDMI player `xhplayer` | 25 | 4 | 0 | not opened |
| Auto keystone `autokst` | 131 | 0 (the engine is never called) | 0 | not opened: it is a calibration app and may change factory data |
| Accessories `ota.accessories` | 189 | 31 | 27 | not opened: update dialogs for the curtain, speaker and HDMI dongle |

The `autokst` app is not what the user sees: it is a factory calibration station (gyroscope, warm-up,
projection positions), and it has no skin engine. The messages of the real auto keystone are drawn by SystemUI,
and by default they are in English ("Auto-focusing, please wait", "Automatic correction completed"). Of the 114
related strings, 14 go through the skin (the environment-detection dialog and wall-colour matching), 62 use plain
`getString` and 13 come from XML. Only the six `tilt_shift` strings are Chinese, and they are plain too. Checked
by code only; not on screen, because that needs auto keystone to run.

The other 28 apps with 15 or more Chinese strings were checked by code only. The most replaceable strings are in
`doubanfm` (137), `home` (122), `user` (65), `stream.video` (55), `smartaccessories` (44) and `bootwizard`
(34): services of the Chinese ecosystem (music, account and VIP, cloud backup, curtain, kids mode). None was
translated. Conclusion: extending the skin to these apps is not worth it; the only way to translate the rest of
the text without root is to draw over the screen through an accessibility service.

## Firmware quirks and pitfalls

- **Accessibility services are reset on boot.** Also, if the service's process is killed, it is
  marked as crashed and never bound again. On every start and return to home, Beam restores its own
  service through `WRITE_SECURE_SETTINGS`: it removes it from `enabled_accessibility_services`,
  pauses for ~1.5 s (the system merges two writes in a row) and adds it again. Code:
  `AccessibilityGuard.kt`.
- **During video the firmware force-stops Beam** to free memory, and the service drops out of the
  list. The only thing that brings Beam back afterwards is the notification listener
  (`NotificationListenerService`): the system binds it again immediately, and it restores the
  service.
- **`AF_AK` type 10 no longer starts auto keystone.** The command reaches SystemUI, but the ToF
  measurement doesn't start. Only `ProjectorFocusManager.newAutoKst(9)` works.
- **The number of the AI picture mode**: reading returns 10, writing requires 16.
- **A call without an exception doesn't mean the firmware accepted the value.** The `gmpf`
  managers take a refusal silently. The only reliable check is reading the value back, and the
  reason for a refusal is in logcat (`XGIMI_ZOOM_CTL`, `GM_DISP_TVCTRL`, `[LDM ERROR]`).
- **Noise reduction, MEMC, colour temperature, the four sliders and local contrast** are written,
  but the firmware applies them only in the custom picture mode (3). In the other modes the write
  is ignored and the read returns the old value.
- **The `changePictureMode` intent** leaves XGIMI's picture page open behind the video. A direct
  `MstPictureManager` call doesn't.
- **The time zone** defaults to Asia/Shanghai, and there is no SIM to detect it. `restore.ps1`
  turns auto-detection off and sets the zone with `service call alarm 3 s16 <zone>`.
- **`persist.*` system properties** can't be written by an app. Only
  `XgimiCommonManager.setSystemProperties` helps, since the service runs as system.
- **A marquee in a full-screen overlay** kept ~40% CPU in Beam and ~17% in SurfaceFlinger: the
  overlay was redrawn constantly on top of the video. Animations over video must be stopped.
- **An overlay window that stays registered without a surface takes the input focus**, and the
  remote stops working until a reboot. Before removing the window, make it `FLAG_NOT_FOCUSABLE` and
  remove it through the same `WindowManager`, and close it in `onUnbind` while the token is still
  valid.

## Permissions granted over adb

`tools/restore.ps1` does all of this:

| Permission | What for |
|---|---|
| `pm grant … WRITE_SECURE_SETTINGS` | restoring the accessibility service |
| `pm grant … READ_TV_LISTINGS` | channels from other apps that the stock launcher hasn't approved |
| `pm grant … ACCESS_FINE_LOCATION` | searching for Bluetooth devices |
| `appops set … WRITE_SETTINGS allow` | the screensaver delay, click sounds |
| `appops set … GET_USAGE_STATS allow` | the order of the tiles |
| `cmd notification allow_listener …/MediaListener` | "Now playing" and restarting after a force stop |
| `settings put secure enabled_accessibility_services …` | the panel over apps and the voice button |
| `cmd package set-home-activity …` | Beam as the home screen |

## How to research further

`AdbCommandReceiver` has debug commands (available only from adb shell: protected by the `DUMP`
permission). A universal call of any `gmpf` getter:

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "DisplayManager.getHumanDetectOnOff"
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "GmTvManager.getHdmiConnectStatus:1"
```

The arguments are integers separated by commas. They are cast to `byte` or `boolean` if the method
requires it. The class may be given by its full name (`com.xgimi.video.MstPictureManager.getColorTemp`).

Ready-made state snapshots:

| Command | What it shows |
|---|---|
| `--ez kst_get true` | keystone corners, offsets, the zoom range |
| `--ez pic_get true` / `pic_items` / `pic_adv` | the picture mode and parameters |
| `--ez sound_get true` | the sound output and the connected devices |
| `--ez hdmi_get true` | HDMI, boot into HDMI, CEC, the list of inputs |
| `--ez sensors_get true` | sensors |
| `--ez game_get true` | game mode |
| `--ez bt_list true` | paired Bluetooth devices |
| `--ez lumens true` | light source brightness |

The easiest way to find the mode numbers is to switch the value on XGIMI's stock page and read it
with a getter through `gmpf`. That's how the sound modes and the game mode levels were found.
