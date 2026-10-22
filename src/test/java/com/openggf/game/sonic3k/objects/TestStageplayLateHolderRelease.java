package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native ownership cleanup on Stageplay interruption, without launch, SFX or route completion. */
class TestStageplayLateHolderRelease {
    @ParameterizedTest
    @ValueSource(strings = {"fbz-cage", "fbz-stationary", "fbz-chain", "fbz-pole", "fbz-spider", "fbz-pendulum",
            "mhz-horizontal", "mhz-vertical", "mhz-pulley", "mhz-parachute", "lrz-wall", "lrz-corkscrew",
            "dez-hang", "dez-tube", "dez-hub", "dez-room", "dez-teleporter", "dez-tunnel", "dez-controller",
            "soz-light", "soz-rapel", "ssz-elevator", "ssz-carrier", "ssz-rotating", "ssz-rotating-carrier",
            "ssz-claw", "ssz-release", "ssz-teleporter"})
    void nativeHeldStateIsReleasedWithoutLaunchingOrPlayingSound(String kind) throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> player, List::of));
        when(services.objectManager()).thenReturn(mock(ObjectManager.class));
        var spawn = new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
        AbstractObjectInstance object = switch (kind) {
            case "fbz-cage" -> new FbzWireCageObjectInstance(spawn);
            case "fbz-stationary" -> new FbzWireCageStationaryObjectInstance(spawn);
            case "fbz-chain" -> new FbzChainLinkObjectInstance(spawn);
            case "fbz-pole" -> new FbzSpinningPoleObjectInstance(spawn);
            case "fbz-spider" -> new FbzSpiderCraneObjectInstance(spawn);
            case "fbz-pendulum" -> new FbzMagneticPendulumObjectInstance(spawn);
            case "mhz-horizontal" -> new MhzSwingBarHorizontalObjectInstance(spawn);
            case "mhz-vertical" -> new MhzSwingBarVerticalObjectInstance(spawn);
            case "mhz-pulley" -> new MhzPulleyLiftObjectInstance(spawn);
            case "mhz-parachute" -> new MhzMushroomParachuteObjectInstance(spawn);
            case "lrz-wall" -> new LrzWallRideObjectInstance(spawn);
            case "lrz-corkscrew" -> new LrzCorkscrewObjectInstance(spawn);
            case "dez-hang" -> new S3kDezHangCarrierObjectInstance(spawn);
            case "dez-tube" -> new S3kDezGravityTubeObjectInstance(spawn);
            case "dez-hub" -> new S3kDezGravityHubObjectInstance(spawn);
            case "dez-room" -> new S3kDezGravityRoomObjectInstance(spawn);
            case "dez-teleporter" -> new S3kDezTeleporterObjectInstance(spawn);
            case "dez-tunnel" -> new S3kDezTunnelLauncherObjectInstance(spawn);
            case "dez-controller" -> new S3kDezTunnelLauncherObjectInstance.Controller(spawn);
            case "soz-light" -> new SozLightSwitchObjectInstance(spawn);
            case "soz-rapel" -> new SozRapelWireObjectInstance.Segment(spawn);
            case "ssz-elevator" -> new SszElevatorBarObjectInstance(spawn);
            case "ssz-carrier" -> new SszSwingingCarrierBarObjectInstance(spawn);
            case "ssz-rotating" -> new SszRotatingPlatformObjectInstance(spawn);
            case "ssz-rotating-carrier" -> new SszRotatingPlatformCarrierObjectInstance(spawn);
            case "ssz-claw" -> new SszCraneClaw(spawn, null);
            case "ssz-release" -> new SszCranePlayerRelease(spawn);
            default -> new SSZHPZTeleporterObjectInstance(spawn);
        };
        object.setServices(services);
        // An idle object must not release control owned by another native holder.
        when(player.isObjectControlled()).thenReturn(true);
        object.releasePlayersForVisualDirection();
        verify(player, never()).applyObjectControlState(any());
        verify(player, never()).setHidden(anyBoolean());
        verify(player, never()).setControlLocked(anyBoolean());
        clearInvocations(player, services);
        switch (kind) {
            case "fbz-cage" -> holdInTable(object, "participants", player, 2);
            case "fbz-stationary" -> holdInTable(object, "participants", player, 3);
            case "fbz-chain" -> holdInTable(object, "participants", player, 0);
            case "fbz-pole", "soz-light", "soz-rapel" -> holdInTable(object, "players", player, 0);
            case "fbz-spider" -> setEnum(object, "state", "CAPTURE");
            case "fbz-pendulum" -> set(object, "grabbed", true);
            case "mhz-horizontal", "mhz-vertical" -> {
                String type = kind.equals("mhz-horizontal") ? "HangState" : "BarState";
                var constructor = Class.forName(object.getClass().getName() + "$" + type).getDeclaredConstructor();
                constructor.setAccessible(true); Object state = constructor.newInstance();
                if (kind.equals("mhz-vertical")) set(state, "hanging", true);
                @SuppressWarnings("unchecked") var states = (Map<Object, Object>) get(object,
                        kind.equals("mhz-horizontal") ? "hangingPlayers" : "playerStates");
                states.put(player, state);
            }
            case "mhz-pulley" -> {
                Object hold = get(get(object, "leftHandle"), "player1");
                set(hold, "grabbed", true); set(hold, "player", player);
            }
            case "mhz-parachute" -> { set(object, "grabbed", true); set(object, "grabbedPlayer", player); }
            case "lrz-wall", "lrz-corkscrew" -> set(object, "p1Riding", true);
            case "dez-hang" -> ((boolean[]) get(object, "grabbed"))[0] = true;
            case "dez-tube" -> set(get(object, "playerOneState"), "riding", true);
            case "dez-hub" -> set(get(object, "playerOneState"), "state", 2);
            case "dez-room" -> set(get(object, "playerOneState"), "captured", true);
            case "dez-teleporter" -> setEnum(get(object, "playerOneState"), "routine", "SPIN_UP");
            case "dez-tunnel" -> ((int[]) get(object, "captured"))[0] = 2;
            case "dez-controller" -> ((int[]) get(object, "routine"))[0] = 2;
            case "ssz-elevator" -> set(object, "p1Holding", true);
            case "ssz-carrier" -> set(object, "p1Carried", true);
            case "ssz-rotating", "ssz-rotating-carrier" -> set(object, "p1State", -1);
            case "ssz-claw" -> set(object, "phase", 4);
            case "ssz-release" -> set(object, "ownedPlayer", player);
            default -> set(object, "state", SSZHPZTeleporterObjectInstance.STATE_CHARGING);
        }
        object.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
        verify(services, never()).playSfx(anyInt());
    }

    @Test void cnzRouteInterruptionRestoresOnlyOwnedPlayerLocksWithoutCompletingTheRoute() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> player, List::of));
        var object = new CnzTeleporterInstance(new ObjectSpawn(100, 100, 1, 0, 0, false, 100));
        object.setServices(services);
        object.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
        set(object, "armed", true); set(object, "playerCaptured", true); set(object, "playerHidden", true);
        object.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        verify(player).setControlLocked(false);
        verify(player).setHidden(false);
        verify(services, never()).camera();
        verify(services, never()).playSfx(anyInt());
        assertFalse((boolean) get(object, "playerCaptured"));
    }

    @Test void cannonInterruptionUsesNativeControlCooldownWithoutLaunch() throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class);
        var manager = mock(ObjectManager.class);
        when(services.objectManager()).thenReturn(manager);
        when(manager.getFrameCounter()).thenReturn(123);
        var cannon = new CnzCannonInstance(new ObjectSpawn(100, 100, 1, 0, 0, false, 100));
        cannon.setServices(services);
        cannon.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
        set(cannon, "capturedPlayer", player);
        cannon.releasePlayersForVisualDirection();
        verify(player).releaseFromObjectControl(123);
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
        verify(services, never()).playSfx(anyInt());
        verify(services, never()).camera();
        assertNull(get(cannon, "capturedPlayer"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cnzCagesDropOnlyTheirOwnNativeStandingLatch(boolean barber) throws Exception {
        var player = mock(AbstractPlayableSprite.class);
        var spawn = new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
        AbstractObjectInstance cage = barber ? new CnzBarberPoleObjectInstance(spawn) : new CnzWireCageObjectInstance(spawn);
        cage.setServices(mock(ObjectServices.class));
        when(player.getAnimationManager()).thenReturn(mock(com.openggf.sprites.managers.PlayableSpriteAnimation.class));
        cage.releasePlayersForVisualDirection();
        verifyNoInteractions(player);
        var constructor = Class.forName(cage.getClass().getName() + (barber ? "$RiderState" : "$CageState")).getDeclaredConstructor();
        constructor.setAccessible(true); Object state = constructor.newInstance(); set(state, "latched", true);
        @SuppressWarnings("unchecked") var riders = (Map<Object, Object>) get(cage, "riders");
        riders.put(player, state);
        cage.releasePlayersForVisualDirection();
        verify(player).setOnObject(false);
        assertFalse((boolean) get(state, "latched"));
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
    }

    @Test void craneHandoffBeforeFirstUpdateReleasesOnlyItsCapturedOwnerAndSurvivesRewind() {
        var player = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var spawn = new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
        var helper = new SszCranePlayerRelease(spawn, player);
        var table = new com.openggf.game.rewind.identity.RewindIdentityTable();
        table.registerPlayer(player, com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
        var context = com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(table);
        var saved = helper.captureRewindState(context);
        helper.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        verifyNoInteractions(unrelated);
        clearInvocations(player);
        helper.restoreRewindState(saved, context);
        helper.releasePlayersForVisualDirection();
        verify(player).applyObjectControlState(ObjectControlState.NONE);
        verifyNoInteractions(unrelated);
    }

    private static void holdInTable(Object owner, String name, AbstractPlayableSprite player, int flag) throws Exception {
        var table = (FbzParticipantStateTable) get(owner, name);
        table.flag(table.slot(player), flag, true);
    }
    private static Object get(Object owner, String name) throws Exception { return field(owner, name).get(owner); }
    private static void set(Object owner, String name, Object value) throws Exception { field(owner, name).set(owner, value); }
    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(Object owner, String name, String value) throws Exception {
        Field field = field(owner, name); field.set(owner, Enum.valueOf((Class) field.getType(), value));
    }
}
