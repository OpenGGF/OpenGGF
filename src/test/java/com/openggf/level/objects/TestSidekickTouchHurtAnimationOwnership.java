package com.openggf.level.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSidekickTouchHurtAnimationOwnership {

    @AfterEach
    void resetModule() {
        GameModuleRegistry.reset();
    }

    @Test
    void spikedLogTouchKeepsWalkByteAndRestartsRetainedScript() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());

        TestablePlayableSprite tails = new TestablePlayableSprite("tails_p2", (short) 160, (short) 112);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 160);
        tails.setCentreY((short) 112);
        tails.setAirForTest(true);
        tails.setAnimationId(0);
        tails.setAnimationProfile(new ScriptedVelocityAnimationProfile()
                .setWalkAnimId(0)
                .setRunAnimId(0)
                .setHurtAnimId(0x1A)
                .setWalkRunPublishesFrameBeforeTimerAdvance(true));
        SpriteAnimationSet animationSet = new SpriteAnimationSet();
        animationSet.addScript(0, new SpriteAnimationScript(
                0xFF, List.of(7, 8, 1), SpriteAnimationEndAction.LOOP, 0));
        animationSet.addScript(0x1A, new SpriteAnimationScript(
                0, List.of(0x1A), SpriteAnimationEndAction.HOLD, 0));
        tails.setAnimationSet(animationSet);

        // Establish raw Walk/map 8 before the touch, matching the AIZ trace.
        tails.getAnimationManager().update(1);
        tails.getAnimationManager().update(2);
        assertEquals(8, tails.getMappingFrame());

        ObjectManager objectManager = mock(ObjectManager.class);
        TouchResponseTable table = mock(TouchResponseTable.class);
        SpikedLogTouchObject spikes = new SpikedLogTouchObject();
        when(objectManager.objectCallbacks()).thenReturn(new ObjectCallbackRouter(null));
        when(objectManager.getTouchResponseObjects()).thenReturn(List.of(spikes));
        when(table.getWidthRadius(0x1C)).thenReturn(16);
        when(table.getHeightRadius(0x1C)).thenReturn(16);

        new ObjectTouchResponseController(objectManager, table)
                .updateSidekick(tails, 1, false);

        assertTrue(tails.isHurt());
        assertEquals(0, tails.getAnimationId(), "the spiked-log owner leaves raw anim untouched");
        assertEquals(0, tails.getForcedAnimationId(), "retained raw anim must drive the hurt pass");
        assertEquals(8, tails.getMappingFrame(), "the damage frame keeps its prior mapping");

        tails.getAnimationManager().update(3);
        assertEquals(7, tails.getMappingFrame(), "retained script restarts on the next Animate_Tails pass");

        tails.setAirForTest(false);
        tails.setHurt(false);
        tails.getAnimationManager().update(4);
        assertEquals(-1, tails.getForcedAnimationId(), "hurt recovery releases the retained animation owner");
        assertEquals(8, tails.getMappingFrame(), "normal Walk animation resumes after recovery");
    }

    private static final class SpikedLogTouchObject extends AbstractObjectInstance
            implements TouchResponseProvider {
        private SpikedLogTouchObject() {
            super(new ObjectSpawn(160, 112, 0x2E, 0, 0, false, 0), "AIZSpikedLogSpikes");
        }

        @Override
        public int getCollisionFlags() {
            return 0x9C;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public boolean requiresRenderFlagForTouch() {
            return false;
        }

        @Override
        public boolean sidekickTouchHurtPublishesAnimation() {
            return false;
        }

        @Override
        public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
