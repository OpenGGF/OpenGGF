package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Hidden Palace ($1601) checkpoint/death reload and Knuckles' teleporter exit through the production loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzLifecycleProduction {
    /** Object Pos/1.bin: StarPost $34 subtype 2 at ($CF0,$3E8). */
    private static final int POST_INDEX = 2;
    private static final int POST_X = 0xCF0;
    private static final int POST_Y = 0x3E8;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    static Stream<Arguments> scenarios() {
        var rows = new ArrayList<Arguments>();
        for (String character : new String[]{"sonic", "tails", "knuckles"}) {
            for (int width : new int[]{320, 400, 512, 640, 800}) {
                for (String donor : new String[]{"off", "s1", "s2"}) {
                    if (SozAcceptanceConfigurations.supportsCharacter(donor, character)) {
                        rows.add(Arguments.of(character, width, donor));
                    }
                }
            }
        }
        return rows.stream();
    }

    private static HeadlessTestFixture boot(String character, int width, String donor, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        if (!donor.equals("off")) {
            var rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                    : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom, "required donor ROM");
            config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM
                    : SonicConfiguration.SONIC_2_ROM, rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .startPosition((short) x, (short) y).startPositionIsCentre().withFreshLevelStartLifecycle();
        if (!donor.equals("off")) {
            builder.withCrossGameDonation(donor);
        }
        var fixture = builder.build();
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        assertEquals(width, fixture.camera().getWidth() & 0xFFFF);
        return fixture;
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void touchingThePlacedStarPostThenDyingReloadsAtThePost(String character, int width, String donor) {
        var fixture = boot(character, width, donor, POST_X - 24, POST_Y + 4);
        var manager = GameServices.level().getObjectManager();
        var player = fixture.sprite();
        assertFalse(GameServices.level().getCheckpointState().isActive());
        for (int i = 0; i < 60 && !GameServices.level().getCheckpointState().isActive(); i++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        if (character.equals("knuckles")) {
            // HPZ_ScreenInit sets Knuckles' Camera_max_X_pos to $AA0, so the level's right
            // boundary ($AA0 + 320 - 24) keeps him far left of this Sonic/Tails-route post.
            assertFalse(GameServices.level().getCheckpointState().isActive());
            assertTrue((player.getCentreX() & 0xFFFF) <= 0xAA0 + 320 - 24,
                    "Knuckles is held at the $AA0 camera boundary, was $"
                            + Integer.toHexString(player.getCentreX() & 0xFFFF));
            return;
        }
        assertEquals(POST_INDEX, GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "walking into the placed post must activate it");
        assertTrue(player.applyPitDeath());
        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            boolean reloaded = false;
            for (int i = 0; i < 1200; i++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                if (GameServices.level().getObjectManager() != manager) {
                    reloaded = true;
                    assertEquals(Sonic3kZoneIds.ZONE_HPZ, GameServices.level().getCurrentZone());
                    assertEquals(1, GameServices.level().getCurrentAct());
                    var focused = GameServices.camera().getFocusedSprite();
                    assertEquals(POST_X, focused.getCentreX() & 0xFFFF);
                    assertEquals(POST_Y, focused.getCentreY() & 0xFFFF);
                    assertFalse(focused.getDead());
                    assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
                    assertEquals(character, focused.getCode());
                    SozAcceptanceConfigurations.assertUsableTeam(donor);
                    break;
                }
            }
            assertTrue(reloaded, "the death countdown must reach the GameLoop reload");
        } finally {
            loop.closePresence();
        }
    }

    @Test
    void knucklesUpperTeleporterStartsSkySanctuaryActTwo() {
        var fixture = boot("knuckles", 320, "off", 0xAF0, 0x400);
        var manager = GameServices.level().getObjectManager();
        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int i = 0; i < 60; i++) {
                fixture.stepFrame(false, false, false, false, false);
            }
            for (int i = 0; i < 18; i++) {
                fixture.stepFrame(false, false, false, true, true);
            }
            for (int i = 0; i < 6; i++) {
                fixture.stepFrame(false, false, false, true, false);
            }
            boolean loaded = false;
            for (int i = 0; i < 900 && !loaded; i++) {
                if (GameServices.level().getObjectManager() == manager) {
                    fixture.stepFrame(false, false, false, false, false);
                } else {
                    loaded = true;
                }
                if (!loaded && GameServices.level().getCurrentZone() != Sonic3kZoneIds.ZONE_HPZ) {
                    loaded = true;
                }
                if (!loaded && i > 400) {
                    fixture.gameplayMode().getFadeManager().update();
                    loop.step();
                }
            }
            assertTrue(loaded, "loc_45B94 must start a new level");
            assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct(), "StartNewLevel #$A01");
        } finally {
            loop.closePresence();
        }
    }
}
