package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Player-only interruption of native story owners, without completing their story or moving players. */
class TestStageplayStoryControlRelease {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(100, 100, 1, 0, 0, false, 0);

    @ParameterizedTest
    @ValueSource(strings = {"mhz2", "mhz2-lift", "cnz2b", "sk-intro", "mhz1", "mhz1-p2",
            "lbz1-range", "hpz-entry", "hpz-theft", "hpz-route", "giant-ring", "pedestal", "p2-lock"})
    void releasesOnlyAcquiredParticipantsWithoutStorySideEffects(String kind) throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 1000);
        when(player.isObjectControlled()).thenReturn(true);
        when(unrelated.isObjectControlled()).thenReturn(true);
        when(unrelated.isControlLocked()).thenReturn(true);
        when(unrelated.isHidden()).thenReturn(true);
        var services = services(kind, player, unrelated);
        AbstractObjectInstance owner = owner(kind);
        owner.setServices(services);
        clearInvocations(player, unrelated, services);
        owner.releasePlayersForVisualDirection();
        verifyNoInteractions(player, unrelated);

        acquire(kind, owner, player);
        clearInvocations(player, unrelated, services);
        owner.releasePlayersForVisualDirection();
        if (Set.of("mhz2", "mhz2-lift", "cnz2b", "lbz1-range", "hpz-entry", "hpz-theft",
                "hpz-route", "giant-ring", "pedestal").contains(kind))
            verify(player).applyObjectControlState(ObjectControlState.NONE);
        if (Set.of("mhz2", "mhz2-lift", "cnz2b", "sk-intro", "mhz1", "mhz1-p2", "lbz1-range",
                "hpz-theft", "hpz-route", "p2-lock").contains(kind))
            verify(player).setControlLocked(false);
        if (kind.equals("giant-ring")) verify(player).setHidden(false);
        else verify(player, never()).setHidden(anyBoolean());
        verifyNoInteractions(unrelated);
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
        verify(player, never()).setGSpeed(anyShort());
        for (var invocation : mockingDetails(services).getInvocations())
            assertFalse(Set.of("camera", "playSfx", "requestZoneAndAct", "requestSpecialStageEntry",
                    "requestSanctuaryEntry", "requestSessionSave").contains(invocation.getMethod().getName()),
                    "Interruption must not run story completion: " + invocation);
        clearInvocations(player, unrelated, services);
        owner.releasePlayersForVisualDirection();
        verifyNoInteractions(player, unrelated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cnz2b", "hpz-entry", "hpz-route", "giant-ring", "pedestal", "p2-lock"})
    void rewindOwnershipResolvesStablePlayerIdsInsteadOfKeepingOldPlayers(String kind) throws Exception {
        var oldPlayer = mock(AbstractPlayableSprite.class);
        when(oldPlayer.getCentreX()).thenReturn((short) 1000);
        var unrelated = mock(AbstractPlayableSprite.class);
        var owner = owner(kind);
        owner.setServices(services(kind, oldPlayer, unrelated));
        acquire(kind, owner, oldPlayer);
        Object phase = null;
        if (kind.equals("cnz2b")) {
            Field field = owner.getClass().getDeclaredField("phase");
            field.setAccessible(true);
            phase = java.util.Arrays.stream(field.getType().getEnumConstants())
                    .filter(value -> value.toString().equals("PRE_JUMP_WAIT")).findFirst().orElseThrow();
            field.set(owner, phase);
        }
        var identities = new RewindIdentityTable();
        identities.registerPlayer(oldPlayer, PlayerRefId.mainPlayer());
        var snapshot = owner.captureRewindState(RewindCaptureContext.withIdentityTable(identities));
        owner.releasePlayersForVisualDirection();
        if (kind.equals("cnz2b")) {
            Field field = owner.getClass().getDeclaredField("phase");
            field.setAccessible(true);
            field.set(owner, field.getType().getEnumConstants()[0]);
        }
        var restoredPlayer = mock(AbstractPlayableSprite.class);
        var restoredIdentities = new RewindIdentityTable();
        restoredIdentities.registerPlayer(restoredPlayer, PlayerRefId.mainPlayer());
        clearInvocations(oldPlayer);
        owner.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(restoredIdentities));
        if (kind.equals("cnz2b")) {
            Field field = owner.getClass().getDeclaredField("phase");
            field.setAccessible(true);
            assertEquals(phase, field.get(owner), "Adding ownership extras must preserve existing native rewind fields");
        }
        // Ignore generic scalar restoration; the hook must target only the newly resolved participant.
        clearInvocations(restoredPlayer, oldPlayer);
        owner.releasePlayersForVisualDirection();
        assertFalse(mockingDetails(restoredPlayer).getInvocations().isEmpty());
        verifyNoInteractions(oldPlayer);
    }

    @Test void giantRingTransfersCapturedOwnershipToItsSurvivingFlashWithoutChangingPlayerState() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var ring = new Sonic3kSSEntryRingObjectInstance(SPAWN);
        ring.setServices(services("giant-ring", player, unrelated));
        acquire("giant-ring", ring, player);
        var flash = new Sonic3kSSEntryFlashObjectInstance(ring, 100, 100);
        clearInvocations(player);
        ring.transferCapturedPlayersTo(flash);
        ring.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(player, PlayerRefId.mainPlayer());
        var snapshot = flash.captureRewindState(RewindCaptureContext.withIdentityTable(identities));
        flash.releasePlayersForVisualDirection();
        verify(player).setHidden(false);
        var restoredPlayer = mock(AbstractPlayableSprite.class);
        var restoredIdentities = new RewindIdentityTable();
        restoredIdentities.registerPlayer(restoredPlayer, PlayerRefId.mainPlayer());
        flash.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(restoredIdentities));
        clearInvocations(restoredPlayer, player);
        flash.releasePlayersForVisualDirection();
        verify(restoredPlayer).applyObjectControlState(ObjectControlState.NONE);
        verify(restoredPlayer).setHidden(false);
        verifyNoInteractions(player);
    }

    @Test void rejectedSpecialStageAdmissionNeverAcquiresOrReleasesPlayerControl() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var services = services("giant-ring", player, unrelated);
        when(services.runPolicy()).thenReturn(GameplayRunPolicy.isolatedAct());
        var ring = new Sonic3kSSEntryRingObjectInstance(SPAWN);
        ring.setServices(services);
        invoke(ring, "onTouched", new Class<?>[]{AbstractPlayableSprite.class}, player);
        clearInvocations(player);
        ring.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
        var pedestal = (HPZSuperEmeraldObjectInstance) owner("pedestal");
        pedestal.setServices(services);
        try (var policy = mockStatic(com.openggf.game.mutators.LevelMutatorPolicyAccess.class)) {
            policy.when(() -> com.openggf.game.mutators.LevelMutatorPolicyAccess.admit(services,
                    com.openggf.game.mutators.StageEntryKind.SPECIAL)).thenReturn(0L);
            assertFalse(pedestal.beginSelection(player));
        }
        clearInvocations(player);
        pedestal.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
    }

    @Test void naturallyReleasedCnzControlDoesNotClearALaterOwnersLock() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = services("cnz2b", player, mock(AbstractPlayableSprite.class));
        when(services.camera().getY()).thenReturn((short) -1000);
        var owner = new CutsceneKnucklesCnz2BInstance(SPAWN);
        owner.setServices(services);
        invoke(owner, "routineForcePlayerRight", new Class<?>[]{AbstractPlayableSprite.class}, player);
        invoke(owner, "routineForcePlayerLeft", new Class<?>[]{AbstractPlayableSprite.class}, player);
        clearInvocations(player);
        owner.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
    }

    private static ObjectServices services(String kind, AbstractPlayableSprite player, AbstractPlayableSprite unrelated) {
        var services = mock(ObjectServices.class);
        boolean sidekickOwner = kind.equals("p2-lock");
        if (sidekickOwner) when(player.isControlLocked()).thenReturn(true);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(
                () -> sidekickOwner ? unrelated : player,
                () -> sidekickOwner ? List.of(player) : List.of(unrelated)));
        when(services.camera()).thenReturn(mock(Camera.class));
        when(services.zoneRuntimeRegistry()).thenReturn(mock(ZoneRuntimeRegistry.class));
        when(services.runPolicy()).thenReturn(GameplayRunPolicy.stock());
        var manager = mock(ObjectManager.class);
        when(manager.captureIdentityContext()).thenReturn(
                RewindCaptureContext.withIdentityTable(new RewindIdentityTable()));
        when(services.objectManager()).thenReturn(manager);
        return services;
    }

    private static AbstractObjectInstance owner(String kind) throws Exception {
        return switch (kind) {
            case "mhz2" -> new CutsceneKnucklesMhz2Instance(SPAWN);
            case "mhz2-lift" -> nested("CutsceneKnucklesMhz2Instance$Mhz2KnucklesLiftChild",
                    ObjectSpawn.class, SPAWN);
            case "cnz2b" -> new CutsceneKnucklesCnz2BInstance(SPAWN);
            case "sk-intro" -> new CutsceneKnucklesSkIntroInstance(SPAWN);
            case "mhz1" -> new Mhz1CutsceneKnucklesInstance(SPAWN);
            case "mhz1-p2" -> nested("Mhz1CutsceneKnucklesInstance$Mhz1CutscenePlayerTwoStopper",
                    Mhz1CutsceneKnucklesInstance.class, new Mhz1CutsceneKnucklesInstance(SPAWN));
            case "lbz1-range" -> new CutsceneKnucklesLbz1RangeHelper(SPAWN);
            case "hpz-entry" -> {
                var controller = new HPZSSEntryControlObjectInstance(SPAWN);
                set(controller, "progression", mock(S3kEmeraldProgression.class));
                set(controller, "runtime", mock(S3kSanctuaryRuntimeState.class));
                yield controller;
            }
            case "hpz-theft" -> new HpzEmeraldTheftPlayerControlObjectInstance();
            case "hpz-route" -> new HpzTeleporterRouteHelperObjectInstance(SPAWN, null);
            case "giant-ring" -> new Sonic3kSSEntryRingObjectInstance(SPAWN);
            case "pedestal" -> {
                var progression = mock(S3kEmeraldProgression.class);
                when(progression.state(anyInt())).thenReturn(S3kEmeraldProgression.EmeraldState.CHAOS);
                var runtime = mock(S3kSanctuaryRuntimeState.class);
                when(runtime.beginPedestalSelection(anyInt())).thenReturn(true);
                yield new HPZSuperEmeraldObjectInstance(SPAWN, progression, runtime);
            }
            default -> new S3kNativeP2LockInstance();
        };
    }

    private static void acquire(String kind, AbstractObjectInstance owner, AbstractPlayableSprite player) throws Exception {
        switch (kind) {
            case "mhz2" -> invoke(owner, "lockPlayer", new Class<?>[]{PlayableEntity.class}, player);
            case "mhz2-lift" -> set(owner, "player", player); // Native carrier constructor's existing captured reference.
            case "cnz2b" -> invoke(owner, "routineForcePlayerRight", new Class<?>[]{AbstractPlayableSprite.class}, player);
            case "sk-intro" -> invoke(owner, "routineInit", new Class<?>[]{PlayableEntity.class}, player);
            case "mhz1" -> invoke(owner, "routineWaitPlayer", new Class<?>[]{PlayableEntity.class}, player);
            case "mhz1-p2" -> invoke(owner, "lockSidekick", new Class<?>[]{AbstractPlayableSprite.class}, player);
            case "lbz1-range" -> invoke(owner, "applyCapture", new Class<?>[]{AbstractPlayableSprite.class}, player);
            case "hpz-entry" -> ((HPZSSEntryControlObjectInstance) owner).applyFreshLockForTest(player);
            case "hpz-theft" -> invoke(owner, "lock", new Class<?>[]{AbstractPlayableSprite.class, int.class}, player, 0);
            case "hpz-route" -> invoke(owner, "lockController", new Class<?>[]{AbstractPlayableSprite.class, int.class}, player, 0);
            case "giant-ring" -> invoke(owner, "enterSpecialStageSequence", new Class<?>[]{AbstractPlayableSprite.class}, player);
            case "pedestal" -> assertTrue(((HPZSuperEmeraldObjectInstance) owner).beginSelection(player));
            case "p2-lock" -> owner.update(0, null);
            default -> fail(kind);
        }
    }

    private static AbstractObjectInstance nested(String name, Class<?> parameterType, Object value) throws Exception {
        var constructor = Class.forName("com.openggf.game.sonic3k.objects." + name).getDeclaredConstructor(parameterType);
        constructor.setAccessible(true);
        return (AbstractObjectInstance) constructor.newInstance(value);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, arguments);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
