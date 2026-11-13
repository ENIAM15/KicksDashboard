package com.consen.autodash;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renderizador de nueva generación para Consen Dashboard (Nissan Kicks e-POWER).
 * Incluye modo Día / Noche automático, saludo dinámico por horario, mapa GPS en tiempo real,
 * velocímetro junto al vehículo, reproductor Spotify con álbum y tarjeta de clima completa.
 */
public class DashboardCanvasRenderer {

    private final Context context;
    private Bitmap bgDay;
    private Bitmap bgNight;
    private Bitmap defaultArt;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    // Áreas táctiles interactivas
    public final RectF hitSpotify = new RectF();
    public final RectF hitPrev = new RectF();
    public final RectF hitPlayPause = new RectF();
    public final RectF hitNext = new RectF();
    public final RectF hitProgressBar = new RectF();
    public final RectF hitMaps = new RectF();
    public final RectF hitMapsCenter = new RectF();
    public final RectF hitWeather = new RectF();
    public final RectF hitPhone = new RectF();

    public DashboardCanvasRenderer(Context ctx) {
        this.context = ctx;
        loadBitmaps();
    }

    private void loadBitmaps() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            bgDay = BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_day, opts);
        } catch (Throwable ignored) {}
        try {
            bgNight = BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_night, opts);
        } catch (Throwable ignored) {}
        try {
            defaultArt = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_app);
        } catch (Throwable ignored) {}
    }

    public void render(Canvas canvas, int w, int h, boolean fullLayout) {
        if (w <= 0 || h <= 0) return;

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        boolean night = DashboardState.isNightMode();

        // 1. Fondo Nissan Kicks Día o Noche sin deformación
        drawStudioBackground(canvas, w, h, night);

        // 2. Saludo dinámico ("Buenos días, Nancy" / "Buenas tardes, Eder" / "Buenas noches, Eder") + Fecha + Hora
        drawHeaderGreetingAndClock(canvas, w, h, night);

        // 3. Velocímetro digital estilizado cerca de la camioneta
        drawSpeedometerNearCar(canvas, w, h, night);

        // 4. Tarjeta izquierda: Google Maps en tiempo real con GPS y ubicación actual
        drawGoogleMapsCard(canvas, w, h, night);

        // 5. Tarjeta inferior central: Spotify con canción, artista, álbum y progreso dinámico
        drawSpotifyCard(canvas, w, h, night);

        // 6. Tarjeta inferior derecha: Clima en tiempo real (actual, descripción, máx/mín)
        drawWeatherCard(canvas, w, h, night);
    }

    private void drawStudioBackground(Canvas canvas, int w, int h, boolean night) {
        // Degradado vertical continuo emparejado pixel a pixel con el borde izquierdo de la foto
        LinearGradient baseGrad = night
                ? new LinearGradient(0, 0, 0, h,
                        new int[]{0xFF0A121F, 0xFF0F1827, 0xFF060A15},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, 0, h,
                        new int[]{0xFFD8DEF4, 0xFFCDD4EE, 0xFFAEBEDA},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(baseGrad);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        Bitmap bg = night ? bgNight : bgDay;
        if (bg == null || bg.isRecycled()) {
            bg = bgDay;
        }
        if (bg != null && !bg.isRecycled()) {
            float imgW = bg.getWidth();
            float imgH = bg.getHeight();
            float screenAspect = (float) w / (float) h;
            float imgAspect = imgW / imgH;

            float dw, dh, dx, dy;
            float liftY = -h * 0.085f; // Subimos la camioneta para liberar las llantas sobre Spotify y Clima
            if (screenAspect > imgAspect + 0.05f) {
                // Pantalla ultra-ancha: escalamos al alto y anclamos a la derecha para conservar la camioneta íntegra
                float scale = (float) h / imgH;
                dw = imgW * scale;
                dh = h;
                dx = w - dw;
                dy = liftY;
            } else {
                // Pantalla estándar 16:9 / 16:10: ajuste proporcional limpio
                float scale = Math.max((float) w / imgW, (float) h / imgH);
                dw = imgW * scale;
                dh = imgH * scale;
                dx = (w - dw) * 0.65f;
                dy = (h - dh) * 0.50f + liftY;
            }

            rectF.set(dx, dy, dx + dw, dy + dh);
            paint.setAlpha(255);
            canvas.drawBitmap(bg, null, rectF, paint);

            // Extendemos la franja inferior real si al subir la imagen queda espacio por debajo
            float drawnBottom = dy + dh;
            if (drawnBottom < h) {
                android.graphics.Rect srcBot = new android.graphics.Rect(0, bg.getHeight() - 8, bg.getWidth(), bg.getHeight());
                RectF dstBot = new RectF(dx, drawnBottom - 2f, dx + dw, h);
                canvas.drawBitmap(bg, srcBot, dstBot, paint);
            }

            // Extendemos la columna izquierda real (6px) hacia x=0 para una unión 100% invisible
            if (dx > 1f) {
                android.graphics.Rect srcLeft = new android.graphics.Rect(0, 0, 6, bg.getHeight());
                RectF dstLeft = new RectF(0, dy, dx + 2f, dy + dh);
                canvas.drawBitmap(bg, srcLeft, dstLeft, paint);
                if (drawnBottom < h) {
                    android.graphics.Rect srcCorner = new android.graphics.Rect(0, bg.getHeight() - 8, 6, bg.getHeight());
                    RectF dstCorner = new RectF(0, drawnBottom - 2f, dx + 2f, h);
                    canvas.drawBitmap(bg, srcCorner, dstCorner, paint);
                }
            }
        }
    }

    private void drawHeaderGreetingAndClock(Canvas canvas, int w, int h, boolean night) {
        Date now = new Date();
        Locale esLocale = new Locale("es", "MX");

        String greeting = DashboardState.getDynamicGreeting();
        String dateStr = new SimpleDateFormat("EEEE, d 'de' MMMM", esLocale).format(now);
        if (!dateStr.isEmpty()) {
            dateStr = Character.toUpperCase(dateStr.charAt(0)) + dateStr.substring(1);
        }
        String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(now).toLowerCase(Locale.getDefault());

        float leftX = w * 0.030f;
        float greetY = h * 0.112f;
        float dateY = h * 0.172f;

        int primaryText = night ? 0xFFF8FAFC : 0xFF0F172A;
        int secondaryText = night ? 0xFFCBD5E1 : 0xFF334155;

        // Saludo principal
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(h * 0.068f);
        textPaint.setColor(primaryText);
        if (night) {
            textPaint.setShadowLayer(8f, 0f, 2f, 0x66000000);
        } else {
            textPaint.clearShadowLayer();
        }
        canvas.drawText(greeting, leftX, greetY, textPaint);

        // Fecha en español
        textPaint.clearShadowLayer();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(h * 0.036f);
        textPaint.setColor(secondaryText);
        canvas.drawText(dateStr, leftX, dateY, textPaint);

        // Reloj más discreto en esquina superior derecha para dejar espacio al vehículo elevado
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(h * 0.042f);
        textPaint.setColor(primaryText);
        canvas.drawText(timeStr, w * 0.968f, h * 0.078f, textPaint);

        hitPhone.set(w * 0.82f, 0, w, h * 0.12f);
    }

    private void drawSpeedometerNearCar(Canvas canvas, int w, int h, boolean night) {
        // Indicador de velocidad totalmente transparente (sin caja sólida) integrado cerca del coche
        float cx = w * 0.405f;
        float numY = h * 0.315f;

        if (night) {
            textPaint.setShadowLayer(10f, 0f, 2f, 0x99000000);
        } else {
            textPaint.setShadowLayer(8f, 0f, 2f, 0x55FFFFFF);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(h * 0.115f);
        textPaint.setColor(night ? 0xFFF8FAFC : 0xFF0F172A);
        canvas.drawText(String.valueOf(DashboardState.speedKmh), cx, numY, textPaint);

        float unitX = cx + w * 0.036f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(h * 0.030f);
        textPaint.setColor(night ? 0xFFCBD5E1 : 0xFF334155);
        canvas.drawText("km/h", unitX, numY - h * 0.030f, textPaint);

        // Pequeño indicador de estado GPS activo
        textPaint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DashboardState.gpsActive ? 0xFF22C55E : 0xFFF59E0B);
        float dotR = Math.max(4f, h * 0.0065f);
        canvas.drawCircle(unitX + dotR, numY - h * 0.004f, dotR, paint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(h * 0.021f);
        textPaint.setColor(night ? 0xFFCBD5E1 : 0xFF475569);
        canvas.drawText("GPS", unitX + dotR * 3.0f, numY + h * 0.002f, textPaint);
    }

    private void drawGoogleMapsCard(Canvas canvas, int w, int h, boolean night) {
        float cardL = w * 0.025f;
        float cardT = h * 0.225f;
        float cardW = w * 0.305f;
        float cardH = h * 0.705f;
        float cardR = cardW * 0.068f;

        RectF cardRect = new RectF(cardL, cardT, cardL + cardW, cardT + cardH);
        drawGlassCard(canvas, cardRect, cardR, night);

        float pad = cardW * 0.055f;

        // 1. Header: Pin Google Maps + "Google Maps" + Lugar actual en tiempo real
        float pinCenterX = cardL + pad + cardW * 0.065f;
        float pinCenterY = cardT + pad + cardH * 0.055f;
        drawGoogleMapsPin(canvas, pinCenterX, pinCenterY, cardW * 0.060f);

        float titleX = pinCenterX + cardW * 0.090f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.062f);
        textPaint.setColor(night ? 0xFFF8FAFC : 0xFF0F172A);
        canvas.drawText("Google Maps", titleX, cardT + pad + cardH * 0.056f, textPaint);

        String place = (DashboardState.currentPlaceName != null && !DashboardState.currentPlaceName.isEmpty())
                ? DashboardState.currentPlaceName : "Tu ubicación";
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(cardH * 0.036f);
        textPaint.setColor(night ? 0xFF94A3B8 : 0xFF475569);
        float maxPlaceW = (cardL + cardW - pad) - titleX;
        place = ellipsizeText(place, textPaint, maxPlaceW);
        canvas.drawText(place, titleX, cardT + pad + cardH * 0.105f, textPaint);

        // 2. Visor de Mapa en Tiempo Real con GPS
        float mapL = cardL + pad;
        float mapT = cardT + pad + cardH * 0.132f;
        float mapW = cardW - pad * 2f;
        float mapH = cardH * 0.625f;
        float mapR = cardW * 0.048f;
        RectF mapRect = new RectF(mapL, mapT, mapL + mapW, mapT + mapH);

        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(mapRect, mapR, mapR, Path.Direction.CW);
        canvas.clipPath(clipPath);
        MapTileManager.drawLiveMap(
                canvas, mapRect,
                DashboardState.currentLat,
                DashboardState.currentLon,
                DashboardState.currentBearing,
                night
        );
        canvas.restore();

        // Borde sutil del mapa
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(night ? 0x3394A3B8 : 0x220F172A);
        canvas.drawRoundRect(mapRect, mapR, mapR, paint);
        paint.setStyle(Paint.Style.FILL);

        // Botón circular flotante de centrar ubicación GPS dentro del mapa (esquina inferior derecha)
        float gpsBtnR = cardW * 0.068f;
        float gpsBtnX = mapRect.right - gpsBtnR * 1.45f;
        float gpsBtnY = mapRect.bottom - gpsBtnR * 1.45f;

        paint.setColor(night ? 0x33000000 : 0x22000000);
        canvas.drawCircle(gpsBtnX, gpsBtnY + 2f, gpsBtnR + 1.5f, paint);
        paint.setColor(night ? 0xEE1E293B : 0xF8FFFFFF);
        canvas.drawCircle(gpsBtnX, gpsBtnY, gpsBtnR, paint);
        drawCrosshairIcon(canvas, gpsBtnX, gpsBtnY, gpsBtnR * 0.56f, night ? 0xFFF8FAFC : 0xFF0F172A);

        hitMapsCenter.set(gpsBtnX - gpsBtnR * 1.4f, gpsBtnY - gpsBtnR * 1.4f,
                gpsBtnX + gpsBtnR * 1.4f, gpsBtnY + gpsBtnR * 1.4f);

        // 3. Botón Azul "Abrir mapa"
        float btnL = cardL + pad;
        float btnT = mapRect.bottom + cardH * 0.032f;
        float btnW = cardW - pad * 2f;
        float btnH = (cardT + cardH - pad) - btnT;
        float btnR = btnH * 0.34f;
        RectF btnRect = new RectF(btnL, btnT, btnL + btnW, btnT + btnH);

        LinearGradient btnGrad = new LinearGradient(
                btnL, btnT, btnL + btnW, btnT + btnH,
                new int[]{0xFF2563EB, 0xFF1D4ED8}, null, Shader.TileMode.CLAMP);
        paint.setShader(btnGrad);
        canvas.drawRoundRect(btnRect, btnR, btnR, paint);
        paint.setShader(null);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(btnH * 0.38f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Abrir mapa", btnRect.centerX() - btnH * 0.14f, btnRect.centerY() + btnH * 0.13f, textPaint);

        drawExternalLinkIcon(canvas, btnRect.right - btnH * 0.72f, btnRect.centerY(), btnH * 0.28f, Color.WHITE);

        hitMaps.set(btnRect.left, mapRect.top, btnRect.right, btnRect.bottom);
    }

    private void drawSpotifyCard(Canvas canvas, int w, int h, boolean night) {
        float cardL = w * 0.348f;
        float cardT = h * 0.665f;
        float cardW = w * 0.432f;
        float cardH = h * 0.265f;
        float cardR = cardH * 0.14f;

        RectF cardRect = new RectF(cardL, cardT, cardL + cardW, cardT + cardH);
        drawGlassCard(canvas, cardRect, cardR, night);

        float pad = cardH * 0.092f;
        float artSize = cardH * 0.575f;
        float artL = cardL + pad;
        float artT = cardT + pad;
        RectF artRect = new RectF(artL, artT, artL + artSize, artT + artSize);

        // Carátula del álbum
        Bitmap artBmp = (DashboardState.albumArt != null) ? DashboardState.albumArt : defaultArt;
        if (artBmp != null && !artBmp.isRecycled()) {
            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(artRect, artSize * 0.14f, artSize * 0.14f, Path.Direction.CW);
            canvas.clipPath(clipPath);
            paint.setAlpha(255);
            canvas.drawBitmap(artBmp, null, artRect, paint);
            canvas.restore();
        }
        hitSpotify.set(artRect);

        // Info de la pista (Logo Spotify + Canción + Artista + Álbum)
        float infoX = artRect.right + cardW * 0.032f;
        float controlsZoneW = cardW * 0.34f;
        float infoMaxW = (cardL + cardW - pad - controlsZoneW) - infoX;

        // Logo Spotify verde + texto "Spotify"
        float spLogoR = cardH * 0.055f;
        float spLogoX = infoX + spLogoR;
        float spLogoY = artT + spLogoR * 1.15f;
        drawSpotifyBadge(canvas, spLogoX, spLogoY, spLogoR);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.092f);
        textPaint.setColor(night ? 0xFFF1F5F9 : 0xFF0F172A);
        canvas.drawText("Spotify", spLogoX + spLogoR * 1.55f, spLogoY + spLogoR * 0.42f, textPaint);

        // Título de la canción
        String track = (DashboardState.trackTitle != null && !DashboardState.trackTitle.isEmpty())
                ? DashboardState.trackTitle : "Spotify";
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.125f);
        textPaint.setColor(night ? 0xFFFFFFFF : 0xFF0F172A);
        float titleY = artT + cardH * 0.275f;
        canvas.drawText(ellipsizeText(track, textPaint, infoMaxW), infoX, titleY, textPaint);

        // Artista
        String artist = (DashboardState.trackArtist != null && !DashboardState.trackArtist.isEmpty())
                ? DashboardState.trackArtist : "Reproduce tu música";
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(cardH * 0.088f);
        textPaint.setColor(night ? 0xFFCBD5E1 : 0xFF334155);
        float artistY = titleY + cardH * 0.135f;
        canvas.drawText(ellipsizeText(artist, textPaint, infoMaxW), infoX, artistY, textPaint);

        // Álbum explícito debajo del artista
        String album = (DashboardState.trackAlbum != null && !DashboardState.trackAlbum.isEmpty())
                ? DashboardState.trackAlbum : "Álbum no disponible";
        float discR = cardH * 0.036f;
        float albumY = artistY + cardH * 0.120f;
        drawAlbumDiscIcon(canvas, infoX + discR, albumY - discR * 0.72f, discR, night ? 0xFF94A3B8 : 0xFF64748B);

        textPaint.setTextSize(cardH * 0.076f);
        textPaint.setColor(night ? 0xFF94A3B8 : 0xFF64748B);
        canvas.drawText(ellipsizeText(album, textPaint, infoMaxW - discR * 2.8f), infoX + discR * 2.8f, albumY, textPaint);

        // Controles de reproducción a la derecha (Prev, Play/Pause azul grande, Next)
        float ctrlCenterY = artRect.centerY();
        float playR = cardH * 0.175f;
        float playX = cardL + cardW - pad - cardW * 0.155f;
        float stepOffset = cardW * 0.112f;
        float prevX = playX - stepOffset;
        float nextX = playX + stepOffset;

        int iconColor = night ? 0xFFF8FAFC : 0xFF0F172A;
        drawPrevNextIcon(canvas, prevX, ctrlCenterY, playR * 0.45f, true, iconColor);
        hitPrev.set(prevX - playR, ctrlCenterY - playR, prevX + playR, ctrlCenterY + playR);

        // Botón Play/Pause azul vibrante como en la propuesta
        LinearGradient playGrad = new LinearGradient(
                playX - playR, ctrlCenterY - playR, playX + playR, ctrlCenterY + playR,
                new int[]{0xFF2563EB, 0xFF1E40AF}, null, Shader.TileMode.CLAMP);
        paint.setShader(playGrad);
        canvas.drawCircle(playX, ctrlCenterY, playR, paint);
        paint.setShader(null);
        drawPlayPauseGlyph(canvas, playX, ctrlCenterY, playR * 0.44f, DashboardState.isPlaying);
        hitPlayPause.set(playX - playR * 1.15f, ctrlCenterY - playR * 1.15f,
                playX + playR * 1.15f, ctrlCenterY + playR * 1.15f);

        drawPrevNextIcon(canvas, nextX, ctrlCenterY, playR * 0.45f, false, iconColor);
        hitNext.set(nextX - playR, ctrlCenterY - playR, nextX + playR, ctrlCenterY + playR);

        // Barra de progreso inferior + Tiempos (como en la propuesta)
        float barLeft = artL;
        float barRight = cardL + cardW - pad;
        float barY = artRect.bottom + cardH * 0.098f;
        float barW = barRight - barLeft;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(3.5f, cardH * 0.022f));
        paint.setColor(night ? 0xFF334155 : 0xFFCBD5E1);
        canvas.drawLine(barLeft, barY, barRight, barY, paint);

        float progress = DashboardState.getSongProgress();
        if (progress > 0.002f) {
            float curX = barLeft + barW * progress;
            paint.setColor(0xFF2563EB);
            canvas.drawLine(barLeft, barY, curX, barY, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(curX, barY, Math.max(5f, cardH * 0.028f), paint);
        }
        paint.setStyle(Paint.Style.FILL);

        hitProgressBar.set(barLeft, barY - cardH * 0.10f, barRight, barY + cardH * 0.12f);

        // Tiempos debajo de la barra
        long currentPos = DashboardState.getCurrentPositionMs();
        long totalDur = DashboardState.trackDurationMs;
        String curStr = DashboardState.formatTime(currentPos);
        String durStr = (totalDur > 0) ? DashboardState.formatTime(totalDur) : "--:--";

        float timeY = barY + cardH * 0.118f;
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(cardH * 0.076f);
        textPaint.setColor(night ? 0xFF94A3B8 : 0xFF475569);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(curStr, barLeft, timeY, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(durStr, barRight, timeY, textPaint);
    }

    private void drawWeatherCard(Canvas canvas, int w, int h, boolean night) {
        float cardL = w * 0.796f;
        float cardT = h * 0.665f;
        float cardW = w * 0.180f;
        float cardH = h * 0.265f;
        float cardR = cardH * 0.14f;

        RectF cardRect = new RectF(cardL, cardT, cardL + cardW, cardT + cardH);
        drawGlassCard(canvas, cardRect, cardR, night);
        hitWeather.set(cardRect);

        float pad = cardW * 0.095f;
        float leftX = cardL + pad;

        // Etiqueta "Clima"
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.096f);
        textPaint.setColor(night ? 0xFFCBD5E1 : 0xFF334155);
        canvas.drawText("Clima", leftX, cardT + pad + cardH * 0.06f, textPaint);

        // Temperatura actual grande
        String tempStr = (DashboardState.weatherTemp != null && !DashboardState.weatherTemp.isEmpty())
                ? DashboardState.weatherTemp : "--°";
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.285f);
        textPaint.setColor(night ? 0xFFFFFFFF : 0xFF0F172A);
        canvas.drawText(tempStr, leftX, cardT + cardH * 0.50f, textPaint);

        // Ícono vectorial del clima a la derecha
        float iconCenterX = cardL + cardW - pad - cardW * 0.18f;
        float iconCenterY = cardT + cardH * 0.35f;
        drawWeatherVectorIcon(canvas, iconCenterX, iconCenterY, cardH * 0.22f,
                DashboardState.weatherCode, !night && DashboardState.weatherIsDay);

        // Descripción en español
        String desc = (DashboardState.weatherDesc != null && !DashboardState.weatherDesc.isEmpty())
                ? DashboardState.weatherDesc : "Parcialmente nublado";
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(cardH * 0.088f);
        textPaint.setColor(night ? 0xFFE2E8F0 : 0xFF1E293B);
        canvas.drawText(ellipsizeText(desc, textPaint, cardW - pad * 2f), leftX, cardT + cardH * 0.72f, textPaint);

        // Máxima y mínima reales
        String maxMin = "Máx. " + DashboardState.weatherMaxTemp + " • Mín. " + DashboardState.weatherMinTemp;
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(cardH * 0.076f);
        textPaint.setColor(night ? 0xFF94A3B8 : 0xFF475569);
        canvas.drawText(ellipsizeText(maxMin, textPaint, cardW - pad * 2f), leftX, cardT + cardH * 0.87f, textPaint);
    }

    // ==================== HELPERS DE ESTILO E ÍCONOS ====================

    private void drawGlassCard(Canvas canvas, RectF rect, float radius, boolean night) {
        // Sombra suave realista
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(night ? 0x55000000 : 0x1A0F172A);
        canvas.drawRoundRect(
                new RectF(rect.left - 3f, rect.top + 5f, rect.right + 3f, rect.bottom + 12f),
                radius + 2f, radius + 2f, paint);

        // Fondo translúcido estilo glassmorphism
        paint.setColor(night ? 0xD9111827 : 0xE6FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // Borde sutil luminoso
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setColor(night ? 0x4464748B : 0xAAFFFFFF);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private String ellipsizeText(String text, Paint p, float maxW) {
        if (text == null) return "";
        if (p.measureText(text) <= maxW || maxW <= 10f) return text;
        String t = text;
        while (t.length() > 3 && p.measureText(t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private void drawGoogleMapsPin(Canvas canvas, float cx, float cy, float size) {
        // Gota / Pin multicolor estilo Google Maps
        float r = size * 0.65f;
        paint.setStyle(Paint.Style.FILL);

        // Cuerpo principal azul/rojo/verde/amarillo simulado en capas nítidas
        Path pin = new Path();
        pin.addCircle(cx, cy - r * 0.25f, r, Path.Direction.CW);
        pin.moveTo(cx - r * 0.88f, cy + r * 0.12f);
        pin.lineTo(cx, cy + r * 1.45f);
        pin.lineTo(cx + r * 0.88f, cy + r * 0.12f);
        pin.close();

        paint.setColor(0xFF34A853); // Verde abajo
        canvas.drawPath(pin, paint);

        // Arco superior rojo
        RectF topArc = new RectF(cx - r, cy - r * 1.25f, cx + r, cy + r * 0.75f);
        paint.setColor(0xFFEA4335);
        canvas.drawArc(topArc, 210, 150, true, paint);

        // Arco derecho azul
        paint.setColor(0xFF4285F4);
        canvas.drawArc(topArc, 0, 95, true, paint);

        // Arco izquierdo amarillo
        paint.setColor(0xFFFBBC05);
        canvas.drawArc(topArc, 130, 85, true, paint);

        // Centro blanco
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy - r * 0.25f, r * 0.42f, paint);
    }

    private void drawCrosshairIcon(Canvas canvas, float cx, float cy, float r, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.2f, r * 0.18f));
        paint.setColor(color);
        canvas.drawCircle(cx, cy, r * 0.62f, paint);
        canvas.drawLine(cx - r, cy, cx - r * 0.45f, cy, paint);
        canvas.drawLine(cx + r * 0.45f, cy, cx + r, cy, paint);
        canvas.drawLine(cx, cy - r, cx, cy - r * 0.45f, paint);
        canvas.drawLine(cx, cy + r * 0.45f, cx, cy + r, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.22f, paint);
    }

    private void drawExternalLinkIcon(Canvas canvas, float cx, float cy, float r, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(2.5f, r * 0.22f));
        paint.setColor(color);

        // Caja abierta
        Path box = new Path();
        box.moveTo(cx - r * 0.1f, cy - r * 0.85f);
        box.lineTo(cx - r * 0.85f, cy - r * 0.85f);
        box.lineTo(cx - r * 0.85f, cy + r * 0.85f);
        box.lineTo(cx + r * 0.85f, cy + r * 0.85f);
        box.lineTo(cx + r * 0.85f, cy + r * 0.1f);
        canvas.drawPath(box, paint);

        // Flecha diagonal
        canvas.drawLine(cx - r * 0.15f, cy + r * 0.15f, cx + r * 0.85f, cy - r * 0.85f, paint);
        canvas.drawLine(cx + r * 0.25f, cy - r * 0.85f, cx + r * 0.85f, cy - r * 0.85f, paint);
        canvas.drawLine(cx + r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy - r * 0.25f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSpotifyBadge(Canvas canvas, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1DB954);
        canvas.drawCircle(cx, cy, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF0F172A);
        paint.setStrokeWidth(Math.max(1.8f, r * 0.16f));

        for (int i = 0; i < 3; i++) {
            float offset = (i - 1) * r * 0.34f;
            float arcW = r * (0.65f - i * 0.12f);
            RectF arcR = new RectF(cx - arcW, cy + offset - r * 0.15f, cx + arcW, cy + offset + r * 0.45f);
            canvas.drawArc(arcR, 215, 110, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawAlbumDiscIcon(Canvas canvas, float cx, float cy, float r, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, r * 0.28f));
        paint.setColor(color);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.32f, paint);
    }

    private void drawWeatherVectorIcon(Canvas canvas, float cx, float cy, float size, int code, boolean isDay) {
        boolean hasRain = (code >= 51 && code <= 82) || code >= 95;
        boolean fullCloud = (code == 3 || code == 45 || code == 48 || hasRain);
        boolean partlyCloudy = (code == 1 || code == 2);

        if (isDay && !fullCloud) {
            // Sol brillante con rayos
            float sunR = partlyCloudy ? size * 0.40f : size * 0.52f;
            float sx = partlyCloudy ? cx + size * 0.18f : cx;
            float sy = partlyCloudy ? cy - size * 0.18f : cy;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2.5f, size * 0.08f));
            paint.setColor(0xFFF59E0B);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                float r1 = sunR * 1.25f;
                float r2 = sunR * 1.60f;
                canvas.drawLine(
                        sx + (float) Math.cos(ang) * r1, sy + (float) Math.sin(ang) * r1,
                        sx + (float) Math.cos(ang) * r2, sy + (float) Math.sin(ang) * r2, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            LinearGradient sunGrad = new LinearGradient(sx, sy - sunR, sx, sy + sunR,
                    new int[]{0xFFFDE047, 0xFFF59E0B}, null, Shader.TileMode.CLAMP);
            paint.setShader(sunGrad);
            canvas.drawCircle(sx, sy, sunR, paint);
            paint.setShader(null);
        } else if (!isDay && !fullCloud) {
            // Luna creciente nocturna
            float moonR = partlyCloudy ? size * 0.38f : size * 0.48f;
            float mx = partlyCloudy ? cx + size * 0.18f : cx;
            float my = partlyCloudy ? cy - size * 0.18f : cy;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFDE68A);
            canvas.drawCircle(mx, my, moonR, paint);
            paint.setColor(0xFF111827);
            canvas.drawCircle(mx + moonR * 0.42f, my - moonR * 0.25f, moonR * 0.85f, paint);
        }

        if (partlyCloudy || fullCloud) {
            float clX = partlyCloudy ? cx - size * 0.08f : cx;
            float clY = partlyCloudy ? cy + size * 0.16f : cy;
            float clR = size * 0.32f;

            paint.setStyle(Paint.Style.FILL);
            LinearGradient cloudGrad = new LinearGradient(
                    clX, clY - clR, clX, clY + clR * 0.8f,
                    new int[]{0xFFFFFFFF, 0xFFDBEAFE}, null, Shader.TileMode.CLAMP);
            paint.setShader(cloudGrad);
            canvas.drawCircle(clX - clR * 0.55f, clY + clR * 0.15f, clR * 0.65f, paint);
            canvas.drawCircle(clX + clR * 0.05f, clY - clR * 0.18f, clR * 0.82f, paint);
            canvas.drawCircle(clX + clR * 0.68f, clY + clR * 0.15f, clR * 0.62f, paint);
            canvas.drawRoundRect(
                    new RectF(clX - clR * 1.18f, clY - clR * 0.10f, clX + clR * 1.28f, clY + clR * 0.78f),
                    clR * 0.44f, clR * 0.44f, paint);
            paint.setShader(null);

            // Borde suave azulado para resaltar sobre fondo claro u oscuro
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2.0f, size * 0.05f));
            paint.setColor(0xFF60A5FA);
            canvas.drawRoundRect(
                    new RectF(clX - clR * 1.18f, clY - clR * 0.10f, clX + clR * 1.28f, clY + clR * 0.78f),
                    clR * 0.44f, clR * 0.44f, paint);
            paint.setStyle(Paint.Style.FILL);

            if (hasRain) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(Math.max(2.5f, size * 0.07f));
                paint.setColor(0xFF38BDF8);
                for (int i = -1; i <= 1; i++) {
                    float rx = clX + i * clR * 0.55f;
                    float ry = clY + clR * 0.95f;
                    canvas.drawLine(rx, ry, rx - clR * 0.18f, ry + clR * 0.45f, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawPlayPauseGlyph(Canvas canvas, float cx, float cy, float r, boolean playing) {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        if (playing) {
            float barW = r * 0.34f;
            float barH = r * 1.05f;
            float gap = r * 0.24f;
            canvas.drawRoundRect(new RectF(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f), 3f, 3f, paint);
            canvas.drawRoundRect(new RectF(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f), 3f, 3f, paint);
        } else {
            Path p = new Path();
            p.moveTo(cx - r * 0.35f, cy - r * 0.75f);
            p.lineTo(cx + r * 0.75f, cy);
            p.lineTo(cx - r * 0.35f, cy + r * 0.75f);
            p.close();
            canvas.drawPath(p, paint);
        }
    }

    private void drawPrevNextIcon(Canvas canvas, float cx, float cy, float r, boolean isPrev, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        Path p = new Path();
        if (isPrev) {
            p.moveTo(cx + r * 0.65f, cy - r * 0.75f);
            p.lineTo(cx - r * 0.35f, cy);
            p.lineTo(cx + r * 0.65f, cy + r * 0.75f);
            p.close();
            canvas.drawPath(p, paint);
            canvas.drawRoundRect(new RectF(cx - r * 0.75f, cy - r * 0.75f, cx - r * 0.44f, cy + r * 0.75f), 2f, 2f, paint);
        } else {
            p.moveTo(cx - r * 0.65f, cy - r * 0.75f);
            p.lineTo(cx + r * 0.35f, cy);
            p.lineTo(cx - r * 0.65f, cy + r * 0.75f);
            p.close();
            canvas.drawPath(p, paint);
            canvas.drawRoundRect(new RectF(cx + r * 0.44f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f), 2f, 2f, paint);
        }
    }
}
