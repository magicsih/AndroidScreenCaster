package com.github.magicsih.androidscreencaster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.magicsih.androidscreencaster.service.CaptureConfig;
import com.github.magicsih.androidscreencaster.service.ScreenCastService;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 300;
    private SharedPreferences preferences;
    private ScreenCastService service;
    private boolean bound;
    private boolean awaitingConsent;
    private boolean busy;
    private CaptureConfig pendingConfig;
    private EditText host;
    private Button start;
    private Button stop;
    private TextView status;
    private final int[] spinners = { R.id.spinner_protocol, R.id.spinner_video_format,
            R.id.spinner_video_resolution, R.id.spinner_video_bitrate };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ScreenCastService.LocalBinder) binder).getService();
            service.setListener(MainActivity.this::showState);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            busy = false;
            status.setText(R.string.status_disconnected);
            updateControls();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else if (Build.VERSION.SDK_INT >= 23) {
            int appearance = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) appearance |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(appearance);
        }
        preferences = getSharedPreferences("default", MODE_PRIVATE);
        host = findViewById(R.id.editText_server_host);
        start = findViewById(R.id.button_start);
        stop = findViewById(R.id.button_stop);
        status = findViewById(R.id.text_status);
        host.setText(preferences.getString("server_host", ""));
        setSpinner(spinners[0], R.array.options_protocols, "protocol");
        setSpinner(spinners[1], R.array.options_format_keys, "spinner_format");
        setSpinner(spinners[2], R.array.options_resolution_keys, "spinner_resolution");
        setSpinner(spinners[3], R.array.options_bitrate_keys, "spinner_bitrate");
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edges = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(edges.left, edges.top, edges.right, edges.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
        start.setOnClickListener(view -> requestCapture());
        stop.setOnClickListener(view -> { if (service != null) service.stopCapture(); });
        bound = bindService(new Intent(this, ScreenCastService.class), connection, Context.BIND_AUTO_CREATE);
        updateControls();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("notification_permission_requested", false)) {
            preferences.edit().putBoolean("notification_permission_requested", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 301);
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        findViewById(R.id.root).requestApplyInsets();
    }

    private void setSpinner(int id, int options, String key) {
        Spinner spinner = findViewById(id);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, options,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(adapter.getCount() - 1, preferences.getInt(key, 0))));
    }

    private int selected(int index) {
        return ((Spinner) findViewById(spinners[index])).getSelectedItemPosition();
    }

    private void requestCapture() {
        if (service == null || busy || awaitingConsent) return;
        try {
            String address = host.getText().toString().trim();
            String[] resolution = getResources().getStringArray(R.array.options_resolution_values)[selected(2)].split(",");
            pendingConfig = new CaptureConfig(address, selected(0) == 1,
                    getResources().getStringArray(R.array.options_format_values)[selected(1)],
                    Integer.parseInt(resolution[0]), Integer.parseInt(resolution[1]),
                    Integer.parseInt(resolution[2]), getResources().getIntArray(R.array.options_bitrate_values)[selected(3)]);
            preferences.edit().putString("server_host", address).putInt("protocol", selected(0))
                    .putInt("spinner_format", selected(1)).putInt("spinner_resolution", selected(2))
                    .putInt("spinner_bitrate", selected(3)).apply();
            awaitingConsent = true;
            updateControls();
            if (Build.VERSION.SDK_INT >= 37
                    && checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                status.setText(R.string.status_network_permission);
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_LOCAL_NETWORK}, 302);
            } else launchConsent();
        } catch (IllegalArgumentException e) {
            host.setError(getString(R.string.error_host));
            pendingConfig = null;
            awaitingConsent = false;
            updateControls();
        }
    }

    private void launchConsent() {
        status.setText(R.string.status_consent);
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != 302) return;
        if (grants.length > 0 && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                && pendingConfig != null && service != null) {
            launchConsent();
        } else {
            pendingConfig = null;
            awaitingConsent = false;
            status.setText(R.string.status_network_denied);
            updateControls();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        awaitingConsent = false;
        CaptureConfig config = pendingConfig;
        pendingConfig = null;
        if (resultCode == RESULT_OK && data != null && service != null && config != null) {
            service.startCapture(config, resultCode, data);
        } else status.setText(R.string.status_consent_denied);
        updateControls();
    }

    private void showState(ScreenCastService.State state, String message) {
        busy = state == ScreenCastService.State.CONNECTING || state == ScreenCastService.State.CASTING
                || state == ScreenCastService.State.STOPPING;
        status.setText(message == null || message.isEmpty() ? getString(R.string.status_idle) : message);
        stop.setEnabled(busy && state != ScreenCastService.State.STOPPING);
        updateControls();
    }

    private void updateControls() {
        boolean editable = !busy && !awaitingConsent;
        start.setEnabled(service != null && editable);
        if (!busy) stop.setEnabled(false);
        host.setEnabled(editable);
        for (int id : spinners) findViewById(id).setEnabled(editable);
    }

    @Override protected void onDestroy() {
        if (service != null) service.setListener(null);
        if (bound) unbindService(connection);
        super.onDestroy();
    }
}
