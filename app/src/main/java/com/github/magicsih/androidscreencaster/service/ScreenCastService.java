package com.github.magicsih.androidscreencaster.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.Surface;

import com.github.magicsih.androidscreencaster.consts.ActivityServiceMessage;
import com.github.magicsih.androidscreencaster.consts.ExtraIntent;
import com.github.magicsih.androidscreencaster.datagram.DatagramSocketClient;
import com.github.magicsih.androidscreencaster.tcpstream.TcpSocketClient;
import com.github.magicsih.androidscreencaster.writer.IvfWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Created by sih on 2017-05-31.
 */
public final class ScreenCastService extends Service {

    private static final int FPS = 30;
    private final String TAG = "ScreenCastService";

    private MediaProjectionManager mediaProjectionManager;
    private Handler handler;
    private Messenger crossProcessMessenger;

    private TcpSocketClient tcpSocketClient;
    private DatagramSocketClient datagramSocketClient;

    private MediaProjection mediaProjection;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec encoder;
    private IvfWriter ivf;

    private InetAddress remoteHost;
    private int remotePort;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message. what:" + msg.what);
                switch(msg.what) {
                    case ActivityServiceMessage.CONNECTED:
                    case ActivityServiceMessage.DISCONNECTED:
                        break;
                    case ActivityServiceMessage.STOP:
                        stopScreenCapture();
                        closeSocket();
                        stopSelf();
                        break;
                }
                return false;
            }
        });
        crossProcessMessenger = new Messenger(handler);
        return crossProcessMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopScreenCapture();
        closeSocket();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final String protocol = intent.getStringExtra(ExtraIntent.PROTOCOL.toString());

        final String remoteHost = intent.getStringExtra(ExtraIntent.SERVER_HOST.toString());
        remotePort = intent.getIntExtra(ExtraIntent.PORT.toString(), 49152);
        if(remoteHost == null) {
            return START_NOT_STICKY;
        }

        try {
            this.remoteHost = InetAddress.getByName(remoteHost);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return START_NOT_STICKY;
        }


        final int resultCode = intent.getIntExtra(ExtraIntent.RESULT_CODE.toString(), -1);
        final Intent resultData = intent.getParcelableExtra(ExtraIntent.RESULT_DATA.toString());

        Log.i(TAG, "resultCode: " + resultCode + " serverAddr:" + remoteHost);

        if (resultCode == 0 || resultData == null) { return  START_NOT_STICKY; }

        final String format = intent.getStringExtra(ExtraIntent.VIDEO_FORMAT.toString());
        final int screenWidth = intent.getIntExtra(ExtraIntent.SCREEN_WIDTH.toString(), 640);
        final int screenHeight = intent.getIntExtra(ExtraIntent.SCREEN_HEIGHT.toString(), 360);
        final int screenDpi = intent.getIntExtra(ExtraIntent.SCREEN_DPI.toString(), 96);
        final int bitrate = intent.getIntExtra(ExtraIntent.VIDEO_BITRATE.toString(), 1024000);

        Log.i(TAG, "Start casting with format:" + format + ", screen:" + screenWidth +"x"+screenHeight +" @ " + screenDpi + " bitrate:" + bitrate);


        if("udp".equals(protocol)) {
            if(!createUdpSocket()) {
                Log.e(TAG, "Failed to connect udp://" + remoteHost + ":" + remotePort);
                return START_NOT_STICKY;
            }
            Log.i(TAG, "UDP Socket created.");
        } else {
            if (!createSocket()) {
                Log.e(TAG, "Failed to connect tcp://" + remoteHost + ":" + remotePort);
                return START_NOT_STICKY;
            }
            Log.i(TAG, "TCP Socket created.");
        }
        
        startScreenCapture(resultCode, resultData, format, screenWidth, screenHeight, screenDpi, bitrate);

        return START_STICKY;
    }

    private void startScreenCapture(int resultCode, Intent resultData, String format, int width, int height, int dpi, int bitrate) {
        this.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);

        Log.d(TAG, "startRecording...");

        this.videoBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(format, width, height);

        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        try {

            switch (format) {
                case MediaFormat.MIMETYPE_VIDEO_AVC:
                    // AVC
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

                    this.encoder = MediaCodec.createEncoderByType(format);
                    this.encoder.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int inputBufferId) {
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {
                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset);
                                outputBuffer.limit(info.offset + info.size);
                                byte[] b = new byte[outputBuffer.remaining()];
                                outputBuffer.get(b);
                                sendData(null, b);
                            }
                            if (encoder != null) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                            }
                            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "End of Stream");
                                stopScreenCapture();
                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                            Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo().toString() + " MediaFormat:" + format.toString());
                        }
                    });
                    break;
                case MediaFormat.MIMETYPE_VIDEO_VP8:
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    final int frameSize = width * height * 3 / 2;
                    //VP8
                    byte[] ivfHeader = IvfWriter.makeIvfHeader(0, width, height, 1, bitrate);
                    sendData(null, ivfHeader);

                    this.encoder = MediaCodec.createByCodecName("OMX.google.vp8.encoder");
