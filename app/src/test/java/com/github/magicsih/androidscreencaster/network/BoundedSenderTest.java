package com.github.magicsih.androidscreencaster.network;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class BoundedSenderTest {
    private static final class Sink implements SocketSink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);
        final CountDownLatch delivered;
        boolean block;
        boolean failConnect;
        boolean failWrite;
        Sink(int packets) { delivered = new CountDownLatch(packets); }
        @Override public void connect() throws IOException {
            if (failConnect) throw new IOException("connection refused");
        }
        @Override public void write(byte[] data) throws IOException {
            writing.countDown();
            if (block) {
                try { if (!gate.await(2, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
            }
            if (failWrite) throw new IOException("receiver closed");
            bytes.write(data); delivered.countDown();
        }
        @Override public void close() { gate.countDown(); }
    }
    @Test public void firstHeaderAndFramesAreOrdered() throws Exception {
        Sink sink = new Sink(3);
        BoundedSender sender = new BoundedSender(sink);
        try {
            sender.connect();
            sender.send(new byte[]{'D','K','I','F'});
            sender.send(new byte[]{1}); sender.send(new byte[]{2});
            assertTrue(sink.delivered.await(2, TimeUnit.SECONDS));
            assertArrayEquals(new byte[]{'D','K','I','F',1,2}, sink.bytes.toByteArray());
        } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
    }
    @Test public void connectionFailureIsReportedBeforeSending() throws Exception {
        Sink sink = new Sink(0); sink.failConnect = true;
        BoundedSender sender = new BoundedSender(sink);
        try {
            IOException error = assertThrows(IOException.class, sender::connect);
            assertTrue(error.getMessage().contains("connection refused"));
            assertThrows(IOException.class, () -> sender.send(new byte[]{1}));
        } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
    }
    @Test public void byteLimitIncludesBlockedWrite() throws Exception {
        Sink sink = new Sink(1); sink.block = true;
        BoundedSender sender = new BoundedSender(sink, 4, 10, 1500);
        try {
            sender.connect(); sender.send(new byte[]{1,2,3,4});
            assertTrue(sink.writing.await(2, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> sender.send(new byte[]{5}));
        } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
    }
    @Test public void packetLimitBoundsSmallPackets() throws Exception {
        Sink sink = new Sink(1); sink.block = true;
        BoundedSender sender = new BoundedSender(sink, 100, 1, 1500);
        try {
            sender.connect(); sender.send(new byte[]{1});
            assertTrue(sink.writing.await(2, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> sender.send(new byte[]{2}));
        } finally { sender.close(); assertTrue(sender.awaitTermination(1000)); }
    }
    @Test public void expiredVideoStopsWorker() throws Exception {
        Sink sink = new Sink(1);
        BoundedSender sender = new BoundedSender(sink, 100, 10, 0);
        sender.connect(); sender.send(new byte[]{1});
        assertTrue(sender.awaitTermination(1000));
        assertTrue(assertThrows(IOException.class, sender::check).getMessage().contains("expired"));
        assertEquals(0, sink.bytes.size());
    }
    @Test public void receiverWriteFailureStopsWorker() throws Exception {
        Sink sink = new Sink(1); sink.failWrite = true;
        BoundedSender sender = new BoundedSender(sink);
        sender.connect(); sender.send(new byte[]{1});
        assertTrue(sender.awaitTermination(1000));
        assertTrue(assertThrows(IOException.class, sender::check).getMessage().contains("receiver closed"));
    }
    @Test public void repeatedCloseUnblocksWritesAndEndsThreads() throws Exception {
        for (int i = 0; i < 10; i++) {
            Sink sink = new Sink(1); sink.block = true;
            BoundedSender sender = new BoundedSender(sink);
            sender.connect(); sender.send(new byte[]{1});
            assertTrue(sink.writing.await(2, TimeUnit.SECONDS));
            sender.close(); sender.close();
            assertTrue(sender.awaitTermination(1000));
            assertThrows(IOException.class, () -> sender.send(new byte[]{2}));
        }
    }
}
