package com.github.magicsih.androidscreencaster.writer;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

public class VideoPacketsTest {
    @Test public void ivfHasMicrosecondTimebaseAndCorrectDimensions() {
        ByteBuffer h = ByteBuffer.wrap(VideoPackets.ivfHeader(640, 360)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(32, h.remaining());
        assertEquals("DKIF", new String(new byte[]{h.get(), h.get(), h.get(), h.get()}, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(0, h.getShort());
        assertEquals(32, h.getShort());
        assertEquals(0x30385056, h.getInt());
        assertEquals(640, h.getShort());
        assertEquals(360, h.getShort());
        assertEquals(1_000_000, h.getInt());
        assertEquals(1, h.getInt());
        assertEquals(0, h.getInt());
        assertEquals(0, h.getInt());
    }
    @Test public void framePreserves64BitTimestampAndPayload() {
        byte[] payload = {1, 2, 3};
        ByteBuffer frame = ByteBuffer.wrap(VideoPackets.ivfFrame(payload, 5_000_000_123L)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3, frame.getInt());
        assertEquals(5_000_000_123L, frame.getLong());
        byte[] actual = new byte[3]; frame.get(actual);
        assertArrayEquals(payload, actual);
    }
    @Test public void codecBufferCopyKeepsPositionAndRange() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{0, 1, 2, 3});
        original.position(1); original.limit(3);
        assertArrayEquals(new byte[]{1, 2}, VideoPackets.copy(original));
        assertEquals(1, original.position());
    }
}
