package com.consen.autodash;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class GpsSpeedTracker {
    private static boolean started = false;
    private static long lastLocationTime = 0;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Context appContext = null;

    private static final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                lastLocationTime = System.currentTimeMillis();
                DashboardState.gpsActive = true;

                double lat = location.getLatitude();
                double lon = location.getLongitude();
                DashboardState.currentLat = lat;
                DashboardState.currentLon = lon;
                DashboardState.hasLocationFix = true;

                int kmh = DashboardState.speedKmh;
                if (location.hasSpeed()) {
                    kmh = Math.round(location.getSpeed() * 3.6f);
                    if (kmh < 2) kmh = 0;
                    DashboardState.speedKmh = kmh;
                }
                if (location.hasBearing() && kmh >= 3) {
                    DashboardState.currentBearing = location.getBearing();
                }

                DashboardState.addTrailPoint(lat, lon);
                MapTileManager.prefetchAround(lat, lon, DashboardState.isNightMode());

                if (appContext != null) {
                    WeatherService.checkWeather(appContext, location);
                    LocationPlaceResolver.resolveIfNeeded(appContext, lat, lon, false);
                }
            }
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    @SuppressLint("MissingPermission")
    public static void seedLastKnownLocation(Context context, boolean forcePlace) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        try {
            LocationManager lm = (LocationManager) appCtx.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location gps = null;
                Location net = null;
                try { gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) {}
                try { net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) {}

                Location best = gps;
                if (best == null || (net != null && net.getTime() > best.getTime())) {
                    best = net;
                }

                if (best != null) {
                    double lat = best.getLatitude();
                    double lon = best.getLongitude();
                    DashboardState.currentLat = lat;
                    DashboardState.currentLon = lon;
                    DashboardState.hasLocationFix = true;
                    DashboardState.addTrailPoint(lat, lon);
                    MapTileManager.prefetchAround(lat, lon, DashboardState.isNightMode());
                    LocationPlaceResolver.resolveIfNeeded(appCtx, lat, lon, forcePlace);
                    WeatherService.checkWeather(appCtx, best);
                } else {
                    MapTileManager.prefetchAround(DashboardState.currentLat, DashboardState.currentLon, DashboardState.isNightMode());
                }
            }
        } catch (Throwable ignored) {}
    }

    @SuppressLint("MissingPermission")
    public static synchronized void start(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        if (started) {
            seedLastKnownLocation(appContext, false);
            return;
        }
        final Context appCtx = appContext;
        handler.post(() -> {
            try {
                LocationManager lm = (LocationManager) appCtx.getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    try {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, listener, Looper.getMainLooper());
                    } catch (Throwable ignored) {}
                    try {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, listener, Looper.getMainLooper());
                    } catch (Throwable ignored) {}
                    started = true;
                }
            } catch (Throwable ignored) {}
            seedLastKnownLocation(appCtx, true);
            WeatherService.checkInitialWeather(appCtx);
        });

        // Verificación periódica para llevar velocidad a 0 al detenerse y refrescar Spotify
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - lastLocationTime > 4500) {
                    DashboardState.speedKmh = 0;
                }
                SpotifyMediaListener.refreshSessions(appCtx);
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }
}
