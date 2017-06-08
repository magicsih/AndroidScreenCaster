package com.github.magicsih.androidscreencaster;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Created by sih on 2017-02-20.
 */

public class DatagramSocketClient {

    private DatagramSocket datagramSocket;
    private Handler handler;

    public DatagramSocketClient()
    {
        handler = new Handler(Looper.getMainLooper());
        try {
            datagramSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public DatagramSocket getDatagramSocket() {
        return datagramSocket;
    }

    public void close() {
        datagramSocket.close();
    }

    public void send(final AsyncTask<byte[],Void,Boolean> task, final byte[] data) {
        if(handler == null || datagramSocket == null)  {
            return;
        }
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                task.execute(data);
            }
        });
    }
}
