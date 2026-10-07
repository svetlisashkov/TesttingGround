package bg.svetli.arcvolume;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView config;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
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

        TextView subtitle = text("Показва точната стойност на TCL/Sony ARC звука до оригиналния плъзгач.", 16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(12), 0, dp(24));
        root.addView(subtitle);

        status = text("", 18);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        Button permission = button("Разреши показване върху други приложения");
        permission.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permission);

        Button test = button("Тест – покажи текущата стойност");
        test.setOnClickListener(v -> startListener(true));
        root.addView(test);

        config = text("", 15);
        config.setGravity(Gravity.CENTER);
        config.setPadding(0, dp(14), 0, dp(8));
        root.addView(config);

        LinearLayout row1 = row();
        row1.addView(smallButton("←", v -> change("x_dp", +4, 96, 20, 220)));
        row1.addView(smallButton("→", v -> change("x_dp", -4, 96, 20, 220)));
        row1.addView(smallButton("↑", v -> change("y_dp", -4, 0, -200, 200)));
        row1.addView(smallButton("↓", v -> change("y_dp", +4, 0, -200, 200)));
        root.addView(row1);

        LinearLayout row2 = row();
        row2.addView(smallButton("A−", v -> change("text_sp", -1, 20, 12, 36)));
        row2.addView(smallButton("A+", v -> change("text_sp", +1, 20, 12, 36)));
        row2.addView(smallButton("Нулирай", v -> resetUi()));
        root.addView(row2);

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

    private void change(String key, int delta, int def, int min, int max) {
        int v = prefs.getInt(key, def) + delta;
        if (v < min) v = min;
        if (v > max) v = max;
        prefs.edit().putInt(key, v).apply();
        refreshStatus();
        startListener(true);
    }

    private void resetUi() {
        prefs.edit().clear().apply();
        refreshStatus();
        startListener(true);
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        int current = Settings.System.getInt(getContentResolver(), ArcVolumeService.KEY_ARC_VOLUME, -1);
        status.setText((overlay ? "Overlay: разрешен" : "Overlay: НЕ е разрешен")
                + "    ARC volume: " + (current >= 0 ? current : "—"));

        int x = prefs.getInt("x_dp", 96);
        int y = prefs.getInt("y_dp", 0);
        int size = prefs.getInt("text_sp", 20);
        config.setText("Позиция: X " + x + "dp, Y " + y + "dp    Размер: " + size + "sp");
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

    private Button smallButton(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(115), dp(56));
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
