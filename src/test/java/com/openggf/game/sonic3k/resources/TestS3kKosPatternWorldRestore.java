package com.openggf.game.sonic3k.resources;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual GameLoop VInt, full-registry/pixels and two-way pending-DMA replay. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKosPatternWorldRestore {
    @ParameterizedTest @ValueSource(ints = {320, 800})
    void pendingNativeDmaAndPublishedWorldsReplayBothDirectionsWithoutRenderRelease(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off",
                null, null, null, "1111111", false, false, 0, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 13, 1, settings);
            GameServices.rng().setSeed(0); // Declared setup before the first production Process_Sprites only.
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            CompositeSnapshot pending = null;
            for (int pass = 0; pass < 200; pass++) {
                session.step(null); session.renderFrame();
                var candidate = registry.capture();
                var transfer = (S3kPatternModuleTransfer.State) candidate.get(S3kPatternModuleTransfer.REWIND_KEY);
                if (!transfer.pendingDma().isEmpty()) { pending = candidate; break; }
            }
            assertNotNull(pending, "the real native ending producer must reach Add_To_DMA_Queue within the finite bound");
            var queuedBytes = SessionManager.getCurrentGameplayMode().getKosinskiModuleQueue().capture();
            var priorImage = session.render();
            same(pending, registry.capture());
            assertArrayEquals(priorImage.pixels(), session.render().pixels(), "rendering has no Process_DMA_Queue authority");
            assertEquals(queuedBytes, SessionManager.getCurrentGameplayMode().getKosinskiModuleQueue().capture());
            for (int i = 0; i < 8; i++) { session.step(null); session.renderFrame(); }
            var later = registry.capture(); var laterImage = session.render();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(pending); same(pending, registry.capture());
                assertArrayEquals(priorImage.pixels(), session.render().pixels(), "pending world restored pixels");
                for (int i = 0; i < 8; i++) { session.step(null); session.renderFrame(); }
                same(later, registry.capture());
                assertArrayEquals(laterImage.pixels(), session.render().pixels(), "forward native VInt replay");
                registry.restore(later); same(later, registry.capture());
                assertArrayEquals(laterImage.pixels(), session.render().pixels(), "later world restored pixels");
                registry.restore(pending); same(pending, registry.capture());
                assertArrayEquals(priorImage.pixels(), session.render().pixels(), "reverse native VInt restore");
            }
        }
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(
                key, expected.get(key), actual.get(key)).isEmpty(), key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }
}
