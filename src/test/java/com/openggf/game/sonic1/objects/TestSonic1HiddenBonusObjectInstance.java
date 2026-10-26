package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestSonic1HiddenBonusObjectInstance {
    @ParameterizedTest
    @CsvSource({"0,0", "1,10000", "2,1000", "3,10"})
    void awardsShippedBonusPointsWhileKeepingTheNativePopupFrame(int subtype, int points) {
        GameStateManager state = new GameStateManager();
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        Sonic1HiddenBonusObjectInstance bonus = new Sonic1HiddenBonusObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x7D, subtype, 0, false, 0)) {
            @Override protected PatternSpriteRenderer getRenderer(String key) { return renderer; }
            @Override protected boolean isInRange() { return true; }
        };
        bonus.setServices(new TestObjectServices().withGameState(state));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x100);
        player.setCentreY((short) 0x100);
        bonus.update(0, player);
        bonus.appendRenderCommands(new ArrayList<>());
        // REV01 ROM Bonus_Points at $179BE: 0000 03E8 0064 0001; AddPoints uses tens.
        assertEquals(points, state.getScore());
        verify(renderer).drawFrameIndex(subtype, 0x100, 0x100, false, false);
        bonus.update(1, player);
        assertEquals(points, state.getScore(), "the pickup awards once");
    }
}
