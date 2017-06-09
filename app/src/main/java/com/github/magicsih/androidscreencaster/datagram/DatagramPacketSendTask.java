package com.github.magicsih.androidscreencaster.datagram;

import android.os.AsyncTask;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by sih on 2017-02-22.
 */

public class DatagramPacketSendTask extends AsyncTask<byte[],Void,Boolean> {

    private final static String TAG = DatagramPacketSendTask.class.getName();

    private final DatagramSocket udpSocket;
    private final InetAddress inetAddress;
    private final int port;


    public DatagramPacketSendTask(DatagramSocket udpSocket, InetAddress inetAddress, int port) {
        this.udpSocket = udpSocket;
        this.inetAddress = inetAddress;
        this.port = port;
    }

    @Override
    protected Boolean doInBackground(byte[]... bytes) {
        for(byte[] b : bytes){
            try {
                udpSocket.send(new DatagramPacket(b, b.length,  inetAddress, port));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }
}
