package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Player code never clears Status_OnObj; only the object that owns the ride does. Over
 * empty ICZ2 terrain with the bit set and no object, Player_AnglePos returns before any
 * floor probe (loc_EC42, sonic3k.asm:18771-18777), so Sonic stays where he is.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIcz2StaleObjectGrounding {
    private static final int SCREENSHOT_TOP_LEFT_X = 2137;
    private static final int SCREENSHOT_TOP_LEFT_Y = 797;

    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private AbstractPlayableSprite player;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_ICZ, 1);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        player = fixture.sprite();
    }

    @Test
    void ownerlessObjectStatusSkipsTheFloorProbeInIcz2EmptyAir() {
        player.setCentreX((short) (SCREENSHOT_TOP_LEFT_X + player.getXRadius()));
        player.setCentreY((short) (SCREENSHOT_TOP_LEFT_Y + player.getYRadius()));
        player.setAir(false);
        player.setOnObject(true);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) 0);
        int x = player.getCentreX();
        int y = player.getCentreY();

        fixture.stepFrame(false, false, false, false, false);

        assertFalse(player.getAir(), "No object released Sonic, so Status_InAir stays clear");
        assertTrue(player.isOnObject(), "Only the owning object clears Status_OnObj");
        assertEquals(x, player.getCentreX(), "Sonic does not move at rest");
        assertEquals(y, player.getCentreY(), "Player_AnglePos returns before the floor probe");
    }
}
