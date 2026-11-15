package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Sonic CD stage plays the same after another stage in the same session as it does first: a player goes from
 * stage to stage in one process, so state that survives a stage change is a gameplay bug. Two full-act native
 * routes ({@code rehost-long}), or a route and a boss movie ({@code rehost-boss-mmz}), user-local and skipped
 * without them, are replayed in both orders in one session, the first stopped two thirds of the way through
 * (mid-act, where the most state is live), the second loaded over it through the level owner's stage load, as {@link CdFixtureReplay#replaySequence} does. Each second stage must
 * equal the same stage replayed alone: its comparison with native (first divergence and rows in error) and, on
 * every row, the engine's own player, camera, ring count and object table.
 *
 * <p>What this caught (lane SESSION): the object window seeded from the previous stage's camera, the movement
 * manager's stage word and pad latches (Wacky Workbench's beam mode and hang timer), the camera Y centre and the
 * object V-int clock. Sonic CD's per-stage state is cleared at its stage load
 * ({@code SonicCdLevelInitProfile}, {@code CdPlayerStage.resetForLevel}) or through
 * {@code StageLoadClearsStageRam}; see docs/architecture/implementation-pitfalls.md.
 */
class TestSonicCdStageChangeIsolation {
    @TempDir
    Path tempDir;

    @Test
    void wackyWorkbenchActsPlayAsAloneAfterEachOther() throws Exception {
        // R61B stopped mid-act leaves the movement stage word (beam mode, hang timer) and a camera far right.
        assertBothOrdersPlayAsAlone(longRoute(CdStagePayload.R61B), longRoute(CdStagePayload.R62A));
    }

    @Test
    void palmtreePanicAndAMetallicMadnessBossPlayAsAloneAfterEachOther() throws Exception {
        // R11A stopped on its 3D ramp leaves camYCenter at $88; R83D's fight reads the object V-int clock.
        Path dir = CdTestMedia.nativeFile("rehost-boss-mmz/R83D-fight/movie.bk2").getParent();
        assertBothOrdersPlayAsAlone(longRoute(CdStagePayload.R11A),
                new CdFixtureReplay.Fixture(CdStagePayload.R83D, dir.resolve("trace"), dir.resolve("movie.bk2")));
    }

    private static CdFixtureReplay.Fixture longRoute(CdStagePayload payload) {
        return CdFixtureReplay.Fixture.under(CdTestMedia.nativeFile("rehost-long/README.md").getParent(), payload);
    }

