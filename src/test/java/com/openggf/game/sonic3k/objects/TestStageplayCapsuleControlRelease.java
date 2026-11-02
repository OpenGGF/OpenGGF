package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayCapsuleControlRelease {
    @Test
    void floatingCapsuleUsesSevenNormalExplosionsWithChildOwnedBreakSound() throws Exception {
        var fixture = new Fixture();
        var manager = mock(com.openggf.level.objects.ObjectManager.class);
        when(fixture.services.objectManager()).thenReturn(manager);
        when(fixture.services.rng()).thenReturn(new com.openggf.game.GameRng(
                com.openggf.game.GameRng.Flavour.S3K));
        var capsule = new Aiz2EndEggCapsuleInstance(100, 100) {
            @Override protected int getAnimalCount() { return 0; }
        };
        capsule.setServices(fixture.services);
        invoke(capsule, "openCapsule", new Class<?>[0]);
        verify(fixture.services, never()).playSfx(anyInt());
        var controller = (S3kBossExplosionController) field(capsule, "explosionController").get(capsule);
        for (int frame = 0; frame < 24; frame++) {
            controller.tick();
            invoke(capsule, "spawnPendingExplosions", new Class<?>[0]);
        }
        assertTrue(controller.isFinished());
        var children = org.mockito.ArgumentCaptor.forClass(com.openggf.level.objects.ObjectInstance.class);
        verify(manager, times(7)).addDynamicObjectAfterCurrent(children.capture());
        for (var child : children.getAllValues()) {
            assertInstanceOf(FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion.class, child);
            assertTrue(child.isHighPriority());
            assertEquals(1, child.getPriorityBucket());
            ((AbstractObjectInstance) child).setServices(fixture.services);
            child.update(1, null);
        }
        verify(fixture.services, times(7)).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.BREAK.id);
        verify(fixture.services, never()).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.EXPLODE.id);
    }

    @Test
    void idleCapsulesAndEmbeddedBossNeverResetUnacquiredParticipants() {
        var fixture = new Fixture();
        for (var owner : List.<AbstractObjectInstance>of(new CnzEggCapsuleInstance(spawn()),
                new Aiz2EndEggCapsuleInstance(100, 100), new Mgz2EndEggCapsuleInstance(100, 100),
                new HczEndBossEggCapsuleInstance(100, 100), com.openggf.level.objects.ObjectConstructionContext.with(fixture.services, -1, () -> new AizEndBossInstance(spawn())))) {
            owner.setServices(fixture.services);
            owner.releasePlayersForVisualDirection();
        }
        verifyNoInteractions(fixture.main, fixture.sidekick);
    }

    @Test
    void uprightReleaseClearsOnlyAcquiredControlAndExclusiveSupportWithoutChangingPoseOrPosition() throws Exception {
        var fixture = new Fixture();
        var capsule = new CnzEggCapsuleInstance(spawn());
        capsule.setServices(fixture.services);
        invoke(capsule, "lockForResults", new Class<?>[] {AbstractPlayableSprite.class}, fixture.main);
        clearInvocations(fixture.main, fixture.sidekick);
        capsule.releasePlayersForVisualDirection();
        verify(fixture.main).applyObjectControlState(ObjectControlState.NONE);
        verify(fixture.main).setControlLocked(false);
        verify(fixture.main).setObjectControlledSolidContactObject(null);
        verifyNoMoreInteractions(fixture.main);
        verifyNoInteractions(fixture.sidekick);
        assertFalse(capsule.allowsObjectControlledSolidContact(fixture.main, capsule));
    }

    @Test
    void floatingAcquiredResultsOwnersRoundTripByStablePlayerIdentity() {
        var fixture = new Fixture();
        var capsule = new Aiz2EndEggCapsuleInstance(100, 100);
        capsule.setServices(fixture.services);
        capsule.lockForResults(fixture.main);
        var snapshot = capsule.captureRewindState(fixture.context());
        capsule.releasePlayersForVisualDirection();
        capsule.restoreRewindState(snapshot, fixture.context());
        clearInvocations(fixture.main, fixture.sidekick);
        capsule.releasePlayersForVisualDirection();
        verify(fixture.main).applyObjectControlState(ObjectControlState.NONE);
        verify(fixture.main).setControlLocked(false);
        verify(fixture.main).setForcedAnimationId(-1);
        verifyNoMoreInteractions(fixture.main);
        verifyNoInteractions(fixture.sidekick);
    }

    @Test
    void mgzFloatingRouteDoesNotClaimResultsPlayersItNeverLocks() {
        class Mgz extends Mgz2EndEggCapsuleInstance {
            Mgz() { super(100, 100); }
            @Override protected void spawnResultsScreen() { }
        }
        var fixture = new Fixture();
        var capsule = new Mgz();
        capsule.setServices(fixture.services);
        assertFalse(capsule.shouldLockPlayersForResults());
        capsule.startResults(fixture.main);
        clearInvocations(fixture.main, fixture.sidekick);
        capsule.releasePlayersForVisualDirection();
        verifyNoInteractions(fixture.main, fixture.sidekick);
    }

    @Test
    void aizNativeP2EndingOwnerReleasesItsOwnLocksWithoutTheNormalAnimationAndAirRestore() throws Exception {
        var fixture = new Fixture();
        var cpu = mock(SidekickCpuController.class);
        when(fixture.sidekick.getCpuController()).thenReturn(cpu);
        var capsule = new Aiz2EndEggCapsuleInstance(100, 100);
        capsule.setServices(fixture.services);
        field(capsule, "nativeP2EndingPoseOwner").set(capsule, fixture.sidekick);
        field(capsule, "tailsOpenControllerLockApplied").set(capsule, true);
        field(capsule, "tailsEndingPoseObjectControlLocked").set(capsule, true);
        clearInvocations(fixture.main, fixture.sidekick, cpu);
        capsule.releasePlayersForVisualDirection();
        verify(cpu).cancelQueuedNativeEndingPose();
        verify(cpu).setController2SignedLocked(false);
        verify(cpu).mirrorRawController2LogicalForEndingPose();
        verify(fixture.sidekick).applyObjectControlState(ObjectControlState.NONE);
        verify(fixture.sidekick).setControlLocked(false);
        verify(fixture.sidekick, never()).setAir(anyBoolean());
        verify(fixture.sidekick, never()).setAnimationId(anyInt());
        verifyNoInteractions(fixture.main);
    }

    @Test
    void hczResultsAndQueuedP2ReleaseRoundTripAndDoNotRunTheGeyserHandoff() throws Exception {
        var fixture = new Fixture();
        var cpu = mock(SidekickCpuController.class);
        when(fixture.sidekick.getCpuController()).thenReturn(cpu);
        var capsule = new HczEndBossEggCapsuleInstance(100, 100);
        capsule.setServices(fixture.services);
        invoke(capsule, "lockForResults", new Class<?>[] {AbstractPlayableSprite.class}, fixture.main);
        invoke(capsule, "advanceTailsEndingPoseCheck", new Class<?>[0]);
        var snapshot = capsule.captureRewindState(fixture.context());
        capsule.releasePlayersForVisualDirection();
        capsule.restoreRewindState(snapshot, fixture.context());
        clearInvocations(fixture.main, fixture.sidekick, cpu);
        var nativeState = nativeScalarState(capsule);
        capsule.releasePlayersForVisualDirection();
        verify(fixture.main).applyObjectControlState(ObjectControlState.NONE);
        verify(fixture.main).setControlLocked(false);
        verify(cpu).cancelQueuedNativeEndingPose();
        verify(cpu, never()).setController2SignedLocked(anyBoolean());
        verify(fixture.sidekick, never()).applyObjectControlState(any());
        verify(fixture.main, never()).setAnimationId(anyInt());
        verify(fixture.main, never()).setAir(anyBoolean());
        verify(fixture.sidekick, never()).setAir(anyBoolean());
        assertEquals(nativeState, nativeScalarState(capsule), "Release must not advance any native capsule phase");
        verify(fixture.services, never()).camera();
        verify(fixture.services, never()).objectManager();
    }

    @Test
    void owningAizBossDelegatesTheEmbeddedControllersPlayerOnlyRelease() throws Exception {
        var fixture = new Fixture();
        var boss = com.openggf.level.objects.ObjectConstructionContext.with(fixture.services, -1, () -> new AizEndBossInstance(spawn()));
        boss.setServices(fixture.services);
        var sequence = field(boss, "postDefeatSequence").get(boss);
        var controller = (Aiz2BossEndSequenceController) field(sequence, "controller").get(sequence);
        controller.setServices(fixture.services);
        invoke(controller, "holdEndingPose", new Class<?>[] {AbstractPlayableSprite.class}, fixture.main);
        invoke(controller, "forceRightLogicalInput", new Class<?>[] {AbstractPlayableSprite.class}, fixture.main);
        var snapshot = boss.captureRewindState(fixture.context());
        boss.releasePlayersForVisualDirection();
        boss.restoreRewindState(snapshot, fixture.context());
        clearInvocations(fixture.main, fixture.sidekick);
        boss.releasePlayersForVisualDirection();
        verify(fixture.main).applyObjectControlState(ObjectControlState.NONE);
        verify(fixture.main).setControlLocked(false);
        verify(fixture.main).setForcedAnimationId(-1);
        verify(fixture.main).clearForcedInputMask();
        verify(fixture.main).setForceInputRight(false);
        verifyNoMoreInteractions(fixture.main);
        verifyNoInteractions(fixture.sidekick);
    }

    @Test
    void uprightQueuedPoseCancellationLeavesTheNotYetAcquiredPlayerControlUntouched() throws Exception {
        var fixture = new Fixture();
        var cpu = mock(SidekickCpuController.class);
        when(fixture.sidekick.getCpuController()).thenReturn(cpu);
        var capsule = new CnzEggCapsuleInstance(spawn());
        capsule.setServices(fixture.services);
        invoke(capsule, "queueSidekickEndPoses", new Class<?>[] {com.openggf.game.PlayableEntity.class}, fixture.main);
        var snapshot = capsule.captureRewindState(fixture.context());
        capsule.releasePlayersForVisualDirection();
        capsule.restoreRewindState(snapshot, fixture.context());
        clearInvocations(fixture.main, fixture.sidekick, cpu);
        capsule.releasePlayersForVisualDirection();
        verify(cpu).cancelQueuedNativeEndingPose();
        verify(cpu, never()).setController2SignedLocked(anyBoolean());
        verify(fixture.sidekick, never()).applyObjectControlState(any());
        verify(fixture.sidekick, never()).setControlLocked(anyBoolean());
        verifyNoInteractions(fixture.main);
    }

    @Test
    void cancellingAQueuedPosePreventsNextPlayerPassReacquisitionWithoutResettingOtherCpuState() throws Exception {
        var fixture = new Fixture();
        var cpu = new SidekickCpuController(fixture.sidekick, fixture.main);
        cpu.setController2SignedLocked(true);
        field(cpu, "controlCounter").setInt(cpu, 37);
        cpu.queueNativeEndingPoseForNextPlayerSlot();
        clearInvocations(fixture.sidekick);
        cpu.cancelQueuedNativeEndingPose();
        invoke(cpu, "applyPendingNativeEndingPoseAfterPhysics", new Class<?>[0]);
        assertTrue(cpu.isController2SignedLocked());
        assertEquals(37, field(cpu, "controlCounter").getInt(cpu));
        verifyNoInteractions(fixture.sidekick);
    }

    @Test
    void ownerIdentitySetRejectsMissingReferencesAndRestoresToRecreatedPlayers() {
        var fixture = new Fixture();
        var owners = new PlayerControlOwners();
        assertEquals(List.of(), owners.capture(RewindCaptureContext.none()));
        owners.add(fixture.main); owners.add(fixture.main);
        assertEquals(1, owners.players().size());
        assertThrows(IllegalStateException.class, () -> owners.capture(RewindCaptureContext.none()));
        var refs = owners.capture(fixture.context());
        var recreated = mock(AbstractPlayableSprite.class);
        var identities = new RewindIdentityTable(); identities.registerPlayer(recreated, PlayerRefId.mainPlayer());
        owners.restore(refs, RewindCaptureContext.withIdentityTable(identities));
        assertSame(recreated, owners.players().getFirst());
    }

    private static java.util.Map<Field, Object> nativeScalarState(Object owner) throws Exception {
        var result = new java.util.HashMap<Field, Object>();
        for (Class<?> type = owner.getClass(); type != AbstractObjectInstance.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && (field.getType().isPrimitive() || field.getType().isEnum())) {
                    field.setAccessible(true);
                    result.put(field, field.get(owner));
                }
            }
        }
        return result;
    }

    private static ObjectSpawn spawn() { return new ObjectSpawn(100, 100, 0x81, 0, 0, false, 100); }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static final class Fixture {
        final AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        final AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        final ObjectServices services = mock(ObjectServices.class);
        Fixture() {
            when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, () -> List.of(sidekick)));
            when(services.gameState()).thenReturn(mock(GameStateManager.class));
            when(services.configuration()).thenReturn(mock(com.openggf.configuration.SonicConfigurationService.class));
        }
        RewindCaptureContext context() {
            var table = new RewindIdentityTable();
            table.registerPlayer(main, PlayerRefId.mainPlayer()); table.registerPlayer(sidekick, PlayerRefId.sidekick(0));
            return RewindCaptureContext.withIdentityTable(table);
        }
    }
}
