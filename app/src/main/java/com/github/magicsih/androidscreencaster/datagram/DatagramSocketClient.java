package com.github.magicsih.androidscreencaster.datagram;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

/**
 * Created by sih on 2017-02-20.
 */

public class DatagramSocketClient extends Thread {

    private final String TAG = "DatagramSocketClient";

    private DatagramSocket datagramSocket;
    private Handler handler;
    private final InetAddress remoteHost;
    private final int remotePort;
    private final int MTU = 1024;

    public DatagramSocketClient(InetAddress remoteHost, int remotePort)
    {
        super("DatagramSocketClient");
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;


    }

    @Override
    public void run() {
        try {
            datagramSocket = new DatagramSocket();

        } catch (SocketException e) {
            e.printStackTrace();
        }

        Looper.prepare();
        handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if(message == null || message.obj == null) return;

                byte[] msg = (byte[])message.obj;

                final int totalLength = msg.length;
                int remainLength = msg.length;

                while(remainLength > 0) {
                    int offset = totalLength - remainLength;
                    int size = (remainLength > MTU) ? MTU : remainLength;
                    remainLength -= size;

                    try {
                        datagramSocket.send(new DatagramPacket(msg, offset, size, remoteHost, remotePort));
                    } catch(IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };

        Looper.loop();
    }

    public void close() {
        datagramSocket.close();
    }

    public void send(final byte[] data) {
        if(handler == null || datagramSocket == null)  {
            return;
        }
        Message message = handler.obtainMessage();
        message.obj = data;
        handler.sendMessage(message);
    }
}
