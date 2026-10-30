package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.FbzDezPlayerLauncherObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The launcher's native SolidObjectTop new-contact boundary, with real participants. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFloorLauncherLanding {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,0,false", "1,0,false", "0,1,false", "1,1,false",
            "0,0,true", "1,0,true"})
    void exactBoundaryRejectsEachParticipantBeforeLandingAndAfterRecreation(
            int participant, int placementFlags, boolean reverse) {
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
        var player = players.get(participant);
        var other = players.get(1 - participant);
        var launcher = new FbzDezPlayerLauncherObjectInstance(
                new ObjectSpawn(0x600, 0x500, 0x78, 0, placementFlags, false, 0));
        manager.addDynamicObject(launcher);
        dispatch(fixture);
        NativePositionOps.writeXPosResetSubpixel(other, 0x500);
        NativePositionOps.writeYPosResetSubpixel(other, 0x400);
        other.setAir(true);
        player.setRenderFlagOnScreen(true);
        player.setRolling(true);
        player.setAir(true);
        player.setOnObject(false);
        player.setYSpeed((short) 0x3B8);
        player.setXSpeed((short) 0x451);
        player.setGSpeed((short) 0x452);
        NativePositionOps.writeXPosResetSubpixel(player, launcher.getX() - 6);
        // loc_1E45A's d0 = object top - (player Y + live radius + 4).
        int reach = 3 + player.getYRadius() + 4;
        NativePositionOps.writeYPosResetSubpixel(player, launcher.getY() + (reverse ? reach : -reach));
        player.setSubpixelRaw(0xD700, 0xD800);
        int boundaryY = player.getCentreY();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var boundary = registry.capture();
        int slot = launcher.getSlotIndex();

        dispatch(fixture);
        assertRejected(launcher, player, boundaryY);
        assertFalse(manager.hasObjectStandingBit(other, launcher));
        manager.removeDynamicObject(launcher);
        assertFalse(manager.getActiveObjects().contains(launcher));
        registry.restore(boundary);
        var restored = atSlot(slot);
        assertNotSame(launcher, restored);
        assertEquals(slot, restored.getSlotIndex());
        dispatch(fixture);
        assertRejected(restored, player, boundaryY);

        // One pixel inside the native window must still land; rejection is not disabled solidity.
        NativePositionOps.writeYPosPreserveSubpixel(player, boundaryY + (reverse ? -1 : 1));
        dispatch(fixture);
        assertTrue(manager.isRidingObject(player, restored));
        assertTrue(manager.hasObjectStandingBit(player, restored));
        assertFalse(manager.hasObjectStandingBit(other, restored));
        assertFalse(player.getAir());
        var landed = registry.capture();
        var trajectory = forward(fixture, restored, player, 24);
        manager.removeDynamicObject(restored);
        assertFalse(manager.getActiveObjects().contains(restored));
        registry.restore(landed);
        var recreated = atSlot(slot);
        assertNotSame(restored, recreated);
        assertTrue(manager.isRidingObject(player, recreated));
        assertTrue(manager.hasObjectStandingBit(player, recreated));
        assertFalse(manager.hasObjectStandingBit(other, recreated));
        assertEquals(trajectory, forward(fixture, recreated, player, 24));

        manager.removeDynamicObject(recreated);
        var replacement = new FbzDezPlayerLauncherObjectInstance(
                new ObjectSpawn(0x600, 0x500, 0x78, 0, placementFlags, false, 0));
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertEquals(slot, replacement.getSlotIndex());
        for (var p : players) assertFalse(manager.hasObjectStandingBit(p, replacement));
    }

    private void assertRejected(FbzDezPlayerLauncherObjectInstance launcher,
            AbstractPlayableSprite player, int boundaryY) {
        var manager = GameServices.level().getObjectManager();
        assertFalse(manager.isRidingObject(player, launcher), "native d0 == 0 returns without landing");
        assertFalse(manager.hasObjectStandingBit(player, launcher));
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(boundaryY, player.getCentreY());
        assertEquals(0x3B8, player.getYSpeed());
        assertEquals(0x451, player.getXSpeed());
        assertEquals(0x452, player.getGSpeed());
        assertEquals(0xD700, player.getXSubpixelRaw());
        assertEquals(0xD800, player.getYSubpixelRaw());
    }

    private FbzDezPlayerLauncherObjectInstance atSlot(int slot) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof FbzDezPlayerLauncherObjectInstance launcher && launcher.getSlotIndex() == slot)
                .map(o -> (FbzDezPlayerLauncherObjectInstance) o).findFirst().orElseThrow();
    }

    private List<String> forward(HeadlessTestFixture fixture, FbzDezPlayerLauncherObjectInstance launcher,
            AbstractPlayableSprite player, int count) {
        var rows = new ArrayList<String>();
        var manager = GameServices.level().getObjectManager();
        for (int i = 0; i < count; i++) {
            dispatch(fixture);
            rows.add(launcher.getX() + ":" + player.getCentreX() + ":" + player.getCentreY()
                    + ":" + player.getXSpeed() + ":" + player.getYSpeed() + ":" + player.getGSpeed()
                    + ":" + player.getXSubpixelRaw() + ":" + player.getYSubpixelRaw()
                    + ":" + manager.hasObjectStandingBit(player, launcher) + ":" + player.getAir());
        }
        return rows;
    }

    private void dispatch(HeadlessTestFixture fixture) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(), fixture.sprite(),
                GameServices.sprites().getRegisteredSidekicks(), 0, false, true, true);
    }
}