//                this.encoder = MediaCodec.createEncoderByType(format);
                    this.encoder.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int inputBufIndex) {
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {
                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset);
                                outputBuffer.limit(info.offset + info.size);

                                byte[] header = IvfWriter.makeIvfFrameHeader(outputBuffer.remaining(), info.presentationTimeUs);
                                byte[] b = new byte[outputBuffer.remaining()];
                                outputBuffer.get(b);

                                sendData(header, b);
                            }
                            if (encoder != null) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                            }
                            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "End of Stream");
                                stopScreenCapture();
                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                            Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo().toString() + " MediaFormat:" + format.toString());
                        }
                    });
                    break;
                default:
                    throw new RuntimeException("Unknown Media Format. You need to add mimetype to string.xml and else if statement");
            }

            this.encoder.configure(mediaFormat
                                    , null // surface
                                    , null // crypto
                                    , MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.inputSurface = this.encoder.createInputSurface();
            this.encoder.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to initial encoder, e: " + e);
            releaseEncoders();
        }

        this.virtualDisplay = this.mediaProjection.createVirtualDisplay("Recording Display", width, height, dpi, 0, this.inputSurface, null, null);
    }

    private void sendData(byte[] header, byte[] data) {
//        if(socketOutputStream != null) {
//            try {
//                if(header != null) {
//                    socketOutputStream.write(header);
//                }
//                socketOutputStream.write(data);
//            } catch (IOException e) {
//                Log.e(TAG, "Failed to write data to tcp socket, stop casting");
//                e.printStackTrace();
//                stopScreenCapture();
//            }
        if(tcpSocketClient != null) {
            if(header != null) {
                tcpSocketClient.send(header);
            }
            tcpSocketClient.send(data);
        } else if(datagramSocketClient != null) {
            if(header != null) {
                byte[] headerAndBody = new byte[header.length + data.length];
                System.arraycopy(header, 0, headerAndBody, 0, header.length);
                System.arraycopy(data, 0, headerAndBody, header.length, data.length);
                datagramSocketClient.send(headerAndBody);
            } else{
                datagramSocketClient.send(data);
            }
        } else{
            Log.e(TAG, "Both tcp and udp socket are not available.");
            stopScreenCapture();
        }
    }

    private void stopScreenCapture() {
        releaseEncoders();
        closeSocket();
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        virtualDisplay = null;
    }

    private void releaseEncoders() {

        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if(ivf != null) {
            ivf = null;
        }

        videoBufferInfo = null;
    }

    private boolean createUdpSocket() {
        datagramSocketClient = new DatagramSocketClient(remoteHost, remotePort);
        datagramSocketClient.start();
        return true;
    }

    private boolean createSocket() {
        tcpSocketClient = new TcpSocketClient(remoteHost, remotePort);
        tcpSocketClient.start();
//        Thread t = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Log.i(TAG, "Socket creating...");
//                    socket = new Socket(remoteHost, remotePort);
//                    socketOutputStream = socket.getOutputStream();
//                } catch (IOException e) {
//                    Log.e(TAG, "Socket creation failed - " + e.toString());
//                    e.printStackTrace();
//                    socket = null;
//                    socketOutputStream = null;
//                }
//            }
//        });
//        t.start();
//        try {
//            t.join();
//            if (socket != null && socketOutputStream != null) {
//                return true;
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return false;
        return true;
    }

    private void closeSocket() {
        if(datagramSocketClient !=null) {
            datagramSocketClient.close();
            datagramSocketClient = null;
        }

//        if (socket != null) {
//            try {
//                socket.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                socket = null;
//                socketOutputStream = null;
//            }
//        }
        if(tcpSocketClient != null) {
            try {
                tcpSocketClient.close();
            } catch(Exception ex) {
                ex.printStackTrace();
            } finally {
                tcpSocketClient = null;
            }
        }
    }
}
