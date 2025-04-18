package com.practicum.playlist_maker.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Класс для автоматического обновления URL сервера в приложении.
 * 
 * Вставьте этот файл в пакет com.practicum.playlist_maker.network вашего проекта
 * и вызовите его в onCreate() вашего класса Application или в активити.
 */
public class ServerUrlUpdater {
    private static final String TAG = "ServerUrlUpdater";
    private static final String SETTINGS_PREFS = "PROVITA_SETTINGS";
    private static final String SERVER_URL_KEY = "SERVER_URL";
    
    private static final String[] AVAILABLE_URLS = {
        "http://172.17.3.66:3000",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://10.0.2.2:3000"
    };
    
    private final Context context;
    private final Executor executor;
    private final OkHttpClient client;
    
    public ServerUrlUpdater(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.client = new OkHttpClient();
    }
    
    /**
     * Проверяет и обновляет URL сервера в SharedPreferences.
     * @param serverCheckUrls список URL для проверки (например, localhost, ngrok URL)
     * @param callback обратный вызов, возвращающий найденный URL или null
     */
    public void checkAndUpdateServerUrl(String[] serverCheckUrls, ServerUrlCallback callback) {
        executor.execute(() -> {
            Log.d(TAG, "Checking server URLs...");
            
            // 1. Сначала проверяем сохраненный URL
            String savedUrl = getSavedServerUrl();
            Log.d(TAG, "Saved server URL: " + savedUrl);
            
            if (savedUrl != null) {
                // Проверяем, доступен ли сохраненный URL
                if (checkServerAvailability(savedUrl)) {
                    Log.d(TAG, "Saved server URL is available: " + savedUrl);
                    // Проверяем, не изменился ли публичный URL
                    String publicUrl = checkPublicUrlFromServer(savedUrl);
                    if (publicUrl != null && !publicUrl.equals(savedUrl)) {
                        Log.d(TAG, "Found new public URL: " + publicUrl);
                        saveServerUrl(publicUrl);
                        notifyCallback(callback, publicUrl);
                        return;
                    }
                    
                    // Если публичный URL не изменился, используем сохраненный
                    notifyCallback(callback, savedUrl);
                    return;
                } else {
                    Log.d(TAG, "Saved server URL is not available: " + savedUrl);
                }
            }
            
            // 2. Если сохраненный URL недоступен, перебираем все URL
            for (String url : serverCheckUrls) {
                if (checkServerAvailability(url)) {
                    Log.d(TAG, "Found available server URL: " + url);
                    
                    // Проверяем, не предоставляет ли сервер публичный URL
                    String publicUrl = checkPublicUrlFromServer(url);
                    String finalUrl = publicUrl != null ? publicUrl : url;
                    
                    // Сохраняем URL в настройках
                    saveServerUrl(finalUrl);
                    notifyCallback(callback, finalUrl);
                    return;
                }
            }
            
            // 3. Если ни один URL не доступен, возвращаем null
            Log.e(TAG, "No server URLs available");
            notifyCallback(callback, null);
        });
    }
    
    /**
     * Проверяет, предоставляет ли сервер публичный URL
     * @param baseUrl базовый URL сервера
     * @return публичный URL или null
     */
    private String checkPublicUrlFromServer(String baseUrl) {
        try {
            String url = baseUrl;
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            
            Request request = new Request.Builder()
                    .url(url + "/api/mobile/server-url")
                    .build();
            
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                
                String serverUrl = json.getString("server_url");
                boolean isPublic = json.optBoolean("is_public", false);
                
                Log.d(TAG, "Server URL from API: " + serverUrl + ", isPublic: " + isPublic);
                
                if (isPublic) {
                    return serverUrl;
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error checking public URL from server", e);
        }
        return null;
    }
    
    /**
     * Проверяет доступность сервера по URL
     * @param url URL сервера для проверки
     * @return true, если сервер доступен
     */
    private boolean checkServerAvailability(String url) {
        try {
            String healthUrl = url;
            if (healthUrl.endsWith("/")) healthUrl = healthUrl.substring(0, healthUrl.length() - 1);
            healthUrl += "/api/server-info";
            
            Request request = new Request.Builder()
                    .url(healthUrl)
                    .build();
            
            Response response = client.newCall(request).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e(TAG, "Server not available: " + url, e);
            return false;
        }
    }
    
    /**
     * Сохраняет URL сервера в настройках
     * @param url URL сервера
     */
    private void saveServerUrl(String url) {
        try {
            if (url == null) {
                Log.e(TAG, "Cannot save null server URL");
                return;
            }
            
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
            String oldUrl = prefs.getString(SERVER_URL_KEY, "");
            
            if (!url.equals(oldUrl)) {
                Log.d(TAG, "Saving new server URL: " + url + " (was: " + oldUrl + ")");
                prefs.edit().putString(SERVER_URL_KEY, url).apply();
            } else {
                Log.d(TAG, "Server URL unchanged: " + url);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving server URL", e);
        }
    }
    
    /**
     * Получает сохраненный URL сервера из настроек
     * @return сохраненный URL или null
     */
    private String getSavedServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(SERVER_URL_KEY, null);
    }
    
    /**
     * Вызывает обратный вызов в основном потоке
     * @param callback обратный вызов
     * @param url найденный URL сервера
     */
    private void notifyCallback(ServerUrlCallback callback, String url) {
        if (callback != null) {
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(() -> callback.onServerUrlFound(url));
        }
    }
    
    /**
     * Интерфейс обратного вызова для получения URL сервера
     */
    public interface ServerUrlCallback {
        void onServerUrlFound(String serverUrl);
    }
    
    /**
     * Удобный метод для использования в Java-коде
     */
    public static void updateServerUrl(Context context) {
        ServerUrlUpdater updater = new ServerUrlUpdater(context);
        String[] defaultUrls = new String[] {
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://10.0.2.2:3000"
        };
        updater.checkAndUpdateServerUrl(defaultUrls, url -> {
            Log.d(TAG, "Server URL updated: " + url);
        });
    }
    
    /**
     * Удобный метод для использования в Kotlin-коде (с лямбдой)
     */
    public static void updateServerUrl(Context context, ServerUrlCallback callback) {
        ServerUrlUpdater updater = new ServerUrlUpdater(context);
        String[] defaultUrls = new String[] {
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://10.0.2.2:3000"
        };
        updater.checkAndUpdateServerUrl(defaultUrls, callback);
    }
} 