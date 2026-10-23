package com.openggf.game.sonic1.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent producer ledger for Bom_Action and the in-place native ID3F dispatch. */
class TestSonic1BombBodyNativeSubmission {
    @BeforeEach void boot() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() { GraphicsManager.getInstance().resetState(); }

    @Test
    void constructorDoesNotExecuteMainAndFirstNullPlayerDispatchFallsThroughOnce() throws Exception {
        Harness h = harness(40, false);
        assertEquals(List.of(), calls(h));
        assertEquals(0, h.body.getPriorityBucket());
        assertEquals(0, h.body.getCollisionFlags());
        assertEquals(0, integer(h.body, "mappingFrame"));

        h.step(1, null);
        assertEquals(List.of(call(40, 3)), calls(h));
        assertEquals(0x5F, h.body.getLiveObjectId());
        assertEquals(0x9A, h.body.getCollisionFlags());
        assertEquals(1535, integer(h.body, "timer"));
        assertEquals(5, integer(h.body, "mappingFrame"));
        assertEquals(19, integer(h.body, "animationDuration"));
        assertEquals(160, h.body.getX(), "Bom_Main/action start walking without SpeedToPos");
        for (int pass = 2; pass <= 20; pass++) {
            h.step(pass, null);
            assertEquals(List.of(call(40, 3)), calls(h), "held animation frames still call DisplaySprite");
            assertEquals(5, integer(h.body, "mappingFrame"));
        }
        h.step(21, null);
        assertEquals(4, integer(h.body, "mappingFrame"));
        assertEquals(158, h.body.getX(), "bchg tests the OLD bit before negating walking speed");
        assertEquals(0xC000, motion(h.body).xSub, "native SpeedToPos uses 16-bit fractions");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void everyBodyActionSubmitsOnlyOnRememberStatesInRangeBranch(int action) throws Exception {
        Harness h = harness(40, false);
        set(h.body, "state", action);
        set(h.body, "timer", 10);
        set(h.body, "currentAnim", action == 2 ? 2 : 0);
        h.step(1, null);
        assertEquals(List.of(call(40, 3)), calls(h));
        assertEquals(9, integer(h.body, "timer"));

        h.camera.x = 0x600;
        h.step(2, null);
        assertTrue(h.body.isDestroyed());
        assertTrue(h.body.isDestroyedRespawnable());
        assertEquals(8, integer(h.body, "timer"), "the routine runs before RememberState deletes");
        assertEquals(List.of(), calls(h));
    }

    @Test
    void handoffRetainsBombPoseThenInitMakesNoCallAndThirtyNineAnimatePassesDisplay() throws Exception {
        Harness h = harness(40, false);
        h.step(1, null);
        expireNext(h.body);
        motion(h.body).xSub = 0x1234;
        motion(h.body).ySub = 0x5678;
        set(h.body, "previousAnim", 2);
        set(h.body, "animationDuration", 0);
        set(h.body, "animationIndex", 1);
        set(h.body, "mappingFrame", 7);

        h.step(2, null);
        assertSame(h.body, body(h.manager));
        assertEquals(0x3F, h.body.getLiveObjectId());
        assertEquals(0x5F, h.body.getSpawn().objectId(), "placement/factory identity remains the Bomb");
        assertEquals(List.of(call(40, 3)), calls(h));
        assertEquals(6, integer(h.body, "mappingFrame"), "old Bomb animation still advances on ID write");
        assertEquals(0x9A, h.body.getCollisionFlags());
        assertEquals(3, h.body.getPriorityBucket());
        assertEquals(List.of(), h.sounds);
        assertEquals(40, h.body.getSlotIndex());

        h.step(3, null);
        assertEquals(List.of(), calls(h), "Expl_Main tail-jumps to QueueSound2 without DisplaySprite");
        assertEquals(List.of(Sonic1Sfx.BOSS_EXPLOSION.id), h.sounds);
        assertEquals(0, h.body.getCollisionFlags());
        assertEquals(1, h.body.getPriorityBucket());
        assertEquals(0, integer(h.body, "mappingFrame"));
        assertEquals(7, integer(h.body, "animationDuration"));
        assertEquals(0x1234, motion(h.body).xSub);
        assertEquals(0x5678, motion(h.body).ySub);
        assertEquals(2, integer(h.body, "currentAnim"));
        assertEquals(2, integer(h.body, "animationIndex"), "Expl_Main leaves old animation index intact");
        for (int animate = 1; animate <= 39; animate++) {
            h.step(3 + animate, null);
            assertEquals(List.of(call(40, 1)), calls(h), "animate pass " + animate);
            assertEquals(animate / 8, integer(h.body, "mappingFrame"));
            assertFalse(h.body.isDestroyed());
        }
        h.step(43, null);
        assertTrue(h.body.isDestroyed());
        assertEquals(0, h.body.getLiveObjectId());
        assertEquals(List.of(), calls(h), "frame5 deletes before DisplaySprite");
        assertEquals(List.of(Sonic1Sfx.BOSS_EXPLOSION.id), h.sounds);
        assertEquals(32, h.manager.allocateDynamicSlot(), "no animal/points allocation consumes a lower hole");
        assertTrue(h.manager.reserveDynamicSlot(40), "the original SST is released only at DeleteObject");
    }

    @Test
    void actualManagerRenderingUsesRetainedBombMapThenZeroCallInitAndFieryMap() throws Exception {
        ObjectRenderManager render = mock(ObjectRenderManager.class);
        PatternSpriteRenderer bombArt = mock(PatternSpriteRenderer.class);
        PatternSpriteRenderer fieryArt = mock(PatternSpriteRenderer.class);
        when(render.getRenderer(ObjectArtKeys.BOMB)).thenReturn(bombArt);
        when(render.getBossExplosionRenderer()).thenReturn(fieryArt);
        when(bombArt.isReady()).thenReturn(true);
        when(fieryArt.isReady()).thenReturn(true);
        Harness h = harness(40, false, render);
        expireNext(h.body);
        h.step(1, null);
        h.manager.drawUnifiedBucket(3, null);
        verify(bombArt).drawFrameIndex(7, 160, 96, true, false);
        verifyNoInteractions(fieryArt);
        clearInvocations(bombArt);

        h.step(2, null);
        h.manager.drawUnifiedBucket(1, null);
        h.manager.drawUnifiedBucket(3, null);
        verifyNoInteractions(bombArt, fieryArt);
        h.step(3, null);
        h.manager.drawUnifiedBucket(1, null);
        verify(fieryArt).drawFrameIndex(0, 160, 96, false, false);
        verifyNoInteractions(bombArt);
    }

    @Test
    void conversionOutsideRangeDeletesWithoutCallOrSoundInsteadOfInitializingExplosion() throws Exception {
        Harness h = harness(40, false);
        h.step(1, null);
        expireNext(h.body);
        h.camera.x = 0x600;
        h.step(2, null);
        assertTrue(h.body.isDestroyedRespawnable());
        assertEquals(0, h.body.getLiveObjectId());
        assertEquals(List.of(), calls(h));
        assertEquals(List.of(), h.sounds);
    }

    @ParameterizedTest
    @ValueSource(ints = {62, 63})
    void lowerAndHigherProducersObserveCallTimeCapacityAcrossTheBucketChange(int prefixCount) throws Exception {
        Harness h = harness(40, false);
        h.manager.createDynamicObjectAtSlot(() -> new Producer(prefixCount, 3), 32);
        Producer tail = h.manager.createDynamicObjectAtSlot(() -> new Producer(1, 3), 100);
        expireNext(h.body);
        h.step(1, null);
        assertEquals(63, calls(h).size());
        assertEquals(prefixCount == 62 ? List.of(call(40, 3)) : List.of(), calls(h).stream()
                .filter(c -> c.slot() == 40).toList());
        assertEquals(0, tail.accepted);
        assertEquals(0x3F, h.body.getLiveObjectId(), "display rejection cannot prevent the ID write");
        h.step(2, null);
        assertEquals(prefixCount == 62 ? 1 : 0, tail.accepted, "zero-call init consumes no bucket3 word");
        assertTrue(calls(h).stream().noneMatch(c -> c.slot() == 40));
        h.step(3, null);
        assertTrue(calls(h).contains(call(40, 1)), "a full bucket3 cannot reject bucket1 animation");
    }

    @Test
    void laterDeletionLeavesTheSavedPointerConsumingCapacityWithoutRenderingTheRetiredOwner() {
        Harness h = harness(40, false);
        h.manager.createDynamicObjectAtSlot(() -> new RetireEarlier(h.body), 60);
        Producer tail = h.manager.createDynamicObjectAtSlot(() -> new Producer(63, 3), 100);
        h.step(1, null);
        assertTrue(h.body.isDestroyed());
        assertEquals(call(40, 3), calls(h).getFirst());
        assertEquals(62, tail.accepted, "clearing the earlier SST cannot reclaim its queued word");
        List<ObjectInstance> rendered = new ArrayList<>();
        h.manager.drawUnifiedBucket(3, (object, high) -> rendered.add(object));
        assertFalse(rendered.contains(h.body), "late pointer resolution sees a cleared SST, not a disposed owner");
    }

    @Test
    void realFuseOutlivesIdWriteAndDestructiveRegisteredRestoreReplaysItsOwnSlotAllocation() throws Exception {
        Harness h = harness(32, false);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short)160);
        player.setCentreY((short)96);
        h.step(1, player);
        assertEquals(142, integer(h.body, "timer"));
        assertEquals(143, integer(fuse(h.manager), "timer"));
        for (int pass = 2; pass <= 144; pass++) h.step(pass, null);
        assertEquals(0x3F, h.body.getLiveObjectId());
        Sonic1BombFuseInstance oldFuse = fuse(h.manager);
        assertEquals(0, integer(oldFuse, "timer"));
        assertSame(h.body, field(oldFuse, "parent").get(oldFuse));
        List<NativeSpriteSubmissionAccess.Admission> handoff = calls(h);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = registry(h.manager);
        var captured = registry.capture();

