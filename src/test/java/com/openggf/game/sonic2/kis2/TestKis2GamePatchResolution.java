package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.PlayerCharacter;
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
import com.openggf.game.session.BuiltInPatches;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2WaterDataProvider;
import com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Palette;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(Set.of(LogicalRom.KIS2), patch.optionalRomPrerequisites(),
                "the lock-on dump is optional: it selects tier two, never gates resolution");
        assertEquals(List.of("knuckles"), patch.providedMainCharacters());
    }

    @Test
    void withoutTheLockOnDumpTheModuleRunsTierOneAndKeepsStockWaterAndContinueSeams() {
        Sonic2GameModule base = new Sonic2GameModule();
        PatchContext context = new PatchContext(rom -> {
            if (rom == LogicalRom.KIS2) {
                throw new IOException("no lock-on dump in this test");
            }
            return RomByteReader.fromBytes(new byte[0x200000]);
        }, SonicConfigurationService.createStandalone(tempDir));

        Kis2GameModule patched = (Kis2GameModule) patch.apply(base, context);

        assertEquals(Kis2GameModule.Fidelity.TIER_ONE, patched.fidelity());
        assertNotSame(base.getZoneFeatureProvider(), patched.getZoneFeatureProvider());
        assertSame(patched.getZoneFeatureProvider(), patched.getZoneFeatureProvider());
        assertInstanceOf(Sonic2WaterDataProvider.class, patched.getWaterDataProvider());
        assertInstanceOf(Sonic2ContinueScreenProvider.class, patched.createContinueScreenProvider());
        assertInstanceOf(Sonic2ObjectArtProvider.class, patched.getObjectArtProvider());
    }

    @Test
    void withALockOnImageTheModuleRunsTierTwoAndReadsUnderwaterPalettesFromTheChip() {
        Sonic2GameModule base = new Sonic2GameModule();
        byte[] image = new byte[0x340000];
        // A recognisable Pal_CPZ_U line 0 on the chip: colour 1 = $0EEE.
        image[Kis2Constants.PAL_CPZ_U + 2] = 0x0E;
        image[Kis2Constants.PAL_CPZ_U + 3] = (byte) 0xEE;
        PatchContext context = new PatchContext(rom -> switch (rom) {
            case KIS2 -> RomByteReader.fromBytes(image);
            case SK -> RomByteReader.fromBytes(image, 0, 0x200000);
            default -> throw new IOException("unexpected " + rom);
        }, SonicConfigurationService.createStandalone(tempDir));

        Kis2GameModule patched = (Kis2GameModule) patch.apply(base, context);

        assertEquals(Kis2GameModule.Fidelity.TIER_TWO, patched.fidelity());
        assertEquals(new com.openggf.game.resources.PlayerArtTransferProfile.ConvertedBank(0xFFF100, 0xF000),
                patched.convertedPlayerArtBank("knuckles"));
        assertNull(patched.convertedPlayerArtBank("sonic"));
        assertSame(patched.getZoneFeatureProvider(), patched.getZoneFeatureProvider());
        Palette[] cpz = patched.getWaterDataProvider().getUnderwaterPalette(null,
                Sonic2ZoneConstants.ROM_ZONE_CPZ, 1, PlayerCharacter.SONIC_ALONE);
        assertEquals(4, cpz.length);
        Palette expectedLine0 = new Palette();
        expectedLine0.fromSegaFormat(java.util.Arrays.copyOfRange(image, Kis2Constants.PAL_CPZ_U,
                Kis2Constants.PAL_CPZ_U + Palette.PALETTE_SIZE_IN_ROM));
        assertTrue(cpz[0].dataEquals(expectedLine0), "chip Pal_CPZ_U line 0 is served as read");
        assertTrue(cpz[0].getColor(1).r != 0 && cpz[0].getColor(2).r == 0, "colour 1 set, colour 2 black");
        Palette[] arz = patched.getWaterDataProvider().getUnderwaterPalette(null,
                Sonic2ZoneConstants.ROM_ZONE_ARZ, 0, PlayerCharacter.SONIC_ALONE);
        assertEquals(4, arz.length);
        assertEquals(0, arz[0].getColor(1).r, "Pal_ARZ_U is read from its own chip address");
    }

    @Test
    void tierTwoAddressSpaceResolvesTheCasinoNightPointersIntoTheChipWindow() {
        RomByteReader sk = RomByteReader.fromBytes(new byte[0x200000]);
        RomByteReader s2 = RomByteReader.fromBytes(new byte[0x100000]);
        RomByteReader image = RomByteReader.fromBytes(new byte[0x340000]);

        LockOnAddressSpace tierOne = LockOnAddressSpace.tierOne(sk, s2);
        LockOnAddressSpace tierTwo = LockOnAddressSpace.tierTwo(sk, s2, image);

        assertFalse(tierOne.hasChip());
        assertTrue(tierOne.resolve(Kis2Constants.OBJECTS_CNZ_1).isEmpty());
        assertTrue(tierTwo.hasChip());
        LockOnAddressSpace.Read cnz1 = tierTwo.require(Kis2Constants.OBJECTS_CNZ_1);
        assertEquals(LockOnAddressSpace.Window.CHIP, cnz1.window());
        assertEquals(Kis2Constants.OBJECTS_CNZ_1 - Kis2Constants.CHIP_WINDOW_START, cnz1.localAddress());
        assertEquals(0x40000, cnz1.reader().size(), "chip window is the 256 KiB at $300000");
        assertEquals(LockOnAddressSpace.Window.SK, tierTwo.require(Kis2Constants.OFF_OBJECTS_KIS2).window());
        assertThrows(IllegalArgumentException.class,
                () -> LockOnAddressSpace.tierTwo(sk, s2, RomByteReader.fromBytes(new byte[0x300000])));
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
        List<RegisteredPatch> builtIns = BuiltInPatches.registrations();
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
        return new ModuleResolutionService(BuiltInPatches.registrations(),
                PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> skAvailable ? new byte[0x200000] : null),
                SonicConfigurationService.createStandalone(tempDir));
    }
}
