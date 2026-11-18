package com.openggf.game.soniccd.special;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdSpecialStageWidescreen {
    @Test
    void extendsTheFloorAndRetainsTheCentredNativePictureThroughRewind() throws Exception {
        TestEnvironment.resetAll();
        try {
            RgbaImage nativeFrame = capture(320);
            RgbaImage wideFrame = capture(400);
            int mismatches = 0;
            for (int y = 0; y < 224; y++) {
                for (int x = 0; x < 256; x++) {
                    if (nativeFrame.argb(32 + x, y) != wideFrame.argb(72 + x, y)) mismatches++;
                }
            }
            assertEquals(0, mismatches, "the centred 256 x 224 crop stays pixel-identical");

            HashSet<Integer> left = new HashSet<>();
            HashSet<Integer> right = new HashSet<>();
            for (int y = 140; y < 224; y++) {
                for (int x = 0; x < 72; x++) {
                    left.add(wideFrame.argb(x, y));
                    right.add(wideFrame.argb(328 + x, y));
                }
            }
            assertTrue(left.size() > 2, "the left wing shows floor detail");
            assertTrue(right.size() > 2, "the right wing shows floor detail");
        } finally {
            TestEnvironment.resetAll();
        }
    }

    private static RgbaImage capture(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var disc = CdSpecialNative.disc();
             var session = new GameplayCaptureSession(settings)) {
            session.boot(disc, new SonicCdGameModule(), 0, 0, settings);
            session.loop().enterSpecialStage();
            for (int i = 0; session.loop().getCurrentGameMode() != GameMode.SPECIAL_STAGE; i++) {
                assertTrue(i < 180, "special-stage entry completes");
                session.step(null);
            }
            for (int i = 0; i < 75; i++) session.step(null);
            var provider = (CdSpecialStageProvider) session.loop().getActiveSpecialStageProvider();
            var adapter = new CdSpecialStageRewindAdapter(provider);
            var checkpoint = adapter.capture();
            RgbaImage before = session.render();
            var turn = new Bk2FrameInput(0, 8, 0, false, "turn right");
            for (int i = 0; i < 15; i++) session.step(turn);
            assertFalse(Arrays.equals(before.pixels(), session.render().pixels()), "turning moves the picture");
            adapter.restore(checkpoint);
            assertArrayEquals(before.pixels(), session.render().pixels(), "rewind restores both floor edges");
            return before;
        }
    }
}
