package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.MgzZoneRuntimeState;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kZoneRuntimeStateAdapters {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        SessionManager.clear();
    }

    @Test
    void aizAdapterMirrorsNamedStateFromExistingEvents() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setBossFlag(true);
        events.setEventsFg5(true);

        AizZoneRuntimeState state = new AizZoneRuntimeState(0, PlayerCharacter.KNUCKLES, events);

        assertEquals("s3k", state.gameId());
        assertEquals(0, state.zoneIndex());
        assertEquals(0, state.actIndex());
        assertEquals(PlayerCharacter.KNUCKLES, state.playerCharacter());
        assertTrue(state.isBossFlagActive());
        assertTrue(state.isActTransitionFlagActive());
        assertFalse(state.isPostFireHazeActive());
    }

    @Test
    void hczAdapterMirrorsTransitionFlagAndRoutine() {
        Sonic3kHCZEvents events = new Sonic3kHCZEvents();
        events.init(0);
        events.setEventsFg5(true);
        events.setDynamicResizeRoutine(8);

        HczZoneRuntimeState state = new HczZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS, events);

        assertEquals("s3k", state.gameId());
        assertEquals(1, state.zoneIndex());
        assertEquals(0, state.actIndex());
        assertEquals(PlayerCharacter.SONIC_AND_TAILS, state.playerCharacter());
        assertTrue(state.isActTransitionFlagActive());
        assertEquals(8, state.getDynamicResizeRoutine());
    }

    @Test
    void cnzAdapterCarriesPlayerCharacterAndBossBackgroundMode() {
        Sonic3kCNZEvents events = new Sonic3kCNZEvents();
        events.init(0);
        events.setEventsFg5(true);

        CnzZoneRuntimeState state = new CnzZoneRuntimeState(0, PlayerCharacter.TAILS_ALONE, events);

        assertEquals("s3k", state.gameId());
        assertEquals(3, state.zoneIndex());
        assertEquals(0, state.actIndex());
        assertEquals(PlayerCharacter.TAILS_ALONE, state.playerCharacter());
        assertTrue(state.isActTransitionFlagActive());
        assertNotNull(state.bossBackgroundMode());
    }

    @Test
    void mgzAdapterOwnsScrollRuntimePublications() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        events.setEventsFg5(true);
        events.setBgRiseRoutine(8);
        events.setBgRiseOffset(0x120);

        MgzZoneRuntimeState state = new MgzZoneRuntimeState(1, PlayerCharacter.KNUCKLES, events);

        assertEquals("s3k", state.gameId());
        assertEquals(2, state.zoneIndex());
        assertEquals(1, state.actIndex());
        assertEquals(PlayerCharacter.KNUCKLES, state.playerCharacter());
        assertTrue(state.isActTransitionFlagActive());
        assertEquals(8, state.bgRiseRoutine());
        assertEquals(0x120, state.bgRiseOffset());

        assertFalse(state.hasBossBgScrollOffset());
        state.publishBossBgScrollOffset(0x13D80);
        assertTrue(state.hasBossBgScrollOffset());
        assertEquals(0x3D80, state.bossBgScrollOffset());

        state.requestScreenShakeOffset(2);
        state.requestScreenShakeOffset(6);
        state.requestScreenShakeOffset(3);
        assertEquals(6, state.consumeScreenShakeOffset());
        assertEquals(0, state.consumeScreenShakeOffset());
    }
}
