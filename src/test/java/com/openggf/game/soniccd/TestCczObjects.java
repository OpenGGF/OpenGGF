package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.ccz.CczAmyRose;
import com.openggf.game.soniccd.objects.ccz.CczAnimal;
import com.openggf.game.soniccd.objects.ccz.CczBumper;
import com.openggf.game.soniccd.objects.ccz.CczBumperPlatform;
import com.openggf.game.soniccd.objects.ccz.CczDoor;
import com.openggf.game.soniccd.objects.ccz.CczDrain;
import com.openggf.game.soniccd.objects.ccz.CczDrumPlatform;
import com.openggf.game.soniccd.objects.ccz.CczFireShooter;
import com.openggf.game.soniccd.objects.ccz.CczFlipper;
import com.openggf.game.soniccd.objects.ccz.CczFlipperLaunch;
import com.openggf.game.soniccd.objects.ccz.CczFlipperObject;
import com.openggf.game.soniccd.objects.ccz.CczGa;
import com.openggf.game.soniccd.objects.ccz.CczGlassShatter;
import com.openggf.game.soniccd.objects.ccz.CczIntroSpikes;
import com.openggf.game.soniccd.objects.ccz.CczMetalPlatform;
import com.openggf.game.soniccd.objects.ccz.CczMetalSonic;
import com.openggf.game.soniccd.objects.ccz.CczObjectArt;
import com.openggf.game.soniccd.objects.ccz.CczObjectCatalogue;
import com.openggf.game.soniccd.objects.ccz.CczPocket;
import com.openggf.game.soniccd.objects.ccz.CczSpikeChain;
import com.openggf.game.soniccd.objects.ccz.CczTentou;
import com.openggf.game.soniccd.objects.common.CdAnimal;
import com.openggf.game.soniccd.objects.projector.CdProjector;
import com.openggf.game.soniccd.objects.projector.ProjectorChild;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.PlcParser;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused behaviour of the Collision Chaos objects on loaded stages (their art and tables come from the disc). Each
 * case checks values taken from the routine it cites (Level/Collision Chaos/Objects/*, objects/r3/*); the native
 * comparisons are {@link TestCczObjectsAppearances} and the frontier table. Sonic stands at the stage start and is
 * idle, so objects are placed relative to him.
 */
class TestCczObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    @BeforeEach
    void boot() throws Exception {
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
        load(CdStagePayload.R32A);
    }

    private void load(CdStagePayload payload) throws Exception {
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
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

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    private ObjectSpawn spawn(int id, int dx, int dy, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, id, subtype, 0, false, 0);
    }

    @Test
    void spikeChainTurnsOnceIn256UpdatesAndRestoresItsOrbit() throws Exception {
        for (CdStagePayload payload : List.of(CdStagePayload.R32B, CdStagePayload.R32A, CdStagePayload.R32C,
                CdStagePayload.R32D)) {
            load(payload);
            int count = switch (payload) {
                case R32B -> 2;
                case R32A -> 3;
                default -> 4;
            };
            int subtype = payload == CdStagePayload.R32A ? 0 : 1;
            CczSpikeChain pivot = addSpikeChain(spawn(0x2C, 112, -32, subtype));
            runner.stepIdleFrames(1);
            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var snapshot = rewind.capture();
            List<List<OrbitPosition>> orbit = new ArrayList<>();
            for (int update = 1; update <= 256; update++) {
                List<OrbitPosition> balls = spikeBallPositions(pivot);
                orbit.add(balls);
                assertEquals(count, balls.size(), payload + " update " + update);
                for (int i = 0; i < count; i++) {
                    int radius = 16 * (i + 1);
                    double angle = 2 * Math.PI * update / 256 * (subtype == 0 ? 1 : -1);
                    var position = balls.get(i);
                    // The native sine table stores integer values in units of 1/256.
                    assertEquals(radius * Math.cos(angle), position.x(), 1.5);
                    assertEquals(radius * Math.sin(angle), position.y(), 1.5);
                }
                if (update < 256) runner.stepIdleFrames(1);
            }
            assertEquals(new OrbitPosition(count * 16, 0), orbit.getLast().getLast());
            pivot.setDestroyed(true);
            runner.stepIdleFrames(1);
            assertTrue(live(0x2C).isEmpty(), "balls retire after the pivot slot empties");

            rewind.restore(snapshot);
            CczSpikeChain restored = live(0x2C).stream().filter(CczSpikeChain.class::isInstance)
                    .map(CczSpikeChain.class::cast).findFirst().orElseThrow();
            for (int update = 0; update < orbit.size(); update++) {
                assertEquals(orbit.get(update), spikeBallPositions(restored), payload + " restored update " + update);
                if (update + 1 < orbit.size()) runner.stepIdleFrames(1);
            }

        }
    }

    @Test
    void spikeChainBallHurtsThroughThePlayerTouchPass() {
        CczSpikeChain pivot = addSpikeChain(spawn(0x2C, 112, -32, 0));
        runner.stepIdleFrames(1);
        var ball = spikeBalls().getLast();
        GameServices.level().getLevelGamestate().setRings(5);
        sonic.setCentreX((short) ball.getX());
        sonic.setCentreY((short) ball.getY());
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0);
        runner.stepIdleFrames(1);
        assertTrue(sonic.isHurt(), "overlapping the outer ball enters Sonic's hurt routine");
        assertEquals(0, GameServices.level().getLevelGamestate().getRings());
        assertFalse(pivot.isDestroyed());
    }

    @Test
    void spikeChainPictureUsesTheDiscCueAndTwoDistinctMappings() throws Exception {
        var art = (CdObjectArtProvider) GameServices.module().getObjectArtProvider();
        var payload = art.payload();
        var image = art.image();
        int plcLists = CdStageCatalogue.entry(payload).plcLists();
        // Art_SpikeChain is the Nemesis upload at VRAM byte address $77A0.
        // PLC_Section0 (Level/Collision Chaos/Graphics Lists (Act 2 Variant).asm:158-182).
        var artEntry = PlcParser.parse(image.reader(), plcLists, 2).entries().stream()
                .filter(entry -> entry.tileIndex() == 0x3BD).findFirst().orElseThrow();
        byte[] nativeTiles = NemesisReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(image.tail(image.offsetOf(artEntry.romAddr())))));
        assertTrue(nativeTiles.length >= 8 * Pattern.PATTERN_SIZE_IN_ROM);

        for (boolean ball : List.of(false, true)) {
            String kind = ball ? "spikechainball" : "spikechainpivot";
            var sheet = art.getSheet(CczObjectArt.key(art, kind, 0x3BD, GameServices.camera().getX()));
            assertNotNull(sheet, kind);
            int firstTile = ball ? 4 : 0;
            // MapSpr_SpikeChain1 and MapSpr_SpikeChain2 (Level/Collision Chaos/Objects/Spike Chain/Main.asm:187-232).
            assertEquals(List.of(new SpriteMappingPiece(-8, -8, 2, 2, firstTile, false, false, 0)),
                    sheet.getFrame(0).pieces());
            for (int tile = firstTile; tile < firstTile + 4; tile++) {
                byte[] pixels = new byte[64];
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        pixels[y * 8 + x] = sheet.getPatterns()[tile].getPixel(x, y);
                    }
                }
                byte[] expected = new byte[64];
                for (int i = 0; i < 32; i++) {
                    int packed = nativeTiles[tile * 32 + i] & 0xFF;
                    expected[2 * i] = (byte) (packed >> 4);
                    expected[2 * i + 1] = (byte) (packed & 0xF);
                }
                assertArrayEquals(expected, pixels, kind + " tile " + tile);
            }
        }
    }

    private CczSpikeChain addSpikeChain(ObjectSpawn spawn) {
        var registry = SessionManager.requireCurrentGameModule().createObjectRegistry();
        return add(assertInstanceOf(CczSpikeChain.class, registry.create(spawn)));
    }

    private record OrbitPosition(int x, int y) { }

    private List<CczSpikeChain.Ball> spikeBalls() {
        return live(0x2C).stream().filter(CczSpikeChain.Ball.class::isInstance)
                .map(CczSpikeChain.Ball.class::cast)
                .sorted(Comparator.comparingInt(CczSpikeChain.Ball::getSlotIndex)).toList();
    }

    private List<OrbitPosition> spikeBallPositions(CczSpikeChain pivot) {
        return spikeBalls().stream().map(ball -> new OrbitPosition(ball.getX() - pivot.getX(),
                (short) (ball.getY() - pivot.getY()))).toList();
    }

    // ---- catalogue ----------------------------------------------------------------------------------------------

    @Test
    void catalogueFindsTheTablesOfTheStagesThatAssembleTheObjects() {
        // Every Collision Chaos stage assembles the platforms, badniks and effects ($23-$2F share one block in all object
        // indexes: r3/object_index_1b_1c_1d.asm, object_index_2.asm, object_index_3.asm; Object Index (Act 1 Present).asm:45-55).
        for (CdStagePayload p : CdStagePayload.values()) {
            if (!p.name().startsWith("R3")) continue;
            for (String column : List.of("metalPlatformMappings", "drumPlatformMappings", "fireShootMappings",
                    "bumperPlatformMappings", "glassBreakMappings", "gaMappings1", "gaMappings2", "pocketMappings1",
                    "pocketMappings2", "tentouMappings1", "tentouMappings2", "tentouBombMappings", "kamaKamaMappings1",
                    "kamaKamaMappings2", "kamaSickleMappings", "gaAnimations", "gaDustAnimations", "kamaKamaAnimations",
                    "kamaSickleAnimations", "tentouAnimations", "tentouBombAnimations", "pocketAnimations",
                    "glassBreakAnimations", "fireShootAnimations")) {
                assertTrue(CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
            // Only Act 1 Present links the intro objects (Object Index (Act 1 Present).asm:60-64) and only Act 3 the drain.
            boolean intro = p == CdStagePayload.R31A;
            boolean drain = p == CdStagePayload.R33C || p == CdStagePayload.R33D;
            for (String column : List.of("introSpikesMappings", "metalSonicMappings", "metalSonicAnimations",
                    "metalSonicExhaustAnimations")) {
                assertEquals(intro, CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
            for (String column : List.of("drainBlockMappings", "drainLightMappings", "drainSwitchMappings",
                    "drainBlockAnimations", "drainSwitchAnimations")) {
                assertEquals(drain, CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
        }
    }

    // ---- platforms ----------------------------------------------------------------------------------------------

    @Test
    void metalPlatformFollowsItsHorizontalMotionRecords() {
        // Subtype 1 starts with HorizontalStartRight: $1C frames at $300, then 8 frames at $300 losing $30 (Metal Platform/Main.asm:206-232).
        CczMetalPlatform platform = add(new CczMetalPlatform(spawn(0x23, 0x40, -0x80, 1)));
        int base = platform.xPosition();
        runner.stepIdleFrames(1);                          // routine 0 runs Init and loads record 0 (the legacy ABI does not move)
        assertEquals(base, platform.xPosition());
        assertEquals(0x1C, platform.timer());
        runner.stepIdleFrames(0x1C);                       // 28 moves of 3 pixels; the last loads record 1 in the same frame
        assertEquals(base + 3 * 0x1C, platform.xPosition());
        assertEquals(1, platform.motionStep());
        assertEquals(8, platform.timer());
        assertEquals(0x300, platform.velocity());
        runner.stepIdleFrames(8);                          // $300+$2D0+...+$1B0 = $12C0 of 8.8 over 8 frames
        assertEquals(base + 84 + 0x12C0 / 256, platform.xPosition());
        assertEquals((0x12C0 % 256) << 8, platform.xSubpixel());
        assertEquals(2, platform.motionStep());
        assertEquals(0, platform.velocity(), "record 2 is {8, -$30, 0}");
    }

    @Test
    void metalPlatformFollowsItsVerticalMotionRecords() {
        // Subtype 3: VerticalStartUp, $23 frames at -$200 (two pixels up a frame), solid called before each lookup (:294-319).
        CczMetalPlatform platform = add(new CczMetalPlatform(spawn(0x23, 0x40, -0x80, 3)));
        int base = platform.yPosition();
        runner.stepIdleFrames(1 + 0x23);
        assertEquals(base - 2 * 0x23, platform.yPosition());
        assertEquals(1, platform.motionStep());
        assertEquals(8, platform.timer());
        assertEquals(-0x200, platform.velocity());
    }

    @Test
    void drumPlatformStartsItsSolidCycleAtTheSubtypeStep() {
        // Subtype 2 enters routine 4 (Drum Platform/Main.asm:100-126). Init ends with rts; the next frame loads step 2
        // ({6, $C, 2}); each of the next 4 frames moves 6 pixels; step 3 then loads ({8, $B, 3}) in the same frame (:130-181, 238-262).
        CczDrumPlatform drum = add(new CczDrumPlatform(spawn(0x28, 0x40, -0x80, 2)));
        int base = drum.yPosition();
        runner.stepIdleFrames(2);
        assertEquals(2, drum.step());
        assertEquals(4, drum.stepTimer());
        assertEquals(2, drum.mapFrame());
        runner.stepIdleFrames(4);
        assertEquals(base + 4 * 6, drum.yPosition());
        assertEquals(3, drum.step());
        assertEquals(3, drum.mapFrame());
        assertEquals(4, drum.stepTimer());
    }

    @Test
    void bumperPlatformSlidesAndReversesAfterItsFirstLeg() {
        // Subtype $40 (bit 7 clear: Y, range $A0, first leg $50; bit 6 set: positive) moves one pixel a frame (Bumper Platform/Main.asm:33-57, 59-132).
        CczBumperPlatform platform = add(new CczBumperPlatform(spawn(0x2A, 0x60, -0x80, 0x40)));
        int base = platform.yPosition();
        runner.stepIdleFrames(0x50);
        assertEquals(base + 0x50, platform.yPosition());
        assertEquals(0xA0, platform.steps(), "the next leg is the range");
        assertEquals(-0x10000, platform.velocity());
    }

    // ---- badniks ------------------------------------------------------------------------------------------------

    @Test
    void gaWaitsHoversAndFliesWithItsCollisionTypes() {
        // Timer $3D twice, collision $25 after $3D more frames, then $33 frames until $26 and the flight (Ga/Main.asm:45-150).
        CczGa ga = add(new CczGa(spawn(0x2D, 0x80, -0x60, 0)));
        runner.stepIdleFrames(0x3D);
        assertEquals(4, ga.routine());
        assertEquals(0, ga.getCollisionFlags());
        runner.stepIdleFrames(0x3D);
        assertEquals(0x25, ga.getCollisionFlags());
        assertEquals(6, ga.routine());
        runner.stepIdleFrames(0x33);
        assertEquals(0x26, ga.getCollisionFlags());
        assertEquals(8, ga.routine());
        assertEquals(0x8000, ga.verticalVelocity());
        assertEquals(0x20, ga.timer());
        assertEquals(-0x8000, ga.horizontalVelocity(), "the player is to the left, so Ga flies left");
    }

    @Test
    void tentouBombExplodesWhenTouched() {
        // ObjTentou_Bomb_Explode turns the slot into explosion $18 (Tentou/Main.asm:423-435).
        CczTentou.Bomb bomb = add(new CczTentou.Bomb(spawn(0x2E, 0x40, -0x80, 1)));
        runner.stepIdleFrames(1);
        assertFalse(bomb.isDestroyed());
        bomb.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        assertTrue(bomb.isDestroyed());
        assertEquals(1, live(0x18).size());
    }

    @Test
    void glassShatterAwardsAHundredAndPlaysItsAnimationOnce() {
        // ObjGlassShatter_Init: AddPoints 10 (units of ten), SpawnPoints frame 0, then Ani_GlassBreak to its $FC (Glass Shatter/Main.asm:95-97).
        int score = GameServices.gameState().getScore();
        CczGlassShatter glass = add(new CczGlassShatter(spawn(0x29, 0x40, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertEquals(score + 100, GameServices.gameState().getScore());
        assertEquals(1, live(0x1C).stream().filter(o -> o.getSpawn().subtype() == 0x80).count(), "the 100-point sprite (subtype $80 | frame 0)");
        runner.stepIdleFrames(120);
        assertTrue(glass.isDestroyed(), "the animation's $FC advances to the delete routine");
    }

    // ---- pocket -------------------------------------------------------------------------------------------------

    @Test
    void pocketCapturesTheFallingPlayerAndAwardsPrizesThenReleasesHim() {
        // Capture window: $8-$18 above, $18 either side (Pocket/Main.asm:76-125); timed frames :127-244.
        int score = GameServices.gameState().getScore();
        int pockets = live(0x2F).size();                   // the stage's own machine is placed at level start
        CczPocket pocket = add(new CczPocket(spawn(0x2F, 0, 0x10, 0x80)));
        runner.stepIdleFrames(1);
        assertTrue(pocket.caught());
        assertTrue(sonic.isObjectControlled(), "bset #0,oPlayerCtrl(a6)");
        assertEquals(-0x800, sonic.getYSpeed());
        assertEquals(pocket.yPosition() - 0x10, sonic.getCentreY());
        assertEquals(1, pocket.mapFrame());
        runner.stepIdleFrames(9);                           // SpawnEffect: the 8 timer expires on the ninth decrement
        assertEquals(2, pocket.mapFrame());
        assertTrue(sonic.isHidden(), "bset #6,oPlayerCtrl(a6)");
        assertEquals(pockets + 2, live(0x2F).size(), "the machine and its glass front");
        runner.stepIdleFrames(115);                         // frames 11-125: open, award twice, close, release
        assertEquals(20, pocket.routine());
        assertEquals(5, pocket.prizes());
        assertEquals(score + 200, GameServices.gameState().getScore());
        assertFalse(sonic.isObjectControlled(), "bclr #0,var_2c");
        assertFalse(sonic.isHidden(), "bclr #6,var_2c");
        assertEquals(0, pocket.mapFrame());
    }

    // ---- fire shooter -------------------------------------------------------------------------------------------

    @Test
    void fireFlameCountsRisesAndThrowsTwoFireballs() {
        // Player within $78: timer $3C, then height 8 a frame to $20 (Fire Shooter/Main.asm:167-249).
        CczFireShooter.Flame flame = add(new CczFireShooter.Flame(spawn(0x27, 0x40, -0x80, 1)));
        int baseY = flame.yPosition();
        runner.stepIdleFrames(1);                           // Init
        assertEquals(2, flame.routine());
        runner.stepIdleFrames(0x3C);
        assertEquals(4, flame.routine());
        runner.stepIdleFrames(4);
        assertEquals(0x20, flame.height());
        assertEquals(baseY - 0x20, flame.yPosition());
        assertEquals(6, flame.routine());
        assertEquals(8, flame.timer());
        assertEquals(2, live(0x27).stream().filter(o -> o.getSpawn().subtype() == 2).count(), "two fireballs (present)");
    }

    // ---- drain (Act 3) ------------------------------------------------------------------------------------------

    @Test
    void drainSwitchSpawnsItsBlockAndLightsOneStepAtATime() throws Exception {
        load(CdStagePayload.R33C);
        CczDrain.Switch drainSwitch = add(new CczDrain.Switch(spawn(0x36, 0, 0, 0xFF)));
        runner.stepIdleFrames(1);
        assertEquals(2, live(0x36).size(), "the switch and its block at ($380,$4F8)");
        assertEquals(0x7F, drainSwitch.cooldown(), "subtype $FF starts at $80 with the player latched inside");
        sonic.setCentreX((short) (sonic.getCentreX() + 0x100));
        runner.stepIdleFrames(0x81);                        // unlatch and let the cooldown run out
        assertEquals(0, drainSwitch.cooldown());
        sonic.setCentreX((short) drainSwitch.xPosition());
        sonic.setCentreY((short) drainSwitch.yPosition());
        runner.stepIdleFrames(1);
        assertEquals(0, drainSwitch.stage(), "DrainSwitch_AdvanceStage: -2 to 0");
        assertEquals(3, live(0x36).size(), "the first indicator light, at X $380, Y $498");
        var light = live(0x36).stream().filter(o -> o.getSpawn().subtype() == 0).findFirst().orElseThrow();
        assertEquals(0x380, light.getX());
        assertEquals(0x498, light.getY());
        assertEquals(0x7F, drainSwitch.cooldown(), "set to $80, then DrainSwitch_Main counts it down in the same frame");
    }

    // ---- intro (Act 1 Present) ----------------------------------------------------------------------------------

    @Test
    void introSpikesBreakIntoThreeShardsWhenMetalSonicReachesThem() throws Exception {
        load(CdStagePayload.R31A);
        // IntroSpikesMain: Metal Sonic within width+$10 and height+$10 breaks it; frame 4 for subtype 0, shards 3, 2, 1 (objects/r3/intro.asm:627-715).
        CczIntroSpikes spikes = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        CczMetalSonic metal = add(new CczMetalSonic(spawn(0x31, 0x80, -0x40, 0)));
        runner.stepIdleFrames(2);
        assertEquals(4, spikes.routine());
        assertEquals(4, spikes.mapFrame());
        TreeSet<Integer> frames = new TreeSet<>();
        for (ObjectInstance o : live(0x30)) {
            if (o != spikes) frames.add(((CczIntroSpikes) o).mapFrame());
        }
        assertEquals(new TreeSet<>(List.of(1, 2, 3)), frames);
        assertNotNull(metal);
    }

    @Test
    void metalSonicWaitsThenFliesInAndArrives() throws Exception {
        load(CdStagePayload.R31A);
        // $3C frames of waiting, then -$10 acceleration per frame until X <= $3E0 without Amy; arrival spawns the exhaust
        // (objects/r3/intro.asm:46-99, 174-183). Starting at $3F0 it arrives after 22 moves.
        CczMetalSonic metal = add(new CczMetalSonic(new ObjectSpawn(0x3F0, sonic.getCentreY() - 0x40, 0x31, 0, 0, false, 0)));
        runner.stepIdleFrames(0x3C + 1);
        assertEquals(4, metal.routine());
        int frames = 0;
        while (!metal.arrived() && frames < 200) {
            runner.stepIdleFrames(1);
            frames++;
        }
        assertEquals(22, frames);
        assertEquals(0x3E0, metal.xPosition());
        assertEquals(6, metal.routine());
        assertEquals(1, live(0x34).size(), "the exhaust child");
    }

    // ---- pinball flipper launch (player hook) -----------------------------------------------------------------

    private record Flipper(int x, int y, int width, boolean flipped) implements CczFlipper {
        @Override public int flipperX() { return x; }
        @Override public int flipperY() { return y; }
        @Override public int flipperWidth() { return width; }
        @Override public boolean flipperFlippedHorizontally() { return flipped; }
        @Override public void flipperSwing() { }
    }

    @Test
    void flipperLaunchSpeedFollowsTheDistanceFromThePivot() {
        // ObjSonic_ChkFlipper (Level/_Objects/Sonic/Main.asm:3125-3152): force = playerX - flipperX + width, or 64 - force for a
        // flipper that is not flipped; speed = -$A00 + (-$A00 * force) / 64. Player 16 pixels right of the pivot, width $20:
        // flipped: force 48, speed -2560 - 1920; not flipped: force 64 - 48 = 16, speed -2560 - 640.
        var flipped = CczFlipperLaunch.compute(new Flipper(100, 100, 0x20, true), 116, 90);
        assertEquals(-2560 - 1920, flipped.speed());
        var plain = CczFlipperLaunch.compute(new Flipper(100, 100, 0x20, false), 116, 90);
        assertEquals(-2560 - 640, plain.speed());
        // The target point is left of and below the player: dx = -16, dy = 34: the second quadrant of the angle circle ($40-$80).
        assertTrue(plain.angle() > 0x40 && plain.angle() < 0x80, "angle " + plain.angle());
    }

    @Test
    void introObjectsStandDownWhereTheStageIndexDoesNotLinkThem() throws Exception {
        // Only Act 1 Present links $30/$31 as the intro (Object Index (Act 1 Present).asm:60-64). The other acts 1 and 2 stages
        // leave the slot ObjNull, which deletes itself (r3/object_index_1b_1c_1d.asm); Act 3 links boss parts there, so the
        // intro classes leave the slot alone until the boss lane registers them.
        load(CdStagePayload.R31B);
        CczIntroSpikes nullSlot = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertTrue(nullSlot.isDestroyed());
        load(CdStagePayload.R33C);
        CczIntroSpikes bossSlot = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertFalse(bossSlot.isDestroyed());
        assertEquals(0, bossSlot.routine(), "no intro routine ran");
    }

    // ---- bumper, flipper, Amy, animal (second pass) ----------------------------------------------------------------

    @Test
    void bumperSlidesCountsPrizesAndThrowsThePlayerAwayAtMinusSeven() {
        // Subtype 3: range $20 + $40 = $60, first leg $30 steps of a pixel, bit 7 clear: along Y; bit 6 clear: upwards (Bumper and Flipper/Main.asm:20-65).
        CczBumper moving = add(new CczBumper(spawn(0x1D, 0x80, -0x40, 3)));
        int baseY = moving.yPosition();
        runner.stepIdleFrames(1);
        assertEquals(baseY - 1, moving.yPosition());
        assertEquals(0x2F, moving.steps());
        // A still bumper (subtype 0: no range, no velocity) directly to the player's right.
        CczBumper bumper = add(new CczBumper(spawn(0x1D, 0x20, 0, 0)));
        runner.stepIdleFrames(1);
        assertEquals(5, bumper.prizes());
        // A touch: 100 points, a 100-point sprite, and the player is thrown along CalcAngle(bumper - player) at $F900/256:
        // the bumper is directly to his right, so angle 0: X speed -$700, Y speed 0 (:66-106).
        int score = GameServices.gameState().getScore();
        bumper.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        assertEquals(4, bumper.prizes());
        assertEquals(score + 100, GameServices.gameState().getScore());
        assertEquals(-0x700, sonic.getXSpeed());
        assertEquals(0, sonic.getYSpeed());
        assertTrue(sonic.getAir());
    }

    @Test
    void flipperSeatsTheStandingPlayerOnItsSlope() {
        // Column $20 of FlipperTopSlope is $FD (-3): the surface is 3 above oY; a player whose feet ($13 below his centre) are
        // within 8 above and 8 below it is placed on it (Bumper and Flipper/Main.asm:152-232).
        CczFlipperObject flipper = add(new CczFlipperObject(spawn(0x1E, 0, 0x14, 0)));
        int centreY = sonic.getCentreY();
        runner.stepIdleFrames(1);
        assertEquals(centreY + 0x14 - 3 - sonic.getYRadius(), sonic.getCentreY());
        assertTrue(sonic.isOnObject());
        assertEquals(flipper, CczFlipperObject.standingOn(sonic));
        flipper.flipperSwing();
        runner.stepIdleFrames(1);
        assertEquals(flipper, CczFlipperObject.standingOn(sonic), "animation 1 stops the collision test but does not release him");
    }

    @Test
    void groundHologramPlaysItsDiscLoopAndResumesAfterRewind() throws Exception {
        // Ani_CCAnimalGround (Level/Collision Chaos/Objects/Animal/Main.asm:243-245)
        // selects a delay of zero, 56 mapping frames, then $FF to loop.
        String loop = "33233233" + "23323322" + "33223322" + "33224424"
                + "42442442" + "44224422" + "44224422";
        int rewindAt = 13;
        for (var payload : List.of(CdStagePayload.R31B, CdStagePayload.R32B)) {
            load(payload);
            var projector = add(new CdProjector(spawn(0x38, 0xB0, -0x40, 0)));
            runner.stepIdleFrames(1);
            int parentSlot = projector.getSlotIndex();
            var ground = groundHologram(parentSlot);
            if (ground.routine() == 0) runner.stepIdleFrames(1);

            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            String prefix = hologramFrames(ground, rewindAt);
            var snapshot = rewind.capture();
            String suffix = hologramFrames(ground, loop.length() - rewindAt + 4);
            assertEquals(loop + loop.substring(0, 4), prefix + suffix, payload + " forward playback");

            ground.setDestroyed(true);
            runner.stepIdleFrames(1);
            rewind.restore(snapshot);
            assertEquals(loop.substring(rewindAt) + loop.substring(0, 4),
                    hologramFrames(groundHologram(parentSlot), suffix.length()), payload + " after rewind");
        }
    }

    private CczAnimal groundHologram(int parentSlot) {
        return live(0x39).stream().filter(CczAnimal.class::isInstance).map(CczAnimal.class::cast)
                .filter(animal -> animal.getSpawn().subtype() == 0x81 && animal.projectorSlot() == parentSlot)
                .findFirst().orElseThrow();
    }

    private String hologramFrames(CczAnimal animal, int count) {
        StringBuilder frames = new StringBuilder();
        for (int update = 0; update < count; update++) {
            runner.stepIdleFrames(1);
            frames.append(animal.mapFrame());
        }
        return frames.toString();
    }

    @Test
    void palmtreeHologramsStayAliveOutsideRangeUntilProjectorShutsDown() throws Exception {
        checkDistantHolograms(CdStagePayload.R11B, 0x2E, 0x24);
    }

    @Test
    void collisionChaosHologramsStayAliveOutsideRangeUntilProjectorShutsDown() throws Exception {
        checkDistantHolograms(CdStagePayload.R31B, 0x38, 0x39);
    }

    private void checkDistantHolograms(CdStagePayload payload, int parentId, int animalId) throws Exception {
        load(payload);
        var projector = add(new CdProjector(spawn(parentId, 0xB0, -0x40, 0)));
        runner.stepIdleFrames(1);
        List<ProjectorChild> distant = new ArrayList<>();
        for (int subtype : List.of(0x80, 0x81)) {
            ObjectSpawn outside = spawn(animalId, 0x800, -0x40, subtype);
            ProjectorChild child = payload == CdStagePayload.R11B
                    ? add(new CdAnimal(outside)) : add(new CczAnimal(outside));
            child.linkProjector(projector.getSlotIndex(), parentId);
            distant.add(child);
        }
        runner.stepIdleFrames(3);
        assertTrue(live(animalId).containsAll(distant), "both distant holograms keep their slots while the parent is live");

        sonic.setAnimationId(CdSonicAnimation.ROLL);
        projector.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(3);
        assertTrue(projector.childrenInvalid(), "the shutdown flag is set");
        assertFalse(projector.isDestroyed(), "the projector still occupies its slot during the explosion sequence");
        assertTrue(live(animalId).stream().noneMatch(distant::contains), "the shutdown flag retires both holograms");
    }

    @Test
    void flyingHologramPlaysTheDiscScriptAndDrawsTheProjectorTilesAfterRewind() throws Exception {
        // Ani_CCAnimalFlying (Level/Collision Chaos/Objects/Animal/Main.asm:239-241) uses animation 1 for holograms.
        String firstFrames = "00200200200211221122";
        for (var payload : List.of(CdStagePayload.R31B, CdStagePayload.R32B)) {
            load(payload);
            var projector = add(new CdProjector(spawn(0x38, 0xB0, -0x40, 0)));
            runner.stepIdleFrames(1);
            int parentSlot = projector.getSlotIndex();
            var flying = flyingHologram(parentSlot);
            if (flying.routine() == 0) runner.stepIdleFrames(1);

            assertEquals(firstFrames.substring(0, 5), hologramFrames(flying, 5));
            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var snapshot = rewind.capture();
            assertEquals(firstFrames.substring(5), hologramFrames(flying, firstFrames.length() - 5));
            flying.setDestroyed(true);
            runner.stepIdleFrames(1);
            rewind.restore(snapshot);
            flying = flyingHologram(parentSlot);
            assertEquals(firstFrames.substring(5), hologramFrames(flying, firstFrames.length() - 5));
            runner.stepIdleFrames(1);
            assertProjectorArt(flying, payload);
        }
    }

    private CczAnimal flyingHologram(int parentSlot) {
        return live(0x39).stream().filter(CczAnimal.class::isInstance).map(CczAnimal.class::cast)
                .filter(animal -> animal.getSpawn().subtype() == 0x80 && animal.projectorSlot() == parentSlot)
                .findFirst().orElseThrow();
    }

    private void assertProjectorArt(CczAnimal flying, CdStagePayload payload) throws Exception {
        var services = mock(ObjectServices.class);
        var renderManager = mock(ObjectRenderManager.class);
        var renderer = mock(PatternSpriteRenderer.class);
        when(services.gameModule()).thenReturn(GameServices.module());
        when(services.camera()).thenReturn(GameServices.camera());
        when(services.renderManager()).thenReturn(renderManager);
        when(renderer.isReady()).thenReturn(true);
        var key = ArgumentCaptor.forClass(String.class);
        when(renderManager.getRenderer(key.capture())).thenReturn(renderer);
        flying.setServices(services);
        flying.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(eq(flying.mapFrame()), eq(flying.getX()), eq(flying.getY()),
                eq((flying.spriteFlip() & 1) != 0), eq(false), anyInt());

        var art = (CdObjectArtProvider) GameServices.module().getObjectArtProvider();
        // The first eight tiles are identical in PLC 4 and 5 in the USA Past stages.
        String projectorKey = CczObjectArt.key(art, "flyinghologram", 0x797, GameServices.camera().getX());
        assertEquals(projectorKey, key.getValue(), payload + " must select the projector sheet");
        var sheet = art.getSheet(key.getValue());
        assertNotNull(sheet, "the renderer has a registered sheet");
        // PLC_Projector (Level/Collision Chaos/Graphics Lists (Act 1 Variant).asm:223-226) uploads at byte $F2E0.
        var vram = new CdStageVram();
        vram.loadPlc(art.image(), CdStageCatalogue.entry(payload).plcLists(), 5);
        Pattern[] patterns = sheet.getPatterns();
        var expected = new byte[patterns.length * Pattern.PATTERN_SIZE_IN_MEM];
        var actual = new byte[expected.length];
        for (int tile = 0; tile < patterns.length; tile++) {
            int offset = tile * Pattern.PATTERN_SIZE_IN_MEM;
            vram.tile(0x797 + tile).copyInto(expected, offset);
            patterns[tile].copyInto(actual, offset);
        }
        assertArrayEquals(expected, actual, payload + " must draw tiles from PLC 5");
    }

    @Test
    void animalsRunOnlyOnceTheProjectorIsDestroyed() {
        // CheckAnimalPrescence: in the present only CheckObjectDespawn runs until projDestroyed (Object Time Check.asm:52-81).
        CczAnimal flying = add(new CczAnimal(spawn(0x39, 0x40, -0x60, 0)));
        runner.stepIdleFrames(3);
        assertEquals(0, flying.phase(), "present, projector standing: no routine");
        com.openggf.game.soniccd.timetravel.CdTimeTravel.of(com.openggf.game.session.SessionManager.requireCurrentGameModule())
                .destroyProjector();                          // projDestroyed lives with the campaign time owner
        runner.stepIdleFrames(3);
        assertEquals(3, flying.phase(), "init sets phase 1, then it steps by 1 a frame (Animal/Main.asm:46-93)");
        CczAnimal ground = add(new CczAnimal(spawn(0x39, 0x40, -0x60, 1)));
        int baseX = ground.xPosition();
        int baseY = ground.yPosition();
        runner.stepIdleFrames(2);                            // init (rts), then the first hop frame
        assertEquals(baseX + 1, ground.xPosition());
        assertEquals(baseY - 4, ground.yPosition());
        assertEquals(-0x40000 + 0x2000, ground.yVelocity());
    }

    @Test
    void amyRunsFromThePlayerAndEmitsAHeartEveryThirtyTwoFrames() throws Exception {
        load(CdStagePayload.R31A);
        // Waits while the player is left of her; once he is at or past her X she runs ($20 acceleration) and emits hearts
        // when the byte accumulator adds $8 past $FF (objects/r3/intro.asm:271-376, 506-531).
        CczAmyRose amy = add(new CczAmyRose(spawn(0x32, 0x80, 0, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0, amy.routine());
        sonic.setCentreX((short) (amy.xPosition() + 0x10));
        runner.stepIdleFrames(1);
        assertEquals(2, amy.routine());
        runner.stepIdleFrames(1);
        assertEquals(0x20, amy.xVelocity());
        runner.stepIdleFrames(31);
        assertEquals(1, live(0x33).size(), "one heart after 32 running frames");
    }

    @Test
    void doorSlidesByTheOffsetByteWhileThePlayerIsBelowIt() {
        // Subtype $80 slides toward +X: offset += $10 a frame to $40 while the player's Y is at or below the door's, then back
        // down by $10 a frame while he is above (Level/Collision Chaos/Objects/Door/Main.asm, ObjDoor_Update).
        CczDoor door = add(new CczDoor(spawn(0x26, 0x40, -0x40, 0x80)));
        int baseX = door.xPosition();
        runner.stepIdleFrames(4);
        assertEquals(0x40, door.offset());
        assertEquals(baseX + 0x40, door.xPosition());
        assertEquals(8, door.delay(), "the open delay");
        sonic.setCentreY((short) (door.yPosition() - 0x40));
        runner.stepIdleFrames(5);                            // $30, $20, $10, 0, then the borrow of the fifth step
        assertEquals(0, door.offset());
        assertEquals(baseX, door.xPosition());
        assertEquals(0x3C, door.delay(), "subtype low nibble 0: a $3C frame close delay");
    }
}
