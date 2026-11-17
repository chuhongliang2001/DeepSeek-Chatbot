package com.deepseek.chatbot;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

final class DeepSeekApi {
    private DeepSeekApi() {
    }

    static String validateApiKey(String apiKey) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.deepseek.com/models").openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                connection.getInputStream().close();
                return null;
            }
            if (status == 401 || status == 403) {
                return "API Key 无效或已失效，请检查后重试";
            }
            if (status == 429) {
                return "请求过于频繁，请稍后再试";
            }
            return "密钥验证失败（HTTP " + status + "）";
        } catch (IOException error) {
            return "网络连接失败，请检查网络后重试";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
