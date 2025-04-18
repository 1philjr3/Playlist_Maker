# Постоянный доступ к серверу через DuckDNS

## Что настроено

1. **DuckDNS домен**: provitaserver.duckdns.org
2. **Проброс портов на роутере**: Внешний порт 8443 → Внутренний порт 3000

## Как запускать сервер

```bash
cd file-server
./start-duckdns.sh
```

или просто

```bash
cd file-server
node server.js
```

## URL для доступа к серверу

- **Скачивание APK**: http://provitaserver.duckdns.org:8443/download
- **Админка**: http://provitaserver.duckdns.org:8443/admin-app
- **API**: http://provitaserver.duckdns.org:8443/api/server-info

## Проверка доступности

```bash
curl http://provitaserver.duckdns.org:8443/api/server-info
```

## Обновление Android-приложения

1. Скопируйте файл `ServerUrlUpdater_DuckDNS.kt` в пакет `com.practicum.playlist_maker.network` вашего проекта, переименовав его в `ServerUrlUpdater.kt`

2. В классе `App.kt` или `MainActivity.kt` добавьте:

```kotlin
import com.practicum.playlist_maker.network.ServerUrlUpdater

// В методе onCreate:
ServerUrlUpdater.updateServerUrl(this) { serverUrl ->
    if (serverUrl != null) {
        Log.d("App", "Сервер доступен по адресу: $serverUrl")
    } else {
        Log.e("App", "Сервер недоступен")
    }
}
```

## Требования для работы

1. **Компьютер с сервером должен быть включен**
2. **Роутер должен быть настроен на проброс портов 8443 → 3000**
3. **Если ваш внешний IP меняется**, DuckDNS будет автоматически обновлять запись

## Возможные проблемы

- **Сервер недоступен**: Проверьте, что сервер запущен и роутер настроен правильно
- **Проблемы с обновлением DuckDNS**: Убедитесь, что ваш токен DuckDNS правильный
- **Брандмауэр блокирует подключения**: Проверьте настройки брандмауэра на компьютере

## Продвинутые настройки

### Настройка HTTPS (для большей безопасности)

1. Получите бесплатный SSL сертификат от Let's Encrypt
2. Настройте HTTPS на вашем сервере
3. Обновите URL в приложении на https://

### Автоматический запуск сервера при загрузке Mac

1. Создайте Launch Agent для автоматического запуска сервера
2. Сервер будет запускаться даже после перезагрузки компьютера 