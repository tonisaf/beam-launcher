# Выпуск обновления Beam / Release procedure

1. Увеличьте `versionName` и `versionCode` в `app/build.gradle.kts`. Тег должен соответствовать versionName: например, `v0.5` для `0.5`.
2. Используйте постоянный релизный ключ из `BEAM_RELEASE_PROPS` или `~/.beam/release.properties`. Debug-подпись не обновит официальный APK.
3. Соберите и проверьте:

```sh
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease -PlintStrict
```

4. Проверьте APK на устройстве. Сохраните коммит и запушьте его; релиз должен ссылаться на точный проверенный коммит.
5. Подготовьте заметки о версии и SHA256SUMS.txt для `app-release.apk`.
6. Создайте draft Release, загрузите APK и контрольную сумму. Проверьте имя `app-release.apk`, размер и поле `digest` файла в GitHub API. После этого публикуйте стабильный релиз как latest.
7. На предыдущей версии Beam проверьте полный путь: поиск версии → загрузка → проверка → системный установщик → новый versionCode. Настройки должны сохраниться.

Updater contract: public latest stable GitHub Release in `tonisaf/beam-launcher`, numeric version tag, uploaded `app-release.apk` asset with a SHA-256 digest. Both the tag version and APK versionCode must increase. The installed signing certificate must match. No GitHub token is shipped in the app. The current APK targets Android 8+ and includes AndroidX graphics libraries for armeabi-v7a, arm64-v8a, x86 and x86_64.

The GitHub build workflow produces debug-signed artifacts when the private release key is absent. Do not publish those as official updater APKs. The release key stays outside Git and is never uploaded with release assets.
