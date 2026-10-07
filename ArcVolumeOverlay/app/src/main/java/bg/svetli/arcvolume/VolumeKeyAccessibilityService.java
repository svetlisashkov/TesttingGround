package bg.svetli.arcvolume;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeKeyAccessibilityService extends AccessibilityService {
    private AudioManager audioManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // Keep the volume observer/OSD service alive whenever key interception is enabled.
        Intent service = new Intent(this, ArcVolumeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP
                && code != KeyEvent.KEYCODE_VOLUME_DOWN
                && code != KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        // Consume both DOWN and UP so TCL SystemUI never receives the key and therefore
        // never shows its ARC +/- OSD. On DOWN, control Sony through AudioManager with
        // flags=0 (no SHOW_UI). TCL/CEC then reports the exact ARC value back and
        // ArcVolumeService displays it using the Sony 0..50 scale.
        if (event.getAction() == KeyEvent.ACTION_DOWN && audioManager != null) {
            int direction;
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                direction = AudioManager.ADJUST_RAISE;
            } else if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                direction = AudioManager.ADJUST_LOWER;
            } else {
                direction = AudioManager.ADJUST_TOGGLE_MUTE;
            }
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            } catch (Exception ignored) {}
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
