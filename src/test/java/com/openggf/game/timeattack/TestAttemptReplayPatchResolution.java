package com.openggf.game.timeattack;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.RomDetectionService;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class TestAttemptReplayPatchResolution {

    @Test
    void attemptMetadataDrivesInjectedDeterministicResolver() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        ModuleResolutionService resolver = new ModuleResolutionService(List.of(
                new RegisteredPatch(new PatchOwner.BuiltIn("attempt"), "attempt",
                        knucklesPatch(), 0)), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        EngineContext services = new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver);

        GameModule resolved = AttemptReplayHarness.resolveAttemptModuleForReplay(services,
                new Sonic2GameModule(),
                new AttemptStartDescriptor("s2", 0, 0, "knuckles", "fingerprint"));

        assertInstanceOf(DelegatingGameModule.class, resolved);
        assertEquals("attempt", ((DelegatingGameModule) resolved).patchId());
    }

    private static GamePatch knucklesPatch() {
        return new GamePatch() {
            @Override public String id() { return "attempt"; }
            @Override public String displayName() { return "attempt"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) {
                return "knuckles".equals(request.mainCharacter());
            }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of("knuckles"); }
            @Override public GameModule apply(GameModule base, PatchContext context) {
                class Patched extends DelegatingGameModule {
                    Patched() { super(base, "attempt"); }
                }
                return new Patched();
            }
        };
    }
}
