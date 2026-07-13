package com.openggf.mods.integration;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.launch.*;
import com.openggf.game.patch.*;
import com.openggf.game.save.SaveManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.io.ModInputLimits;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.TestEnvironment;
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
class TestPhase3SampleCharacterIntegration {
    private static final String CODE = "phase3-character:runner";
    @TempDir Path temp;

    @BeforeEach void resetState() { TestEnvironment.resetAll(); }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test
    void realPackagedCharacterCoversLaunchPhysicsSaveRewindAndDeterministicDisable()
            throws Exception {
        Path jar = buildSample();
        try (JarFile packed = new JarFile(jar.toFile())) {
            assertNotNull(packed.getEntry("art/runner.ggfp"));
        }
        try (CatalogFixture fixture = load(jar)) {
            SonicConfigurationService config = SonicConfigurationService.createStandalone(temp.resolve("config"));
            config.setConfigValue(SonicConfiguration.LAUNCH_S2_CROSS_GAME_SOURCE, "off");
            config.setConfigValue(SonicConfiguration.LAUNCH_S2_MAIN_CHARACTER, CODE);
            config.setConfigValue(SonicConfiguration.LAUNCH_S2_SIDEKICK, "none");
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, CODE);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            ModuleResolutionService resolver = new ModuleResolutionService(List.of(),
                    new EffectiveCatalogPatchEnablement(fixture.effective()),
                    new LogicalRomResolver(() -> null), config, ignored -> fixture.plan());
            var prepared = resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD);
            LaunchProfileStore store = new LaunchProfileStore(config, resolver, prepared);
            assertEquals(CODE, store.load(MasterTitleScreen.GameEntry.SONIC_2).mainCharacter());
            assertEquals("Phase Runner", store.displayValue(
                    store.load(MasterTitleScreen.GameEntry.SONIC_2),
                    LaunchProfile.Row.MAIN_CHARACTER, MasterTitleScreen.GameEntry.SONIC_2));

            GameModule base = new Sonic2GameModule();
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest("s2", CODE, List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            CharacterDefinition definition = resolved.getPlayableCharacterRegistry()
                    .find(CharacterKey.parsePersisted(CODE)).orElseThrow();
            assertEquals(PlayerCharacter.SONIC_ALONE, definition.behavesLike());
            assertEquals(SecondaryAbility.NONE, definition.secondaryAbility());
            assertFalse(definition.supportsSuperForm());
            assertFalse(definition.artSupplier().load(CODE).isEmpty());
            assertNotNull(definition.paletteSupplier().load(CODE));
            assertEquals((short) 0x500, resolved.getPhysicsProvider().getProfile(CODE).max());
            assertNotEquals(PhysicsProfile.SONIC_2_SONIC,
                    resolved.getPhysicsProvider().getProfile(CODE));

            TestEnvironment.configureGameModuleFixture(resolved);
            SpriteManager sprites = new SpriteManager(config);
            var team = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(
                    resolved, sprites, config);
            assertEquals(CODE, team.mainSprite().characterKey().persisted());
            assertTrue(team.sidekicks().isEmpty());
            assertEquals((short) 0x500, team.mainSprite().getPhysicsProfile().max());
            ClassLoader ownerLoader = fixture.runtime().loadOwned("phase3-character",
                    "example.phase3character.SampleCharacter").getClassLoader();
            assertSame(ownerLoader, team.mainSprite().getClass().getClassLoader());
            int originalX = team.mainSprite().getCentreX();
            var rewind = sprites.rewindSnapshottable();
            var snapshot = rewind.capture();
            team.mainSprite().setCentreX((short) (originalX + 40));
            rewind.restore(snapshot);
            assertEquals(originalX, sprites.getSprite(CODE).getCentreX());
            assertEquals(CODE, ((com.openggf.sprites.playable.AbstractPlayableSprite)
                    sprites.getSprite(CODE)).characterKey().persisted());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("act", 0); payload.put("zone", 0); payload.put("lives", 3);
            payload.put("mainCharacter", CODE); payload.put("sidekicks", List.of());
            payload.put("chaosEmeralds", List.of()); payload.put("clear", false);
            payload.put("progressCode", 1); payload.put("clearState", 0);
            SaveManager saves = new SaveManager(temp.resolve("saves"));
            saves.writeSlot("s2", 1, payload);
            var summary = saves.readSlotSummary("s2", 1,
                    (com.openggf.game.dataselect.DataSelectGameProfile)
                            resolved.getDataSelectHostProfile());
            assertEquals(CODE, summary.payload().get("mainCharacter"));
            assertEquals(List.of(), summary.payload().get("sidekicks"));

            assertFalse(resolver.availableMainCharactersForLaunch("s2",
                    ModuleResolutionService.LaunchPolicy.DETERMINISTIC).contains(CODE));
            assertSame(base, resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest("s2", CODE, List.of()),
                    ModuleResolutionService.LaunchPolicy.DETERMINISTIC));
        }
    }

    private Path buildSample() throws Exception {
        Path engine = temp.resolve("engine.jar"), sdk = temp.resolve("sdk.jar");
        createJar(Path.of("target/classes"), engine, entry ->
                !entry.startsWith("com/openggf/tools/modsdk/")
                        && !entry.startsWith("META-INF/openggf-mod-sdk/"));
        createJar(Path.of("target/classes"), sdk, entry ->
                entry.startsWith("com/openggf/tools/modsdk/")
                        || entry.startsWith("META-INF/openggf-mod-sdk/"));
        Path output = temp.resolve("sample-character");
        Path fixture = Path.of("src/test/resources/mods/sample-character-src").toAbsolutePath();
        List<String> command = System.getProperty("os.name", "").startsWith("Windows")
                ? List.of("powershell.exe", "-NoProfile", "-File",
                        fixture.resolve("build.ps1").toString(), engine.toString(), sdk.toString(),
                        output.toString())
                : List.of("sh", fixture.resolve("build.sh").toString(), engine.toString(),
                        sdk.toString(), output.toString());
        Process process = new ProcessBuilder(command).directory(Path.of("").toAbsolutePath().toFile())
                .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), log);
        Path jar = output.resolve("target/phase3-character-mod.jar");
        assertTrue(Files.isRegularFile(jar), log);
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
