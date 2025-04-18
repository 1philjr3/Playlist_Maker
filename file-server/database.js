const fs = require('fs');
const path = require('path');

class Database {
    constructor() {
        this.usersFile = path.join(__dirname, 'users.json');
        this.initializeDatabase();
    }

    initializeDatabase() {
        try {
            if (!fs.existsSync(this.usersFile)) {
                console.log('Создание файла пользователей...');
                fs.writeFileSync(this.usersFile, JSON.stringify([], null, 2));
                console.log('Файл пользователей успешно создан.');
            }
        } catch (error) {
            console.error('Ошибка при инициализации базы данных:', error);
            // Создаем файл с пустым массивом в любом случае
            try {
                fs.writeFileSync(this.usersFile, '[]');
                console.log('Файл пользователей пересоздан после ошибки.');
            } catch (fatalError) {
                console.error('Критическая ошибка при создании файла пользователей:', fatalError);
            }
        }
    }

    async getUsers() {
        try {
            console.log('Чтение файла пользователей...');
            const data = await fs.promises.readFile(this.usersFile, 'utf8');
            console.log('Файл пользователей успешно прочитан.');
            return JSON.parse(data);
        } catch (error) {
            console.error('Ошибка при чтении файла пользователей:', error);
            // Возвращаем пустой массив в случае ошибки
            return [];
        }
    }

    async saveUsers(users) {
        try {
            console.log('Сохранение файла пользователей...');
            const data = JSON.stringify(users, null, 2);
            await fs.promises.writeFile(this.usersFile, data);
            
            // Проверяем, что данные были корректно записаны
            const checkData = await fs.promises.readFile(this.usersFile, 'utf8');
            const parsedData = JSON.parse(checkData);
            console.log(`Файл пользователей успешно сохранен. Сохранено ${parsedData.length} пользователей.`);
            
            // Логируем отладочную информацию о пользователях
            parsedData.forEach((user, index) => {
                console.log(`Пользователь #${index + 1}: id=${user.id}, email=${user.email}, name=${user.name}`);
            });
            
            return true;
        } catch (error) {
            console.error('Ошибка при сохранении файла пользователей:', error);
            return false;
        }
    }

    async findUserByEmail(email) {
        try {
            console.log(`Поиск пользователя по email: ${email}`);
            const users = await this.getUsers();
            const user = users.find(user => user.email === email);
            console.log(`Результат поиска: ${user ? 'Пользователь найден' : 'Пользователь не найден'}`);
            return user;
        } catch (error) {
            console.error('Ошибка при поиске пользователя:', error);
            return null;
        }
    }

    async createUser(user) {
        try {
            console.log(`Создание нового пользователя: ${user.email}`);
            const users = await this.getUsers();
            
            // Проверяем, существует ли пользователь
            const existingUser = users.find(u => u.email === user.email);
            if (existingUser) {
                console.log(`Пользователь с email ${user.email} уже существует`);
                return null;
            }
            
            // Добавляем пользователя
            users.push(user);
            
            // Сохраняем обновленный список
            const saved = await this.saveUsers(users);
            if (!saved) {
                throw new Error('Не удалось сохранить пользователя');
            }
            
            console.log(`Пользователь ${user.email} успешно создан`);
            return user;
        } catch (error) {
            console.error('Ошибка при создании пользователя:', error);
            throw error; // Пробрасываем ошибку дальше для обработки
        }
    }

    async updateUser(emailOrId, updates) {
        try {
            console.log(`Обновление пользователя: ${emailOrId}`);
            const users = await this.getUsers();
            
            // Сначала ищем по email
            let index = users.findIndex(user => user.email === emailOrId);
            
            // Если не найдено по email, пробуем найти по id
            if (index === -1) {
                index = users.findIndex(user => user.id === emailOrId);
            }
            
            if (index !== -1) {
                users[index] = { ...users[index], ...updates };
                await this.saveUsers(users);
                console.log(`Пользователь ${emailOrId} успешно обновлен`);
                return users[index];
            }
            console.log(`Пользователь ${emailOrId} не найден для обновления`);
            return null;
        } catch (error) {
            console.error('Ошибка при обновлении пользователя:', error);
            return null;
        }
    }
}

module.exports = new Database(); 