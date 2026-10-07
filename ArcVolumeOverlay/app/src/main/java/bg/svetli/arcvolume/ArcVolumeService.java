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
import android.content.SharedPreferences;
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
import android.view.WindowManager;
import android.widget.TextView;

public class ArcVolumeService extends Service {
    public static final String EXTRA_TEST = "test_overlay";
    public static final String KEY_ARC_VOLUME = "volume_music_hdmi_arc";

    private static final String CHANNEL_ID = "arc_volume_listener";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private TextView overlayView;
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
            handler.removeCallbacks(readAndMaybeShow);
            handler.postDelayed(readAndMaybeShow, 300);
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
                handler.postDelayed(readAndMaybeShow, 90);
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
                showVolume(value >= 0 ? value : 20);
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

    private void showVolume(int value) {
        if (!Settings.canDrawOverlays(this) || windowManager == null) return;

        handler.removeCallbacks(hideOverlay);
        removeOverlay();

        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        int xDp = p.getInt("x_dp", 96);
        int yDp = p.getInt("y_dp", 0);
        int textSp = p.getInt("text_sp", 20);
        int durationMs = p.getInt("duration_ms", 2800);

        // TCL stores the HDMI-ARC CEC volume on a 0..100 scale, while the
        // Sony HT-RT3 front display uses a 0..50 scale. Convert the value
        // shown by the overlay to the same number the Sony displays.
        int displayValue = Math.round(value / 2.0f);

        TextView tv = new TextView(this);
        tv.setText(String.valueOf(displayValue));
        // The native TCL ARC OSD uses a clean white foreground. Keep the
        // number the same pure white and remove our own panel completely so
        // the native dark OSD becomes the only visible background.
        tv.setTextColor(Color.rgb(255, 255, 255));
        tv.setTextSize(textSp);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        tv.setPadding(0, 0, 0, 0);
        tv.setBackgroundColor(Color.TRANSPARENT);

        // TCL's volume window is 120 logical px wide. On this TV the UI
        // density is 2x, so 60dp matches the native panel width exactly.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(60),
                dp(42),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = dp(xDp);
        lp.y = dp(yDp);
        lp.setTitle("ARC Volume Number");

        try {
            windowManager.addView(tv, lp);
            overlayView = tv;
            overlayAttached = true;
            handler.postDelayed(hideOverlay, durationMs);
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

    private int dp(int value) {
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
                .setContentText("Listening for TCL ARC volume changes")
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
