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

const app = express();
const PORT = process.env.PORT || 3000;

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

const SERVER_IP = getServerIP();
const SERVER_URL = `http://${SERVER_IP}:${PORT}`;

// Создаем необходимые папки, если их нет
['uploads', 'uploads/pdfs', 'uploads/images'].forEach(dir => {
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
    } else if (file.fieldname === 'image') {
      if (file.mimetype.startsWith('image/')) {
        cb(null, true);
      } else {
        cb(new Error('Only image files are allowed!'));
      }
    } else {
      cb(new Error('Unexpected field'));
    }
  },
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB limit
  }
});

// Файлы для хранения данных
const directoriesFile = 'directories.json';
const filesFile = 'files.json';
const favoritesFile = 'favorites.json';
const activityHistoryFile = 'activity_history.json';

// Создаем файлы, если они не существуют
[directoriesFile, filesFile, favoritesFile, activityHistoryFile].forEach(file => {
  if (!fs.existsSync(file)) {
    fs.writeFileSync(file, '[]');
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
app.use(express.static('.')); // Обслуживание статических файлов из текущей директории

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
        const { email, name, department, password } = req.body;
        const adminKey = req.headers['admin-key'];
        
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
            }
            console.log('Email с данными для входа отправлен: ' + info?.response);
        });

        res.status(201).json({
            message: 'Пользователь успешно добавлен и данные для входа отправлены'
        });
    } catch (error) {
        console.error('Ошибка при добавлении пользователя:', error);
        res.status(500).json({ error: 'Ошибка сервера' });
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

app.use(express.urlencoded({ extended: true }));

// Маршрут для загрузки изображения
app.post('/upload/image', upload.single('image'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded.' });
  }
  const fileUrl = `/uploads/images/${req.file.filename}`;
  res.json({ fileUrl });
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
    
    console.log('File upload form data:', JSON.stringify(req.body));
    console.log(`Using userName from form: ${userName}, userId: ${userId}`);
    
    // Проверяем случай с root директорией
    if (directoryId === 'root') {
      const newFile = {
        id: Date.now().toString(),
        name: req.body.name || req.file.originalname,
        description: req.body.description || '',
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
      description: req.body.description || '',
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
      fileUrl: `${SERVER_URL}${file.fileUrl}`
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
        `${SERVER_URL}${newDirectory.imageUrl}`
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
          fileUrl: `${SERVER_URL}${file.fileUrl}`
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

// Обработка ошибок
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Something broke!' });
});

// Запуск сервера
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server is running on http://localhost:${PORT}`);
  console.log(`Server IP address: ${SERVER_IP}`);
  console.log(`PDF files will be accessible at ${SERVER_URL}/uploads/pdfs/`);
  console.log(`Image files will be accessible at ${SERVER_URL}/uploads/images/`);
  console.log(`Server is accessible from other devices in your network at ${SERVER_URL}`);
});

// Функция для получения полного пути к директории
function getDirectoryPath(directory) {
  if (!directory) return '';
  
  const pathParts = [];
  let currentDir = directory;
  
  // Добавляем имя текущей директории
  pathParts.unshift(currentDir.name);
  
  // Рекурсивно поднимаемся по родительским директориям
  while (currentDir.parentId) {
    const parentDir = directories.find(dir => dir.id === currentDir.parentId);
    if (!parentDir) break;
    
    pathParts.unshift(parentDir.name);
    currentDir = parentDir;
  }
  
  return pathParts.join('/');
} 