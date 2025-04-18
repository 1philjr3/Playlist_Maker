# Используем базовый образ Node.js
FROM node:18-alpine

# Создаем рабочую директорию
WORKDIR /app

# Копируем файлы зависимостей
COPY package*.json ./

# Устанавливаем зависимости
RUN npm install

# Копируем исходный код
COPY . .

# Создаем директории для хранения файлов, если их нет
RUN mkdir -p uploads/pdfs uploads/images

# Экспортируем порт
EXPOSE 3000

# Аргумент для режима запуска (локальный или с туннелем)
ARG USE_TUNNEL=false
ENV USE_TUNNEL=$USE_TUNNEL

# Скрипт запуска
CMD if [ "$USE_TUNNEL" = "true" ]; then \
      node tunnel.js & \
      sleep 3 && \
      node server.js; \
    else \
      node server.js; \
    fi
