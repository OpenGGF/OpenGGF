package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezGravityRoomObjectInstance;
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

/** The turbine's own MoveSprite2/terrain call, using the real DEZ1 ceiling. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityRoomTerrain {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,320", "1,320", "0,800", "1,800"})
    void capturedSlotChecksTheCeilingAfterGroundedActivationAndRecreation(int participant, int width) {
        var fixture = fixture(width);
        var manager = GameServices.level().getObjectManager();
        var room = new S3kDezGravityRoomObjectInstance(
                new ObjectSpawn(0x2480, 0x840, 0x5F, 0, 0, false, 0x840, -1));
        manager.addDynamicObject(room);
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var player = players.get(participant);
        var other = players.get(1 - participant);
        position(other, 0x2400, 0x840);
        position(player, 0x27C0, 0x7F1 + player.getYRadius());
        player.setRolling(false);
        player.setAir(false);
        player.setGroundMode(GroundMode.GROUND);
        player.setGSpeed((short) 0);
        player.updateSensors(player.getX(), player.getY());
        assertFalse(player.getCeilingSensors()[0].isActive(), "grounded preparation skips the ceiling");
        room.update(0, fixture.sprite());
        assertTrue(room.isCapturedForTest(participant == 0));
        assertTrue(player.isObjectControlled());
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) 0);
        player.setSubpixelRaw(0x4500, 0x6700);
        var otherBefore = state(other);
        int freeY = player.getCentreY();
        int slot = room.getSlotIndex();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var boundary = registry.capture();

        room.update(1, fixture.sprite());
        assertTrue(player.getCentreY() > freeY,
                "the room's right-moving native branch must separate the real ceiling overlap");
        assertEquals(0x6700, player.getYSubpixelRaw(), "native ceiling separation writes only the Y word");
        assertTrue(player.getAir());
        assertTrue(player.isObjectControlled());
        assertEquals(otherBefore, state(other), "the uncaptured native slot is untouched");
        var contact = state(player);
        var forward = forward(room, fixture, player);

        manager.removeDynamicObject(room);
        registry.restore(boundary);
        var recreated = manager.getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezGravityRoomObjectInstance r && r.getSlotIndex() == slot)
                .map(o -> (S3kDezGravityRoomObjectInstance) o).findFirst().orElseThrow();
        assertNotSame(room, recreated);
        assertTrue(recreated.isCapturedForTest(participant == 0));
        recreated.update(1, fixture.sprite());
        assertEquals(contact, state(player), "fresh controller repeats the same ceiling contact");
        assertEquals(forward, forward(recreated, fixture, player));
        assertEquals(otherBefore, state(other));

        position(player, 0x2980, 0x840);
        recreated.update(30, fixture.sprite());
        assertFalse(player.isObjectControlled(), "native unsigned corridor exit releases control");
        assertFalse(recreated.isCapturedForTest(participant == 0));
        manager.removeDynamicObject(recreated);
        var replacement = new S3kDezGravityRoomObjectInstance(room.getSpawn());
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertFalse(replacement.isCapturedForTest(true));
        assertFalse(replacement.isCapturedForTest(false));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "0,true", "1,true"})
    void unobstructedAndAlreadyActiveCeilingControls(int participant, boolean ceiling) {
        var fixture = fixture(320);
        var room = new S3kDezGravityRoomObjectInstance(
                new ObjectSpawn(0x2480, 0x840, 0x5F, 0, 0, false, 0x840, -1));
        GameServices.level().getObjectManager().addDynamicObject(room);
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var player = players.get(participant);
        position(players.get(1 - participant), 0x2400, 0x840);
        position(player, ceiling ? 0x27C0 : 0x2500, ceiling ? 0x7F1 + player.getYRadius() : 0x840);
        player.setAir(true);
        player.setRolling(false);
        player.setGroundMode(GroundMode.GROUND);
        player.setXSpeed((short) 0x400);
        player.setYSpeed((short) 0);
        player.updateSensors(player.getX(), player.getY());
        room.update(0, fixture.sprite());
        int freeY = player.getCentreY();
        room.update(1, fixture.sprite());
        if (ceiling) assertTrue(player.getCentreY() > freeY, "active-probe control reaches real terrain");
        else assertEquals(freeY, player.getCentreY(), "clear space preserves the integration result");
        assertEquals(0x438, player.getXSpeed() & 0xFFFF, "ordinary turbine acceleration is preserved");
        assertTrue(player.isObjectControlled());
        assertTrue(player.getAir());
    }

    /** loc_12158 (sonic3k.asm:24352-24357) resets velocity and TouchFloor before the room reasserts air. */
    @ParameterizedTest
    @CsvSource({"0,320", "1,320", "0,800", "1,800"})
    void directFloorLandingResetsVelocityAndTumbleAfterRecreation(int participant, int width) {
        var fixture = fixture(width);
        var manager = GameServices.level().getObjectManager();
        var room = new S3kDezGravityRoomObjectInstance(
                new ObjectSpawn(0x2480, 0x840, 0x5F, 0, 0, false, 0x840, -1));
        manager.addDynamicObject(room);
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var player = players.get(participant);
        var other = players.get(1 - participant);
        position(other, 0x2400, 0x840);
        // Positioned component inlet against real DEZ1 terrain; no trace state hydration.
        position(player, 0x275B, 0x8C5 - player.getYRadius());
        player.setRolling(false);
        player.setAir(true);
        player.setGroundMode(GroundMode.GROUND);
        room.update(0, fixture.sprite());
        player.setXSpeed((short) 0xF90);
        player.setYSpeed((short) 0x61);
        player.setGSpeed((short) -1);
        player.setDirectionalInputPressed(true, false, false, false);
        player.setSubpixelRaw(0xB92C, 0x95E8);
        player.setPushing(true);
        player.setRollingJump(true);
        player.setJumping(true);
        player.setFlipType(1);
        player.setLookDelayCounter((short) 7);
        var otherBefore = state(other);
        int slot = room.getSlotIndex();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var boundary = registry.capture();

        room.update(1, fixture.sprite());
        assertEquals(0, player.getYSpeed(), "native direct floor landing zeros vertical velocity");
        assertEquals(0xFC8, player.getGSpeed() & 0xFFFF, "inertia samples horizontal velocity");
        assertEquals(0xFC8, player.getXSpeed() & 0xFFFF);
        assertEquals(0x812C, player.getXSubpixelRaw());
        assertEquals(0xDCE8, player.getYSubpixelRaw(), "terrain separation preserves fraction");
        assertEquals(0xF8, player.getAngle() & 0xFF);
        assertTrue(player.getAir(), "loc_49730 immediately restores InAir after TouchFloor");
        assertTrue(player.isObjectControlled());
        assertFalse(player.getPushing());
        assertFalse(player.isJumping());
        assertFalse(player.getRollingJump());
        assertEquals(0, player.getFlipAngle());
        assertEquals(0, player.getFlipType());
        assertEquals(0, player.getFlipsRemaining());
        assertEquals(0, player.getLookDelayCounter());
        assertEquals(otherBefore, state(other));
        var contact = state(player);
        var after = forward(room, fixture, player);

        manager.removeDynamicObject(room);
        registry.restore(boundary);
        var recreated = manager.getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezGravityRoomObjectInstance r && r.getSlotIndex() == slot)
                .map(o -> (S3kDezGravityRoomObjectInstance) o).findFirst().orElseThrow();
        assertNotSame(room, recreated);
        recreated.update(1, fixture.sprite());
        assertEquals(contact, state(player));
        assertEquals(0, player.getFlipAngle());
        assertEquals(after, forward(recreated, fixture, player));
        assertEquals(otherBefore, state(other));
        position(player, 0x2980, 0x840);
        recreated.update(30, fixture.sprite());
        assertFalse(player.isObjectControlled());
        manager.removeDynamicObject(recreated);
        var replacement = new S3kDezGravityRoomObjectInstance(room.getSpawn());
        manager.addDynamicObjectAtSlot(replacement, slot);
        assertFalse(replacement.isCapturedForTest(true));
        assertFalse(replacement.isCapturedForTest(false));
    }

    @ParameterizedTest
    @CsvSource({"0", "1"})
    void directFloorLandingDoesNotCompleteCapturedHurtRecovery(int participant) {
        var fixture = fixture(320);
        var room = new S3kDezGravityRoomObjectInstance(
                new ObjectSpawn(0x2480, 0x840, 0x5F, 0, 0, false, 0x840, -1));
        GameServices.level().getObjectManager().addDynamicObject(room);
        var players = List.of(fixture.sprite(), GameServices.sprites().getRegisteredSidekicks().getFirst());
        var player = players.get(participant);
        position(players.get(1 - participant), 0x2400, 0x840);
        position(player, 0x275B, 0x8C5 - player.getYRadius());
        player.setRolling(false);
        player.setAir(true);
        player.setGroundMode(GroundMode.GROUND);
        room.update(0, fixture.sprite());
        player.setXSpeed((short) 0xF90);
        player.setYSpeed((short) 0x61);
        player.setDirectionalInputPressed(true, false, false, false);
        player.setSubpixelRaw(0xB92C, 0x95E8);
        player.setHurt(true);
        player.setInvulnerableFrames(0x37);
        int routine = com.openggf.trace.TraceCharacterState.routineFromSprite(player);
        assertTrue(player.isObjectControlled());

        room.update(1, fixture.sprite());
        assertEquals(0, player.getYSpeed(), "the direct floor callback actually ran");
        assertTrue(player.getAir());
        assertTrue(player.isHurt());
        assertTrue(player.isObjectControlled());
        assertEquals(0x37, player.getInvulnerableFrames());
        assertEquals(routine, com.openggf.trace.TraceCharacterState.routineFromSprite(player));
        assertTrue(room.isCapturedForTest(participant == 0));
        room.update(2, fixture.sprite());
        assertTrue(player.isHurt());
        assertTrue(player.isObjectControlled());
        assertEquals(0x37, player.getInvulnerableFrames());
        assertEquals(routine, com.openggf.trace.TraceCharacterState.routineFromSprite(player));
    }

    private HeadlessTestFixture fixture(int width) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name() : WidescreenAspect.SUPER_32_9.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 0).build();
        assertEquals(width, fixture.camera().getWidth());
        return fixture;
    }

    private void position(AbstractPlayableSprite player, int x, int y) {
        NativePositionOps.writeXPosResetSubpixel(player, x);
        NativePositionOps.writeYPosResetSubpixel(player, y);
    }

    private List<Integer> state(AbstractPlayableSprite p) {
        return List.of((int) p.getCentreX(), (int) p.getCentreY(), p.getXSubpixelRaw(), p.getYSubpixelRaw(),
                (int) p.getXSpeed(), (int) p.getYSpeed(), (int) p.getGSpeed(), p.getAir() ? 1 : 0,
                p.isObjectControlled() ? 1 : 0);
    }

    private List<List<Integer>> forward(S3kDezGravityRoomObjectInstance room,
                                        HeadlessTestFixture fixture, AbstractPlayableSprite player) {
        var result = new ArrayList<List<Integer>>();
        for (int i = 2; i < 26; i++) {
            room.update(i, fixture.sprite());
            result.add(state(player));
        }
        return result;
    }
}
