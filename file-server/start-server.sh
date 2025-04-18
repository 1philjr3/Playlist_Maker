#!/bin/bash

# Указываем порт сервера
SERVER_PORT=3000

# Проверяем аргументы командной строки
USE_TUNNEL=false
for arg in "$@"
do
    if [ "$arg" == "--tunnel" ] || [ "$arg" == "-t" ]; then
        USE_TUNNEL=true
    fi
done

# Остановка предыдущего экземпляра сервера, если запущен
echo "Остановка предыдущего экземпляра сервера..."
pkill -f "node server.js" || true
pkill -f "node tunnel.js" || true

# Выводим информацию о режиме запуска
if [ "$USE_TUNNEL" = true ]; then
    echo "🌐 Запуск сервера с публичным доступом через туннель"
else
    echo "🏠 Запуск сервера в локальном режиме"
fi

# Получаем список всех подключенных устройств
echo "Настройка adb port forwarding для устройств..."
DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | cut -f1)

# Для каждого устройства настраиваем перенаправление портов
for DEVICE in $DEVICES
do
    echo "Настройка port forwarding для устройства $DEVICE"
    adb -s $DEVICE reverse tcp:$SERVER_PORT tcp:$SERVER_PORT
done

# Если не найдено ни одного устройства, выводим предупреждение
if [ -z "$DEVICES" ]
then
    echo "⚠️ Предупреждение: Устройства не обнаружены. Убедитесь, что устройство подключено и USB-отладка включена"
else
    echo "✅ Port forwarding успешно настроен для всех подключенных устройств"
fi

# Переходим в директорию скрипта
cd "$(dirname "$0")"

# Проверяем наличие необходимых пакетов и устанавливаем их при необходимости
echo "Проверка зависимостей..."
npm install

# Запуск сервера
echo "Запуск сервера на порту $SERVER_PORT..."
if [ "$USE_TUNNEL" = true ]; then
    # Запускаем туннель в фоновом режиме
    node tunnel.js &
    TUNNEL_PID=$!
    
    # Ждем немного для запуска туннеля
    sleep 3
    
    # Запускаем сервер
    node server.js
    
    # Останавливаем туннель при выходе
    kill $TUNNEL_PID
else
    # Запускаем только сервер
    node server.js
fi

# Этот код выполнится только если сервер завершил работу
echo "Сервер остановлен" 