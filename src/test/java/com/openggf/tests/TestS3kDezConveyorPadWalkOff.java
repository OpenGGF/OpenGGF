package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezConveyorPadObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real participant contacts: a full-solid walk-off retires only that slot's carry bit. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezConveyorPadWalkOff {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,0,false", "1,0,false", "0,1,false", "1,1,false",
            "0,0,true", "1,0,true", "0,1,true", "1,1,true"})
    void walkOffConsumesOnlyExitingBitAndRecreates(int leavingPlayer, int facing, boolean reverse) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0)
                .startPosition((short) 0x560, (short) 0x400).startPositionIsCentre().build();
        fixture.camera().setX((short) 0x500);
        fixture.camera().setY((short) 0x480);
        GameServices.gameState().setReverseGravityActive(reverse);
        var manager = GameServices.level().getObjectManager();
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var pad = new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x600, 0x500, 0x53, 0x30, facing, false, 0));
        manager.addDynamicObject(pad);
        dispatch(fixture);
        for (var player : players) {
            player.setRenderFlagOnScreen(true);
            NativePositionOps.writeXPosResetSubpixel(player, pad.getX());
            int reach = 0x10 + player.getYRadius() + 1;
            NativePositionOps.writeYPosResetSubpixel(player, pad.getY() + (reverse ? reach : -reach));
            player.setAir(true);
            player.setOnObject(false);
            player.setYSpeed((short) 0x30);
        }
        dispatch(fixture);
        for (var player : players) {
            assertTrue(manager.isRidingObject(player, pad), "real landing " + player.getCode());
            assertTrue(manager.hasObjectStandingBit(player, pad));
        }
        dispatch(fixture); // Activate without carry, as loc_479F0 does.
        dispatch(fixture); // Active body: both riders carried, first-standing edge latched.
        assertEquals(1, pad.routineForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        int slot = pad.getSlotIndex();
        var first = walkOffAndObserve(fixture, pad, leavingPlayer, reverse);
        manager.removeDynamicObject(pad);
        registry.restore(saved);
        var restored = manager.getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezConveyorPadObjectInstance p && p.getSlotIndex() == slot)
                .map(o -> (S3kDezConveyorPadObjectInstance) o).findFirst().orElseThrow();
        assertNotSame(pad, restored);
        assertEquals(slot, restored.getSlotIndex());
        for (var player : players) {
            assertTrue(manager.isRidingObject(player, restored));
            assertTrue(manager.hasObjectStandingBit(player, restored));
        }
        assertEquals(first, walkOffAndObserve(fixture, restored, leavingPlayer, reverse));
        manager.removeDynamicObject(restored);
        var replacement = new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x600, 0x500, 0x53, 0x30, facing, false, 0));
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertEquals(slot, replacement.getSlotIndex());
        for (var player : players) assertFalse(manager.hasObjectStandingBit(player, replacement));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "0,true", "1,true"})
    void inBoundsControlledParticipantKeepsItsStandingBit(int playerIndex, boolean reverse) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0)
                .startPosition((short) 0x560, (short) 0x400).startPositionIsCentre().build();
        fixture.camera().setX((short) 0x500);
        fixture.camera().setY((short) 0x480);
        GameServices.gameState().setReverseGravityActive(reverse);
        var manager = GameServices.level().getObjectManager();
        var player = playerIndex == 0 ? fixture.sprite()
                : GameServices.sprites().getRegisteredSidekicks().getFirst();
        var pad = new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x600, 0x500, 0x53, 0x30, 0, false, 0));
        manager.addDynamicObject(pad);
        dispatch(fixture);
        player.setRenderFlagOnScreen(true);
        NativePositionOps.writeXPosResetSubpixel(player, pad.getX());
        int reach = 0x10 + player.getYRadius() + 1;
        NativePositionOps.writeYPosResetSubpixel(player, pad.getY() + (reverse ? reach : -reach));
        player.setAir(true);
        player.setYSpeed((short) 0x30);
        dispatch(fixture);
        assertTrue(manager.hasObjectStandingBit(player, pad), "real landing");
        player.setObjectControlled(true);
        dispatch(fixture);
        // loc_1E1CA / loc_1E1F4 return without clearing an in-bounds controlled slot.
        assertTrue(manager.hasObjectStandingBit(player, pad));
    }

    private List<String> walkOffAndObserve(HeadlessTestFixture fixture,
            S3kDezConveyorPadObjectInstance pad, int leavingPlayer, boolean reverse) {
        var manager = GameServices.level().getObjectManager();
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var leaving = players.get(leavingPlayer);
        var held = players.get(1 - leavingPlayer);
        int dx = (pad.animationForTest() == 0 ? 2 : -2) * (reverse ? -1 : 1);
        int edge = pad.getSolidParams().halfWidth();
        // Declared component movement to one pixel within the departing edge.
        // Object-local carry crosses it; no trace positions are loaded here.
        int startX = pad.getX() + (dx > 0 ? edge - 1 : -edge + 1);
        NativePositionOps.writeXPosPreserveSubpixel(leaving, startX);
        leaving.setSubpixelRaw(0x1234, 0x5678);
        int leavingY = leaving.getCentreY();
        int heldX = held.getCentreX();
        assertFalse(leaving.getAir());
        dispatch(fixture);
        assertEquals(startX + dx, leaving.getCentreX(), "final supported carry precedes walk-off");
        assertEquals(leavingY, leaving.getCentreY(), "out-of-range return does not reseat Y");
        assertFalse(manager.isRidingObject(leaving, pad));
        assertFalse(leaving.isOnObject());
        assertTrue(leaving.getAir());
        assertFalse(manager.hasObjectStandingBit(leaving, pad), "own full-solid walk-off clears the exiting slot bit");
        assertEquals(heldX + dx, held.getCentreX(), "other participant keeps native conveyor carry");
        assertTrue(manager.isRidingObject(held, pad));
        assertTrue(manager.hasObjectStandingBit(held, pad));
        var rows = new ArrayList<String>();
        for (int i = 0; i < 24; i++) {
            dispatch(fixture);
            assertEquals(startX + dx, leaving.getCentreX(), "no stale carry after walk-off");
            assertEquals(0x1234, leaving.getXSubpixelRaw());
            assertEquals(0x5678, leaving.getYSubpixelRaw());
            assertFalse(manager.hasObjectStandingBit(leaving, pad));
            assertTrue(manager.hasObjectStandingBit(held, pad));
            rows.add(pad.getY() + ":" + pad.remainingForTest() + ":" + pad.animationForTest()
                    + ":" + leaving.getCentreX() + ":" + held.getCentreX() + ":" + held.getCentreY());
        }
        return rows;
    }

    private void dispatch(HeadlessTestFixture fixture) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(), fixture.sprite(),
                GameServices.sprites().getRegisteredSidekicks(), 0, false, true, true);
    }
}
