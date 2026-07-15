package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SaveSessionLaunchTeamAccess;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRepositoryScanner;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real-S3K boot coverage for the native-Tails Flappy sample's atomic cutover. */
@Isolated
class TestSampleFlappyIntegration {
    private static final Path FLAPPY = Path.of(
            "src/test/resources/mods/sample-flappy-src/project");
    private static final ZoneKey.Mod FLAPPY_ZONE = new ZoneKey.Mod(
            "sample-flappy", "flappy-garden");

    @TempDir Path temp;

    @BeforeEach
    void resetState() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        TestEnvironment.resetAll();
    }

    @Test
    void bootsVisibleNativeTailsAtFixedAnchorAndMaintainsFlight() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null, "Sonic 3&K ROM unavailable");

        Path jar = buildFlappyMod();
        try (CatalogFixture fixture = load(jar); Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getAbsolutePath()),
                    "Configured Sonic 3&K ROM must be readable");

            GameModule base = new Sonic3kGameModule();
            base.createGame(rom);
            ModuleResolutionService resolver = resolver(fixture);
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest("s3k", "sonic", List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            var resolvedZone = resolved.getZoneRegistry().resolveZoneKey(FLAPPY_ZONE);
            assertTrue(resolvedZone.isPresent(),
                    "the S3K patch must publish its tagged Flappy destination");
            int zoneIndex = resolvedZone.getAsInt();

            GameplayLaunchTeam requiredTeam = resolved.getGameplayPolicyProvider()
                    .launchTeam(FLAPPY_ZONE).orElseThrow();
            assertEquals(CharacterKey.TAILS, requiredTeam.main());
            assertTrue(requiredTeam.sidekicks().isEmpty());
            SaveSessionContext launchContext = SaveSessionLaunchTeamAccess.withLaunchTeam(
                    SaveSessionContext.noSave("s3k",
                            new SelectedTeam("sonic", List.of()), zoneIndex, 0),
                    requiredTeam);

            EngineContext previous = EngineServices.current();
            EngineContext injected = withResolver(previous, resolver, isolatedRomManager());
            try {
                EngineServices.configure(injected);
                injected.roms().setRom(rom);
                GameplayModeContext gameplay = SessionManager.openGameplaySession(
                        base, resolved, StockGameDataSources.pinned(rom, base), launchContext);
                GameplaySessionFactory.attachManagers(gameplay, injected);
                injected.graphics().initHeadless();
                GameplayTeamBootstrap.BootstrappedTeam team =
                        GameplayTeamBootstrap.registerActiveTeam(
                                resolved, GameServices.sprites(), injected.configuration());
                AbstractPlayableSprite tails = team.mainSprite();
                assertEquals(CharacterKey.TAILS, tails.characterKey());
                assertTrue(team.sidekicks().isEmpty());

                Camera camera = GameServices.camera();
                camera.setFocusedSprite(tails);
                camera.setFrozen(false);
                GameServices.level().loadZoneAndAct(zoneIndex, 0);
                GroundSensor.setLevelManager(GameServices.level());
                camera.updatePosition(true);

                int fixedCameraX = camera.getX();
                int fixedCameraY = camera.getY();
                assertEquals(camera.getMinX(), camera.getMaxX());
                assertEquals(camera.getMinY(), camera.getMaxY());

                HeadlessTestRunner runner = new HeadlessTestRunner(tails);
                runner.stepIdleFrames(1);
                int anchorX = fixedCameraX + 96;
                assertEquals(anchorX, tails.getCentreX());
                assertEquals(fixedCameraY + 112, tails.getCentreY());
                assertFalse(tails.isHidden(), "native Tails must remain visible");
                assertFalse(tails.isObjectControlled(),
                        "native flight must not seize object control");
                assertTrue(tails.getTailsFlightController().isActive(),
                        "the controller must activate native Tails flight");
                assertEquals(0xF0, tails.getDoubleJumpProperty() & 0xFF,
                        "the MGZ2 flight-time refill must be armed on activation");

                for (int i = 0; i < 12; i++) {
                    runner.stepFrame(false, false, false, true, i == 0);
                    assertEquals(anchorX, tails.getCentreX(),
                            "horizontal input and native drift must not move Tails");
                    assertEquals(0, tails.getXSpeed());
                    assertEquals(0, tails.getGSpeed());
                    assertEquals(0xF0, tails.getDoubleJumpProperty() & 0xFF);
                }
                assertEquals(fixedCameraX, camera.getX());
                assertEquals(fixedCameraY, camera.getY());
                assertFalse(tails.isHidden());
                assertFalse(tails.isObjectControlled());
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
            }
        }
    }

    @Test
    void firstForwardUpdateCreatesSixIndependentEntriesAndCoversSuper32By9() throws Exception {
        withLaunchedGameplay("SUPER_32_9", game -> {
            assertEquals(800, game.camera().getWidth(),
                    "viewport-width configuration must reach the active camera");
            assertEquals(0, pipes(game.objects()).size(),
                    "the controller constructor and rewind recreation must not spawn pipes");

            game.runner().stepIdleFrames(1);
            List<ObjectInstance> initialPipes = pipes(game.objects()).stream()
                    .sorted(Comparator.comparingInt(TestSampleFlappyIntegration::centreX))
                    .toList();
            assertEquals(6, initialPipes.size());
            assertEquals(game.camera().getX() + game.camera().getWidth() + 64,
                    centreX(initialPipes.getFirst()));
            for (int index = 1; index < initialPipes.size(); index++) {
                assertEquals(224,
                        centreX(initialPipes.get(index)) - centreX(initialPipes.get(index - 1)),
                        "adjacent pipe centres must cover the viewport at the fixed spacing");
            }
            assertEquals(6, new HashSet<>(initialPipes.stream()
                    .map(pipe -> objectId(game.objects(), pipe)).toList()).size(),
                    "every independent dynamic entry needs a distinct stable identity");
            Map<ObjectRefId, Integer> initialCentres = new HashMap<>();
            for (ObjectInstance pipe : initialPipes) {
                initialCentres.put(objectId(game.objects(), pipe), centreX(pipe));
            }

            ObjectManagerSnapshot snapshot = game.objects().rewindSnapshottable().capture();
            assertEquals(1, snapshot.slots().size(), "the level layout contains only its controller");
            assertEquals("example.flappysample.FlappyController",
                    snapshot.slots().getFirst().className());
            assertEquals(6, snapshot.dynamicObjects().size());

            game.runner().stepIdleFrames(1);
            List<ObjectInstance> movedPipes = pipes(game.objects());
            assertEquals(6, movedPipes.size());
            assertEquals(initialCentres.keySet(), new HashSet<>(movedPipes.stream()
                    .map(pipe -> objectId(game.objects(), pipe)).toList()));
            for (ObjectInstance pipe : movedPipes) {
                ObjectRefId id = objectId(game.objects(), pipe);
                assertEquals(initialCentres.get(id) - 2, centreX(pipe),
                        "0x200 fixed-point speed must move every stable entry exactly two pixels");
            }

            game.runner().stepIdleFrames(2);
            assertEquals(6, pipes(game.objects()).size(),
                    "resizing and later updates must not grow the fixed pool");
        });
    }

    @Test
    void recycleMovesTheSameEntryAndAdvancesCounterPermutation() throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            ObjectInstance pipe = pipes(game.objects()).stream()
                    .min(Comparator.comparingInt(TestSampleFlappyIntegration::centreX))
                    .orElseThrow();
            ObjectRefId id = objectId(game.objects(), pipe);
            ObjectInstance controller = controller(game.objects());
            int before = invokeInt(controller, "generationCounter");

            int pixelsPastViewport = centreX(pipe) - game.camera().getX() + 17;
            invoke(pipe, "advance", int.class, pixelsPastViewport << 8);
            invoke(pipe, "consumeGate");
            assertTrue(invokeBoolean(pipe, "gateConsumed"));

            game.runner().stepIdleFrames(1);

            ObjectInstance rightmost = pipes(game.objects()).stream()
                    .max(Comparator.comparingInt(TestSampleFlappyIntegration::centreX))
                    .orElseThrow();
            assertEquals(id, objectId(game.objects(), rightmost),
                    "recycling must reposition the same live entry");
            assertEquals(before + 1, invokeInt(controller, "generationCounter"));
            assertEquals(expectedVariant(before), invokeInt(rightmost, "gapVariant"));
            assertFalse(invokeBoolean(rightmost, "gateConsumed"));
            assertEquals(6, pipes(game.objects()).size());
        });
    }

    private void withLaunchedGameplay(String aspect, GameplayAssertion assertion) throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null, "Sonic 3&K ROM unavailable");

        Path jar = buildFlappyMod();
        try (CatalogFixture fixture = load(jar); Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getAbsolutePath()),
                    "Configured Sonic 3&K ROM must be readable");
            GameModule base = new Sonic3kGameModule();
            base.createGame(rom);

            SonicConfigurationService configuration = SonicConfigurationService.createStandalone();
            if (aspect != null) {
                configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect);
                configuration.resolveDisplayAspect();
            }
            ModuleResolutionService resolver = resolver(fixture, configuration);
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest("s3k", "sonic", List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            int zoneIndex = resolved.getZoneRegistry().resolveZoneKey(FLAPPY_ZONE).orElseThrow();
            GameplayLaunchTeam requiredTeam = resolved.getGameplayPolicyProvider()
                    .launchTeam(FLAPPY_ZONE).orElseThrow();
            SaveSessionContext launchContext = SaveSessionLaunchTeamAccess.withLaunchTeam(
                    SaveSessionContext.noSave("s3k",
                            new SelectedTeam("sonic", List.of()), zoneIndex, 0),
                    requiredTeam);

            EngineContext previous = EngineServices.current();
            EngineContext injected = withResolver(
                    previous, resolver, isolatedRomManager(), configuration);
            try {
                EngineServices.configure(injected);
                injected.roms().setRom(rom);
                GameplayModeContext gameplay = SessionManager.openGameplaySession(
                        base, resolved, StockGameDataSources.pinned(rom, base), launchContext);
                GameplaySessionFactory.attachManagers(gameplay, injected);
                injected.graphics().initHeadless();
                GameplayTeamBootstrap.BootstrappedTeam team =
                        GameplayTeamBootstrap.registerActiveTeam(
                                resolved, GameServices.sprites(), configuration);
                AbstractPlayableSprite tails = team.mainSprite();
                Camera camera = GameServices.camera();
                camera.setFocusedSprite(tails);
                camera.setFrozen(false);
                GameServices.level().loadZoneAndAct(zoneIndex, 0);
                GroundSensor.setLevelManager(GameServices.level());
                camera.updatePosition(true);
                assertion.verify(new LaunchedGameplay(tails, camera,
                        new HeadlessTestRunner(tails), GameServices.level().getObjectManager()));
            } finally {
                configuration.clearSessionOverrides();
                SessionManager.clear();
                EngineServices.configure(previous);
            }
        }
    }

    private static List<ObjectInstance> pipes(ObjectManager objects) {
        return objects.getActiveObjects().stream()
                .filter(object -> object.getClass().getName()
                        .equals("example.flappysample.FlappyPipe"))
                .toList();
    }

    private static ObjectInstance controller(ObjectManager objects) {
        return objects.getActiveObjects().stream()
                .filter(object -> object.getClass().getName()
                        .equals("example.flappysample.FlappyController"))
                .findFirst().orElseThrow();
    }

    private static ObjectRefId objectId(ObjectManager objects, ObjectInstance object) {
        return objects.captureIdentityContext().requireIdentityTable().idFor(object);
    }

    private static int centreX(Object object) {
        try {
            return invokeInt(object, "centreX");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static int invokeInt(Object target, String name) throws Exception {
        return (int) target.getClass().getMethod(name).invoke(target);
    }

    private static boolean invokeBoolean(Object target, String name) throws Exception {
        return (boolean) target.getClass().getMethod(name).invoke(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        target.getClass().getMethod(name).invoke(target);
    }

    private static void invoke(Object target, String name, Class<?> parameterType, Object argument)
            throws Exception {
        Method method = target.getClass().getMethod(name, parameterType);
        method.invoke(target, argument);
    }

    private static int expectedVariant(int generation) {
        return switch (Math.floorMod(generation, 5)) {
            case 0 -> 2;
            case 1 -> 0;
            case 2 -> 4;
            case 3 -> 1;
            default -> 3;
        };
    }

    private Path buildFlappyMod() throws Exception {
        Path output = temp.resolve("flappy-exploded");
        Files.createDirectories(output);
        copyTree(FLAPPY.resolve("src/main/resources"), output);
        compileJava(FLAPPY.resolve("src/main/java"), output);
        assertCli("convert", "art", "--image", FLAPPY.resolve("src/main/mod/pipe.png").toString(),
                "--sheet", FLAPPY.resolve("src/main/mod/pipe-sheet.yaml").toString(),
                "--out", output.resolve("art/pipe.ggfs").toString());
        Path level = materializeLevel(FLAPPY.resolve("src/main/mod/level-source"), "flappy-level");
        assertCli("convert", "level", "--from-export", level.toString(),
                "--out", output.resolve("levels/flappy").toString());
        Path jar = temp.resolve("sample-flappy.jar");
        assertCli("package", "--input", output.toString(), "--out", jar.toString());
        assertTrue(Files.isRegularFile(jar));
        return jar;
    }

    private Path materializeLevel(Path source, String name) throws Exception {
        Path output = temp.resolve(name);
        copyTree(source, output);
        Path encoded = output.resolve("binary-assets.properties");
        java.util.Properties assets = new java.util.Properties();
        try (var input = Files.newInputStream(encoded)) {
            assets.load(input);
        }
        Files.delete(encoded);
        for (String file : assets.stringPropertyNames()) {
            Files.write(output.resolve(file),
                    java.util.Base64.getDecoder().decode(assets.getProperty(file)));
        }
        return output;
    }

    private static void compileJava(Path source, Path output) throws Exception {
        List<String> arguments = new ArrayList<>(List.of("--release", "21", "-classpath",
                Path.of("target/classes").toAbsolutePath().toString(), "-d", output.toString()));
        try (var files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java")).sorted()
                    .map(Path::toString).forEach(arguments::add);
        }
        int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, source.toString());
    }

    private static void copyTree(Path source, Path output) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path destination = output.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
    }

    private static void assertCli(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(arguments, new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
    }

    private CatalogFixture load(Path jar) throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        Path packed = repo.resolve(jar.getFileName());
        Files.copy(jar, packed);
        ModRepositoryScanner scanner = new DefaultModRepositoryScanner();
        var scanned = scanner.scan(repo.toAbsolutePath().normalize());
        var validated = new ModCatalogValidator(repo.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        ModState state = new ModState(1, List.of(
                new ModState.Entry(descriptor.manifest().id(), true, 0, true,
                        descriptor.sha256())));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        assertEquals(1, catalog.effective().orderedEnabled().size());
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog.effective(), Set.of(descriptor.manifest().id()));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(),
                new ModRuntimeFindingStore(), owners -> new ModStateSaveResult.Saved(),
                owners -> { }));
        return new CatalogFixture(runtime, catalog.effective(), runtime.newRegistrationPlan());
    }

    private ModuleResolutionService resolver(CatalogFixture fixture) {
        return resolver(fixture, SonicConfigurationService.createStandalone());
    }

    private ModuleResolutionService resolver(CatalogFixture fixture,
                                             SonicConfigurationService configuration) {
        return new ModuleResolutionService(List.of(),
                new EffectiveCatalogPatchEnablement(fixture.effective()),
                new LogicalRomResolver(() -> null), configuration,
                ignored -> fixture.plan());
    }

    private static EngineContext withResolver(EngineContext old, ModuleResolutionService resolver,
                                              RomManager roms) {
        return withResolver(old, resolver, roms, SonicConfigurationService.createStandalone());
    }

    private static EngineContext withResolver(EngineContext old, ModuleResolutionService resolver,
                                              RomManager roms,
                                              SonicConfigurationService configuration) {
        return new EngineContext(configuration, old.graphics(),
                old.audio(), roms, old.profiler(), old.debugOverlay(), old.playbackDebug(),
                old.romDetection(), old.crossGameFeatures(), resolver);
    }

    private static RomManager isolatedRomManager() throws Exception {
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private record CatalogFixture(ModRuntime runtime,
                                  com.openggf.mods.EffectiveModCatalog effective,
                                  ModuleResolutionService.PatchPlan plan)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            runtime.close();
        }
    }

    private record LaunchedGameplay(AbstractPlayableSprite tails, Camera camera,
                                    HeadlessTestRunner runner, ObjectManager objects) {}

    @FunctionalInterface
    private interface GameplayAssertion {
        void verify(LaunchedGameplay game) throws Exception;
    }
}
