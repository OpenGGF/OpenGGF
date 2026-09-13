package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kis2GamePatch identity, activation and module resolution with a fake context; no ROM. */
class TestKis2GamePatchResolution {

    @TempDir
    Path tempDir;

    private final Kis2GamePatch patch = new Kis2GamePatch();

    @Test
    void identityAndPrerequisites() {
        assertEquals("kis2", patch.id());
        assertEquals("s2", patch.baseGameId());
        assertEquals("Knuckles in Sonic 2", patch.displayName());
        assertEquals(Set.of(LogicalRom.SK), patch.romPrerequisites());
        assertEquals(List.of("knuckles"), patch.providedMainCharacters());
    }

    @Test
    void activatesOnlyForKnucklesMain() {
        assertTrue(patch.activatesFor(new GameplayLaunchRequest("s2", "knuckles", List.of())));
        assertTrue(patch.activatesFor(new GameplayLaunchRequest("s2", "Knuckles", List.of("tails"))));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s2", "sonic", List.of())));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s2", "tails", List.of())));
    }

    @Test
    void patchedModuleOverridesPhysicsAndArtSeamsAndDelegatesEverythingElse() {
        Sonic2GameModule base = new Sonic2GameModule();
        PatchContext context = new PatchContext(rom -> {
            throw new IOException("no ROM in this test");
        }, SonicConfigurationService.createStandalone(tempDir));

        GameModule patched = patch.apply(base, context);

        assertInstanceOf(DelegatingGameModule.class, patched);
        assertEquals("kis2", ((DelegatingGameModule) patched).patchId());
        assertSame(base, ((DelegatingGameModule) patched).base());
        assertInstanceOf(Kis2PhysicsProvider.class, patched.getPhysicsProvider());
        assertSame(patched.getPhysicsProvider(), patched.getPhysicsProvider());
        assertInstanceOf(Sonic2ObjectArtProvider.class, patched.getObjectArtProvider());
        assertEquals(base.getGameId(), patched.getGameId());
        assertEquals(base.getCheckpointObjectId(), patched.getCheckpointObjectId());
        assertSame(base.getZoneRegistry(), patched.getZoneRegistry());
        assertSame(base.getLevelInitProfile(), patched.getLevelInitProfile());
    }

    @Test
    void builtInRegistryResolvesKnucklesOnSonic2AndLeavesSonicOnTheRoot() {
        ModuleResolutionService service = serviceWithPrerequisite(true);
        Sonic2GameModule root = new Sonic2GameModule();

        GameModule knuckles = service.resolveForLaunch(root,
                new GameplayLaunchRequest("s2", "knuckles", List.of()),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);
        GameModule sonic = service.resolveForLaunch(root,
                new GameplayLaunchRequest("s2", "sonic", List.of("tails")),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);

        assertInstanceOf(DelegatingGameModule.class, knuckles);
        assertEquals("kis2", ((DelegatingGameModule) knuckles).patchId());
        assertSame(root, sonic);
        assertEquals(List.of("knuckles"),
                service.availableMainCharactersForLaunch("s2", ModuleResolutionService.LaunchPolicy.STANDARD));
    }

    @Test
    void missingSkRomLeavesTheRootModuleAndOffersNoKnuckles() {
        ModuleResolutionService service = serviceWithPrerequisite(false);
        Sonic2GameModule root = new Sonic2GameModule();

        GameModule resolved = service.resolveForLaunch(root,
                new GameplayLaunchRequest("s2", "knuckles", List.of()),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);

        assertSame(root, resolved);
        assertEquals(List.of(),
                service.availableMainCharactersForLaunch("s2", ModuleResolutionService.LaunchPolicy.STANDARD));
    }

    @Test
    void engineContextRegistersKis2AsABuiltInPatch() {
        List<RegisteredPatch> builtIns = EngineContext.builtInPatches();
        assertEquals(1, builtIns.size());
        assertEquals(new PatchOwner.BuiltIn("kis2"), builtIns.getFirst().owner());
        assertInstanceOf(Kis2GamePatch.class, builtIns.getFirst().patch());
    }

    @Test
    void traceReplayBootstrapResolvesTheRecordedTeamDeterministically() throws IOException {
        EngineContext legacy = EngineServices.current();
        EngineContext injected = new EngineContext(SonicConfigurationService.createStandalone(tempDir),
                legacy.graphics(), legacy.audio(), legacy.roms(), legacy.profiler(),
                legacy.debugOverlay(), legacy.playbackDebug(), legacy.romDetection(),
                legacy.crossGameFeatures(), serviceWithPrerequisite(true));
        Sonic2GameModule root = new Sonic2GameModule();

        GameModule knuckles = TraceReplaySessionBootstrap.resolveReplayModule(
                injected, root, metadata("knuckles"));
        GameModule sonic = TraceReplaySessionBootstrap.resolveReplayModule(
                injected, root, metadata("sonic"));

        assertEquals("kis2", ((DelegatingGameModule) knuckles).patchId());
        assertSame(root, sonic);
    }

    private TraceMetadata metadata(String mainCharacter) throws IOException {
        Path file = tempDir.resolve("metadata-" + mainCharacter + ".json");
        Files.writeString(file, "{\"game\":\"s2\",\"zone\":\"ehz\",\"act\":1,"
                + "\"trace_schema\":5,\"main_character\":\"" + mainCharacter + "\","
                + "\"sidekicks\":[]}", StandardCharsets.UTF_8);
        return TraceMetadata.load(file);
    }

    private ModuleResolutionService serviceWithPrerequisite(boolean skAvailable) {
        return new ModuleResolutionService(EngineContext.builtInPatches(),
                PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> skAvailable ? new byte[0x200000] : null),
                SonicConfigurationService.createStandalone(tempDir));
    }
}
