package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.audio.AudioManager;
import com.openggf.audio.LevelMusicAccess;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.data.Rom;
import com.openggf.data.RomGame;
import com.openggf.data.RomManager;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectMetadata;
import com.openggf.editor.project.EditorProjectRuntimeModule;
import com.openggf.editor.project.EditorProjectStore;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.*;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Emerald Remix (examples/emerald-remix): the packaged jar is admitted through the ordinary
 * scanner, validator and resolver, then Sonic plays the remixed Emerald Hill Act 1 and the
 * original Clover Valley to their native results, and both levels pass through the editor.
 */
@Isolated
class TestEmeraldRemix {
    static final Path PROJECT = Path.of("examples/emerald-remix");
    static final Path CLOVER = PROJECT.resolve("src/main/resources/levels/clover-valley");
    static final ZoneKey.Mod CLOVER_KEY = new ZoneKey.Mod("emerald-remix", "clover-valley");
    static final int EHZ1_MAX_X = 10656, COLUMN = 128;
    /** The strips in remix/ehz1.remix: source act (0 = Act 1), first and last column. */
    static final int[][] STRIPS = {{0, 0, 6}, {1, 15, 21}, {0, 18, 35}, {0, 72, 77}, {0, 36, 38}, {0, 39, 71}, {0, 36, 38}, {0, 78, 90}};
    @TempDir Path temp;

    @BeforeEach void reset() {
        TestEnvironment.resetAll();
        var configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        configuration.resolveDisplayAspect();
    }
    @AfterEach void closeSession() { GroundSensor.setLevelManager(null); SessionManager.clear(); GameModuleRegistry.reset(); TestEnvironment.resetAll(); }

    record Fixture(ModRuntime runtime, GameModule root, GameModule resolved) implements AutoCloseable {
        public void close() throws Exception { runtime.close(); }
    }

