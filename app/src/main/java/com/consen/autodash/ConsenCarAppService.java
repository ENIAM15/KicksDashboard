package com.consen.autodash;

import android.content.Intent;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

public class ConsenCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                GpsSpeedTracker.start(getCarContext());
                WeatherService.startPeriodicWeather(getCarContext());
                SpotifyMediaListener.refreshSessions(getCarContext());
                return new ConsenCarScreen(getCarContext());
            }
        };
    }

    public static class ConsenCarScreen extends Screen implements SurfaceCallback {

        private Surface surface;
        private int surfaceW = 0;
        private int surfaceH = 0;
        private final DashboardCanvasRenderer renderer;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean rendering = false;

        private final Runnable frameLoop = new Runnable() {
            @Override
            public void run() {
                if (!rendering) return;
                drawSurfaceFrame();
                handler.postDelayed(this, 40); // ~25 FPS en Android Auto
            }
        };

        public ConsenCarScreen(@NonNull CarContext carContext) {
            super(carContext);
            renderer = new DashboardCanvasRenderer(carContext);
            try {
                carContext.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable ignored) {}
        }

        private void drawSurfaceFrame() {
            if (surface == null || !surface.isValid() || surfaceW <= 0 || surfaceH <= 0) return;
            Canvas canvas = null;
            try {
                canvas = surface.lockCanvas(null);
                if (canvas != null) {
                    renderer.render(canvas, surfaceW, surfaceH, true);
                }
            } catch (Throwable ignored) {
            } finally {
                if (canvas != null) {
                    try {
                        surface.unlockCanvasAndPost(canvas);
                    } catch (Throwable ignored) {}
                }
            }
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
            this.surface = surfaceContainer.getSurface();
            this.surfaceW = surfaceContainer.getWidth();
            this.surfaceH = surfaceContainer.getHeight();
            if (!rendering && this.surface != null) {
                rendering = true;
                handler.post(frameLoop);
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
            rendering = false;
            handler.removeCallbacks(frameLoop);
            this.surface = null;
        }

        @Override
        public void onClick(float x, float y) {
            if (renderer.hitPlayPause.contains(x, y)) {
                DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return;
            }
            if (renderer.hitPrev.contains(x, y)) {
                DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                return;
            }
            if (renderer.hitNext.contains(x, y)) {
                DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_NEXT);
                return;
            }
            if (renderer.hitProgressBar.contains(x, y)) {
                float ratio = (x - renderer.hitProgressBar.left) / renderer.hitProgressBar.width();
                DashboardState.seekTo(ratio);
                return;
            }
            if (renderer.hitPhone.contains(x, y)) {
                DashboardState.openDialer(getCarContext());
                return;
            }
            if (renderer.hitSpotify.contains(x, y)) {
                DashboardState.openSpotify(getCarContext());
                return;
            }
            if (renderer.hitMapsCenter.contains(x, y)) {
                GpsSpeedTracker.seedLastKnownLocation(getCarContext(), true);
                return;
            }
            if (renderer.hitMaps.contains(x, y)) {
                DashboardState.openMaps(getCarContext());
                return;
            }
            if (renderer.hitWeather.contains(x, y)) {
                WeatherService.forceRefresh(getCarContext());
                return;
            }
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            ActionStrip actionStrip = new ActionStrip.Builder()
                    .addAction(new Action.Builder()
                            .setTitle("⏮")
                            .setOnClickListener(() -> DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                            .build())
                    .addAction(new Action.Builder()
                            .setTitle("⏯")
                            .setOnClickListener(() -> DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                            .build())
                    .addAction(new Action.Builder()
                            .setTitle("⏭")
                            .setOnClickListener(() -> DashboardState.sendMediaKey(getCarContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
                            .build())
                    .build();

            return new NavigationTemplate.Builder()
                    .setActionStrip(actionStrip)
                    .setBackgroundColor(CarColor.PRIMARY)
                    .build();
        }
    }
}
