package bg.svetli.arcvolume;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (Settings.canDrawOverlays(this)) startListener(false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(50), dp(32), dp(50), dp(32));
        root.setBackgroundColor(Color.rgb(16,16,16));

        TextView title = text("ARC Volume", 30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Замества визуално TCL ARC плъзгача със стил като системния TV volume OSD.\n" +
                "Позицията е фиксирана точно върху системния прозорец: 120×400, x=50.",
                16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(12), 0, dp(24));
        root.addView(subtitle);

        status = text("", 18);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        Button permission = button("Разреши показване върху други приложения");
        permission.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permission);

        Button test = button("Тест – покажи OSD");
        test.setOnClickListener(v -> startListener(true));
        root.addView(test);

        setContentView(root);
        refreshStatus();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try { startActivity(i); } catch (Exception ignored) {}
        }
    }

    private void startListener(boolean test) {
        if (!Settings.canDrawOverlays(this)) {
            refreshStatus();
            return;
        }
        Intent i = new Intent(this, ArcVolumeService.class);
        i.putExtra(ArcVolumeService.EXTRA_TEST, test);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        int raw = Settings.System.getInt(getContentResolver(), ArcVolumeService.KEY_ARC_VOLUME, -1);
        String sony = raw >= 0 ? String.valueOf(Math.round(raw / 2.0f)) : "—";
        status.setText((overlay ? "Overlay: разрешен" : "Overlay: НЕ е разрешен")
                + "    Sony volume: " + sony);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(520), dp(58));
        p.setMargins(0, dp(10), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
