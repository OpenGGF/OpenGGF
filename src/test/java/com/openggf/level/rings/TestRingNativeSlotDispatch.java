package com.openggf.level.rings;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.snapshot.RingSnapshot;
import com.openggf.graphics.*;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.level.objects.NativeSpriteSubmissionAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real slot walk + numeric DisplaySprite queue; independent native motion/animation edges. */
class TestRingNativeSlotDispatch {
    private static final int BASE = 300;
    private GraphicsManager graphics;
    private LevelManager level;
    private ObjectManager objects;
    private RingManager rings;
    private Sonic player;
    private final AtomicInteger levelClock = new AtomicInteger(100);

    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        level = mock(LevelManager.class);
        when(level.getFrameCounter()).thenAnswer(ignored -> levelClock.get());
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(false);
        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.getMaxY()).thenReturn((short) 1024);
        var services = new TestObjectServices().withLevelManager(level).withCamera(camera)
                .withGraphicsManager(graphics).withGameModule(GameServices.currentOrBootstrapGameModule());
        objects = new ObjectManager(List.of(), new EmptyRegistry(), 0, null, null, graphics, camera, services);
        when(level.getObjectManager()).thenReturn(objects);
        rings = new RingManager(List.of(new RingSpawn(144, 64)), sheet(), level, null, null);
        when(level.getRingManager()).thenReturn(rings);
        rings.ensurePatternsCached(graphics, BASE);
        rings.reset(0);
        player = new Sonic("sonic", (short) 0, (short) 0);
        NativePositionOps.writeXPosResetSubpixel(player, 128);
        NativePositionOps.writeYPosResetSubpixel(player, 64);
        player.giveShield(ShieldType.LIGHTNING);
    }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void actualSlotTargetsAndLateClockSuppressExactlyOneFallbackStep() {
        restoreRings(attracted(0, 6, 128, false, 0, 0, 0),
                attracted(1, 9, 128, false, 0, 0, 0));
        NativePositionOps.writeXPosResetSubpixel(player, 80);
        add(7, () -> NativePositionOps.writeXPosResetSubpixel(player, 200));
        step();
        var moved = rings.capture().attractedRings();
        assertEquals(-0xC0, moved[0].xVel(), "slot6 sees Player1 before slot7 carries him");
        assertEquals(0x30, moved[1].xVel(), "slot9 sees the earlier slot's carry");
        assertEquals(List.of(new Admission(6, 2), new Admission(9, 2)), admissions(objects));
        rings.update(0, player, 101, false); // LM100+1, while OM local counter is only1
        assertArrayEquals(moved, rings.capture().attractedRings(), "the old late phase cannot move them twice");
        assertTrue(RingDrawInternalAccess.slottedAttracted(rings, 2).isEmpty());
        assertNotNull(rings.resolveNativeSlot(6), "late lookup still exposes admitted current art");
        var picture = draw();
        assertEquals(2, picture.tiles().size());
        assertTrue(picture.tiles().stream().allMatch(tile -> tile.layer() == SpritePresentation.Layer.RINGS));
        var noObjects = SpritePresentationRenderer.prepare(graphics, 0, 0, () ->
                SpritePresentationRenderer.drawNative(graphics, picture, 0, 0,
                        layer -> layer != SpritePresentation.Layer.OBJECT));
        var noRings = SpritePresentationRenderer.prepare(graphics, 0, 0, () ->
                SpritePresentationRenderer.drawNative(graphics, picture, 0, 0,
                        layer -> layer != SpritePresentation.Layer.RINGS));
        assertEquals(2, noObjects.tiles().size(), "attracted art is independent of object visibility");
        assertTrue(noRings.tiles().isEmpty(), "ring visibility owns the actual queued attracted art");
    }

    @Test void newlyAllocatedLaterSlotExecutesButPastSlotWaitsWithoutLateRescue() {
        for (boolean later : List.of(false, true)) {
            objects.reset(0);
            rings.reset(0);
            if (later) for (int slot = 4; slot < 10; slot++) add(slot, () -> { });
            add(10, () -> rings.attractStageRings(player));
            step();
            var created = rings.capture().attractedRings()[0];
            assertEquals(later ? 11 : 4, created.objectSlotIndex());
            assertEquals(later ? -0xC0 : 0, created.xVel());
            assertEquals(later ? List.of(new Admission(11, 2)) : List.of(), admissions(objects));
            rings.update(0, player, 101, false);
            assertEquals(created, rings.capture().attractedRings()[0]);
            if (!later) {
                nextClock(); step();
                assertEquals(-0xC0, rings.capture().attractedRings()[0].xVel());
                assertEquals(List.of(new Admission(4, 2)), admissions(objects));
                levelClock.set(100);
            }
        }
    }

    @Test void shortenedAndEmptyWalksNeverAdvanceUnvisitedRingsInLateFallback() {
        restoreRings(attracted(0, 9, 144, false, 0, 0, 0));
        final Probe[] cutoff = new Probe[1];
        cutoff[0] = add(4, () -> objects.overrideRemainingObjectLoopSlots(cutoff[0], 0));
        step();
        var untouched = rings.capture().attractedRings()[0];
        rings.update(0, player, 101, false);
        assertEquals(untouched, rings.capture().attractedRings()[0]);
        assertEquals(List.of(), admissions(objects));
        assertTrue(RingDrawInternalAccess.slottedAttracted(rings, 2).isEmpty());
        objects.removeDynamicObject(cutoff[0]);
        rings.restore(with(rings.capture(), new RingSnapshot.AttractedRingEntry[0]));
        step(); // beginPass must stamp even when there was no live ring at any slot
        rings.attractStageRings(player);
        var createdAfter = rings.capture().attractedRings()[0];
        rings.update(0, player, 101, false);
        assertEquals(createdAfter, rings.capture().attractedRings()[0]);
        assertEquals(List.of(), admissions(objects));
    }

    @Test void rejectedOverflowStillHasNoLegacyEmission() {
        restoreRings(attracted(0, 5, 144, false, 0, 0, 0));
        add(4, () -> {
            for (int bucket = 2; bucket <= 7; bucket++)
                for (int call = 0; call < 63; call++) assertTrue(submit(objects, 4, bucket));
        });
        step();
        assertEquals(378, admissions(objects).size());
        assertTrue(admissions(objects).stream().noneMatch(entry -> entry.slot() == 5));
        assertNotNull(rings.resolveNativeSlot(5));
        assertTrue(RingDrawInternalAccess.slottedAttracted(rings, 2).isEmpty(),
                "a live ring rejected by every Draw_Sprite bucket cannot bypass admission");
        assertTrue(draw().tiles().isEmpty());
    }

    @Test void restoredEmptyNativeAdmissionCannotTurnItsUnvisitedTailIntoLegacyArt() {
        restoreRings(attracted(0, 9, 144, false, 0, 0, 0));
        final Probe[] cutoff = new Probe[1];
        cutoff[0] = add(4, () -> objects.overrideRemainingObjectLoopSlots(cutoff[0], 0));
        step();
        objects.removeDynamicObject(cutoff[0]);
        assertTrue(draw().tiles().isEmpty());
        RewindRegistry registry = new RewindRegistry();
        registry.register(objects.rewindSnapshottable());
        registry.register(rings);
        registerRewind(objects, registry);
        var captured = registry.capture();
        objects.reset(0);
        rings.reset(0);
        registry.restore(captured);
        assertEquals(List.of(), admissions(objects));
        assertEquals(1, rings.capture().attractedRings().length);
        assertTrue(draw().tiles().isEmpty(),
                "a captured empty native pass must retain admission ownership after transient phase reset");
    }

    @Test void rejectedConversionAndItsFutureBouncerFramesNeverReceiveInferredLegacyDraws() {
        restoreRings(attracted(0, 5, 144, false, 0, 0, 0));
        add(4, () -> {
            for (int bucket = 2; bucket <= 7; bucket++)
                for (int call = 0; call < 63; call++) assertTrue(submit(objects, 4, bucket));
        });
        player.giveShield(ShieldType.FIRE);
        step();
        var converted = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertTrue(converted.nativeSpriteSubmissionsAuthoritative());
        assertTrue(admissions(objects).stream().noneMatch(entry -> entry.slot() == 5));
        assertTrue(draw().tiles().isEmpty(), "rejected conversion cannot fall through to inferred draw");
        nextClock(); step();
        assertEquals(378, admissions(objects).size());
        assertTrue(admissions(objects).stream().noneMatch(entry -> entry.slot() == 5));
        assertTrue(draw().tiles().isEmpty(), "future bouncer Draw_Sprite rejection also disables fallback");
    }

    @Test void convertedDeferredOwnerAndSparkleDeleteBranchesMakeNoNativeCall() {
        int slot = objects.allocateDynamicSlot();
        var converted = LostRingObjectInstance.fromAttractedRing(144, 64, 0, 0, 0, 0,
                0, rings.getSpillAnimationState(), new LostRingRenderState(2, 1, true));
        converted.deferFirstUpdateUntilOwnerPass();
        objects.spawnLostRingObjectAtSlot(converted, slot);
        step();
        assertEquals(List.of(), admissions(objects));
        assertTrue(draw().tiles().isEmpty(), "the behind-cursor owner has no inferred rescue draw");
        nextClock(); step();
        assertEquals(List.of(new Admission(slot, 2)), admissions(objects));
        converted.markCollected(objects.getFrameCounter());
        // Three art frames plus the native $FC invocation still submit; the following dispatch deletes.
        for (int sparkle = 0; sparkle < 4; sparkle++) {
            nextClock(); step();
            assertEquals(List.of(new Admission(slot, 1)), admissions(objects));
        }
        nextClock(); step();
        assertTrue(converted.isDestroyed());
        assertEquals(List.of(), admissions(objects), "the successful delete branch never calls Draw_Sprite");
        assertTrue(draw().tiles().isEmpty());
    }

    @Test void shieldLossResetsSpillBeforeOutOfRangeDeletionAndRestoreForwardRepeatsThatWrite() {
        restoreRings(attracted(0, 6, 0x280, false, 3, 0, 0));
        NativePositionOps.writeXPosResetSubpixel(player, 0x500);
        player.giveShield(ShieldType.FIRE);
        rings.getSpillAnimationState().restore(new int[]{7, 0x1234, 2});
        RewindRegistry registry = new RewindRegistry();
        registry.register(objects.rewindSnapshottable());
        registry.register(rings);
        registerRewind(objects, registry);
        var before = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            step();
            assertArrayEquals(new int[]{255, 0x1234, 2}, rings.getSpillAnimationState().snapshot(),
                    "loc_1A88C resets only the counter before loc_1A8C6 deletes");
            assertEquals(0, rings.capture().attractedRings().length);
            assertTrue(objects.activeObjectsOfType(LostRingObjectInstance.class).isEmpty());
            assertEquals(0, objects.getAllocatedSlotCount());
            assertEquals(List.of(), admissions(objects));
            assertTrue(draw().tiles().isEmpty());
            if (replay == 0) {
                registry.restore(before);
                assertArrayEquals(new int[]{7, 0x1234, 2}, rings.getSpillAnimationState().snapshot());
            }
        }
    }

    @Test void coarseRangeIncludes280AndSpinRunsOnItsLastAdmittedNativeInvocation() {
        NativePositionOps.writeXPosResetSubpixel(player, 0x500);
        for (boolean shieldLost : List.of(false, true)) {
            for (int x : new int[]{0x200, 0x280}) {
                objects.reset(0);
                rings.reset(0);
                restoreRings(attracted(0, 6, x, false, 3, 0, 0));
                rings.getSpillAnimationState().restore(new int[]{7, 0x1234, 2});
                player.giveShield(shieldLost ? ShieldType.FIRE : ShieldType.LIGHTNING);
                step();
                assertEquals(shieldLost ? 255 : 7, rings.getSpillAnimationState().counter());
                if (x == 0x200) { // coarseBack=$FF80: ($0200-$FF80)&$FFFF ==$280
                    assertEquals(List.of(new Admission(6, 2)), admissions(objects));
                    if (shieldLost) {
                        var converted = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
                        var art = SpritePresentationRenderer.prepare(graphics, 0, 0,
                                () -> converted.appendRenderCommands(new ArrayList<>()));
                        assertEquals(BASE, art.tiles().getFirst().patternId(),
                                "conversion retains spin3->0 from this same native invocation");
                        assertEquals(2, converted.getPriorityBucket());
                    } else {
                        assertEquals(0, rings.capture().attractedRings()[0].mappingFrame());
                    }
                } else {
                    assertEquals(List.of(), admissions(objects));
                    assertEquals(0, rings.capture().attractedRings().length);
                    assertTrue(objects.activeObjectsOfType(LostRingObjectInstance.class).isEmpty());
                }
            }
        }
    }

    @Test void nativeRangeCheckUsesThePositionAfterItsUnchangedMotionStep() {
        NativePositionOps.writeXPosResetSubpixel(player, 0x500);
        player.giveShield(ShieldType.FIRE);
        for (int x : new int[]{0x1FF, 0x27F}) {
            objects.reset(0);
            rings.reset(0);
            restoreRings(new RingSnapshot.AttractedRingEntry(true, 0, x, 64,
                    0, 0, 0xD0, 0, 0, 6, false, -1, 3, 0, 0));
            step(); // +$30 acceleration makes x_vel=$100, advancing exactly one pixel
            if (x == 0x1FF) {
                var converted = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
                assertEquals(0x200, converted.getX());
                assertEquals(0x100, converted.getXVelForTest());
                assertEquals(List.of(new Admission(6, 2)), admissions(objects));
            } else {
                assertTrue(objects.activeObjectsOfType(LostRingObjectInstance.class).isEmpty());
                assertEquals(0, rings.capture().attractedRings().length);
                assertEquals(List.of(), admissions(objects));
            }
        }
    }

    @Test void nonLightningCapabilityDoesNotSelectNativeSpillResetFromTheCurrentDrawProfile() {
        // Keep the current S3K draw provider while the ring field's player lacks
        // the semantic lightning capability; presentation never selects gameplay.
        player = new Sonic("sonic", (short) 0, (short) 0) {
            @Override public GameRules getGameRules() { return GameRules.SONIC_2; }
        };
        NativePositionOps.writeXPosResetSubpixel(player, 0x500);
        NativePositionOps.writeYPosResetSubpixel(player, 64);
        player.giveShield(ShieldType.FIRE);
        restoreRings(attracted(0, 6, 0x280, false, 3, 0, 0));
        rings.getSpillAnimationState().restore(new int[]{7, 0x1234, 2});
        step();
        assertArrayEquals(new int[]{7, 0x1234, 2}, rings.getSpillAnimationState().snapshot());
        assertEquals(0, rings.capture().attractedRings().length);
        assertTrue(objects.activeObjectsOfType(LostRingObjectInstance.class).isEmpty());
        assertEquals(List.of(), admissions(objects));
    }

    @Test void sparkleFcStillDrawsAndItsSlotIsReusableOnlyAfterNextDeleteDispatch() {
        restoreRings(attracted(0, 5, 144, true, 6, 0, 3));
        for (int slot = 4; slot <= 92; slot++) if (slot != 5) add(slot, () -> { });
        add(94, () -> objects.queueDynamicObjectAfterExec(new Probe(() -> { })));
        step();
        assertEquals(4, rings.capture().attractedRings()[0].sparkleAnimationFrame());
        assertEquals(List.of(new Admission(5, 1)), admissions(objects));
        assertEquals(List.of(BASE + 6), draw().tiles().stream().map(SpritePresentation.Tile::patternId).toList());
        assertEquals(-1, objects.firstFreeDynamicSlot(), "post-exec fixed-tail creation cannot steal the $FC slot");
        nextClock(); step();
        assertEquals(0, rings.capture().attractedRings().length);
        assertTrue(objects.objectIdInSlot(5) >= 0, "next-pass Delete frees slot5 before fixed-tail allocation");
        assertEquals(List.of(), admissions(objects));
    }

    @Test void conversionRetainsBucketMappingAndArtUntilTheFirstActualBouncerUpdate() {
        restoreRings(attracted(0, 6, 144, false, 0, 0, 0));
        rings.getSpillAnimationState().restore(new int[]{255, 0, 2});
        player.giveShield(ShieldType.FIRE);
        step();
        LostRingObjectInstance converted = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertEquals(-0xC0, converted.getXVelForTest());
        assertEquals((144 << 8) - 0xC0, converted.getXSubpixelForTest(), "conversion must not execute bounce physics twice");
        assertEquals(2, converted.getPriorityBucket());
        assertTrue(converted.isHighPriority());
        assertEquals(List.of(new Admission(6, 2)), admissions(objects));
        for (int pausedDraw = 0; pausedDraw < 3; pausedDraw++) {
            assertEquals(List.of(BASE + 1), draw().tiles().stream().map(SpritePresentation.Tile::patternId).toList());
            assertEquals(2, converted.getPriorityBucket());
        }
        nextClock(); step();
        assertEquals((144 << 8) - 0x180, converted.getXSubpixelForTest());
        assertEquals(List.of(new Admission(6, 2)), admissions(objects), "every actual converted routine2 tail submits once");
        assertEquals(2, converted.getPriorityBucket(), "routine2 never reruns priority$180 Init");
        // Slot6 has phase103: the native routine copies shared frame2 directly,
        // rather than adding this separate floor-probe cadence phase.
        var direct = SpritePresentationRenderer.prepare(graphics, 0, 0,
                () -> converted.appendRenderCommands(new ArrayList<>()));
        assertEquals(List.of(BASE + 2), direct.tiles().stream().map(SpritePresentation.Tile::patternId).toList());
        converted.markCollected(objects.getFrameCounter());
        assertEquals(2, converted.getPriorityBucket(), "touch alone does not execute GiveRing");
        nextClock(); step();
        assertEquals(1, converted.getPriorityBucket());
    }

    @Test void queuedRingUsesRealReplacementAndResetRebindsFreshStateWithoutReleasingItsSlot() {
        restoreRings(attracted(0, 5, 144, false, 0, 0, 0));
        step();
        objects.releaseDynamicSlot(5);
        Probe replacement = add(5, () -> { });
        assertTrue(draw().tiles().isEmpty(), "real replacement wins over the external ring resolver");
        nextClock(); step();
        assertEquals(0, rings.capture().attractedRings().length);
        assertEquals(replacement, objects.getActiveObjects().stream()
                .filter(object -> object instanceof Probe probe && probe.getSlotIndex() == 5).findFirst().orElseThrow());
        assertFalse(5 == objects.allocateDynamicSlot(), "retiring the stale external DTO must preserve replacement ownership");
        objects.reset(0);
        rings.reset(0); // actual reset clears OM bindings, so the ring owner must bind again
        restoreRings(attracted(0, 6, 160, false, 0, 0, 0));
        step();
        assertEquals(List.of(new Admission(6, 2)), admissions(objects));
        assertNotNull(rings.resolveNativeSlot(6));
        assertNull(rings.resolveNativeSlot(5));
    }

    @Test void captureRestoreForwardRepeatsLiveDtoMovementQueueAndRecreatedConversionRenderState() {
        restoreRings(attracted(0, 5, 144, false, 0, 0, 0));
        RewindRegistry registry = new RewindRegistry();
        registry.register(objects.rewindSnapshottable());
        registry.register(rings);
        registerRewind(objects, registry);
        var before = registry.capture();
        step();
        var moved = rings.capture().attractedRings();
        var calls = admissions(objects);
        var picture = draw();
        nextClock(); step();
        registry.restore(before);
        levelClock.set(100);
        step();
        assertArrayEquals(moved, rings.capture().attractedRings());
        assertEquals(calls, admissions(objects));
        assertEquals(picture, draw());
        player.giveShield(ShieldType.FIRE);
        nextClock(); step();
        var conversion = registry.capture();
        var convertedPicture = draw();
        var first = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        nextClock(); step();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(conversion);
        levelClock.set(101);
        var restored = objects.activeObjectsOfType(LostRingObjectInstance.class).getFirst();
        assertNotSame(first, restored);
        assertEquals(2, restored.getPriorityBucket());
        assertTrue(restored.isHighPriority());
        assertEquals(convertedPicture, draw(), "generic primitive capture must retain the pending conversion mapping");
        nextClock(); step();
        assertEquals(2, restored.getPriorityBucket());
        assertEquals(0, rings.capture().attractedRings().length);
    }

    private void nextClock() { levelClock.incrementAndGet(); }
    private void step() { objects.update(0, player, List.of(), levelClock.get(), false); }
    private Probe add(int slot, Runnable action) {
        Probe object = new Probe(action);
        objects.addDynamicObjectAtSlot(object, slot);
        return object;
    }
    private void restoreRings(RingSnapshot.AttractedRingEntry... entries) { rings.restore(with(rings.capture(), entries)); }
    private SpritePresentation.Frame draw() {
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            SpriteSatInternalAccess.beginCollection(graphics, SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
            for (int bucket = 0; bucket < 8; bucket++) {
                graphics.setCurrentSpriteSatBucket(bucket);
                ObjectDrawInternalAccess.drawBucket(objects, bucket, graphics,
                        RingDrawInternalAccess.slottedAttracted(rings, bucket));
            }
            graphics.endSpriteSatCollectionAndReplay();
        });
    }
    private static RingSnapshot.AttractedRingEntry attracted(int pool, int slot, int x, boolean collected,
                                                              int mapping, int timer, int animationFrame) {
        return new RingSnapshot.AttractedRingEntry(true, pool, x, 64, 0, 0, 0, 0,
                pool, slot, collected, -1, mapping, timer, animationFrame);
    }
    private static RingSnapshot with(RingSnapshot base, RingSnapshot.AttractedRingEntry[] entries) {
        return new RingSnapshot(base.collectedWords(), base.sparkleTimers(), base.placementCursorIndex(),
                base.placementLastCameraX(), base.activeSpawnIndices(), base.lostRingActiveCount(),
                base.spillAnimCounter(), base.spillAnimAccum(), base.spillAnimFrame(), base.lostRingFrameCounter(),
                base.lostRings(), entries);
    }
    static RingSpriteSheet sheet() {
        Pattern[] patterns = new Pattern[7];
        List<RingFrame> frames = new ArrayList<>();
        for (int frame = 0; frame < 7; frame++) {
            patterns[frame] = new Pattern();
            patterns[frame].setPixel(0, 0, (byte) 1);
            frames.add(new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, frame, false, false, 0))));
        }
        return new RingSpriteSheet(patterns, frames, 1, 8, 1, 4, 3);
    }
    private static final class EmptyRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "Probe"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
    }
    private static final class Probe extends AbstractObjectInstance implements NativeSpriteSubmissionSource {
        private final Runnable action;
        Probe(Runnable action) { super(new ObjectSpawn(128, 64, 0x70, 0, 0, false, 0), "Probe"); this.action = action; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { action.run(); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
