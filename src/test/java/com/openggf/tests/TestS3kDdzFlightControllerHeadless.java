package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Doomsday flight controller {@code loc_81492} through the real level loop from a cold
 * {@code $C00} load. Expected values are the ROM routine's arithmetic ({@code loc_81554},
 * {@code loc_8160A}, {@code loc_8167C}, {@code sub_82920}, {@code sub_829D2}); the native
 * complete-run observations (movie frames 514214+) agree with them from frame 0 except for the
 * camera X fraction the native run inherits from the Death Egg.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzFlightControllerHeadless {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) {
                saved.put(key, config.getConfigValue(key));
            }
        }
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void entryRingAwardRetainsHudDigitsAndReplaysTheLaterDrainRefresh() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0).build();
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 3), true);
        fixture.stepIdleFrames(25);
        var state = GameServices.level().getLevelGamestate();
        assertEquals(50, state.getRings());
        assertEquals(0, com.openggf.game.LevelRingDisplay.value(state),
                "loc_8160A does not request an UpdateHUD ring redraw");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(saved);
            assertEquals(50, state.getRings());
            assertEquals(0, com.openggf.game.LevelRingDisplay.value(state));
            // Native unchanged entry save: Ring_count=50/redraw=0 at24,
            // Ring_count=49/redraw=1 at85, redraw consumed at86.
            fixture.stepIdleFrames(59);
            assertEquals(50, state.getRings(), "native pass84");
            assertEquals(0, com.openggf.game.LevelRingDisplay.value(state));
            fixture.stepIdleFrames(1);
            assertEquals(49, state.getRings(), "native first powered-form drain at85");
            assertEquals(0, com.openggf.game.LevelRingDisplay.value(state));
            fixture.stepIdleFrames(1);
            assertEquals(49, com.openggf.game.LevelRingDisplay.value(state), "native HUD refresh at86");
        }
    }

    /**
     * The fixture's level load runs {@code loc_81554} in the initial object pass, so after
     * {@code n} {@code fixture.stepIdleFrames} calls the state is ROM frame {@code n} of the native
     * observation (movie frame 514214 + n).
     */
    @Test
    void introFlightTransformationAndReleaseFollowTheControllerRoutines() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var sonic = fixture.sprite();
        var ddz = S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElseThrow();

        // ROM frame 1: loc_81554 put the controller at Camera_X - $20, Camera_Y + $20 and the
        // camera moved $1.0800; frame 1 adds MoveSprite's $400 and a $1.1000 camera step.
        fixture.stepIdleFrames(1);
        assertEquals(0, GameServices.sprites().getSidekicks().size(), "loc_81554 clears Player_2");
        assertTrue(sonic.isObjectControlled(), "object_control = $81");
        assertEquals(0xFFE6, sonic.getCentreX() & 0xFFFF, "native x at ROM frame 1");
        assertEquals(0xC0, sonic.getCentreY() & 0xFFFF, "Camera_Y $A0 + $20");
        assertEquals(0x11000, ddz.scrollSpeed(), "_unkFA82 after two $800 steps");

        // ROM frames 1..23: loc_8160A counts $17 down; frame 24 transforms.
        fixture.stepIdleFrames(22);
        assertFalse(sonic.isSuperSonic(), "still falling in at ROM frame 23");
        int ringsBefore = sonic.getRingCount();
        fixture.stepIdleFrames(1);
        assertTrue(sonic.isSuperSonic(), "loc_8160A transforms at ROM frame 24");
        assertEquals(ringsBefore + 50, sonic.getRingCount(), "addi.w #50,(Ring_count).w");

        // SuperHyper_PalCycle: Palette_timer $F then six 2-frame fade steps; the controller sees
        // object_control clear at ROM frame 50 (native observation: routine 4 -> 6 at frame 50).
        fixture.stepIdleFrames(25);
        assertTrue(sonic.isObjectControlled(), "still transforming at ROM frame 49");
        fixture.stepIdleFrames(1);
        assertTrue(isHyper(sonic), "seven Super Emeralds: sub_5FCCE Hyper form");
        assertEquals(260, sonic.getCentreX() & 0xFFFF, "native x at ROM frame 50");
        assertEquals(260, sonic.getCentreY() & 0xFFFF, "native y at ROM frame 50");
        fixture.stepIdleFrames(1);
        assertEquals(263, sonic.getCentreX() & 0xFFFF, "native x at ROM frame 51 (camera delta only)");
    }

    private static boolean isHyper(com.openggf.sprites.playable.AbstractPlayableSprite sonic) {
        return sonic.getSuperStateController() != null && sonic.getSuperStateController().isHyperFormActive();
    }
}
