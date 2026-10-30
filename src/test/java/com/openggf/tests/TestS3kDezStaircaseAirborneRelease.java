package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezStaircaseObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.CollisionTrace;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real P1/P2 standing ownership through moving parent/child checkpoints and recreation. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezStaircaseAirborneRelease {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,0,false", "1,0,false", "0,1,false", "1,1,false",
            "0,0,true", "1,0,true", "0,1,true", "1,1,true"})
    void movingSectionConsumesOnlyItsOwnAirborneStandingBit(
            int participant, int section, boolean reverse) {
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
        var earlier = create(0x500, 0);
        var root = create(0x600, 5);
        dispatch(fixture);
        var family = family(root);
        assertEquals(4, family.size());
        var ridden = family.get(section);
        int rootSlot = root.getSlotIndex(), slot = ridden.getSlotIndex();
        assertTrue(earlier.getSlotIndex() < slot, "unrelated solid dispatches before ridden section");
        NativePositionOps.writeXPosResetSubpixel(other, 0x480);
        NativePositionOps.writeYPosResetSubpixel(other, 0x400);
        other.setAir(true);
        player.setRenderFlagOnScreen(true);
        player.setRolling(false);
        player.setAir(true);
        player.setOnObject(false);
        // Native y_vel is gravity-relative; loc_1E17E tests its sign
        // unchanged even when Reverse_gravity_flag selects the opposite face.
        player.setYSpeed((short) 0x100);
        NativePositionOps.writeXPosResetSubpixel(player, ridden.getX());
        int reach = 0x10 + player.getYRadius();
        NativePositionOps.writeYPosResetSubpixel(player, ridden.getY() + (reverse ? reach : -reach));
        dispatch(fixture);
        assertTrue(manager.isRidingObject(player, ridden), "real new contact establishes support");
        assertTrue(manager.hasObjectStandingBit(player, ridden));
        assertFalse(manager.hasObjectStandingBit(other, ridden));
        assertFalse(player.getAir());

        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var held = registry.capture();
        // Component-entry jump state, independent of movie values: the smaller
        // rolling radius moves the center five pixels, as native Jump does.
        player.setRolling(true);
        NativePositionOps.writeYPosPreserveSubpixel(player,
                player.getCentreY() + (reverse ? -5 : 5));
        player.setAir(true);
        player.setYSpeed((short) -0x680);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setSubpixelRaw(0x1200, 0x3400);
        int jumpY = player.getCentreY();
        var jumping = registry.capture();
        var first = release(fixture, player, other, earlier, ridden, jumpY);

        for (var part : family) manager.removeDynamicObject(part);
        registry.restore(jumping);
        var restoredRoot = atSlot(rootSlot);
        var restored = atSlot(slot);
        assertNotSame(root, restoredRoot);
        assertNotSame(ridden, restored);
        if (section != 0) assertSame(restoredRoot, restored.parentForTest());
        assertEquals(first, release(fixture, player, other, earlier, restored, jumpY));

        // A held support restores too; its moving pose must carry the rider.
        for (var part : family(restoredRoot)) manager.removeDynamicObject(part);
        registry.restore(held);
        var heldRoot = atSlot(rootSlot);
        var heldSection = atSlot(slot);
        assertTrue(manager.isRidingObject(player, heldSection));
        assertTrue(manager.hasObjectStandingBit(player, heldSection));
        int beforeY = player.getCentreY(), beforeSectionY = heldSection.getY();
        dispatch(fixture);
        assertFalse(player.getAir());
        assertEquals(heldSection.getY() - beforeSectionY, player.getCentreY() - beforeY);
        assertTrue(manager.hasObjectStandingBit(player, heldSection));

        for (var part : family(heldRoot)) manager.removeDynamicObject(part);
        var replacement = new S3kDezStaircaseObjectInstance(
                new ObjectSpawn(0x600, 0x500, 0x4F, 0, 0, false, 0));
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertEquals(slot, replacement.getSlotIndex());
        for (var p : players) assertFalse(manager.hasObjectStandingBit(p, replacement));
    }

    private List<String> release(HeadlessTestFixture fixture, AbstractPlayableSprite player,
            AbstractPlayableSprite other, S3kDezStaircaseObjectInstance earlier,
            S3kDezStaircaseObjectInstance ridden, int jumpY) {
        var manager = GameServices.level().getObjectManager();
        var checkpoints = new ArrayList<String>();
        var originalTrace = GameServices.collision().getTrace();
        var trace = (CollisionTrace) Proxy.newProxyInstance(CollisionTrace.class.getClassLoader(),
                new Class<?>[] {CollisionTrace.class}, (proxy, method, args) -> {
                    if (method.getName().equals("onSolidCheckpointStart")
                            && args[0].equals("S3kDezStaircaseObjectInstance")
                            && ((int) args[1] == earlier.getX() || (int) args[1] == ridden.getX())) {
                        boolean own = (int) args[1] == ridden.getX();
                        checkpoints.add((own ? "own:" : "earlier:") + player.isOnObject() + ":"
                                + manager.isRidingObject(player, ridden) + ":"
                                + manager.hasObjectStandingBit(player, ridden));
                        assertTrue(player.isOnObject(), "support remains until its own checkpoint");
                        assertTrue(manager.isRidingObject(player, ridden));
                        assertTrue(manager.hasObjectStandingBit(player, ridden),
                                "moving spawn retains the section's native standing bit");
                    }
                    return method.getReturnType() == List.class ? List.of() : null;
                });
        GameServices.collision().setTrace(trace);
        try {
            dispatch(fixture);
        } finally {
            GameServices.collision().setTrace(originalTrace);
        }
        assertEquals(List.of("earlier:true:true:true", "own:true:true:true"), checkpoints);
        assertEquals(jumpY, player.getCentreY(), "airborne stale-rider branch must not run the new-contact lift");
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertFalse(player.isOnObject());
        assertFalse(manager.isRidingObject(player, ridden));
        assertFalse(manager.hasObjectStandingBit(player, ridden));
        assertFalse(manager.hasObjectStandingBit(other, ridden));
        assertEquals(0x680, Math.abs(player.getYSpeed()));
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(0x1200, player.getXSubpixelRaw());
        assertEquals(0x3400, player.getYSubpixelRaw());
        checkpoints.add(ridden.getY() + ":" + jumpY + ":" + player.getYSpeed());
        return checkpoints;
    }

    private S3kDezStaircaseObjectInstance create(int x, int subtype) {
        var object = new S3kDezStaircaseObjectInstance(new ObjectSpawn(x, 0x500, 0x4F, subtype, 0, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private List<S3kDezStaircaseObjectInstance> family(S3kDezStaircaseObjectInstance root) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o == root || o instanceof S3kDezStaircaseObjectInstance s && s.parentForTest() == root)
                .map(o -> (S3kDezStaircaseObjectInstance) o)
                .sorted(Comparator.comparingInt(S3kDezStaircaseObjectInstance::getX)).toList();
    }

    private S3kDezStaircaseObjectInstance atSlot(int slot) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezStaircaseObjectInstance staircase && staircase.getSlotIndex() == slot)
                .map(o -> (S3kDezStaircaseObjectInstance) o).findFirst().orElseThrow();
    }

    private void dispatch(HeadlessTestFixture fixture) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(), fixture.sprite(),
                GameServices.sprites().getRegisteredSidekicks(), 0, false, true, true);
    }
}
