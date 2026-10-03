package com.github.magicsih.androidscreencaster;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;

/** Platform-only tests; capture mode still requires Android's visible consent dialog. */
public final class UiSmokeInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : arguments;
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        try {
            if ("codecs".equals(arguments.getString("mode"))) {
                StringBuilder report = new StringBuilder();
                for (android.media.MediaCodecInfo codec : new android.media.MediaCodecList(
                        android.media.MediaCodecList.ALL_CODECS).getCodecInfos()) {
                    if (!codec.isEncoder()) continue;
                    for (String mime : codec.getSupportedTypes()) {
                        if (!mime.equals("video/avc") && !mime.equals("video/x-vnd.on2.vp8")) continue;
                        android.media.MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                        report.append(codec.getName()).append(" ").append(mime)
                            .append(" colors=").append(java.util.Arrays.toString(caps.colorFormats))
                            .append(" widths=").append(caps.getVideoCapabilities().getSupportedWidths())
                            .append(" heights=").append(caps.getVideoCapabilities().getSupportedHeights())
                            .append(" bitrate=").append(caps.getVideoCapabilities().getBitrateRange()).append("\n");
                    }
                }
                result.putString("stream", report.toString());
                finish(Activity.RESULT_OK, result);
                return;
            }
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            Activity current = activity;
            await(() -> ((Button) current.findViewById(R.id.button_start)).isEnabled(), 5000);
            String mode = arguments.getString("mode", "smoke");
            if (!"smoke".equals(mode)) {
                runOnMainSync(() -> {
                    ((EditText) current.findViewById(R.id.editText_server_host)).setText(arguments.getString("host"));
                    ((Spinner) current.findViewById(R.id.spinner_protocol)).setSelection("udp".equals(arguments.getString("protocol")) ? 1 : 0);
                    ((Spinner) current.findViewById(R.id.spinner_video_format)).setSelection("vp8".equals(arguments.getString("codec")) ? 1 : 0);
                    ((Spinner) current.findViewById(R.id.spinner_video_resolution)).setSelection(Integer.parseInt(arguments.getString("resolution", "2")));
                    ((Spinner) current.findViewById(R.id.spinner_video_bitrate)).setSelection(3);
                    current.findViewById(R.id.button_start).performClick();
                    // A second click while consent is pending must have no effect.
                    current.findViewById(R.id.button_start).performClick();
                });
                if ("cancel".equals(mode) || "failure".equals(mode)) {
                    await(() -> ((Button) current.findViewById(R.id.button_start)).isEnabled(), 180_000);
                    String status = ((TextView) current.findViewById(R.id.text_status)).getText().toString();
                    if ("cancel".equals(mode) && !status.equals(getTargetContext().getString(R.string.status_consent_denied))) {
                        throw new AssertionError("Expected consent denial: " + status);
                    }
                    if ("failure".equals(mode) && (status.contains("Ready") || status.contains("준비"))) {
                        throw new AssertionError("Expected connection error: " + status);
                    }
                    assertNoWorkers();
                    result.putString("stream", mode + " passed: " + status + ", no workers remain\n");
                    finish(Activity.RESULT_OK, result);
                    return;
                }
                await(() -> {
                    String status = ((TextView) current.findViewById(R.id.text_status)).getText().toString();
                    if (((Button) current.findViewById(R.id.button_start)).isEnabled()) {
                        throw new AssertionError("Capture did not start: " + status);
                    }
                    return status.contains("Sending screen") || status.contains("전송하는 중");
                }, 180_000);
                runOnMainSync(() -> getContext().startActivity(new Intent()
                        .setClassName(getContext().getPackageName(), MotionActivity.class.getName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                int seconds = Integer.parseInt(arguments.getString("seconds", "125"));
                if ("external-stop".equals(mode)) {
                    await(() -> ((Button) current.findViewById(R.id.button_start)).isEnabled(), 180_000);
                    assertNoWorkers();
                    result.putString("stream", "System stop passed: controls reset, no workers remain\n");
                    finish(Activity.RESULT_OK, result);
                    return;
                }
                Thread.sleep(seconds * 1000L);
                runOnMainSync(() -> {
                    Button stop = current.findViewById(R.id.button_stop);
                    if (!stop.isEnabled()) throw new AssertionError("Capture ended before the test interval");
                    stop.performClick();
                });
                await(() -> ((Button) current.findViewById(R.id.button_start)).isEnabled(), 7000);
                assertNoWorkers();
                result.putString("stream", "Capture completed: " + seconds + " seconds, no capture/sender threads remain\n");
            } else {
                AtomicReference<Throwable> failure = new AtomicReference<>();
                runOnMainSync(() -> {
                    try {
                        if (!(current.findViewById(R.id.root) instanceof ScrollView)) throw new AssertionError("Form must scroll");
                        Button stop = current.findViewById(R.id.button_stop);
                        if (stop.isEnabled()) throw new AssertionError("Stop must be disabled when idle");
                        EditText host = current.findViewById(R.id.editText_server_host);
                        CharSequence previous = host.getText().toString();
                        host.setText("tcp://invalid");
                        current.findViewById(R.id.button_start).performClick();
                        if (host.getError() == null) throw new AssertionError("Invalid address must show an error");
                        host.setText(previous);
                    } catch (Throwable e) { failure.set(e); }
                });
                if (failure.get() != null) throw new AssertionError(failure.get());
                result.putString("stream", "UI smoke passed: scroll layout, idle controls, address validation\n");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "Test failed: " + e + "\n");
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (activity != null) {
                Activity current = activity;
                runOnMainSync(() -> {
                    Button stop = current.findViewById(R.id.button_stop);
                    if (stop.isEnabled()) stop.performClick();
                    current.finish();
                });
            }
        }
    }
    private void assertNoWorkers() throws InterruptedException {
        // The main-thread state update can run just before the capture thread returns.
        Thread.sleep(100);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("ScreenCaster-")) {
                throw new AssertionError("Capture thread remains: " + thread.getName());
            }
        }
    }
    private interface Condition { boolean matches(); }
    private void await(Condition condition, long timeoutMillis) throws InterruptedException {
        long deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            AtomicReference<Throwable> error = new AtomicReference<>();
            runOnMainSync(() -> {
                try { done.set(condition.matches()); }
                catch (Throwable e) { error.set(e); }
            });
            if (error.get() != null) throw new AssertionError(error.get());
            if (done.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for capture state");
    }
}
