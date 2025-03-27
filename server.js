const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const os = require('os');

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

// Создаем файлы, если они не существуют
[directoriesFile, filesFile, favoritesFile].forEach(file => {
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

app.use(express.json());
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
app.post('/directories/:directoryId/files', upload.single('pdf'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded.' });
    }

    const directoryId = req.params.directoryId;
    
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

      return res.json(newFile);
    }

    const directory = directories.find(dir => dir.id === directoryId);
    if (!directory) {
      // Удаляем загруженный файл, если директория не найдена
      try {
        fs.unlinkSync(req.file.path);
      } catch (error) {
        console.error('Error deleting uploaded file:', error);
      }
      return res.status(404).json({ error: 'Directory not found' });
    }

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
app.delete('/files/:fileId', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const fileIndex = files.findIndex(file => file.id === fileId);
    
    if (fileIndex === -1) {
      return res.status(404).json({ error: 'File not found' });
    }

    const file = files[fileIndex];
    const filePath = path.join(__dirname, file.fileUrl);
    
    if (fs.existsSync(filePath)) {
      try {
        fs.unlinkSync(filePath);
      } catch (error) {
        console.error('Error deleting file:', error);
      }
    }

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
    const { name, description, imageUrl, parentId } = req.body;

    if (!name) {
      return res.status(400).json({ error: 'Directory name is required' });
    }

    const newDirectory = {
      id: Date.now().toString(),
      name,
      description: description || '',
      imageUrl: imageUrl || '/uploads/images/default.jpg',
      parentId: parentId || null
    };

    directories.push(newDirectory);
    fs.writeFileSync(directoriesFile, JSON.stringify(directories, null, 2));
    
    // Возвращаем с полным URL для изображения
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
    const directoryExists = directories.some(dir => dir.id === directoryId);
    
    if (!directoryExists) {
      return res.status(404).json({ error: 'Directory not found' });
    }

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
app.put('/files/:fileId', (req, res) => {
  try {
    const fileId = req.params.fileId;
    const { name } = req.body;
    
    console.log(`PUT /files/${fileId} - Запрос на обновление имени файла`);
    console.log(`Параметр fileId: ${fileId}`);
    console.log(`Тело запроса:`, req.body);
    
    if (!name) {
      console.log(`Ошибка: Имя файла не указано`);
      return res.status(400).json({ error: 'Name is required' });
    }
    
    console.log(`Поиск файла с ID: ${fileId}`);
    console.log(`Всего файлов в базе: ${files.length}`);
    
    const fileIndex = files.findIndex(file => file.id === fileId);
    console.log(`Найден файл по индексу: ${fileIndex}`);
    
    if (fileIndex === -1) {
      console.log(`Ошибка: Файл с ID ${fileId} не найден`);
      return res.status(404).json({ error: 'File not found' });
    }
    
    console.log(`Старое имя файла: ${files[fileIndex].name}`);
    console.log(`Новое имя файла: ${name}`);
    
    files[fileIndex].name = name;
    fs.writeFileSync(filesFile, JSON.stringify(files, null, 2));
    
    console.log(`Файл успешно переименован`);
    res.json(files[fileIndex]);
  } catch (error) {
    console.error('Error updating file name:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Обновление имени директории
app.put('/directories/:directoryId', (req, res) => {
  try {
    const directoryId = req.params.directoryId;
    const { name } = req.body;
    
    console.log(`PUT /directories/${directoryId} - Запрос на обновление имени директории`);
    console.log(`Параметр directoryId: ${directoryId}`);
    console.log(`Тело запроса:`, req.body);
    
    if (!name) {
      console.log(`Ошибка: Имя директории не указано`);
      return res.status(400).json({ error: 'Name is required' });
    }
    
    console.log(`Поиск директории с ID: ${directoryId}`);
    console.log(`Всего директорий в базе: ${directories.length}`);
    
    const directoryIndex = directories.findIndex(dir => dir.id === directoryId);
    console.log(`Найдена директория по индексу: ${directoryIndex}`);
    
    if (directoryIndex === -1) {
      console.log(`Ошибка: Директория с ID ${directoryId} не найдена`);
      return res.status(404).json({ error: 'Directory not found' });
    }
    
    console.log(`Старое имя директории: ${directories[directoryIndex].name}`);
    console.log(`Новое имя директории: ${name}`);
    
    directories[directoryIndex].name = name;
    fs.writeFileSync(directoriesFile, JSON.stringify(directories, null, 2));
    
    console.log(`Директория успешно переименована`);
    res.json(directories[directoryIndex]);
  } catch (error) {
    console.error('Error updating directory name:', error);
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