package com.openggf.sprites.managers;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RequiresRom(SonicGame.SONIC_3K)
class TestTailsTailsFlightSelection {

    @Test
    void flyingParentAnimationsDrawSeparateTailArt() {
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x100, (short) 0x200);
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        TailsTailsController controller = new TailsTailsController(tails, renderer, true);

        for (int parentAnimation = 0x20; parentAnimation <= 0x24; parentAnimation++) {
            clearInvocations(renderer);
            tails.setAnimationId(parentAnimation);

            controller.update();
            controller.draw();

            verify(renderer).drawFrame(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void swimmingParentAnimationsKeepSeparateTailBlank() {
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x100, (short) 0x200);

        for (int parentAnimation = 0x25; parentAnimation <= 0x28; parentAnimation++) {
            PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
            TailsTailsController controller = new TailsTailsController(tails, renderer, true);
            tails.setAnimationId(parentAnimation);

            controller.update();
            controller.draw();

            verifyNoInteractions(renderer);
        }
    }

    @Test
    void romFlightAndSwimScriptsResolveToMappingAndDplcFrames() throws Exception {
        SpriteArtSet tails = new Sonic3kPlayerArt(
                RomByteReader.fromRom(TestEnvironment.currentRom())).loadTails();

        for (int animationId = 0x20; animationId <= 0x28; animationId++) {
            SpriteAnimationScript script = tails.animationSet().getScript(animationId);
            assertNotNull(script, "missing animation script 0x" + Integer.toHexString(animationId));
            assertFalse(script.frames().isEmpty(), "empty animation script 0x" + Integer.toHexString(animationId));
            for (int frame : script.frames()) {
                assertTrue(frame >= 0 && frame < tails.mappingFrames().size(),
                        "mapping frame out of range for animation 0x" + Integer.toHexString(animationId));
                assertNotNull(tails.mappingFrames().get(frame));
                assertTrue(frame < tails.dplcFrames().size(),
                        "DPLC frame out of range for animation 0x" + Integer.toHexString(animationId));
                assertNotNull(tails.dplcFrames().get(frame));
            }
        }
    }
}
