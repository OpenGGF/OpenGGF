package com.openggf.mods.code;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSeekAwareEngineStepper;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
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
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end proof that the maintained remix sample borrows, packages, and rewinds real S2 art. */
@Isolated
class TestSampleRomArtRemixIntegration {
    private static final Path SAMPLE = Path.of(
            "src/test/resources/mods/sample-rom-art-remix-src/project");
    private static final String ART_KEY = "sample-rom-art-remix:tails-flight";
    private static final String OBJECT_CLASS = "example.romartremix.TailsFlightArtObject";

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
    void materializesRealTailsArtForDefaultSonicAndRewindsDisplayObject() throws Exception {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "ROM-art remix integration requires a configured S2 ROM");

        Path jar = buildSample();
        assertPackageContainsNoRomOrMaterializedArt(jar);

        try (CatalogFixture fixture = load(jar); Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getAbsolutePath()), "Configured S2 ROM must be readable");
            System.out.println("sample-rom-art-remix package sha256=" + fixture.sha256());

            GameModule base = new Sonic2GameModule();
            base.createGame(rom);
            ModuleResolutionService resolver = resolver(fixture);
            GameplayLaunchRequest launchRequest = new GameplayLaunchRequest("s2", "sonic", List.of());
            GameModule resolved = resolver.resolveForLaunch(base, launchRequest,
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            int zoneIndex = resolved.getZoneRegistry()
                    .resolveZoneKey(ZoneKey.mod("sample-rom-art-remix", "rom-art-gallery"))
                    .orElseThrow();

            GameplayLaunchTeam launchTeam = resolved.getGameplayPolicyProvider().launchTeam(
                    ZoneKey.mod("sample-rom-art-remix", "rom-art-gallery"))
                    .orElseGet(() -> new GameplayLaunchTeam(
                            CharacterKey.parsePersisted(launchRequest.mainCharacter()), List.of()));
            assertEquals(CharacterKey.SONIC, launchTeam.main(),
                    "the sample must work with S2's default Sonic-main team");

            ObjectSpriteSheet sheet = resolved.getObjectArtProvider().getSheet(ART_KEY);
            assertNotNull(sheet, "the resolver must materialize the borrowed ROM sheet");
            assertTrue(sheet.getFrameCount() > 95,
                    "borrowed sheet must include Tails flight frames 94 and 95");
            assertFalse(sheet.getFrame(94).pieces().isEmpty(), "frame 94 must have mapping pieces");
            assertFalse(sheet.getFrame(95).pieces().isEmpty(), "frame 95 must have mapping pieces");
            SpriteMappingPiece firstPiece = sheet.getFrame(94).pieces().getFirst();
            assertEquals(0, firstPiece.paletteIndex(),
                    "Tails' mapping piece must address the shared Sonic/Tails palette line");
            assertTrue(pieceReferencesNonzeroPixel(sheet, firstPiece),
                    "frame 94's first piece must reference visible ROM pattern data");
            assertDefaultPaletteLineMatchesRom(rom,
                    resolved.getCrossGameDonorProvider().loadCharacterPalette(
                            RomByteReader.fromRom(rom), launchTeam.main().persisted()));

            EngineContext previous = EngineServices.current();
            EngineContext injected = withResolver(previous, resolver, isolatedRomManager());
            try {
                EngineServices.configure(injected);
                injected.roms().setRom(rom);
                GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                        injected, base, ModuleResolutionService.LaunchPolicy.STANDARD);
                GameplaySessionFactory.attachManagers(gameplay, injected);
                injected.graphics().initHeadless();
                var team = GameplayTeamBootstrap.registerActiveTeam(
                        SessionManager.requireCurrentGameModule(), GameServices.sprites(),
                        injected.configuration());
                GameServices.camera().setFocusedSprite(team.mainSprite());
                GameServices.camera().setFrozen(false);
                GameServices.level().loadZoneAndAct(zoneIndex, 0);
                GroundSensor.setLevelManager(GameServices.level());
                GameServices.camera().updatePosition(true);

                assertDefaultPaletteLineMatchesRom(rom,
                        GameServices.level().getCurrentLevel().getPalette(0));

                ObjectInstance initialObject = displayObject();
                var initialId = GameServices.level().getObjectManager().captureIdentityContext()
                        .requireIdentityTable().idFor(initialObject);
                int initialTick = animTick(initialObject);
                int initialFrame = displayFrame(initialObject);

                HeadlessTestRunner runner = new HeadlessTestRunner(team.mainSprite());
                TenFrameInputSource inputs = new TenFrameInputSource();
                RewindController rewind = new RewindController(
                        gameplay.getRewindRegistry(), new InMemoryKeyframeStore(), inputs,
                        new RunnerStepper(runner, inputs), 1);
                for (int i = 0; i < 9; i++) {
                    rewind.step();
                }
                assertNotEquals(initialTick, animTick(displayObject()),
                        "nine real frames must advance the object's captured animation scalar");

                rewind.seekTo(0);
                ObjectInstance restoredObject = displayObject();
                var restoredId = GameServices.level().getObjectManager().captureIdentityContext()
                        .requireIdentityTable().idFor(restoredObject);
                assertEquals(initialId, restoredId,
                        "layout reconstruction must preserve the object's rewind identity");
                assertEquals(initialFrame, displayFrame(restoredObject),
                        "backward seek must restore the displayed Tails flight frame");
                assertEquals(initialTick, animTick(restoredObject),
                        "backward seek must restore the sample's non-final animation scalar");
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
            }
        }
    }

    private static void assertDefaultPaletteLineMatchesRom(Rom rom, Palette actual) throws Exception {
        byte[] romBytes = rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR,
                Palette.PALETTE_SIZE_IN_ROM);
        Palette expected = new Palette();
        expected.fromSegaFormat(romBytes);
        for (int index = 0; index < Palette.PALETTE_SIZE; index++) {
            Palette.Color expectedColor = expected.getColor(index);
            Palette.Color actualColor = actual.getColor(index);
            assertEquals(expectedColor.r, actualColor.r, "line 0 red at index " + index);
            assertEquals(expectedColor.g, actualColor.g, "line 0 green at index " + index);
            assertEquals(expectedColor.b, actualColor.b, "line 0 blue at index " + index);
        }
        // This exact all-index assertion is intentionally for the default Sonic team. In the
        // cross-game Knuckles-main lock-on, Sonic2Level substitutes only indices 2 through 5;
        // the sample therefore documents that team as the one real palette caveat.
    }

    private static boolean pieceReferencesNonzeroPixel(
            ObjectSpriteSheet sheet, SpriteMappingPiece piece) {
        Pattern[] patterns = sheet.getPatterns();
        int tileCount = piece.widthTiles() * piece.heightTiles();
        for (int tile = piece.tileIndex(); tile < piece.tileIndex() + tileCount; tile++) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    if (patterns[tile].getPixel(x, y) != 0) return true;
                }
            }
        }
        return false;
    }

    private static ObjectInstance displayObject() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(OBJECT_CLASS))
                .findFirst().orElseThrow(() -> new AssertionError("display object is not active"));
    }

    private static int animTick(ObjectInstance object) throws Exception {
        Field field = object.getClass().getDeclaredField("animTick");
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static int displayFrame(ObjectInstance object) throws Exception {
        return 94 + animTick(object) / 4;
    }

    private Path buildSample() throws Exception {
        Path output = temp.resolve("rom-art-remix-exploded");
        Files.createDirectories(output);
        copyTree(SAMPLE.resolve("src/main/resources"), output);
        compileJava(SAMPLE.resolve("src/main/java"), output);
        Path level = materializeLevel(SAMPLE.resolve("src/main/mod/level-source"));
        assertCli("convert", "level", "--from-export", level.toString(),
                "--out", output.resolve("levels/rom-art-gallery").toString());
        Path jar = temp.resolve("sample-rom-art-remix.jar");
        assertCli("package", "--input", output.toString(), "--out", jar.toString());
        assertTrue(Files.isRegularFile(jar));
        return jar;
    }

    private Path materializeLevel(Path source) throws Exception {
        Path output = temp.resolve("rom-art-gallery-level");
        copyTree(source, output);
        Path encoded = output.resolve("binary-assets.properties");
        Properties assets = new Properties();
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

    private static void assertPackageContainsNoRomOrMaterializedArt(Path jar) throws Exception {
        try (JarFile packed = new JarFile(jar.toFile())) {
            List<String> entries = packed.stream().map(java.util.jar.JarEntry::getName).toList();
            assertTrue(entries.stream().noneMatch(name -> name.toLowerCase().endsWith(".gen")),
                    "the package must never contain a ROM image");
            assertFalse(entries.contains("art/tails-flight.ggfs"),
                    "ROM-derived art must be materialized only at install/resolve time");
        }
    }

    private static void compileJava(Path source, Path output) throws Exception {
        List<String> arguments = new ArrayList<>(List.of(
                "--release", "21", "-classpath",
                Path.of("target/classes").toAbsolutePath().toString(),
                "-d", output.toString()));
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
        ModState state = new ModState(1, List.of(new ModState.Entry(
                descriptor.manifest().id(), true, 0, true, descriptor.sha256())));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        assertEquals(1, catalog.effective().orderedEnabled().size());
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog.effective(), Set.of(descriptor.manifest().id()));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return new CatalogFixture(runtime, catalog.effective(), runtime.newRegistrationPlan(),
                descriptor.sha256());
    }

    private ModuleResolutionService resolver(CatalogFixture fixture) {
        return new ModuleResolutionService(List.of(),
                new EffectiveCatalogPatchEnablement(fixture.effective()),
                new LogicalRomResolver(() -> null), SonicConfigurationService.createStandalone(),
                ignored -> fixture.plan());
    }

    private static EngineContext withResolver(
            EngineContext old, ModuleResolutionService resolver, RomManager roms) {
        return new EngineContext(SonicConfigurationService.createStandalone(), old.graphics(),
                old.audio(), roms, old.profiler(), old.debugOverlay(), old.playbackDebug(),
                old.romDetection(), old.crossGameFeatures(), resolver);
    }

    private static RomManager isolatedRomManager() throws Exception {
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static final class RunnerStepper implements RewindSeekAwareEngineStepper {
        private final HeadlessTestRunner runner;
        private final InputSource inputs;

        private RunnerStepper(HeadlessTestRunner runner, InputSource inputs) {
            this.runner = runner;
            this.inputs = inputs;
        }

        @Override
        public void step(Bk2FrameInput input) {
            runner.stepFrame(false, false, false, false, false);
        }

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            runner.primeInputState(inputs.read(frame));
        }
    }

    private static final class TenFrameInputSource implements InputSource {
        @Override
        public int frameCount() {
            return 10;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "rom-art-remix");
        }
    }

    private record CatalogFixture(
            ModRuntime runtime,
            com.openggf.mods.EffectiveModCatalog effective,
            ModuleResolutionService.PatchPlan plan,
            String sha256) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            runtime.close();
        }
    }
}
