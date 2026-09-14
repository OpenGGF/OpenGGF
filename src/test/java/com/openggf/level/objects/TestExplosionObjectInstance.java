package com.openggf.level.objects;

import com.openggf.game.GameId;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.Sonic1GameModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestExplosionObjectInstance {

    /**
     * ROM Obj27 (S2/S3K): {@code anim_frame_duration} init 3, reload 7, delete
     * at mapping_frame 5 (docs/s2disasm/s2.asm:46672-46684,
     * docs/skdisasm/sonic3k.asm:42195-42205). The first {@code update} is the
     * same-frame Init-&gt;Main fall-through (the ROM-spawn frame), so counting
     * that first update as game-frame 1, the explosion self-deletes on the 36th
     * update — i.e. 35 game frames after spawn, matching the EHZ1/SCZ/WFZ trace
     * timelines (e.g. appeared f153, removed f188 = 35). Default (no game module
     * wired) uses the S2/S3K value (3).
     */
    @Test
    public void explosionSelfDeletesAtRomExactFrameForS2() {
        ExplosionObjectInstance explosion =
                new ExplosionObjectInstance(0x27, 100, 200, null);
        explosion.setServices(new TestObjectServices()); // gameModule() == null -> default 3
        for (int update = 1; update <= 35; update++) {
            explosion.update(update, (PlayableEntity) null);
            assertFalse(explosion.isDestroyed(),
                    "explosion must survive through update " + update + " (35 frames live)");
        }
        explosion.update(36, (PlayableEntity) null);
        assertTrue(explosion.isDestroyed(),
                "explosion must self-delete 35 game frames after spawn (ROM-exact)");
    }

    /**
     * ROM Obj27 (S1): {@code obTimeFrame} init 7 (frame 0 held 8 game frames)
     * vs S2/S3K's 3, so S1 lives 39 game frames — modelled via
     * {@link Sonic1GameModule#explosionInitialAnimDuration()} =&gt; 7
     * (docs/s1disasm/_incObj/24, 27 &amp; 3F Explosions.asm ExItem_Main). With
     * the first update as game-frame 1, S1 self-deletes on the 40th update.
     */
    @Test
    public void explosionSelfDeletesAtRomExactFrameForS1() {
        Sonic1GameModule s1 = new Sonic1GameModule();
        assertEquals(7, s1.explosionInitialAnimDuration());
        assertEquals(GameId.S1, s1.getGameId());

        ExplosionObjectInstance explosion =
                new ExplosionObjectInstance(0x27, 100, 200, null);
        explosion.setServices(new TestObjectServices().withGameModule(s1));
        for (int update = 1; update <= 39; update++) {
            explosion.update(update, (PlayableEntity) null);
            assertFalse(explosion.isDestroyed(),
                    "S1 explosion must survive through update " + update + " (39 frames live)");
        }
        explosion.update(40, (PlayableEntity) null);
        assertTrue(explosion.isDestroyed(), "S1 explosion must self-delete 39 game frames after spawn");
    }

    /**
     * ROM {@code Obj27_Init} plays the explosion sound from the explosion's own
     * execution — {@code move.w #SndID_Explosion,d0 / jsr (PlaySound).l}
     * (docs/s2disasm/s2.asm:46717-46734) — so neither construction nor service
     * injection may make the request. The object that spawned the explosion has
     * already finished its own pass by then.
     */
    @Test
    public void explosionSoundPlaysOnTheExplosionsOwnFirstUpdate() {
        RecordingObjectServices services = new RecordingObjectServices();

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 77);
        assertEquals(0, services.playedSfxCount);

        explosion.setServices(services);
        assertEquals(0, services.playedSfxCount,
                "injecting services must not make the request; the explosion's own pass does");

        explosion.update(1, (PlayableEntity) null);
        assertEquals(1, services.playedSfxCount);
        assertEquals(77, services.lastSfxId);

        explosion.update(2, (PlayableEntity) null);
        assertEquals(1, services.playedSfxCount, "the request is made once, not every pass");
    }

    /**
     * An explosion allocated into a slot the object scan has already passed
     * runs its init on the next pass, so its sound follows one frame later.
     * {@code Obj26_Break} allocates with lowest-free {@code AllocateObject}
     * from the monitor's own slot (docs/s2disasm/s2.asm:25702-25707).
     */
    @Test
    public void passedSlotExplosionDefersItsSoundByOnePass() {
        RecordingObjectServices services = new RecordingObjectServices();

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 88);
        explosion.setServices(services);
        explosion.delayFirstUpdateForPassedSlot();

        explosion.update(1, (PlayableEntity) null);
        assertEquals(0, services.playedSfxCount,
                "the passed-slot pass is consumed by the deferral");

        explosion.update(2, (PlayableEntity) null);
        assertEquals(1, services.playedSfxCount);
        assertEquals(88, services.lastSfxId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recreatedPendingExplosionKeepsExactCustomFactoriesAndAllocationOrder(boolean pointsFirst) {
        List<String> calls = new ArrayList<>();
        List<ObjectServices> receivedServices = new ArrayList<>();
        DestructionEffects.AnimalFactory animals = (spawn, services) -> {
            calls.add("custom-animal:" + spawn.rawYWord());
            receivedServices.add(services);
            return new ChildMarker(spawn, "CustomAnimal");
        };
        DestructionEffects.PointsFactory points = (spawn, services, value) -> {
            calls.add("custom-points:" + value);
            receivedServices.add(services);
            return new ChildMarker(spawn, "CustomPoints");
        };
        var originalServices = new TestObjectServices().withIsolatedObjectManager();
        var original = new ExplosionObjectInstance(0x27, 100, 200, null,
                animals, points, 800, pointsFirst);
        original.setServices(originalServices);
        original.delayFirstUpdateForPassedSlot();
        var pending = original.captureRewindState();
        var restoredServices = new TestObjectServices().withIsolatedObjectManager();
        var restored = recreate(original, pending, restoredServices);

        restored.update(1, null);
        assertTrue(calls.isEmpty(), "The restored passed-slot deferral must delay child initialization");
        restored.update(2, null);
        List<String> expected = pointsFirst
                ? List.of("custom-points:800", "custom-animal:800")
                : List.of("custom-animal:800", "custom-points:800");
        assertEquals(expected, calls);
        assertEquals(2, restoredServices.objectManager().getActiveObjects().size());
        receivedServices.forEach(services -> assertSame(restoredServices, services));
        assertTrue(originalServices.objectManager().getActiveObjects().isEmpty());

        var initialized = restored.captureRewindState();
        var laterServices = new TestObjectServices().withIsolatedObjectManager();
        var later = recreate(restored, initialized, laterServices);
        for (int vIntRunCount = 3; vIntRunCount <= 10; vIntRunCount++) {
            restored.update(vIntRunCount, null);
            later.update(vIntRunCount, null);
            assertSnapshotsMatch(restored, later);
        }
        assertEquals(expected, calls, "An initialized explosion must not invoke either factory again");
        assertTrue(laterServices.objectManager().getActiveObjects().isEmpty());
    }

    @Test
    void recreatePreservesPendingSoundDeferralAndAnimationThroughDeletion() {
        var originalServices = new RecordingObjectServices();
        var original = new ExplosionObjectInstance(0x27, 100, 200, null, 88);
        original.setServices(originalServices);
        original.delayFirstUpdateForPassedSlot();
        var restoredServices = new RecordingObjectServices();
        var restored = recreate(original, original.captureRewindState(), restoredServices);
        for (int vIntRunCount = 1; vIntRunCount <= 37; vIntRunCount++) {
            original.update(vIntRunCount, null);
            restored.update(vIntRunCount, null);
            assertSnapshotsMatch(original, restored);
            assertEquals(vIntRunCount == 1 ? 0 : 1, restoredServices.playedSfxCount);
            if (vIntRunCount == 8) {
                // A second reconstruction starts from an already animated object
                // whose SFX request has been consumed, not from constructor defaults.
                restored = recreate(restored, restored.captureRewindState(), restoredServices);
                assertSnapshotsMatch(original, restored);
            }
        }
        assertTrue(restored.isDestroyed());
        assertEquals(88, restoredServices.lastSfxId);
    }

    @Test
    void sonic1SubtypeKeepsItsOwnPointsAndChildInitStateAcrossRecreation() {
        var module = new Sonic1GameModule();
        var originalServices = new TestObjectServices().withGameModule(module).withIsolatedObjectManager();
        var original = new com.openggf.game.sonic1.objects.Sonic1ExplosionItemObjectInstance(
                100, 200, originalServices, 800);
        original.setServices(originalServices);
        var restoredServices = new TestObjectServices().withGameModule(module).withIsolatedObjectManager();
        var restored = recreate(original, original.captureRewindState(), restoredServices);
        assertSnapshotsMatch(original, restored);

        original.update(1, null);
        restored.update(1, null);
        assertSnapshotsMatch(original, restored);
        assertEquals(1, originalServices.objectManager().getActiveObjects().size());
        assertEquals(1, restoredServices.objectManager().getActiveObjects().size());

        var laterServices = new TestObjectServices().withGameModule(module).withIsolatedObjectManager();
        var later = recreate(restored, restored.captureRewindState(), laterServices);
        original.update(2, null);
        later.update(2, null);
        assertSnapshotsMatch(original, later);
        assertTrue(laterServices.objectManager().getActiveObjects().isEmpty(),
                "S1's separate initialized-child latch must not revert to its constructor default");
    }

    private static ExplosionObjectInstance recreate(ExplosionObjectInstance source,
            PerObjectRewindSnapshot state, ObjectServices services) {
        var restored = (ExplosionObjectInstance) source.recreateForRewind(
                new RewindRecreateContext(source.getSpawn(), state, services));
        restored.setServices(services);
        restored.restoreRewindState(state, com.openggf.game.rewind.schema.RewindCaptureContext.none());
        return restored;
    }

    private static void assertSnapshotsMatch(ExplosionObjectInstance expected, ExplosionObjectInstance actual) {
        var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "explosion", expected.captureRewindState(), actual.captureRewindState());
        assertTrue(differences.isEmpty(), differences.toString());
    }

    private static final class ChildMarker extends AbstractObjectInstance {
        ChildMarker(ObjectSpawn spawn, String name) { super(spawn, name); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static final class RecordingObjectServices extends TestObjectServices {
        private int playedSfxCount;
        private int lastSfxId = -1;

        @Override
        public void playSfx(int soundId) {
            playedSfxCount++;
            lastSfxId = soundId;
        }
    }
}
