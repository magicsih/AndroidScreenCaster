package com.github.magicsih.androidscreencaster.writer;

import java.nio.ByteBuffer;

/** Framing only; transport sends these packets unchanged and in order. */
public final class VideoPackets {
    private VideoPackets() { }

    public static byte[] ivfHeader(int width, int height) {
        return IvfWriter.makeIvfHeader(0, width, height, 1, 1_000_000);
    }

    public static byte[] ivfFrame(byte[] data, long presentationTimeUs) {
        byte[] header = IvfWriter.makeIvfFrameHeader(data.length, presentationTimeUs);
        byte[] packet = new byte[header.length + data.length];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(data, 0, packet, header.length, data.length);
        return packet;
    }

    public static byte[] copy(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
