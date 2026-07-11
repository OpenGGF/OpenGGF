package com.openggf;

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
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class TestEngineDataSelectPatchResolution {

    interface PatchTrail {
        List<String> ids();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void dataSelectTeamChangeAlwaysReresolvesTwoPatchStackFromWorldRoot() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        List<RegisteredPatch> builtIns = List.of(
                new RegisteredPatch(new PatchOwner.BuiltIn("one"), "one", patch("one"), 0),
                new RegisteredPatch(new PatchOwner.BuiltIn("two"), "two", patch("two"), 1));
        ModuleResolutionService resolver = new ModuleResolutionService(builtIns,
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config);
        Engine engine = new Engine(new EngineContext(config, mock(GraphicsManager.class),
                mock(AudioManager.class), mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class), resolver));
        GameModule root = new Sonic2GameModule();
        GameModule initiallyResolved = resolver.resolveForLaunch(root,
                new GameplayLaunchRequest("s2", "knuckles", List.of()),
                ModuleResolutionService.LaunchPolicy.STANDARD);
        SessionManager.openGameplaySession(root, initiallyResolved, null);

        engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                new SelectedTeam("sonic", List.of("tails")), 0, 0),
                List.of("sonic", "tails", "knuckles"));
        assertSame(root, SessionManager.requireCurrentGameModule());

        engine.openDataSelectPatchSession(SaveSessionContext.noSave("s2",
                new SelectedTeam("knuckles", List.of()), 0, 0),
                List.of("sonic", "tails", "knuckles"));
        assertSame(root, SessionManager.getCurrentWorldSession().rootGameModule());
        assertEquals(List.of("one", "two"),
                ((PatchTrail) SessionManager.requireCurrentGameModule()).ids());
    }

    private static GamePatch patch(String id) {
        return new GamePatch() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) {
                return "knuckles".equals(request.mainCharacter());
            }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of("knuckles"); }
            @Override public GameModule apply(GameModule base, PatchContext context) {
                List<String> trail = new ArrayList<>(
                        base instanceof PatchTrail current ? current.ids() : List.of());
                trail.add(id);
                class Patched extends DelegatingGameModule implements PatchTrail {
                    Patched() { super(base, id); }
                    @Override public List<String> ids() { return List.copyOf(trail); }
                }
                return new Patched();
            }
        };
    }
}
