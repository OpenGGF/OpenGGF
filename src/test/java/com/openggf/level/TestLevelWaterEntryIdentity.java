package com.openggf.level;

import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Water eligibility uses saved identity, including a fresh load after a bonus. */
class TestLevelWaterEntryIdentity {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void freshWaterSelectionUsesApparentActRatherThanAssumingDirectEntry(boolean retainedAct) throws Exception {
        TestEnvironment.resetAll();
        var manager = GameServices.level();
        var module = mock(GameModule.class);
        var provider = mock(WaterDataProvider.class);
        when(module.getWaterDataProvider()).thenReturn(provider);
        manager.gameModule = module;
        manager.level = mock(Level.class);
        when(manager.level.getZoneIndex()).thenReturn(0);
        when(module.getRemappedFeatureAct(anyInt(), anyInt(), anyInt())).thenReturn(-1);
        manager.waterSystem = mock(WaterSystem.class);
        manager.writeCurrentZone(0);
        manager.writeCurrentAct(1);
        manager.writeApparentAct(retainedAct ? 0 : 1);

        manager.initWater();

        verify(manager.waterSystem).loadForLevelFromProvider(eq(provider), nullable(Rom.class),
                eq(0), eq(1), any(PlayerCharacter.class), eq(retainedAct));
    }
}
