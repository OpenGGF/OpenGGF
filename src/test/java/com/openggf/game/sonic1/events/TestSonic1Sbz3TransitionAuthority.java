package com.openggf.game.sonic1.events;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1Sbz3TransitionAuthority {

    private Sonic1LevelEventManager events;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        events = new Sonic1LevelEventManager();
        events.initLevel(Sonic1ZoneConstants.ZONE_SBZ, 1);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void registeredSidekickCannotRequestGlobalSbz3Transition() {
        TestPlayableSprite main = playable("main", false, 0x1FFF);
        TestPlayableSprite sidekick = playable("sidekick", true, 0x2000);
        GameServices.sprites().addSprite(main);
        GameServices.sprites().addSprite(sidekick);
        GameServices.camera().setFocusedSprite(main);

        assertFalse(events.interceptPitDeath(sidekick));
        assertFalse(GameServices.level().getTransitions().consumeZoneActRequest());
        assertFalse(sidekick.isControlLocked());
    }

    @Test
    void acceptedTransitionLocksMainThenEveryRegisteredSidekickOnce() {
        List<String> lockOrder = new ArrayList<>();
        RecordingPlayable main = playable("main", false, 0x2000, lockOrder);
        RecordingPlayable nativeSidekick = playable("p2", true, 0x1F00, lockOrder);
        RecordingPlayable extraSidekick = playable("p3", true, 0x1E00, lockOrder);
        GameServices.sprites().addSprite(main);
        GameServices.sprites().addSprite(nativeSidekick);
        GameServices.sprites().addSprite(extraSidekick);
        GameServices.camera().setFocusedSprite(main);

        assertTrue(events.interceptPitDeath(main));

        assertEquals(List.of("main", "p2", "p3"), lockOrder);
        assertTrue(main.isControlLocked());
        assertTrue(nativeSidekick.isControlLocked());
        assertTrue(extraSidekick.isControlLocked());
        assertTrue(GameServices.level().getTransitions().consumeZoneActRequest());
        assertEquals(Sonic1ZoneConstants.ZONE_SBZ,
                GameServices.level().getTransitions().getRequestedZone());
        assertEquals(2, GameServices.level().getTransitions().getRequestedAct());

        assertTrue(events.interceptPitDeath(extraSidekick));
        assertFalse(GameServices.level().getTransitions().consumeZoneActRequest(),
                "accepted transition must not be requested again by a later playable update");
        assertEquals(List.of("main", "p2", "p3"), lockOrder,
                "accepted transition cleanup must remain single-shot");
    }

    private static TestPlayableSprite playable(String code, boolean cpuControlled, int centreX) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCode(code);
        player.setCpuControlled(cpuControlled);
        player.setCentreX((short) centreX);
        return player;
    }

    private static RecordingPlayable playable(
            String code, boolean cpuControlled, int centreX, List<String> lockOrder) {
        RecordingPlayable player = new RecordingPlayable(lockOrder);
        player.setCode(code);
        player.setCpuControlled(cpuControlled);
        player.setCentreX((short) centreX);
        return player;
    }

    private static final class RecordingPlayable extends TestPlayableSprite {
        private final List<String> lockOrder;

        private RecordingPlayable(List<String> lockOrder) {
            this.lockOrder = lockOrder;
        }

        @Override
        public void setControlLocked(boolean controlLocked) {
            super.setControlLocked(controlLocked);
            if (controlLocked) {
                lockOrder.add(getCode());
            }
        }
    }
}
