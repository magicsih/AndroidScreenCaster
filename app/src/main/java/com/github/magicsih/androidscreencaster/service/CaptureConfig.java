package com.github.magicsih.androidscreencaster.service;

public final class CaptureConfig {
    public final String host;
    public final boolean udp;
    public final String mime;
    public final int width;
    public final int height;
    public final int dpi;
    public final int bitrate;

    public CaptureConfig(String host, boolean udp, String mime, int width, int height,
                         int dpi, int bitrate) {
        if (host == null || host.trim().isEmpty() || host.contains("://")
                || host.matches(".*\\s.*") || host.contains("/")
                || (host.indexOf(':') >= 0 && host.indexOf(':') == host.lastIndexOf(':'))) {
            throw new IllegalArgumentException("Enter a receiver IP address or hostname, without a port or URL");
        }
        if (!("video/avc".equals(mime) || "video/x-vnd.on2.vp8".equals(mime))
                || width <= 0 || height <= 0 || dpi <= 0 || bitrate <= 0) {
            throw new IllegalArgumentException("Invalid capture settings");
        }
        this.host = host.trim();
        this.udp = udp;
        this.mime = mime;
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.bitrate = bitrate;
    }
}
