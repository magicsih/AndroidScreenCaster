package com.github.magicsih.androidscreencaster.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

/** Raw Annex B / IVF bytes; UDP retains the original 1024-byte fragmentation. */
public final class NetworkSink implements SocketSink {
    private final String host;
    private final int port;
    private final boolean udp;
    private boolean closed;
    private SocketChannel tcp;
    private Selector selector;
    private DatagramSocket datagram;
    private InetSocketAddress address;

    public NetworkSink(String host, int port, boolean udp) {
        this.host = host;
        this.port = port;
        this.udp = udp;
    }

    @Override public void connect() throws IOException {
        // DNS runs on the sender thread. The capture thread limits its entire wait to 5 s.
        InetSocketAddress resolved = new InetSocketAddress(host, port);
        if (resolved.isUnresolved()) throw new IOException("Cannot resolve receiver address");
        synchronized (this) {
            if (closed) throw new IOException("Connection cancelled");
            address = resolved;
            if (udp) {
                datagram = new DatagramSocket();
                datagram.connect(address);
                return;
            }
            tcp = SocketChannel.open();
            tcp.configureBlocking(false);
            tcp.socket().setTcpNoDelay(true);
            selector = Selector.open();
            tcp.register(selector, SelectionKey.OP_CONNECT);
        }
        if (!tcp.connect(address)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!tcp.finishConnect()) awaitReady(deadline);
        }
        tcp.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
    }

    private void awaitReady(long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("Receiver timed out");
        selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
        selector.selectedKeys().clear();
    }

    @Override public void write(byte[] data) throws IOException {
        if (udp) {
            for (int offset = 0; offset < data.length; offset += 1024) {
                datagram.send(new DatagramPacket(data, offset,
                        Math.min(1024, data.length - offset), address));
            }
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
            while (buffer.hasRemaining()) {
                if (tcp.write(buffer) == 0) awaitReady(deadline);
                if (System.nanoTime() > deadline) throw new IOException("Receiver is too slow");
            }
        }
    }

    @Override public synchronized void close() throws IOException {
        closed = true;
        try {
            if (datagram != null) datagram.close();
            if (tcp != null) tcp.close();
        } finally {
            if (selector != null) selector.close();
        }
    }
}
