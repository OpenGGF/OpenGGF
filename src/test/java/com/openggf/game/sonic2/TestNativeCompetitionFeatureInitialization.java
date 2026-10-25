package com.openggf.game.sonic2;

import com.openggf.camera.Camera;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestNativeCompetitionFeatureInitialization {
    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); GraphicsManager.getInstance().resetState(); }

    @Test void sixNativeProfilesOmitOnlyTheOrdinaryBumperModelAndKeepTheSingleReelManager() throws Exception {
        var f = fixture();
        var rom = TestEnvironment.currentRom();
        for (int zone : new int[]{0, 11, 12}) {
            for (int act = 0; act < 2; act++) {
                Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                        f.provider, f.context, f.roster, rom, zone, act, 0);
                assertNull(bumpers(f.provider));
                if (zone == 12) assertNotNull(f.provider.getSlotMachineManager());
                else assertNull(f.provider.getSlotMachineManager());
            }
        }
        assertEquals(6, f.provider.resets);
        var ordinary = new CountingProvider();
        ordinary.initZoneFeatures(rom, 12, 0, 0);
        assertNotNull(bumpers(ordinary), "the ordinary initializer retains its ROM bumper model");
        assertNotNull(ordinary.getSlotMachineManager());
    }

    @Test void foreignRosterProviderProfileAndCpuFailBeforeAnyReset() throws Exception {
        var f = fixture();
        var rom = TestEnvironment.currentRom();
        var foreign = new Sonic2CompetitionSession();
        assertThrows(IllegalArgumentException.class, () -> Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                f.provider, f.context, foreign, rom, 0, 0, 0));
        var unrelated = new CountingProvider();
        assertThrows(IllegalArgumentException.class, () -> Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                unrelated, f.context, f.roster, rom, 0, 0, 0));
        for (int[] invalid : new int[][]{{1, 0}, {0, -1}, {12, 2}})
            assertThrows(IllegalArgumentException.class, () -> Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                    f.provider, f.context, f.roster, rom, invalid[0], invalid[1], 0));
        f.roster.player(1).setCpuControlled(true);
        assertThrows(IllegalArgumentException.class, () -> Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                f.provider, f.context, f.roster, rom, 0, 0, 0));
        assertEquals(0, f.provider.resets);
        assertEquals(0, unrelated.resets);
    }

    private static Fixture fixture() throws Exception {
        var roster = new Sonic2CompetitionSession();
        roster.bindPlayers(new Sonic("sonic", (short) 0, (short) 0), new Tails("tails", (short) 0, (short) 0));
        roster.bindCameras(new Camera(), new Camera());
        var provider = new CountingProvider();
        var policies = mock(WorldSessionPolicyState.class);
        when(policies.getService(NativeCompetitionRoster.class)).thenReturn(roster);
        doReturn(roster).when(policies).rewindAdapter();
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-feature-init") {
            @Override public com.openggf.game.ZoneFeatureProvider getZoneFeatureProvider() { return provider; }
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> policies) : super.getGameService(type);
            }
        };
        var context = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(context, EngineServices.current());
        return new Fixture(provider, context, roster);
    }

    private static Object bumpers(Sonic2ZoneFeatureProvider provider) throws Exception {
        Field field = Sonic2ZoneFeatureProvider.class.getDeclaredField("cnzBumperManager");
        field.setAccessible(true);
        return field.get(provider);
    }
    private record Fixture(CountingProvider provider, GameplayModeContext context, Sonic2CompetitionSession roster) { }
    private static final class CountingProvider extends Sonic2ZoneFeatureProvider {
        int resets;
        @Override public void reset() { resets++; super.reset(); }
    }
}
