package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Intro/controller interruptions release acquired participants without performing their story effects. */
class TestStageplayIntroControlRelease {
    @AfterEach void resetIntroStatics() { AizPlaneIntroInstance.resetIntroPhaseState(); }

    @ParameterizedTest @ValueSource(strings = {"aiz", "icz", "arrival", "tails", "launch"})
    void idleControllerDoesNotResetAnotherOwnersPlayer(String kind) {
        var unrelated = mock(AbstractPlayableSprite.class);
        var services = servicesFor(unrelated);
        var object = object(kind);
        object.setServices(services);
        object.releasePlayersForVisualDirection();
        verifyNoInteractions(unrelated);
        verify(services, never()).camera();
        verify(services, never()).playerQuery();
        verify(services, never()).playSfx(anyInt());
    }

    @ParameterizedTest @ValueSource(strings = {"aiz", "icz", "arrival", "tails", "launch"})
    void ownedParticipantReleaseSurvivesRewindAndIgnoresTheNewFocusedPlayer(String kind) throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var services = servicesFor(unrelated);
        var object = object(kind);
        object.setServices(services);
        acquire(object, owner, kind);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(owner, PlayerRefId.mainPlayer());
        identities.registerPlayer(unrelated, PlayerRefId.sidekick(0));
        var context = RewindCaptureContext.withIdentityTable(identities);
        var saved = object.captureRewindState(context);
        clearInvocations(owner, unrelated, services);
        object.releasePlayersForVisualDirection();
        assertPlayerOnlyRelease(owner, unrelated, services, kind);
        clearInvocations(owner, unrelated, services);
        object.restoreRewindState(saved, context);
        object.releasePlayersForVisualDirection();
        assertPlayerOnlyRelease(owner, unrelated, services, kind);
    }

    @Test void tailsHelperOwnsItsActualSidekickBeforeItsFirstUpdate() {
        var owner = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var helper = new SszTailsArrivalHelperObjectInstance(spawn(), owner);
        var services = servicesFor(unrelated); helper.setServices(services);
        helper.releasePlayersForVisualDirection();
        verify(owner).applyObjectControlState(ObjectControlState.NONE);
        verify(owner).getCpuController(); // The normal release tail restores native follow when present.
        verifyNoInteractions(unrelated);
        verifyNoInteractions(services);
    }

    @Test void forcedJumpRecordsBothActualParticipantsAndClearsTheirQueuedInput() throws Exception {
        var leader = mock(AbstractPlayableSprite.class);
        var partner = mock(AbstractPlayableSprite.class);
        var launch = new SszLaunchControllerObjectInstance(spawn());
        var forcedJump = SszLaunchControllerObjectInstance.class.getDeclaredMethod(
                "forcedJump", AbstractPlayableSprite.class, boolean.class);
        forcedJump.setAccessible(true);
        forcedJump.invoke(launch, leader, false);
        forcedJump.invoke(launch, partner, true);
        clearInvocations(leader, partner);
        launch.releasePlayersForVisualDirection();
        for (var player : List.of(leader, partner)) {
            verify(player).clearForcedInputMask();
            verify(player).clearLogicalInputState();
            verify(player).clearQueuedControlState();
            verify(player).applyObjectControlState(ObjectControlState.NONE);
            verify(player).setObjectMappingFrameControl(false);
            verify(player, never()).setXSpeed(anyShort());
            verify(player, never()).setYSpeed(anyShort());
        }
    }

    private static void assertPlayerOnlyRelease(AbstractPlayableSprite owner, AbstractPlayableSprite unrelated,
            ObjectServices services, String kind) {
        verify(owner).applyObjectControlState(ObjectControlState.NONE);
        verify(owner).setObjectMappingFrameControl(false);
        if (kind.equals("aiz") || kind.equals("icz")) {
            verify(owner).setControlLocked(false);
            verify(owner).setHidden(false);
        }
        if (kind.equals("launch") || kind.equals("icz")) {
            verify(owner).clearForcedInputMask();
            verify(owner).clearQueuedControlState();
        }
        if (kind.equals("launch")) verify(owner).clearLogicalInputState();
        verify(owner, never()).setXSpeed(anyShort());
        verify(owner, never()).setYSpeed(anyShort());
        verifyNoInteractions(unrelated);
        verify(services, never()).camera();
        verify(services, never()).playSfx(anyInt());
        verify(services, never()).fadeOutMusic();
        verify(services, never()).requestZoneAndAct(anyInt(), anyInt(), anyBoolean());
    }

    private static ObjectServices servicesFor(AbstractPlayableSprite unrelated) {
        var services = mock(ObjectServices.class);
        var camera = mock(Camera.class);
        when(camera.getFocusedSprite()).thenReturn(unrelated);
        when(services.camera()).thenReturn(camera);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> unrelated, List::of));
        return services;
    }
    private static ObjectSpawn spawn() { return new ObjectSpawn(100, 100, 1, 0, 0, false, 100); }
    private static AbstractObjectInstance object(String kind) {
        return switch (kind) {
            case "aiz" -> new AizPlaneIntroInstance(spawn());
            case "icz" -> new IczSnowboardIntroInstance(spawn());
            case "arrival" -> new SszArrivalControllerObjectInstance(spawn());
            case "tails" -> new SszTailsArrivalHelperObjectInstance(spawn());
            default -> new SszLaunchControllerObjectInstance(spawn());
        };
    }
    private static void acquire(AbstractObjectInstance object, AbstractPlayableSprite owner, String kind) throws Exception {
        if (kind.equals("launch")) {
            var method = object.getClass().getDeclaredMethod("forcedJump", AbstractPlayableSprite.class, boolean.class);
            method.setAccessible(true); method.invoke(object, owner, false);
        } else if (kind.equals("icz")) {
            ((IczSnowboardIntroInstance) object).primePostStartupWaitForBoardHandoff(owner);
        } else {
            field(object, "ownedPlayer").set(object, owner);
            if (kind.equals("aiz")) field(object, "ownsPlayerControl").setBoolean(object, true);
            if (kind.equals("arrival")) field(object, "phase").setInt(object, SszArrivalControllerObjectInstance.PHASE_RISE);
        }
    }
    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
}
