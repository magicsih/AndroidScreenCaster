package com.github.magicsih.androidscreencaster.network;

import java.io.Closeable;
import java.io.IOException;

/** One connection, owned by a sender thread; close must unblock writes. */
public interface SocketSink extends Closeable {
    void connect() throws IOException;
    void write(byte[] data) throws IOException;
}
