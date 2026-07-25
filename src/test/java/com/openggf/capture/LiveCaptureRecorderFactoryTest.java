package com.openggf.capture;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveCaptureRecorderFactoryTest {
    @Test void usesConfiguredDirectoryFixedUtcTimestampAndLiveLabel() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        try {
            config.setSessionOverride(SonicConfiguration.CAPTURE_OUTPUT_DIR, "target/my-live");
            Clock clock = Clock.fixed(Instant.parse("2026-07-23T10:11:12.345Z"), ZoneOffset.UTC);
            CaptureRecorder recorder = new LiveCaptureRecorderFactory(config, clock, "ffmpeg")
                    .create(new CaptureViewport(4, 5, 320, 224), 60);
            assertEquals(Path.of("target/my-live/capture-live-20260723-101112-345.mkv"),
                    recorder.outputFile());
        } finally {
            config.clearSessionOverrides();
        }
    }
}