    /** Compiles the example from source and packages it with the SDK, which runs the mod validator. */
    Path compilePackage() throws Exception {
        Path classes = Files.createDirectory(temp.resolve("classes"));
        try (var paths = Files.walk(PROJECT.resolve("src/main/resources"))) {
            for (Path source : paths.toList()) {
                Path target = classes.resolve(PROJECT.resolve("src/main/resources").relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(target); else Files.copy(source, target);
            }
        }
        var arguments = new ArrayList<>(List.of("--release", "25", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
        try (var paths = Files.walk(PROJECT.resolve("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(arguments::add);
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        Path jar = Files.createDirectory(temp.resolve("mods")).resolve("emerald-remix.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", classes.toString(), "--out", jar.toString()}, System.out));
        return jar;
    }

    static Rom open(java.io.File file) throws Exception {
        Rom rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        RomManager.getInstance().setRom(rom);
        return rom;
    }

    Fixture load(Path jar) throws Exception {
        var scanned = new DefaultModRepositoryScanner().scan(jar.getParent().toAbsolutePath());
        var validated = new ModCatalogValidator(jar.getParent().toAbsolutePath(), ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = assertInstanceOf(ModDescriptor.class, validated.entries().getFirst());
        assertFalse(descriptor.hasErrors(), descriptor.findings().toString());
        var catalog = new EffectiveCatalogBuilder().build(validated.entries(),
                new ModState(1, List.of(new ModState.Entry("emerald-remix", true, 0, true, descriptor.sha256()))));
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(catalog.effective(), Set.of("emerald-remix"));
        runtime.installStorageRoot(temp.resolve("saves"));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(), ignored -> new ModStateSaveResult.Saved(), runtime::disableOwnersForProcess));
        var plan = runtime.newRegistrationPlan();
        assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
        // The remix patch declares the S2 ROM as a prerequisite; resolve it from the ROM catalogue.
        var roms = LogicalRomResolver.fromRomManager(RomManager.getInstance());
        assumeTrue(roms.isAvailable(LogicalRom.S2), "The ROM catalogue must find S2 REV01 in the working directory");
        var resolver = new ModuleResolutionService(List.of(), new EffectiveCatalogPatchEnablement(catalog.effective()), roms,
                SonicConfigurationService.getInstance(), ignored -> plan);
        GameModule root = new Sonic2GameModule();
        GameModule resolved = resolver.resolveForLaunch(root, GameplayLaunchRequest.fromConfig(SonicConfigurationService.getInstance(), "s2"),
                ModuleResolutionService.LaunchPolicy.STANDARD);
        return new Fixture(runtime, root, resolved);
    }

    record Play(Rom rom, GameplayModeContext gameplay, AbstractPlayableSprite main, LevelManager manager, HeadlessTestRunner runner) { }

    Play play(Rom rom, Fixture fixture) throws Exception {
        EngineServices.current().graphics().initHeadless();
        var gameplay = SessionManager.openGameplaySession(fixture.root, fixture.resolved, StockGameDataSources.pinned(rom, fixture.root), null);
        GameModuleRegistry.setCurrent(fixture.resolved);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        gameplay.getLevelManager().setRewindClassResolver(new ModClassResolver(fixture.runtime, getClass().getClassLoader()));
        var main = GameplayTeamBootstrap.registerActiveTeam(fixture.resolved, gameplay.getSpriteManager(), SonicConfigurationService.getInstance()).mainSprite();
        gameplay.getCamera().setFocusedSprite(main);
        gameplay.getCamera().setFrozen(false);
        return new Play(rom, gameplay, main, gameplay.getLevelManager(), new HeadlessTestRunner(main));
    }

    void enter(Play play, int zone, int act) throws Exception {
        play.manager.loadZoneAndAct(zone, act);
        GroundSensor.setLevelManager(play.manager);
        play.gameplay.getCamera().updatePosition(true);
    }

    /**
     * Holds right and jumps whenever progress stalls; every third stall it spindashes instead,
     * which climbs the quarter-pipes on stock Emerald Hill's lower route. Runs until the level
     * changes. Damage is switched off so the run measures the terrain; badnik and spike
     * placement is stock design.
     */
    static boolean runToNextLevel(Play play, int frames) {
        int zone = play.manager.getCurrentZone(), act = play.manager.getCurrentAct();
        int best = play.main.getCentreX(), stalled = 0, stalls = 0;
        var script = new ArrayDeque<boolean[]>();          // {down, right, jump}
        for (int frame = 0; frame < frames; frame++) {
            if (play.manager.getCurrentZone() != zone || play.manager.getCurrentAct() != act) return true;
            play.main.setInvulnerableFrames(600);
            boolean[] input = script.isEmpty() ? new boolean[]{false, true, false} : script.poll();
            play.runner.stepFrame(false, input[0], false, input[1], input[2]);
            int x = play.main.getCentreX();
            if (System.getProperty("remix.trace") != null && frame % 60 == 0)
                System.err.println("TRACE f=" + frame + " x=" + x + " y=" + play.main.getCentreY() + " gs=" + play.main.getGSpeed() + " air=" + play.main.getAir());
            if (x > best) { best = x; stalled = 0; stalls = 0; continue; }
            if (++stalled <= 45 || !script.isEmpty()) continue;
            stalled = 0;
            if (++stalls % 3 == 0) {
                for (int i = 0; i < 8; i++) script.add(new boolean[]{true, false, false});
                for (int rev = 0; rev < 4; rev++) {
                    for (int i = 0; i < 3; i++) script.add(new boolean[]{true, false, true});
                    for (int i = 0; i < 3; i++) script.add(new boolean[]{true, false, false});
                }
            } else {
                for (int i = 0; i < 24; i++) script.add(new boolean[]{false, true, true});
            }
        }
        return false;
    }

    @Test void recipeRebuildsActOneFromStockStripsAndCloverValleyFollowsActTwo() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            var registry = fixture.resolved.getZoneRegistry();
            int clover = registry.resolveZoneKey(CLOVER_KEY).orElseThrow();
            assertEquals(new ZoneProgressionPlan.Successor(clover, 0), registry.progressionPlan().next(registry.progressionTopology(), 0, 1),
                    "Clover Valley follows Emerald Hill Act 2");
            assertEquals(new ZoneProgressionPlan.Successor(1, 0), registry.progressionPlan().next(registry.progressionTopology(), clover, 0),
                    "Chemical Plant follows Clover Valley");

            var play = play(rom, fixture);
            enter(play, 0, 0);
            Level remix = play.manager.getCurrentLevel();
            var stock = new Sonic2(rom);
            Level[] acts = {stock.buildDetachedLevel(0), stock.buildDetachedLevel(1)};
            int column = 0;
            for (int[] strip : STRIPS) {
                for (int dx = 0; dx <= strip[2] - strip[1]; dx++)
                    for (int y = 0; y < remix.getMap().getHeight(); y++)
                        assertEquals(y < 8 ? acts[strip[0]].getMap().getValue(0, strip[1] + dx, y) : 0, remix.getMap().getValue(0, column + dx, y),
                                "column " + (column + dx) + " row " + y + " (rows under Act 1's kill plane stay empty)");
                column += strip[2] - strip[1] + 1;
            }
            assertEquals(90, column);
            for (int x = column; x < remix.getMap().getWidth(); x++) assertEquals(0, remix.getMap().getValue(0, x, 4));
            assertArrayEquals(layer(acts[0], 1), layer(remix, 1), "The background layer is Act 1's own");
            int shift = (90 - 13 - 78) * COLUMN;
            assertEquals(EHZ1_MAX_X + shift, remix.getMaxX(), "The camera bound moves with the signpost's strip");

            List<ObjectSpawn> objects = remix.getObjects();
            for (int i = 1; i < objects.size(); i++) assertTrue(objects.get(i - 1).x() <= objects.get(i).x(), "placements are x-sorted");
            for (int i = 0; i < objects.size(); i++) assertEquals(i, objects.get(i).layoutIndex());
            assertEquals(List.of(1, 2, 3), objects.stream().filter(o -> o.objectId() == 0x79).map(ObjectSpawn::subtype).toList(),
                    "Act 1's checkpoints keep their order");
            assertTrue(objects.stream().allMatch(o -> o.y() < 8 * COLUMN), "nothing is placed under the kill plane");
            assertEquals(4, objects.stream().filter(o -> o.objectId() == 0x06).count(),
                    "the triple corkscrew (Act 1's double then single) plus the single again");
            assertTrue(objects.stream().anyMatch(o -> o.objectId() == 0x26 && o.x() == 448 && o.y() == 628 && o.subtype() == 6),
                    "the recipe's authored monitor is placed");
            var signpost = objects.stream().filter(o -> o.objectId() == 0x0D).toList();
            assertEquals(1, signpost.size());
            assertEquals(0x2A40 + shift, signpost.getFirst().x());
            assertFalse(remix.getRings().isEmpty(), "rings travel with their strips");
            assertTrue(fixture.runtime.runtimeDisabledOwners().isEmpty());
        }
    }

    @Test void sonicCompletesTheRemixedActAndCloverValley() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            var play = play(rom, fixture);
            enter(play, 0, 0);
            play.runner.stepIdleFrames(30);
            assertTrue(runToNextLevel(play, 12000), "Sonic reaches the remixed act's signpost and results; stopped at x="
                    + play.main.getCentreX() + " y=" + play.main.getCentreY());
            assertEquals(0, play.manager.getCurrentZone());
            assertEquals(1, play.manager.getCurrentAct(), "Native results lead on to Act 2");

            int clover = fixture.resolved.getZoneRegistry().resolveZoneKey(CLOVER_KEY).orElseThrow();
            enter(play, clover, 0);
            play.runner.stepIdleFrames(30);
            assertFalse(play.main.getAir(), "Sonic stands on Clover Valley's original ground");
            boolean checkpoint = false;
            int zone = play.manager.getCurrentZone();
            for (int burst = 0; burst < 60 && play.manager.getCurrentZone() == zone; burst++) {
                runToNextLevel(play, 100);
                checkpoint |= play.manager.getCheckpointState().isActive();
            }
            assertTrue(checkpoint, "The authored checkpoint is on the route");
            assertEquals(1, play.manager.getCurrentZone(), "Clover Valley's signpost and results lead to Chemical Plant; stopped at x="
                    + play.main.getCentreX());
            assertTrue(fixture.runtime.runtimeDisabledOwners().isEmpty());
        }
    }

    @Test void cloverValleyRoundTripsThroughEditorImportProjectExportAndPlaytest() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romFile.getAbsolutePath()));
            RomManager.getInstance().setRom(rom);
            EngineServices.current().graphics().initHeadless();
            GameModule module = new Sonic2GameModule();
            GameModuleRegistry.setCurrent(module);
            module.createGame(rom);
            ModLevelDefinition parsed;
            Path root = CLOVER.toAbsolutePath();
            try (var assets = ModAssetRoot.directory(root.getParent(), root, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
                parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            }
            // The same steps as the editor's "Import exported level".
            var data = ModZoneLoader.prepareHostData(parsed);
            module.getModZoneAdapter().validate("openggf-editor", data);
            Level imported = module.getModZoneAdapter().load("openggf-editor", data);
            var music = new FullLevelExporter.ExportMusic.Stock(((ModLevelDefinition.StockMusic) parsed.music()).stockId());
            var metadata = new EditorProjectMetadata(GameId.S2, parsed.zoneName(), 0, 0, "test", parsed.start().x(), parsed.start().y(),
                    parsed.bounds().minX(), parsed.bounds().maxX(), parsed.bounds().minY(), parsed.bounds().maxY(), music, null);
            var store = new EditorProjectStore();
            Path project = temp.resolve("clover-valley.ogfproject");
            store.save(project, EditorProjectDocument.cloneLevel(metadata, imported));
            var reopened = store.open(project);
            assertEquals(parsed.objects().size(), reopened.level().getObjects().size());
            assertEquals(parsed.rings().size(), reopened.level().getRings().size());

            Path export = temp.resolve("export");
            new FullLevelExporter(new ModLevelExportStagingValidator(module)).export(reopened.level(),
                    EditorProjectRuntimeModule.request(reopened, export, parsed.start().x(), parsed.start().y()));
            // The exporter keeps spare chunk/block slots for editing, so compare what the level draws and collides with.
            ModLevelDefinition exported;
            try (var assets = ModAssetRoot.directory(temp, export, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
                exported = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            }
            Level reloaded = module.getModZoneAdapter().load("openggf-editor", ModZoneLoader.prepareHostData(exported));
            assertEquals(terrain(imported), terrain(reloaded), "Every placed tile, palette line and collision profile survives the editor");
            assertEquals(parsed.objects().size(), exported.objects().size());
            assertEquals(parsed.rings().size(), exported.rings().size());
            assertEquals(parsed.bounds(), exported.bounds());

            var playtest = EditorProjectRuntimeModule.prepare(module, reopened, parsed.start().x(), parsed.start().y());
            assertNotNull(playtest, "The project is accepted for playtest");
        }
    }

    @Test void authoredMusicCancelsStockWorkAndReturningStockKeepsItsNativeBoundary() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            var play = play(rom, fixture);
            var audio = GameServices.audio();
            audio.setBackend(new NullAudioBackend()); // real synthesis over the default NoDeviceAudioSink
            var profile = fixture.resolved.getLevelInitProfile();
            int clover = fixture.resolved.getZoneRegistry().resolveZoneKey(CLOVER_KEY).orElseThrow();

            enter(play, 0, 0);
            assertTrue(profile.isLevelMusicPublicationPending());
            audio.presentFrame(PresentationMode.FORWARD);
            assertNull(audio.captureLogicalSnapshot().presentation().activeMusic(),
                    "stock preparation cannot publish before its native VBlank boundary");

            enter(play, clover, 0);
            assertFalse(profile.isLevelMusicPublicationPending(),
                    "an authored destination must retire the previous stock countdown");
            assertEquals(OptionalInt.of(0x91), play.manager.prepareCurrentLevelMusic());
            for (int frame = 0; frame < 12; frame++) profile.serviceLevelLoadVBlank();
            assertForwardMusicPcm(audio, 0x91, AudioSourceDescriptor.baseMusic(0x91));
            assertTrue(audio.captureLogicalSnapshot().presentation().smpsLogical().sequencers().stream()
                    .anyMatch(entry -> !entry.sfx() && entry.snapshot().tracks().stream().anyMatch(track -> track.active())),
                    "the authored stock-music selection must reach a real ROM SMPS sequencer");

            enter(play, 0, 0);
            assertTrue(profile.isLevelMusicPublicationPending());
            // EHZ, NTSC, Sonic alone: the existing independent timing-model
            // regression pins this ROM workload to bucket 11.
            for (int frame = 0; frame < 10; frame++) profile.serviceLevelLoadVBlank();
            assertTrue(profile.isLevelMusicPublicationPending(), "returning stock must not publish early");
            profile.serviceLevelLoadVBlank();
            assertFalse(profile.isLevelMusicPublicationPending());
            assertForwardMusicPcm(audio, 0x81, AudioSourceDescriptor.baseMusic(0x81));

            try (var control = LevelMusicAccess.suppressAutomatic(audio)) {
                enter(play, clover, 0);
                assertFalse(profile.isLevelMusicPublicationPending());
                for (int frame = 0; frame < 12; frame++) {
                    profile.serviceLevelLoadVBlank();
                    audio.presentFrame(PresentationMode.FORWARD);
                }
                var active = audio.captureLogicalSnapshot().presentation().activeMusic();
                assertTrue(active == null || active.musicId() != 0x91,
                        "authored stock music must respect the canonical automatic-music control");
            }
            profile.serviceLevelLoadVBlank();
            audio.presentFrame(PresentationMode.FORWARD);
            var active = audio.captureLogicalSnapshot().presentation().activeMusic();
            assertTrue(active == null || active.musicId() != 0x91,
                    "releasing a control must not resurrect a suppressed authored request");
            assertTrue(fixture.runtime.runtimeDisabledOwners().isEmpty());
        }
    }

    @Test void authoredNamespacedMusicUsesPreparedPublicationAndHonorsBothSuppressionOwners() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            // Keep the admitted, decoded authored payload and runtime profile.
            // Only this test's selected music changes to an exact namespaced key.
            var original = ((ModZoneRegistry) fixture.resolved.getZoneRegistry()).contributions().getFirst();
            var base = original.definition();
            var key = new TrackKey("emerald-remix", "authored-theme");
            var definition = new ModLevelDefinition(base.formatVersion(), base.zoneName(), base.zoneIndex(),
                    base.levelIndex(), base.blockGridSide(), base.width(), base.height(), base.bounds(),
                    base.start(), new ModLevelDefinition.TrackMusic(key), base.objects(), base.rings(),
                    base.patternBytes(), base.chunkBytes(), base.blockBytes(), base.foregroundMap(),
                    base.backgroundMap().orElse(null), base.solidHeights(), base.solidWidths(), base.solidAngles(),
                    base.primaryCollisionIndices(), base.secondaryCollisionIndices(), base.paletteLines(),
                    base.patternCount(), base.chunkCount(), base.blockCount(), base.solidProfileCount(),
                    base.hostMetadata().orElse(null), base.paletteClaims(), base.settings());
            var contributed = new PreparedModZone(original.ownerModId(), original.localKey(), original.insertAfter(),
                    definition, original.eventFactory(), original.zoneName(), original.levelIndex(), original.authoredZoneIndex(),
                    original.startX(), original.startY(), original.runtimeProfile(), original.hostData(), original.gameStart(),
                    original.actIndex(), original.runtimeFactory(), original.terminalCampaign());
            var registry = ModZoneRegistry.decorate(fixture.root.getZoneRegistry(), List.of(contributed));
            var selected = new DelegatingGameModule(fixture.resolved, "test:authored-namespaced-music") {
                @Override public ZoneRegistry getZoneRegistry() { return registry; }
                @Override public MusicReference getLevelMusicReference(int zone, int act) {
                    return registry.getMusicReference(zone, act);
                }
            };
            var play = play(rom, new Fixture(fixture.runtime, fixture.root, selected));
            var audio = GameServices.audio();
            audio.setBackend(new NullAudioBackend());
            int rate = audio.outputSampleRate();
            var track = new PreparedTrack(key, PcmData.takeOwnership(rate, 1,
                    new short[]{2048, -2048, 4096, -4096}), 0, 4, 1.0f, false, "a".repeat(64));
            var manifest = new ModManifest(1, "emerald-remix", "Authored music", SemanticVersion.parse("1.0.0"),
                    List.of("Test"), "Namespaced publication fixture", VersionRange.parse("*"), ModType.PATCH,
                    "s2", null, List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
            var descriptor = new ModDescriptor(Path.of("emerald-remix.jar"), manifest, "b".repeat(64), false, List.of());
            var tracks = new ModTrackRegistry(List.of(new ModAudioTrack(key, "audio/theme.wav", true,
                    0, OptionalLong.of(4), 1.0f, false)));
            var music = PreparedModMusic.build(new EffectiveModCatalog(List.of(descriptor)), tracks,
                    new PreparedAudioSession(List.of(track), List.of(), Set.of()), rate);
            audio.installStreamedMusicPort(new com.openggf.ModStreamedMusicPort(music, new StreamedMusicPlayer(rate), "s2"));

            int clover = registry.resolveZoneKey(CLOVER_KEY).orElseThrow();
            var profile = selected.getLevelInitProfile();
            enter(play, 0, 0);
            assertTrue(profile.isLevelMusicPublicationPending());
            // A preview selects real authored data without executing either
            // entry-music step. Re-entering the ordered music step below must
            // therefore cancel the still-prepared stock request itself.
            play.manager.loadZoneAndAct(clover, 0, LevelLoadMode.PREVIEW_CAPTURE);
            assertTrue(profile.isLevelMusicPublicationPending());
            var ctx = new LevelLoadContext();
            ctx.setLevelIndex(registry.getLevelDataForZone(clover).getFirst().levelIndex());
            var schedule = profile.levelLoadSteps(ctx).stream()
                    .filter(step -> step.name().equals("ScheduleLevelMusic")).findFirst().orElseThrow();
            try (var control = LevelMusicAccess.suppressAutomatic(audio)) {
                assertEquals(OptionalInt.of(-1), play.manager.prepareCurrentLevelMusic(),
                        "namespaced preparation has no stock ID and does not itself publish");
                schedule.execute();
                assertFalse(profile.isLevelMusicPublicationPending());
                audio.presentFrame(PresentationMode.FORWARD);
                assertNull(audio.captureLogicalSnapshot().presentation().activeMusic());
            }
            play.manager.setSuppressNextMusicChange(true);
            schedule.execute();
            profile.serviceLevelLoadVBlank();
            audio.presentFrame(PresentationMode.FORWARD);
            assertNull(audio.captureLogicalSnapshot().presentation().activeMusic(),
                    "one-shot suppression must consume the prepared request without publishing");

            schedule.execute();
            assertFalse(profile.isLevelMusicPublicationPending(), "-1 must never arm a stock countdown");
            assertNull(audio.captureLogicalSnapshot().presentation().activeMusic(),
                    "publication still awaits the ordinary forward presentation boundary");
            assertForwardMusicPcm(audio, -1, AudioSourceDescriptor.streamedTrack("emerald-remix:authored-theme"));
            assertTrue(fixture.runtime.runtimeDisabledOwners().isEmpty());
        }
    }

    private static void assertForwardMusicPcm(AudioManager audio, int musicId, AudioSourceDescriptor source) {
        boolean nonzero = false;
        try (var capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate())) {
            short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
            for (int frame = 0; frame < 12; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                int frames = capture.drainPresentationFrame(pcm);
                assertTrue(frames > 0, "forward presentation must produce a capture packet");
                var active = audio.captureLogicalSnapshot().presentation().activeMusic();
                if (active != null && active.musicId() == musicId && source.equals(active.sourceDescriptor())) {
                    for (int sample = 0; sample < frames * 2; sample++) nonzero |= pcm[sample] != 0;
                }
            }
        }
        var active = audio.captureLogicalSnapshot().presentation().activeMusic();
        assertNotNull(active, "the selected authored/stock music must become an admitted voice");
        assertEquals(musicId, active.musicId());
        assertEquals(source, active.sourceDescriptor());
        assertTrue(nonzero, "the real presentation owner must render forward music PCM");
    }

    @Test void remixedActIsAdoptedByTheEditorAndExportsAsAPlayableLevel() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            var play = play(rom, fixture);
            enter(play, 0, 0);
            play.runner.stepIdleFrames(60);
            Level remix = play.manager.getCurrentLevel();
            // Explicit "Clone native layout as project": adoptLoadedLevel clones the edited layout.
            int[] start = fixture.resolved.getZoneRegistry().getStartPosition(0, 0);
            var metadata = new EditorProjectMetadata(GameId.S2, "Emerald Hill Act 1", 0, 0, "test", start[0], start[1],
                    remix.getMinX(), remix.getMaxX(), remix.getMinY(), remix.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(0x81), null);
            var document = EditorProjectDocument.cloneLevel(metadata, remix);
            assertArrayEquals(layer(remix, 0), layer(document.level(), 0), "The editor shows the remixed layout");
            assertNotNull(EditorProjectRuntimeModule.prepare(fixture.root, document, start[0], start[1]), "The adopted remix is playtestable");
            Path export = temp.resolve("remix-export");
            new FullLevelExporter(new ModLevelExportStagingValidator(fixture.root)).export(document.level(),
                    EditorProjectRuntimeModule.request(document, export, start[0], start[1]));
            try (var assets = ModAssetRoot.directory(temp, export, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
                var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
                assertEquals(remix.getObjects().size(), parsed.objects().size());
            }
        }
    }

    /**
     * The engine, like the ROM, snaps a floor angle that changes by 0x20 or more in one step to
     * a cardinal angle, so Clover Valley's slopes ease in. Sonic must actually take the slope
     * angles on its first hill rather than run it as flat ground.
     */
    @Test void cloverValleySlopesTurnSonicThroughEasedAngles() throws Exception {
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Requires configured S2 World REV01 ROM");
        try (Rom rom = open(romFile);
             var catalogue = RomCatalogueFixture.bind(GameServices.configuration(), RomManager.getInstance(),
                     Map.of(RomGame.S2, romFile.toPath()));
             Fixture fixture = load(compilePackage())) {
            var play = play(rom, fixture);
            enter(play, fixture.resolved.getZoneRegistry().resolveZoneKey(CLOVER_KEY).orElseThrow(), 0);
            var angles = new LinkedHashSet<Integer>();
            for (int frame = 0; frame < 240; frame++) {
                play.runner.stepFrame(false, false, false, true, false);
                if (!play.main.getAir()) angles.add(play.main.getAngle() & 0xFF);
            }
            assertTrue(angles.containsAll(List.of(0xEC, 0xE0, 0x14, 0x20)),
                    "eased and 45-degree angles up and down the first hill; saw " + angles);
        }
    }

    /** Everything a placed cell shows and collides with, independent of definition numbering. */
    static String terrain(Level level) {
        var map = level.getMap();
        var out = new StringBuilder();
        int side = level.getChunksPerBlockSide();
        for (int layer = 0; layer < map.getLayerCount(); layer++)
            for (int y = 0; y < map.getHeight(); y++)
                for (int x = 0; x < map.getWidth(); x++) {
                    var block = level.getBlock(Byte.toUnsignedInt(map.getValue(layer, x, y)));
                    for (int c = 0; c < side * side; c++) {
                        var desc = block.getChunkDesc(c % side, c / side);
                        var chunk = level.getChunk(desc.getChunkIndex());
                        out.append(desc.get() & 0xFC00).append(',').append(Arrays.toString(level.getSolidTile(chunk.getSolidTileIndex()).heights));
                        for (int t = 0; t < 4; t++) {
                            var pattern = chunk.getPatternDesc(t % 2, t / 2);
                            out.append(pattern.get() & 0xF800).append(':');
                            for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) out.append(Character.forDigit(level.getPattern(pattern.getPatternIndex()).getPixel(px, py), 16));
                        }
                    }
                    out.append('\n');
                }
        return Integer.toHexString(out.toString().hashCode()) + " over " + out.length() + " chars";
    }

    static byte[] layer(Level level, int layer) {
        var map = level.getMap();
        byte[] cells = new byte[map.getWidth() * map.getHeight()];
        for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++) cells[y * map.getWidth() + x] = map.getValue(layer, x, y);
        return cells;
    }
}
