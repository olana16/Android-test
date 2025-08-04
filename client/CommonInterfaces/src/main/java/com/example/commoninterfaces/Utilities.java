package com.example.commoninterfaces;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class Utilities {
    public static JSONObject getGrantedPermissions(Context context) {
        JSONObject data = new JSONObject();
        try {
            JSONArray perms = new JSONArray();
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_PERMISSIONS);
            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    if ((pi.requestedPermissionsFlags[i]
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        perms.put(pi.requestedPermissions[i]);
                    }
                }
            }
            data.put("permissions", perms);
        } catch (Exception ignored) {
            //
        }
        return data;
    }

    public static boolean canIUse(String perm, Context context) {
        return context.getPackageManager().checkPermission(perm, context.getPackageName())
                == PackageManager.PERMISSION_GRANTED;
    }


    public static JSONObject scanWIFI(Context context) {
        try {
            JSONObject dRet = new JSONObject();
            JSONArray jSONArray = new JSONArray();
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    List<ScanResult> scanResults = wifiManager.getScanResults();
                    if (scanResults != null && scanResults.size() > 0) {
                        int i = 0;
                        while (i < scanResults.size() && i < 10) {
                            ScanResult scanResult = scanResults.get(i);
                            JSONObject jSONObject = new JSONObject();
                            jSONObject.put("BSSID", scanResult.BSSID);
                            jSONObject.put("SSID", scanResult.SSID);
                            jSONArray.put(jSONObject);
                            i++;
                        }
                        dRet.put("networks", jSONArray);
                        return dRet;
                    }
                }

            }
            return dRet;
        } catch (Throwable th) {
            Log.e("MtaSDK", "isWifiNet error", th);
            return null;
        }
    }

    public static JSONObject getInstalledApps(boolean getSysPackages, Context context) {
        JSONArray apps = new JSONArray();
        List<PackageInfo> packs = context.getPackageManager().getInstalledPackages(0);
        for(int i=0;i < packs.size();i++) {
            PackageInfo p = packs.get(i);
            if ((!getSysPackages) && (p.versionName == null)) {
                continue ;
            }

            try {
                JSONObject newInfo = new JSONObject();
                String appname = p.applicationInfo.loadLabel(context.getPackageManager()).toString();
                String pname = p.packageName;
                String versionName = p.versionName;
                int versionCode = p.versionCode;

                newInfo.put("appName",appname);
                newInfo.put("packageName",pname);
                newInfo.put("versionName",versionName);
                newInfo.put("versionCode",versionCode);
                apps.put(newInfo);
            }catch (JSONException ignored) {}
        }

        JSONObject data = new JSONObject();
        try {
            data.put("apps", apps);
        } catch (JSONException ignored) {
            //
        }

        return data;
    }
}
