package com.github.magicsih.androidscreencaster.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import com.github.magicsih.androidscreencaster.MainActivity;
import com.github.magicsih.androidscreencaster.R;
import com.github.magicsih.androidscreencaster.network.BoundedSender;
import com.github.magicsih.androidscreencaster.network.NetworkSink;
import com.github.magicsih.androidscreencaster.writer.VideoPackets;

import java.io.IOException;
import java.nio.ByteBuffer;

/** A bound, same-process service. All capture resources belong to one worker/session. */
public final class ScreenCastService extends Service {
    private static final String TAG = "ScreenCastService";
    private static final String CHANNEL = "screen_capture";
    private static final String ACTION_STOP = "com.github.magicsih.androidscreencaster.STOP";
    public enum State { IDLE, CONNECTING, CASTING, STOPPING, ERROR }
    public interface Listener { void onStateChanged(State state, String message); }
    public final class LocalBinder extends Binder {
        public ScreenCastService getService() { return ScreenCastService.this; }
    }
    private final LocalBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private State state = State.IDLE;
    private String message = "";
    private Session session;

    @Override public IBinder onBind(Intent intent) { return binder; }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) listener.onStateChanged(state, message);
    }

    private void update(State state, String message) {
        this.state = state;
        this.message = message;
        if (listener != null) listener.onStateChanged(state, message);
    }

    /** Called only by the bound Activity, immediately after fresh consent. Never persists the token. */
    public void startCapture(CaptureConfig config, int resultCode, Intent consent) {
        if (session != null) return;
        Session next = new Session(config);
        session = next;
        try {
            Intent started = new Intent(this, ScreenCastService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(started);
            else startService(started);
            showNotification();
            update(State.CONNECTING, getString(R.string.status_connecting));
            // Only this one worker receives the token, for exactly one getMediaProjection call.
            new Thread(() -> next.capture(resultCode, consent), "ScreenCaster-capture").start();
        } catch (RuntimeException e) {
            session = null;
            stopForeground(true);
            stopSelf();
            update(State.ERROR, e.getMessage());
        }
    }

    public void stopCapture() {
        if (session == null) return;
        update(State.STOPPING, getString(R.string.status_stopping));
        session.cancel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) stopCapture();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        listener = null;
        if (session != null) session.cancel();
        super.onDestroy();
    }

    private void showNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, CHANNEL);
        } else builder = new Notification.Builder(this);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ScreenCastService.class).setAction(ACTION_STOP), flags);
        Notification notification = builder.setSmallIcon(R.drawable.ic_cast)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(R.drawable.ic_cast,
                        getString(R.string.action_stop), stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(1, notification);
    }

    private static MediaCodec createSurfaceEncoder(MediaFormat format, String mime) throws IOException {
        Exception lastError = null;
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!mime.equalsIgnoreCase(type)) continue;
                boolean surface = false;
                for (int color : info.getCapabilitiesForType(type).colorFormats) {
                    if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) surface = true;
                }
                if (!surface) continue;
                MediaCodec candidate = null;
                try {
                    candidate = MediaCodec.createByCodecName(info.getName());
                    // Configuring verifies the actual settings, including on API 21 whose
                    // format queries reject frame rate and can misreport video constraints.
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    return candidate;
                } catch (IOException | RuntimeException e) {
                    lastError = e;
                    if (candidate != null) release("Unsupported encoder", candidate::release);
                }
            }
        }
        throw new IOException("No Surface encoder supports " + mime
                + " at these settings. Try another resolution, bitrate, or format.", lastError);
    }

    private static void release(String resource, Runnable release) {
        try { release.run(); }
        catch (RuntimeException e) { Log.w(TAG, resource + " was already released", e); }
    }

    private final class Session {
        private final CaptureConfig config;
        private final BoundedSender sender;
        private volatile boolean cancelled;
        Session(CaptureConfig config) {
            this.config = config;
            sender = new BoundedSender(new NetworkSink(config.host, 49152, config.udp));
        }
        void cancel() {
            cancelled = true;
            sender.close();
        }
        void capture(int resultCode, Intent consent) {
            MediaProjection projection = null;
            MediaCodec encoder = null;
            Surface surface = null;
            VirtualDisplay display = null;
            boolean encoderStarted = false;
            String error = null;
            MediaProjection.Callback callback = new MediaProjection.Callback() {
                @Override public void onStop() {
                    main.post(() -> { if (session == Session.this) stopCapture(); });
                }
            };
            try {
                sender.connect();
                if (cancelled) return;
                MediaProjectionManager manager = (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(resultCode, consent);
                consent = null;
                if (projection == null) throw new IOException("Screen capture consent is unavailable");
                projection.registerCallback(callback, main);
                MediaFormat format = MediaFormat.createVideoFormat(config.mime, config.width, config.height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                encoder = createSurfaceEncoder(format, config.mime);
                String codecName = encoder.getName();
                surface = encoder.createInputSurface();
                boolean vp8 = MediaFormat.MIMETYPE_VIDEO_VP8.equals(config.mime);
                if (vp8) sender.send(VideoPackets.ivfHeader(config.width, config.height));
                encoder.start();
                encoderStarted = true;
                if (cancelled) return;
                display = projection.createVirtualDisplay("ScreenCaster", config.width, config.height,
                        config.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, main);
                Log.i(TAG, "Capturing " + config.mime + " using " + codecName);
                main.post(() -> {
                    if (session == Session.this && !cancelled) {
                        update(State.CASTING, getString(config.udp ? R.string.status_udp : R.string.status_casting));
                    }
                });
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (!cancelled) {
                    sender.check();
                    int index = encoder.dequeueOutputBuffer(info, 10_000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !vp8) {
                        MediaFormat output = encoder.getOutputFormat();
                        for (int i = 0; i < 2; i++) {
                            ByteBuffer csd = output.getByteBuffer("csd-" + i);
                            if (csd != null) sender.send(VideoPackets.copy(csd));
                        }
                    } else if (index >= 0) {
                        try {
                            ByteBuffer buffer = encoder.getOutputBuffer(index);
                            if (info.size > 0 && buffer != null
                                    && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                                byte[] bytes = VideoPackets.copy(buffer);
                                sender.send(vp8 ? VideoPackets.ivfFrame(bytes, info.presentationTimeUs) : bytes);
                            }
                        } finally { encoder.releaseOutputBuffer(index, false); }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    }
                }
            } catch (Exception e) {
                if (!cancelled) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                    Log.e(TAG, "Capture ended", e);
                }
            } finally {
                // Disconnect first to unblock network writes; release display before encoder/surface.
                sender.close();
                if (display != null) release("Display", display::release);
                if (encoder != null) {
                    try { if (encoderStarted) encoder.stop(); }
                    catch (RuntimeException e) { Log.w(TAG, "Encoder was already stopped", e); }
                    finally { release("Encoder", encoder::release); }
                }
                if (surface != null) release("Surface", surface::release);
                if (projection != null) {
                    try { projection.unregisterCallback(callback); }
                    catch (RuntimeException e) { Log.w(TAG, "Projection callback already removed", e); }
                    release("Projection", projection::stop);
                }
                try {
                    if (!sender.awaitTermination(1000)) Log.w(TAG, "DNS lookup is still finishing");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                String finalError = error;
                main.post(() -> {
                    if (session != Session.this) return;
                    session = null;
                    stopForeground(true);
                    stopSelf();
                    update(finalError == null ? State.IDLE : State.ERROR,
                            finalError == null ? getString(R.string.status_idle) : finalError);
                });
            }
        }
    }
}
