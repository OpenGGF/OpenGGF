package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.common.CdRing;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.animation.StageRingAnimationFrameProvider;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
@EnabledOnOs(OS.LINUX)
class TestCdStageRingSubmissions {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @AfterEach void cleanup() {
        GameServices.configuration().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @Test
    void eachVisibleRingObjectSubmitsOneSpriteUnderNativeLimits() throws Exception {
        var settings = settings(320);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(disc, new SonicCdGameModule(), 0, 0, settings);
            moveCamera(session, 0x620, 0x220);

            var loaded = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(CdRing.class::isInstance)
                    .map(CdRing.class::cast)
                    .filter(ring -> !ring.isDestroyed())
                    .sorted(Comparator.comparingInt(CdRing::getPriorityBucket).thenComparingInt(CdRing::getSlotIndex))
                    .toList();
            List<RingSpawn> expected = loaded.stream()
                    .filter(CdRing::isOnScreenForTouch)
                    .map(ring -> new RingSpawn(ring.getX(), ring.getY()))
                    .toList();
            assertTrue(expected.size() >= 4, "exercise a group of visible rings");
            assertTrue(loaded.size() > expected.size(), "exercise loaded rings rejected by DrawObject");
            assertEquals(expected, ringEntries(session).stream().map(TestCdStageRingSubmissions::centre).toList(),
                    "visible rings must enter the sprite table exactly once, in object order");
        }
    }

    @Test
    void nativeWidescreenDrawsDormantLayoutRingsOnTheLiveSpinFrame() throws Exception {
        var settings = settings(800);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var session = new GameplayCaptureSession(settings)) {
            session.boot(disc, new SonicCdGameModule(), 0, 0, settings);
            moveCamera(session, 0x27C, 0x710);
            for (int frame = 0; frame < 8; frame++) session.step(null);

            var level = GameServices.level();
            var camera = GameServices.camera();
            assertEquals(320, camera.getWidth());
            var animation = (StageRingAnimationFrameProvider) level.getAnimatedPatternManager();
            assertTrue(animation.stageRingAnimationFrame() != 0, "the live spin has advanced from frame zero");
            var dormant = level.getRingManager().resolveCanonicalSpawn(0x4A0, 0x7B0);
            assertTrue(dormant != null && !level.getRingManager().getActiveSpawns().contains(dormant),
                    "the second ring of the group must be outside the active window");
            var centre = new RingSpawn(dormant.x(), dormant.y());
            var entries = ringEntries(session);
            var live = new java.util.HashSet<RingSpawn>();
            for (var spawn : level.getRingManager().getActiveSpawns()) live.add(new RingSpawn(spawn.x(), spawn.y()));
            var liveWords = entries.stream().filter(entry -> live.contains(centre(entry)))
                    .map(entry -> entry.piece().rawTileWordLow11()).distinct().toList();
            assertEquals(1, liveWords.size(), "live placed rings share one spin frame");
            // D15: the dormant ring spins with the live rings, so it keeps its phase when it turns live.
            assertEquals(liveWords, entries.stream()
                    .filter(entry -> centre(entry).equals(centre))
                    .map(entry -> entry.piece().rawTileWordLow11())
                    .toList(), "submit the dormant ring once on the live rings' spin frame");
        }
    }

    private static void moveCamera(GameplayCaptureSession session, int x, int y) {
        var camera = GameServices.camera();
        camera.setFrozen(true);
        camera.setX((short) x);
        camera.setY((short) y);
        for (int frame = 0; frame < 3; frame++) session.step(null);
    }

    private static GameplayCaptureSession.Settings settings(int width) {
        return new GameplayCaptureSession.Settings(width, "sonic", "", "off",
                null, null, null, null, false, false, null, null,
                false, null, false, false, "NATIVE", "NORMAL");
    }

    private static RingSpawn centre(SpriteSatEntry entry) {
        var piece = entry.piece();
        return new RingSpawn(piece.x() + piece.widthTiles() * 4, piece.y() + piece.heightTiles() * 4);
    }

    private static List<SpriteSatEntry> ringEntries(GameplayCaptureSession session) throws ReflectiveOperationException {
        try (var tables = new SubmittedTables(GameServices.graphics())) {
            session.renderFrame();
            assertEquals(1, tables.tables.size(), "one sprite table per render");
            return tables.tables.getFirst().stream()
                    .filter(entry -> SpritePresentation.Layer.RINGS.name().equals(entry.piece().debugSource()))
                    .toList();
        }
    }

    /** Read-only observer of each submitted sprite table at collection teardown. */
    private static final class SubmittedTables extends ArrayList<SpriteSatEntry> implements AutoCloseable {
        private final GraphicsManager graphics;
        private final Field field;
        private final List<SpriteSatEntry> original;
        private final List<List<SpriteSatEntry>> tables = new ArrayList<>();

        @SuppressWarnings("unchecked")
        SubmittedTables(GraphicsManager graphics) throws ReflectiveOperationException {
            this.graphics = graphics;
            field = GraphicsManager.class.getDeclaredField("spriteSatEntries");
            field.setAccessible(true);
            original = (List<SpriteSatEntry>) field.get(graphics);
            assertTrue(original.isEmpty(), "attach only between collections");
            field.set(graphics, this);
        }

        @Override public void clear() {
            if (!isEmpty()) tables.add(List.copyOf(this));
            super.clear();
        }

        @Override public void close() throws ReflectiveOperationException {
            field.set(graphics, original);
        }
    }
}
