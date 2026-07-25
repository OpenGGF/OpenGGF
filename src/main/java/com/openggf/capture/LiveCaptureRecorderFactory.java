package com.openggf.capture;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class LiveCaptureRecorderFactory {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final SonicConfigurationService config;
    private final Clock clock;
    private final String ffmpeg;

    public LiveCaptureRecorderFactory(SonicConfigurationService config, Clock clock, String ffmpeg) {
        this.config = config;
        this.clock = clock;
        this.ffmpeg = ffmpeg;
    }

    public CaptureRecorder create(CaptureViewport viewport, int frameRate) {
        return new CaptureRecorder(new FfmpegEncoder(ffmpeg, 1), BackpressurePolicy.BLOCK, 8,
                Path.of(config.getString(SonicConfiguration.CAPTURE_OUTPUT_DIR)),
                "live", TIMESTAMP.format(clock.instant()));
    }
}
