package bg.svetli.arcvolume;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.ScrollView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private TextView status;
    private static final int EXPORT_SETTINGS = 501;
    private static final int IMPORT_SETTINGS = 502;

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
                "Базова позиция: 120×400, x=50, y=0. Ръчните корекции са по избор.",
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

        Button accessibility = button("Активирай системен overlay (Accessibility)");
        accessibility.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
        });
        root.addView(accessibility);

        Button test = button("Тест – покажи OSD");
        test.setOnClickListener(v -> startListener(true));
        root.addView(test);

        Button sonyUp = button("Тест Sony + без системен OSD");
        sonyUp.setOnClickListener(v -> adjustSony(AudioManager.ADJUST_RAISE));
        root.addView(sonyUp);

        Button sonyDown = button("Тест Sony − без системен OSD");
        sonyDown.setOnClickListener(v -> adjustSony(AudioManager.ADJUST_LOWER));
        root.addView(sonyDown);

        TextView pos = text("", 16);
        pos.setGravity(Gravity.CENTER);
        pos.setPadding(0, dp(12), 0, 0);
        root.addView(pos);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        root.addView(controls);
        addPositionButton(controls, "◀", -1, 0, pos);
        addPositionButton(controls, "▶", 1, 0, pos);
        addPositionButton(controls, "▲", 0, -1, pos);
        addPositionButton(controls, "▼", 0, 1, pos);
        Button reset = button("Нулирай позицията");
        reset.setOnClickListener(v -> {
            getSharedPreferences("osd_position", MODE_PRIVATE).edit().clear().apply();
            updatePositionLabel(pos);
            VolumeKeyAccessibilityService.refreshPosition();
        });
        root.addView(reset);
        updatePositionLabel(pos);
        TextView scaleTitle = text("Скала на звука", 18);
        scaleTitle.setGravity(Gravity.CENTER);
        root.addView(scaleTitle);
        RadioGroup scales = new RadioGroup(this);
        scales.setOrientation(RadioGroup.HORIZONTAL);
        scales.setGravity(Gravity.CENTER);
        RadioButton sonyScale = new RadioButton(this);
        sonyScale.setText("Sony 0–50");
        sonyScale.setTextColor(Color.WHITE);
        sonyScale.setId(10050);
        scales.addView(sonyScale);
        RadioButton tclScale = new RadioButton(this);
        tclScale.setText("TCL 0–100");
        tclScale.setTextColor(Color.WHITE);
        tclScale.setId(10100);
        scales.addView(tclScale);
        scales.check(getSharedPreferences("arc_volume_settings", MODE_PRIVATE)
                .getInt("volume_scale", 50) == 100 ? 10100 : 10050);
        scales.setOnCheckedChangeListener((group, checkedId) -> {
            getSharedPreferences("arc_volume_settings", MODE_PRIVATE).edit()
                    .putInt("volume_scale", checkedId == 10100 ? 100 : 50).apply();
            refreshStatus();
        });
        root.addView(scales);

        Button export = button("Експортирай настройки (JSON)");
        export.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "ArcVolume-settings.json");
            startActivityForResult(i, EXPORT_SETTINGS);
        });
        root.addView(export);
        Button imp = button("Импортирай настройки (JSON)");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, IMPORT_SETTINGS);
        });
        root.addView(imp);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    private void addPositionButton(LinearLayout row, String label, int dx, int dy, TextView pos) {
        Button b = new Button(this);
        b.setText(label);
        row.addView(b);
        b.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences("osd_position", MODE_PRIVATE);
            p.edit().putInt("offset_x", p.getInt("offset_x", 0) + dx)
                    .putInt("offset_y", p.getInt("offset_y", 0) + dy).apply();
            updatePositionLabel(pos);
            VolumeKeyAccessibilityService.refreshPosition();
        });
    }

    private void updatePositionLabel(TextView v) {
        SharedPreferences p = getSharedPreferences("osd_position", MODE_PRIVATE);
        v.setText("Корекция X: " + p.getInt("offset_x", 0) + " px; Y: "
                + p.getInt("offset_y", 0) + " px (стъпка 1 px)");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            if (requestCode == EXPORT_SETTINGS) {
                SharedPreferences pos = getSharedPreferences("osd_position", MODE_PRIVATE);
                int scale = getSharedPreferences("arc_volume_settings", MODE_PRIVATE)
                        .getInt("volume_scale", 50);
                JSONObject json = new JSONObject();
                json.put("schemaVersion", 1);
                json.put("volumeScale", scale);
                json.put("positionX", pos.getInt("offset_x", 0));
                json.put("positionY", pos.getInt("offset_y", 0));
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new Exception("Няма достъп за запис");
                    out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Настройките са експортирани", Toast.LENGTH_LONG).show();
            } else if (requestCode == IMPORT_SETTINGS) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("Няма достъп за четене");
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        if (buffer.size() + n > 16384) throw new Exception("Файлът е твърде голям");
                        buffer.write(chunk, 0, n);
                    }
                }
                JSONObject json = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                if (json.getInt("schemaVersion") != 1) throw new Exception("Неподдържана версия");
                int scale = json.getInt("volumeScale");
                int x = json.getInt("positionX");
                int y = json.getInt("positionY");
                if ((scale != 50 && scale != 100) || Math.abs((long)x) > 1000
                        || Math.abs((long)y) > 1000) throw new Exception("Невалидни настройки");
                getSharedPreferences("osd_position", MODE_PRIVATE).edit()
                        .putInt("offset_x", x).putInt("offset_y", y).apply();
                getSharedPreferences("arc_volume_settings", MODE_PRIVATE).edit()
                        .putInt("volume_scale", scale).apply();
                VolumeKeyAccessibilityService.refreshPosition();
                buildUi();
                Toast.makeText(this, "Настройките са импортирани", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Грешка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

    private void adjustSony(int direction) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                // flags=0 deliberately avoids requesting Android's SHOW_UI flag.
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            }
        } catch (Exception ignored) {}
        new android.os.Handler(getMainLooper()).postDelayed(this::refreshStatus, 500);
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        int raw = Settings.System.getInt(getContentResolver(), ArcVolumeService.KEY_ARC_VOLUME, -1);
        int scale = getSharedPreferences("arc_volume_settings", MODE_PRIVATE).getInt("volume_scale", 50);
        String sony = raw >= 0 ? String.valueOf(scale == 100 ? raw : Math.round(raw / 2.0f)) : "—";
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean keys = enabled != null && enabled.contains(
                getPackageName() + "/.VolumeKeyAccessibilityService");
        status.setText((overlay ? "Overlay: разрешен" : "Overlay: НЕ е разрешен")
                + "    Volume (" + scale + "): " + sony
                + "    System overlay: " + (keys ? "АКТИВЕН" : "НЕАКТИВЕН"));
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
