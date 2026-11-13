package com.consen.autodash;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Administrador de mosaicos cartográficos en tiempo real para Canvas/Surface.
 * Utiliza ArcGIS World Street Map (Modo Día) y ArcGIS Dark Gray (Modo Noche),
 * mostrando calles, avenidas y lugares reales por donde circula el vehículo.
 */
public class MapTileManager {

    private static final int ZOOM = 16;
    private static final int TILE_SIZE = 256;

    private static final LruCache<String, Bitmap> tileCache = new LruCache<>(96);
    private static final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    private static final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static void drawLiveMap(Canvas canvas, RectF mapRect, double lat, double lon, float bearing, boolean nightMode) {
        if (mapRect == null || mapRect.width() <= 0 || mapRect.height() <= 0) return;

        // 1. Fondo cartográfico de respaldo mientras descargan los mosaicos
        drawFallbackMapGrid(canvas, mapRect, nightMode);

        // 2. Coordenadas continuas de mosaico (Web Mercator Slippy Map)
        double n = Math.pow(2.0, ZOOM);
        double latRad = Math.toRadians(lat);
        double exactX = ((lon + 180.0) / 360.0) * n;
        double exactY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

        int centerTileX = (int) Math.floor(exactX);
        int centerTileY = (int) Math.floor(exactY);

        float renderTilePx = Math.max(TILE_SIZE * 1.25f, mapRect.width() * 0.68f);
        float fracX = (float) (exactX - centerTileX);
        float fracY = (float) (exactY - centerTileY);

        float centerPxX = mapRect.centerX();
        float centerPxY = mapRect.centerY();

        float originX = centerPxX - (fracX * renderTilePx);
        float originY = centerPxY - (fracY * renderTilePx);

        int maxIdx = (1 << ZOOM) - 1;
        RectF dstRect = new RectF();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int tx = centerTileX + dx;
                int ty = centerTileY + dy;
                if (ty < 0 || ty > maxIdx) continue;
                int wrappedX = ((tx % (1 << ZOOM)) + (1 << ZOOM)) % (1 << ZOOM);

                float tileLeft = originX + dx * renderTilePx;
                float tileTop = originY + dy * renderTilePx;
                float tileRight = tileLeft + renderTilePx;
                float tileBottom = tileTop + renderTilePx;

                if (tileRight < mapRect.left || tileLeft > mapRect.right
                        || tileBottom < mapRect.top || tileTop > mapRect.bottom) {
                    continue;
                }

                Bitmap bmp = getOrFetchTile(ZOOM, wrappedX, ty, nightMode);
                if (bmp != null && !bmp.isRecycled()) {
                    dstRect.set(tileLeft, tileTop, tileRight + 0.8f, tileBottom + 0.8f);
                    canvas.drawBitmap(bmp, null, dstRect, tilePaint);
                }
            }
        }

        // 3. Trazo azul de ruta recorrida con GPS en tiempo real
        List<double[]> trail = DashboardState.getTrailSnapshot();
        if (trail.size() >= 2) {
            Path routePath = new Path();
            boolean first = true;
            for (double[] pt : trail) {
                double ptLatRad = Math.toRadians(pt[0]);
                double pxExactX = ((pt[1] + 180.0) / 360.0) * n;
                double pxExactY = (1.0 - Math.log(Math.tan(ptLatRad) + 1.0 / Math.cos(ptLatRad)) / Math.PI) / 2.0 * n;

                float screenX = centerPxX + (float) ((pxExactX - exactX) * renderTilePx);
                float screenY = centerPxY + (float) ((pxExactY - exactY) * renderTilePx);

                if (first) {
                    routePath.moveTo(screenX, screenY);
                    first = false;
                } else {
                    routePath.lineTo(screenX, screenY);
                }
            }

            routePaint.setStyle(Paint.Style.STROKE);
            routePaint.setStrokeCap(Paint.Cap.ROUND);
            routePaint.setStrokeJoin(Paint.Join.ROUND);

            routePaint.setStrokeWidth(Math.max(9f, mapRect.width() * 0.032f));
            routePaint.setColor(nightMode ? 0x6660A5FA : 0xDDFFFFFF);
            canvas.drawPath(routePath, routePaint);

            routePaint.setStrokeWidth(Math.max(5.5f, mapRect.width() * 0.020f));
            routePaint.setColor(nightMode ? 0xFF3B82F6 : 0xFF2563EB);
            canvas.drawPath(routePath, routePaint);
        }

        // 4. Indicador de posición GPS actual en el centro
        float radius = Math.max(10f, mapRect.width() * 0.036f);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(nightMode ? 0x4460A5FA : 0x332563EB);
        canvas.drawCircle(centerPxX, centerPxY, radius * 1.9f, markerPaint);

        markerPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(centerPxX, centerPxY, radius, markerPaint);

        markerPaint.setColor(0xFF2563EB);
        canvas.drawCircle(centerPxX, centerPxY, radius * 0.72f, markerPaint);

        canvas.save();
        canvas.rotate(bearing, centerPxX, centerPxY);
        Path arrow = new Path();
        float ar = radius * 0.45f;
        arrow.moveTo(centerPxX, centerPxY - ar);
        arrow.lineTo(centerPxX + ar * 0.72f, centerPxY + ar * 0.65f);
        arrow.lineTo(centerPxX, centerPxY + ar * 0.25f);
        arrow.lineTo(centerPxX - ar * 0.72f, centerPxY + ar * 0.65f);
        arrow.close();
        markerPaint.setColor(0xFFFFFFFF);
        canvas.drawPath(arrow, markerPaint);
        canvas.restore();
    }

    public static void prefetchAround(double lat, double lon, boolean nightMode) {
        double n = Math.pow(2.0, ZOOM);
        double latRad = Math.toRadians(lat);
        int centerTileX = (int) Math.floor(((lon + 180.0) / 360.0) * n);
        int centerTileY = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        int maxIdx = (1 << ZOOM) - 1;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int ty = centerTileY + dy;
                if (ty < 0 || ty > maxIdx) continue;
                int wrappedX = ((centerTileX + dx) % (1 << ZOOM) + (1 << ZOOM)) % (1 << ZOOM);
                getOrFetchTile(ZOOM, wrappedX, ty, nightMode);
            }
        }
    }

    private static Bitmap getOrFetchTile(int z, int x, int y, boolean nightMode) {
        String theme = nightMode ? "dark" : "day";
        String key = theme + "_" + z + "_" + x + "_" + y;
        Bitmap cached = tileCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        if (inFlight.contains(key)) {
            return null;
        }
        inFlight.add(key);

        executor.execute(() -> {
            try {
                // ArcGIS: orden es {z}/{y}/{x}
                String urlStr = nightMode
                        ? String.format(Locale.US, "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/%d/%d/%d", z, y, x)
                        : String.format(Locale.US, "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/%d/%d/%d", z, y, x);

                Bitmap bmp = downloadBitmap(urlStr);
                if (bmp != null) {
                    tileCache.put(key, bmp);
                }
            } catch (Throwable ignored) {
            } finally {
                inFlight.remove(key);
            }
        });
        return null;
    }

    private static Bitmap downloadBitmap(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "ConsenAutoDash/2.0 (Android)");
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                return bmp;
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static void drawFallbackMapGrid(Canvas canvas, RectF rect, boolean nightMode) {
        fallbackPaint.setStyle(Paint.Style.FILL);
        fallbackPaint.setColor(nightMode ? 0xFF282828 : 0xFFF4F3DE);
        canvas.drawRect(rect, fallbackPaint);

        fallbackPaint.setColor(nightMode ? 0xFF1F332B : 0xFFD9F2D9);
        canvas.drawRoundRect(
                new RectF(rect.left + rect.width() * 0.58f, rect.top + rect.height() * 0.08f,
                        rect.right - rect.width() * 0.08f, rect.top + rect.height() * 0.36f),
                12f, 12f, fallbackPaint);

        fallbackPaint.setStyle(Paint.Style.STROKE);
        fallbackPaint.setColor(nightMode ? 0xFF3E3E3E : 0xFFFFFFFF);
        fallbackPaint.setStrokeWidth(Math.max(4f, rect.width() * 0.015f));
        for (int i = 1; i <= 4; i++) {
            float y = rect.top + rect.height() * (i * 0.20f);
            canvas.drawLine(rect.left, y, rect.right, y - rect.height() * 0.05f, fallbackPaint);
            float x = rect.left + rect.width() * (i * 0.22f);
            canvas.drawLine(x, rect.top, x + rect.width() * 0.06f, rect.bottom, fallbackPaint);
        }
        fallbackPaint.setStyle(Paint.Style.FILL);
    }
}
