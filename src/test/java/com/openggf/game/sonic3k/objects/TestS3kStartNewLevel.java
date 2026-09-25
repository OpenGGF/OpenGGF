package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.save.SaveReason;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.Mockito.*;

class TestS3kStartNewLevel {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }
    @AfterEach void resetConfig() {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
    }


    @ParameterizedTest
    @CsvSource({"-17,0,false", "-16,0,true", "15,0,true", "16,0,false",
            "0,-129,false", "0,-128,true", "0,127,true", "0,128,false"})
    void triggerUsesTheRomHalfOpenRectangle(int dx, int dy, boolean enters) {
        var services = mock(ObjectServices.class);
        var object = new S3kStartNewLevelObjectInstance(new ObjectSpawn(0x3FE0, 0xE0, 0xB3, 0x2D, 0, false, 0));
        object.setServices(services);
        var player = new TestablePlayableSprite("sonic", (short)0, (short)0);
        player.setCentreX((short)(0x3FE0 + dx)); player.setCentreY((short)(0xE0 + dy));
        object.update(0, player); object.update(1, player);
        verify(services, times(enters ? 1 : 0)).requestZoneAndAct(0x16, 1, true);
        verify(services, never()).requestSessionSave(any());
    }

    @ParameterizedTest
    @CsvSource({"sonic,9,false", "tails,9,false", "knuckles,9,true", "knuckles,10,false"})
    void saveGateDoesNotGateTheTransition(String character, int zone, boolean saves) {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var services = mock(ObjectServices.class);
        when(services.configuration()).thenReturn(config); when(services.currentZone()).thenReturn(zone);
        var object = new S3kStartNewLevelObjectInstance(new ObjectSpawn(0x100, 0x100, 0xB3, 0x2C, 0, false, 0));
        object.setServices(services);
        var player = new TestablePlayableSprite(character, (short)0, (short)0);
        player.setCentreX((short)0x100); player.setCentreY((short)0x100);
        object.update(0, player);
        verify(services).requestZoneAndAct(0x16, 0, true);
        verify(services, times(saves ? 1 : 0)).requestSessionSave(SaveReason.PROGRESSION_SAVE);
    }
}
