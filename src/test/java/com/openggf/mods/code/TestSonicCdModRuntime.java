package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.*;
import com.openggf.mods.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** CD session and disc-backed registration example. No assets are bundled in this fixture. */
class TestSonicCdModRuntime {
    @Test void ownedPatchRunsOnNativeCdTerrain() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
            var nativeLevel = new CdStageLoader(disc).load(CdStagePayload.R11A, 0, false);
            var context = new ModContext("cd-example", "scd", ModAssetRoot.forTests("cd-example"));
            int[] updates = {0};
            new example.soniccd.CdMarkerExampleMod(nativeLevel.startX() + 32, nativeLevel.startY(), () -> updates[0]++)
                    .register(context);
            var faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    ignored -> new ModStateSaveResult.Saved(), ignored -> {});
            var module = new ModBackedGamePatch(context.freeze(), faults).apply(new SonicCdGameModule(), null);
            var loop = boot.boot(disc, module, 0, 0);
            var level = assertInstanceOf(CdLevel.class, GameServices.level().getCurrentLevel());
            assertTrue(level.getObjects().stream().anyMatch(s -> "cd-example:greeting".equals(s.objectKey())));
            // Ordinary production frames, with native CD terrain and the registered owned factory.
            for (int i = 0; i < 4; i++) loop.step();
            assertTrue(updates[0] > 0);
            assertTrue(faults.isOwnerAvailable("cd-example"));
            assertDoesNotThrow(() -> GameServices.crossGameFeatures().initialize("s3k"));
            assertNotNull(GameServices.crossGameFeatures().loadPlayerSpriteArt("sonic"));
        }
    }

    @Test void anyPlanPublishesCdAndNoOtherGamesDecorators() {
        var context = new ModContext("shared", "any", ModAssetRoot.forTests("shared"), null,
                com.openggf.mods.ModType.GAME, com.openggf.game.save.SavePaths.root());
        context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
            public void enter(com.openggf.mods.scene.SceneContext ctx) {}
            public void update(com.openggf.mods.scene.SceneContext ctx) {}
            public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas canvas) {}
        });
        context.registerObject("greeting", (spawn, registry) -> new Greeting(spawn, new int[1]));
        assertEquals(List.of("s1", "s2", "s3k", "scd"),
                context.freeze().stockScenePlans().stream().map(ModRegistrationPlan::baseGameId).toList());
    }

    @Test void discSceneViewsAreBoundedAndNeverUseCartridgeBytes() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var module = new SonicCdGameModule();
            var provider = module.getGameService(com.openggf.game.internal.MediaSceneArt.class);
            var art = com.openggf.mods.scene.host.SceneRomArtFactory.forMedia(
                    provider.program(disc, CdStagePayload.R11A.file().isoName()));
            assertEquals("scd", art.gameId());
            assertArrayEquals(Arrays.copyOf(disc.readFile(CdStagePayload.R11A.file()), 32), art.read(0, 32));
            assertThrows(IllegalArgumentException.class, () -> provider.program(disc, "../art.bin"));
            assertThrows(IllegalArgumentException.class, () -> art.read(Integer.MAX_VALUE, 32));
        }
    }

    @Test void fileArtIsOwnedAndDoesNotDeclareACartridgePrerequisite() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var entry = CdStageCatalogue.entry(CdStagePayload.R11A);
            var context = new ModContext("cd-art", "scd", ModAssetRoot.forTests("cd-art"));
            context.registerProgramObjectArt("sonic", CdStagePayload.R11A.file().isoName(),
                    new RomArtRequest(entry.playerArt(), RomArtCompression.UNCOMPRESSED, 32,
                            entry.playerMappings(), 0, 0, 1));
            var patch = new ModBackedGamePatch(context.freeze(), new ModFaultBoundary(Map.of(),
                    new ModRuntimeFindingStore(), ignored -> new ModStateSaveResult.Saved(), ignored -> {}));
            assertTrue(patch.romPrerequisites().isEmpty());
            var module = patch.apply(new SonicCdGameModule(), new com.openggf.game.patch.PatchContext(
                    source -> { throw new AssertionError("Program art must not open a cartridge"); },
                    com.openggf.configuration.SonicConfigurationService.getInstance()));
            assertNotNull(module.getObjectArtProvider().getSheet("cd-art:sonic"));
            try (var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
                var loop = boot.boot(disc, module, 0, 0);
                var stage = (CdLevel) GameServices.level().getCurrentLevel();
                for (int i = 0; i < 600 && stage.flags().ctrlLocked; i++) loop.step();
                assertFalse(stage.flags().ctrlLocked, "an art overlay retains the native title-card program owner");
                assertNotNull(module.getGameService(com.openggf.game.soniccd.objects.art.CdObjectArtProvider.class).image());
            }
        }
    }

    @Test void discDonorHostIsSupported() {
        assertTrue(new SonicCdGameModule().supportsCrossGameHost());
        assertTrue(new com.openggf.game.patch.DelegatingGameModule(new SonicCdGameModule(), "example") {}
                .supportsCrossGameHost());
    }

    @Test void cdZoneAndPoliciesKeepTheOwnedTransactionPath() {
        var zones = new ModContext("cd-zone", "scd", ModAssetRoot.forTests("cd-zone"));
        assertDoesNotThrow(() -> zones.registerZone(ModZoneContribution.singleAct(
                "flat", new BakedLevelRef("level.json"), null, null, false)));
        var context = new ModContext("cd-policy", "scd", ModAssetRoot.forTests("cd-policy"));
        var destination = new com.openggf.game.ZoneKey.Mod("cd-policy", "flat");
        context.registerLaunchTeam(new ModLaunchTeamContribution(destination, com.openggf.game.CharacterKey.SONIC, List.of()));
        context.registerInputFilter(new ModInputFilterContribution(destination, com.openggf.game.GameplayInputFilter.IDENTITY));
        context.registerHudProfile(new ModHudProfileContribution(destination, com.openggf.level.objects.HudProfile.stock()));
        var base = new SonicCdGameModule();
        var module = new ModBackedGamePatch(context.freeze(), new ModFaultBoundary(Map.of(),
                new ModRuntimeFindingStore(), ignored -> new ModStateSaveResult.Saved(), ignored -> {})).apply(base, null);
        assertSame(base.getPolicies(), module.getPolicies());
        assertEquals(com.openggf.game.CharacterKey.SONIC,
                module.getGameplayPolicyProvider().launchTeam(destination).orElseThrow().main());
        assertTrue(module.getGameplayPolicyProvider().inputFilter(destination).isPresent());
        assertTrue(module.getGameplayPolicyProvider().hudProfile(destination).isPresent());
    }

    @Test void authoredWaterLoadsFromDiscAtTheCreatorHeight() throws Exception {
        var key = new com.openggf.game.ZoneKey.Mod("cd-water", "pool");
        var declared = ModZoneContribution.singleAct(key.localName(), new BakedLevelRef("level.json"),
                null, null, false);
        var data = new ModLevelDefinition(1, "Pool", 0x40, 0x400, 16, 2, 2,
                new ModLevelDefinition.Bounds(0, 511, 0, 511), new ModLevelDefinition.Start(96, 96),
                new ModLevelDefinition.StockMusic(1), List.of(),
                List.of(new ModLevelDefinition.RingEntry(0, 96, 96)),
                new byte[32], new byte[8], new byte[1024], new byte[4], null,
                new byte[16], new byte[16], new byte[1], new int[]{0}, new int[]{0},
                new byte[][]{new byte[32], new byte[32], new byte[32], new byte[32]},
                1, 1, 1, 1, null, List.of(),
                com.openggf.game.modzone.ModLevelSettings.DEFAULT.withWaterHeight(160));
        var prepared = PreparedModZone.prepared(key.ownerModId(), declared, data);
        var plan = new ModRegistrationPlan(key.ownerModId(), "scd", Map.of(), Map.of(), Map.of(),
                List.of(), List.of(declared), List.of(prepared));
        var module = new ModBackedGamePatch(plan).apply(new SonicCdGameModule(),
                new com.openggf.game.patch.PatchContext(ignored -> null, GameServices.configuration()));
        int zone = module.getZoneRegistry().resolveZoneKey(key).orElseThrow();

        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, module, zone, 0);
            var levels = GameServices.level();
            assertInstanceOf(com.openggf.level.ModLevel.class, levels.getCurrentLevel());
            var ring = levels.getCurrentLevel().getRings().getFirst();
            var ringArt = levels.getCurrentLevel().getRingSpriteSheet();
            assertEquals(4, ringArt.getSpinFrameCount());
            assertEquals(4, ringArt.getSparkleFrameCount());
            assertEquals(1, ringArt.getPaletteIndex());
            assertFalse(levels.getRingManager().isCollected(ring));
            int featureZone = levels.getFeatureZoneId();
            int featureAct = levels.getFeatureActId();
            var water = GameServices.water();
            assertTrue(water.hasWater(featureZone, featureAct));
            assertEquals(160, water.getWaterLevelY(featureZone, featureAct));
            var nativeStage = new CdStageLoader(disc).readStage(CdStagePayload.R41A);
            var nativePalettes = new com.openggf.game.soniccd.zones.ttz.TtzWaterDataProvider(() -> nativeStage)
                    .getUnderwaterPalette(null, CdZone.TIDAL_TEMPEST.ordinal(), 0,
                            com.openggf.game.PlayerCharacter.SONIC_ALONE);
            var authoredPalettes = water.getUnderwaterPalette(featureZone, featureAct);
            assertEquals(4, authoredPalettes.length);
            for (int i = 0; i < 4; i++) assertTrue(nativePalettes[i].dataEquals(authoredPalettes[i]));
            for (int i = 0; i < 4; i++) loop.step();
            assertTrue(levels.getRingManager().isCollected(ring));
            assertEquals(160, water.getWaterLevelY(featureZone, featureAct));
        }
    }

    @Test void packagedExampleLoadsThroughTheProductionTrustedRuntime(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var jar = directory.resolve("cd-marker.jar");
        byte[] manifestBytes;
        try (var input = getClass().getResourceAsStream("/mods/cd-marker/openggf-mod.yaml")) {
            manifestBytes = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        try (var output = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry("META-INF/openggf-mod.yaml"));
            output.write(manifestBytes); output.closeEntry();
            for (String resource : List.of("example/soniccd/CdMarkerExampleMod.class",
                    "example/soniccd/CdMarkerExampleMod$Marker.class")) {
                output.putNextEntry(new java.util.jar.JarEntry(resource));
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                    java.util.Objects.requireNonNull(input).transferTo(output);
                }
                output.closeEntry();
            }
        }
        var manifest = new ModManifestParser(com.openggf.io.ModInputLimits.production()).parse(manifestBytes);
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(jar)));
        var descriptor = new ModDescriptor(jar, manifest, hash, true, List.of());
        // The test runner also compiles the fixture. A real engine parent has no creator classes.
        var engineOnlyParent = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("example.soniccd.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        var factory = new ModClassLoaderFactory(engineOnlyParent);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var runtime = factory.create(new EffectiveModCatalog(List.of(descriptor)), Set.of("cd-example"));
             var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    ignored -> new ModStateSaveResult.Saved(), ignored -> {}));
            var registered = runtime.newRegistrationPlan().registrations();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            assertEquals(1, registered.size());
            var module = registered.getFirst().patch().apply(new SonicCdGameModule(), null);
            var loop = boot.boot(disc, module, 0, 0);
            for (int i = 0; i < 4; i++) loop.step();
            assertInstanceOf(CdLevel.class, GameServices.level().getCurrentLevel());
            var marker = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getName().equals("example.soniccd.CdMarkerExampleMod$Marker"))
                    .findFirst().orElseThrow();
            assertNotSame(getClass().getClassLoader(), marker.getClass().getClassLoader(),
                    "the factory must come from the immutable mod snapshot, not the test classpath");
        }
    }

    private static final class Greeting extends AbstractObjectInstance {
        private final int[] updates;
        Greeting(ObjectSpawn spawn, int[] updates) { super(spawn, "CD greeting"); this.updates = updates; }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { updates[0]++; }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
