const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const os = require('os');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');
const db = require('./database');
const http = require('http');
const WebSocket = require('ws');
const axios = require('axios');
const admin = require('firebase-admin');

// Инициализация Firebase Admin SDK
const serviceAccount = require('./provitaproject-f0a83-firebase-adminsdk-fbsvc-bd1a1506ab.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const app = express();
const PORT = process.env.PORT || 3000;

// Объявляем переменные, которые могут быть переопределены позже
let PUBLIC_URL = 'https://provita.spb.ru';
let SERVER_IP = 'provita.spb.ru';
let SERVER_URL;

// Настройка CORS - разрешаем доступ только с домена provita.spb.ru
app.use(cors({
    origin: function(origin, callback) {
        if (!origin || origin === PUBLIC_URL) {
            callback(null, true);
        } else {
            callback(new Error('Not allowed by CORS'));
        }
    },
    credentials: true
}));

// Проверка домена
app.use((req, res, next) => {
  const host = req.get('host');
  const referer = req.get('referer');
  
  // Разрешаем запросы только с домена provita.spb.ru
  if (host && !host.includes('provita.spb.ru') && 
      referer && !referer.includes('provita.spb.ru')) {
    console.log(`Blocked request from unauthorized domain: ${host}`);
    return res.status(403).json({ error: 'Access denied' });
  }

  // Добавляем заголовки для предотвращения кэширования ответов
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Expires', '0');
  
  // Добавляем заголовки CORS
  res.setHeader('Access-Control-Allow-Origin', 'https://provita.spb.ru');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, Admin-Key, X-Requested-With');
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  
  next();
});

// Создаем HTTP сервер
const server = http.createServer(app);

// Создаем WebSocket сервер
const wss = new WebSocket.Server({ 
  server,
  verifyClient: (info, callback) => {
    const host = info.req.headers.host;
    if (host && host.includes('provita.spb.ru')) {
      callback(true);
    } else {
      console.log(`Blocked WebSocket connection from unauthorized domain: ${host}`);
      callback(false, 403, 'Access denied');
    }
  }
});

// Хранилище активных WebSocket соединений по userId
const wsConnections = new Map();

// Хранилище FCM токенов по userId
const fcmTokens = new Map();

// Обработка WebSocket соединений
wss.on('connection', (ws, req) => {
  const url = new URL(req.url, 'http://localhost');
  const userId = url.searchParams.get('userId');

  if (userId) {
    console.log(`WebSocket connected: userId=${userId}`);
    wsConnections.set(userId, ws);

    ws.on('close', () => {
      console.log(`WebSocket disconnected: userId=${userId}`);
      wsConnections.delete(userId);
    });
  } else {
    console.log('WebSocket connection rejected: missing userId');
    ws.terminate();
  }
});

// Функция для отправки уведомления через WebSocket
function sendNotification(userId, data) {
  const ws = wsConnections.get(userId);
  if (ws && ws.readyState === WebSocket.OPEN) {
    console.log(`Sending notification to userId=${userId} via WebSocket: ${JSON.stringify(data)}`);
    ws.send(JSON.stringify(data));
    return true;
  }

  // Если WebSocket не доступен, пробуем отправить через FCM
  const fcmToken = fcmTokens.get(userId);
  if (fcmToken) {
    sendFcmNotification(userId, fcmToken, data);
    return true;
  }

  return false;
}

// API для обновления FCM токена пользователя
app.post('/api/users/fcm-token', express.json(), async (req, res) => {
  try {
    const { userId, fcmToken, deviceInfo, appVersion, timestamp } = req.body;

    console.log(`Received FCM token registration: userId=${userId}, token=${fcmToken ? fcmToken.substring(0, 15) + '...' : 'null'}`);
    console.log(`Device info: ${JSON.stringify(deviceInfo || 'not provided')}`);
    console.log(`App version: ${appVersion || 'not provided'}`);

    if (!userId || !fcmToken) {
      return res.status(400).json({ error: 'userId and fcmToken are required' });
    }

    // Сохраняем токен в памяти для быстрого доступа
    fcmTokens.set(userId, fcmToken);

    // Сохраняем токен в файле для долгосрочного хранения
    const saveTimestamp = timestamp || new Date().toISOString();
    savedFcmTokens[userId] = {
      token: fcmToken,
      lastUpdated: saveTimestamp,
      deviceInfo: deviceInfo || null,
      appVersion: appVersion || null
    };

    // Записываем в файл
    fs.writeFileSync(fcmTokensFile, JSON.stringify(savedFcmTokens, null, 2));

    console.log(`FCM token updated and saved for user ${userId} at ${saveTimestamp}`);

    // Отправляем тестовое уведомление для проверки
    sendTestNotification(userId, fcmToken);

    res.status(200).json({
      success: true,
      message: 'FCM token updated successfully',
      server_url: PUBLIC_URL
    });
  } catch (error) {
    console.error('Error updating FCM token:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// API для тестовой отправки уведомлений
app.post('/api/notifications/test', express.json(), async (req, res) => {
  try {
    const { userId, token, title, body, data } = req.body;

    console.log(`Received test notification request: ${JSON.stringify(req.body)}`);

    if (!token) {
      return res.status(400).json({ error: 'token is required' });
    }

    // Создаем объект сообщения
    const message = {
      notification: {
        title: title || 'Тест уведомлений',
        body: body || 'Проверка настройки уведомлений'
      },
      data: {
        ...(data || {}),
        timestamp: Date.now().toString(),
        type: 'test_notification'
      },
      token: token
    };

    try {
      console.log(`Sending test FCM notification to token: ${token.substring(0, 15)}...`);
      const response = await admin.messaging().send(message);
      console.log('Test notification sent successfully:', response);
      return res.json({ success: true, message: 'Test notification sent' });
    } catch (fcmError) {
      console.error('Error sending test FCM notification:', fcmError);
      return res.status(500).json({ success: false, error: fcmError.message });
    }
  } catch (error) {
    console.error('Error in test notification endpoint:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Функция для отправки тестового уведомления
function sendTestNotification(userId, token) {
  if (!token) {
    console.log(`Cannot send test notification: missing token for user ${userId}`);
    return;
  }

  try {
    console.log(`=========== SENDING TEST NOTIFICATION ===========`);
    console.log(`Target user ID: ${userId}`);
    console.log(`FCM token: ${token}`);

    const currentTimestamp = Date.now().toString();

    // Создаем объект сообщения с полными данными
    const message = {
      notification: {
        title: 'Успешная регистрация - Уведомления настроены и работают!',
        body: 'Если вы видите это сообщение, значит уведомления работают корректно.'
      },
      data: {
        type: 'test_notification',
        title: 'Успешная регистрация - Уведомления настроены и работают!',
        body: 'Если вы видите это сообщение, значит уведомления работают корректно.',
        timestamp: currentTimestamp,
        priority: 'high',
        click_action: 'FLUTTER_NOTIFICATION_CLICK'
      },
      android: {
        priority: 'high',
        notification: {
          clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          priority: 'high',
          defaultSound: true,
          defaultVibrateTimings: true
        }
      },
      apns: {
        headers: {
          'apns-priority': '10'
        },
        payload: {
          aps: {
            sound: 'default',
            badge: 1
          }
        }
      },
      token: token
    };

    console.log(`Notification message: ${JSON.stringify(message, null, 2)}`);

    admin.messaging().send(message)
      .then(response => {
        console.log(`✓ Successfully sent test message to ${userId}:`, response);
        console.log(`=================================================`);
      })
      .catch(error => {
        console.error(`✗ Error sending test message to ${userId}:`, error);
        console.log(`=================================================`);
      });
  } catch (error) {
    console.error(`Error preparing test notification for ${userId}:`, error);
  }
}

// Функция для отправки уведомления через Firebase Cloud Messaging (FCM)
async function sendFcmNotification(userId, token, notification) {
  if (!token) {
    console.log(`Missing FCM token for user ${userId}`);
    return false;
  }

  try {
    console.log(`Sending FCM notification to ${userId ? 'user ' + userId : 'token'}, notification: ${JSON.stringify(notification)}`);

    // Создаем объект сообщения
    const message = {
      notification: {
        title: notification.title || 'Новое уведомление',
        body: notification.body || 'У вас новое уведомление'
      },
      data: {
        ...(notification.data || {}),
        timestamp: Date.now().toString()
      },
      token: token
    };

    const response = await admin.messaging().send(message);
    console.log(`Successfully sent message to ${userId || token.substring(0, 10)}:`, response);
    return true;
  } catch (error) {
    console.error(`Error sending message to ${userId || token.substring(0, 10)}:`, error);

    // Если токен недействителен, удаляем его
    if (error.code === 'messaging/invalid-registration-token' ||
        error.code === 'messaging/registration-token-not-registered') {
      console.log(`Removing invalid token for user ${userId}`);

      if (userId) {
        fcmTokens.delete(userId);
        if (savedFcmTokens[userId]) {
          delete savedFcmTokens[userId];
          fs.writeFileSync(fcmTokensFile, JSON.stringify(savedFcmTokens, null, 2));
        }
      }
    }

    return false;
  }
}

// Функция для получения IP-адреса сервера
function getServerIP() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const interface of interfaces[name]) {
            // Ищем IPv4 адрес, который не является localhost
            if (interface.family === 'IPv4' && !interface.internal) {
                return interface.address;
            }
        }
    }
    return 'localhost';
}

// Получаем данные о внешнем URL, если он существует
function getPublicUrl() {
    return PUBLIC_URL;
}

// Получаем первый внешний IP-адрес и обновляем переменные
SERVER_IP = 'provita.spb.ru';
// URL для доступа
SERVER_URL = PUBLIC_URL;
// URL для публичного доступа
PUBLIC_URL = getPublicUrl();

// Выводим информацию о запуске
console.log(`\n===== СЕРВЕР ЗАПУЩЕН =====`);
console.log(`URL: ${PUBLIC_URL}`);
console.log(`=========================\n`);

// Создаем необходимые папки, если их нет
['uploads', 'uploads/pdfs', 'uploads/images', 'uploads/apk'].forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
});

// Настройка Multer для загрузки файлов
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    if (file.fieldname === 'pdf') {
      cb(null, 'uploads/pdfs/');
    } else if (file.fieldname === 'image') {
      cb(null, 'uploads/images/');
    } else if (file.fieldname === 'avatar') {
      cb(null, 'uploads/avatars/');
    } else if (file.fieldname === 'apk') {
      cb(null, 'uploads/apk/');
    } else {
      cb(new Error('Invalid field name'));
    }
  },
  filename: (req, file, cb) => {
    // Удаляем пробелы и специальные символы из имени файла
    const sanitizedName = file.originalname.replace(/[^a-zA-Z0-9.]/g, '_');
    cb(null, `${Date.now()}-${sanitizedName}`);
  }
});

const upload = multer({
  storage,
  fileFilter: (req, file, cb) => {
    if (file.fieldname === 'pdf') {
      if (file.mimetype === 'application/pdf') {
        cb(null, true);
      } else {
        cb(new Error('Only PDF files are allowed!'));
      }
    } else if (file.fieldname === 'image' || file.fieldname === 'avatar') {
      if (file.mimetype.startsWith('image/')) {
        cb(null, true);
      } else {
        cb(new Error('Only image files are allowed!'));
      }
    } else if (file.fieldname === 'apk') {
      // Для APK файлов принимаем application/vnd.android.package-archive или application/octet-stream
      if (file.mimetype === 'application/vnd.android.package-archive' || 
          file.mimetype === 'application/octet-stream' || 
          file.originalname.endsWith('.apk')) {
        cb(null, true);
      } else {
        cb(new Error('Only APK files are allowed!'));
      }
    } else {
      cb(new Error('Unexpected field'));
    }
  },
  limits: {
    fileSize: 200 * 1024 * 1024 // Увеличиваем лимит до 200MB для APK
  }
});

// Файлы для хранения данных
const directoriesFile = 'directories.json';
const filesFile = 'files.json';
const favoritesFile = 'favorites.json';
const activityHistoryFile = 'activity_history.json';
const fcmTokensFile = 'fcm_tokens.json'; // Новый файл для хранения FCM токенов

// Создаем файлы, если они не существуют
[directoriesFile, filesFile, favoritesFile, activityHistoryFile, fcmTokensFile].forEach(file => {
  if (!fs.existsSync(file)) {
    fs.writeFileSync(file, file === fcmTokensFile ? '{}' : '[]');
  }
});

// Загружаем данные о директориях
let directories = [];
try {
  directories = JSON.parse(fs.readFileSync(directoriesFile, 'utf8'));
} catch (error) {
  console.error('Error reading directories file:', error);
  directories = [];
}

// Загружаем данные о файлах
let files = [];
try {
  files = JSON.parse(fs.readFileSync(filesFile, 'utf8'));
} catch (error) {
  console.error('Error reading files file:', error);
  files = [];
}

// Загружаем данные о избранных файлах
let favorites = [];
try {
  favorites = JSON.parse(fs.readFileSync(favoritesFile, 'utf8'));
} catch (error) {
  console.error('Error reading favorites file:', error);
  favorites = [];
}

// Загружаем данные о истории действий
let activityHistory = [];
try {
  activityHistory = JSON.parse(fs.readFileSync(activityHistoryFile, 'utf8'));
} catch (error) {
  console.error('Error reading activity history file:', error);
  activityHistory = [];
}

// Загружаем данные о FCM токенах
let savedFcmTokens = {};
try {
  savedFcmTokens = JSON.parse(fs.readFileSync(fcmTokensFile, 'utf8'));

  // Заполняем Map из загруженных данных
  Object.keys(savedFcmTokens).forEach(userId => {
    if (savedFcmTokens[userId] && savedFcmTokens[userId].token) {
      fcmTokens.set(userId, savedFcmTokens[userId].token);
      console.log(`Loaded FCM token for user ${userId} from storage`);
    }
  });
} catch (error) {
  console.error('Error reading FCM tokens file:', error);
  savedFcmTokens = {};
}

// Middleware
app.use(cors({
  origin: '*', // Разрешаем запросы с любого домена
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: [
    'Content-Type',
    'Admin-Key',
    'User-Id',
    'User-Name',
    'user-name',
    'userName',
    'username'
  ]
}));

// Сохраняем последний запрос для доступа к заголовкам
app.use((req, res, next) => {
  global.lastRequest = req;
  next();
});

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('.')); // Обслуживание статических файлов из текущей директории

