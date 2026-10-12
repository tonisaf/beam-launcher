# Beam

Android TV лаунчер с раскладками Switch и Google TV. Интерфейс: русский и английский.

## Возможности

- Приложения, избранное, USB-накопители и каналы установленных приложений.
- «Продолжить просмотр», «Сейчас играет», состояние сети и заряд батареи (API XGIMI на проекторе, стандартный API Android на других устройствах).
- Настройки Beam на главном экране: тема, фон XMB, язык, раскладка, каналы, звуки и голос.
- Офлайн голосовой поиск Vosk, голосовой ввод в приложениях, команды запуска приложений и управления воспроизведением.

Панель поверх приложений и управление XGIMI удалены: изображение, звук проектора, фокус,
трапеция, HDMI, датчики, питание и таймер сна. Beam больше не перехватывает кнопку настроек
пульта и не закрывает штатные настройки. Короткое нажатие голосовой кнопки не открывает панель.
Удержание сохраняет голосовой ввод. Настройки Android открываются через стандартный intent.

Состояние до удаления сохранено в [архивной ветке](https://github.com/tonisaf/beam-launcher/tree/codex/archive-xgimi-2026-10-13).
Инструкции XGIMI в `docs/xgimi-firmware.md`, `tools/restore.ps1`, `skin/` и `russifier/`
относятся к прежней интеграции; они не подтверждают поведение текущего лаунчера на устройстве.

## Установка

Установите собранный APK и назначьте Beam домашним приложением. Список необходимых
разрешений и команд ADB доступен в диалоге первоначальной настройки. Модель голоса ставится отдельно.
Сборка по-прежнему содержит нативные библиотеки для `armeabi-v7a`; другие ABI пока не проверены.

### Голосовая модель

Модель Vosk (~45 МБ) не входит в APK и сама не скачивается: без неё голосовое распознавание недоступно. Поставьте её по adb один раз (нужно разрешение микрофона, `restore.ps1` его выдаёт):

```sh
# проектор скачивает модель сам (только https; для проверки добавьте --es voice_sha256 <SHA-256 архива>):
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver \
    --es voice_url https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
# или залейте файл с компьютера (см. `VoiceModelProvider` в `Voice.kt`):
adb exec-in "content write --uri content://com.home.tiles.voicemodel/model.zip" < vosk-model-small-ru-0.22.zip
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --ez voice_install true
```

Голосовые команды открывают SmartTube, Spotify и Jellyfin (любую из известных сборок, какая
установлена; не установленные не распознаются). Свои приложения добавляются по adb:

```sh
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver \
    --es voice_app "Kodi|org.xbmc.kodi|коди;открой коди"   # имя | пакеты через запятую | фразы через ;
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --ez voice_apps true          # список добавленных
adb shell am broadcast -n com.home.tiles/.AdbCommandReceiver --es voice_app_remove Kodi    # убрать
```

Модель Vosk русская, поэтому фразы пишите по-русски (так, как их произносят). Язык интерфейса
на голос не влияет: команды всегда русские.

Beam отвечает и на стандартные запросы распознавания речи от других приложений
(`RECOGNIZE_SPEECH`, `RecognitionService`). Поэтому любое установленное приложение может получить
расшифровку того, что вы говорите в микрофон пульта, пока вы держите голосовую кнопку, даже если у
самого приложения нет права на микрофон: так устроен этот механизм Android. Без зажатой кнопки
запись не идёт. Если это неприемлемо, не назначайте Beam распознавателем и не ставьте модель.

Проверка: `--ez voice_status true`. Чтобы кнопки микрофона в других приложениях (например, SmartTube)
тоже использовали Beam, назначьте его системным распознавателем (по желанию):
`adb shell settings put secure voice_recognition_service com.home.tiles/com.home.tiles.VoskRecognitionService`.


## Сборка

Нужны JDK 17 и Android SDK (путь в `ANDROID_HOME` или `local.properties`).

```sh
./gradlew :app:assembleRelease :stub:assembleRelease
```

APK появятся в `app/build/outputs/apk/release/` и `stub/build/outputs/apk/*/release/`.

### Подпись

Релизы подписаны постоянным ключом, он лежит вне репозитория. Благодаря этому новую версию можно
ставить поверх старой (`adb install -r`, настройки сохраняются). Gradle берёт ключ из
`~/.beam/release.properties` (или из файла, путь к которому в `BEAM_RELEASE_PROPS`):

```properties
storeFile=C:/Users/you/.beam/beam-release.jks
storePassword=...
keyAlias=beam
keyPassword=...
```

Ключ создаётся один раз: `keytool -genkeypair -alias beam -keyalg RSA -keysize 4096 -validity 36500
-keystore beam-release.jks`. Храните копию: потерянный ключ значит, что у всех придётся удалить Beam и
поставить заново. Без этого файла (например, в CI) сборка подписывается debug-ключом компьютера:
она ставится, но не обновит Beam, подписанный настоящим ключом, и наоборот. Отпечаток SHA-256
сертификата релизов: `B0:A3:37:70:07:0C:3C:84:00:29:E1:D4:B3:4F:F3:CF:05:F0:A3:4B:97:41:7D:62:22:38:3E:95:9E:8C:20:F9`.


## Лицензия

MIT. See [LICENSE](LICENSE).
