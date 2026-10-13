# Прошивка XGIMI Play 6: как Beam вызывает стоковые функции

> **Архивный документ.** Здесь описана прежняя интеграция Beam с XGIMI, сохранённая в [архивной ветке](https://github.com/tonisaf/beam-launcher/tree/codex/archive-xgimi-2026-10-13). Управление проектором и панель поверх приложений удалены из универсального лаунчера. Ссылки на код ниже относятся к архивной версии. Актуальные возможности и требования — в [README](../README.md).

[English version](xgimi-firmware.en.md)

Отчёт о том, что удалось найти в прошивке XGIMI Play 6 (Android 11) и как Beam управляет проектором
без стоковой оболочки. Всё найдено разбором стоковых приложений и проверено на проекторе. Коды
режимов записаны такими, какими их передаёт настройка XGIMI.

Ничего из этого не является публичным API: на другой модели или прошивке XGIMI классы, методы и
числа могут отличаться. Код Beam обращается ко всему через рефлексию и при любой ошибке просто
отключает функцию.

## Содержание

- [Как устроены настройки XGIMI](#как-устроены-настройки-xgimi)
- [Способы вызова](#способы-вызова)
- [Изображение](#изображение)
- [Звук](#звук)
- [Bluetooth](#bluetooth)
- [HDMI и HDMI-CEC](#hdmi-и-hdmi-cec)
- [Проекция, трапеция, размер](#проекция-трапеция-размер)
- [Фокус и датчики](#фокус-и-датчики)
- [Питание](#питание)
- [Прочее](#прочее)
- [Пульт](#пульт)
- [Управление по сети: протокол приложения XGIMI](#управление-по-сети-протокол-приложения-xgimi)
- [Русский скин и другие приложения XGIMI](#русский-скин-и-другие-приложения-xgimi)
- [Особенности и грабли прошивки](#особенности-и-грабли-прошивки)
- [Права, выдаваемые через adb](#права-выдаваемые-через-adb)
- [Как исследовать дальше](#как-исследовать-дальше)

## Как устроены настройки XGIMI

- **Activity для настроек нет.** Настройки XGIMI (`com.android.newsettings`) и быстрая панель —
  это окна-оверлеи, которые рисуют сервисы. Поэтому их открывают через `startService` с нужным
  action, а не через `startActivity`.
- **Логика настроек живёт в платформенной библиотеке `com.xgimi.api`.** Она есть только на прошивке
  XGIMI и подключается в манифесте так:
  ```xml
  <uses-library android:name="com.xgimi.api" android:required="false" />
  ```
  Внутри два слоя:
  - `com.xgimi.gmpf.api.*` — менеджеры железа (`DisplayManager`, `GmTvManager`, `GmAudioManager`,
    `SystemManager`, `MotionDetectionManager`, `PowerManager`, `GmFactoryManager`,
    `ProjectorFocusManager`). Работают сразу, синглтон через `getInstance()`.
  - `com.xgimi.api.*` и `com.xgimi.video.*` (`XgimiAudioManager`, `XgimiCommonManager`,
    `MstPictureManager`) ходят по AIDL в системный сервис `com.xgimi.xgimiservice` и
    **работают только после привязки** (см. ниже).
- **Bluetooth** — отдельный класс `com.xgimi.bluetooth.XDBluetoothManager` на boot classpath
  прошивки.

## Способы вызова

### 1. Интент в сервис XGIMI

Открывает стоковые экраны и панели. Код: `Xgimi.kt`, `Actions.kt`, `SleepTimer.kt`.

| Что | Интент |
|---|---|
| Полные настройки | action `com.xgimi.settings.SETTINGS`, пакет `com.android.newsettings` |
| Страница настроек | то же + extra `data` = маршрут `Settings://…` (таблица ниже) |
| Быстрая панель (как кнопка-шестерёнка) | action `com.xgimi.misckey.MISCKEY`, пакет `com.android.newsettings` |
| Меню питания (как кнопка питания) | action `com.xgimi.action.WINODWSYSTEM` (опечатка XGIMI), пакет `com.xgimi.systemui` или `com.xgimi.shutdown` |
| Автофокус / ручной фокус / автотрапеция | action `com.xgimi.systemui.action.AF_AK`, пакет `com.xgimi.systemui`, extra `type` (1 ручной фокус, 8 автофокус, 10 автотрапеция), extra `from` = свой пакет |
| Смена режима изображения | action `com.xgimi.settings.SETTINGS`, `data` = `changePictureMode`, `pictureModeValue` = номер режима |

Маршруты для `data`:

| Страница | Маршрут |
|---|---|
| Режим изображения (с AI-настройками) | `Settings://com.xgimi.settings.image/mode` |
| Звуковой выход | `Settings://com.xgimi.settings.sound/soundOutput` |
| Bluetooth | `Settings://com.xgimi.settings.bluetooth` |
| Wi-Fi | `Settings://com.xgimi.settings.net/wifi` |
| Трапеция | `Settings://com.xgimi.settings.picture/keyStone` |
| Масштаб и сдвиг | `Settings://com.xgimi.settings.picture/zoom_displacement` |
| Поворот | `Settings://com.xgimi.settings.picture/rotate` |
| Источник HDMI (CEC, загрузка, plug-and-play) | `Settings://com.xgimi.settings.signalSource/` |
| Коррекция картинки (фокус, сброс) | `Settings://com.xgimi.settings/projection_screen` |

Типы `AF_AK` взяты из `FocusUIV2.receiveIntent` в SystemUI прошивки.

### 2. Менеджеры `com.xgimi.gmpf.api` через рефлексию

```kotlin
val cls = Class.forName("com.xgimi.gmpf.api.DisplayManager")
val dm = cls.getMethod("getInstance").invoke(null)
cls.getMethod("getDlpLumensLevel").invoke(dm)
```

Многие сеттеры принимают `byte`, а не `int`: это указано в таблицах ниже. Код: `XgimiHardware.kt`.

### 3. Сервис `com.xgimi.xgimiservice` (нужна привязка)

`XgimiAudioManager`, `XgimiCommonManager` и `MstPictureManager` работают, только если библиотека
привязалась к сервису. Стоковые настройки делают это при старте, Beam делает так же:

```kotlin
val cls = Class.forName("com.xgimi.clients.XgimiAidlServiceManager")
val instance = cls.getField("INSTANCE").get(null)
val listener = Class.forName("com.xgimi.clients.XgimiAidlServiceManager\$IAidlConnectListener")
val callback = Proxy.newProxyInstance(listener.classLoader, arrayOf(listener)) { … }
cls.getMethod("init", Context::class.java, listener).invoke(instance, appContext, callback)
```

Привязка асинхронная: первые вызовы сразу после неё могут вернуть ошибку, поэтому Beam
повторяет чтение несколько раз (`XgimiService.bind`).

Если сервис перезапустился и сообщил об этом через слушатель (имя метода с `disconnect`, `died`,
`unbind` или `lost`), `XgimiService` повторяет привязку: через 2, 4, 8 … 30 с, не больше восьми раз.
Имена методов слушателя в документации библиотеки не описаны, поэтому при первой привязке Beam пишет
их в лог (тег `XgimiService`). Можно ли вызывать `init` повторно, не проверено: если повтор не
помогает, смотрите в логе, что библиотека сообщила и что сделал повтор.

`XgimiCommonManager` работает от имени system. Через него Beam пишет системные свойства, которые
обычному приложению недоступны (`setSystemProperties(name, value)`).

### 4. Обычные настройки Android

Часть стоковых переключателей оказалась просто `Settings.System/Global/Secure`: задержка
заставки, звуки нажатий, видимость Bluetooth. Для записи нужны права, выданные через adb (см.
[ниже](#права-выдаваемые-через-adb)).

## Изображение

| Функция | Вызов | Значения |
|---|---|---|
| Яркость источника света | `DisplayManager.getDlpLumensLevel()` / `setDlpLumensLevel(byte)`, режим `getDlpLumensMode()` | 0..10 |
| Эко-режим | `SystemManager.getEcoState()` / `setEcoState(boolean)` | |
| Текущий режим изображения | `GmTvManager.getPictureMode(getCurrentInputSource())` | AI-режим возвращается как **10**, хотя настройки передают **16** |
| Сменить режим | `MstPictureManager.setPictureMode(int)` (нужна привязка); запасной путь — интент `changePictureMode` | AI 16, Кино 1, Спорт 9, ТВ 7, Пользовательский 3, Офис 25, Производительность 5 |
| Яркость, контраст, насыщенность, резкость, оттенок | `MstPictureManager.getPictureItem(item)` / `setPictureItem(item, value)` | item: 0 яркость, 1 контраст, 2 насыщенность, 3 резкость, 4 оттенок |
| Цветовая температура | `MstPictureManager.getColorTemp()` / `setColorTemp(int)` | 0 холодная, 1 нормальная, 2 тёплая |
| Шумоподавление | `MstPictureManager.getNoiseReduction()` / `setNoiseReduction(int)` | 0 выкл … 3 высокое, 4 авто |
| MEMC (сглаживание движения) | `MstPictureManager.getMfcLevel()` / `setMfcLevel(int)` | 0 выкл … 3 сильная |
| Гамма | `GmTvManager.getTvGammaLevel(source)` / `setTvGammaLevel(source, int)` | 0 = 1.8 … 4 = 2.2 … 8 = 2.6 |
| Динамический контраст | `GmTvManager.getTvDynamicContrastEnable(source)` / `setTvDynamicContrastEnable(source, boolean)` | |
| Локальный контраст | `GmTvManager.getUcdLevel(source)` / `setUcdLevel(source, int)` | 0 выкл … 3 высокий |
| HDR | `GmTvManager.getHdrEnable()` / `setHdrEnable(boolean)` | |
| Игровой режим | `DisplayManager.setGameModeType(int)`, `setGameModeState(int)`; чтение: `getGameModeProp(com.xgimi.gmpf.rp.GameModeProp)` → поля `type`, `state` | авто: type 1; вкл: type 0 + state 0; выкл: type 0 + state 1. Работает только при сигнале HDMI |
| Уровень игрового режима | `DisplayManager.setGameModeOption(int)` | 0 базовый, 3 максимальная скорость. Значение 1 (высокая частота кадров) драйвер этой модели превращает в 0. Геттера нет, Beam помнит значение сам |

Параметры `GmTvManager`, помеченные `source`, первым аргументом получают текущий вход
(`getCurrentInputSource()`), как это делает страница XGIMI.

Параметры пользовательского режима XGIMI хранит только в этом режиме. Заводские значения: всё по
50, температура 1, шумоподавление 2, MEMC 3, гамма 4, динамический контраст вкл, локальный
контраст 2, HDR вкл.

## Звук

| Функция | Вызов | Значения |
|---|---|---|
| Выбор выхода | `XgimiAudioManager.setAudioDeviceOn(device, 0)` (нужна привязка) | 0 динамик, 1 S/PDIF, 2 ARC, 3 Bluetooth |
| Текущий выход | `GmAudioManager.getAudioOutput()` | те же номера |
| Подключено ли устройство | `GmAudioManager.isAudioDeviceConnected(byte)` | |
| Авто/ручной выбор выхода | `GmAudioManager.getAudioDeviceSwitchMode()` / `setAudioDeviceSwitchMode(byte)` | 0 авто, 1 вручную |
| Звуковой режим | `GmAudioManager.getSoundeffect()` / `setSoundeffect(byte)` | AI 3, Кино 1, Музыка 2, Спорт 12, Караоке 4 |
| eARC | `GmTvManager.getEARCEnableState()` / `setEARCEnable(boolean)` | «Авто» у XGIMI = вкл |
| Звуки нажатий | `Settings.System.SOUND_EFFECTS_ENABLED` + `AudioManager.load/unloadSoundEffects()` | |

`GmAudioManager.setAudioOutput(byte)` переключает только усилитель: маршрутизация Android остаётся
прежней, и Bluetooth-колонка продолжает играть. Правильный путь — `XgimiAudioManager.setAudioDeviceOn`,
так делает `VoiceHelper.setAudioDevice` в настройках XGIMI.

## Bluetooth

Подключать аудиоустройства через обычный Android API нельзя без системных прав. Поэтому Beam
работает через сервис XGIMI:

```kotlin
val cls = Class.forName("com.xgimi.bluetooth.XDBluetoothManager")
val manager = cls.getConstructor(Context::class.java).newInstance(appContext)
```

| Функция | Вызов |
|---|---|
| Сопряжённые устройства | `getBondDevices()` → список `XDBluetoothDeviceItem` с полями `BName`, `BAddress`, `BType`, `BStatus` |
| Подключить / отключить | `connectDevice(item)` / `disConnectDevice(item)` |
| Константы | статические поля `XDBluetoothDeviceItem`: `CONNECT_STATUS_CONNECTED`, `CONNECT_STATUS_CONNECTING`, `BTYPE_A2DP`, `BTYPE_HEADSET`, `BTYPE_REMOTE_CONTROL_HID` |
| Видимость проектора | `Settings.Global` `bluetooth_discoverable` (1/0); сервис XGIMI следит за ней и применяет сам |
| Абсолютная громкость | свойство `persist.bluetooth.disableabsvol` через `XgimiCommonManager.setSystemProperties` |
| Имя проектора | обычный `BluetoothAdapter.setName()` |

Поиск XGIMI отдаёт результаты через AIDL-callback. Поэтому новые устройства Beam ищет
стандартным поиском Android (`BluetoothAdapter.startDiscovery()`), а сопрягает обычным
`createBond()`. Для поиска нужно разрешение `ACCESS_FINE_LOCATION`, его выдают через adb. После
сопряжения колонку подключают уже через `XDBluetoothManager`.

## HDMI и HDMI-CEC

| Функция | Вызов |
|---|---|
| Список входов | `TvInputManager.tvInputList`, тип `TYPE_HDMI`. Устройство, представившееся по CEC, появляется как дочерний вход порта (`parentId`) |
| Открыть вход | `Intent("com.xgimi.action.hdmiPlayer", TvContract.buildChannelUriForPassthroughInput(id))`, пакет `com.xgimi.xhplayer` |
| Что-то подключено к порту HDMI | `GmTvManager.getHdmiConnectStatus(byte порт)`, порты с 1 в порядке списка входов (Beam опрашивает все его порты; `--ez hdmi_get true` печатает `connectedPorts`) |
| Переключаться на HDMI при подключении | `SystemManager.getHdmiAutoSwitch()` / `setHdmiAutoSwitch(boolean)` |
| Загрузка сразу в HDMI | свойства `persist.sys.hdmi.bootsource` и `persist.sys.bootanim.alwayswait` = `1`/`0`, оба через `XgimiCommonManager.setSystemProperties` |
| Управление HDMI-устройствами (нужно для ARC) | `XgimiCommonManager.isHdmiCecControlEnabled()` / `setHdmiCecControlEnabled(boolean)` |
| Включение и выключение вместе с HDMI-устройством | `GmTvManager.getCecWakeUpState()`, `setCecWakeUp(boolean)` плюс `XgimiCommonManager.setHdmiCecAutoDeviceOffEnabled`, `setHdmiCecAutoWakeupEnabled` |

Обычный `ACTION_VIEW` того же URI открывает стоковое приложение Live TV из AOSP, и оно
показывает чёрный экран. Нужен именно плеер XGIMI.

Когда выключается управление CEC, страница XGIMI выключает и пробуждение по CEC. Beam
повторяет это поведение.

## Проекция, трапеция, размер

| Функция | Вызов | Значения |
|---|---|---|
| Установка проектора | `DisplayManager.getProjectorPutMode()` / `setProjectorPutMode(byte)` | 0 стол, 1 потолок, +2 обратная проекция |
| Авто по датчику наклона | `MotionDetectionManager.getAutoReverse()` / `setAutoReverse(boolean)` | |
| Тонкий наклон картинки | `SystemManager.setScreenRotation(int, float)` | 5 по часовой, 6 против; шаг 0.5° |
| Углы трапеции | `DisplayManager.getCorrectKeystone(KeyStoneFullCoordinates)` / `correctKeystone(KeyStoneFullCoordinates)` | см. ниже |
| Цифровой масштаб | `DisplayManager.zoomStart(int)`, `setDigitalZoomStep(int)`, `getCurrentZoomStep(0)`, `zoomFinish(int)` | 0 = полный размер … 32; см. «Сессия масштаба» ниже |
| Автотрапеция | `ProjectorFocusManager.getInstance().newAutoKst(9)` | 9 — как вызывают настройки |

`com.xgimi.gmpf.rp.KeyStoneFullCoordinates` содержит поле `coordinates` — сетку 9×9 точек с полями
`x`, `y` типа `short` в пикселях DLP-чипа 1920×1080. В 4-точечном режиме используются `[0][0]`
левый верхний, `[0][1]` правый верхний, `[1][0]` левый нижний и `[1][1]` правый нижний угол.
«Без коррекции» — углы в (0,0), (1919,0), (0,1079), (1919,1079).

Сдвиг картинки — это перемещение всех четырёх углов сразу. Он работает, только когда картинка
уменьшена масштабом.

### Сессия масштаба

`setDigitalZoomStep` принимается только внутри сессии: `zoomStart(1)` … `zoomFinish(1)` (1 — тип,
с которым открывает сессию стоковая страница «Масштаб и сдвиг»). Вне сессии прошивка пишет в
logcat `XGIMI_ZOOM_CTL: steps is ERR`, а вызов при этом завершается без ошибки, так что по
возвращаемому значению отказ не заметить.

Внутри сессии прошивка двигает картинку **на один шаг за вызов** в сторону заданного: вызов с
шагом 8 из нуля даёт шаг 1. Поэтому Beam повторяет вызов, пока `getCurrentZoomStep(0)` не вернёт
нужное число (на это геттер годится, в том числе после `zoomFinish`, и установленный шаг хранить
самим не нужно). Вызовы идут быстро: пять подряд занимают меньше секунды.

Открытие сессии сдвигает верхние углы коррекции примерно на (11, 6) пикселя от «без коррекции».
Точные значения возвращает «без коррекции» (запись углов).

## Фокус и датчики

| Функция | Вызов |
|---|---|
| Автофокус | интент `AF_AK`, `type` 8 |
| Ручной фокус (оверлей XGIMI, управление стрелками) | интент `AF_AK`, `type` 1 |
| Трапеция при перемещении проектора | `MotionDetectionManager.getAccTriggerAK()` / `setAccTriggerAK(boolean)` |
| Фокус при наклоне | `MotionDetectionManager.getAngTriggerAF()` / `setAngTriggerAF(boolean)` |
| Защита глаз (приглушение, когда кто-то в луче) | `DisplayManager.getHumanDetectOnOff()` / `setHumanDetectOnOff(boolean)` |
| Автотрапеция при включении | `GmFactoryManager.getPowerOnAKFlag()` / `savePowerOnAKFlag(boolean)` |

## Питание

| Функция | Вызов |
|---|---|
| Батарея | `com.xgimi.gmpf.api.PowerManager.getBatteryLevel()` (0..100), `isAdapterPowered()` |
| Меню питания | интент `com.xgimi.action.WINODWSYSTEM` |
| Выключение (standby, как с пульта) | служба доступности: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. Система пишет `Going to sleep due to power_button`, проектор уходит в ждущий режим и пропадает из сети, меню на экране нет |
| То же без службы доступности | интент `WINODWSYSTEM` в пакет `com.xgimi.shutdown`, отправленный **дважды** с паузой ~1.5 с: повторный запрос при открытом меню выключает проектор |
| Перезагрузка без меню | широковещательное сообщение `com.xgimi.systemui.OPEN_RESTART_DEVICE` с правом `com.xgimi.permission.system` (уровень `normal`, выдаётся при установке). Меню питания обрабатывает его как нажатие «Перезагрузка» |
| Системное меню питания Android | `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`: окно «Блокировка / Выключить / Перезапустить» на русском. «Выключить» здесь, скорее всего, полностью отключает питание, не проверялось |
| Мелодия при включении | `SystemManager.isPowerOnMusicEnabled()` / `enablePowerOnMusic(boolean)` |

Стандартный сервис батареи Android на проекторе — заглушка: «нет батареи», всегда 100%.
Настоящий заряд есть только в `PowerManager` XGIMI. Событий об изменении нет, поэтому Beam
опрашивает его раз в минуту.

Таймер сна XGIMI есть только в китайском меню питания. Beam держит свой таймер (`AlarmManager`) и
выключает проектор блокировкой экрана (запасной путь: меню питания).

Команда `com.xgimi.action.shutdown` с `action=com.xgimi.action.strScreenOff` экран не гасит: меню
питания только сбрасывает своё состояние. Погасить один экран, не уводя проектор в ждущий режим,
не удалось.

## Прочее

| Функция | Вызов |
|---|---|
| Задержка заставки | `Settings.System.SCREEN_OFF_TIMEOUT` (мс; «никогда» = `Int.MAX_VALUE`) |
| Сцены «Any Door» (任意门) | приложение `com.xgimi.atmosphere` 6.0.0.33: лаунчер-активити `.ScreensaverActivity`; настройки `com.xgimi.atmosphere.action.SETTING_MANAGE` / `SCREENSAVER_MANAGE` / `SCREENSAVER_CAROUSEL`. Содержит свой DreamService `.view.AmbientService`, но не выбран |
| Системная заставка (DreamService) | `com.xgimi.screensaver/.service.ScreenSaverDreamService` (6.0.1_lite), записана в `Settings.Secure.screensaver_components`; `screensaver_enabled=1` |
| Файловый менеджер | `com.xgimi.filemanager` |
| Модель, прошивка, серийный номер | свойства `ro.boot.xgimi.modelname`, `ro.build.version.incremental`, `ro.boot.serialno` |

## Пульт

| Кнопка | Код | Что происходит в прошивке и как Beam это обходит |
|---|---|---|
| Голосовая | `KEYCODE_F5` | Прошивка её не использует. Beam ловит её в службе специальных возможностей по всей системе |
| Шестерёнка (настройки) | `KEYCODE_MOVE_HOME` | Оконный менеджер XGIMI открывает быструю панель **до** того, как службы специальных возможностей видят кнопку, а отключить компонент через adb нельзя. Beam закрывает панель broadcast'ом `ACTION_CLOSE_SYSTEM_DIALOGS` (с любым `reason`, кроме собственного у XGIMI): несколько раз за ~1.5 с и сразу при появлении окна `com.android.newsettings` |
| Четыре кнопки приложений | — | Прошивка съедает их раньше всех и запускает фиксированные приложения. Beam ставит заглушки с этими именами пакетов (модуль `stub`), они передают нажатие в Beam |
| Громкость | `KEYCODE_VOLUME_UP/DOWN` | Android TV обрабатывает её в оконном менеджере до приложений, и прошивка меняет громкость до служб специальных возможностей. Когда Beam перехватывает эти кнопки (экран трапеции), он возвращает громкость обратно |

Какие приложения прошивка запускает на кнопках приложений:

| Кнопка | Пакет | Activity |
|---|---|---|
| 1 | `com.cibn.tv` | `com.youku.tv.home.activity.HomeActivity` |
| 2 | `com.ktcp.tvvideo` | `com.ktcp.video.activity.HomeActivity` |
| 3 | `com.gitvjimi.video` | `com.gala.video.app.epg.HomeActivity` |
| 4 | `com.hunantv.license` | `com.mgtv.tv.launcher.ChannelHomeActivity` |

Одни кнопки запускают приложение по компоненту, другие через `getLaunchIntentForPackage`.
Поэтому у заглушки есть и точная activity, и запись для лаунчера.

**Микрофон пульта.** Пока голосовая кнопка зажата, аудио-HAL (`telinkhid voice`, ADPCM) отдаёт
звук с микрофона пульта любому приложению через обычный `AudioRecord`. Когда кнопка отпущена,
вход молчит: встроенного микрофона нет. В первые ~0.3 с потока слышен щелчок пульта.

## Управление по сети: протокол приложения XGIMI

Разобрано официальное приложение «XGIMI» (`com.xgimi.zhushou` 4.10.32, декомпиляция jadx, класс
`com.xgimi.control.manager.ConnectManagerV2`) и проверено на Play 6. Для управления по Wi-Fi
китайский аккаунт не нужен: протокол локальный, облако и WeChat требует только интерфейс самого
приложения (`isLogin()` блокирует автоподключение и список устройств).

| Порт | Что это | Результат на Play 6 |
|---|---|---|
| UDP **16735** | Клавиши: строка `KEYPRESSES:<код>` | Работает без сопряжения, проектор отвечает `XGIMI_UCONTROL` |
| UDP 16752 | Поиск: `{"action":9998,"msgid":"1","appid":"1"}` | Порт закрыт, ответа нет |
| UDP 16750 | JSON-команды (`VcontrolCmd`: режим изображения, HDMI, запуск приложения) | Порт закрыт |
| TCP 13145 | То же и сопряжение (`AuthCode`, action 50001) | Соединение отвергнуто |
| UDP 16737 | Не упоминается в приложении | Открыт, но молчит на любые пакеты. Владельца без root не узнать |

Коды клавиш `KEYPRESSES:<код>`:

| Кнопка | Код | Кнопка | Код |
|---|---|---|---|
| Влево / Вправо | 50 / 37 | Домой / Назад | 35 / 48 |
| Вверх / Вниз | 36 / 38 | Меню | 139 |
| OK | 49 | Громкость + / − | 115 / 114 |
| Питание (выключить) | 116 | Быстрое меню | 251 |

Удержание: `KEYSSTATUS:<код>+1` при нажатии и `KEYSSTATUS:<код>+0` при отпускании (так приложение
делает долгое нажатие на стрелках, OK и Домой).

**Фокус** управляется тем же каналом: `KEYSSTATUS:253+1` … `KEYSSTATUS:253+0` в одну сторону и
`254` в другую. Проверено по logcat: мотор шагает (`MOTOR_CUR_STEP`), прошивка присылает
`electromotion focus stop`. Какая сторона ближе, а какая дальше, видно только по картинке.

Громкость проверена по `dumpsys audio`: `KEYPRESSES:115` поднял громкость динамика на 2 (16 → 18),
`114` вернул обратно.

Чего это не даёт: команды изображения и HDMI из `VcontrolCmd` идут на закрытые порты, так что по
сети их на этой прошивке не отправить. Для них остаются способы вызова из начала отчёта.

Проверочный скрипт: [`tools/xgimi_probe.py`](../tools/xgimi_probe.py) (`search`, `key`, `cmd`, `tcp`).

## Русский скин и другие приложения XGIMI

Перевод стоковых экранов без root держится на «скине». В каждое приложение XGIMI встроен
`com.xgimi.gimiskin.sdk.SkinEngine`: он загружает APK, названный в свойстве
`persist.sys.xgimi.themeV2.url`, в отдельный `AssetManager` и подменяет строки **по имени ресурса**.
Это не RRO и не слияние по id. Скин (только ресурсы, без кода) лежит в `skin/` отдельного проекта [xgimi-russifier](https://github.com/tonisaf/xgimi-russifier) и собирается `skin/build.ps1`.

Подмена идёт в двух местах:
- `DynamicResource.getString(id)` в библиотеке cell. Её вызывает `TextComponent.setText(int)`. Если имени нет в скине,
  берётся строка самого приложения.
- `SkinEngine.getString(int)` в кнопках и диалогах XGIMI.

`SkinFactory` при разборе XML подменяет цвета, картинки и стили, но не `android:text`. Текст, взятый через
`Context.getString`, `TextView.setText(int)` или из XML, скин не меняет. Поэтому движок есть почти во всех
приложениях, а работает по тексту далеко не везде. Левая колонка настроек, плитки быстрой панели и меню
питания скином не переводятся.

**Как проверить приложение, не переводя его.** Собрать пробный скин, где каждая китайская строка
приложения равна `RU|` плюс оригинал, подставить его в `themeV2.url`, остановить приложение
(`am force-stop`) и открыть. Маркер на экране: текст идёт через скин. Нет маркера: перевод ничего не даст.
Контроль: те же маркеры в приложении настроек, где скин заведомо работает. После проверки вернуть
свойство на настоящий скин. Статический разбор smali показывает лишь верхнюю границу: он посчитал
`current_system_version` подменяемой, а на экране «当前版本» остался китайским.

Проверено (число китайских строк по умолчанию):

| Приложение | Строк | Через скин (по коду) | Нет в скине | На устройстве |
|---|---|---|---|---|
| Обновление системы `upgrade` | 106 | 5 | 1 | главный экран без маркеров |
| Беспроводная трансляция `wirelessscreen` | 190 | 6 | 3 | главный экран без маркеров |
| Файловый менеджер `filemanager` | 23 | 4 | 0 | главный экран без маркеров |
| HDMI-плеер `xhplayer` | 25 | 4 | 0 | не запускался |
| Автотрапеция `autokst` | 131 | 0 (движок не вызывается) | 0 | не запускался: это калибровка, она может менять заводские данные |
| Аксессуары `ota.accessories` | 189 | 31 | 27 | не запускался: диалоги обновления шторы, колонки и HDMI-донгла |

Приложение `autokst` не то, что видит пользователь: это заводская станция калибровки (гироскоп, прогрев,
позиции проекции), движка скина в нём нет. Сообщения настоящей автотрапеции рисует SystemUI, и по умолчанию
они на английском («Auto-focusing, please wait», «Automatic correction completed»). Из 114 связанных строк
через скин проходят 14 (диалог определения среды и подбор цвета стены), 62 берутся обычным `getString`, 13 из
XML. Китайскими остаются только шесть строк про `tilt_shift`, и они тоже обычные. Проверено по коду; на экране
не проверялось, потому что для этого нужно запустить автотрапецию.

Остальные 28 приложений с 15 и более китайскими строками разобраны только по коду. Больше всего подменяемых
строк у `doubanfm` (137), `home` (122), `user` (65), `stream.video` (55), `smartaccessories` (44),
`bootwizard` (34): это сервисы китайской экосистемы (музыка, аккаунт и VIP, облачная копия, шторы,
детский режим). Ни одно не переведено. Вывод: расширять скин на эти приложения не стоит; единственный
способ перевести остальной текст без root — рисовать поверх экрана через службу доступности.

## Особенности и грабли прошивки

- **Службы специальных возможностей сбрасываются при загрузке.** Кроме того, если процесс службы
  убит, она помечается как упавшая и больше не привязывается. Beam при каждом старте и возврате
  домой восстанавливает свою службу через `WRITE_SECURE_SETTINGS`: убирает её из
  `enabled_accessibility_services`, делает паузу ~1.5 с (две записи подряд система склеивает) и
  добавляет снова. Код: `AccessibilityGuard.kt`.
- **Во время видео прошивка принудительно останавливает Beam** (force stop) ради памяти, и служба
  выпадает из списка. Единственное, что после этого поднимает Beam, — слушатель уведомлений
  (`NotificationListenerService`): система сразу привязывает его заново, и он возвращает службу.
- **`AF_AK` type 10 больше не запускает автотрапецию.** Команда доходит до SystemUI, но
  ToF-измерение не стартует. Работает только `ProjectorFocusManager.newAutoKst(9)`.
- **Номер AI-режима изображения**: чтение отдаёт 10, запись требует 16.
- **Вызов без исключения не значит, что прошивка приняла значение.** Менеджеры `gmpf` принимают
  отказ молча. Надёжно только прочитать значение обратно, а причину отказа искать в logcat
  (`XGIMI_ZOOM_CTL`, `GM_DISP_TVCTRL`, `[LDM ERROR]`).
- **Шумоподавление, MEMC, цветовая температура, четыре ползунка и локальный контраст**
  записываются, но прошивка применяет их только в пользовательском режиме изображения (3). В
  остальных режимах запись игнорируется, а чтение возвращает старое значение.
- **Интент `changePictureMode`** оставляет открытой страницу изображения XGIMI за видео. Прямой
  вызов `MstPictureManager` этого не делает.
- **Часовой пояс** по умолчанию Asia/Shanghai, SIM для определения нет. `restore.ps1` отключает
  автоопределение и ставит пояс через `service call alarm 3 s16 <zone>`.
- **Системные свойства `persist.*`** приложение писать не может. Помогает только
  `XgimiCommonManager.setSystemProperties`, так как сервис работает от имени system.
- **Бегущая строка в полноэкранном оверлее** держала ~40% CPU в Beam и ~17% в SurfaceFlinger:
  оверлей постоянно перерисовывался поверх видео. Анимации поверх видео нужно останавливать.
- **Окно оверлея, которое осталось зарегистрированным без поверхности, забирает фокус ввода**, и
  пульт перестаёт работать до перезагрузки. Перед удалением окно нужно сделать
  `FLAG_NOT_FOCUSABLE` и удалять через тот же `WindowManager`, а при `onUnbind` закрывать, пока
  токен ещё действителен.

## Права, выдаваемые через adb

Всё это делает `tools/restore.ps1`:

| Право | Зачем |
|---|---|
| `pm grant … WRITE_SECURE_SETTINGS` | восстановление службы специальных возможностей |
| `pm grant … READ_TV_LISTINGS` | каналы других приложений, которые стоковый лаунчер не одобрил |
| `pm grant … ACCESS_FINE_LOCATION` | поиск Bluetooth-устройств |
| `appops set … WRITE_SETTINGS allow` | задержка заставки, звуки нажатий |
| `appops set … GET_USAGE_STATS allow` | порядок плиток |
| `cmd notification allow_listener …/MediaListener` | «Сейчас играет» и перезапуск после force stop |
| `settings put secure enabled_accessibility_services …` | панель поверх приложений и голосовая кнопка |
| `cmd package set-home-activity …` | Beam как домашний экран |

## Как исследовать дальше

У `AdbCommandReceiver` есть отладочные команды (доступны только из adb shell: защищены
разрешением `DUMP`). Универсальный вызов любого геттера `gmpf`:

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "DisplayManager.getHumanDetectOnOff"
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es gmpf "GmTvManager.getHdmiConnectStatus:1"
```

Аргументы — целые числа через запятую. Они приводятся к `byte` или `boolean`, если метод этого
требует. Класс можно указать полным именем (`com.xgimi.video.MstPictureManager.getColorTemp`).

Готовые снимки состояния:

| Команда | Что показывает |
|---|---|
| `--ez kst_get true` | углы трапеции, смещения, диапазон масштаба |
| `--ez pic_get true` / `pic_items` / `pic_adv` | режим и параметры изображения |
| `--ez sound_get true` | выход звука и подключённые устройства |
| `--ez hdmi_get true` | HDMI, загрузка в HDMI, CEC, список входов |
| `--ez sensors_get true` | датчики |
| `--ez game_get true` | игровой режим |
| `--ez bt_list true` | сопряжённые Bluetooth-устройства |
| `--ez lumens true` | яркость источника света |

Числа режимов удобнее всего находить так: переключать значение на стоковой странице XGIMI и
читать его геттером через `gmpf`. Так были получены звуковые режимы и уровни игрового режима.