// Маршрут для доступа к админской странице
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'admin.html'));
});

// Маршрут для доступа к админской странице через /admin-app
app.get('/admin-app', (req, res) => {
    res.sendFile(path.join(__dirname, 'admin.html'));
});

// Маршрут для скачивания APK
app.get('/download', (req, res) => {
    const apkPath = path.join(__dirname, 'uploads', 'apk', 'latest.apk');
    if (fs.existsSync(apkPath)) {
        res.download(apkPath, 'provita.apk');
    } else {
        res.status(404).json({ error: 'APK файл не найден' });
    }
});

// Маршрут для загрузки нового APK файла
app.post('/upload/apk', upload.single('apk'), (req, res) => {
    try {
        console.log('Получен запрос на загрузку APK');
        
        if (!req.file) {
            console.log('Ошибка: APK файл не был загружен');
            return res.status(400).json({ error: 'APK файл не загружен' });
        }

        // Переименовываем загруженный файл в latest.apk
        const newPath = path.join(__dirname, 'uploads', 'apk', 'latest.apk');
        fs.renameSync(req.file.path, newPath);
        
        console.log('APK файл успешно загружен и переименован в latest.apk');
        
        res.json({ 
            success: true,
            message: 'APK успешно загружен',
            downloadUrl: `${PUBLIC_URL}/download`
        });
    } catch (error) {
        console.error('Ошибка при загрузке APK:', error);
        res.status(500).json({ 
            error: 'Ошибка сервера',
            message: error.message
        });
    }
});

// Настройка транспорта для отправки почты
const transporter = nodemailer.createTransport({
  service: 'Mail.ru',
  auth: {
    user: '123uks123@mail.ru',
    // ВАЖНО: Текущий пароль не работает!
    // 1. Войдите в аккаунт Mail.ru (123uks123@mail.ru)
    // 2. Перейдите в Настройки → Безопасность → Пароли для внешних приложений
    // 3. Создайте новый пароль приложения для "Node.js"
    // 4. Замените строку ниже на полученный пароль приложения
    pass: 'fc1x5uQkcWU9Q5qeBtXJ'   // ⚠️ Замените на новый пароль приложения Mail.ru
  }
});

