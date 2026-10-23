package com.openggf.level.rings;

import com.openggf.game.rewind.snapshot.RingSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The placed-list and reserved-slot draw producers share art but never mutate ring state. */
class TestRingDrawEmission {
    private GraphicsManager graphics;
    private RingManager rings;
    private RingSpawn placed;

    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        placed = new RingSpawn(64, 64);
        var sheet = new RingSpriteSheet(new Pattern[]{new Pattern(), new Pattern()},
                List.of(new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0))),
                        new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 1, false, false, 0)))),
                1, 8, 6, 1, 1);
        rings = new RingManager(List.of(placed), sheet, null, null, null);
        rings.ensurePatternsCached(graphics, 200);
        rings.reset(0);
        rings.update(0, null, 0);
    }

    @AfterEach void cleanup() { SessionManager.clear(); graphics.resetState(); }

    @Test void placedAndAttractedDrawsAreDisjointAndAttractedOrderUsesReservedSlots() {
        RingSnapshot base = rings.capture();
        rings.restore(with(base, base.collectedWords(), base.sparkleTimers(), new RingSnapshot.AttractedRingEntry[]{
                attracted(0, 60, 160, false), attracted(1, 40, 120, true)}));
        var placedFrame = prepare(() -> RingDrawInternalAccess.drawPlaced(rings, 0));
        assertEquals(List.of(64f), placedFrame.tiles().stream().map(SpritePresentation.Tile::x).toList());
        assertEquals(List.of(40, 60), rings.attractedRenderEntries().stream().map(e -> e.slot()).toList());
        assertEquals(List.of(40), RingDrawInternalAccess.slottedAttracted(rings, 1).stream().map(e -> e.slot()).toList());
        assertEquals(List.of(60), RingDrawInternalAccess.slottedAttracted(rings, 2).stream().map(e -> e.slot()).toList());
        var attractedFrame = prepare(() -> RingDrawInternalAccess.drawAttracted(rings, 0));
        assertEquals(List.of(160f, 120f), attractedFrame.tiles().stream().map(SpritePresentation.Tile::x).toList(),
                "prepared painter list reverses native reserved-slot emission");
        assertTrue(attractedFrame.tiles().stream().allMatch(SpritePresentation.Tile::priority),
                "S3K attracted ring art stays high priority");
    }

    @Test void expiredSparkleDrawDoesNotClearCapturedPlacementState() {
        RingSnapshot base = rings.capture();
        rings.restore(with(base, new long[]{1}, new RingSnapshot.SparkleEntry[]{new RingSnapshot.SparkleEntry(0, 10)},
                new RingSnapshot.AttractedRingEntry[0]));
        var before = rings.capture();
        assertTrue(prepare(() -> RingDrawInternalAccess.drawPlaced(rings, 100)).tiles().isEmpty());
        var after = rings.capture();
        assertArrayEquals(before.collectedWords(), after.collectedWords());
        assertArrayEquals(before.sparkleTimers(), after.sparkleTimers());
        assertArrayEquals(before.activeSpawnIndices(), after.activeSpawnIndices());
        assertEquals(10, rings.getSparkleStartFrame(placed));
    }

    @Test void nativePlacedRingTopEdgeDistinguishesShippedMaskOrderAndScreenWrap() {
        var above = new RingSpawn(64, -4);
        for (SonicGame game : List.of(SonicGame.SONIC_2, SonicGame.SONIC_3K)) {
            RingManager manager = managerFor(game, List.of(above));
            var frame = prepare(() -> RingDrawInternalAccess.drawPlaced(manager, 0, 0, 0xFFFF));
            assertEquals(game == SonicGame.SONIC_3K, !frame.tiles().isEmpty(),
                    "S2 FixBugs=0 masks before +8; S3K adds8 before Screen_Y_wrap_value");
        }
        RingManager wrapped = managerFor(SonicGame.SONIC_3K, List.of(new RingSpawn(64, 2044)));
        assertTrue(prepare(() -> RingDrawInternalAccess.drawPlaced(wrapped, 0, 0, 0xFFFF)).tiles().isEmpty());
        assertFalse(prepare(() -> RingDrawInternalAccess.drawPlaced(wrapped, 0, 0, 0x7FF)).tiles().isEmpty(),
                "the native wrap mask relocates admitted art as well as testing visibility");
    }

    @Test void verticallyCulledStageListDoesNotConsumeTheHardwareTableBeforeVisibleObjects() {
        var remote = new java.util.ArrayList<RingSpawn>();
        for (int i = 0; i < 81; i++) remote.add(new RingSpawn(32 + i * 2, 512));
        for (SonicGame game : List.of(SonicGame.SONIC_2, SonicGame.SONIC_3K)) {
            RingManager manager = managerFor(game, remote);
            var frame = prepare(() -> {
                RingDrawInternalAccess.drawPlaced(manager, 0, 0, 0xFFFF);
                graphics.submitSpriteSatPiece(new com.openggf.level.render.SpritePieceRenderer.PreparedPiece(
                        144, 64, 1, 1, 200, 0, 1, false, false, false, false,
                        SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, null));
            });
            assertEquals(List.of(144f), frame.tiles().stream().map(SpritePresentation.Tile::x).toList(),
                    "BuildRings/Render_Rings culls before writing SAT, independently of VDP vertical evaluation");
        }
    }

    private RingManager managerFor(SonicGame game, List<RingSpawn> spawns) {
        TestEnvironment.configureGameModuleFixture(game);
        var sheet = new RingSpriteSheet(new Pattern[]{new Pattern()}, List.of(
                new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)))), 1, 8, 1, 0);
        var manager = new RingManager(spawns, sheet, null, null, null);
        manager.ensurePatternsCached(graphics, 200);
        manager.reset(0);
        return manager;
    }

    private SpritePresentation.Frame prepare(Runnable producer) {
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            SpriteSatInternalAccess.beginCollection(graphics, SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
            producer.run();
            graphics.endSpriteSatCollectionAndReplay();
        });
    }

    private static RingSnapshot.AttractedRingEntry attracted(int pool, int slot, int x, boolean collected) {
        return new RingSnapshot.AttractedRingEntry(true, 0, x, 64, 0, 0, 0, 0,
                pool, slot, collected, 0, 0, 0, 0);
    }

    private static RingSnapshot with(RingSnapshot b, long[] collected, RingSnapshot.SparkleEntry[] sparkle,
                                      RingSnapshot.AttractedRingEntry[] attracted) {
        return new RingSnapshot(collected, sparkle, b.placementCursorIndex(), b.placementLastCameraX(),
                b.activeSpawnIndices(), b.lostRingActiveCount(), b.spillAnimCounter(), b.spillAnimAccum(),
                b.spillAnimFrame(), b.lostRingFrameCounter(), b.lostRings(), attracted);
    }
}
