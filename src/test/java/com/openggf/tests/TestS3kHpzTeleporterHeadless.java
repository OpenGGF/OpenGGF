package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Obj_SSZHPZTeleporter transport in Hidden Palace ($1601) through the real level loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzTeleporterHeadless {
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
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void lowerTeleporterLiftsSonicToTheUpperPadAndReleasesControl() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .startPosition((short) 0xB40, (short) 0x8B0)
                .startPositionIsCentre()
                .build();
        var sonic = fixture.sprite();

        boolean beamSeen = false;
        boolean transportSeen = false;
        int lowestY = 0;
        for (int frame = 0; frame < 600; frame++) {
            fixture.stepIdleFrames(1);
            beamSeen |= GameServices.level().getObjectManager().getActiveObjects().stream()
                    .anyMatch(TeleporterBeamObjectInstance.class::isInstance);
            var hpz = S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            transportSeen |= hpz.teleporterTransportActive();
            lowestY = sonic.getCentreY() & 0xFFFF;
            if (transportSeen && !hpz.teleporterTransportActive() && !sonic.isObjectControlled()) {
                break;
            }
        }

        assertTrue(beamSeen, "standing on the $4A pad spawns Obj_TeleporterBeam");
        assertTrue(transportSeen, "beam progress 8 raises Events_bg+$04");
        assertFalse(sonic.isObjectControlled(), "loc_4581C releases object_control");
        assertEquals(0xB40, sonic.getCentreX() & 0xFFFF, "loc_456F4 centres the player on the pad");
        assertTrue(lowestY < 0x480, "a $4A rise lifts Sonic $4A0 pixels to the upper pad, was $"
                + Integer.toHexString(lowestY));
    }
}