// Регистрация пользователя
app.post('/register', async (req, res) => {
    try {
        const { email, name, department } = req.body;

        // Проверяем, существует ли пользователь
        const existingUser = await db.findUserByEmail(email);
        if (existingUser) {
            return res.status(400).json({ error: 'Пользователь с таким email уже существует' });
        }

        // Формируем данные для отправки на почту администратора
        const mailOptions = {
            from: '123uks123@mail.ru',
            to: '123uks123@mail.ru',
            subject: 'Новый запрос на регистрацию',
            html: `
                <h2>Новый запрос на регистрацию</h2>
                <p><strong>ФИО:</strong> ${name}</p>
                <p><strong>Отдел:</strong> ${department}</p>
                <p><strong>Почта:</strong> ${email}</p>
                <p>Для создания учетной записи добавьте пользователя в базу данных через административный интерфейс и отправьте ему данные для входа.</p>
            `
        };

        // Отправляем письмо
        transporter.sendMail(mailOptions, function(error, info) {
            if (error) {
                console.error('Ошибка при отправке почты:', error);
                // В случае ошибки отправки все равно возвращаем успешный результат клиенту
                // чтобы пользователь получил сообщение, что запрос отправлен
            }
            console.log('Email отправлен: ' + info?.response);
        });

        // Возвращаем успешный результат клиенту
        res.status(201).json({
            message: 'Запрос на регистрацию отправлен'
        });
    } catch (error) {
        console.error('Ошибка при регистрации:', error);
        res.status(500).json({ error: 'Ошибка сервера' });
    }
});

// Добавить пользователя администратором
app.post('/admin/add-user', async (req, res) => {
    try {
        const { email, name, department, password, 'admin-key': adminKey } = req.body;

        console.log('Получен запрос на добавление пользователя:', { email, name, department, adminKey });

        // Проверка административного ключа
        if (adminKey !== '1234') {
            return res.status(401).json({ error: 'Неавторизованный доступ' });
        }

        // Проверяем, существует ли пользователь
        let existingUser = await db.findUserByEmail(email);
        if (existingUser) {
            return res.status(400).json({ error: 'Пользователь с таким email уже существует' });
        }

        // Хешируем пароль
        const hashedPassword = await bcrypt.hash(password, 10);

        // Создаем нового пользователя
        const user = await db.createUser({
            email,
            password: hashedPassword,
            name,
            department,
            isApproved: true,
            createdAt: new Date().toISOString()
        });

        // Отправляем данные для входа на почту пользователя
        const mailOptions = {
            from: '123uks123@mail.ru',
            to: email,
            subject: 'Данные для входа в систему',
            html: `
                <h2>Данные для входа в систему</h2>
                <p>Ваш запрос на регистрацию в системе был одобрен.</p>
                <p><strong>Email для входа:</strong> ${email}</p>
                <p><strong>Пароль:</strong> ${password}</p>
                <p>Рекомендуем изменить пароль после первого входа.</p>
            `
        };

        // Отправляем письмо
        transporter.sendMail(mailOptions, function(error, info) {
            if (error) {
                console.error('Ошибка при отправке почты пользователю:', error);
            } else {
                console.log('Email с данными для входа отправлен: ' + info?.response);
                console.log('Отправлено на адрес:', email);
            }
            console.log('Попытка отправки письма завершена');
        });

        // Возвращаем успешный ответ в формате JSON
        return res.status(201).json({
            message: 'Пользователь успешно добавлен и данные для входа отправлены',
            user: {
                email,
                name,
                department
            }
        });
    } catch (error) {
        console.error('Ошибка при добавлении пользователя:', error);
        return res.status(500).json({
            error: 'Ошибка сервера',
            message: error.message
        });
    }
});

// Авторизация пользователя
app.post('/login', async (req, res) => {
    try {
        const { email, password } = req.body;

        // Ищем пользователя
        const user = await db.findUserByEmail(email);
        if (!user) {
            return res.status(401).json({ error: 'Неверный email или пароль' });
        }

        // Проверяем пароль
        const isValidPassword = await bcrypt.compare(password, user.password);
        if (!isValidPassword) {
            return res.status(401).json({ error: 'Неверный email или пароль' });
        }

        // Создаем JWT токен
        const token = jwt.sign(
            { userId: user.email, isAdmin: user.isAdmin },
            '123', // В продакшене используйте безопасный ключ
            { expiresIn: '24h' }
        );

        res.json({
            token,
            user: {
                email: user.email,
                name: user.name,
                department: user.department,
                isAdmin: user.isAdmin
            }
        });
    } catch (error) {
        console.error('Ошибка при авторизации:', error);
        res.status(500).json({ error: 'Ошибка при авторизации' });
    }
});

// Проверка работоспособности сервера
app.get('/health', (req, res) => {
    res.json({ status: 'ok' });
});

// Маршрут для получения актуального IP-адреса сервера
app.get('/api/server-info', (req, res) => {
    const serverInfo = {
      server_url: PUBLIC_URL,
      public_url: PUBLIC_URL,
      local_url: SERVER_URL,
      timestamp: new Date().toISOString(),
      is_public: true
    };
    
    console.log(`Запрос информации о сервере:`, serverInfo);
    res.json(serverInfo);
});

// Специальный API-эндпоинт для Android-приложения
app.get('/api/mobile/server-url', (req, res) => {
  const serverUrl = PUBLIC_URL; // Всегда используем публичный URL
  console.log(`Запрос URL сервера для мобильного приложения: ${serverUrl}`);
  res.json({
    server_url: serverUrl,
    is_public: true,
    timestamp: new Date().toISOString()
  });
});

// Тестовая отправка почты для проверки добавления пользователя
app.get('/test-admin-mail', (req, res) => {
    try {
        // Тестовые данные
        const email = '123uks123@mail.ru';
        const password = 'тестовый_пароль';

        // Отправляем тестовые данные для входа на почту
        const mailOptions = {
            from: '123uks123@mail.ru',
            to: email,
            subject: 'Тестовые данные для входа в систему',
            html: `
                <h2>Тестовые данные для входа в систему</h2>
                <p>Это тестовое сообщение для проверки добавления пользователя через админскую панель.</p>
                <p><strong>Email для входа:</strong> ${email}</p>
                <p><strong>Пароль:</strong> ${password}</p>
                <p>Тестовое сообщение, пожалуйста, игнорируйте.</p>
            `
        };

        // Отправляем письмо
        transporter.sendMail(mailOptions, function(error, info) {
            if (error) {
                console.error('Ошибка при тестовой отправке почты:', error);
                res.status(500).json({ error: 'Ошибка при отправке тестового письма', details: error.message });
            } else {
                console.log('Тестовый email отправлен: ' + info?.response);
                res.status(200).json({
                    message: 'Тестовое письмо отправлено успешно',
                    details: info?.response,
                    sendTo: email
                });
            }
        });
    } catch (error) {
        console.error('Ошибка в тестовом маршруте:', error);
        res.status(500).json({ error: 'Ошибка сервера при тестировании почты' });
    }
});

// CORS и базовые настройки
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Content-Disposition, Range');
  res.header('Access-Control-Expose-Headers', 'Content-Disposition, Content-Length, Content-Range');
  next();
});

app.options('*', (req, res) => {
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Content-Disposition, Range');
  res.status(200).send();
});

// Маршрут для загрузки изображения
app.post('/upload/image', upload.single('image'), (req, res) => {
  try {
    console.log('Получен запрос на загрузку изображения');
    
    if (!req.file) {
      console.log('Ошибка: файл не был загружен');
      return res.status(400).json({ error: 'No file uploaded.' });
    }
    
    // Сохраняем информацию о загруженном файле
    console.log('Файл успешно загружен:', {
      originalName: req.file.originalname,
      mimetype: req.file.mimetype,
      size: req.file.size,
      filename: req.file.filename,
      path: req.file.path
    });
    
    // Формируем относительный URL для доступа к файлу
    const fileUrl = `/uploads/images/${req.file.filename}`;
    
    // Формируем полный URL, включающий адрес сервера
    const fullFileUrl = `${PUBLIC_URL}${fileUrl}`;
    
    console.log('URL для доступа к изображению:', {
      relativeUrl: fileUrl,
      fullUrl: fullFileUrl
    });
    
    // Отправляем успешный ответ с URL файла
    // Важно: в fileUrl возвращаем полный URL, а не относительный путь
    res.json({ 
      success: true, 
      fileUrl: fullFileUrl, // Возвращаем полный URL вместо относительного пути
      fullUrl: fullFileUrl,
      message: 'Image uploaded successfully'
    });
  } catch (error) {
    console.error('Ошибка при загрузке изображения:', error);
    res.status(500).json({ 
      error: 'Server error',
      message: error.message
    });
  }
});

