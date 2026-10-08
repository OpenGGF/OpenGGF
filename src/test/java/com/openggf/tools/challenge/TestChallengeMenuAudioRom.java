package com.openggf.tools.challenge;

import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real ROM cues must survive the standalone source/output ownership boundary. */
@RequiresRom(SonicGame.SONIC_1)
class TestChallengeMenuAudioRom {
    @Test
    void nativeCuesReachCaptureDeviceAndFocusedMix(@TempDir Path directory) throws Exception {
        RecordingDevice device = new RecordingDevice();
        var failures = new ArrayList<Throwable>();
        try (var sink = new OpenAlPcmSink(device, failures::add, () -> 0L, ignored -> {});
             var capture = new ChallengeCapture(directory);
             var menu = new ChallengeMenuAudio(RomTestUtils.ensureSonic1RomAvailable().toPath(), sink, capture)) {
            long now = 1_000_000_000L;
            for (ChallengeMenuAudio.Cue cue : ChallengeMenuAudio.Cue.values()) {
                int firstPacket = device.packets.size();
                menu.cue(cue);
                for (int tick = 0; tick < 90; tick++) menu.update(true, now += 16_666_667L);
                assertTrue(hasVariation(device.packets.subList(firstPacket, device.packets.size())),
                        "Native cue must reach the retained device sink: " + cue);
            }
            int audiblePackets = device.packets.size();
            menu.cue(ChallengeMenuAudio.Cue.SELECT);
            for (int tick = 0; tick < 4; tick++) menu.update(false, now += 16_666_667L);
            assertEquals(audiblePackets, device.packets.size(), "gameplay mixes UI feedback at its focus owner");
            short[] game = new short[1600];
            assertTrue(hasVariation(List.of(menu.mix(game))), "focus change feedback reaches final mix");
            assertSame(game, menu.mix(game), "one UI packet is consumed once");
            assertTrue(failures.isEmpty(), failures.toString());
        }
        byte[] wav = Files.readAllBytes(directory.resolve("menu.wav"));
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(48_000, header.getInt(24));
        assertEquals(wav.length - 44, header.getInt(40));
        assertTrue(wav.length > 44 + 1600 * 2 * 450, "captured actual presentation packets");
    }

    private static boolean hasVariation(List<short[]> packets) {
        int min = Short.MAX_VALUE, max = Short.MIN_VALUE;
        for (short[] packet : packets) for (short value : packet) {
            min = Math.min(min, value); max = Math.max(max, value);
        }
        return max - min > 100;
    }

    private static final class RecordingDevice implements OpenAlPcmSink.Device {
        final List<short[]> packets = new ArrayList<>();
        public int initialize() { return 48_000; }
        public void enqueue(short[] pcm, int frames, int rate) { packets.add(pcm.clone()); }
        public int update() { return 0; }
        public void flush() {}
        public void pause() {}
        public void resume() {}
        public void close() {}
    }
}
