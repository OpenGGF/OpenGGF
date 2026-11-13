package com.openggf.game.sonic3k.ending;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ProfileSuperStateController;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class TestS3kEndingPlayerPresentation {
    @Test void nativeHiddenMappingDoesNotDrawAuthoredIdleOverReunionPose() {
        var player = mock(AbstractPlayableSprite.class);
        var profile = mock(ProfileSuperStateController.class);
        var ordinary = mock(PlayerSpriteRenderer.class);
        var services = mock(ObjectServices.class);
        when(services.graphicsManager()).thenReturn(mock(GraphicsManager.class));
        when(player.getSuperStateController()).thenReturn(profile);
        when(profile.isEndingSceneActive()).thenReturn(true);
        when(player.isObjectMappingFrameControl()).thenReturn(true);
        new EndingPlayerSpriteRenderer(ordinary,player,services).drawFrame(0,160,100,false,false);
        verifyNoInteractions(ordinary);
    }
    @Test void ordinaryIdleStillDrawsOutsideSceneOwnership() {
        var player = mock(AbstractPlayableSprite.class);
        var ordinary = mock(PlayerSpriteRenderer.class);
        var services = mock(ObjectServices.class);
        when(services.graphicsManager()).thenReturn(mock(GraphicsManager.class));
        new EndingPlayerSpriteRenderer(ordinary,player,services).drawFrame(0,160,100,false,false);
        verify(ordinary).drawFrame(0,160,100,false,false);
    }
}
