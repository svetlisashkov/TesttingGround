package bg.svetli.arcvolume;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class VolumeKeyAccessibilityService extends AccessibilityService {
    public static volatile boolean isConnected = false;
    private static volatile VolumeKeyAccessibilityService instance;
    // Exact TCL ARC SystemUI window geometry observed with dumpsys:
    // RIGHT|CENTER_VERTICAL, x=50, y=0, w=120, h=400.
    private static final int SYS_W_PX = 120;
    private static final int SYS_H_PX = 400;
    private static final int SYS_X_PX = 50;
    private static final int SYS_Y_PX = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private TextView numberView;
    private View fillView;
    private int trackHeightPx;
    private int lastDisplayed = -1;
    private int lastFillHeight = -1;
    private WindowManager.LayoutParams windowParams;
    private final Runnable hide = this::hideOverlay;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ArcVolumeService.ACTION_SHOW_ACCESSIBILITY_OSD.equals(intent.getAction())) {
                int raw = intent.getIntExtra(ArcVolumeService.EXTRA_RAW_VOLUME, -1);
                if (raw >= 0) showVolume(raw);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isConnected = true;
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(receiver, new IntentFilter(ArcVolumeService.ACTION_SHOW_ACCESSIBILITY_OSD));
        ensureOverlay();

        // Keep the Settings observer alive.
        Intent service = new Intent(this, ArcVolumeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    public static boolean showNow(int rawValue) {
        VolumeKeyAccessibilityService s = instance;
        if (s == null || !isConnected || s.windowManager == null) return false;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            s.showVolume(rawValue);
        } else {
            s.handler.post(() -> s.showVolume(rawValue));
        }
        return true;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void showVolume(int rawValue) {
        if (windowManager == null) return;
        handler.removeCallbacks(hide);

        ensureOverlay();
        if (overlay == null || numberView == null || fillView == null) return;

        // The caller may pass an early, speculative volume estimate. Use it
        // only as a trigger; the visible number must come from TCL's actual
        // ARC setting. Never render a prediction as a confirmed Sony value.
        int confirmed = Settings.System.getInt(getContentResolver(),
                ArcVolumeService.KEY_ARC_VOLUME, -1);
        if (confirmed >= 0) {
            int value = Math.max(0, Math.min(100, confirmed));
            int displayValue = getSharedPreferences("arc_volume_settings", MODE_PRIVATE)
                    .getInt("volume_scale", 50) == 100 ? value : Math.round(value / 2.0f);

            if (displayValue != lastDisplayed) {
                numberView.setText(String.valueOf(displayValue));
                lastDisplayed = displayValue;
            }
            FrameLayout.LayoutParams fp =
                    (FrameLayout.LayoutParams) fillView.getLayoutParams();
            int fillHeight = Math.max(0, Math.round(trackHeightPx * (value / 100.0f)));
            if (fillHeight != lastFillHeight) {
                fp.height = fillHeight;
                fillView.setLayoutParams(fp);
                lastFillHeight = fillHeight;
            }
        }

        // The window/surface already exists above TCL SystemUI. Revealing it is just
        // an alpha change, avoiding the ~80-100 ms addView/first-draw delay seen in v10.
        overlay.animate().cancel();
        overlay.setAlpha(1.0f);
        handler.postDelayed(hide, 3200);
    }

    private void ensureOverlay() {
        if (windowManager == null || overlay != null) return;

        FrameLayout root = new FrameLayout(this);
        root.setBackground(rounded(Color.rgb(17, 18, 20), 4.0f));
        root.setAlpha(0.0f);

        TextView number = new TextView(this);
        number.setText("0");
        number.setTextColor(Color.WHITE);
        number.setTextSize(16);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.NORMAL));
        FrameLayout.LayoutParams numberLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(34));
        numberLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        numberLp.topMargin = dp(18);
        root.addView(number, numberLp);

        FrameLayout track = new FrameLayout(this);
        track.setBackground(rounded(Color.rgb(105, 108, 112), 3.0f));
        trackHeightPx = dp(92);
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(dp(6), trackHeightPx);
        trackLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        trackLp.topMargin = dp(62);
        root.addView(track, trackLp);

        View fill = new View(this);
        fill.setBackground(rounded(Color.WHITE, 3.0f));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0);
        fillLp.gravity = Gravity.BOTTOM;
        track.addView(fill, fillLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_volume);
        icon.setColorFilter(Color.WHITE);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(27), dp(27));
        iconLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        iconLp.bottomMargin = dp(15);
        root.addView(icon, iconLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                SYS_W_PX,
                SYS_H_PX,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        SharedPreferences prefs = getSharedPreferences("osd_position", MODE_PRIVATE);
        lp.x = SYS_X_PX + prefs.getInt("offset_x", 0);
        lp.y = SYS_Y_PX + prefs.getInt("offset_y", 0);
        lp.setTitle("ARC Volume Accessibility OSD");

        try {
            windowManager.addView(root, lp);
            windowParams = lp;
            overlay = root;
            numberView = number;
            fillView = fill;
        } catch (Exception ignored) {
            overlay = null;
            numberView = null;
            fillView = null;
            trackHeightPx = 0;
        }
    }

    public static void refreshPosition() {
        VolumeKeyAccessibilityService s = instance;
        if (s != null) s.handler.post(s::applyPosition);
    }

    private void applyPosition() {
        if (overlay == null || windowParams == null || windowManager == null) return;
        SharedPreferences prefs = getSharedPreferences("osd_position", MODE_PRIVATE);
        windowParams.x = SYS_X_PX + prefs.getInt("offset_x", 0);
        windowParams.y = SYS_Y_PX + prefs.getInt("offset_y", 0);
        try { windowManager.updateViewLayout(overlay, windowParams); } catch (Exception ignored) {}
    }

    private void hideOverlay() {
        if (overlay != null) {
            overlay.animate().cancel();
            overlay.setAlpha(0.0f);
        }
    }

    private void removeOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
        numberView = null;
        fillView = null;
        trackHeightPx = 0;
        lastDisplayed = -1;
        lastFillHeight = -1;
        windowParams = null;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        isConnected = false;
        instance = null;
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
