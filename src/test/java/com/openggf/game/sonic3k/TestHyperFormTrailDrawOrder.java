package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestHyperFormTrailDrawOrder {
    @Test
    void liveBodyPrecedesEchoInFrontToBackSpriteTable() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        verifyOrder(new Sonic("sonic", (short) 0, (short) 0));
        verifyOrder(new Knuckles("knuckles", (short) 0, (short) 0));
    }

    private void verifyOrder(AbstractPlayableSprite player) {
        var renderer = mock(PlayerSpriteRenderer.class);
        var controller = mock(SuperStateController.class);
        player.setSpriteRenderer(renderer);
        player.setSuperStateController(controller);
        player.draw();
        var order = inOrder(renderer, controller);
        order.verify(renderer).drawFrame(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        order.verify(controller).renderPoweredTrail();
    }
}
