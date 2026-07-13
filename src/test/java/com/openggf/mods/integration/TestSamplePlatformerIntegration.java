package com.openggf.mods.integration;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.ModSubsystem;
import com.openggf.SessionExternalContentView;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AbstractSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MusicReference;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.snapshot.GenericObjectSnapshot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.ModLevel;
import com.openggf.mods.*;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRuntime;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural clone of {@link TestPhase3StandaloneSampleIntegration} for the {@code sample-platformer}
 * gallery mod: real packaged jar, no-ROM boot, character/level/audio-manifest/terminal-topology
 * assertions, and the full master-title New Game -> save -> complete -> credits -> Continue
 * restoration flow plus corrupt-slot Continue-hiding cases.
 *
 * <p>Unlike {@code phase3-standalone} (which registers a second {@code friend} character and
 * {@code supportsSidekick() == true}), {@code sample-platformer} registers only {@code bolt} and
 * {@code PlatformerModule#supportsSidekick()} is {@code false} (see
 * {@code GameplayTeamBootstrap#registerActiveTeam}, which drops configured sidekick names entirely
 * when the module does not support them). The Continue-restoration flow below is adapted to always
 * carry an empty sidekick list rather than reusing phase-3's {@code FRIEND} second-character path.
 *
 * <p>Gameplay-level assertions (badnik/spring behavior) are deferred to later tasks: {@code ZapBug}
 * and {@code SpringPad} are still inert-body stubs at this point in the plan.
 */
@Isolated
class TestSamplePlatformerIntegration {
    private static final String OWNER = "sample-platformer";
    private static final String RUNNER = OWNER + ":bolt";
    @TempDir Path temp;
    private String previousSaveRoot;
    private EngineContext previousEngineContext;

    @BeforeEach void resetState() { TestEnvironment.resetAll(); }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.reset();
        ModSubsystem.clearProcess();
        AudioManager.getInstance().resetState();
        if (previousEngineContext != null) {
            EngineServices.configure(previousEngineContext);
            previousEngineContext = null;
        }
        if (previousSaveRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", previousSaveRoot);
        TestEnvironment.resetAll();
    }

    @Test
    void realPackagedPlatformerLoadsWithoutRomAndExercisesCharacterLevelAudioAndTerminalTopology()
            throws Exception {
        Path jar = buildSample();
        try (Fixture fixture = load(jar)) {
            GameModule module = fixture.runtime.prepareStandaloneModule(OWNER).orElseThrow();
            assertEquals(OWNER, module.getIdentifier());
            assertEquals(com.openggf.game.GameId.STANDALONE, module.getGameId());
            assertEquals(1, module.getZoneRegistry().getZoneCount());
            assertFalse(module.supportsSidekick());
            assertEquals(com.openggf.game.ZoneProgressionPlan.Credits.INSTANCE,
                    module.getZoneRegistry().progressionPlan().next(
                            module.getZoneRegistry().progressionTopology(), 0, 0));

            var assets = fixture.runtime.standaloneAssetSnapshot(OWNER);
            GameDataSource source = new GameDataSource() {
                @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
                @Override public java.io.InputStream openAsset(String path) throws java.io.IOException {
                    return new ByteArrayInputStream(assets.readBounded(path, assets.limits().maxAssetBytes()));
                }
                @Override public String identity() { return "standalone:" + OWNER + ":acceptance"; }
            };
            var game = module.createGame(source);
            assertNull(game.getRom());
            assertInstanceOf(ModLevel.class, game.loadLevel(0x400));
            assertEquals(0x400, module.getZoneRegistry().getLevelDataForZone(0).getFirst().levelIndex(),
                    "Every declared descriptor must route through ModGame.loadLevel by its exact index");
            // game.loadLevel routes through OwnerAwareStandaloneModule's fault boundary, which wraps
            // the mod's raw IOException into a CallbackAborted runtime failure.
            assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                    () -> game.loadLevel(0x401),
                    "Any level index other than the one declared descriptor must throw");
            assertEquals(new MusicReference.Namespaced(OWNER, "zone-theme"),
                    module.getLevelMusicReference(0, 0));

            CharacterKey runnerKey = CharacterKey.parsePersisted(RUNNER);
            var definition = module.getPlayableCharacterRegistry().find(runnerKey).orElseThrow();
            assertEquals(com.openggf.sprites.playable.SecondaryAbility.NONE,
                    definition.secondaryAbility());
            assertFalse(definition.supportsSuperForm());
            assertFalse(definition.artSupplier().load(RUNNER).mappingFrames().isEmpty());
            assertNotNull(definition.paletteSupplier());
            assertEquals(0x480, module.getPhysicsProvider().getProfile(RUNNER).max());
            assertEquals(0x780, module.getPhysicsProvider().getProfile(RUNNER).jump());
            assertNotEquals(PhysicsProfile.SONIC_2_SONIC,
                    module.getPhysicsProvider().getProfile(RUNNER));

            GameModuleRegistry.setCurrent(module);
            AbstractPlayableSprite player = definition.spriteFactory().create(RUNNER, 32, 96);
            assertEquals(runnerKey, player.characterKey());
            assertEquals(0x480, player.getPhysicsProfile().max(),
                    "The sample's literal profile must drive the constructed player");
            assertEquals(0x780, player.getPhysicsProfile().jump());
            exerciseDoubleJumpAndRewindLatch(player);

            RecordingBackend backend = new RecordingBackend();
            AudioManager audio = AudioManager.getInstance();
            audio.resetState();
            audio.setBackend(backend);

            ModAudioManifest audioManifest;
            try (JarFile packed = new JarFile(jar.toFile())) {
                byte[] yaml = packed.getInputStream(packed.getJarEntry("audio/audio-manifest.yaml"))
                        .readAllBytes();
                audioManifest = new ModAudioManifestParser(OWNER).parse(yaml);
                assertNotNull(packed.getJarEntry("art/bolt.ggfp"));
                assertNotNull(packed.getJarEntry("art/zapbug.ggfs"));
                assertNotNull(packed.getJarEntry("art/springpad.ggfs"));
                assertNotNull(packed.getJarEntry("art/ring.ggfs"));
                assertNotNull(packed.getJarEntry("levels/act1/level.json"));
            }
            assertEquals(List.of(new TrackKey(OWNER, "zone-theme")),
                    audioManifest.tracks().stream().map(ModAudioTrack::key).toList());
            assertEquals(List.of(new SfxKey(OWNER, "jump2"), new SfxKey(OWNER, "hit"),
                            new SfxKey(OWNER, "spring")),
                    audioManifest.sfx().stream().map(ModAudioSfx::key).toList());
            assertAllPackagedAudioAssetsTraverseBoundedPcmPool(fixture, audioManifest);

            exerciseMasterTitleNewCompleteAndContinue(fixture, module, backend);
        }
    }

    /**
     * Exercises {@code BoltCharacter}'s double-jump secondary ability in isolation, without a
     * live gameplay session: {@code onAbilityActivate} fires once per airborne stretch, is
     * latched against a second mid-air press, and re-arms after the {@code draw()}-based
     * landing-reset seam runs. Air-state transitions use direct field reflection (mirroring
     * the engine's own {@code TestablePlayableSprite#setAirForTest}) rather than the public
     * {@code setAir(boolean)} setter, because the landing branch of {@code setAir} calls
     * {@code currentGameState().resetItemBonus()}, which requires an active
     * {@code GameplayModeContext} that this construction-only slice of the test does not set up.
     *
     * <p>The rewind assertion drives {@link GenericFieldCapturer#restore(Object, GenericObjectSnapshot)}
     * -- the real production write path -- against a snapshot scoped to just the {@code doubleJumpUsed}
     * key, per the task brief's "GenericFieldCapturer directly at test level is acceptable" allowance.
     * {@link GenericFieldCapturer#capture(Object)} cannot be used unscoped here: it walks the full
     * {@code AbstractPlayableSprite}/{@code AbstractSprite} field hierarchy and throws on pre-existing,
     * unrelated structural fields (e.g. {@code AbstractSprite#ceilingSensors}, a {@code Sensor[]} with
     * no {@code @RewindTransient} annotation) -- an engine-wide gap orthogonal to this task's scope.
     * This is also not an assertion about the production {@code AbstractPlayableSprite#captureRewindState()}
     * pipeline, which is a closed, hand-enumerated record and does not currently capture
     * mod-subclass-declared fields at all.
     */
    private void exerciseDoubleJumpAndRewindLatch(AbstractPlayableSprite player) throws Exception {
        setAirField(player, true);
        assertTrue(invokeAbilityActivate(player), "First mid-air press must fire the double jump");
        assertEquals((short) -0x600, player.getYSpeed(), "Double jump must apply the ROM impulse");

        player.setYSpeed((short) 0x0111);
        assertFalse(invokeAbilityActivate(player), "A second mid-air press before landing must be latched");
        assertEquals((short) 0x0111, player.getYSpeed(), "A latched press must not re-apply the impulse");

        setAirField(player, false);
        player.draw();
        setAirField(player, true);
        assertTrue(invokeAbilityActivate(player), "After the landing-reset seam the ability must be available again");
        assertEquals((short) -0x600, player.getYSpeed(), "The re-armed ability must apply the ROM impulse again");

        Field doubleJumpUsedField = player.getClass().getDeclaredField("doubleJumpUsed");
        doubleJumpUsedField.setAccessible(true);
        assertEquals(true, doubleJumpUsedField.get(player), "Sanity: latch must be set after the ability just fired");

        GenericObjectSnapshot snapshot = new GenericObjectSnapshot(player.getClass(),
                List.of(FieldKey.of(doubleJumpUsedField)), new Object[] { true });
        doubleJumpUsedField.set(player, false);
        GenericFieldCapturer.restore(player, snapshot);
        assertEquals(true, doubleJumpUsedField.get(player),
                "Rewind restore must bring the double-jump latch back rather than leaving the stale live value");
    }

    private static void setAirField(AbstractPlayableSprite player, boolean value) throws Exception {
        Field field = AbstractPlayableSprite.class.getDeclaredField("air");
        field.setAccessible(true);
        field.setBoolean(player, value);
    }

    private static boolean invokeAbilityActivate(AbstractPlayableSprite player) throws Exception {
        Method method = AbstractPlayableSprite.class.getDeclaredMethod("dispatchAbilityActivate",
                boolean.class, boolean.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(player, false, false, false, false);
    }

    /**
     * Mirrors {@code TestPhase3StandaloneSampleIntegration#assertPackagedSfxTraversesBoundedPcmPool}
     * but exercises all four packaged audio assets (the {@code zone-theme} OGG streamed track plus
     * the {@code jump2}/{@code hit}/{@code spring} WAV one-shots), each through its own freshly
     * prepared session so no asset's playback state can leak into another's PCM check.
     */
    private void assertAllPackagedAudioAssetsTraverseBoundedPcmPool(Fixture fixture,
                                                                     ModAudioManifest manifest) {
        ModTrackRegistry tracks = new ModTrackRegistry(manifest.tracks());
        ModSfxRegistry sfx = new ModSfxRegistry(manifest.sfx());
        assertAssetDecodesNonZeroPcm(fixture, tracks, sfx, true, "zone-theme");
        for (String name : List.of("jump2", "hit", "spring")) {
            assertAssetDecodesNonZeroPcm(fixture, tracks, sfx, false, name);
        }
    }

    private void assertAssetDecodesNonZeroPcm(Fixture fixture, ModTrackRegistry tracks,
                                              ModSfxRegistry sfx, boolean isTrack, String name) {
        ModAudioPreparer preparer = new ModAudioPreparer(
                fixture.descriptor.jarPath().getParent().toAbsolutePath().normalize(),
                ModInputLimits.production(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved());
        PreparedAudioSession session = preparer.prepare(
                fixture.catalog.effective(), tracks, sfx, 8_000);
        PreparedModMusic music = PreparedModMusic.build(fixture.catalog.effective(), tracks, sfx,
                session, 8_000, OWNER);
        PcmPoolBackend backend = new PcmPoolBackend();
        backend.installStreamedMusicPort(new com.openggf.ModStreamedMusicPort(
                music, new StreamedMusicPlayer(8_000), OWNER));
        backend.update();
        if (isTrack) {
            assertTrue(backend.tryPlayStreamedMusic(new StreamedMusicPort.TrackRef(OWNER, name)),
                    name + " track must resolve through the streamed music port");
        } else {
            assertTrue(backend.tryPlayStreamedSfx(new StreamedMusicPort.SfxRef(OWNER, name)),
                    name + " sfx must resolve through the streamed music port");
        }
        backend.update();
        assertNotNull(backend.uploaded, name + " must have produced an uploaded PCM buffer");
        assertTrue(java.util.stream.IntStream.range(0, backend.uploaded.length)
                        .anyMatch(index -> backend.uploaded[index] != 0),
                "The packaged " + name + " asset must decode and mix non-zero PCM through the bounded pool");
        backend.destroy();
    }

    /**
     * Adapted from {@code TestPhase3StandaloneSampleIntegration#exerciseMasterTitleNewCompleteAndContinue}.
     * {@code sample-platformer} has no second playable character and
     * {@code PlatformerModule#supportsSidekick()} is {@code false}, so every {@code SelectedTeam}
     * here (New Game default, written Continue payloads, and corrupt-slot payloads) carries an
     * empty sidekick list instead of phase-3's {@code FRIEND} second character.
     */
    private void exerciseMasterTitleNewCompleteAndContinue(Fixture fixture, GameModule module,
                                                            RecordingBackend backend) throws Exception {
        previousSaveRoot = System.getProperty("openggf.saveRoot");
        Path saveRoot = temp.resolve("saves");
        System.setProperty("openggf.saveRoot", saveRoot.toString());
        ModSubsystem.installProcess(new ModSubsystem(fixture.catalog, new ModRuntimeFindingStore(),
                (rate, game) -> SessionExternalContentView.EMPTY));

        EngineContext previous = EngineServices.current();
        previousEngineContext = previous;
        SonicConfigurationService config = SonicConfigurationService.createStandalone(temp.resolve("config"));
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext services = new EngineContext(config, previous.graphics(), previous.audio(),
                previous.roms(), previous.profiler(), previous.debugOverlay(), previous.playbackDebug(),
                previous.romDetection(), previous.crossGameFeatures(), previous.moduleResolutionService());
        EngineServices.configure(services);
        services.graphics().initHeadless();
        AudioManager audio = services.audio();
        audio.resetState();
        audio.setBackend(backend);
        Engine engine = new Engine(services);
        setField(engine, "modRuntime", fixture.runtime);
        installUninitializedTitleScreen(engine);
        MasterTitleEntry.Standalone standalone = standaloneEntry(engine);
        assertFalse(standalone.continueAvailable());
        launchThroughTitleScreen(engine, MasterTitleEntry.Action.NEW_GAME);

        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertTrue(SessionManager.getCurrentWorldSession().getDataSource().rom().isEmpty());
        var saveContext = SessionManager.getCurrentWorldSession().getSaveSessionContext();
        assertEquals(1, saveContext.activeSlot().orElseThrow());
        assertEquals(RUNNER, saveContext.selectedTeam().mainCharacter());
        assertTrue(saveContext.selectedTeam().sidekicks().isEmpty());
        assertEquals(0, GameServices.level().getCurrentZone());
        assertEquals(new StreamedMusicPort.TrackRef(OWNER, "zone-theme"), backend.track);
        assertTrue(Files.isRegularFile(saveRoot.resolve(OWNER).resolve("slot1.json")),
                "New Game must reserve and write namespaced slot 1");

        GameServices.level().advanceToNextLevel();
        assertEquals(1, GameServices.level().getCurrentZone(),
                "Terminal completion retains the out-of-range sentinel until returning to title");
        GameLoop loop = (GameLoop) getField(engine, "gameLoop");
        loop.setInputHandler(new InputHandler());
        AtomicBoolean returnedToTitle = new AtomicBoolean();
        invoke(loop, "setReturnToMasterTitleHandler", new Class<?>[] { Runnable.class },
                (Runnable) () -> {
                    returnedToTitle.set(true);
                    headlessReturnToMasterTitle(engine);
                });
        loop.step();
        for (int i = 0; i < 100 && !returnedToTitle.get(); i++) {
            GameServices.fade().update();
        }
        assertTrue(returnedToTitle.get(), "Credits requested by the real game-loop step must finish at title");
        assertEquals(GameMode.MASTER_TITLE_SCREEN, engine.getCurrentGameMode());
        assertNull(SessionManager.getCurrentWorldSession());

        var saved = new com.openggf.game.save.SaveManager(saveRoot)
                .readSlotSummary(OWNER, 1);
        assertTrue(saved.isLoadable());
        assertEquals(RUNNER, saved.payload().get("mainCharacter"));
        assertEquals(0, ((Number) saved.payload().get("zone")).intValue());

        new com.openggf.game.save.SaveManager(saveRoot).writeSlot(OWNER, 1,
                Map.of("zone", 0, "act", 0, "mainCharacter", RUNNER,
                        "sidekicks", List.of()));

        MasterTitleEntry.Standalone continued = standaloneEntry(engine);
        assertTrue(continued.continueAvailable());
        invoke(engine, "exitStandaloneMasterTitle",
                new Class<?>[] { MasterTitleEntry.Launch.class },
                new MasterTitleEntry.Launch(continued, MasterTitleEntry.Action.CONTINUE));
        assertEquals(GameMode.LEVEL, engine.getCurrentGameMode());
        assertEquals(0, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        assertEquals(RUNNER, SessionManager.getCurrentWorldSession().getSaveSessionContext()
                .selectedTeam().mainCharacter());
        assertTrue(SessionManager.getCurrentWorldSession().getSaveSessionContext()
                .selectedTeam().sidekicks().isEmpty());
        assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));

        SessionManager.clear();
        assertContinueHidden(fixture, services, saveRoot,
                Map.of("zone", 99, "act", 0, "mainCharacter", RUNNER, "sidekicks", List.of()),
                "A semantically invalid slot must hide Continue");
        assertContinueHidden(fixture, services, saveRoot,
                Map.of("zone", 0.5d, "act", 0, "mainCharacter", RUNNER, "sidekicks", List.of()),
                "A fractional zone must hide Continue instead of truncating");
        assertContinueHidden(fixture, services, saveRoot,
                Map.of("zone", 4_294_967_296L, "act", 0, "mainCharacter", RUNNER,
                        "sidekicks", List.of()),
                "An overflowing zone must hide Continue instead of wrapping");
        EngineServices.configure(previous);
        previousEngineContext = null;
    }

    private static void assertContinueHidden(Fixture fixture, EngineContext services, Path saveRoot,
                                             Map<String, Object> payload, String message) throws Exception {
        new com.openggf.game.save.SaveManager(saveRoot).writeSlot(OWNER, 1, payload);
        Engine corruptEngine = new Engine(services);
        setField(corruptEngine, "modRuntime", fixture.runtime);
        installUninitializedTitleScreen(corruptEngine);
        assertFalse(standaloneEntry(corruptEngine).continueAvailable(), message);
    }

    private static void launchThroughTitleScreen(Engine engine, MasterTitleEntry.Action action)
            throws Exception {
        com.openggf.game.MasterTitleScreen screen = engine.getMasterTitleScreen();
        List<MasterTitleEntry> entries = entries(screen);
        int index = java.util.stream.IntStream.range(0, entries.size())
                .filter(i -> entries.get(i) instanceof MasterTitleEntry.Standalone standalone
                        && OWNER.equals(standalone.owner()))
                .findFirst().orElseThrow();
        screen.setSelectedIndexForTest(index);
        InputHandler input = new InputHandler();
        GameLoop loop = (GameLoop) getField(engine, "gameLoop");
        loop.setInputHandler(input);

        pressTitleKey(loop, input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        if (action == MasterTitleEntry.Action.CONTINUE) {
            pressTitleKey(loop, input,
                    EngineServices.current().configuration().getInt(SonicConfiguration.DOWN));
        }
        pressTitleKey(loop, input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected(), "The real title chooser must publish a launch");
        assertEquals(action, screen.getSelectedLaunch().action());

        for (int i = 0; i < 100 && engine.getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN; i++) {
            EngineServices.current().graphics().getFadeManager().update();
        }
        for (int i = 0; i < 100 && engine.getCurrentGameMode() == GameMode.LEVEL
                && GameServices.fade().isActive(); i++) {
            GameServices.fade().update();
        }
    }

    private static void pressTitleKey(GameLoop loop, InputHandler input, int key) {
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        loop.step();
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        loop.step();
    }

    private Path buildSample() throws Exception {
        Path engine = temp.resolve("engine-local.jar");
        Path sdk = temp.resolve("openggf-mod-sdk-local.jar");
        createJar(Path.of("target/classes"), engine, entry -> !entry.startsWith("com/openggf/tools/modsdk/")
                && !entry.startsWith("META-INF/openggf-mod-sdk/"));
        createJar(Path.of("target/classes"), sdk, entry -> entry.startsWith("com/openggf/tools/modsdk/")
                || entry.startsWith("META-INF/openggf-mod-sdk/"));
        Path source = Path.of("src/test/resources/mods/sample-platformer-src").toAbsolutePath();
        Path project = temp.resolve("sample-platformer-project");
        List<String> command = System.getProperty("os.name", "").startsWith("Windows")
                ? List.of("powershell.exe", "-NoProfile", "-File", source.resolve("build.ps1").toString(),
                engine.toString(), sdk.toString(), project.toString())
                : List.of("sh", source.resolve("build.sh").toString(), engine.toString(), sdk.toString(),
                project.toString());
        runProcess(command);
        Path jar = project.resolve("target/sample-platformer-mod.jar");
        assertTrue(Files.isRegularFile(jar));
        return jar;
    }

    private Fixture load(Path jar) throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        Path packed = repo.resolve(jar.getFileName());
        Files.copy(jar, packed);
        var scanned = new DefaultModRepositoryScanner().scan(repo.toAbsolutePath().normalize());
        var validated = new ModCatalogValidator(repo.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        ModState state = new ModState(1, List.of(new ModState.Entry(OWNER, true, 0, true,
                descriptor.sha256())));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog.effective(), Set.of(OWNER));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return new Fixture(runtime, descriptor, catalog);
    }

    private static void runProcess(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static void createJar(Path root, Path jar, Predicate<String> include) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String entry = root.relativize(file).toString().replace('\\', '/');
                if (!include.test(entry)) continue;
                out.putNextEntry(new JarEntry(entry));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    private static Object getField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private static List<MasterTitleEntry> entries(com.openggf.game.MasterTitleScreen screen)
            throws Exception {
        var method = screen.getClass().getDeclaredMethod("entriesForTest");
        method.setAccessible(true);
        return (List<MasterTitleEntry>) method.invoke(screen);
    }

    private static MasterTitleEntry.Standalone standaloneEntry(Engine engine) throws Exception {
        return entries(engine.getMasterTitleScreen()).stream()
                .filter(MasterTitleEntry.Standalone.class::isInstance)
                .map(MasterTitleEntry.Standalone.class::cast)
                .filter(entry -> OWNER.equals(entry.owner())).findFirst().orElseThrow();
    }

    private static void installUninitializedTitleScreen(Engine engine) throws Exception {
        Object screen = invoke(engine, "createMasterTitleScreen", new Class<?>[0]);
        setField(engine, "masterTitleScreen", screen);
        setField(screen, "state", com.openggf.game.MasterTitleScreen.State.ACTIVE);
        ((GameLoop) getField(engine, "gameLoop")).setGameMode(GameMode.MASTER_TITLE_SCREEN);
    }

    private static void headlessReturnToMasterTitle(Engine engine) {
        try {
            invoke(engine, "resetForGameplayFromMasterTitle", new Class<?>[0]);
            GameLoop loop = (GameLoop) getField(engine, "gameLoop");
            loop.setGameplayMode(null);
            setField(engine, "gameplayMode", null);
            installUninitializedTitleScreen(engine);
        } catch (Exception failure) {
            throw new AssertionError("Headless master-title return failed", failure);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw (Exception) failure.getCause(); }
    }

    private record Fixture(ModRuntime runtime, ModDescriptor descriptor, ModCatalog catalog)
            implements AutoCloseable {
        @Override public void close() throws Exception { runtime.close(); }
    }

    private static final class RecordingBackend extends NullAudioBackend {
        private StreamedMusicPort.SfxRef sfx;
        private StreamedMusicPort.TrackRef track;
        @Override public boolean tryPlayStreamedMusic(StreamedMusicPort.TrackRef value) {
            track = value; return true;
        }
        @Override public boolean hasStreamedMusic(StreamedMusicPort.TrackRef value) { return true; }
        @Override public boolean tryPlayStreamedSfx(StreamedMusicPort.SfxRef value) {
            sfx = value; return true;
        }
    }

    private static final class PcmPoolBackend extends AbstractSmpsAudioBackend {
        private short[] uploaded;

        private PcmPoolBackend() {
            super(SonicConfigurationService.createStandalone(), null);
        }

        @Override protected int getDeviceSampleRate() { return 8_000; }
        @Override protected void hookInitDevice() { }
        @Override protected void hookDestroyDevice() { }
        @Override protected void hookStartStream() { }
        @Override protected void hookStopStreamSource() { }
        @Override protected void hookUpdateStream() { if (hasPresentationWork()) fillBuffer(1); }
        @Override protected void hookStopAndClearMusicSource() { }
        @Override protected void hookStopAndUnqueueAllMusicBuffers() { }
        @Override protected void hookStopAndClearAllMusicBuffers() { }
        @Override protected void hookRestartStreamIfDry() { }
        @Override protected void hookUploadStreamBuffer(int id, short[] pcm, int rate) {
            uploaded = pcm.clone();
        }
        @Override protected void hookPlayWavSfx(String name, float pitch) { }
        @Override protected void hookStopAndDeleteWavSfxSources() { }
        @Override protected void hookCleanupStoppedWavSfx() { }
        @Override protected void hookPause() { }
        @Override protected void hookResume() { }
    }
}
