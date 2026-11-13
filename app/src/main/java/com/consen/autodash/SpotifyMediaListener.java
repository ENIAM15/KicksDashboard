package com.consen.autodash;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.view.KeyEvent;

import java.util.List;

public class SpotifyMediaListener extends NotificationListenerService {

    private static MediaController activeController = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updateFromController(activeController);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            updateFromController(activeController);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        refreshSessions(this);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        refreshSessions(this);
    }

    public static void refreshSessions(Context ctx) {
        try {
            MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            ComponentName cn = new ComponentName(ctx, SpotifyMediaListener.class);
            List<MediaController> controllers = msm.getActiveSessions(cn);
            if (controllers == null || controllers.isEmpty()) return;

            String ownPkg = ctx.getPackageName();
            MediaController chosen = null;
            for (MediaController mc : controllers) {
                if ("com.spotify.music".equals(mc.getPackageName())) {
                    chosen = mc;
                    break;
                }
            }
            if (chosen == null) {
                for (MediaController mc : controllers) {
                    if (mc.getPackageName() != null && !mc.getPackageName().equals(ownPkg)) {
                        chosen = mc;
                        break;
                    }
                }
            }
            if (chosen != null) {
                if (activeController != chosen) {
                    if (activeController != null) {
                        try { activeController.unregisterCallback(controllerCallback); } catch (Throwable ignored) {}
                    }
                    activeController = chosen;
                    try { activeController.registerCallback(controllerCallback, mainHandler); } catch (Throwable ignored) {}
                }
                updateFromController(chosen);
            }
        } catch (Throwable ignored) {}
    }

    public static void updateFromController(MediaController mc) {
        if (mc == null) return;
        try {
            PlaybackState ps = mc.getPlaybackState();
            if (ps != null) {
                DashboardState.isPlaying = (ps.getState() == PlaybackState.STATE_PLAYING);
                DashboardState.playbackPositionMs = ps.getPosition();
                long updateTime = ps.getLastPositionUpdateTime();
                DashboardState.lastPositionUpdateTime = (updateTime > 0) ? updateTime : SystemClock.elapsedRealtime();
                DashboardState.playbackSpeed = ps.getPlaybackSpeed() > 0 ? ps.getPlaybackSpeed() : 1.0f;
            }

            MediaMetadata meta = mc.getMetadata();
            if (meta != null) {
                String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                String album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM);
                if (title != null && !title.isEmpty()) {
                    DashboardState.trackTitle = title;
                }
                if (artist != null && !artist.isEmpty()) {
                    DashboardState.trackArtist = artist;
                }
                DashboardState.trackAlbum = (album != null) ? album : "";

                long dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (dur > 0) {
                    DashboardState.trackDurationMs = dur;
                }

                Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) {
                    art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
                }
                if (art != null) {
                    DashboardState.albumArt = art;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void seekTo(long positionMs) {
        if (activeController == null) return;
        try {
            MediaController.TransportControls tc = activeController.getTransportControls();
            if (tc != null) {
                tc.seekTo(positionMs);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean dispatchTransportKey(int keyCode) {
        if (activeController == null) return false;
        try {
            MediaController.TransportControls tc = activeController.getTransportControls();
            if (tc == null) return false;
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (DashboardState.isPlaying) {
                    tc.pause();
                } else {
                    tc.play();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                tc.skipToNext();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                tc.skipToPrevious();
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
