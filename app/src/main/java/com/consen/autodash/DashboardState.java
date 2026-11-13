package com.consen.autodash;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardState {
    public static volatile int speedKmh = 0;
    public static volatile boolean gpsActive = false;
    public static volatile float tripDistanceKm = 0.0f;

    // Ubicación GPS y mapa en tiempo real
    public static volatile double currentLat = 17.9895;
    public static volatile double currentLon = -92.9475;
    public static volatile float currentBearing = 0f;
    public static volatile boolean hasLocationFix = false;
    public static volatile String currentPlaceName = "Tu ubicación";
    private static final int MAX_TRAIL_POINTS = 60;
    private static final List<double[]> trailPoints = new ArrayList<>();

    // Clima en tiempo real (Open-Meteo)
    public static volatile String weatherEmoji = "🌤️";
    public static volatile String weatherTemp = "--°";
    public static volatile String weatherDesc = "Actualizando clima...";
    public static volatile String weatherMaxTemp = "--°";
    public static volatile String weatherMinTemp = "--°";
    public static volatile int weatherCode = 1;
    public static volatile boolean weatherIsDay = true;

    // Reproductor Spotify
    public static volatile String trackTitle = "Spotify";
    public static volatile String trackArtist = "Reproduce tu música";
    public static volatile String trackAlbum = "";
    public static volatile Bitmap albumArt = null;
    public static volatile boolean isPlaying = false;

    // Métricas precisas de reproducción y progreso
    public static volatile long playbackPositionMs = 0;
    public static volatile long lastPositionUpdateTime = 0;
    public static volatile float playbackSpeed = 1.0f;
    public static volatile long trackDurationMs = 0;

    /**
     * Devuelve el saludo dinámico según la hora del día solicitada:
     * - Antes del mediodía (< 12): "Buenos días, Nancy"
     * - Antes de las 7:00 p.m. (12..18): "Buenas tardes, Nancy"
     * - Desde las 7:00 p.m. (>= 19): "Buenas noches, Nancy"
     */
    public static String getDynamicGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Buenos días, Nancy";
        } else if (hour < 19) {
            return "Buenas tardes, Nancy";
        } else {
            return "Buenas noches, Nancy";
        }
    }

    /**
     * Indica si el dashboard debe mostrarse en modo oscuro nocturno (19:00 a 05:59).
     */
    public static boolean isNightMode() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 19 || hour < 6;
    }

    public static synchronized void addTrailPoint(double lat, double lon) {
        if (!trailPoints.isEmpty()) {
            double[] last = trailPoints.get(trailPoints.size() - 1);
            double dLat = Math.toRadians(lat - last[0]);
            double dLon = Math.toRadians(lon - last[1]);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(last[0])) * Math.cos(Math.toRadians(lat))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double distMeters = 6371000.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
            if (distMeters < 10.0) {
                return;
            }
            tripDistanceKm += (float) (distMeters / 1000.0);
        }
        trailPoints.add(new double[]{lat, lon});
        while (trailPoints.size() > MAX_TRAIL_POINTS) {
            trailPoints.remove(0);
        }
    }

    public static synchronized List<double[]> getTrailSnapshot() {
        return new ArrayList<>(trailPoints);
    }

    /**
     * Calcula la posición actual de la pista en milisegundos extrapolando el tiempo
     * transcurrido desde la última actualización recibida del MediaController.
     */
    public static long getCurrentPositionMs() {
        long pos = playbackPositionMs;
        if (isPlaying && playbackSpeed > 0 && lastPositionUpdateTime > 0) {
            long delta = SystemClock.elapsedRealtime() - lastPositionUpdateTime;
            pos += (long) (delta * playbackSpeed);
        }
        if (pos < 0) pos = 0;
        if (trackDurationMs > 0 && pos > trackDurationMs) {
            pos = trackDurationMs;
        }
        return pos;
    }

    /**
     * Devuelve el progreso relativo de la canción entre 0.0f (inicio) y 1.0f (final).
     */
    public static float getSongProgress() {
        if (trackDurationMs <= 0) {
            return 0f;
        }
        long cur = getCurrentPositionMs();
        float p = (float) cur / (float) trackDurationMs;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        return p;
    }

    /**
     * Formatea milisegundos a formato min:seg (ej: 3:24)
     */
    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static void seekTo(float progressRatio) {
        if (trackDurationMs <= 0) return;
        long targetMs = (long) (trackDurationMs * Math.max(0f, Math.min(1f, progressRatio)));
        SpotifyMediaListener.seekTo(targetMs);
        playbackPositionMs = targetMs;
        lastPositionUpdateTime = SystemClock.elapsedRealtime();
    }

    public static void sendMediaKey(Context ctx, int keyCode) {
        try {
            if (SpotifyMediaListener.dispatchTransportKey(keyCode)) {
                return;
            }
        } catch (Throwable ignored) {}

        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = System.currentTimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                am.dispatchMediaKeyEvent(down);
                am.dispatchMediaKeyEvent(up);
            }
        } catch (Throwable ignored) {}
    }

    public static void openSpotify(Context ctx) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage("com.spotify.music");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Throwable ignored) {}
    }

    public static void openMaps(Context ctx) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Throwable ignored) {}
    }

    public static void openDialer(Context ctx) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) {}
    }
}
