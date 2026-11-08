package com.openggf.mods.integration;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.launch.*;
import com.openggf.game.patch.*;
import com.openggf.game.save.SaveManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.io.ModInputLimits;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestMicaCharacterIntegration {
    private static final String CODE = "mica-and-talon:mica";
    @TempDir Path temp;

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void sonic1() throws Exception { acceptance(new com.openggf.game.sonic1.Sonic1GameModule(), "s1", 1, 0); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void sonic2() throws Exception { acceptance(new Sonic2GameModule(), "s2", 1, 1); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void sonic3k() throws Exception { acceptance(new com.openggf.game.sonic3k.Sonic3kGameModule(), "s3k", 1, 0); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void titleRouteKeepsExplicitDevelopmentCharacter() throws Exception {
        Path input = temp.resolve("title.txt");
        com.openggf.tools.InputLogAuthorTool.main(new String[]{"--inline",
                "121 -; 4 S; 18 -; 5 S; 25 -; 5 S; 200 -; 1 D; 60 -; 1 S; 240 -",
                "--out", input.toString()});
        String previous = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("capture-saves").toString());
        try {
            com.openggf.tools.GameplayCaptureTool.run(
                    com.openggf.tools.GameplayCaptureTool.Arguments.parse(new String[]{
                            "--game", "s3k", "--zone", "aiz", "--act", "1", "--mod", buildSample().toString(),
                            "--main", CODE, "--extra-teams", CODE, "--title-screen", "--interactive-pacing",
                            "--input", input.toString(), "--every", "400", "--no-video",
                            "--out-dir", temp.resolve("capture").toString()}));
            var rows = Files.readAllLines(temp.resolve("capture/state.csv"));
            assertTrue(rows.stream().anyMatch(row -> row.contains(",DATA_SELECT,")));
            // Session teardown retires the world; inspect the final native mapping frame.
            // MICA's four idle frames are 0..3; stock Sonic's idle is 186.
            var last = rows.getLast().split(",");
            assertEquals("LEVEL", last[19]);
            assertTrue(Integer.parseInt(last[12]) < 4, rows.getLast());
        } finally {
            if (previous == null) System.clearProperty("openggf.saveRoot");
            else System.setProperty("openggf.saveRoot", previous);
        }
    }

    private void acceptance(GameModule base, String game, int waterZone, int waterAct) throws Exception {
        try (CatalogFixture fixture = load(buildSample())) {
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, CODE);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            var resolver = new ModuleResolutionService(List.of(),
                    new EffectiveCatalogPatchEnablement(fixture.effective()),
                    new LogicalRomResolver(() -> null), config, ignored -> fixture.plan());
            assertTrue(resolver.availableMainCharactersForLaunch(game,
                    ModuleResolutionService.LaunchPolicy.STANDARD).contains(CODE));
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest(game, CODE, List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            GameModuleRegistry.setCurrent(resolved);
            com.openggf.game.session.SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var playable = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0, 0).build().sprite();
            assertEquals(CODE, playable.characterKey().persisted());
            assertEquals(PlayerCharacter.SONIC_ALONE,
                    com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
            assertNotNull(GameServices.level().getCurrentLevel());
            assertNotNull(playable.getSpriteRenderer());
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            assertEquals(PlayerCharacter.SONIC_AND_TAILS,
                    com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            var sprites = GameServices.sprites();
            var rewind = sprites.rewindSnapshottable();
            var state = rewind.capture();
            int x = playable.getCentreX();
            playable.setCentreX((short) (x + 40));
            rewind.restore(state);
            assertEquals(x, sprites.getSprite(CODE).getCentreX());
            assertEquals(CODE, ((com.openggf.sprites.playable.AbstractPlayableSprite)
                    sprites.getSprite(CODE)).characterKey().persisted());

            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("act",0); payload.put("zone",0); payload.put("lives",3);
            payload.put("mainCharacter",CODE); payload.put("sidekicks",List.of());
            payload.put("chaosEmeralds",List.of()); payload.put("clear",false);
            payload.put("progressCode",1); payload.put("clearState",0);
            SaveManager saves = new SaveManager(temp.resolve("saves"));
            saves.writeSlot(game,1,payload);
            var profile = (com.openggf.game.dataselect.DataSelectGameProfile) resolved.getDataSelectHostProfile();
            assertEquals(CODE, saves.readSlotSummary(game,1,profile).payload().get("mainCharacter"));
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    saves.readSlotSummary(game,1,profile).payload().get("mainCharacter").toString());
            var reloaded = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(resolved,
                    new SpriteManager(config), config);
            assertEquals(CODE, reloaded.mainSprite().characterKey().persisted());
            byte[] saved = Files.readAllBytes(temp.resolve("saves").resolve(game).resolve("slot1.json"));

            // Load real host water tables and exercise the mod palette's derived-row policy.
            GameServices.level().loadZoneAndAct(waterZone, waterAct);
            assertEquals(CODE, ((com.openggf.sprites.playable.AbstractPlayableSprite)
                    GameServices.sprites().getSprite(CODE)).characterKey().persisted());
            var water = resolved.getWaterDataProvider().getUnderwaterPalette(
                    TestEnvironment.currentRom(), game.equals("s2") ? 13 : waterZone, waterAct, PlayerCharacter.SONIC_ALONE);
            assertNotNull(water);
            var dry = GameServices.level().getCurrentLevel().getPalette(0);
            var definition = resolved.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(CODE)).orElseThrow();
            var custom = definition.paletteSupplier().load(CODE);
            var current = (com.openggf.sprites.playable.AbstractPlayableSprite) GameServices.sprites().getSprite(CODE);
            var context = current.getSpriteRenderer().getRenderContext();
            assertNotNull(context);
            assertTrue(custom.dataEquals(context.getPalette(0)));
            assertNull(context.getUnderwaterPalette(0), "Absent authored palette retains live host derivation");
            var derived = com.openggf.graphics.RenderContext.deriveUnderwaterPalette(custom, dry, water[0]);
            assertFalse(custom.dataEquals(derived), "Water must change the jade art");

            // Disabled owner: use the stock registry and preserve the persisted slot.
            var findings = new ArrayList<com.openggf.game.session.GameplayTeamBootstrap.CharacterFinding>();
            var fallback = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(base,
                    resolved.getPlayableCharacterRegistry(),
                    CharacterAvailability.dynamic(owner -> owner.equals("mica-and-talon"), owner -> false),
                    new SpriteManager(config), config, (short) 100, (short) 100, findings::add);
            assertEquals(CharacterKey.SONIC, fallback.mainSprite().characterKey());
            assertFalse(findings.isEmpty());
            assertEquals(CODE, findings.getFirst().requestedCode());
            assertEquals(PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER, findings.getFirst().reason());
            assertEquals(CODE, saves.readSlotSummary(game,1,profile).payload().get("mainCharacter"));
            assertArrayEquals(saved, Files.readAllBytes(temp.resolve("saves").resolve(game).resolve("slot1.json")));
            assertTrue(fixture.runtime().runtimeDisabledOwners().isEmpty());
        }
    }

    private Path buildSample() throws Exception {
        Path project = Path.of("examples/mica-and-talon"), classes = temp.resolve("classes");
        Files.createDirectories(classes.resolve("art"));
        var args = new ArrayList<String>(List.of("--release","25","-classpath",
                System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var sources=Files.walk(project.resolve("src/main/java"))) {
            sources.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(args::add);
        }
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),
                classes.resolve("META-INF/openggf-mod.yaml"));
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"convert","art","--playable",
                "--image",project.resolve("src/main/mod/mica.png").toString(),
                "--sheet",project.resolve("src/main/mod/mica-sheet.yaml").toString(),
                "--out",classes.resolve("art/mica.ggfp").toString()},System.out));
        Path jar = temp.resolve("mica-and-talon.jar");
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"package","--input",
                classes.toString(),"--out",jar.toString(),"--warnings","error"},System.out));
        try (JarFile packed = new JarFile(jar.toFile())) {
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(
                    packed.getInputStream(packed.getJarEntry("art/mica.ggfp")).readAllBytes());
            assertEquals(96, sheet.frames().size());
            assertEquals(17, sheet.animations().size());
            assertFalse(sheet.animations().containsKey("HOMING_DIVE"));
            assertEquals(List.of(8, 8, 8, 8), sheet.animations().get("WALK").slopes()
                    .stream().map(List::size).toList());
            var base = com.openggf.level.objects.BakedSheetReader.read(sheet.baseSheetV1());
            assertArrayEquals(new int[]{0x0000, 0x0222, 0x0440, 0x0660, 0x0680, 0x08A2,
                    0x0AC6, 0x08CE, 0x06AC, 0x068A, 0x044E, 0x022A, 0x0EEE, 0x0CCC,
                    0x088E, 0x0024}, base.palette().orElseThrow().colors());
            assertTrue(sheet.frames().stream().allMatch(f -> f.runs().stream()
                    .mapToInt(r -> r.tileCount()).sum() <= 36));
        }
        return jar;
    }

    private CatalogFixture load(Path jar) throws Exception {
        Path repo = temp.resolve("repo"); Files.createDirectories(repo);
        Path copy = repo.resolve(jar.getFileName()); Files.copy(jar, copy);
        var scanned = new DefaultModRepositoryScanner().scan(repo.toAbsolutePath().normalize());
        var validated = new ModCatalogValidator(repo.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        ModState state = new ModState(1, List.of(new ModState.Entry(
                descriptor.manifest().id(), true, 0, true, descriptor.sha256())));
        EffectiveModCatalog effective = new EffectiveCatalogBuilder()
                .build(validated.entries(), state).effective();
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(effective, Set.of(descriptor.manifest().id()));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return new CatalogFixture(runtime, effective, runtime.newRegistrationPlan());
    }

    private static void createJar(Path root, Path jar, Predicate<String> include) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String entry = root.relativize(file).toString().replace('\\', '/');
                if (!include.test(entry)) continue;
                out.putNextEntry(new JarEntry(entry)); Files.copy(file, out); out.closeEntry();
            }
        }
    }

    private record CatalogFixture(ModRuntime runtime, EffectiveModCatalog effective,
                                  ModuleResolutionService.PatchPlan plan) implements AutoCloseable {
        @Override public void close() throws Exception { runtime.close(); }
    }
}
