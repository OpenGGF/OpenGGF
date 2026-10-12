package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeSpecialStagePacing;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class TestNativeStagePacingOwnership {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void realOwnerBoundCustomProviderCannotDeclareNativePacingReadiness() throws Exception {
        TestEnvironment.resetAll();
        var custom = mock(SpecialStageProvider.class, withSettings().extraInterfaces(NativeSpecialStagePacing.class));
        var reported = (NativeSpecialStagePacing) custom;
        when(reported.pacingState()).thenReturn(new NativeSpecialStagePacing.State(true, 7, 9, 0, 0, true));
        var base = new Sonic2GameModule(); var patch = mock(GamePatch.class);
        when(patch.apply(any(), any())).thenAnswer(call -> new DelegatingGameModule(base, "test:custom-stage") {
            @Override public SpecialStageProvider getSpecialStageProvider() { return custom; }
        });
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var wrapped = OwnerBoundGamePatch.wrap("test-mod", patch, boundary).apply(base, null);
        var provider = wrapped.getSpecialStageProvider();
        assertTrue(Proxy.isProxyClass(provider.getClass()));
        assertInstanceOf(NativeSpecialStagePacing.class, provider, "Actual callback projection preserves the creator's reported SPI");
        clearInvocations(custom);
        var input = new InputHandler(); var loop = new StockLoop(input);
        var context = mock(GameplayModeContext.class);
        var world = MutatorPhysicsWorld.create(
                () -> new GameplayMutatorPolicy(100, 0, 100, 0xC00, 400, false));
        when(context.getWorldSession()).thenReturn(world);
        when(context.isGameplayRuntimeReady()).thenReturn(true);
        set(loop, "gameplayMode", context); set(loop, "audioManager", mock(AudioManager.class));
        set(loop, "fadeManager", mock(FadeManager.class)); set(loop, "activeSpecialStageProvider", provider);
        loop.setGameMode(GameMode.SPECIAL_STAGE); loop.stepPresentationFrame();
        assertEquals(1, loop.ticks, "Only a native engine owner may opt into four-tick scheduling");
        verify(reported, never()).pacingState();
    }
    private static void set(GameLoop loop, String name, Object value) throws Exception {
        Field field = GameLoop.class.getDeclaredField(name); field.setAccessible(true); field.set(loop, value);
    }
    private static final class StockLoop extends GameLoop {
        private int ticks;
        StockLoop(InputHandler input) { super(input); }
        @Override public void step() { ticks++; }
    }
}
