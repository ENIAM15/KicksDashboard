package com.consen.autodash;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherService {

    private static final String PREFS_NAME = "ConsenWeatherPrefs";
    private static final String KEY_TEMP = "cached_temp";
    private static final String KEY_EMOJI = "cached_emoji";
    private static final String KEY_DESC = "cached_desc";
    private static final String KEY_MAX = "cached_max";
    private static final String KEY_MIN = "cached_min";
    private static final String KEY_CODE = "cached_code";
    private static final String KEY_IS_DAY = "cached_is_day";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Context appContext;
    private static boolean periodicScheduled = false;

    // Coordenadas predeterminadas (Villahermosa / Tabasco, México)
    private static final double DEFAULT_LAT = 17.9895;
    private static final double DEFAULT_LON = -92.9475;

    private static double currentLat = DEFAULT_LAT;
    private static double currentLon = DEFAULT_LON;
    private static long lastSuccessfulFetchTime = 0;
    private static boolean isFetching = false;
    private static boolean hasLoaded = false;

    public static synchronized void startPeriodicWeather(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
            loadCachedWeather();
        }

        fetchImmediately();

        if (!periodicScheduled) {
            periodicScheduled = true;
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fetchImmediately();
                    mainHandler.postDelayed(this, 10 * 60 * 1000); // Cada 10 minutos para tiempo real
                }
            }, 10 * 60 * 1000);
        }
    }

    public static void checkInitialWeather(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        loadCachedWeather();
        fetchImmediately();
    }

    public static void forceRefresh(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        lastSuccessfulFetchTime = 0;
        fetchImmediately();
    }

    public static void checkWeather(Context context, Location loc) {
        if (context != null) appContext = context.getApplicationContext();
        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLon = loc.getLongitude();
        }
        long now = System.currentTimeMillis();
        if (now - lastSuccessfulFetchTime > 10 * 60 * 1000) {
            fetchImmediately();
        }
    }

    private static void loadCachedWeather() {
        if (appContext == null || hasLoaded) return;
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedTemp = prefs.getString(KEY_TEMP, null);
            String savedEmoji = prefs.getString(KEY_EMOJI, null);
            String savedDesc = prefs.getString(KEY_DESC, null);
            String savedMax = prefs.getString(KEY_MAX, null);
            String savedMin = prefs.getString(KEY_MIN, null);
            if (savedTemp != null && savedEmoji != null) {
                DashboardState.weatherTemp = savedTemp;
                DashboardState.weatherEmoji = savedEmoji;
                if (savedDesc != null) DashboardState.weatherDesc = savedDesc;
                if (savedMax != null) DashboardState.weatherMaxTemp = savedMax;
                if (savedMin != null) DashboardState.weatherMinTemp = savedMin;
                DashboardState.weatherCode = prefs.getInt(KEY_CODE, 1);
                DashboardState.weatherIsDay = prefs.getBoolean(KEY_IS_DAY, true);
                hasLoaded = true;
            }
        } catch (Throwable ignored) {}
    }

    private static void saveCachedWeather(String temp, String emoji, String desc, String maxT, String minT, int code, boolean isDay) {
        if (appContext == null) return;
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_TEMP, temp)
                    .putString(KEY_EMOJI, emoji)
                    .putString(KEY_DESC, desc)
                    .putString(KEY_MAX, maxT)
                    .putString(KEY_MIN, minT)
                    .putInt(KEY_CODE, code)
                    .putBoolean(KEY_IS_DAY, isDay)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static boolean isNetworkAvailable() {
        if (appContext == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network net = cm.getActiveNetwork();
                if (net == null) return false;
                NetworkCapabilities act = cm.getNetworkCapabilities(net);
                return act != null && (act.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || act.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || act.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            }
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void fetchImmediately() {
        if (isFetching) return;
        isFetching = true;

        executor.execute(() -> {
            try {
                if (!isNetworkAvailable()) {
                    isFetching = false;
                    return;
                }

                if (appContext != null) {
                    try {
                        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                        if (lm != null) {
                            Location l = null;
                            try { l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) {}
                            if (l == null) {
                                try { l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) {}
                            }
                            if (l != null) {
                                currentLat = l.getLatitude();
                                currentLon = l.getLongitude();
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                        currentLat, currentLon);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "ConsenDashboard/2.0");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("current")) {
                        JSONObject cur = json.getJSONObject("current");
                        double tempC = cur.optDouble("temperature_2m", 25.0);
                        final int weatherCode = cur.optInt("weather_code", 0);
                        final boolean isDay = cur.optInt("is_day", 1) == 1;

                        String maxStrTmp = Math.round(tempC + 2) + "°";
                        String minStrTmp = Math.round(tempC - 5) + "°";
                        if (json.has("daily")) {
                            JSONObject daily = json.getJSONObject("daily");
                            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
                            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
                            if (maxArr != null && maxArr.length() > 0) {
                                maxStrTmp = Math.round(maxArr.getDouble(0)) + "°";
                            }
                            if (minArr != null && minArr.length() > 0) {
                                minStrTmp = Math.round(minArr.getDouble(0)) + "°";
                            }
                        }

                        final String tempStr = Math.round(tempC) + "°";
                        final String emojiStr = codeToEmoji(weatherCode, isDay);
                        final String descStr = codeToDescription(weatherCode, isDay);
                        final String maxFinal = maxStrTmp;
                        final String minFinal = minStrTmp;

                        mainHandler.post(() -> {
                            DashboardState.weatherTemp = tempStr;
                            DashboardState.weatherEmoji = emojiStr;
                            DashboardState.weatherDesc = descStr;
                            DashboardState.weatherMaxTemp = maxFinal;
                            DashboardState.weatherMinTemp = minFinal;
                            DashboardState.weatherCode = weatherCode;
                            DashboardState.weatherIsDay = isDay;
                            saveCachedWeather(tempStr, emojiStr, descStr, maxFinal, minFinal, weatherCode, isDay);
                            hasLoaded = true;
                        });
                        lastSuccessfulFetchTime = System.currentTimeMillis();
                    }
                }
                conn.disconnect();
            } catch (Throwable ignored) {
            } finally {
                isFetching = false;
            }
        });
    }

    private static String codeToDescription(int code, boolean isDay) {
        switch (code) {
            case 0:
                return isDay ? "Despejado" : "Noche despejada";
            case 1:
                return "Mayormente despejado";
            case 2:
                return "Parcialmente nublado";
            case 3:
                return "Nublado";
            case 45:
            case 48:
                return "Neblina";
            case 51:
            case 53:
            case 55:
                return "Llovizna";
            case 61:
                return "Lluvia ligera";
            case 63:
                return "Lluvia moderada";
            case 65:
                return "Lluvia fuerte";
            case 71:
            case 73:
            case 75:
                return "Nieve";
            case 80:
            case 81:
            case 82:
                return "Chubascos";
            case 95:
            case 96:
            case 99:
                return "Tormenta eléctrica";
            default:
                return isDay ? "Parcialmente nublado" : "Noche despejada";
        }
    }

    private static String codeToEmoji(int code, boolean isDay) {
        switch (code) {
            case 0:
                return isDay ? "☀️" : "🌙";
            case 1:
            case 2:
                return isDay ? "🌤️" : "☁️";
            case 3:
                return "☁️";
            case 45:
            case 48:
                return "🌫️";
            case 51:
            case 53:
            case 55:
                return "🌦️";
            case 61:
            case 63:
            case 65:
            case 80:
            case 81:
            case 82:
                return "🌧️";
            case 71:
            case 73:
            case 75:
                return "🌨️";
            case 95:
            case 96:
            case 99:
                return "⛈️";
            default:
                return isDay ? "☀️" : "🌙";
        }
    }
}
