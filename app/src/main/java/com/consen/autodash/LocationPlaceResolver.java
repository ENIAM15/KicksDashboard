package com.consen.autodash;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Obtiene en tiempo real el nombre de la calle, colonia o lugar por donde
 * circula el vehículo usando el Geocoder del sistema con respaldo de Nominatim OSM.
 */
public class LocationPlaceResolver {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static volatile boolean isResolving = false;
    private static long lastResolveTime = 0;
    private static double lastLat = 0;
    private static double lastLon = 0;

    public static void resolveIfNeeded(Context ctx, double lat, double lon, boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            if (isResolving) return;
            if (now - lastResolveTime < 8000) return;
            double dist = approxMeters(lastLat, lastLon, lat, lon);
            if (lastResolveTime > 0 && dist < 45.0 && now - lastResolveTime < 25000) {
                return;
            }
        }

        isResolving = true;
        lastResolveTime = now;
        lastLat = lat;
        lastLon = lon;
        final Context appCtx = (ctx != null) ? ctx.getApplicationContext() : null;

        executor.execute(() -> {
            String resolved = null;
            try {
                if (appCtx != null && Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(appCtx, new Locale("es", "MX"));
                    List<Address> list = geocoder.getFromLocation(lat, lon, 1);
                    if (list != null && !list.isEmpty()) {
                        resolved = formatAddress(list.get(0));
                    }
                }
            } catch (Throwable ignored) {}

            if (resolved == null || resolved.isEmpty()) {
                resolved = fetchFromNominatim(lat, lon);
            }

            if (resolved != null && !resolved.isEmpty()) {
                DashboardState.currentPlaceName = resolved;
            }
            isResolving = false;
        });
    }

    private static String formatAddress(Address addr) {
        String road = addr.getThoroughfare();
        String subLocality = addr.getSubLocality();
        String locality = addr.getLocality();
        String feature = addr.getFeatureName();

        StringBuilder sb = new StringBuilder();
        if (road != null && !road.isEmpty()) {
            sb.append(road);
        } else if (feature != null && !feature.isEmpty() && !feature.matches("\\d+")) {
            sb.append(feature);
        }

        String area = (subLocality != null && !subLocality.isEmpty()) ? subLocality : locality;
        if (area != null && !area.isEmpty()) {
            if (sb.length() > 0 && !sb.toString().equalsIgnoreCase(area)) {
                sb.append(" • ").append(area);
            } else if (sb.length() == 0) {
                sb.append(area);
            }
        }
        return sb.toString().trim();
    }

    private static String fetchFromNominatim(double lat, double lon) {
        HttpURLConnection conn = null;
        try {
            String urlStr = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.5f&lon=%.5f&zoom=17&accept-language=es",
                    lat, lon);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ConsenAutoDash/2.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject addr = json.optJSONObject("address");
                if (addr != null) {
                    String road = firstNonEmpty(addr, "road", "pedestrian", "neighbourhood", "suburb");
                    String area = firstNonEmpty(addr, "suburb", "city_district", "town", "city", "village");
                    if (road != null && area != null && !road.equalsIgnoreCase(area)) {
                        return road + " • " + area;
                    } else if (road != null) {
                        return road;
                    } else if (area != null) {
                        return area;
                    }
                }
                String name = json.optString("name", "");
                if (!name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String firstNonEmpty(JSONObject obj, String... keys) {
        for (String k : keys) {
            String v = obj.optString(k, "");
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static double approxMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }
}
