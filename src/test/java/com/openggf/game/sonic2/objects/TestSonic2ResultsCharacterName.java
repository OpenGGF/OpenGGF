package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayerCharacter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestSonic2ResultsCharacterName {
    @Test void tailsAloneAdvancesOnceAndAgainForOverseasGraphics() {
        assertEquals(1, ResultsScreenObjectInstance.nativeNameFrame(PlayerCharacter.TAILS_ALONE, false));
        assertEquals(2, ResultsScreenObjectInstance.nativeNameFrame(PlayerCharacter.TAILS_ALONE, true));
    }

    @Test void drawingUsesTheNativeProviderGraphicsFlag() {
        var services = mock(com.openggf.level.objects.ObjectServices.class);
        var renderManager = mock(com.openggf.level.objects.ObjectRenderManager.class);
        var renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        when(services.camera()).thenReturn(mock(com.openggf.camera.Camera.class));
        when(services.renderManager()).thenReturn(renderManager);
        when(renderManager.getResultsRenderer()).thenReturn(renderer);
        try (var team = mockStatic(com.openggf.game.session.ActiveGameplayTeamResolver.class)) {
            team.when(() -> com.openggf.game.session.ActiveGameplayTeamResolver
                    .resolvePlayerCharacter(null)).thenReturn(PlayerCharacter.TAILS_ALONE);
            for (boolean overseas : new boolean[]{false, true}) {
                reset(renderer);
                when(services.gameService(com.openggf.game.sonic2.options.Sonic2NativeModeProvider.class))
                        .thenReturn(new com.openggf.game.sonic2.options.Sonic2NativeModeProvider(overseas));
                var results = new ResultsScreenObjectInstance(0, 0, 1, false);
                results.setServices(services);
                results.appendRenderCommands(new java.util.ArrayList<>());
                verify(renderer).drawFrameIndex(eq(overseas ? 2 : 1), anyInt(), anyInt(), eq(false), eq(false));
            }
        }
    }

    @Test void otherRoutesKeepSonicRegardlessOfGraphicsRegion() {
        for (var character : new PlayerCharacter[]{PlayerCharacter.SONIC_ALONE,
                PlayerCharacter.SONIC_AND_TAILS, PlayerCharacter.KNUCKLES}) {
            assertEquals(0, ResultsScreenObjectInstance.nativeNameFrame(character, false));
            assertEquals(0, ResultsScreenObjectInstance.nativeNameFrame(character, true));
        }
    }
}
