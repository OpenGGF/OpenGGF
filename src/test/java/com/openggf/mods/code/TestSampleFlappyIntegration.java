package com.openggf.mods.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.ModSubsystem;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.LevelState;
import com.openggf.game.ShieldType;
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
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
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
import java.lang.reflect.Field;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private int buildSequence;
    private int loadSequence;

    @BeforeEach
    void resetState() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        ModSubsystem.clearProcess();
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
            // The controller spawns each pipe into a free SST slot above its own, and
            // ROM ExecuteObjects reaches a higher slot in the same pass, so every pipe
            // takes one PIPE_SPEED step (0x200 = 2px) in its spawn frame.
            assertEquals(game.camera().getX() + game.camera().getWidth() + 64 - 2,
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

    @Test
    void onePipeScoresOncePerCycleAndResetEnablesTheNextCycle() throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            LevelState levelState = GameServices.level().getLevelGamestate();
            levelState.setRings(0);
            ObjectInstance pipe = orderedPipes(game.objects()).getFirst();
            ObjectRefId id = objectId(game.objects(), pipe);

            recyclePipe(pipe, game.tails().getCentreX() + 2, 2);
            game.runner().stepIdleFrames(1);
            assertEquals(0, levelState.getRings(),
                    "a gate level with Tails has not passed his centre");
            game.runner().stepIdleFrames(1);
            assertEquals(1, levelState.getRings());
            assertTrue(invokeBoolean(pipe, "gateConsumed"));
            assertEquals(id, objectId(game.objects(), pipe));

            game.runner().stepIdleFrames(20);
            assertEquals(1, levelState.getRings(),
                    "a consumed gate must not score on later overlapping frames");
            assertFalse(game.tails().getDead(),
                    "the no-repeat interval must keep native flight alive for the next cycle");

            int pixelsPastViewport = centreX(pipe) - game.camera().getX() + 17;
            invoke(pipe, "advance", int.class, pixelsPastViewport << 8);
            game.runner().stepIdleFrames(1);
            ObjectInstance recycled = pipeWithId(game.objects(), id);
            assertFalse(invokeBoolean(recycled, "gateConsumed"));
            assertEquals(id, objectId(game.objects(), recycled));

            recyclePipe(recycled, game.tails().getCentreX() + 2, 2);
            game.runner().stepIdleFrames(2);
            assertEquals(2, levelState.getRings(),
                    "the same stable pipe may score again after a live recycle");
            assertTrue(invokeBoolean(recycled, "gateConsumed"));
            assertEquals(id, objectId(game.objects(), recycled));
        });
    }

    @Test
    void scoreCrossingOneHundredDoesNotRunCollectibleRingBonusLogic() throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            LevelState levelState = GameServices.level().getLevelGamestate();
            levelState.setRings(99);
            int livesBefore = GameServices.gameState().getLives();
            int stockScoreBefore = GameServices.gameState().getScore();
            var audioBefore = GameServices.audio().captureLogicalSnapshot();

            ObjectInstance pipe = orderedPipes(game.objects()).getFirst();
            recyclePipe(pipe, game.tails().getCentreX() + 2, 2);
            game.runner().stepIdleFrames(2);

            var audioAfter = GameServices.audio().captureLogicalSnapshot();
            assertEquals(100, levelState.getRings());
            assertEquals(livesBefore, GameServices.gameState().getLives(),
                    "Flappy score must bypass collectible-ring extra lives");
            assertEquals(stockScoreBefore, GameServices.gameState().getScore(),
                    "the stock score counter is not part of Flappy scoring");
            assertEquals(audioBefore.presentation().activeMusic(),
                    audioAfter.presentation().activeMusic(),
                    "crossing 100 Flappy points must not replace music with the 1-up cue");
            assertEquals(audioBefore.commandEntryCount() + 1, audioAfter.commandEntryCount(),
                    "only the gate's ring SFX command should be emitted");
        });
    }

    @Test
    void pipeAndVisibleBoundsAlwaysUseCrushDeath() throws Exception {
        for (Protection protection : Protection.values()) {
            withLaunchedGameplay(null, game -> {
                game.runner().stepIdleFrames(1);
                applyProtection(game, protection);
                ObjectInstance pipe = orderedPipes(game.objects()).getFirst();
                recyclePipe(pipe, game.tails().getCentreX(), 0);

                game.runner().stepIdleFrames(1);

                assertTrue(game.tails().getDead(),
                        protection + " must not protect Tails from a pipe body");
            });
        }

        assertDiesAtVisibleBoundary(true);
        assertDiesAtVisibleBoundary(false);
    }

    @Test
    void engineRestartResetsRunAndRecreatesTheSameInitialSequence() throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            List<Integer> initialVariants = gapVariants(game.objects());
            int livesBefore = GameServices.gameState().getLives();
            GameServices.level().getLevelGamestate().setRings(7);
            ObjectInstance pipe = orderedPipes(game.objects()).getFirst();
            recyclePipe(pipe, game.tails().getCentreX(), 0);
            game.runner().stepIdleFrames(1);
            assertTrue(game.tails().getDead());

            for (int frame = 0;
                 frame < 600 && GameServices.gameState().getLives() == livesBefore;
                 frame++) {
                game.runner().stepIdleFrames(1);
            }
            assertEquals(livesBefore - 1, GameServices.gameState().getLives(),
                    "entering the normal death-restart routine owns the single life decrement");
            for (int frame = 0;
                 frame < 600 && !GameServices.level().isRespawnRequestedForRewind();
                 frame++) {
                game.runner().stepIdleFrames(1);
            }
            assertTrue(GameServices.level().consumeRespawnRequest(),
                    "the later restartime countdown expiry must request the normal engine restart");

            GameServices.level().loadCurrentLevel();
            GroundSensor.setLevelManager(GameServices.level());
            game.camera().setFocusedSprite(game.tails());
            game.camera().updatePosition(true);
            assertEquals(0, pipes(GameServices.level().getObjectManager()).size());
            game.runner().stepIdleFrames(1);

            ObjectManager restartedObjects = GameServices.level().getObjectManager();
            assertEquals(livesBefore - 1, GameServices.gameState().getLives());
            assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            assertEquals(6, pipes(restartedObjects).size());
            assertEquals(initialVariants, gapVariants(restartedObjects));
            assertTrue(game.tails().getTailsFlightController().isActive());
            assertEquals(0xF0, game.tails().getDoubleJumpProperty() & 0xFF);
        });
    }

    @Test
    void initialTitleCardSeesTheResolvedS3kCustomZonePalette() throws Exception {
        withLaunchedGameplay(null, game -> {
            assertTrue(GameServices.paletteOwnershipRegistry().hasResolvedThisFrame(),
                    "custom-zone palette ownership must resolve before the first gameplay frame");
            assertEquals("host:s3k-character", GameServices.paletteOwnershipRegistry()
                    .ownerAt(PaletteSurface.NORMAL, 0, 6));
            assertEquals(0x000E, PaletteWriteSupport.segaWordFromColor(
                            GameServices.level().getCurrentLevel().getPalette(0).getColor(6)),
                    "title-card tile $500 uses line 0 color 6 for the red banner");
        });
    }

    @Test
    void customZoneComposesNativeTailsHudCreatorClaimsAndDecodedPipeArt() throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            // Palette ownership is committed at the headless presentation boundary;
            // no framebuffer or GL execution is required for this draw call.
            GameServices.level().draw();

            Palette expectedTails = game.resolved().getCrossGameDonorProvider()
                    .loadCharacterPalette(RomByteReader.fromRom(game.rom()), "tails");
            assertNotNull(expectedTails);
            Palette liveCharacter = GameServices.level().getCurrentLevel().getPalette(0);
            for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
                assertEquals(PaletteWriteSupport.segaWordFromColor(expectedTails.getColor(color)),
                        PaletteWriteSupport.segaWordFromColor(liveCharacter.getColor(color)),
                        "native Tails line-0 word mismatch at color " + color);
                assertColorEquals(expectedTails.getColor(color), liveCharacter.getColor(color),
                        "native Tails line-0 RGB mismatch at color " + color);
            }

            for (int color : new int[]{1, 5, 14, 15}) {
                assertEquals("host:s3k-hud", GameServices.paletteOwnershipRegistry()
                        .ownerAt(PaletteSurface.NORMAL, 1, color),
                        "S3K lives HUD must own reserved line-1 color " + color);
            }

            JsonNode source = new ObjectMapper().readTree(
                    FLAPPY.resolve("src/main/mod/level-source/level.json").toFile());
            for (JsonNode claim : source.path("paletteClaims")) {
                int line = claim.path("line").asInt();
                int color = claim.path("color").asInt();
                int segaWord = claim.path("sega").asInt();
                assertEquals(segaWord, PaletteWriteSupport.segaWordFromColor(
                                GameServices.level().getCurrentLevel()
                                        .getPalette(line).getColor(color)),
                        "live palette must apply sparse creator claim " + line + ":" + color);
                assertEquals("sample-flappy:flappy-garden",
                        GameServices.paletteOwnershipRegistry()
                                .ownerAt(PaletteSurface.NORMAL, line, color));
            }

            ObjectSpriteSheet pipe = game.resolved().getObjectArtProvider()
                    .getSheet("sample-flappy:pipe");
            assertNotNull(pipe);
            assertEquals(2, pipe.getFrameCount());
            assertPieceSize(pipe.getFrame(0).pieces().getFirst(), 4, 4);
            assertPieceSize(pipe.getFrame(1).pieces().getFirst(), 4, 2);
            assertTrue(java.util.Arrays.stream(pipe.getPatterns())
                    .anyMatch(TestSampleFlappyIntegration::hasNonZeroNibble));
        });
    }

    @Test
    void rewindRestoresAndResimulatesScoreAndLiveRecycleExactly() throws Exception {
        withLaunchedGameplay(null, game -> {
            LiveRewindHarness rewind = installLiveRewind(game);
            stepRecordedFrame(game, rewind);

            ObjectInstance pipe = orderedPipes(game.objects()).getFirst();
            recyclePipe(pipe, game.tails().getCentreX() + 2, 2);
            rewind.controller().resetBufferAtCurrentFrame();
            RunState before = captureRunState(game, rewind.controller());
            ObjectRefId recycledId = objectId(game.objects(), pipe);

            for (int frame = 0; frame < 100
                    && (GameServices.level().getLevelGamestate().getRings() == before.rings()
                    || fieldInt(controller(game.objects()), "generationCounter")
                    == before.generationCounter()); frame++) {
                stepRecordedFrame(game, rewind);
            }

            RunState after = captureRunState(game, rewind.controller());
            assertEquals(before.rings() + 1, after.rings(),
                    "the recorded run must cross exactly one scoring gate");
            assertEquals(before.generationCounter() + 1, after.generationCounter(),
                    "the recorded run must recycle exactly one live pipe");
            assertTrue(after.pipes().stream().anyMatch(state -> state.id().equals(recycledId)),
                    "live recycling must retain the original pipe identity");

            rewind.controller().seekTo(before.frame());
            assertEquals(before, captureRunState(game, rewind.controller()),
                    "backward seek must restore every controller, score, and pipe scalar");

            rewind.controller().seekTo(after.frame());
            assertEquals(after, captureRunState(game, rewind.controller()),
                    "raw-input re-simulation must reproduce the exact post-recycle state");
        });
    }

    @Test
    void rewindAcrossFirstControllerUpdateKeepsOneControllerAndSixStablePipes() throws Exception {
        withLaunchedGameplay(null, game -> {
            LiveRewindHarness rewind = installLiveRewind(game);
            assertEquals(0, pipes(game.objects()).size(),
                    "frame-zero keyframe must precede the controller's first update");

            stepRecordedFrame(game, rewind);
            List<ObjectRefId> constructedIds = pipeIds(game.objects());
            assertEquals(6, constructedIds.size());

            rewind.controller().seekTo(0);
            assertEquals(0, pipes(game.objects()).size());
            assertSingleLayoutController(game.objects(), 0);

            rewind.controller().seekTo(1);
            List<ObjectRefId> replayedIds = pipeIds(game.objects());
            assertEquals(6, replayedIds.size(),
                    "re-simulating the first update must create exactly six pipes");
            assertEquals(6, new HashSet<>(replayedIds).size(),
                    "re-simulating construction must not duplicate a dynamic identity");
            assertSingleLayoutController(game.objects(), 6);

            rewind.controller().resetBufferAtCurrentFrame();
            stepRecordedFrame(game, rewind);
            rewind.controller().seekTo(1);
            assertEquals(replayedIds, pipeIds(game.objects()),
                    "restoring the post-construction keyframe must preserve existing identities");
            assertSingleLayoutController(game.objects(), 6);
        });
    }

    private void assertDiesAtVisibleBoundary(boolean top) throws Exception {
        withLaunchedGameplay(null, game -> {
            game.runner().stepIdleFrames(1);
            int boundaryY = top
                    ? game.camera().getMinY() + 0x10
                    : game.camera().getY() + 224;
            game.tails().setCentreY((short) boundaryY);

            game.runner().stepIdleFrames(1);

            assertTrue(game.tails().getDead(),
                    (top ? "top" : "bottom") + " visible bound must be inclusive");
        });
    }

    private static LiveRewindHarness installLiveRewind(LaunchedGameplay game) {
        game.configuration().setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0,
                        0, 0, false, false),
                PlayerInputState.neutral()));
        LiveRewindManager manager = new LiveRewindManager(game.configuration());
        assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));
        RewindController controller = game.gameplay().getRewindController();
        assertTrue(controller != null, "live rewind must install its production controller");
        return new LiveRewindHarness(manager, controller, input);
    }

    private static void stepRecordedFrame(LaunchedGameplay game, LiveRewindHarness rewind) {
        int before = rewind.controller().currentFrame();
        game.runner().stepFrame(false, false, false, true, false);
        rewind.manager().recordExternalFrame(GameMode.LEVEL, false, rewind.input());
        assertEquals(before + 1, rewind.controller().currentFrame());
    }

    private static RunState captureRunState(LaunchedGameplay game, RewindController rewind)
            throws Exception {
        ObjectInstance controller = controller(game.objects());
        List<PipeRunState> pipeStates = new ArrayList<>();
        for (ObjectInstance pipe : pipes(game.objects())) {
            pipeStates.add(new PipeRunState(
                    objectId(game.objects(), pipe),
                    centreX(pipe),
                    fieldInt(pipe, "xSubpixelRemainder"),
                    invokeInt(pipe, "gapVariant"),
                    invokeBoolean(pipe, "gateConsumed")));
        }
        pipeStates.sort(Comparator.comparing(PipeRunState::id,
                TestSampleFlappyIntegration::compareObjectIds));
        return new RunState(
                rewind.currentFrame(),
                fieldInt(controller, "routine"),
                fieldBoolean(controller, "poolInitialized"),
                fieldInt(controller, "anchorX"),
                fieldInt(controller, "generationCounter"),
                GameServices.level().getLevelGamestate().getRings(),
                List.copyOf(pipeStates));
    }

    private static List<ObjectRefId> pipeIds(ObjectManager objects) {
        return pipes(objects).stream()
                .map(pipe -> objectId(objects, pipe))
                .sorted(TestSampleFlappyIntegration::compareObjectIds)
                .toList();
    }

    private static int compareObjectIds(ObjectRefId left, ObjectRefId right) {
        int dynamic = Integer.compare(left.dynamicId(), right.dynamicId());
        if (dynamic != 0) return dynamic;
        int spawn = Integer.compare(left.spawnId(), right.spawnId());
        if (spawn != 0) return spawn;
        return Integer.compare(left.generation(), right.generation());
    }

    private static void assertSingleLayoutController(ObjectManager objects, int dynamicCount) {
        ObjectManagerSnapshot snapshot = objects.rewindSnapshottable().capture();
        assertEquals(1, snapshot.slots().size());
        assertEquals("example.flappysample.FlappyController",
                snapshot.slots().getFirst().className());
        assertEquals(dynamicCount, snapshot.dynamicObjects().size());
        assertEquals(1, objects.getActiveObjects().stream()
                .filter(object -> object.getClass().getName()
                        .equals("example.flappysample.FlappyController"))
                .count());
    }

    private static void assertColorEquals(Palette.Color expected, Palette.Color actual,
                                          String message) {
        assertEquals(expected.r & 0xFF, actual.r & 0xFF, message + " (red)");
        assertEquals(expected.g & 0xFF, actual.g & 0xFF, message + " (green)");
        assertEquals(expected.b & 0xFF, actual.b & 0xFF, message + " (blue)");
    }

    private static void assertPieceSize(SpriteMappingPiece piece,
                                        int widthTiles, int heightTiles) {
        assertEquals(widthTiles, piece.widthTiles());
        assertEquals(heightTiles, piece.heightTiles());
    }

    private static boolean hasNonZeroNibble(Pattern pattern) {
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                if (pattern.getPixel(x, y) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyProtection(LaunchedGameplay game, Protection protection) {
        switch (protection) {
            case RINGS -> GameServices.level().getLevelGamestate().setRings(10);
            case SHIELD -> game.tails().giveShield(ShieldType.FIRE);
            case INVINCIBLE -> game.tails().setInvincibleFrames(120);
            case SUPER -> game.tails().setSuperSonic(true);
        }
    }

    private static List<ObjectInstance> orderedPipes(ObjectManager objects) {
        return pipes(objects).stream()
                .sorted(Comparator.comparingInt(TestSampleFlappyIntegration::centreX))
                .toList();
    }

    private static List<Integer> gapVariants(ObjectManager objects) {
        return orderedPipes(objects).stream().map(pipe -> {
            try {
                return invokeInt(pipe, "gapVariant");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).toList();
    }

    private static ObjectInstance pipeWithId(ObjectManager objects, ObjectRefId id) {
        return pipes(objects).stream()
                .filter(pipe -> id.equals(objectId(objects, pipe)))
                .findFirst().orElseThrow();
    }

    private static void recyclePipe(ObjectInstance pipe, int centreX, int gapVariant)
            throws Exception {
        Method method = pipe.getClass().getMethod("recycleAfter", int.class, int.class);
        method.invoke(pipe, centreX, gapVariant);
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
                        new HeadlessTestRunner(tails), GameServices.level().getObjectManager(),
                        gameplay, configuration, resolved, rom));
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

    private static int fieldInt(Object target, String name) throws Exception {
        return (int) field(target, name).get(target);
    }

    private static boolean fieldBoolean(Object target, String name) throws Exception {
        return (boolean) field(target, name).get(target);
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
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
        int buildId = buildSequence++;
        Path output = temp.resolve("flappy-exploded-" + buildId);
        Files.createDirectories(output);
        copyTree(FLAPPY.resolve("src/main/resources"), output);
        compileJava(FLAPPY.resolve("src/main/java"), output);
        assertCli("convert", "art", "--image", FLAPPY.resolve("src/main/mod/pipe.png").toString(),
                "--sheet", FLAPPY.resolve("src/main/mod/pipe-sheet.yaml").toString(),
                "--out", output.resolve("art/pipe.ggfs").toString());
        Path level = materializeLevel(
                FLAPPY.resolve("src/main/mod/level-source"), "flappy-level-" + buildId);
        assertCli("convert", "level", "--from-export", level.toString(),
                "--out", output.resolve("levels/flappy").toString());
        Path jar = temp.resolve("sample-flappy-" + buildId + ".jar");
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
        Path repo = temp.resolve("repo-" + loadSequence++);
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
        ModSubsystem.current().installRewindClassResolver(
                new ModClassResolver(runtime, getClass().getClassLoader()));
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
                                    HeadlessTestRunner runner, ObjectManager objects,
                                    GameplayModeContext gameplay,
                                    SonicConfigurationService configuration,
                                    GameModule resolved, Rom rom) {}

    private record LiveRewindHarness(LiveRewindManager manager, RewindController controller,
                                     InputHandler input) {}

    private record PipeRunState(ObjectRefId id, int centreX, int xSubpixelRemainder,
                                int gapVariant, boolean gateConsumed) {}

    private record RunState(int frame, int routine, boolean poolInitialized, int anchorX,
                            int generationCounter, int rings, List<PipeRunState> pipes) {}

    @FunctionalInterface
    private interface GameplayAssertion {
        void verify(LaunchedGameplay game) throws Exception;
    }

    private enum Protection {
        RINGS,
        SHIELD,
        INVINCIBLE,
        SUPER
    }
}
