# Используем базовый образ Node.js
FROM node:14

# Создаем рабочую директорию
WORKDIR /app

# Копируем package.json и package-lock.json
COPY package*.json ./

# Устанавливаем зависимости
RUN npm install

# Копируем исходный код
COPY . .

# Создаем папку для загрузок
RUN mkdir -p /app/uploads

# Открываем порт 3000
EXPOSE 3000

# Запускаем сервер
CMD ["npm", "start"]
