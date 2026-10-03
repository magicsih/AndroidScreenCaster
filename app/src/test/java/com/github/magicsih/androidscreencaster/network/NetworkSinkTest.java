package com.github.magicsih.androidscreencaster.network;

import org.junit.Test;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class NetworkSinkTest {
    @Test public void tcpDeliversFirstSmallPacketImmediately() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
                try (Socket accepted = server.accept()) {
                    accepted.setSoTimeout(1500);
                    byte[] bytes = new byte[4];
                    int offset = 0;
                    while (offset < bytes.length) {
                        int count = accepted.getInputStream().read(bytes, offset, bytes.length - offset);
                        if (count < 0) throw new IOException("unexpected EOF");
                        offset += count;
                    }
                    return bytes;
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            BoundedSender sender = new BoundedSender(new NetworkSink("127.0.0.1", server.getLocalPort(), false));
            try {
                sender.connect(); sender.send(new byte[]{1,2,3,4});
                assertArrayEquals(new byte[]{1,2,3,4}, received.get(2, TimeUnit.SECONDS));
            } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
        }
    }
    @Test public void udpRetains1024ByteFragmentationAndByteOrder() throws Exception {
        try (DatagramSocket server = new DatagramSocket(0)) {
            server.setSoTimeout(1500);
            byte[] source = new byte[2500];
            for (int i = 0; i < source.length; i++) source[i] = (byte)i;
            BoundedSender sender = new BoundedSender(new NetworkSink("127.0.0.1", server.getLocalPort(), true));
            try {
                sender.connect(); sender.send(source);
                int offset = 0;
                for (int length : new int[]{1024, 1024, 452}) {
                    DatagramPacket packet = new DatagramPacket(new byte[2000], 2000);
                    server.receive(packet);
                    assertEquals(length, packet.getLength());
                    assertArrayEquals(Arrays.copyOfRange(source, offset, offset + length),
                            Arrays.copyOf(packet.getData(), packet.getLength()));
                    offset += length;
                }
            } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
        }
    }
    @Test public void closingBeforeConnectPreventsResourceCreation() throws Exception {
        NetworkSink sink = new NetworkSink("127.0.0.1", 49152, false);
        sink.close(); sink.close();
        assertThrows(IOException.class, sink::connect);
    }
}