        h.step(145, null);
        List<NativeSpriteSubmissionAccess.Admission> forward = calls(h);
        assertEquals(List.of(call(33, 3), call(33, 3)), forward);
        assertEquals(List.of(33, 34, 35, 36), fragmentSlots(h.manager));
        assertEquals(List.of(Sonic1Sfx.BOSS_EXPLOSION.id), h.sounds);
        h.manager.reset(0);
        registry.restore(captured);
        Sonic1BombBadnikInstance restored = body(h.manager);
        assertNotSame(h.body, restored);
        assertNotSame(oldFuse, fuse(h.manager));
        assertSame(restored, field(fuse(h.manager), "parent").get(fuse(h.manager)),
                "the live fuse must reference the recreated ID3F owner in the manager");
        assertEquals(handoff, calls(h));
        assertEquals(0x3F, restored.getLiveObjectId());
        h.sounds.clear();
        h.step(145, null);
        assertEquals(forward, calls(h));
        assertEquals(List.of(33, 34, 35, 36), fragmentSlots(h.manager));
        assertEquals(List.of(Sonic1Sfx.BOSS_EXPLOSION.id), h.sounds);
    }

    @Test
    void placementIdentitySurvivesIdWriteCameraUnloadRestoreAndFinalTeardown() throws Exception {
        Harness h = harness(32, true);
        h.step(1, null);
        Sonic1BombBadnikInstance bomb = body(h.manager);
        ObjectSpawn original = bomb.getSpawn();
        expireNext(bomb);
        h.step(2, null);
        assertEquals(0x3F, bomb.getLiveObjectId());
        assertEquals(original.objectId(), bomb.getSpawn().objectId());
        h.camera.x = 0x600;
        h.step(3, null);
        assertTrue(h.manager.getActiveObjects().contains(bomb), "ID3F has no RememberState unload");
        RewindRegistry registry = registry(h.manager);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        var snapshot = registry.capture();
        h.manager.reset(0);
        registry.restore(snapshot);
        h.sounds.clear();
        Sonic1BombBadnikInstance restored = body(h.manager);
        assertEquals(0x3F, restored.getLiveObjectId());
        assertEquals(0x5F, restored.getSpawn().objectId());
        for (int pass = 4; pass <= 43; pass++) h.step(pass, null);
        assertTrue(restored.isDestroyed());
        assertEquals(List.of(), h.sounds, "restoring initialized ID3F cannot reissue C4");
        assertFalse(h.manager.getActiveObjects().contains(restored));
        h.camera.x = 0;
        h.step(44, null);
        assertTrue(h.manager.activeObjectsOfType(Sonic1BombBadnikInstance.class).isEmpty(),
                "ID3F deletion retains the original remembered Bomb placement, so camera return cannot respawn it");
        h.manager.reset(0);
        assertTrue(NativeSpriteSubmissionAccess.admissions(h.manager).isEmpty());
    }

    private static RewindRegistry registry(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        NativeSpriteSubmissionAccess.registerRewind(manager, registry);
        return registry;
    }
    private static void expireNext(Sonic1BombBadnikInstance body) throws Exception {
        set(body, "state", 2); set(body, "timer", 0); set(body, "currentAnim", 2);
    }
    private static Sonic1BombBadnikInstance body(ObjectManager manager) {
        return manager.activeObjectsOfType(Sonic1BombBadnikInstance.class).getFirst();
    }
    private static Sonic1BombFuseInstance fuse(ObjectManager manager) {
        return manager.activeObjectsOfType(Sonic1BombFuseInstance.class).getFirst();
    }
    private static List<Integer> fragmentSlots(ObjectManager manager) {
        return manager.activeObjectsOfType(Sonic1BombShrapnelInstance.class).stream()
                .map(AbstractObjectInstance::getSlotIndex).sorted().toList();
    }
    private static NativeSpriteSubmissionAccess.Admission call(int slot, int bucket) {
        return new NativeSpriteSubmissionAccess.Admission(slot, bucket);
    }
    private static List<NativeSpriteSubmissionAccess.Admission> calls(Harness h) {
        return NativeSpriteSubmissionAccess.admissions(h.manager);
    }
    private static Field field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static int integer(Object target, String name) throws Exception { return field(target, name).getInt(target); }
    private static void set(Object target, String name, int value) throws Exception { field(target, name).setInt(target, value); }
    private static SubpixelMotion.State motion(Object target) throws Exception {
        return (SubpixelMotion.State)field(target, "motionState").get(target);
    }
    private static Harness harness(int bodySlot, boolean placement) {
        return harness(bodySlot, placement, null);
    }
    private static Harness harness(int bodySlot, boolean placement, ObjectRenderManager render) {
        ObjectManager[] holder = new ObjectManager[1];
        MutableCamera camera = new MutableCamera();
        List<Integer> sounds = new ArrayList<>();
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public ObjectRenderManager renderManager() { return render; }
            @Override public void playSfx(int id) { sounds.add(id); }
        };
        ObjectSpawn spawn = new ObjectSpawn(160, 96, 0x5F, 0, 0, placement, 96, placement ? 0 : -1);
        ObjectManager manager = new ObjectManager(placement ? List.of(spawn) : List.of(), new Sonic1ObjectRegistry(),
                0, null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.enableCounterBasedRespawn(); manager.reset(0);
        Sonic1BombBadnikInstance body = placement ? null : manager.createDynamicObjectAtSlot(
                () -> new Sonic1BombBadnikInstance(spawn), bodySlot);
        return new Harness(manager, body, camera, sounds);
    }
    private record Harness(ObjectManager manager, Sonic1BombBadnikInstance body, MutableCamera camera,
                           List<Integer> sounds) {
        void step(int tick, PlayableEntity player) { manager.update(camera.x, player, List.of(), tick); }
    }
    private static final class MutableCamera extends Camera {
        int x;
        @Override public short getX() { return (short)x; }
        @Override public short getY() { return 0; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }
    private static final class Producer extends AbstractObjectInstance implements NativeSpriteSubmissionSource {
        private final int requested, bucket;
        int accepted;
        Producer(int requested, int bucket) {
            super(new ObjectSpawn(160, 96, 0x7F, 0, 0, false, 0), "QueueProbe");
            this.requested = requested; this.bucket = bucket;
        }
        @Override public void update(int tick, PlayableEntity player) {
            accepted = 0;
            for (int i = 0; i < requested; i++)
                if (NativeSpriteSubmissionAccess.submit(services().objectManager(), this, bucket)) accepted++;
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    private static final class RetireEarlier extends AbstractObjectInstance implements NativeSpriteSubmissionSource {
        private final Sonic1BombBadnikInstance earlier;
        RetireEarlier(Sonic1BombBadnikInstance earlier) {
            super(new ObjectSpawn(160, 96, 0x7F, 0, 0, false, 0), "RetireEarlier"); this.earlier = earlier;
        }
        @Override public void update(int tick, PlayableEntity player) { earlier.setDestroyed(true); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
