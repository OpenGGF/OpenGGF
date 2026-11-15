package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.common.CdAnimal;
import com.openggf.game.soniccd.objects.projector.CdProjector;
import com.openggf.game.soniccd.objects.projector.CdProjectorHologram;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind of Metal Sonic's projector, its holograms, its hologram animals and its explosions. The authored movie
 * ({@code rehost-fixtures-warp/R11B-destroy-out/R11B}, native reference, comparison only) is replayed to a row where the
 * projector stands with its four children live (row 1850) and to a row in the middle of its explosion schedule (row
 * 1990); the session's rewind registry captures, 90 frames run, the capture is restored and the same frames run again. Both
 * passes must give the same objects in the same dynamic slots frame by frame (class, position, routine, timer, schedule
 * pointer), and the restore must rebuild the captured projector parts in their slots.
 */
class TestCdProjectorRewind {
    @TempDir
    Path tempDir;

    @Test
    void projectorPartsRestoreInTheirSlotsAndReplayForward() throws Exception {
        Path physics = CdTestMedia.nativeFile("rehost-fixtures-warp/R11B-destroy-out/R11B/trace/physics.csv");
        Path root = physics.getParent().getParent();
        for (int row : new int[] {1850, 1990}) {
            List<String> first = new ArrayList<>(), second = new ArrayList<>();
            String[] atSnapshot = new String[2];
            try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
                CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve("r" + row)),
                        new CdFixtureReplay.Fixture(CdStagePayload.R11B, root.resolve("trace"), root.resolve("movie.bk2")),
                        row, false, -1, -1, new CdFixtureReplay.Hooks() {
                            @Override
                            public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                                atSnapshot[0] = slots();
                                CompositeSnapshot snapshot = registry.capture();
                                for (int i = 0; i < 90; i++) {
                                    runner.stepFrame(false, false, false, false, false);
                                    first.add(slots());
                                }
                                registry.restore(snapshot);
                                atSnapshot[1] = slots();
                                for (int i = 0; i < 90; i++) {
                                    runner.stepFrame(false, false, false, false, false);
                                    second.add(slots());
                                }
                            }
                        });
            }
            System.out.println("CD projector rewind at row " + row + ": " + atSnapshot[0]);
            assertTrue(atSnapshot[0].contains("CdProjector"), "the projector is live at row " + row + ": " + atSnapshot[0]);
            assertEquals(atSnapshot[0], atSnapshot[1], "restore rebuilds the captured objects in their slots (row " + row + ")");
            for (int i = 0; i < first.size(); i++) {
                assertEquals(first.get(i), second.get(i), "row " + row + ": frame " + (i + 1) + " after the restore");
            }
        }
    }

    /** Every live projector part, hologram animal and explosion in a dynamic or layout slot, by class and state. */
    private static String slots() {
        TreeMap<Integer, String> out = new TreeMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof AbstractObjectInstance a) || a.isDestroyed() || a.getSlotIndex() < 0) continue;
            boolean mine = o instanceof CdProjector || o instanceof CdProjectorHologram || o instanceof CdAnimal
                    || o instanceof com.openggf.game.soniccd.objects.common.CdExplosion;
            if (!mine) continue;
            out.put(a.getSlotIndex(), String.format("%s %04X,%04X %s", o.getClass().getSimpleName(), o.getX() & 0xFFFF,
                    o.getY() & 0xFFFF, a.traceDebugDetails()));
        }
        return out.toString();
    }
}
