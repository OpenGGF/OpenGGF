package com.openggf.game.sonic1.events;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1EndingMultiSidekick {
    private Sonic1LevelEventManager events;
    private TestPlayableSprite main;
    private List<TestPlayableSprite> sidekicks;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        main = player("main", false);
        sidekicks = List.of(player("p2", true), player("p3", true), player("p4", true));
        GameServices.sprites().addSprite(main);
        sidekicks.forEach(GameServices.sprites()::addSprite);
        GameServices.camera().setFocusedSprite(main);
        events = new Sonic1LevelEventManager();
        events.initLevel(Sonic1ZoneConstants.ZONE_ENDING, 0);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void endingBootstrapAndHandoffControlEveryParticipantButMainOwnsProgression() {
        events.update();
        for (TestPlayableSprite player : allPlayers()) {
            assertTrue(player.isControlLocked());
            assertEquals(TestPlayableSprite.INPUT_LEFT, player.getForcedInputMask());
            assertEquals(-0x800, player.getGSpeed());
        }

        main.setCentreX((short) 0x008F);
        events.update();
        for (TestPlayableSprite player : allPlayers()) {
            assertEquals(TestPlayableSprite.INPUT_RIGHT, player.getForcedInputMask());
        }

        main.setCentreX((short) 0x00A0);
        events.update();
        events.update();
        for (TestPlayableSprite player : allPlayers()) {
            assertTrue(player.isHidden());
            assertEquals(0, player.getGSpeed());
            assertEquals(0, player.getXSpeed());
        }
    }

    private List<TestPlayableSprite> allPlayers() {
        return List.of(main, sidekicks.get(0), sidekicks.get(1), sidekicks.get(2));
    }

    private static TestPlayableSprite player(String code, boolean cpu) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCode(code);
        player.setCpuControlled(cpu);
        player.setCentreX((short) 0x0620);
        player.setCentreY((short) 0x016B);
        return player;
    }
}