// Маршрут для загрузки аватара пользователя
app.post('/upload/avatar', upload.single('avatar'), (req, res) => {
  try {
    console.log('Получен запрос на загрузку аватара');
    
    if (!req.file) {
      console.log('Ошибка: файл аватара не был загружен');
      return res.status(400).json({ error: 'No avatar file uploaded.' });
    }
    
    // Проверяем тип файла
    const allowedTypes = ['image/jpeg', 'image/png', 'image/gif'];
    if (!allowedTypes.includes(req.file.mimetype)) {
      console.log('Ошибка: недопустимый тип файла:', req.file.mimetype);
      return res.status(400).json({ error: 'Invalid file type. Only JPEG, PNG and GIF are allowed.' });
    }
    
    // Проверяем размер файла (максимум 5MB)
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (req.file.size > maxSize) {
      console.log('Ошибка: файл слишком большой:', req.file.size);
      return res.status(400).json({ error: 'File is too large. Maximum size is 5MB.' });
    }
    
    // Сохраняем информацию о загруженном файле
    console.log('Аватар успешно загружен:', {
      originalName: req.file.originalname,
      mimetype: req.file.mimetype,
      size: req.file.size,
      filename: req.file.filename,
      path: req.file.path
    });
    
    // Формируем относительный URL для доступа к файлу
    const fileUrl = `/uploads/avatars/${req.file.filename}`;
    
    // Формируем полный URL, включающий адрес сервера
    const fullFileUrl = `${PUBLIC_URL}${fileUrl}`;
    
    console.log('URL для доступа к аватару:', {
      relativeUrl: fileUrl,
      fullUrl: fullFileUrl
    });
    
    // Отправляем успешный ответ с URL файла
    res.json({ 
      success: true, 
      fileUrl: fullFileUrl,
      fullUrl: fullFileUrl,
      message: 'Avatar uploaded successfully'
    });
  } catch (error) {
    console.error('Ошибка при загрузке аватара:', error);
    res.status(500).json({ 
      error: 'Server error',
      message: error.message
    });
  }
});

