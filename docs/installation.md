# Настройка Beam

[English version](installation.en.md) · [Возможности и совместимость](../README.md)

Установите APK подходящей сборки и выберите Beam домашним приложением в настройках Android. Для установки через ADB: `adb install -r app-release.apk`. При первом запуске выберите язык, раскладку и тему. Эти параметры также доступны в настройках Beam.

## Дополнительные функции

Сам лаунчер работает без выдачи всех дополнительных разрешений. Отдельный диалог «Beam: не хватает настроек» перечисляет недостающие разрешения для вашего устройства. Выдавайте те, которые нужны для используемых функций:

| Разрешение / доступ | Для чего |
| --- | --- |
| READ_TV_LISTINGS | Чтение каналов установленных приложений |
| Доступ к уведомлениям MediaListener | «Сейчас играет» и управление доступными медиасессиями |
| GET_USAGE_STATS | Порядок приложений по последнему использованию, если ручной порядок не задан |
| Геолокация | Получение имени сети Wi-Fi; для современных Android запрашиваются оба разрешения |

Команды из реализации диагностики Beam (выполняйте на компьютере с ADB, подключённом к устройству):

```sh
adb shell pm grant com.home.tiles android.permission.READ_TV_LISTINGS
adb shell cmd notification allow_listener com.home.tiles/com.home.tiles.MediaListener
adb shell appops set com.home.tiles GET_USAGE_STATS allow
adb shell pm grant com.home.tiles android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.home.tiles android.permission.ACCESS_FINE_LOCATION
```

Наличие разрешения не создаёт отсутствующие службы: ряды требуют ТВ-провайдера и данных от приложения. Доступ к уведомлениям также можно включить через системные настройки.

## Домашнее приложение

Если в настройках Android есть выбор домашнего приложения, используйте его. Дополнительный вариант:

```sh
adb shell cmd package set-home-activity com.home.tiles/.MainActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Проверьте и результат разрешения HOME, и само нажатие кнопки HOME. Производитель может ограничивать замену домашнего экрана. Универсальная установка не требует отключения штатных приложений, русификации прошивки или установки помощников кнопок.

`tools/restore.ps1` предназначен для прежней конфигурации XGIMI Play 6: он меняет системные настройки и службы. Не используйте его как установщик для произвольного устройства.

## Разрешение установки обновлений

Если в прошивке нет стандартного экрана разрешения установки из неизвестных источников, выдайте Beam доступ через ADB один раз. Каждую установку по-прежнему нужно подтверждать в системном установщике.

```sh
adb shell appops set com.home.tiles REQUEST_INSTALL_PACKAGES allow
```

## Обновление с прежним голосовым ассистентом

С версии 0.4.4 голосового ассистента в Beam нет. Обновление удаляет сохранённую модель и голосовые настройки, сохраняя оформление, порядок приложений и каналов. Разрешения микрофона и WRITE_SECURE_SETTINGS больше не нужны.

Если раньше Beam вручную назначался системным распознавателем речи, выберите другой доступный распознаватель в настройках Android. Если значение `adb shell settings get secure voice_recognition_service` указывает на `com.home.tiles/.VoskRecognitionService` или `com.home.tiles/com.home.tiles.VoskRecognitionService`, удалите только эту старую привязку: `adb shell settings delete secure voice_recognition_service`. На прошивке без другого распознавателя голосовой ввод приложений будет недоступен.
