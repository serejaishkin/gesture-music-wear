package com.example.gesturemusicwear;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.List;

/**
 * Sends next/previous/play-pause to the real active media session.
 * Needs notification access for MediaListenerService; without it falls back to media keys
 * (with real event timestamps). Always returns a one-line diagnostic of what happened.
 */
public final class PlayerControl {
    private PlayerControl() {}

    public static boolean hasListenerAccess(Context ctx) {
        try {
            String s = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            return s != null && s.contains(ctx.getPackageName() + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    public static String adbGrantCommand(Context ctx) {
        return "adb shell cmd notification allow_listener " + ctx.getPackageName()
                + "/" + MediaListenerService.class.getName();
    }

    public static String send(Context ctx, int keycode) {
        StringBuilder d = new StringBuilder("key=").append(keycode);
        try {
            MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(ctx, MediaListenerService.class));
            d.append(" sessions=").append(list == null ? 0 : list.size());
            MediaController target = pick(list);
            if (target != null) {
                PlaybackState st = target.getPlaybackState();
                int state = st == null ? PlaybackState.STATE_NONE : st.getState();
                long actions = st == null ? 0 : st.getActions();
                d.append(" target=").append(target.getPackageName()).append(" state=").append(state);
                MediaController.TransportControls tc = target.getTransportControls();
                if (keycode == KeyEvent.KEYCODE_MEDIA_NEXT
                        && (st == null || (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0)) {
                    tc.skipToNext();
                    return d.append(" ->skipToNext").toString();
                }
                if (keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        && (st == null || (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0)) {
                    tc.skipToPrevious();
                    return d.append(" ->skipToPrevious").toString();
                }
                if (keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    if (state == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
                    return d.append(" ->").append(state == PlaybackState.STATE_PLAYING ? "pause" : "play").toString();
                }
                // Action not advertised: hand the key to this exact session.
                long t = SystemClock.uptimeMillis();
                target.dispatchMediaButtonEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keycode, 0));
                target.dispatchMediaButtonEvent(new KeyEvent(t, t + 60, KeyEvent.ACTION_UP, keycode, 0));
                return d.append(" ->controller.dispatchMediaButtonEvent").toString();
            }
        } catch (SecurityException e) {
            d.append(" НЕТ ДОСТУПА К УВЕДОМЛЕНИЯМ");
        } catch (Throwable t) {
            d.append(" err=").append(t.getClass().getSimpleName());
        }
        return d.append(" ").append(dispatchKey(ctx, keycode)).toString();
    }

    private static MediaController pick(List<MediaController> list) {
        if (list == null || list.isEmpty()) return null;
        for (MediaController c : list) {
            PlaybackState s = c == null ? null : c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return c;
        }
        return list.get(0);
    }

    private static String dispatchKey(Context ctx, int keycode) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keycode, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t + 60, KeyEvent.ACTION_UP, keycode, 0));
            return "->AudioManager musicActive=" + am.isMusicActive();
        } catch (Throwable t) {
            return "->AudioManager failed " + t.getClass().getSimpleName();
        }
    }
}