// Маршрут для загрузки PDF
app.post('/directories/:directoryId/files', upload.single('pdf'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded.' });
    }

    const directoryId = req.params.directoryId;
    const userId = req.headers['user-id'] || req.body.userId; // Получаем ID из заголовка или тела
    const userName = req.body.userName || 'Неизвестный пользователь'; // Получаем имя из тела запроса
    const shouldSendNotification = req.body.sendNotification === 'true'; // Проверяем, нужно ли отправить уведомление
    const notificationDescription = req.body.notificationDescription || ''; // Описание для уведомления

    console.log('File upload form data:', JSON.stringify(req.body));
    console.log(`Using userName from form: ${userName}, userId: ${userId}, sendNotification: ${shouldSendNotification}`);

    // Проверяем случай с root директорией
    if (directoryId === 'root') {
      const newFile = {
        id: Date.now().toString(),
        name: req.body.name || req.file.originalname,
        description: req.body.description || notificationDescription || '',
        fileUrl: `/uploads/pdfs/${req.file.filename}`,
        directoryId: 'root',
        size: req.file.size,
        createdAt: Date.now(),
        lastOpenedAt: null
      };

      files.push(newFile);
      fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

      // Создаем запись в истории напрямую, без использования функции addToHistory
      const historyEntry = {
        id: Date.now().toString(),
        userId,
        userName, // Используем имя из формы напрямую
        action: 'add',
        itemType: 'file',
        itemName: newFile.name,
        path: 'root',
        timestamp: new Date().toISOString()
      };

      console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

      activityHistory.unshift(historyEntry);
      if (activityHistory.length > 100) {
        activityHistory.pop();
      }
      fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

      // Отправляем уведомление, если требуется
      if (shouldSendNotification) {
        try {
          // Создаем данные для уведомления
          const notificationData = {
            type: 'file_notification',
            data: {
              fileId: newFile.id,
              fileName: newFile.name,
              description: notificationDescription || newFile.description || '',
              userName: userName,
              fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
              timestamp: Date.now()
            }
          };

          // 1. Отправляем через WebSocket всем подключенным пользователям
          const connectedUserIds = Array.from(wsConnections.keys());
          console.log(`Sending notification to all connected users (${connectedUserIds.length}): ${connectedUserIds.join(', ')}`);

          for (const connectedUserId of connectedUserIds) {
            if (connectedUserId !== userId) { // Не отправляем уведомление отправителю
              sendNotification(connectedUserId, notificationData);
            }
          }

          // 2. Отправляем через FCM topic всем подписанным устройствам
          try {
            const topicMessage = {
              topic: 'all',
              notification: {
                title: `Новый файл: ${newFile.name}`,
                body: notificationDescription ? `${userName}: ${notificationDescription}` : `Пользователь ${userName} добавил новый файл`
              },
              data: {
                fileId: newFile.id,
                fileName: newFile.name,
                description: notificationDescription || newFile.description || '',
                userName: userName,
                fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
                timestamp: Date.now().toString(),
                click_action: 'OPEN_FILE_ACTIVITY'
              },
              android: {
                priority: 'high',
                notification: {
                  clickAction: 'OPEN_FILE_ACTIVITY'
                }
              }
            };

            console.log('Sending FCM topic notification:', topicMessage);
            const response = await admin.messaging().send(topicMessage);
            console.log('Successfully sent FCM topic message:', response);
          } catch (fcmError) {
            console.error('Error sending FCM topic notification:', fcmError);
          }

          // 3. Отправляем через FCM индивидуально всем известным устройствам
          try {
            // Собираем все сохраненные токены
            const userTokens = new Map([...fcmTokens.entries()]);

            // Добавляем токены из файлового хранилища
            Object.keys(savedFcmTokens).forEach(tokenUserId => {
              if (!userTokens.has(tokenUserId) && savedFcmTokens[tokenUserId].token) {
                userTokens.set(tokenUserId, savedFcmTokens[tokenUserId].token);
              }
            });

            if (userTokens.size > 0) {
              console.log(`Sending individual FCM notifications to ${userTokens.size} devices`);

              for (const [tokenUserId, token] of userTokens.entries()) {
                if (tokenUserId !== userId) { // Не отправляем отправителю
                  try {
                    const individualMessage = {
                      token: token,
                      notification: {
                        title: `Новый файл: ${newFile.name}`,
                        body: notificationDescription ? `${userName}: ${notificationDescription}` : `Пользователь ${userName} добавил новый файл`
                      },
                      data: {
                        fileId: newFile.id,
                        fileName: newFile.name,
                        description: notificationDescription || newFile.description || '',
                        userName: userName,
                        fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
                        timestamp: Date.now().toString(),
                        click_action: 'OPEN_FILE_ACTIVITY'
                      },
                      android: {
                        priority: 'high',
                        notification: {
                          clickAction: 'OPEN_FILE_ACTIVITY'
                        }
                      }
                    };

                    await admin.messaging().send(individualMessage);
                  } catch (individualError) {
                    console.error(`Error sending FCM to user ${tokenUserId}:`, individualError.message);
                  }
                }
              }
            }
          } catch (fcmUsersError) {
            console.error('Error sending FCM to individual users:', fcmUsersError);
          }
        } catch (notificationError) {
          console.error('Error sending notifications:', notificationError);
        }
      }

      return res.json(newFile);
    }

    const directory = directories.find(dir => dir.id === directoryId);
    if (!directory) {
      try {
        fs.unlinkSync(req.file.path);
      } catch (error) {
        console.error('Error deleting uploaded file:', error);
      }
      return res.status(404).json({ error: 'Directory not found' });
    }

    // Получаем путь к директории
    const dirPath = getDirectoryPath(directory);

    const newFile = {
      id: Date.now().toString(),
      name: req.body.name || req.file.originalname,
      description: req.body.description || notificationDescription || '',
      fileUrl: `/uploads/pdfs/${req.file.filename}`,
      directoryId: directoryId,
      size: req.file.size,
      createdAt: Date.now(),
      lastOpenedAt: null
    };

    files.push(newFile);
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

    // Создаем запись в истории напрямую, без использования функции addToHistory
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName, // Используем имя из формы напрямую
      action: 'add',
      itemType: 'file',
      itemName: newFile.name,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    // Отправляем уведомление, если требуется
    if (shouldSendNotification) {
      try {
        // Создаем данные для уведомления
        const notificationData = {
          type: 'file_notification',
          data: {
            fileId: newFile.id,
            fileName: newFile.name,
            description: notificationDescription || newFile.description || '',
            userName: userName,
            fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
            timestamp: Date.now()
          }
        };

        // 1. Отправляем через WebSocket всем подключенным пользователям
        const connectedUserIds = Array.from(wsConnections.keys());
        console.log(`Sending notification to all connected users (${connectedUserIds.length}): ${connectedUserIds.join(', ')}`);

        for (const connectedUserId of connectedUserIds) {
          if (connectedUserId !== userId) { // Не отправляем уведомление отправителю
            sendNotification(connectedUserId, notificationData);
          }
        }

        // 2. Отправляем через FCM topic всем подписанным устройствам
        try {
          const topicMessage = {
            topic: 'all',
            notification: {
              title: `Новый файл: ${newFile.name}`,
              body: notificationDescription ? `${userName}: ${notificationDescription}` : `Пользователь ${userName} добавил новый файл`
            },
            data: {
              fileId: newFile.id,
              fileName: newFile.name,
              description: notificationDescription || newFile.description || '',
              userName: userName,
              fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
              timestamp: Date.now().toString(),
              click_action: 'OPEN_FILE_ACTIVITY'
            },
            android: {
              priority: 'high',
              notification: {
                clickAction: 'OPEN_FILE_ACTIVITY'
              }
            }
          };

          console.log('Sending FCM topic notification:', topicMessage);
          const response = await admin.messaging().send(topicMessage);
          console.log('Successfully sent FCM topic message:', response);
        } catch (fcmError) {
          console.error('Error sending FCM topic notification:', fcmError);
        }

        // 3. Отправляем через FCM индивидуально всем известным устройствам
        try {
          // Собираем все сохраненные токены
          const userTokens = new Map([...fcmTokens.entries()]);

          // Добавляем токены из файлового хранилища
          Object.keys(savedFcmTokens).forEach(tokenUserId => {
            if (!userTokens.has(tokenUserId) && savedFcmTokens[tokenUserId].token) {
              userTokens.set(tokenUserId, savedFcmTokens[tokenUserId].token);
            }
          });

          if (userTokens.size > 0) {
            console.log(`Sending individual FCM notifications to ${userTokens.size} devices`);

            for (const [tokenUserId, token] of userTokens.entries()) {
              if (tokenUserId !== userId) { // Не отправляем отправителю
                try {
                  const individualMessage = {
                    token: token,
                    notification: {
                      title: `Новый файл: ${newFile.name}`,
                      body: notificationDescription ? `${userName}: ${notificationDescription}` : `Пользователь ${userName} добавил новый файл`
                    },
                    data: {
                      fileId: newFile.id,
                      fileName: newFile.name,
                      description: notificationDescription || newFile.description || '',
                      userName: userName,
                      fileUrl: `${SERVER_URL}${newFile.fileUrl}`,
                      timestamp: Date.now().toString(),
                      click_action: 'OPEN_FILE_ACTIVITY'
                    },
                    android: {
                      priority: 'high',
                      notification: {
                        clickAction: 'OPEN_FILE_ACTIVITY'
                      }
                    }
                  };

                  await admin.messaging().send(individualMessage);
                } catch (individualError) {
                  console.error(`Error sending FCM to user ${tokenUserId}:`, individualError.message);
                }
              }
            }
          }
        } catch (fcmUsersError) {
          console.error('Error sending FCM to individual users:', fcmUsersError);
        }
      } catch (notificationError) {
        console.error('Error sending notifications:', notificationError);
      }
    }

    res.json(newFile);
  } catch (error) {
    console.error('Error handling file upload:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Получение списка файлов в директории
app.get('/directories/:directoryId/files', (req, res) => {
  try {
    const directoryId = req.params.directoryId;
    const directoryFiles = files.filter(file =>
      directoryId === 'root'
        ? file.directoryId === 'root'
        : file.directoryId === directoryId
    ).map(file => ({
      ...file,
      fileUrl: `${PUBLIC_URL}${file.fileUrl}` // Всегда используем публичный URL
    }));
    res.json(directoryFiles);
  } catch (error) {
    console.error('Error getting directory files:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обновление времени последнего открытия файла
app.put('/files/:fileId/lastOpened', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const fileIndex = files.findIndex(file => file.id === fileId);

    if (fileIndex === -1) {
      return res.status(404).json({ error: 'File not found' });
    }

    files[fileIndex].lastOpenedAt = Date.now();
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

    res.json(files[fileIndex]);
  } catch (error) {
    console.error('Error updating last opened time:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Удаление файла
app.delete('/files/:fileId', async (req, res) => {
  try {
    const fileId = req.params.fileId;
    const userId = req.headers['user-id'] || req.body.userId; // Получаем ID из заголовка или тела
    const userName = req.body.userName || req.headers['user-name'] || 'Неизвестный пользователь'; // Получаем имя из тела или заголовка

    console.log(`Delete file request: fileId=${fileId}, userId=${userId}, userName=${userName}`);

    const fileIndex = files.findIndex(file => file.id === fileId);

    if (fileIndex === -1) {
      return res.status(404).json({ error: 'File not found' });
    }

    const file = files[fileIndex];
    const directory = directories.find(dir => dir.id === file.directoryId);
    const dirPath = directory ? getDirectoryPath(directory) : 'root';
    const filePath = path.join(__dirname, file.fileUrl);

    if (fs.existsSync(filePath)) {
      try {
        fs.unlinkSync(filePath);
      } catch (error) {
        console.error('Error deleting file:', error);
      }
    }

    // Создаем запись в истории напрямую
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName,
      action: 'delete',
      itemType: 'file',
      itemName: file.name,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    files.splice(fileIndex, 1);
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

    res.json({ message: 'File deleted successfully' });
  } catch (error) {
    console.error('Error deleting file:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Перемещение файла между директориями
app.put('/files/:fileId/move', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const { newDirectoryId } = req.body;

    const fileIndex = files.findIndex(file => file.id === fileId);
    const directoryExists = newDirectoryId === 'root' || directories.some(dir => dir.id === newDirectoryId);

    if (fileIndex === -1) {
      return res.status(404).json({ error: 'File not found' });
    }

    if (!directoryExists) {
      return res.status(404).json({ error: 'Target directory not found' });
    }

    files[fileIndex].directoryId = newDirectoryId;
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

    res.json(files[fileIndex]);
  } catch (error) {
    console.error('Error moving file:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Получение списка директорий
app.get('/directories', (req, res) => {
  try {
    const { parentId } = req.query;
    let filteredDirectories = parentId
      ? directories.filter(dir => dir.parentId === parentId)
      : directories.filter(dir => dir.parentId === null);

    // Добавляем полный URL для изображений
    filteredDirectories = filteredDirectories.map(dir => ({
      ...dir,
      imageUrl: dir.imageUrl.startsWith('http') ? dir.imageUrl : `${SERVER_URL}${dir.imageUrl}`
    }));

    res.json(filteredDirectories);
  } catch (error) {
    console.error('Error getting directories:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Получение содержимого директории по пути
app.get('/directory', (req, res) => {
  try {
    const { path: dirPath } = req.query;

    console.log(`Getting content for directory path: ${dirPath}`);

    if (!dirPath) {
      return res.status(400).json({ error: 'Directory path is required' });
    }

    // Находим директорию по пути (это упрощенный пример, в реальном приложении нужно разбирать путь)
    const directory = directories.find(dir => dir.path === dirPath);

    if (!directory) {
      return res.status(404).json({ error: 'Directory not found' });
    }

    // Получаем файлы в этой директории
    const directoryFiles = files.filter(file => file.directoryId === directory.id);

    // Преобразуем в формат SearchResponse
    const searchResults = directoryFiles.map(file => ({
      name: file.name,
      path: `${PUBLIC_URL}${file.fileUrl}`, // Используем полный URL вместо относительного пути
      type: file.fileType || getFileTypeFromName(file.name),
      size: file.size || 0,
      lastModified: file.lastModifiedAt || file.createdAt || Date.now(),
      parentPath: getDirectoryPath(directory) // Добавляем путь к родительской директории
    }));

    // Получаем поддиректории
    const subdirectories = directories.filter(dir => dir.parentId === directory.id);

    // Преобразуем поддиректории в формат SearchResponse
    const directoryResults = subdirectories.map(dir => ({
      name: dir.name,
      path: dir.path || `${dirPath}/${dir.name}`.replace(/^\/+/, ''),
      type: "directory",
      parentPath: dirPath
    }));

    // Объединяем результаты
    const results = [...directoryResults, ...searchResults];

    res.json(results);
  } catch (error) {
    console.error('Error getting directory content:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Поиск файлов по имени и/или пути
app.get('/search', (req, res) => {
  try {
    const { query, path } = req.query;

    console.log(`Search request: query=${query}, path=${path}`);

    if (!query && !path) {
      return res.status(400).json({ error: 'Query or path is required' });
    }

    let searchResults = [];

    // Если указан путь, ограничиваем поиск этой директорией
    if (path) {
      const directory = directories.find(dir => dir.path === path);

      if (directory) {
        // Получаем все файлы в директории
        const directoryFiles = files.filter(file => file.directoryId === directory.id);

        // Фильтруем файлы по запросу, если он указан
        const filteredFiles = query
          ? directoryFiles.filter(file => file.name.toLowerCase().includes(query.toLowerCase()))
          : directoryFiles;

        // Преобразуем в формат SearchResponse
        searchResults = filteredFiles.map(file => ({
          name: file.name,
          path: `${PUBLIC_URL}${file.fileUrl}`, // Используем полный URL для файла
          type: file.fileType || getFileTypeFromName(file.name),
          size: file.size || 0,
          lastModified: file.lastModifiedAt || file.createdAt || Date.now(),
          parentPath: getDirectoryPath(directory) // Добавляем путь к директории
        }));

        // Добавляем поддиректории, если есть запрос
        if (query) {
          const subdirectories = directories.filter(dir =>
            dir.parentId === directory.id &&
            dir.name.toLowerCase().includes(query.toLowerCase())
          );

          const directoryResults = subdirectories.map(dir => ({
            name: dir.name,
            path: dir.path || `${path}/${dir.name}`.replace(/^\/+/, ''),
            type: "directory",
            parentPath: path
          }));

          searchResults = [...directoryResults, ...searchResults];
        }
      }
    } else {
      // Глобальный поиск по всем файлам
      const filteredFiles = files.filter(file =>
        file.name.toLowerCase().includes(query.toLowerCase())
      );

      searchResults = filteredFiles.map(file => {
        // Находим директорию
        const directory = directories.find(dir => dir.id === file.directoryId);
        const dirPath = directory ? getDirectoryPath(directory) : '';

        return {
          name: file.name,
          path: `${PUBLIC_URL}${file.fileUrl}`, // Используем полный URL для файла
          type: file.fileType || getFileTypeFromName(file.name),
          size: file.size || 0,
          lastModified: file.lastModifiedAt || file.createdAt || Date.now(),
          parentPath: dirPath // Добавляем путь к директории
        };
      });

      // Добавляем директории в результаты поиска
      const filteredDirectories = directories.filter(dir =>
        dir.name.toLowerCase().includes(query.toLowerCase())
      );

      const directoryResults = filteredDirectories.map(dir => {
        const parentDir = directories.find(d => d.id === dir.parentId);
        const parentPath = parentDir ? getDirectoryPath(parentDir) : '';

        return {
          name: dir.name,
          path: dir.path || `${parentPath}/${dir.name}`.replace(/^\/+/, ''),
          type: "directory",
          parentPath: parentPath
        };
      });

      searchResults = [...directoryResults, ...searchResults];
    }

    res.json(searchResults);
  } catch (error) {
    console.error('Error searching files:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Вспомогательная функция для определения типа файла по имени
function getFileTypeFromName(fileName) {
  const extension = fileName.split('.').pop().toLowerCase();

  const typeMap = {
    'pdf': 'pdf',
    'txt': 'text',
    'doc': 'document',
    'docx': 'document',
    'jpg': 'image',
    'jpeg': 'image',
    'png': 'image',
    'gif': 'image'
  };

  return typeMap[extension] || 'file';
}

// Вспомогательная функция для получения полного пути директории
function getDirectoryPath(dir) {
  if (!dir.parentId) {
    return dir.name;
  }

  const parentDir = directories.find(d => d.id === dir.parentId);
  return parentDir ? `${getDirectoryPath(parentDir)}/${dir.name}` : dir.name;
}

// Создание директории
app.post('/directories', (req, res) => {
  try {
    const { name, description, imageUrl, parentId, userId: bodyUserId, userName: bodyUserName } = req.body;
    const userId = req.headers['user-id'] || bodyUserId; // Получаем ID пользователя из заголовка или тела
    const userName = bodyUserName || req.headers['user-name'] || 'Неизвестный пользователь'; // Получаем имя из тела или заголовка

    console.log(`Create directory request: name=${name}, userId=${userId}, userName=${userName}`);

    if (!name) {
      return res.status(400).json({ error: 'Directory name is required' });
    }

    const parentDirectory = parentId ? directories.find(dir => dir.id === parentId) : null;
    const parentPath = parentDirectory ? getDirectoryPath(parentDirectory) : '';

    const newDirectory = {
      id: Date.now().toString(),
      name,
      description: description || '',
      imageUrl: imageUrl || '/uploads/images/default.jpg',
      parentId: parentId || null
    };

    directories.push(newDirectory);
    fs.writeFileSync(directoriesFile, JSON.stringify(directories, null, 2));

    // Создаем запись в истории напрямую без использования функции addToHistory
    const dirPath = parentPath ? `${parentPath}/${name}` : name;
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName,
      action: 'add',
      itemType: 'directory',
      itemName: name,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    res.json({
      ...newDirectory,
      imageUrl: newDirectory.imageUrl.startsWith('http') ?
        newDirectory.imageUrl :
        `${PUBLIC_URL}${newDirectory.imageUrl}`
    });
  } catch (error) {
    console.error('Error creating directory:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Рекурсивное удаление директории
function deleteDirectoryRecursively(directoryId) {
  // Находим все поддиректории
  const subdirectories = directories.filter(dir => dir.parentId === directoryId);

  // Рекурсивно удаляем все поддиректории
  subdirectories.forEach(subdir => {
    deleteDirectoryRecursively(subdir.id);
  });

  // Удаляем все файлы в текущей директории
  const directoryFiles = files.filter(file => file.directoryId === directoryId);
  directoryFiles.forEach(file => {
    const filePath = path.join(__dirname, file.fileUrl);
    if (fs.existsSync(filePath)) {
      try {
        fs.unlinkSync(filePath);
      } catch (error) {
        console.error('Error deleting file:', error);
      }
    }
  });

  // Удаляем записи о файлах
  files = files.filter(file => file.directoryId !== directoryId);

  // Удаляем саму директорию
  directories = directories.filter(dir => dir.id !== directoryId);
}

// Удаление директории
app.delete('/directories/:id', (req, res) => {
  try {
    const directoryId = req.params.id;
    const userId = req.headers['user-id'] || req.body.userId; // Получаем ID пользователя из заголовка или тела
    const userName = req.body.userName || req.headers['user-name'] || 'Неизвестный пользователь'; // Получаем имя из тела или заголовка

    console.log(`Delete directory request: directoryId=${directoryId}, userId=${userId}, userName=${userName}`);

    const directory = directories.find(dir => dir.id === directoryId);

    if (!directory) {
      return res.status(404).json({ error: 'Directory not found' });
    }

    const dirPath = getDirectoryPath(directory);

    // Создаем запись в истории напрямую
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName,
      action: 'delete',
      itemType: 'directory',
      itemName: directory.name,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    deleteDirectoryRecursively(directoryId);
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));
    fs.writeFileSync(directoriesFile, JSON.stringify(directories, null, 2));

    res.json({ message: 'Directory and all contents deleted successfully' });
  } catch (error) {
    console.error('Error deleting directory:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обработка PDF файлов
app.get('/uploads/pdfs/*', (req, res, next) => {
  try {
    const fileName = req.path.split('/uploads/pdfs/')[1];
    const filePath = path.join(__dirname, 'uploads/pdfs', fileName);

    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ error: 'PDF file not found' });
    }

    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const range = req.headers.range;

    if (range) {
      const parts = range.replace(/bytes=/, "").split("-");
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
      const chunksize = (end - start) + 1;
      const file = fs.createReadStream(filePath, { start, end });
      const head = {
        'Content-Range': `bytes ${start}-${end}/${fileSize}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunksize,
        'Content-Type': 'application/pdf',
      };
      res.writeHead(206, head);
      file.pipe(res);
    } else {
      const head = {
        'Content-Length': fileSize,
        'Content-Type': 'application/pdf',
        'Accept-Ranges': 'bytes',
      };
      res.writeHead(200, head);
      fs.createReadStream(filePath).pipe(res);
    }
  } catch (error) {
    console.error('Error serving PDF file:', error);
    res.status(500).json({ error: 'Error serving PDF file' });
  }
});

// Обработка изображений
app.use('/uploads/images', express.static('uploads/images', {
  setHeaders: (res, path, stat) => {
    res.set('Content-Type', 'image/jpeg');
    res.set('Cache-Control', 'public, max-age=31557600'); // Кэширование на 1 год
  }
}));

// Обработка аватаров
app.use('/uploads/avatars', express.static('uploads/avatars', {
  setHeaders: (res, path, stat) => {
    res.set('Content-Type', 'image/jpeg');
    res.set('Cache-Control', 'public, max-age=31557600'); // Кэширование на 1 год
  }
}));

// Добавление файла в избранное
app.put('/files/:fileId/favorite', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const file = files.find(f => f.id === fileId);

    if (!file) {
      return res.status(404).json({ error: 'File not found' });
    }

    const existingFavorite = favorites.find(f => f.fileId === fileId);
    if (!existingFavorite) {
      favorites.push({
        id: Date.now().toString(),
        fileId: fileId,
        addedAt: Date.now()
      });
      fs.writeFileSync(favoritesFile, JSON.stringify(favorites, null, 2));
    }

    res.json({ success: true });
  } catch (error) {
    console.error('Error adding to favorites:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Удаление файла из избранного
app.delete('/files/:fileId/favorite', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const favoriteIndex = favorites.findIndex(f => f.fileId === fileId);

    if (favoriteIndex === -1) {
      return res.status(404).json({ error: 'File not found in favorites' });
    }

    favorites.splice(favoriteIndex, 1);
    fs.writeFileSync(favoritesFile, JSON.stringify(favorites, null, 2));

    res.json({ success: true });
  } catch (error) {
    console.error('Error removing from favorites:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Получение списка избранных файлов
app.get('/favorites', (req, res) => {
  try {
    const favoriteFiles = favorites.map(favorite => {
      const file = files.find(f => f.id === favorite.fileId);
      if (file) {
        return {
          ...file,
          addedToFavoritesAt: favorite.addedAt,
          fileUrl: `${PUBLIC_URL}${file.fileUrl}`
        };
      }
      return null;
    }).filter(Boolean);

    res.json(favoriteFiles);
  } catch (error) {
    console.error('Error getting favorites:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Проверка статуса избранного для файла
app.get('/files/:fileId/favorite', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const isFavorite = favorites.some(f => f.fileId === fileId);
    res.json({ isFavorite });
  } catch (error) {
    console.error('Error checking favorite status:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обновление имени файла
app.put('/files/:fileId', async (req, res) => {
  try {
    const fileId = req.params.fileId;
    const { name, userId: bodyUserId, userName: bodyUserName } = req.body;
    const userId = req.headers['user-id'] || bodyUserId; // Получаем ID пользователя из заголовка или тела
    const userName = bodyUserName || req.headers['user-name'] || 'Неизвестный пользователь'; // Получаем имя из тела или заголовка

    console.log(`Rename file request: fileId=${fileId}, userId=${userId}, userName=${userName}`);

    if (!name) {
      return res.status(400).json({ error: 'Name is required' });
    }

    const fileIndex = files.findIndex(file => file.id === fileId);
    if (fileIndex === -1) {
      return res.status(404).json({ error: 'File not found' });
    }

    const oldName = files[fileIndex].name;
    const file = files[fileIndex];
    const directory = directories.find(dir => dir.id === file.directoryId);
    const dirPath = directory ? getDirectoryPath(directory) : 'root';

    files[fileIndex].name = name;
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));

    // Создаем запись в истории напрямую без использования функции addToHistory
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName,
      action: 'rename',
      itemType: 'file',
      itemName: `${oldName} → ${name}`,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    res.json(files[fileIndex]);
  } catch (error) {
    console.error('Error updating file name:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обновление имени директории
app.put('/directories/:directoryId', async (req, res) => {
  try {
    const directoryId = req.params.directoryId;
    const { name, userId: bodyUserId, userName: bodyUserName } = req.body;
    const userId = req.headers['user-id'] || bodyUserId; // Получаем ID пользователя из заголовка или тела
    const userName = bodyUserName || req.headers['user-name'] || 'Неизвестный пользователь'; // Получаем имя из тела или заголовка

    console.log(`Rename directory request: dirId=${directoryId}, userId=${userId}, userName=${userName}`);

    if (!name) {
      return res.status(400).json({ error: 'Name is required' });
    }

    const directoryIndex = directories.findIndex(dir => dir.id === directoryId);
    if (directoryIndex === -1) {
      return res.status(404).json({ error: 'Directory not found' });
    }

    const oldName = directories[directoryIndex].name;
    const dirPath = getDirectoryPath(directories[directoryIndex]);

    directories[directoryIndex].name = name;
    fs.writeFileSync(directoriesFile, JSON.stringify(directories, null, 2));

    // Создаем запись в истории напрямую без использования функции addToHistory
    const historyEntry = {
      id: Date.now().toString(),
      userId,
      userName,
      action: 'rename',
      itemType: 'directory',
      itemName: `${oldName} → ${name}`,
      path: dirPath,
      timestamp: new Date().toISOString()
    };

    console.log(`Creating history entry directly: ${JSON.stringify(historyEntry)}`);

    activityHistory.unshift(historyEntry);
    if (activityHistory.length > 100) {
      activityHistory.pop();
    }
    fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));

    res.json(directories[directoryIndex]);
  } catch (error) {
    console.error('Error updating directory name:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Административная панель
app.get('/admin', (req, res) => {
    res.sendFile(path.join(__dirname, 'admin.html'));
});

// Функция для добавления записи в историю
async function addToHistory(userId, action, itemType, itemName, directoryPath = '', req = null) {
  let userName = 'Неизвестный пользователь';

  // Выводим информацию обо всех доступных заголовках для диагностики
  const reqHeaders = req ? JSON.stringify(req.headers) : 'no req headers';
  console.log(`===DEBUG=== Request headers: ${reqHeaders}`);

  try {
    // Прямая проверка всех возможных вариантов написания заголовка
    if (req && req.headers) {
      // Преобразуем все ключи заголовков в нижний регистр
      const normalizedHeaders = {};
      Object.keys(req.headers).forEach(key => {
        normalizedHeaders[key.toLowerCase()] = req.headers[key];
      });

      console.log("Normalized headers:", JSON.stringify(normalizedHeaders));

      // Проверяем все возможные варианты
      if (normalizedHeaders['user-name']) {
        userName = normalizedHeaders['user-name'];
        console.log(`Using user-name: ${userName}`);
      } else if (normalizedHeaders['username']) {
        userName = normalizedHeaders['username'];
        console.log(`Using username: ${userName}`);
      }
    }

    // Если по-прежнему неизвестный, ищем в базе данных
    if (userName === 'Неизвестный пользователь' && userId) {
      console.log(`Looking for user by email: ${userId}`);
      const user = await db.findUserByEmail(userId);
      if (user && user.name) {
        userName = user.name;
        console.log(`Found userName in database: ${userName}`);
      }
    }
  } catch (error) {
    console.error('Error getting user info for history:', error);
  }

  console.log(`FINAL: Adding history entry for user: ${userName}, action: ${action}, item: ${itemName}`);

  const historyEntry = {
    id: Date.now().toString(),
    userId,
    userName,
    action,
    itemType,
    itemName,
    path: directoryPath,
    timestamp: new Date().toISOString()
  };

  activityHistory.unshift(historyEntry); // Добавляем в начало массива
  if (activityHistory.length > 100) { // Ограничиваем историю 100 записями
    activityHistory.pop();
  }
  fs.writeFileSync(activityHistoryFile, JSON.stringify(activityHistory, null, 2));
  return historyEntry;
}

// Получение истории действий
app.get('/activity-history', (req, res) => {
  try {
    res.json(activityHistory);
  } catch (error) {
    console.error('Error getting activity history:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Маршрут для отправки уведомлений о новых файлах
app.post('/notifications', (req, res) => {
  try {
    const { fileId, fileName, description, userId, userName, timestamp } = req.body;

    if (!fileId || !fileName || !userId) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    console.log(`Received notification: ${JSON.stringify(req.body)}`);

    // Отправляем уведомления всем активным пользователям
    let notificationCount = 0;
    wsConnections.forEach((ws, connectedUserId) => {
      // Отправляем уведомления всем пользователям, включая отправителя
      const notificationData = {
        type: 'file_notification',
        data: {
          fileId,
          fileName,
          description,
          userName,
          fileUrl: `/files/${fileId}`,
          timestamp: timestamp || Date.now()
        }
      };

      if (sendNotification(connectedUserId, notificationData)) {
        notificationCount++;
      }
    });

    res.json({
      success: true,
      message: `Notification sent to ${notificationCount} users`,
      notificationCount
    });
  } catch (error) {
    console.error('Error sending notification:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Маршрут для отправки уведомлений через FCM
app.post(['/api/notifications/fcm', '/notifications/fcm', '/api/fcm', '/fcm'], async (req, res) => {
  try {
    const { notification, data, fileId, fileName, description, userId, userName, fileUrl, token, topic } = req.body;

    console.log(`Received FCM notification request: ${JSON.stringify(req.body)}`);

    // Получаем переданный userId из тела запроса
    const targetUserId = userId || null;

    // Если есть готовое уведомление, используем его
    const notificationTitle = notification?.title || `Новый файл: ${fileName || ''}`;
    const notificationBody = notification?.body ||
      (description ?
        `${userName || 'Пользователь'}: ${description}` :
        `Пользователь ${userName || ''} добавил новый файл`);

    // Если токен передан напрямую, используем его
    if (token) {
      const notificationData = {
        type: 'file_notification',
        fileId: fileId || (data?.fileId || ''),
        fileName: fileName || (data?.fileName || ''),
        description: description || (data?.description || ''),
        userName: userName || (data?.userName || ''),
        fileUrl: fileUrl || (data?.fileUrl || ''),
        timestamp: Date.now().toString(),
        ...(data || {})
      };

      const success = await sendFcmNotification(targetUserId, token, {
        title: notificationTitle,
        body: notificationBody,
        data: notificationData
      });

      return res.json({
        success,
        message: success ? 'FCM notification sent successfully' : 'Failed to send FCM notification'
      });
    }

    // Если userId не указан, возвращаем ошибку
    if (!targetUserId) {
      console.log('No userId provided for FCM notification');
      return res.status(400).json({
        success: false,
        error: 'userId is required when token is not provided'
      });
    }

    // Если токен не передан, проверяем, есть ли он в памяти
    let fcmToken = fcmTokens.get(targetUserId);

    // Если нет в памяти, проверяем, есть ли он в файле
    if (!fcmToken && savedFcmTokens[targetUserId] && savedFcmTokens[targetUserId].token) {
      fcmToken = savedFcmTokens[targetUserId].token;
      // Загружаем токен в память
      fcmTokens.set(targetUserId, fcmToken);
      console.log(`Loaded FCM token for user ${targetUserId} from storage`);
    }

    if (!fcmToken) {
      console.log(`No FCM token found for user ${targetUserId}`);
      return res.status(400).json({
        success: false,
        error: 'FCM token not found for this user'
      });
    }

    // Создаем объект с данными уведомления
    const notificationData = {
      type: 'file_notification',
      fileId: fileId || (data?.fileId || ''),
      fileName: fileName || (data?.fileName || ''),
      description: description || (data?.description || ''),
      userName: userName || (data?.userName || ''),
      fileUrl: fileUrl || (data?.fileUrl || ''),
      timestamp: Date.now().toString(),
      ...(data || {})
    };

    // Формируем сообщение
    const success = await sendFcmNotification(targetUserId, fcmToken, {
      title: notificationTitle,
      body: notificationBody,
      data: notificationData
    });

    if (success) {
      console.log(`FCM notification sent to user ${targetUserId}`);
      res.json({ success: true, message: 'FCM notification sent successfully' });
    } else {
      console.log(`Failed to send FCM notification to user ${targetUserId}`);
      res.status(500).json({ success: false, error: 'Failed to send FCM notification' });
    }
  } catch (error) {
    console.error('Error sending FCM notification:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обработка ошибок
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Something broke!' });
});

// Маршрут для отправки уведомлений через FCM всем известным пользователям
app.post(['/api/notifications/fcm/all', '/notifications/fcm/all'], async (req, res) => {
  try {
    const { fileId, fileName, description, userName, fileUrl } = req.body;

    console.log(`Received request to send FCM notifications to all users: ${JSON.stringify(req.body)}`);

    if (!fileId || !fileName) {
      return res.status(400).json({
        success: false,
        error: 'fileId and fileName are required'
      });
    }

    // Подготавливаем данные уведомления
    const notificationData = {
      type: 'file_notification',
      data: {
        fileId: fileId,
        fileName: fileName,
        description: description || '',
        userName: userName || 'Пользователь',
        fileUrl: fileUrl || '',
        timestamp: Date.now()
      }
    };

    // Создаем массив пользователей из хранилища токенов
    const userTokens = new Map([...fcmTokens.entries()]);

    // Добавляем токены из файлового хранилища
    Object.keys(savedFcmTokens).forEach(userId => {
      if (!userTokens.has(userId) && savedFcmTokens[userId].token) {
        userTokens.set(userId, savedFcmTokens[userId].token);
      }
    });

    console.log(`Sending notifications to ${userTokens.size} users`);

    // Отправляем уведомления всем пользователям
    const results = [];
    const promises = [];

    for (const [userId, token] of userTokens.entries()) {
      promises.push(
        sendFcmNotification(userId, token, notificationData)
          .then(success => {
            results.push({
              userId,
              success,
              message: success ? 'Notification sent' : 'Failed to send notification'
            });
          })
          .catch(error => {
            console.error(`Error sending notification to user ${userId}:`, error);
            results.push({
              userId,
              success: false,
              message: `Error: ${error.message}`
            });
          })
      );
    }

    // Ждем завершения всех отправок
    await Promise.all(promises);

    // Подсчитываем успешные отправки
    const successCount = results.filter(r => r.success).length;

    res.json({
      success: successCount > 0,
      message: `Successfully sent ${successCount} of ${userTokens.size} notifications`,
      results
    });
  } catch (error) {
    console.error('Error sending notifications to all users:', error);
    res.status(500).json({ success: false, error: 'Server error' });
  }
});

// Маршрут для обновления профиля пользователя
app.post('/api/user/profile', async (req, res) => {
    try {
        const { uid, name, institute, avatarUrl } = req.body;
        
        console.log('Получен запрос на обновление профиля:', { uid, name, institute, avatarUrl });
        
        if (!uid) {
            return res.status(400).json({ error: 'User ID is required' });
        }
        
        const updates = {
            name: name || null,
            department: institute || null,
            avatarUrl: null
        };
        
        // Обрабатываем аватарку
        if (avatarUrl) {
            console.log('Получен URL аватарки:', avatarUrl);
            
            if (avatarUrl.startsWith('file://')) {
                console.log('Аватарка передана как локальный файл, установка значения по умолчанию');
                updates.avatarUrl = `${PUBLIC_URL}/uploads/avatars/default.jpg`;
            } else if (avatarUrl.startsWith('http')) {
                // Проверяем, указывает ли URL на наш сервер
                const urlObj = new URL(avatarUrl);
                const isOurServer = urlObj.hostname === 'localhost' || 
                                   urlObj.hostname === '127.0.0.1' || 
                                   urlObj.hostname === SERVER_IP ||
                                   urlObj.hostname.includes('duckdns.org');
                
                if (isOurServer) {
                    console.log('Аватарка с нашего сервера, используем полный URL:', avatarUrl);
                    updates.avatarUrl = avatarUrl;
                } else {
                    console.log('Аватарка с внешнего сервера, используем полный URL:', avatarUrl);
                    updates.avatarUrl = avatarUrl;
                }
            } else {
                // Если это просто путь без протокола, преобразуем в полный URL
                console.log('Аватарка передана как относительный путь, преобразуем в полный URL');
                updates.avatarUrl = `${PUBLIC_URL}${avatarUrl}`;
            }
            
            console.log('Итоговый URL аватарки для сохранения:', updates.avatarUrl);
        }
        
        console.log(`Попытка обновления пользователя с идентификатором: ${uid}`);
        console.log('Данные для обновления:', updates);
        
        const updatedUser = await db.updateUser(uid, updates);
        
        if (!updatedUser) {
            console.log(`Пользователь с ID/email ${uid} не найден в базе данных`);
            return res.status(404).json({ error: 'Пользователь не найден' });
        }
        
        console.log(`Профиль пользователя ${uid} успешно обновлен в базе данных:`, updates);
        console.log('Обновленный пользователь:', updatedUser);
        
        res.status(200).json({
            success: true,
            message: 'Профиль успешно обновлен',
            user: {
                uid: updatedUser.id || updatedUser.email,
                name: updatedUser.name,
                institute: updatedUser.department,
                avatarUrl: updatedUser.avatarUrl || `${PUBLIC_URL}/uploads/images/default.jpg`
            }
        });
    } catch (error) {
        console.error('Ошибка при обновлении профиля пользователя:', error);
        res.status(500).json({
            error: 'Ошибка сервера',
            message: error.message
        });
    }
});

// Добавляем функцию для получения текущего URL сервера
function getCurrentServerUrl() {
  // Всегда возвращаем публичный URL с портом 8080
  const baseUrl = PUBLIC_URL;
  // Если URL не содержит порт, добавляем порт 8080
  if (baseUrl && !baseUrl.includes(':3000')) {
    return baseUrl.replace(/\/$/, '') + ':3000';
  }
  return baseUrl;
}

// Запуск сервера
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Сервер успешно запущен на порту ${PORT}`);
}); 