package com.openggf.game.sonic2;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * s2-oil-hops: 600M/1198S, 1779M/3101S and 2037M/3557S, OOZ1/2 Sonic.
 * Isolates the falling-to-oil momentum transfer needed between hops, not the
 * under-pillar trajectory or OOZ2 boss-area warp. Native oracle:
 * Obj07_Main (s2.asm:50145-50203), PlatformObject_ChkYRange (s2.asm:35968-35984),
 * RideObject_SetRide (s2.asm:35986-36040), pinned at 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7.
 */
class TestTasS2OilMomentum {
    private static final int TOP = 0x758 - 0x30;
    private OilSurfaceManager oil;
    private TestablePlayableSprite player;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        oil = new OilSurfaceManager();
        player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {0xC00, -0xC00})
    void rollingOilLandingConvertsHorizontalMomentumToRunningInertia(int speed) {
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setAir(true);
        player.setCentreY((short) (TOP + 1 - player.getYRadius()));
        player.setXSpeed((short) speed);
        player.setGSpeed((short) 0x123); // detect failure to copy x_vel
        player.setYSpeed((short) 0x200);
        player.setSubpixelRaw(0x3400, 0x5600);

        oil.updateSurface(player);

        assertTrue(oil.isStandingOnOil(player), "must reach RideObject_SetRide");
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
        assertFalse(player.getRolling());
        assertEquals(Sonic2AnimationIds.WALK.id(), player.getAnimationId());
        assertEquals(speed, player.getXSpeed());
        assertEquals(speed, player.getGSpeed(), "no run-speed clamp at the oil contact");
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getAngle());
        assertEquals(0x5600, player.getYSubpixelRaw(), "word y_pos writes preserve fraction");

        oil.updateSurface(player);
        assertEquals(0x2F, oil.getSubmersion(player));
        assertEquals(speed, player.getXSpeed());
        assertEquals(speed, player.getGSpeed(), "MvSonicOnPtfm cannot consume the stored momentum");
    }

    @ParameterizedTest
    @CsvSource({"-4,512,false", "-3,512,true", "12,512,true", "13,512,false", "1,-1,false"})
    void contactWindowAndAscendingControlRejectFalseOilLandings(int feetOffset, int ySpeed,
                                                               boolean lands) {
        player.setAir(true);
        player.setCentreY((short) (TOP + feetOffset - player.getYRadius()));
        player.setXSpeed((short) 0xC00);
        player.setGSpeed((short) 0x123);
        player.setYSpeed((short) ySpeed);
        oil.updateSurface(player);

        assertEquals(lands, oil.isStandingOnOil(player));
        assertEquals(!lands, player.getAir());
        assertEquals(lands ? 0 : ySpeed, player.getYSpeed());
        assertEquals(lands ? 0xC00 : 0x123, player.getGSpeed(),
                "PlatformObject accepts signed distance -16..-1; rising players never land");
    }
}
