const fs = require('fs');
const path = require('path');

class Database {
    constructor() {
        this.usersFile = path.join(__dirname, 'users.json');
        this.initializeDatabase();
    }

    initializeDatabase() {
        if (!fs.existsSync(this.usersFile)) {
            fs.writeFileSync(this.usersFile, JSON.stringify([], null, 2));
        }
    }

    async getUsers() {
        const data = await fs.promises.readFile(this.usersFile, 'utf8');
        return JSON.parse(data);
    }

    async saveUsers(users) {
        await fs.promises.writeFile(this.usersFile, JSON.stringify(users, null, 2));
    }

    async findUserByEmail(email) {
        const users = await this.getUsers();
        return users.find(user => user.email === email);
    }

    async createUser(user) {
        const users = await this.getUsers();
        users.push(user);
        await this.saveUsers(users);
        return user;
    }

    async updateUser(email, updates) {
        const users = await this.getUsers();
        const index = users.findIndex(user => user.email === email);
        if (index !== -1) {
            users[index] = { ...users[index], ...updates };
            await this.saveUsers(users);
            return users[index];
        }
        return null;
    }
}

module.exports = new Database(); 