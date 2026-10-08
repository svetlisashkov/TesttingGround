package bg.svetli.arcvolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ArcVolumeService extends Service {
    public static final String EXTRA_TEST = "test_overlay";
    public static final String KEY_ARC_VOLUME = "volume_music_hdmi_arc";
    public static final String ACTION_SHOW_ACCESSIBILITY_OSD = "bg.svetli.arcvolume.SHOW_ACCESSIBILITY_OSD";
    public static final String EXTRA_RAW_VOLUME = "raw_volume";

    private static final String CHANNEL_ID = "arc_volume_listener";
    private static final int NOTIFICATION_ID = 1001;

    // Measured directly from the TCL 98P745 SystemUI volume window:
    // logical display 1920x1080, RIGHT|CENTER, x=50, y=0, requested 120x400.
    private static final int SYS_W_PX = 120;
    private static final int SYS_H_PX = 400;
    private static final int SYS_X_PX = 50;
    private static final int SYS_Y_PX = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayView;
    private boolean overlayAttached = false;
    private int lastVolume = -1;
    private final Runnable hideOverlay = this::removeOverlay;

    private final Runnable readAndMaybeShow = new Runnable() {
        @Override public void run() {
            int value = readArcVolume();
            if (value >= 0 && value != lastVolume) {
                lastVolume = value;
                showVolume(value);
            }
        }
    };

    private ContentObserver volumeObserver;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!"android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) return;

            final int stream = intent.getIntExtra(
                    "android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
            if (stream != android.media.AudioManager.STREAM_MUSIC) return;

            // Cover the TCL OSD immediately, but never invent an intermediate
            // Sony value. Fast CEC notifications can otherwise cause oscillation.
            int confirmed = readArcVolume();
            if (confirmed >= 0) {
                lastVolume = confirmed;
                showVolume(confirmed);
            } else if (lastVolume >= 0) {
                showVolume(lastVolume);
            }

            // A short safety read catches CEC changes when the ContentObserver
            // notification is delayed or skipped by the TV firmware.
            handler.removeCallbacks(readAndMaybeShow);
            handler.postDelayed(readAndMaybeShow, 180);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        lastVolume = readArcVolume();

        final ContentResolver cr = getContentResolver();
        Uri uri = Settings.System.getUriFor(KEY_ARC_VOLUME);
        volumeObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                handler.removeCallbacks(readAndMaybeShow);
                handler.postDelayed(readAndMaybeShow, 45);
            }
        };
        cr.registerContentObserver(uri, false, volumeObserver);

        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_TEST, false)) {
            handler.postDelayed(() -> {
                int value = readArcVolume();
                showVolume(value >= 0 ? value : 28);
            }, 100);
        }
        return START_STICKY;
    }

    private int readArcVolume() {
        try {
            return Settings.System.getInt(getContentResolver(), KEY_ARC_VOLUME, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private boolean isAccessibilityOverlayEnabled() {
        return VolumeKeyAccessibilityService.isConnected;
    }

    private void showVolume(int rawValue) {
        // Accessibility overlays are trusted system overlays. Route through the live
        // bound accessibility service so Android does not clamp us to the untrusted
        // APPLICATION_OVERLAY layer/opacity.
        if (isAccessibilityOverlayEnabled()
                && VolumeKeyAccessibilityService.showNow(rawValue)) {
            return;
        }

        // Fallback for the case where Accessibility is not enabled yet.
        if (!Settings.canDrawOverlays(this) || windowManager == null) return;

        handler.removeCallbacks(hideOverlay);
        removeOverlay();

        int value = Math.max(0, Math.min(100, rawValue));
        int displayValue = Math.round(value / 2.0f); // Sony HT-RT3 display scale: 0..50.

        FrameLayout root = new FrameLayout(this);
        root.setBackground(rounded(Color.argb(238, 17, 18, 20), 4.0f));

        FrameLayout panel = root;

        TextView number = new TextView(this);
        number.setText(String.valueOf(displayValue));
        number.setTextColor(Color.WHITE);
        number.setTextSize(16);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        FrameLayout.LayoutParams numberLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(34));
        numberLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        numberLp.topMargin = dp(21);
        number.setTranslationX(-dp(1));
        panel.addView(number, numberLp);

        // Native-TV-like vertical volume track.
        FrameLayout track = new FrameLayout(this);
        track.setBackground(rounded(Color.rgb(105, 108, 112), 3.0f));
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(dp(6), dp(102));
        trackLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        trackLp.topMargin = dp(57);
        panel.addView(track, trackLp);

        View fill = new View(this);
        fill.setBackground(rounded(Color.WHITE, 3.0f));
        int trackHeight = dp(102);
        int fillHeight = Math.max(0, Math.round(trackHeight * (value / 100.0f)));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, fillHeight);
        fillLp.gravity = Gravity.BOTTOM;
        track.addView(fill, fillLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_volume);
        icon.setColorFilter(Color.WHITE);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(25), dp(25));
        iconLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        iconLp.bottomMargin = dp(10);
        panel.addView(icon, iconLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                SYS_W_PX,
                SYS_H_PX,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = SYS_X_PX;
        lp.y = SYS_Y_PX;
        lp.setTitle("ARC Volume System OSD Cover");

        try {
            windowManager.addView(root, lp);
            overlayView = root;
            overlayAttached = true;
            // Slightly longer than TCL's own OSD so the +/- ARC UI never peeks out at the end.
            handler.postDelayed(hideOverlay, 3500);
        } catch (Exception ignored) {
            overlayView = null;
            overlayAttached = false;
        }
    }

    private void removeOverlay() {
        if (overlayAttached && overlayView != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlayView); } catch (Exception ignored) {}
        }
        overlayView = null;
        overlayAttached = false;
    }

    private int dp(float value) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ARC Volume listener",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Keeps the ARC volume number listener active.");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setSmallIcon(R.drawable.ic_volume)
                .setContentTitle("ARC Volume")
                .setContentText("TCL ARC volume OSD replacement is active")
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        try { if (volumeObserver != null) getContentResolver().unregisterContentObserver(volumeObserver); } catch (Exception ignored) {}
        try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
