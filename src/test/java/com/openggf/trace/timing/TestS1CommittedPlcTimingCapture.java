package com.openggf.trace.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the complete, independently captured S1 PLC arm stream, not a selected subset. */
class TestS1CommittedPlcTimingCapture {
    @Test
    void completeRunRetainsNativeTimingStreamsAndMarbleArm() throws Exception {
        Path root = Path.of(System.getProperty("project.basedir", "."),
                "src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds");
        List<Path> streams;
        try (var files = Files.walk(root)) {
            streams = files.filter(path -> path.getFileName().toString().equals("hardware_timing.jsonl.gz"))
                    .sorted().toList();
        }
        assertEquals(28, streams.size());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ObjectMapper mapper = new ObjectMapper();
        int count = 0;
        for (Path stream : streams) {
            byte[] bytes;
            try (var input = new GZIPInputStream(Files.newInputStream(stream))) {
                bytes = input.readAllBytes();
            }
            digest.update(stream.getParent().getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(bytes);
            count += new String(bytes, StandardCharsets.UTF_8).lines().count();
        }
        assertEquals(242, count);
        assertEquals("711fb8352d1dd5b9c700508ff6b2394999c3bc9b4fb2eecc7bd2b4f3d426f5eb",
                HexFormat.of().formatHex(digest.digest()));
        Path marble = root.resolve("mz2_3/hardware_timing.jsonl.gz");
        try (var input = new GZIPInputStream(Files.newInputStream(marble))) {
            var events = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(line -> {
                        try { return mapper.readTree(line); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                    }).toList();
            var arm = events.stream().filter(event -> event.path("ordinal").asInt() == 59)
                    .findFirst().orElseThrow();
            assertEquals(101, arm.path("raw_frame").asInt());
            assertEquals("sha256:0495d001d7b7d63f2d70ab32c084cb31f69866a3a401d712c7ecc2861dd2206e",
                    arm.path("submission_fingerprint").asText());
        }
    }
}
