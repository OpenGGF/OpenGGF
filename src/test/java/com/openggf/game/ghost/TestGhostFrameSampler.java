package com.openggf.game.ghost;

import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestGhostFrameSampler {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @Test
    void samplesResolvedPlayableRenderState() {
        TestablePlayableSprite sprite = new TestablePlayableSprite(
                "sonic", (short) 100, (short) 200);
        sprite.setCentreX((short) 321);
        sprite.setCentreY((short) 654);
        sprite.setMappingFrame(17);
        sprite.setRenderFlips(true, false);
        sprite.setPriorityBucket(3);
        sprite.setHighPriority(true);

        assertEquals(new GhostFrame(321, 654, 17, true, false,
                        true, 3, true),
                GhostFrameSampler.sample(sprite, true));
    }
}
