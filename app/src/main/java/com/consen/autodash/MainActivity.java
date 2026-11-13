package com.consen.autodash;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity {

    private DashboardView dashboardView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        hideSystemUI();

        dashboardView = new DashboardView(this);
        setContentView(dashboardView);

        checkPermissionsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        GpsSpeedTracker.start(this);
        WeatherService.startPeriodicWeather(this);
        SpotifyMediaListener.refreshSessions(this);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void checkPermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    101);
        } else {
            GpsSpeedTracker.start(this);
            fetchInitialLocationAndWeather();
        }
        checkNotificationPermission();
        SpotifyMediaListener.refreshSessions(this);
    }

    private void checkNotificationPermission() {
        try {
            String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (listeners == null || !listeners.contains(getPackageName())) {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "Activa el acceso a notificaciones para sincronizar Spotify", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) {}
    }

    private void fetchInitialLocationAndWeather() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location loc = null;
                try { loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) {}
                if (loc == null) {
                    try { loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) {}
                }
                if (loc != null) {
                    WeatherService.checkWeather(this, loc);
                }
            }
        } catch (Throwable ignored) {}
        WeatherService.startPeriodicWeather(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            GpsSpeedTracker.start(this);
            fetchInitialLocationAndWeather();
        }
    }

    private void openAndroidAutoSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.google.android.projection.gearhead",
                    "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(android.net.Uri.parse("package:com.google.android.projection.gearhead"));
                startActivity(fallback);
            } catch (Throwable ignored) {}
        }
    }

    private class DashboardView extends View {
        private final DashboardCanvasRenderer renderer;

        public DashboardView(Context context) {
            super(context);
            renderer = new DashboardCanvasRenderer(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            renderer.render(canvas, getWidth(), getHeight(), true);
            postInvalidateDelayed(33); // 30 FPS animación continua suave
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();

                if (renderer.hitPlayPause.contains(x, y)) {
                    DashboardState.sendMediaKey(getContext(), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    return true;
                }
                if (renderer.hitPrev.contains(x, y)) {
                    DashboardState.sendMediaKey(getContext(), KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    return true;
                }
                if (renderer.hitNext.contains(x, y)) {
                    DashboardState.sendMediaKey(getContext(), KeyEvent.KEYCODE_MEDIA_NEXT);
                    return true;
                }
                if (renderer.hitProgressBar.contains(x, y)) {
                    float ratio = (x - renderer.hitProgressBar.left) / renderer.hitProgressBar.width();
                    DashboardState.seekTo(ratio);
                    return true;
                }
                if (renderer.hitMapsCenter.contains(x, y)) {
                    GpsSpeedTracker.seedLastKnownLocation(getContext(), true);
                    Toast.makeText(getContext(), "Actualizando ubicación GPS...", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (renderer.hitMaps.contains(x, y)) {
                    DashboardState.openMaps(getContext());
                    return true;
                }
                if (renderer.hitPhone.contains(x, y)) {
                    DashboardState.openDialer(getContext());
                    return true;
                }
                if (renderer.hitSpotify.contains(x, y)) {
                    DashboardState.openSpotify(getContext());
                    return true;
                }
                if (renderer.hitWeather.contains(x, y)) {
                    WeatherService.forceRefresh(getContext());
                    Toast.makeText(getContext(), "Actualizando clima...", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
            return true;
        }
    }
}