    private void assertBothOrdersPlayAsAlone(CdFixtureReplay.Fixture first, CdFixtureReplay.Fixture second)
            throws Exception {
        Path cue = CdTestMedia.usaCue();
        Assumptions.assumeTrue(first.present() && second.present(), "Native routes absent: " + first + ", " + second);
        List<String> failures;
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            failures = differences(disc, tempDir, first, second);
        }
        assertEquals(List.of(), failures, "a stage played after another differs from the stage alone");
    }

    /**
     * Both orders of {@code first} and {@code second}: each stage alone, then after the other stopped at two thirds of
     * its route. Returns one line per order whose second stage differs from that stage alone.
     */
    static List<String> differences(CdDiscDataSource disc, Path tempDir, CdFixtureReplay.Fixture first,
                                    CdFixtureReplay.Fixture second) throws Exception {
        List<String> failures = new ArrayList<>();
        for (CdFixtureReplay.Fixture[] order : new CdFixtureReplay.Fixture[][] {{first, second}, {second, first}}) {
            Run alone = run(disc, tempDir, List.of(new CdFixtureReplay.Leg(order[1], Integer.MAX_VALUE)));
            int stop = com.openggf.trace.TraceData.load(order[0].traceDir()).frameCount() * 2 / 3;
            Run after = run(disc, tempDir, List.of(new CdFixtureReplay.Leg(order[0], stop),
                    new CdFixtureReplay.Leg(order[1], Integer.MAX_VALUE)));
            String label = order[0].payload() + " to row " + stop + ", then " + order[1].payload();
            assertTrue(alone.rows.size() > 100, label + ": rows replayed alone " + alone.rows.size());
            String difference = difference(alone, after);
            if (difference != null) failures.add(label + ": " + difference);
        }
        return failures;
    }

    /** The last leg's comparison with native and its engine rows. */
    record Run(CdFixtureReplay.Outcome outcome, List<String> rows) { }

    private static Run run(CdDiscDataSource disc, Path tempDir, List<CdFixtureReplay.Leg> legs) throws Exception {
        List<String> rows = new ArrayList<>();
        int[] leg = {-1};
        CdFixtureReplay.Hooks hooks = new CdFixtureReplay.Hooks() {
            @Override
            public void row(int row, AbstractPlayableSprite sonic) {
                if (row == 0) leg[0]++;
                if (leg[0] == legs.size() - 1) rows.add(engineRow(row, sonic));
            }
        };
        Path config = Files.createDirectories(tempDir.resolve("run" + System.nanoTime()));
        List<CdFixtureReplay.Outcome> outcomes =
                CdFixtureReplay.replaySequence(disc, config, legs, false, -1, -1, hooks);
        return new Run(outcomes.getLast(), rows);
    }

    /** The engine after a row: player, camera, rings and every object by slot. */
    private static String engineRow(int row, AbstractPlayableSprite sonic) {
        StringBuilder sb = new StringBuilder();
        sb.append(row).append(" player ").append(Integer.toHexString(sonic.getCentreX() & 0xFFFF)).append(',')
                .append(Integer.toHexString(sonic.getCentreY() & 0xFFFF)).append(" sub ")
                .append(sonic.getXSubpixelRaw()).append(',').append(sonic.getYSubpixelRaw()).append(" speed ")
                .append(sonic.getXSpeed()).append(',').append(sonic.getYSpeed()).append(',').append(sonic.getGSpeed())
                .append(" anim ").append(sonic.getAnimationId()).append('/').append(sonic.getMappingFrame())
                .append(" status ").append(CdFixtureReplay.statusByte(sonic)).append(" camera ")
                .append(GameServices.camera().getX()).append(',').append(GameServices.camera().getY())
                .append(" rings ").append(sonic.getRingCount()).append(" objects");
        List<ObjectInstance> objects = new ArrayList<>(GameServices.level().getObjectManager().getActiveObjects());
        objects.removeIf(ObjectInstance::isDestroyed);
        objects.sort(Comparator.comparingInt(o -> o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -1));
        for (ObjectInstance o : objects) {
            int slot = o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -1;
            sb.append(' ').append(slot).append(':').append(o.getClass().getSimpleName()).append('@')
                    .append(Integer.toHexString(o.getX() & 0xFFFF)).append(',').append(Integer.toHexString(o.getY() & 0xFFFF));
        }
        return sb.toString();
    }

    /** {@code null} when the stage after the other equals the stage alone, else the first difference. */
    static String difference(Run alone, Run after) {
        CdFixtureReplay.Outcome a = alone.outcome(), b = after.outcome();
        if (a.firstErrorRow() != b.firstErrorRow() || a.errorRows() != b.errorRows()
                || a.rowsCompared() != b.rowsCompared()) {
            return "native comparison: alone first divergence " + a.firstErrorRow() + " (" + a.errorRows()
                    + " rows in error), after the other " + b.firstErrorRow() + " (" + b.errorRows() + ") " + b.fields();
        }
        for (int i = 0; i < Math.min(alone.rows().size(), after.rows().size()); i++) {
            if (!alone.rows().get(i).equals(after.rows().get(i))) {
                return "engine row differs:\n  alone " + alone.rows().get(i) + "\n  after " + after.rows().get(i);
            }
        }
        return alone.rows().size() == after.rows().size() ? null
                : "rows replayed " + alone.rows().size() + " alone, " + after.rows().size() + " after the other";
    }
}
