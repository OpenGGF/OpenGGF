package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hidden Palace sanctuary ({@code $1701}) and playable Hidden Palace ({@code $1601})
 * installs a runtime state that owns the {@code Screen_shake_flag} countdown;
 * the paired Death Egg boss act ({@code $1700}) has its own independent owner.
 */
class TestSonic3kHpzRuntimeStateRegistration {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void initLevelInstallsHpzRuntimeStateForSanctuaryAndHiddenPalace() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

        manager.initLevel(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 1);
        ZoneRuntimeState current = GameServices.zoneRuntimeRegistry().current();
        assertInstanceOf(HpzZoneRuntimeState.class, current);
        assertEquals(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, current.zoneIndex());
        assertEquals(1, current.actIndex());
        assertEquals("s3k", current.gameId());
        manager.ensureZoneRuntimeStateInstalled();
        assertEquals(current, GameServices.zoneRuntimeRegistry().current(),
                "the sanctuary state is recognized as current for its own zone and act");

        manager.initLevel(Sonic3kZoneIds.ZONE_HPZ, 1);
        assertInstanceOf(HpzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
        assertEquals(Sonic3kZoneIds.ZONE_HPZ, GameServices.zoneRuntimeRegistry().current().zoneIndex());
    }

    @Test
    void deathEggBossActDoesNotInstallTheSanctuaryState() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

        manager.initLevel(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0);

        assertTrue(S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).isEmpty());
        assertFalse(GameServices.zoneRuntimeRegistry().current() instanceof HpzZoneRuntimeState);
        var state = assertInstanceOf(com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState.class,
                GameServices.zoneRuntimeRegistry().current());
        state.windowBase(0x2C0); state.breakRequest(0x2D0); state.bossSignals(7);
        manager.ensureZoneRuntimeStateInstalled();
        org.junit.jupiter.api.Assertions.assertSame(state, GameServices.zoneRuntimeRegistry().current());
        assertEquals(0x2C0, state.windowBase()); assertEquals(0x2D0, state.breakRequest());
        assertEquals(7, state.bossSignals());
        manager.initLevel(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 1);
        assertInstanceOf(HpzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
        manager.initLevel(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0);
        var fresh = assertInstanceOf(com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState.class,
                GameServices.zoneRuntimeRegistry().current());
        assertEquals(0, fresh.bossSignals()); assertEquals(0, fresh.breakRequest());
    }
}
