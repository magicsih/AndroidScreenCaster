package com.github.magicsih.androidscreencaster.tcpstream;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class TcpSocketClient extends Thread {

    private final String TAG = "TcpSocketClient";

    private Socket socket;
    private OutputStream outputStream;
    private BufferedOutputStream bufferedOutputStream;
    private Handler handler;
    private final InetAddress remoteHost;
    private final int remotePort;
    
    public TcpSocketClient(InetAddress remoteHost, int remotePort) {
        super( "TcpSocketClientThread");
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(remoteHost, remotePort);
            outputStream = socket.getOutputStream();
            bufferedOutputStream = new BufferedOutputStream(outputStream);
        } catch (IOException e) {
            Log.e(TAG, "Socket creation failed - " + e.toString());
            socket = null;
            outputStream = null;
            bufferedOutputStream = null;
        }

        Looper.prepare();
        handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if(message == null || message.obj == null) return;
                byte[] msg = (byte[])message.obj;
                try {
                    bufferedOutputStream.write(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                    close();
                }
            }
        };
        Looper.loop();
    }

    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                socket = null;
                outputStream = null;
                bufferedOutputStream = null;
            }
        }
    }

    public void send(final byte[] data) {
        if(handler == null || socket == null || outputStream == null)  {
            return;
        }
        Message message = handler.obtainMessage();
        message.obj = data;
        handler.sendMessage(message);
    }
}
