const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Инициализация Firebase Admin SDK
admin.initializeApp();

exports.sendPushNotification = functions.database
    .ref("/user_actions/{pushId}")
    .onCreate((snapshot, context) => {
        const actionData = snapshot.val();
        const userId = actionData.userId;

        // Настройка уведомления
        const payload = {
            notification: {
                title: "Action Notification",
                body: `User ${userId} has pressed the button!`,
                image: "https://example.com/image.png", // Замените URL на нужную картинку
                sound: "default" // Добавление звука
            },
            android: {
                notification: {
                    image: "https://example.com/image.png", // Картинка для Android
                }
            },
            apns: {
                payload: {
                    aps: {
                        sound: "default" // Звук для iOS
                    }
                }
            },
            topic: "global"
        };

        // Отправка уведомления через FCM
        return admin.messaging().send(payload)
            .then(() => {
                console.log("Notification sent successfully");
                return null;
            })
            .catch((error) => {
                console.error("Error sending notification:", error);
            });
    });
