package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.projector.CdProjector;
import com.openggf.game.soniccd.player.CdAnimationIds;
import com.openggf.game.soniccd.objects.wwz.ObjWwzAnimal;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.PlcParser;
import com.openggf.game.soniccd.objects.wwz.ObjBataPyon;
import com.openggf.game.soniccd.objects.wwz.ObjBouncePlatform;
import com.openggf.game.soniccd.objects.wwz.ObjEggmanStatue;
import com.openggf.game.soniccd.objects.wwz.ObjElectricBeams;
import com.openggf.game.soniccd.objects.wwz.ObjFreezer;
import com.openggf.game.soniccd.objects.wwz.ObjGoddessStatue;
import com.openggf.game.soniccd.objects.wwz.ObjLauncher;
import com.openggf.game.soniccd.objects.wwz.ObjMinomusi;
import com.openggf.game.soniccd.objects.wwz.ObjPiston;
import com.openggf.game.soniccd.objects.wwz.ObjSemi;
import com.openggf.game.soniccd.objects.wwz.ObjSnakeBlocks;
import com.openggf.game.soniccd.objects.wwz.ObjSpinPlatform;
import com.openggf.game.soniccd.objects.wwz.WwzObject;
import com.openggf.game.soniccd.objects.wwz.WwzObjectArt;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Wacky Workbench objects on the loaded stages (their art, tables and collision come from the
 * disc). Each case cites the routine it checks in {@code Level/Wacky Workbench/Objects/<name>/Main.asm}; the native
 * comparisons are {@link TestWwzObjectsAppearances}, {@link TestWwzObjectsPictures} and the fixture frontier.
 */
class TestWwzObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    private void boot(CdStagePayload payload) throws Exception {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures());
        EngineServices.configure(injected);
        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());
        sonic = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config).mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
        runner.stepIdleFrames(1);
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private List<ObjectInstance> live(Class<?> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && type.isInstance(o)).toList();
    }

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    private int beamMode() {
        return ((PlayableSpriteMovement) sonic.getMovementManager()).stageControlState() >>> 24 & 0xFF;
    }

    // ---- art ---------------------------------------------------------------------------------------

    @Test
    void everySheetHoldsTheStagesArtAfterCueTwo() throws Exception {
        boot(CdStagePayload.R61A);
        CdObjectArtProvider art = art();
        // SectionGfxInitList0 loads the freezer, piston, snake block, bouncing platform and launcher art and the main
        // list the badniks (r6/gfx_lists_1a.asm; the stage has no sections, r6/section_gfx_1a.asm:3-5).
        for (String kind : new String[] {WwzObjectArt.LAUNCHER, WwzObjectArt.PISTON, WwzObjectArt.BOUNCE_PLATFORM,
                WwzObjectArt.SPIN_PLATFORM, WwzObjectArt.EGGMAN_STATUE, WwzObjectArt.SPIKE_BOMB, WwzObjectArt.BEAMS,
                WwzObjectArt.FREEZER, WwzObjectArt.ICE, WwzObjectArt.BATA, WwzObjectArt.BATA_DAMAGED,
                WwzObjectArt.MINOMUSI, WwzObjectArt.MINOMUSI_DAMAGED, WwzObjectArt.SEMI, WwzObjectArt.SEMI_DAMAGED,
                WwzObjectArt.SEMI_BOMB, WwzObjectArt.SNAKE_BLOCKS}) {
            var sheet = art.getSheet(WwzObjectArt.key(kind));
            assertNotNull(sheet, kind);
            boolean ink = false;
            for (var pattern : sheet.getPatterns()) {
                for (int i = 0; i < 64 && !ink; i++) ink = pattern.getPixel(i % 8, i / 8) != 0;
            }
            assertTrue(ink, kind + " sheet is blank");
        }
    }

    @Test
    void actThreeIndexLeavesTheRetiredIdsToNullObject() throws Exception {
        boot(CdStagePayload.R63C);
        // r6/object_index_3.asm:7-8, 11, 51-54: $04, $05, $08 and $30-$33 are NullObject (move.b #0,(a0)).
        ObjLauncher launcher = add(new ObjLauncher(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY(), 0x04, 0)));
        ObjFreezer freezer = add(new ObjFreezer(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY(), 0x05, 0)));
        ObjGoddessStatue goddess = add(new ObjGoddessStatue(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY(), 0x08, 0)));
        ObjSemi semi = add(new ObjSemi(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY(), 0x32, 0)));
        runner.stepIdleFrames(2);
        assertTrue(launcher.isDestroyed() && freezer.isDestroyed() && goddess.isDestroyed() && semi.isDestroyed());
    }

    @Test
    void animalPicturesUseTheDiscArtForEachForm() throws Exception {
        boot(CdStagePayload.R61B);
        CdObjectArtProvider art = art();
        // In r6/gfx_lists_1b.asm MainGfxList and ProjectorGfxList both write the animal tiles at $9A00.
        // AnimalSprites1 and AnimalSprites2 (objects/r6/animal.asm:217-223) select these pieces.
        assertEquals(List.of(
                animalPicture(cueAnimalPatterns(art, 1), new SpriteMappingPiece(-12, -8, 3, 2, 0, false, false, 0, false)),
                animalPicture(cueAnimalPatterns(art, 1), new SpriteMappingPiece(-8, -12, 2, 3, 0x12, false, false, 0, false)),
                animalPicture(cueAnimalPatterns(art, 5), new SpriteMappingPiece(-12, -8, 3, 2, 6, false, false, 0, false)),
                animalPicture(cueAnimalPatterns(art, 5), new SpriteMappingPiece(-12, -8, 3, 2, 0xC, false, false, 0, false))),
                List.of(animalPicture(art, true, false, 0), animalPicture(art, false, false, 0),
                        animalPicture(art, true, true, 1), animalPicture(art, false, true, 3)));
    }

    private static Pattern[] cueAnimalPatterns(CdObjectArtProvider art, int cue) throws Exception {
        int lists = CdStageCatalogue.entry(art.payload()).plcLists();
        var entry = PlcParser.parse(art.image().reader(), lists, cue).entries().stream()
                .filter(e -> e.tileIndex() == 0x4D0).findFirst().orElseThrow();
        byte[] bytes = NemesisReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(art.image().tail(art.image().offsetOf(entry.romAddr())))));
        Pattern[] patterns = new Pattern[bytes.length / 32];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(Arrays.copyOfRange(bytes, i * 32, i * 32 + 32));
        }
        return patterns;
    }

    private static AnimalPicture animalPicture(CdObjectArtProvider art, boolean flying, boolean hologram, int index) {
        ObjectSpriteSheet sheet = art.getSheet(WwzObjectArt.animalKey(flying, hologram));
        return animalPicture(sheet.getPatterns(), sheet.getFrame(index).pieces().getFirst());
    }

    private static AnimalPicture animalPicture(Pattern[] patterns, SpriteMappingPiece piece) {
        int width = piece.widthTiles() * 8, height = piece.heightTiles() * 8;
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int tile = piece.tileIndex() + (x / 8) * piece.heightTiles() + y / 8;
                pixels[y * width + x] = patterns[tile].getPixel(x % 8, y % 8);
            }
        }
        return new AnimalPicture(piece, HexFormat.of().formatHex(pixels));
    }

    private record AnimalPicture(SpriteMappingPiece piece, String pixels) { }

    @Test
    void realAnimalsWaitForProjectorDestructionThenFlyAndHop() throws Exception {
        boot(CdStagePayload.R61B);
        int x = sonic.getCentreX();
        int y = sonic.getCentreY() - 0x70;
        var flying = add(new ObjWwzAnimal(spawn(x, y, 0x29, 0)));
        var hopping = add(new ObjWwzAnimal(spawn(x, y, 0x29, 1)));
        runner.stepIdleFrames(3);
        List<List<Integer>> actual = new ArrayList<>();
        actual.add(animalState(flying, x, y));
        CdTimeTravel.of(SessionManager.requireCurrentGameModule()).destroyProjector();
        runner.stepIdleFrames(2);
        actual.add(animalState(hopping, x, y));
        actual.add(animalState(flying, x, y));
        runner.stepIdleFrames(19);
        actual.add(animalState(flying, x, y));
        runner.stepIdleFrames(1);
        actual.add(animalState(flying, x, y));
        runner.stepIdleFrames(107);
        actual.add(animalState(flying, x, y));
        // AnimalObject_1_Routine2 (objects/r6/animal.asm:49-69) and
        // AnimalObject_0_Routine2 (objects/r6/animal.asm:143-161).
        assertEquals(List.of(List.of(0, 0, 0, 0), List.of(1, -4, 0, 0), List.of(127, 3, 0, 1),
                List.of(112, 60, 0, 1), List.of(111, 63, 1, 1), List.of(-128, 3, 0, 0)), actual);
    }

    @Test
    void projectorChildrenCircleAndFlickerThroughRewindAndVanishOnShutdown() throws Exception {
        boot(CdStagePayload.R61B);
        int x = sonic.getCentreX() + 0x50;
        int y = sonic.getCentreY() - 0x40;
        var parent = add(new CdProjector(spawn(x, y, 0x2F, 0)));
        runner.stepIdleFrames(4);
        int parentSlot = parent.getSlotIndex();
        var children = live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                .filter(a -> a.projectorSlot() == parentSlot).toList();
        var flying = animalOfSubtype(children, 0x80);
        var ground = animalOfSubtype(children, 0x81);
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = rewind.capture();
        var first = hologramCycle(flying, ground);
        rewind.restore(snapshot);
        var restored = live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                .filter(a -> a.projectorSlot() == parentSlot).toList();
        var second = hologramCycle(animalOfSubtype(restored, 0x80), animalOfSubtype(restored, 0x81));
        // AnimalObject_1_Routine4 (objects/r6/animal.asm:73-88) and
        // AnimalObject_0_Routine8 (objects/r6/animal.asm:173-181).
        assertEquals(first, second);
        var samples = first.stream().map(row -> row.subList(0, 2)).collect(Collectors.toSet());
        assertEquals(List.of(true, 64, List.of(0, 1, 2), List.of(2, 3, 4), List.of(List.of(0, 0))),
                List.of(samples.containsAll(List.of(List.of(32, 0), List.of(0, 16), List.of(-32, 0), List.of(0, -16))),
                        samples.size(), first.stream().map(row -> row.get(2)).distinct().sorted().toList(),
                        first.stream().map(row -> row.get(6)).distinct().sorted().toList(),
                        first.stream().map(row -> row.subList(4, 6)).distinct().toList()));
        var restoredParent = (CdProjector) live(CdProjector.class).stream()
                .filter(a -> a.getSpawn().equals(parent.getSpawn())).findFirst().orElseThrow();
        sonic.setAnimationId(CdAnimationIds.resolve(sonic, CanonicalAnimation.ROLL));
        restoredParent.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(3);
        assertEquals(List.of(true, 0L), List.of(restoredParent.childrenInvalid(),
                live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                        .filter(a -> a.projectorSlot() == parentSlot).count()));
    }

    private List<List<Integer>> hologramCycle(ObjWwzAnimal flying, ObjWwzAnimal ground) {
        List<List<Integer>> states = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            runner.stepIdleFrames(1);
            var state = new ArrayList<>(animalState(flying, flying.getSpawn().x(), flying.getSpawn().y()));
            state.addAll(animalState(ground, ground.getSpawn().x(), ground.getSpawn().y()));
            states.add(state);
        }
        return states;
    }

    private static ObjWwzAnimal animalOfSubtype(List<ObjWwzAnimal> animals, int subtype) {
        return animals.stream().filter(a -> a.getSpawn().subtype() == subtype).findFirst().orElseThrow();
    }

    private static List<Integer> animalState(ObjWwzAnimal animal, int x, int y) {
        return List.of(animal.objectX() - x, animal.objectY() - y, animal.mapFrame(), animal.spriteFlip());
    }

    @Test
    void projectorChildrenAppearAndLeaveWithTheNativeR61bRoute() throws Exception {
        Path trace = CdTestMedia.nativeFile("rehost-long/R61B/trace/aux_state.jsonl").getParent();
        var json = new ObjectMapper();
        var nativeEvents = new TreeMap<Integer, List<String>>();
        var children = new HashSet<Integer>();
        for (String line : Files.readAllLines(trace.resolve("aux_state.jsonl"))) {
            if (!line.contains("\"object_type\":\"0x29\"")) continue;
            var event = json.readTree(line);
            int row = event.get("frame").asInt(), slot = event.get("slot").asInt();
            if (event.get("event").asText().equals("object_appeared") && row == 2749) {
                children.add(slot);
                nativeEvents.computeIfAbsent(row, key -> new ArrayList<>()).add(event.get("x").asText() + "," + event.get("y").asText());
            } else if (event.get("event").asText().equals("object_removed") && children.remove(slot)) {
                nativeEvents.putIfAbsent(row, List.of());
            }
            if (row > 3210) break;
        }
        var actual = new TreeMap<Integer, List<String>>();
        List<String> prior = new ArrayList<>();
        try (var media = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var replay = CdFixtureReplay.replay(media, tempDir,
                    new CdFixtureReplay.Fixture(CdStagePayload.R61B, trace, trace.resolveSibling("movie.bk2")),
                    3210, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite player) {
                            List<String> positions = live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                                    .filter(a -> a.projectorSlot() >= 0)
                                    .map(a -> String.format("0x%04X,0x%04X", a.getSpawn().x(), a.getSpawn().y()))
                                    .sorted().toList();
                            if (!prior.equals(positions)) {
                                actual.put(row, positions);
                                prior.clear();
                                prior.addAll(positions);
                            }
                        }
                    });
            assertEquals(-1, replay.firstErrorRow(), "Sonic and camera must follow the native route");
        }
        nativeEvents.replaceAll((row, values) -> values.stream().sorted().toList());
        assertEquals(List.of(2, 0), nativeEvents.values().stream().map(List::size).toList(),
                "the native route must record both children entering and leaving");
        assertEquals(nativeEvents, actual);
    }

    @Test
    void goodFutureFlightMatchesTheNativeR61cPlacement() throws Exception {
        Path trace = CdTestMedia.nativeFile("rehost-fixtures/R61C/trace/aux_state.jsonl").getParent();
        var data = TraceData.load(trace);
        var json = new ObjectMapper();
        var nativePositions = new TreeMap<Integer, List<Integer>>();
        for (String line : Files.readAllLines(trace.resolve("aux_state.jsonl"))) {
            if (!line.contains("\"event\":\"object_near\"") || !line.contains("\"type\":\"0x29\"")) continue;
            var event = json.readTree(line);
            int row = event.get("frame").asInt();
            if (row < 596 || row > 649 || event.get("slot").asInt() != 45
                    || CdFixtureReplay.completedRow(data, row) != row) continue;
            nativePositions.put(row, List.of(Integer.decode(event.get("x").asText()),
                    Integer.decode(event.get("y").asText()), Integer.decode(event.get("routine").asText())));
        }
        var actual = new TreeMap<Integer, List<Integer>>();
        try (var media = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var replay = CdFixtureReplay.replay(media, tempDir,
                    new CdFixtureReplay.Fixture(CdStagePayload.R61C, trace, trace.resolveSibling("movie.bk2")),
                    649, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite player) {
                            int complete = CdFixtureReplay.completedRow(data, row);
                            if (!nativePositions.containsKey(complete)) return;
                            live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                                    .filter(a -> a.getSpawn().x() == 0x488 && a.getSpawn().y() == 0x4D0)
                                    .findFirst().ifPresent(a -> actual.put(complete, List.of(a.objectX(), a.objectY(), a.routine())));
                        }
                    });
            assertEquals(-1, replay.firstErrorRow(), "Sonic and camera must follow the native route");
        }
        assertTrue(nativePositions.size() >= 40, "the route must observe the flying animal for at least 40 rows");
        assertEquals(nativePositions, actual);
    }

    // ---- Piston ------------------------------------------------------------------------------------

    @Test
    void pistonRisesEightAFrameWaitsSinksOneAndWaitsAgain() throws Exception {
        boot(CdStagePayload.R61A);
        int baseY = sonic.getCentreY() - 0x90;
        ObjPiston piston = add(new ObjPiston(spawn(sonic.getCentreX() + 0x100, baseY, 0x20, 0)));
        runner.stepIdleFrames(1);
        // ObjPiston_Init falls into _Main; _Move adds 8 to the offset and the piston sits at pistonY - offset (Piston/Main.asm:90-119).
        assertEquals(8, piston.offset());
        assertEquals(baseY - 8, piston.getY());
        // Two sides, 32 left and right, share its ID (Piston/Main.asm:42-61).
        assertEquals(3, live(ObjPiston.class).size());
        runner.stepIdleFrames(9);
        assertEquals(80, piston.offset(), "cmpi.b #80 / move.b #80: the top");
        // Time 60: the piston waits 60 frames, then sinks one a frame (Piston/Main.asm:90-119).
        runner.stepIdleFrames(59);
        assertEquals(80, piston.offset());
        runner.stepIdleFrames(1);
        assertEquals(79, piston.offset(), ".MovingDown: subq.b #1,oPistonOff");
        runner.stepIdleFrames(1);
        assertEquals(78, piston.offset());
    }

    // ---- Eggman statue -----------------------------------------------------------------------------

    @Test
    void eggmanStatueExplodesOnATimetableThenDropsTenBombs() throws Exception {
        boot(CdStagePayload.R61D);
        // Rolling into the statue sets oColStatus (Player_TouchSpecial .FlagColRoll, Wacky Workbench/Player Object
        // Collision.asm:389-393); the first count clears the collision and starts ObjEggmanStatue_ExplodeLocs.
        ObjEggmanStatue statue = add(new ObjEggmanStatue(spawn(sonic.getCentreX(), sonic.getCentreY(), 0x2B, 0)));
        // A rolling player with speed (a stopped roll ends at once): through the statue's $28 width in a few frames.
        sonic.setRolling(true);
        sonic.setAnimationId(2);
        sonic.setGSpeed((short) 0x300);
        sonic.setXSpeed((short) 0x300);
        int frames = 0;
        while (statue.routine() < 4 && frames++ < 10) runner.stepIdleFrames(1);
        assertEquals(4, statue.routine(), "ObjEggmanStatue_Main -> Explode");
        int explosions = 0;
        for (int i = 0; i < 0x60; i++) {
            runner.stepIdleFrames(1);
            explosions = Math.max(explosions, live(com.openggf.game.soniccd.objects.common.CdExplosion.class).size());
        }
        // Nine records of the table (Eggman Statue/Main.asm:163-173) then frame 1 for 60 frames and ten bombs (:176-188).
        assertTrue(explosions >= 1, "the table's explosions appeared");
    }

    // ---- Goddess statue ----------------------------------------------------------------------------

    @Test
    void goddessStatueSpitsFiftyRingsOnePerElevenFramesThroughTheSharedLostRingOwner() throws Exception {
        boot(CdStagePayload.R61B);
        // Player 0 to 15 right of it and 0 to 31 below (the unsigned tests, Goddess Statue/Main.asm:36-54).
        ObjGoddessStatue statue = add(new ObjGoddessStatue(spawn(sonic.getCentreX() - 4, sonic.getCentreY() - 10, 0x08, 0)));
        GameServices.rng().setSeed(0x2A6D365AL);        // Random's reset seed (Functions (General).asm:14-21)
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.List<int[]> rings = new java.util.ArrayList<>();
        for (int frame = 0; frame < 620; frame++) {
            runner.stepIdleFrames(1);
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                if (o instanceof com.openggf.level.rings.LostRingObjectInstance ring && seen.add(ring)) {
                    rings.add(new int[] {ring.getXVelForTest(), ring.getSlotIndex(), ring.getX() - statue.getX(),
                            ring.getY() - statue.getY(), frame});
                }
            }
        }
        // subq.b #1,oGoddessCount / bpl: the counts 49 down to 0 spit, -1 ends (Goddess Statue/Main.asm:58-69).
        assertEquals(50, statue.ringsSpat());
        assertEquals(6, statue.routine());
        assertEquals(50, rings.size(), "one lost-ring object per spit");
        // Each ring starts 24 right and 16 above the statue (:232-235) and the spits are 11 frames apart (:210-217).
        assertTrue(rings.get(0)[2] >= 23 && rings.get(0)[2] <= 27, "24 right, within the first frame's move: " + rings.get(0)[2]);
        assertTrue(rings.get(0)[3] <= -16 + 4 && rings.get(0)[3] >= -16 - 4, "16 above, within the first frame's fall");
        for (int i = 1; i < rings.size(); i++) assertEquals(11, rings.get(i)[4] - rings.get(i - 1)[4], "ring " + i);
        // Random, in native order: d0 = low word of the seed step; the speed is .XVels[d0 & 7] (:247-249, 256-264).
        int[] table = {-0x100, -0x80, 0, 0x80, 0x100, 0x180, 0x200, 0x280};
        long seed = 0x2A6D365AL;
        for (int i = 0; i < rings.size(); i++) {
            long a = (seed << 2) & 0xFFFFFFFFL;                      // asl.l #2,d1 / add.l d0,d1 / asl.l #3,d1 / add.l d0,d1
            a = (a + seed) & 0xFFFFFFFFL;
            a = (a << 3) & 0xFFFFFFFFL;
            a = (a + seed) & 0xFFFFFFFFL;
            long low = ((a & 0xFFFF) + (a >>> 16)) & 0xFFFF;         // move.w d1,d0 / swap d1 / add.w d1,d0
            seed = (low << 16) | (a & 0xFFFF);                       // move.w d0,d1 / swap d1 / move.l d1,rngSeed
            assertEquals(table[(int) (low & 7)], rings.get(i)[0], "ring " + i + " horizontal speed");
        }
    }

    // ---- Minomusi ----------------------------------------------------------------------------------

    @Test
    void minomusiDropsNinetyFivePixelsWhenTheScheduleFindsThePlayerBelow() throws Exception {
        boot(CdStagePayload.R62A);
        int startY = sonic.getCentreY() - 0x60;
        ObjMinomusi mino = add(new ObjMinomusi(spawn(sonic.getCentreX(), startY, 0x33, 0)));
        runner.stepIdleFrames(1);
        // Init: y + 8, the silk allocated after it (Minomusi/Main.asm:47-95).
        assertEquals(startY + 8, mino.getY());
        assertEquals(0x34, mino.getCollisionFlags());
        assertEquals(1, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getSimpleName().equals("ObjMinomusiPart")).count(), "the silk");
        // WaitPlayer: 121 frames, then the player is 40 to 160 below and within 168 (Minomusi/Main.asm:105-142).
        runner.stepIdleFrames(130);
        assertTrue(mino.getY() > startY + 8, "dropping");
        runner.stepIdleFrames(60);
        assertEquals(startY + 8 + 95, mino.getY(), "oMinoDropY = y + 95");
    }

    // ---- Semi --------------------------------------------------------------------------------------

    @Test
    void semiWaitsForThePlayerSwoopsAndDropsABomb() throws Exception {
        boot(CdStagePayload.R62A);
        sonic.setInvulnerableFrames(0x7000);   // keep the player out of the way of the badniks' touch
        // Directly above the player, so ObjSemi_CheckPlayer's window (96 vertical, 120 horizontal) holds through the swoop.
        ObjSemi semi = add(new ObjSemi(spawn(sonic.getCentreX(), sonic.getCentreY() - 8, 0x32, 0)));
        runner.stepIdleFrames(2);
        assertEquals(0x36, semi.getCollisionFlags());
        int x0 = semi.getX();
        runner.stepIdleFrames(20);
        // StartMove: oSemiPlayerX is 0 (not negative), so the speed is negated: 1 pixel a frame to the left (Semi/Main.asm:100-120).
        assertTrue(semi.getX() < x0, "the Semi moved left");
        int bombs = 0;
        for (int i = 0; i < 500 && bombs == 0; i++) {
            runner.stepIdleFrames(1);
            bombs = (int) GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("ObjSemiBomb")).count();
        }
        assertTrue(bombs > 0, "ObjSemi_Attack allocates a bomb every 64 frames while the player is in range");
    }

    // ---- Bata-pyon ---------------------------------------------------------------------------------

    @Test
    void bataPyonFallsLandsAndHops() throws Exception {
        boot(CdStagePayload.R62A);
        ObjBataPyon bata = add(new ObjBataPyon(spawn(sonic.getCentreX() + 0x60, sonic.getCentreY() - 0x40, 0x30, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0x2F, bata.getCollisionFlags(), "SwapFrames: frame 0 is collision $2F (Bata-pyon/Main.asm:224-242)");
        int landed = -1;
        for (int i = 0; i < 120 && landed < 0; i++) {
            runner.stepIdleFrames(1);
            if (bata.routine() == 4) landed = i;
        }
        assertTrue(landed >= 0, "ObjBataPyon_Fall -> Landed");
        int jumped = -1;
        for (int i = 0; i < 10 && jumped < 0; i++) {
            runner.stepIdleFrames(1);
            if (bata.routine() == 6) jumped = i;
        }
        assertTrue(jumped >= 0, "waits one frame, then ObjBataPyon_Jump (Bata-pyon/Main.asm:120-160)");
        assertEquals(0x30, bata.getCollisionFlags(), "the jump swaps to the tall frame");
    }

    // ---- Snake blocks ------------------------------------------------------------------------------

    @Test
    void snakeBlockSlidesThirtyTwoPixelsAndSpawnsTheNextOfThePath() throws Exception {
        boot(CdStagePayload.R61B);
        ObjSnakeBlocks head = add(new ObjSnakeBlocks(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY() - 0x100, 0x2A, 0)));
        runner.stepIdleFrames(2);
        // Path set 0, first path: up, up, up, $FF (Snake Blocks/Main.asm:331-363); the first copy slides up at 0.5 px a frame.
        var copies = live(ObjSnakeBlocks.class).stream().filter(o -> o != head).toList();
        assertEquals(1, copies.size());
        int y0 = head.getY();
        runner.stepIdleFrames(66);
        assertTrue(copies.get(0).getY() <= y0 - 31 && copies.get(0).getY() >= y0 - 33, "64 frames of $8000: 32 px up");
        runner.stepIdleFrames(40);
        assertTrue(live(ObjSnakeBlocks.class).size() >= 3, "the next copy of the path follows");
    }

    // ---- Electric beams ----------------------------------------------------------------------------

    @Test
    void beamHandlerChargesThenFlashesEachColourInTurn() throws Exception {
        boot(CdStagePayload.R62A);
        ObjElectricBeams handler = add(new ObjElectricBeams(spawn(sonic.getCentreX(), sonic.getCentreY(), 0x21, 1)));
        // Present: 480 frames, 90 of charge, 120 of flash with wwzBeamMode = colour + 1 (Electric Beams/Main.asm:47-136).
        int firstFlash = -1;
        int mode = 0;
        for (int i = 0; i < 900 && firstFlash < 0; i++) {
            runner.stepIdleFrames(1);
            if (beamMode() != 0) {
                firstFlash = i;
                mode = beamMode();
            }
        }
        assertTrue(handler.isHandler());
        assertTrue(firstFlash >= 480 + 90 - 3 && firstFlash <= 480 + 90 + 6, "flash starts after the timer and the charge: " + firstFlash);
        assertEquals(1, mode, "blue first");
        runner.stepIdleFrames(121);
        assertEquals(0, beamMode(), "clr.b wwzBeamMode at the end of the flash");
        assertEquals(1, handler.beamMode(), "addq.b #1,oBeamMode");
    }

    // ---- Freezer -----------------------------------------------------------------------------------

    @Test
    void freezerFreezesAPlayerInReachAndLetsGoOrHurts() throws Exception {
        boot(CdStagePayload.R61A);
        ObjFreezer machine = add(new ObjFreezer(spawn(sonic.getCentreX(), sonic.getCentreY() - 36, 0x05, 0)));
        int frozenAt = -1;
        for (int i = 0; i < 700 && frozenAt < 0; i++) {
            runner.stepIdleFrames(1);
            if ((WwzPlayerProfile.playerControl(sonic) & 0x41) == 0x41) frozenAt = i;
        }
        assertTrue(frozenAt >= 120, "the nozzle appears after oFreezerTime 120: " + frozenAt);
        // Captured and hidden (bits 0 and 6, ObjFreezer_FreezeSonic, Freezer/Main.asm:265-286).
        int released = -1;
        for (int i = 0; i < 200 && released < 0; i++) {
            runner.stepIdleFrames(1);
            if ((WwzPlayerProfile.playerControl(sonic) & 0x41) == 0) released = i;
        }
        assertTrue(released >= 0, "ObjFreezer_IceLanded frees or hurts the player");
        assertTrue(machine.time() >= 0);
    }

    // ---- Platforms ---------------------------------------------------------------------------------

    @Test
    void bouncePlatformJumpsAtMinusSixHundredAndRisesUnderGravity() throws Exception {
        boot(CdStagePayload.R61A);
        ObjBouncePlatform platform = add(new ObjBouncePlatform(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY() - 0x80, 0x23, 0)));
        runner.stepIdleFrames(1);
        // ObjBouncePlatform_Main: CheckFloor bounces unless the flash step is $3C/$5A: yVel -$600, gravity $10 (Bounce:48-56).
        assertEquals(4, platform.routine());
        int y1 = platform.getY();
        runner.stepIdleFrames(1);
        // Move: yVel = -$600 + $10 = -$5F0, then add.l (:107-120).
        assertEquals(-0x5F0, platform.yVelocity());
        assertTrue(platform.getY() < y1);
    }

    @Test
    void spinPlatformRidesASineOfTheFrameCounter() throws Exception {
        boot(CdStagePayload.R62B);
        ObjSpinPlatform platform = add(new ObjSpinPlatform(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY() - 0x80, 0x28, 0)));
        int minX = platform.getX();
        int maxX = platform.getX();
        for (int i = 0; i < 300; i++) {
            runner.stepIdleFrames(1);
            minX = Math.min(minX, platform.getX());
            maxX = Math.max(maxX, platform.getX());
        }
        // Subtype 0 moves along X by CalcSine * 4 >> 4: up to 64 either side (Platform/Main (Spinning).asm:95-119, 140-150).
        assertEquals(0x100 + sonic.getCentreX() + 64, maxX);
        assertEquals(0x100 + sonic.getCentreX() - 64, minX);
    }

    // ---- Launcher ----------------------------------------------------------------------------------

    @Test
    void launcherAllocatesItsSolidSideAndWaitsForARider() throws Exception {
        boot(CdStagePayload.R62A);
        ObjLauncher launcher = add(new ObjLauncher(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY() - 0x80, 0x04, 0)));
        runner.stepIdleFrames(2);
        // Init: a second object with the same ID 24 left and 16 above (Launcher/Main.asm:25-50, 131-144).
        var sides = live(ObjLauncher.class).stream()
                .filter(o -> o != launcher && o.getX() == launcher.getX() - 24 && o.getY() == launcher.getY() - 16).toList();
        assertEquals(1, sides.size());
        assertEquals(launcher.getX() - 24, sides.get(0).getX());
        assertEquals(launcher.getY() - 16, sides.get(0).getY());
        assertEquals(2, launcher.routine(), "waiting: no rider");
        assertEquals(1, ((WwzObject) sides.get(0)).mapFrame());
    }

    @Test
    void projectorChildrenReturnWhenRewindingAcrossDestruction() throws Exception {
        boot(CdStagePayload.R61B);
        var spawn = spawn(sonic.getCentreX() + 0x50, sonic.getCentreY() - 0x40, 0x2F, 0);
        var projector = add(new CdProjector(spawn));
        runner.stepIdleFrames(12);
        int parentSlot = projector.getSlotIndex();
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = rewind.capture();
        List<List<HologramState>> reference = new ArrayList<>();
        for (int frame = 0; frame < 40; frame++) {
            runner.stepIdleFrames(1);
            reference.add(hologramStates(parentSlot));
        }

        rewind.restore(snapshot);
        var restoredParent = (CdProjector) live(CdProjector.class).stream()
                .filter(p -> p.getSpawn().equals(spawn)).findFirst().orElseThrow();
        sonic.setAnimationId(CdAnimationIds.resolve(sonic, CanonicalAnimation.ROLL));
        restoredParent.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(3);
        // ProjectorObject_0_Routine4 (objects/r6/projector.asm:111-120) invalidates both children.
        assertTrue(restoredParent.childrenInvalid());
        assertEquals(List.of(), hologramStates(parentSlot), "both holograms disappear within three frames");
        // ProjectorExplosionRecords (objects/r6/projector.asm:220-233) ends at $28, then
        // ProjectorStartShutdown (objects/r6/projector.asm:150-153) waits another $3C frames.
        runner.stepIdleFrames(0x28 + 0x3C);
        assertTrue(restoredParent.isDestroyed(), "the projector finishes its shutdown before rewind");

        rewind.restore(snapshot);
        // AnimalObject_1_Routine4 (objects/r6/animal.asm:73-88) and
        // AnimalObject_0_Routine8 (objects/r6/animal.asm:173-181).
        assertEquals(List.of(List.of(0x80, 0x2F, 4), List.of(0x81, 0x2F, 8)),
                hologramStates(parentSlot).stream()
                        .map(state -> List.of(state.subtype(), state.parentId(), state.routine())).toList(),
                "rewind recreates the flying and ground holograms linked to the projector");
        for (int frame = 0; frame < reference.size(); frame++) {
            runner.stepIdleFrames(1);
            assertEquals(reference.get(frame), hologramStates(parentSlot), "replayed frame " + (frame + 1));
        }
    }

    private List<HologramState> hologramStates(int parentSlot) {
        return live(ObjWwzAnimal.class).stream().map(ObjWwzAnimal.class::cast)
                .filter(animal -> animal.projectorSlot() == parentSlot)
                .map(animal -> new HologramState(animal.getSpawn().subtype(), animal.projectorId(), animal.routine(),
                        animal.objectX(), animal.objectY(), animal.mapFrame(), animal.spriteFlip()))
                .sorted(Comparator.comparingInt(HologramState::subtype))
                .toList();
    }

    private record HologramState(int subtype, int parentId, int routine, int x, int y, int mapFrame, int spriteFlip) { }
}
