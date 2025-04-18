const ngrok = require('ngrok');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const PORT = process.env.PORT || 3000;

// Путь к файлу с настройками
const configPath = path.join(__dirname, 'ngrok_config.json');

// Проверяем наличие конфигурации
async function getAuthToken() {
  // Проверяем, есть ли уже сохраненная конфигурация
  if (fs.existsSync(configPath)) {
    try {
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      if (config.authtoken) {
        return config.authtoken;
      }
    } catch (error) {
      console.error('Ошибка при чтении файла конфигурации:', error);
    }
  }

  // Если authtoken не найден, запрашиваем его у пользователя
  console.log('\n=======================================================');
  console.log('Для использования ngrok требуется authtoken.');
  console.log('Зарегистрируйтесь на https://dashboard.ngrok.com/signup');
  console.log('Получите ваш authtoken: https://dashboard.ngrok.com/get-started/your-authtoken');
  console.log('=======================================================\n');

  // Создаем интерфейс для чтения из консоли
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });

  return new Promise((resolve) => {
    rl.question('Введите ваш ngrok authtoken: ', (authtoken) => {
      rl.close();
      
      // Сохраняем токен в конфигурационный файл
      fs.writeFileSync(configPath, JSON.stringify({ authtoken }, null, 2));
      console.log(`Authtoken сохранен в ${configPath}`);
      
      resolve(authtoken);
    });
  });
}

async function startTunnel() {
  try {
    // Получаем authtoken
    const authtoken = await getAuthToken();
    
    // Подключаем authtoken
    await ngrok.authtoken(authtoken);
    
    // Запускаем туннель на порту сервера
    const url = await ngrok.connect({
      addr: PORT,
      region: 'eu', // Регион сервера (eu, us, au, ap, sa, jp, in)
      onStatusChange: status => {
        console.log(`Статус туннеля: ${status}`);
      },
      onLogEvent: data => {
        console.log(`[ngrok] ${data}`);
      }
    });

    // Выводим информацию о публичном URL
    console.log('\n========================================================');
    console.log(`🌐 Публичный URL для доступа к серверу: ${url}`);
    console.log('========================================================\n');

    // Сохраняем URL в файл для использования в клиентском приложении
    const serverConfigPath = path.join(__dirname, 'server_config.json');
    let config = {};
    
    try {
      if (fs.existsSync(serverConfigPath)) {
        config = JSON.parse(fs.readFileSync(serverConfigPath, 'utf8'));
      }
    } catch (error) {
      console.error('Ошибка при чтении файла конфигурации:', error);
    }

    config.public_url = url;
    config.last_updated = new Date().toISOString();
    
    fs.writeFileSync(serverConfigPath, JSON.stringify(config, null, 2));
    console.log(`💾 URL сохранен в ${serverConfigPath}`);

    // Обрабатываем завершение программы
    process.on('SIGINT', async () => {
      console.log('Завершение работы туннеля...');
      await ngrok.kill();
      process.exit(0);
    });

  } catch (error) {
    console.error('Ошибка при запуске ngrok:', error);
    process.exit(1);
  }
}

startTunnel(); 