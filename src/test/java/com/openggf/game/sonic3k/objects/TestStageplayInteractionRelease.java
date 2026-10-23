package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic2.objects.FlipperObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayInteractionRelease {
    private static ObjectSpawn spawn() { return new ObjectSpawn(100, 100, 1, 0, 0, false, 100); }
    private static Object value(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    @Test @SuppressWarnings("unchecked")
    void flippersUseTheirNativePerPlayerReleaseAndLeaveOtherParticipantsAlone() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var s2 = new FlipperObjectInstance(spawn(), "flipper");
        ((Map<AbstractPlayableSprite, Boolean>) value(s2, "lockedPlayerPrevSuppressed")).put(player, false);
        s2.releasePlayersForVisualDirection();
        verify(player).setObjectControlSuppressesMovement(false);
        var s3k = new PachinkoFlipperObjectInstance(spawn());
        set(s3k, "lockedPlayer", player);
        s3k.releasePlayersForVisualDirection();
        verify(player).setControlLocked(false);
        assertNull(value(s3k, "lockedPlayer"));
        verifyNoInteractions(unrelated);
    }

    @Test @SuppressWarnings("unchecked")
    void magnetUsesItsNonlaunchReleaseWithTheNativeClock() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class);
        var objects = mock(ObjectManager.class);
        when(services.objectManager()).thenReturn(objects);
        when(objects.getVblaCounter()).thenReturn(123);
        var magnet = new PachinkoMagnetOrbObjectInstance(spawn()); magnet.setServices(services);
        var type = Class.forName(magnet.getClass().getName() + "$PlayerState");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        var state = constructor.newInstance(); set(state, "captured", true);
        ((Map<AbstractPlayableSprite, Object>) value(magnet, "playerStates")).put(player, state);
        magnet.releasePlayersForVisualDirection();
        verify(player).releaseFromObjectControl(123);
        verify(player, never()).setXSpeed(anyShort()); verify(player, never()).setYSpeed(anyShort());
        assertFalse((boolean) value(state, "captured"));
    }

    @Test void turbineAndFallthroughUseNativeDropTailsWithoutAdvancingTheirMotion() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> player, List::of));
        var turbine = new LrzTurbineSpritesObjectInstance(spawn()); turbine.setServices(services);
        ((boolean[]) value(turbine, "captured"))[0] = true;
        turbine.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        assertFalse(((boolean[]) value(turbine, "captured"))[0]);
        clearInvocations(player);
        var loop = new SozLoopFallthroughObjectInstance(spawn()); loop.setServices(services);
        var participants = (FbzParticipantStateTable) value(loop, "participants");
        participants.flag(participants.slot(player), 0, true);
        loop.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        verify(player).setOnObject(false);
        verify(player, never()).setXSpeed(anyShort()); verify(player, never()).setYSpeed(anyShort());
    }
}
