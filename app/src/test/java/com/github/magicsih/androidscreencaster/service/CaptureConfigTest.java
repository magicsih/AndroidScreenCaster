package com.github.magicsih.androidscreencaster.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureConfigTest {
    @Test public void rejectsUrlsPortsAndBlankInput() {
        for (String host : new String[]{"", " ", "tcp://host", "host:49152", "host/path", "bad host"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaptureConfig(host, false, "video/avc", 640, 360, 96, 1024000));
        }
    }
    @Test public void preservesIpv6AndHostnameSupport() {
        for (String host : new String[]{"::1", "192.168.1.1", "receiver.local"}) {
            assertEquals(host, new CaptureConfig(host, false, "video/avc", 640, 360, 96, 1024000).host);
        }
    }
}
