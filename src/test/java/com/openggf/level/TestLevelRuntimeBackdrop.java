package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2ZoneFeatureProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelRuntimeBackdrop {
    private static final Palette.Color LEVEL_BACKDROP =
            new Palette.Color((byte) 0x12, (byte) 0x34, (byte) 0x56);

    private LevelManager levelManager;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        levelManager = GameServices.level();
        Level level = mock(Level.class);
        when(level.getBackdropColor()).thenReturn(LEVEL_BACKDROP);
        levelManager.level = level;
        levelManager.zoneFeatureProvider = null;
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void zoneRuntimeStateDefaultsToNotForcingBlackBackdrop() {
        assertFalse(new DefaultRuntimeState().forceBlackBackdrop());
    }

    @Test
    void runtimeStateCanForceBlackBackdrop() {
        GameServices.zoneRuntimeRegistry().install(new BlackBackdropRuntimeState());

        assertBlack(levelManager.resolveLevelBackdropColor());
    }

    @Test
    void legacyMczProviderStillForcesBlackBackdrop() throws IOException {
        GameServices.zoneRuntimeRegistry().clear();
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        provider.initZoneFeatures(null, Sonic2ZoneConstants.ROM_ZONE_MCZ, 0, 0);
        levelManager.zoneFeatureProvider = provider;

        assertBlack(levelManager.resolveLevelBackdropColor());
    }

    private static void assertBlack(Palette.Color color) {
        assertEquals(0, color.r & 0xFF);
        assertEquals(0, color.g & 0xFF);
        assertEquals(0, color.b & 0xFF);
    }

    private static class DefaultRuntimeState implements ZoneRuntimeState {
        @Override public String gameId() { return "test"; }
        @Override public int zoneIndex() { return 0; }
        @Override public int actIndex() { return 0; }
    }

    private static final class BlackBackdropRuntimeState extends DefaultRuntimeState {
        @Override
        public boolean forceBlackBackdrop() {
            return true;
        }
    }
}
