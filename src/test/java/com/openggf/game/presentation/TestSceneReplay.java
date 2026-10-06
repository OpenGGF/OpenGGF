package com.openggf.game.presentation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSceneReplay {
    private static ScenePresentationFrame scene(int x) {
        return new ScenePresentationFrame(0, 0, 320, 224, x, 0, 0xff102030,
                new int[64], List.of(), List.of());
    }

    @Test void reverseKeepsBothEndpointsAndLeavesAnOriginRowForSynchronizedAudio() {
        var replay = new SceneReplay();
        for (int tick = 0; tick <= 1200; tick++)
            if (replay.wantsSample(tick)) replay.record(tick, scene(tick));
        assertTrue(replay.snapshot().samples().size() <= SceneReplay.MAX_SAMPLES);
        replay.begin(1200, 14, scene(1200));
        assertEquals(1200, replay.frame(1).cameraX());
        int last = 1200;
        while (!replay.atOrigin()) {
            replay.step();
            int x = replay.frame(2).cameraX(); assertTrue(x <= last); last = x;
        }
        assertEquals(0, replay.frame(3).cameraX());
        assertTrue(replay.playing(), "origin must remain available for its outer audio packet");
        replay.clear(); assertNull(replay.frame(4));
    }

    @Test void snapshotRestoresTheSameRemainingReverseViewsWithoutMutableByteAliases() {
        var replay = new SceneReplay();
        replay.record(0, scene(0)); replay.record(3, scene(3)); replay.begin(9, 2, scene(9));
        replay.step(); var saved = replay.snapshot();
        byte[] exposed = saved.samples().getFirst().scene(); exposed[0] = 0;
        replay.step(); var expected = replay.frame(7);
        replay.restore(saved); replay.step(); assertEquals(expected.cameraX(), replay.frame(7).cameraX());
        assertThrows(IllegalArgumentException.class, () -> replay.record(-1, scene(0)));
        assertThrows(IllegalArgumentException.class, () -> replay.record(1, scene(1)));
        assertThrows(IllegalArgumentException.class, () -> replay.begin(9, 0, scene(9)));
        assertEquals(expected.cameraX(), replay.frame(7).cameraX(), "invalid updates leave the reverse view intact");
    }
}
