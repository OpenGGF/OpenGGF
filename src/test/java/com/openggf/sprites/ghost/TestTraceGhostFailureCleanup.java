package com.openggf.sprites.ghost;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestTraceGhostFailureCleanup {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedBatchSetupOrFlushStillRestoresGhostEffectAndPriority(boolean failAtSetup) throws Exception {
        var original = EngineServices.current();
        var graphics = mock(GraphicsManager.class);
        EngineServices.configure(new EngineContext(original.configuration(), graphics, original.audio(),
                original.roms(), original.profiler(), original.debugOverlay(), original.playbackDebug(),
                original.romDetection(), original.crossGameFeatures(), original.moduleResolutionService()));
        try {
            var failure = new IllegalStateException("injected batch failure");
            if (failAtSetup) doThrow(failure).when(graphics).beginPatternBatch();
            else doNothing().doThrow(failure).when(graphics).flushPatternBatch();
            var renderer = new GhostTraceRenderer();
            var real = mock(AbstractPlayableSprite.class);
            when(real.isHighPriority()).thenReturn(true);
            var sprite = mock(AbstractPlayableSprite.class);
            var slotType = Class.forName(GhostTraceRenderer.class.getName() + "$GhostSlot");
            var constructor = slotType.getDeclaredConstructor(AbstractPlayableSprite.class,
                    PlayerSpriteRenderer.class, String.class, SpriteArtSet.class);
            constructor.setAccessible(true);
            var slotsField = GhostTraceRenderer.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var slots = (Map<String, Object>) slotsField.get(renderer);
            slots.put("main:sonic", constructor.newInstance(sprite, mock(PlayerSpriteRenderer.class),
                    "sonic", mock(SpriteArtSet.class)));
            var method = GhostTraceRenderer.class.getDeclaredMethod("renderCharacter", String.class,
                    String.class, TraceCharacterState.class, AbstractPlayableSprite.class,
                    int.class, boolean.class, boolean.class);
            method.setAccessible(true);
            var state = new TraceCharacterState(true, (short) 96, (short) 0,
                    (short) 0, (short) 0, (short) 0, (byte) 0, false, false,
                    0, 0, 0, 2, 0, -1);
            var thrown = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(renderer, "main", "sonic", state, real, 0, false, false));
            assertSame(failure, thrown.getCause());
            verify(graphics).endGhostRenderEffect();
            var order = inOrder(graphics);
            order.verify(graphics).setCurrentSpriteHighPriority(true);
            order.verify(graphics).endGhostRenderEffect();
            order.verify(graphics).setCurrentSpriteHighPriority(false);
        } finally {
            EngineServices.configure(original);
        }
    }
}
