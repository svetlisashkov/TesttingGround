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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class VolumeKeyAccessibilityService extends AccessibilityService {
    public static volatile boolean isConnected = false;
    // Exact TCL ARC SystemUI window geometry observed with dumpsys:
    // RIGHT|CENTER_VERTICAL, x=50, y=0, w=120, h=400.
    private static final int SYS_W_PX = 120;
    private static final int SYS_H_PX = 400;
    private static final int SYS_X_PX = 50;
    private static final int SYS_Y_PX = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private final Runnable hide = this::removeOverlay;

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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(receiver, new IntentFilter(ArcVolumeService.ACTION_SHOW_ACCESSIBILITY_OSD));

        // Keep the Settings observer alive.
        Intent service = new Intent(this, ArcVolumeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
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
        removeOverlay();

        int value = Math.max(0, Math.min(100, rawValue));
        int displayValue = Math.round(value / 2.0f);

        // Full 120x400 dark panel: same footprint as TCL's native ARC window.
        // Because TYPE_ACCESSIBILITY_OVERLAY is above SYSTEM_ERROR, this completely
        // hides the original +/- ARC OSD underneath it.
        FrameLayout root = new FrameLayout(this);
        root.setBackground(rounded(Color.argb(242, 17, 18, 20), 4.0f));

        TextView number = new TextView(this);
        number.setText(String.valueOf(displayValue));
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
        int trackHeight = dp(92);
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(dp(6), trackHeight);
        trackLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        trackLp.topMargin = dp(62);
        root.addView(track, trackLp);

        View fill = new View(this);
        fill.setBackground(rounded(Color.WHITE, 3.0f));
        int fillHeight = Math.max(0, Math.round(trackHeight * (value / 100.0f)));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, fillHeight);
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
        lp.x = SYS_X_PX;
        lp.y = SYS_Y_PX;
        lp.setTitle("ARC Volume Accessibility OSD");

        try {
            windowManager.addView(root, lp);
            overlay = root;
            handler.postDelayed(hide, 3200);
        } catch (Exception ignored) {
            overlay = null;
        }
    }

    private void removeOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        isConnected = false;
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
