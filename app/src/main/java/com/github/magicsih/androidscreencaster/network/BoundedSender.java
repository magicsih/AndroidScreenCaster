package com.github.magicsih.androidscreencaster.network;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Ordered, bounded transmission. A slow receiver ends the session instead of adding latency. */
public final class BoundedSender implements Closeable {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_PACKETS = 120;
    private final SocketSink sink;
    private final CountDownLatch connected = new CountDownLatch(1);
    private final ArrayDeque<Packet> queue = new ArrayDeque<>();
    private final Thread worker;
    private final int maxBytes;
    private final int maxPackets;
    private final long maxAgeNanos;
    private int pendingBytes;
    private int pendingPackets;
    private boolean closed;
    private IOException failure;

    private static final class Packet {
        final byte[] bytes;
        final long queuedAt = System.nanoTime();
        Packet(byte[] bytes) { this.bytes = bytes; }
    }

    public BoundedSender(SocketSink sink) {
        this(sink, MAX_BYTES, MAX_PACKETS, 1500);
    }

    public BoundedSender(SocketSink sink, int maxBytes, int maxPackets, long maxAgeMillis) {
        this.sink = sink;
        this.maxBytes = maxBytes;
        this.maxPackets = maxPackets;
        this.maxAgeNanos = TimeUnit.MILLISECONDS.toNanos(maxAgeMillis);
        worker = new Thread(this::run, "ScreenCaster-sender");
    }

    public void connect() throws IOException, InterruptedException {
        worker.start();
        if (!connected.await(5, TimeUnit.SECONDS)) {
            close();
            throw new IOException("Connection timed out (5 seconds)");
        }
        check();
    }

    public synchronized void check() throws IOException {
        if (failure != null) throw failure;
        if (closed) throw new IOException("Connection closed");
    }

    public synchronized void send(byte[] data) throws IOException {
        check();
        if (data.length == 0) return;
        if (data.length > maxBytes - pendingBytes || pendingPackets >= maxPackets) {
            throw new IOException("Receiver cannot keep up: send queue limit reached");
        }
        queue.add(new Packet(data.clone()));
        pendingBytes += data.length;
        pendingPackets++;
        notifyAll();
    }

    private void run() {
        try {
            sink.connect();
            connected.countDown();
            while (true) {
                Packet packet;
                synchronized (this) {
                    while (!closed && queue.isEmpty()) wait();
                    if (closed) return;
                    packet = queue.removeFirst();
                }
                if (System.nanoTime() - packet.queuedAt > maxAgeNanos) {
                    throw new IOException("Receiver cannot keep up: queued video expired");
                }
                sink.write(packet.bytes);
                synchronized (this) {
                    pendingBytes -= packet.bytes.length;
                    pendingPackets--;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            synchronized (this) {
                if (!closed) failure = new IOException("Transmission failed: " + e.getMessage(), e);
            }
        } finally {
            connected.countDown();
            close();
        }
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            queue.clear();
            notifyAll();
        }
        try { sink.close(); } catch (IOException ignored) { /* Socket already closed. */ }
        worker.interrupt();
    }

    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        worker.join(timeoutMillis);
        return !worker.isAlive();
    }
}
