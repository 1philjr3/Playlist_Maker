#!/bin/bash

# Указываем порт сервера
SERVER_PORT=8080
LOCAL_IP="172.17.3.66"
EXTERNAL_PORT=8080

# Остановка предыдущего экземпляра сервера, если запущен
echo "Остановка предыдущего экземпляра сервера..."
pkill -f "node server.js" || true
pkill -f "node tunnel.js" || true

# Переходим в директорию скрипта
cd "$(dirname "$0")"

# Проверяем наличие необходимых пакетов и устанавливаем их при необходимости
echo "Проверка зависимостей..."
npm install

# Удаляем старые конфигурации ngrok
rm -f ngrok_config.json

# Определяем локальный IP-адрес
if [ -z "$LOCAL_IP" ]; then
    LOCAL_IP=$(hostname -I | awk '{print $1}')
    if [ -z "$LOCAL_IP" ]; then
        echo "❌ Не удалось определить локальный IP-адрес!"
        LOCAL_IP="localhost"
    fi
fi
echo "✅ Используется локальный IP: $LOCAL_IP"

# Настраиваем конфигурацию сервера для локального подключения
echo '{"public_url": "http://'$LOCAL_IP':'$SERVER_PORT'"}' > server_config.json
echo "✅ Файл конфигурации создан с URL: http://$LOCAL_IP:$SERVER_PORT"

echo -e "\n===== ИНФОРМАЦИЯ О СЕРВЕРЕ =====\n"
echo "🖥️ Локальный IP: $LOCAL_IP"
echo "🔌 Порт: $SERVER_PORT"
echo -e "\n📱 Скачивание APK: http://$LOCAL_IP:$SERVER_PORT/download"
echo "⚙️ Админка: http://$LOCAL_IP:$SERVER_PORT/admin-app"
echo -e "\n=================================\n"

# Запуск сервера с привязкой к 0.0.0.0 (все интерфейсы)
echo "Запуск сервера на порту $SERVER_PORT..."
HOST="0.0.0.0" PORT=$SERVER_PORT node server.js

# Этот код выполнится только если сервер завершил работу
echo "Сервер остановлен" 