package com.openggf.game.sonic2.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * s2-spindash-storage: TASVideos 3339M / 5385S (HTZ seesaw storage), with the
 * WFZ slider variant in 600M / 1198S explicitly outside this component test.
 *
 * <p>Native entry: a rider charges on the right end while the ball descends onto
 * the left. The fixture seeds that pre-contact SST state; public ball update
 * must actually cross the contact plane and execute the launch. It does not
 * claim to reproduce the publication's approach, charge inputs or later release.
 * Oracle: Obj14_Ball_Fly (s2.asm:47667-47725),
 * Obj14_LaunchCharacter (s2.asm:47732-47747), pin 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7.
 */
class TestTasS2StoredSpindash {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void descendingBallLaunchPreservesTheIncomingCharge(boolean charging) throws Exception {
        Fixture fixture = fixture(charging, 0);
        fixture.ball.update(1, fixture.player);

        assertTrue(fixture.player.getAir(), "ball contact must reach the launch branch");
        assertFalse(fixture.player.isOnObject());
        assertFalse(fixture.player.isJumping());
        assertEquals(-0x638, fixture.player.getYSpeed(),
                "ObjectMoveAndFall adds $38 before Obj14 negates the ball's $600 velocity");
        assertEquals(Sonic2AnimationIds.SPRING.id(), fixture.player.getAnimationId());
        assertEquals(charging, fixture.player.getSpindash(),
                "fixBugs=0 omits clr.b spindash_flag; an uncharged rider must stay uncharged");
        assertEquals(charging ? 0x400 : 0, fixture.player.getSpindashCounter());
        assertEquals(0x580, fixture.player.getXSpeed(), "launch writes no x_vel");
        assertEquals(0x640, fixture.player.getGSpeed(), "launch writes no inertia");
        assertTrue(fixture.parent.getStandingPlayersInNativeOrder().isEmpty(),
                "the native standing latch must be consumed, preventing a second launch");
        fixture.ball.update(2, fixture.player);
        assertEquals(-0x638, fixture.player.getYSpeed(), "resting ball must not relaunch the former rider");
    }

    @Test
    void landingWithoutAVisualTiltChangeDoesNotLaunchTheChargingRider() throws Exception {
        Fixture fixture = fixture(true, 2);
        fixture.ball.update(1, fixture.player);

        assertFalse(fixture.player.getAir());
        assertTrue(fixture.player.isOnObject());
        assertEquals(0, fixture.player.getYSpeed());
        assertTrue(fixture.player.getSpindash());
        assertEquals(0x400, fixture.player.getSpindashCounter());
        assertSame(fixture.player, fixture.parent.getStandingPlayer1(),
                "loc_21C1E skips launch and standing-bit clear when mapping_frame already equals 2");
    }

    private static Fixture fixture(boolean charging, int parentFrame) throws Exception {
        var parent = new SeesawObjectInstance(
                new ObjectSpawn(0x2340, 0x0770, 0x14, 0, 0, false, 0), "Seesaw");
        parent.setServices(new TestObjectServices());
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x2368);
        player.setCentreY((short) 0x0740);
        player.setAir(false);
        player.setOnObject(true);
        player.setSpindash(charging);
        player.setSpindashCounter((short) (charging ? 0x400 : 0));
        player.setXSpeed((short) 0x580);
        player.setGSpeed((short) 0x640);
        setField(parent, "mappingFrame", parentFrame);
        setField(parent, "standingPlayer1", player);
        // Left contact height is bottom-47 for mapping 0, bottom-8 for mapping 2.
        int contactY = 0x0780 - (parentFrame == 0 ? 47 : 8);
        var ball = new SeesawBallObjectInstance(0x2340, 0x0780,
                0x2318, contactY - 1, parent, false);
        ball.setServices(new TestObjectServices());
        setField(ball, "xVel", -0xCC);
        setField(ball, "yVel", 0x600);
        Field state = SeesawBallObjectInstance.class.getDeclaredField("state");
        state.setAccessible(true);
        for (Object candidate : state.getType().getEnumConstants()) {
            if (candidate.toString().equals("FLYING")) state.set(ball, candidate);
        }
        return new Fixture(parent, ball, player);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private record Fixture(SeesawObjectInstance parent, SeesawBallObjectInstance ball,
                           TestablePlayableSprite player) { }
}
