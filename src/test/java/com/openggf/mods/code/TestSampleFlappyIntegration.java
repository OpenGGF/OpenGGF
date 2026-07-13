package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.io.ModInputLimits;
import com.openggf.level.render.PatternSpriteRenderer;
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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end ROM-gated playthrough of the {@code sample-flappy} gallery mod: seizes the
 * player, flies under gravity/flap physics, force-scrolls the camera, renders the ROM-baked
 * Tails "bird", scores a pipe pass as a ring, and hands the player back to engine-owned
 * hurt/death on contact. Modeled structurally on
 * {@code com.openggf.mods.integration.TestPhase2SampleModIntegration}'s real-build/real-load
 * plumbing, but uses the lighter {@code javac + GgfModCli} materialization path proven by
 * {@code TestSampleModsPackage} instead of a subprocess Maven build.
 */
@Isolated
class TestSampleFlappyIntegration {
    private static final Path S2_ROM = Path.of("s2.gen");
    private static final Path FLAPPY = Path.of("src/test/resources/mods/sample-flappy-src/project");

    @TempDir Path temp;

    @BeforeEach void resetState() { TestEnvironment.resetAll(); }

    @AfterEach void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        TestEnvironment.resetAll();
    }

    @Test
    void flappyControllerFliesScoresAndHandsOffHurtDeath() throws Exception {
        assumeTrue(Files.exists(S2_ROM), "S2 ROM is not available in this environment");

        Path jar = buildFlappyMod();
        try (CatalogFixture fixture = load(jar); Rom rom = new Rom()) {
            assumeTrue(rom.open(S2_ROM.toString()), "S2 ROM could not be opened in this environment");

            GameModule base = new Sonic2GameModule();
            base.createGame(rom);
            ModuleResolutionService resolver = resolver(fixture);
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest("s2", "sonic", List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);

            int zoneIndex = resolved.getZoneRegistry()
                    .resolveZoneKey(ZoneKey.mod("sample-flappy", "flappy-garden")).orElseThrow();

            // Assertion 5: the ROM-baked bird renderer is registered and reachable through the
            // resolved module's object art provider before gameplay ever boots.
            PatternSpriteRenderer birdRenderer = resolved.getObjectArtProvider()
                    .getRenderer("sample-flappy:bird");
            assertNotNull(birdRenderer, "sample-flappy:bird must resolve to a renderer");

            EngineContext previous = EngineServices.current();
            EngineContext injected = withResolver(previous, resolver, isolatedRomManager());
            try {
                EngineServices.configure(injected);
                injected.roms().setRom(rom);
                GameplayModeContext booted = HeadlessGameBoot.openResolvedSessionForBoot(injected, base,
                        ModuleResolutionService.LaunchPolicy.STANDARD);
                GameplaySessionFactory.attachManagers(booted, injected);
                injected.graphics().initHeadless();
                var team = GameplayTeamBootstrap.registerActiveTeam(
                        SessionManager.requireCurrentGameModule(), GameServices.sprites(), injected.configuration());
                GameServices.camera().setFocusedSprite(team.mainSprite());
                GameServices.camera().setFrozen(false);
                GameServices.level().loadZoneAndAct(zoneIndex, 0);
                GroundSensor.setLevelManager(GameServices.level());

                AbstractPlayableSprite player = team.mainSprite();
                Camera camera = GameServices.camera();
                HeadlessTestRunner runner = new HeadlessTestRunner(player);

                // Assertion 1: within a few frames the controller seizes and hides the player.
                runner.stepIdleFrames(2);
                assertTrue(player.isObjectControlled(), "controller must object-control the player");
                assertTrue(player.isHidden(), "controller must hide the native player sprite");

                // Assertion 2: with no input, gravity increases centreY (the bird sinks) over
                // the next 20 frames. (20, not 30, keeps the free-fall comfortably clear of the
                // controller's own y<16 / y>240 kill bounds before the next phase presses jump.)
                int yBeforeGravity = player.getCentreY();
                runner.stepIdleFrames(20);
                int yAfterGravity = player.getCentreY();
                assertTrue(yAfterGravity > yBeforeGravity,
                        "unflapped flight must sink: " + yBeforeGravity + " -> " + yAfterGravity);

                // Assertion 3: a jump edge reverses that trend -- centreY decreases over the
                // following 10 frames.
                int yBeforeJump = player.getCentreY();
                runner.stepFrame(false, false, false, false, true);
                runner.stepIdleFrames(10);
                int yAfterJump = player.getCentreY();
                assertTrue(yAfterJump < yBeforeJump,
                        "a flap must make the bird rise: " + yBeforeJump + " -> " + yAfterJump);

                // Assertion 4: the camera advances monotonically as the controller force-scrolls
                // every frame, driven by the constant 2px/frame forward speed. Piloted (bang-bang
                // altitude hold, edge-triggered on climbing back above the trigger line --
                // isJumpJustPressed() is edge-triggered, so holding the raw button down would
                // only flap once) so the run comfortably clears the kill bounds for the whole
                // 60-frame sample.
                boolean[] previousTooLow = {false};
                int cameraXBefore = camera.getX();
                int previousCameraX = cameraXBefore;
                for (int i = 0; i < 60; i++) {
                    pilotFrame(runner, player, previousTooLow, SAFETY_TRIGGER_Y);
                    int currentCameraX = camera.getX();
                    assertTrue(currentCameraX >= previousCameraX,
                            "camera x must never regress while flying: frame " + i);
                    previousCameraX = currentCameraX;
                }
                assertTrue(camera.getX() > cameraXBefore, "camera x must have advanced overall");

                // Assertion 6: keep flying, now piloted with a lower trigger line so the hold
                // band sits inside the first pipe's gap-safe zone (centreY in (24,104)), until
                // the player clears the pipe's right edge with margin; a single ring must be
                // scored and the player must still be flying (not released).
                int ringsBeforePipe = GameServices.level().getLevelGamestate().getRings();
                int guardFrames = 0;
                while (player.getCentreX() < 560 && guardFrames < 400) {
                    pilotFrame(runner, player, previousTooLow, GAP_TRIGGER_Y);
                    guardFrames++;
                }
                assertTrue(player.getCentreX() >= 560, "player must have reached past the first pipe");
                assertTrue(player.isObjectControlled(), "player must have survived the first pipe");
                int ringsAfterPipe = GameServices.level().getLevelGamestate().getRings();
                assertEquals(ringsBeforePipe + 1, ringsAfterPipe, "clearing the first pipe must score one ring");

                // Assertion 7: force a collision by no longer flapping -- gravity alone carries
                // the player into either a pipe body or the controller's own kill bound, and the
                // controller must hand the player back to engine-owned hurt/death.
                int releaseGuardFrames = 0;
                while (player.isObjectControlled() && releaseGuardFrames < 200) {
                    runner.stepFrame(false, false, false, false, false);
                    releaseGuardFrames++;
                }
                assertFalse(player.isObjectControlled(), "an unavoided fall must release object control");
                assertTrue(player.isHidden() == false, "release must unhide the native player sprite");
                assertTrue(player.isHurt() || player.getDead(),
                        "release must hand off to engine-owned hurt/death");
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
            }
        }
    }

    /**
     * A single flap's rise/fall arc has a fixed amplitude (~31px, set by the controller's
     * FLAP_VELOCITY/GRAVITY constants) measured from wherever it fires -- it is NOT a
     * threshold-relative amplitude. {@link #pilotFrame} presses jump only on the edge where
     * the bird climbs back above the trigger line (matching {@code isJumpJustPressed()}'s
     * edge-triggered semantics -- holding the raw button down would only flap once), so the
     * flight self-stabilizes into a steady oscillation between roughly {@code (trigger - 31)}
     * and {@code trigger}. Two triggers are used: a high one that only needs to clear the
     * controller's own kill bounds (16, 240), and a low one, used once closer to a pipe, whose
     * band sits inside a gap's safe zone.
     */
    private static final int SAFETY_TRIGGER_Y = 125;
    private static final int GAP_TRIGGER_Y = 90;

    private static void pilotFrame(HeadlessTestRunner runner, AbstractPlayableSprite player,
            boolean[] previousTooLow, int triggerY) {
        boolean tooLow = player.getCentreY() > triggerY;
        boolean press = tooLow && !previousTooLow[0];
        previousTooLow[0] = tooLow;
        runner.stepFrame(false, false, false, false, press);
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
            Files.write(output.resolve(file), java.util.Base64.getDecoder().decode(assets.getProperty(file)));
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
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, source.toString());
    }

    private static void copyTree(Path source, Path output) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path destination = output.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) Files.createDirectories(destination);
                else {
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
                new ModState.Entry(descriptor.manifest().id(), true, 0, true, descriptor.sha256())));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        assertEquals(1, catalog.effective().orderedEnabled().size());
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog.effective(), Set.of(descriptor.manifest().id()));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return new CatalogFixture(runtime, catalog.effective(), runtime.newRegistrationPlan());
    }

    private ModuleResolutionService resolver(CatalogFixture fixture) {
        return new ModuleResolutionService(List.of(), new EffectiveCatalogPatchEnablement(fixture.effective()),
                new LogicalRomResolver(() -> null), SonicConfigurationService.createStandalone(),
                ignored -> fixture.plan());
    }

    private static EngineContext withResolver(EngineContext old, ModuleResolutionService resolver,
            RomManager roms) {
        return new EngineContext(SonicConfigurationService.createStandalone(), old.graphics(), old.audio(),
                roms, old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures(), resolver);
    }

    private static RomManager isolatedRomManager() throws Exception {
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private record CatalogFixture(ModRuntime runtime, com.openggf.mods.EffectiveModCatalog effective,
            ModuleResolutionService.PatchPlan plan) implements AutoCloseable {
        public void close() throws Exception { runtime.close(); }
    }
}
