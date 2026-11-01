package com.openggf.render;

import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Short rendering-category smoke witnesses, using the production boot and complete picture. */
@EnabledOnOs(OS.LINUX)
class TestRepeatedPictureDrawPurity {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void ghz1RepeatedPicturesPreserveRegisteredStateAndDetectorRejectsDrawMutation() throws Exception {
        check(RomTestUtils.ensureSonic1RomAvailable().toPath(), true);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void ehz1RepeatedPicturesPreserveRegisteredState() throws Exception {
        check(RomTestUtils.ensureSonic2RomAvailable().toPath(), false);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void aiz1RepeatedPicturesPreserveRegisteredState() throws Exception {
        check(RomTestUtils.ensureSonic3kRomAvailable().toPath(), false);
    }

    private static void check(Path rom, boolean negativeControl) throws Exception {
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            for (int service = 0; service < 3; service++) session.step(null);
            RewindRegistry registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            assertNotNull(registry);
            assertTrue(registry.capture().entries().size() > 10, "Exercise the live session registry");
            // Include the first picture: cache warming must not alter registered state either.
            for (int extraDraws : List.of(1, 3)) {
                assertPure(registry, session::renderFrame, extraDraws);
            }
            if (negativeControl) {
                var mutator = new MutatingDrawObject();
                registry.register(mutator);
                try {
                    AssertionError failure = assertThrows(AssertionError.class, () -> assertPure(registry, () -> {
                        session.renderFrame();
                        // Inject a test-only object submission into the same picture boundary.
                        mutator.appendRenderCommands(new ArrayList<>());
                    }, 1));
                    assertTrue(failure.getMessage().contains("draw-mutation-probe"), failure.getMessage());
                    assertEquals(1, mutator.phase);
                } finally {
                    registry.deregister(mutator.key());
                }
            }
        }
    }

    private static void assertPure(RewindRegistry registry, Runnable draw, int extraDraws) {
        CompositeSnapshot before = registry.capture();
        for (int i = 0; i < extraDraws; i++) draw.run();
        CompositeSnapshot after = registry.capture();
        assertEquals(before.entries().keySet(), after.entries().keySet(), "Draw changed registry membership");
        var differences = new ArrayList<String>();
        for (String key : before.entries().keySet()) {
            differences.addAll(RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)));
        }
        assertEquals(List.of(), differences, "Repeated pictures advanced registered state");
    }

    private static final class MutatingDrawObject extends AbstractObjectInstance implements RewindSnapshottable<Integer> {
        private int phase;
        MutatingDrawObject() { super(new ObjectSpawn(100, 80, 1, 0, 0, false, 80), "draw mutation probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { phase++; }
        @Override public String key() { return "draw-mutation-probe"; }
        @Override public Integer capture() { return phase; }
        @Override public void restore(Integer snapshot) { phase = snapshot; }
    }
}
