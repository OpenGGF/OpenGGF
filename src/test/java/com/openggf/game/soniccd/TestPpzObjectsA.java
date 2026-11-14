package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
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
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.ppz.PpzObjectArt;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.game.soniccd.objects.ppz.Obj3DPlant;
import com.openggf.game.soniccd.objects.ppz.Obj3DRamp;
import com.openggf.game.soniccd.objects.ppz.ObjAnton;
import com.openggf.game.soniccd.objects.ppz.ObjLogShadow;
import com.openggf.game.soniccd.objects.ppz.ObjMosqui;
import com.openggf.game.soniccd.objects.ppz.ObjPataBata;
import com.openggf.game.soniccd.objects.ppz.ObjScenery;
import com.openggf.game.soniccd.objects.ppz.ObjTagaTaga;
import com.openggf.game.soniccd.objects.ppz.ObjTamabboh;
import com.openggf.game.soniccd.objects.ppz.ObjTamabbohMissile;
import com.openggf.game.soniccd.objects.ppz.ObjWaterfall;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Palmtree Panic objects A on the loaded stages (their art, tables and
 * collision come from the disc). Each case cites the routine it checks in
 * {@code Level/Palmtree Panic/Objects/<name>/Main.asm}; the native comparisons are
 * {@link TestPpzObjectsAAppearances} and {@link TestPpzObjectsAPictures}.
 */
class TestPpzObjectsA {
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
        sonic.setInvulnerableFrames(0x7000);   // keep the player out of the way of the badniks' touch
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

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    private int floorDistance(AbstractObjectInstance o, int yRadius) {
        return ObjectTerrainUtils.checkFloorDist(o.getX(), o.getY(), yRadius).distance();
    }

    // ---- art ---------------------------------------------------------------------------------------

    @Test
    void sectionsAndSheetsComeFromTheStageFile() throws Exception {
        boot(CdStagePayload.R11A);
        CdObjectArtProvider art = art();
        // SectionRanges $680, $F80, $1980, $1F80, -1 (Section Data (Act 1 Present).asm:7-13).
        assertEquals(0, PpzObjectArt.section(art, 0));
        assertEquals(0, PpzObjectArt.section(art, 0x67F));
        assertEquals(1, PpzObjectArt.section(art, 0x680));
        assertEquals(4, PpzObjectArt.section(art, 0x1F80));
        assertEquals(4, PpzObjectArt.section(art, 0x7000));
        // ObjectTileIDs: Mosqui $23A0, Pata-Bata $23B0, Anton $2409 and $2370 (Object Tile IDs (Act 1 Present).asm:25-41).
        assertEquals(0x23A0, art.objectTileId(PpzObjectArt.TILE_ID_MOSQUI, 0));
        assertEquals(0x23B0, art.objectTileId(PpzObjectArt.TILE_ID_PATA_BATA, 0));
        assertEquals(0x2409, art.objectTileId(PpzObjectArt.TILE_ID_ANTON, 0));
        assertEquals(0x2370, art.objectTileId(PpzObjectArt.TILE_ID_ANTON, 1));
        // Section 0 loads PLC 2 (Art_Mosqui at $7400 = tile $3A0, Art_3DPlant at $8480): non-blank tiles.
        for (String[] kind : new String[][] {{PpzObjectArt.PPZ_MOSQUI1, "" + 0x23A0},
                {PpzObjectArt.PPZ_PLANT, "" + PpzObjectArt.PLANT_TILE},
                {PpzObjectArt.PPZ_RAMP, "" + PpzObjectArt.RAMP_TILE}}) {
            String key = PpzObjectArt.key(art, kind[0], Integer.parseInt(kind[1]), 0);
            assertNotNull(key, kind[0]);
            boolean ink = false;
            for (var pattern : art.getSheet(key).getPatterns()) {
                for (int i = 0; i < 64 && !ink; i++) ink = pattern.getPixel(i % 8, i / 8) != 0;
            }
            assertTrue(ink, kind[0] + " sheet is blank");
        }
    }

    // ---- Mosqui ------------------------------------------------------------------------------------

    @Test
    void mosquiPatrolsEightyPixelsAndTurns() throws Exception {
        boot(CdStagePayload.R11A);
        int x0 = sonic.getCentreX() + 0x60;
        ObjMosqui mosqui = add(new ObjMosqui(spawn(x0, sonic.getCentreY() - 0x100, 0x3D, 0)));
        runner.stepIdleFrames(1);
        // ObjMosqui_Init falls into _Main: one step of -$10000 (Mosqui/Main.asm:41,67-68).
        assertEquals(x0 - 1, mosqui.getX());
        assertEquals(0x2B, mosqui.getCollisionFlags(), "move.b #$2B,oColType(a0) (Mosqui/Main.asm:33)");
        runner.stepIdleFrames(0x7E);
        assertEquals(x0 - 0x7F, mosqui.getX());
        assertEquals(0, mosqui.spriteFlip() & 1);
        runner.stepIdleFrames(1);
        // |dx| reached $80: neg.l oVar2C, bchg #0, and the step is repeated the other way (Mosqui/Main.asm:77-80).
        assertEquals(x0 - 0x7F, mosqui.getX());
        assertEquals(1, mosqui.spriteFlip() & 1);
        runner.stepIdleFrames(1);
        assertEquals(x0 - 0x7E, mosqui.getX());
    }

    @Test
    void mosquiDivesOntoTheFloorAndSnapsEightPixelsIn() throws Exception {
        boot(CdStagePayload.R11A);
        ObjMosqui mosqui = add(new ObjMosqui(spawn(sonic.getCentreX() + 0x10, sonic.getCentreY() - 0x30, 0x3D, 0)));
        // The player sits inside the window of ObjMosqui_CheckInRange (Mosqui/Main.asm:98-112): dive at once.
        int steps = 0;
        int diveFrames = 0;
        while (mosqui.routine() < 8 && steps++ < 200) {
            int before = mosqui.getY();
            boolean wasDiving = mosqui.routine() == 6;
            runner.stepIdleFrames(1);
            if (wasDiving) diveFrames++;
            // addq.w #6,oY(a0) every frame of the dive (Mosqui/Main.asm:124).
            if (wasDiving && mosqui.routine() == 6) assertEquals(before + 6, mosqui.getY());
        }
        assertTrue(diveFrames > 0, "routine 6 ran");
        assertEquals(8, mosqui.routine(), "the dive ended in the wait routine");
        // subi.w #-8,d1; add.w d1,oY(a0): the snap leaves the radius point 8 pixels into the floor (Mosqui/Main.asm:126-129).
        assertEquals(-8, floorDistance(mosqui, 0x10));
    }

    // ---- Anton -------------------------------------------------------------------------------------

    @Test
    void antonDropsToTheFloorThenWalksFollowingIt() throws Exception {
        boot(CdStagePayload.R11A);
        int x0 = sonic.getCentreX() + 0x60;
        ObjAnton anton = add(new ObjAnton(spawn(x0, sonic.getCentreY() - 8, 0x3F, 0)));
        runner.stepIdleFrames(1);
        // ObjAnton_Init has no addq.b #2,oRoutine: routine 0 re-runs init and _Place each frame (Anton/Main.asm:24-64).
        assertEquals(0, anton.routine());
        assertEquals(0x29, anton.getCollisionFlags());
        int y = anton.getY();
        for (int i = 0; i < 40 && anton.routine() < 4; i++) {
            runner.stepIdleFrames(1);
            if (anton.routine() < 4) assertEquals(y + 1, anton.getY(), "+$10000 a frame");
            y = anton.getY();
        }
        assertEquals(4, anton.routine());
        int x = anton.getX();
        runner.stepIdleFrames(1);
        assertEquals(x - 1, anton.getX(), "walk velocity -$10000 (Anton/Main.asm:37,69-70)");
        // add.w d1,oY(a0) keeps the radius point on the surface (Anton/Main.asm:84).
        assertEquals(0, floorDistance(anton, 0x13));
    }

    // ---- Pata-Bata ---------------------------------------------------------------------------------

    @Test
    void pataBataFliesOnTheSineWithTheLeftoverHighWord() throws Exception {
        boot(CdStagePayload.R11A);
        int x0 = sonic.getCentreX() + 0x60;
        int y0 = sonic.getCentreY() - 0x60;
        ObjPataBata bata = add(new ObjPataBata(spawn(x0, y0, 0x3E, 0)));
        runner.stepIdleFrames(1);
        // Frame 1: x -= $8000 (half a pixel); angle = high byte of -$200 = $FE, sine -12 (angle 1 is 6); flying left the
        // high word of oVar30 is $FFFF, so (-12 << 16 | $FFFF) >> 3 = -$16001 and y = (y0 << 16 | $8000) - $16001
        // = (y0 - 1) << 16 | $1FFF; without the leftover word it would be exactly (y0 - 1) << 16 (Pata-Bata/Main.asm:48-80).
        assertEquals(x0 - 1, bata.getX(), "x0 - 0.5 truncates to x0 - 1 with a $8000 subpixel");
        assertEquals(0x8000, bata.xSubpixel());
        assertEquals(y0 - 1, bata.getY());
        assertEquals(0x1FFF, bata.ySubpixel(), "the leftover high word keeps the sub-pixel off zero");
        assertEquals(0x2A, bata.getCollisionFlags());
        runner.stepIdleFrames(1);
        assertEquals(x0 - 1, bata.getX());
        assertEquals(0, bata.xSubpixel());
        // Angle $FC, sine -25 (the table is not linear): ((-25 << 16 | $FFFF) >> 3) + (y0 << 16 | $8000) = (y0 - 3) << 16 | $7FFF.
        assertEquals(y0 - 3, bata.getY());
        assertEquals(0x7FFF, bata.ySubpixel());
    }

    // ---- Taga-Taga ---------------------------------------------------------------------------------

    @Test
    void tagaTagaJumpsToTheCalculatedHeightAndLands() throws Exception {
        boot(CdStagePayload.R11A);
        int y0 = sonic.getCentreY() + 0x40;
        ObjTagaTaga taga = add(new ObjTagaTaga(spawn(sonic.getCentreX() + 0x80, y0, 0x40, 0)));
        runner.stepIdleFrames(1);
        // Init, wait and animate in one call: routine 4 with the collision off (Taga-Taga/Main.asm:26-74).
        assertEquals(4, taga.routine());
        assertEquals(0, taga.getCollisionFlags());
        int steps = 0;
        while (taga.getCollisionFlags() == 0 && steps++ < 300) runner.stepIdleFrames(1);
        assertEquals(0x2E, taga.getCollisionFlags(), "move.b #$2E,oColType(a0) (Taga-Taga/Main.asm:81)");
        // The jump frame already flies: y = y0 - $3C000 -> y0 - 4 (Taga-Taga/Main.asm:92-94).
        assertEquals(y0 - 4, taga.getY());
        int top = taga.getY();
        steps = 0;
        while (taga.routine() != 2 && steps++ < 400) {
            runner.stepIdleFrames(1);
            top = Math.min(top, taga.getY());
        }
        assertEquals(2, taga.routine(), "falling back past the start sets routine 2");
        // v0 = 3.75 px/frame against 1/16 px/frame^2: the peak is v0^2 / 2g = 112.5 pixels above the start.
        assertEquals(y0 - 113, top, 3);
        runner.stepIdleFrames(1);
        assertEquals(0, taga.getCollisionFlags(), "ObjTagaTaga_Init2 clears oColType");
    }

    // ---- Tamabboh ----------------------------------------------------------------------------------

    @Test
    void tamabbohFiresTwoMissilesLobbedOppositeWays() throws Exception {
        boot(CdStagePayload.R11A);
        ObjTamabboh tamabboh = add(new ObjTamabboh(spawn(sonic.getCentreX() + 0x30, sonic.getCentreY(), 0x22, 0)));
        int steps = 0;
        while (live(0x22).stream().noneMatch(o -> o instanceof ObjTamabbohMissile) && steps++ < 400) {
            runner.stepIdleFrames(1);
        }
        List<ObjectInstance> missiles = live(0x22).stream().filter(o -> o instanceof ObjTamabbohMissile).toList();
        assertEquals(2, missiles.size(), "ObjTamabboh_Fire allocates two (Tamabboh/Main.asm:168-181)");
        ObjTamabbohMissile first = (ObjTamabbohMissile) missiles.get(0);
        ObjTamabbohMissile second = (ObjTamabbohMissile) missiles.get(1);
        assertTrue(first.getSlotIndex() < second.getSlotIndex(), "FindObjSlot order: the right-hand missile first");
        int x1 = first.getX(), x2 = second.getX(), y1 = first.getY();
        assertEquals(tamabboh.getX(), x1);
        assertEquals(tamabboh.getY() - 10, y1, "subi.w #10,oY(a1) (Tamabboh/Main.asm:197)");
        assertEquals(0xAD, first.getCollisionFlags() & 0xFF);
        runner.stepIdleFrames(1);
        runner.stepIdleFrames(1);
        // Frame 1 after creation: +$20000 / -$20000 in X, -$40000 in Y, +$2000 gravity (Tamabboh/Main.asm:238-246,271-279).
        int moved = first.getX() - x1;
        assertTrue(moved >= 2 && moved <= 4, "right missile moves right at two pixels a frame: " + moved);
        assertTrue(x2 - second.getX() >= 2, "left missile moves left");
        assertTrue(first.getY() < y1, "both rise at first");
        assertEquals(ObjTamabboh.class, tamabboh.getClass());
    }

    // ---- kills and the good future ------------------------------------------------------------------

    @Test
    void killingABadnikMakesTheExplosionInItsSlotAndScoresTheChain() throws Exception {
        boot(CdStagePayload.R11A);
        int score = GameServices.gameState().getScore();
        ObjMosqui first = add(new ObjMosqui(spawn(sonic.getCentreX() + 0x40, sonic.getCentreY() - 0x100, 0x3D, 0)));
        runner.stepIdleFrames(1);
        int slot = first.getSlotIndex();
        first.onPlayerAttack(sonic, null);
        assertTrue(first.isDestroyed());
        assertEquals(score + 100, GameServices.gameState().getScore(), "EnemyPoints 10 x AddPoints' 10");
        var explosion = live(0x18).stream().filter(o -> o instanceof CdExplosion).findFirst().orElseThrow();
        assertEquals(slot, ((AbstractObjectInstance) explosion).getSlotIndex(), "the badnik's slot becomes the explosion");
        assertEquals(1, explosion.getSpawn().subtype(), "move.b #1,oSubtype(a1) (Player Object Collision.asm:178)");
        runner.stepIdleFrames(1);
        List<ObjectInstance> points = live(0x1C).stream().filter(o -> o instanceof CdPoints && o.getSpawn().subtype() >= 0x80).toList();
        assertEquals(1, points.size());
        assertEquals(0, ((CdPoints) points.getFirst()).frame());

        ObjMosqui second = add(new ObjMosqui(spawn(sonic.getCentreX() + 0x50, sonic.getCentreY() - 0x100, 0x3D, 0)));
        runner.stepIdleFrames(1);
        second.onPlayerAttack(sonic, null);
        assertEquals(score + 100 + 200, GameServices.gameState().getScore(), "second kill of the chain");
        runner.stepIdleFrames(1);
        assertTrue(live(0x1C).stream().anyMatch(o -> o instanceof CdPoints p && p.frame() == 1), "points frame oVar3E >> 1");
    }

    @Test
    void goodFutureTurnsBadniksIntoExplosionsWithoutScore() throws Exception {
        boot(CdStagePayload.R11C);
        int score = GameServices.gameState().getScore();
        ObjAnton anton = add(new ObjAnton(spawn(sonic.getCentreX() + 0x40, sonic.getCentreY() - 8, 0x3F, 0)));
        runner.stepIdleFrames(1);
        // DestroyOnGoodFuture: timeZone is 2, not 1, so the badnik is deleted into an explosion (Object Time Check.asm:12-44).
        assertTrue(anton.isDestroyed());
        assertTrue(live(0x18).stream().anyMatch(o -> o instanceof CdExplosion && o.getX() == anton.getX()
                && o.getY() == anton.getY()), "explosion $18 at the same position");
        assertEquals(score, GameServices.gameState().getScore());
    }

    @Test
    void aPresentSubtypeZeroBadnikSurvivesAndAGoodFutureOnlyKeepsThatCase() throws Exception {
        boot(CdStagePayload.R11A);
        ObjMosqui damaged = add(new ObjMosqui(spawn(sonic.getCentreX() + 0x40, sonic.getCentreY() - 0x100, 0x3D, 1)));
        runner.stepIdleFrames(1);
        assertFalse(damaged.isDestroyed(), "goodFuture is clear in a present stage: tst.b goodFuture; beq.s .End");
        assertEquals(0x2B, damaged.getCollisionFlags());
    }

    // ---- 3D ramp and plant ---------------------------------------------------------------------------

    @Test
    void plantAllocatesFourChildrenForAnOddSubtypeAndSixOtherwise() throws Exception {
        boot(CdStagePayload.R11A);
        int x0 = sonic.getCentreX() + 0x100;
        int before = live(0x2C).size();
        Obj3DPlant odd = add(new Obj3DPlant(spawn(x0, sonic.getCentreY() - 0x60, 0x2C, 1)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> plants = live(0x2C).stream().filter(o -> o != odd).toList();
        assertEquals(before + 4, plants.size(), "subtype bit 0 set: one row plant and three front plants (3D Ramp/Main.asm:33-36,63-84)");
        // Row plant at +$80, front plants at +0, +$60, -$60 (Obj3DPlant_Offsets1/2, Main.asm:89-90).
        assertEquals(x0 + 0x40, odd.getX(), "the entry itself takes offset $40");
        List<Integer> xs = plants.stream().map(ObjectInstance::getX).sorted().toList();
        assertEquals(List.of(x0 - 0x60, x0, x0 + 0x60, x0 + 0x80), xs);
        long front = plants.stream().filter(o -> ((Obj3DPlant) o).mapFrame() == 1).count();
        assertEquals(3, front, "three frame-1 front plants");

    }

    @Test
    void plantAllocatesSixChildrenForAnEvenSubtype() throws Exception {
        boot(CdStagePayload.R11A);
        int x0 = sonic.getCentreX() + 0x80;
        Obj3DPlant even = add(new Obj3DPlant(spawn(x0, sonic.getCentreY() - 0x60, 0x2C, 0)));
        runner.stepIdleFrames(1);
        // bclr #0 clear -> d6 = 3: itself and three more row plants, then three front plants (3D Ramp/Main.asm:33-35,63-84).
        List<ObjectInstance> children = live(0x2C).stream().filter(o -> o != even).toList();
        assertEquals(6, children.size());
        assertEquals(x0 + 0x40, even.getX());
        List<Integer> xs = children.stream().map(ObjectInstance::getX).sorted().toList();
        assertEquals(List.of(x0 - 0x80, x0 - 0x60, x0 - 0x40, x0, x0 + 0x60, x0 + 0x80), xs);
    }

    @Test
    void rampLaunchesAGroundedPlayerAndRunsItsTimer() throws Exception {
        boot(CdStagePayload.R11A);
        Obj3DRamp ramp = add(new Obj3DRamp(spawn(sonic.getCentreX(), sonic.getCentreY(), 0x2A, 0)));
        assertFalse(sonic.getAir());
        sonic.setYSpeed((short) 0);
        ramp.update(0, sonic);
        // .ChkTouch -> .TimerSet: oVar2E = 60; yVel >= 0 -> +$C00 (3D Ramp/Main.asm:326-337).
        assertEquals(60, ramp.timer());
        assertEquals(0xC00, sonic.getYSpeed());
        sonic.setYSpeed((short) -1);
        ramp.update(0, sonic);
        assertEquals(59, ramp.timer(), "subq.b #1,oVar2E(a0)");
        assertEquals(-0xC00, sonic.getYSpeed(), "yVel < 0 -> -$C00");
    }

    // ---- static objects --------------------------------------------------------------------------------

    @Test
    void sceneryAndLogShadowTakeTheirFrameFromTheSubtypeAndTheirTileFromTheTimeZone() throws Exception {
        boot(CdStagePayload.R11A);
        ObjLogShadow shadow = add(new ObjLogShadow(spawn(sonic.getCentreX() + 0x60, sonic.getCentreY(), 0x33, 3)));
        ObjScenery scenery = add(new ObjScenery(spawn(sonic.getCentreX() + 0x90, sonic.getCentreY(), 0x32, 2)));
        runner.stepIdleFrames(1);
        assertEquals(3, shadow.mapFrame(), "move.b oSubtype(a0),oMapFrame(a0) (Log Shadow/Main.asm:27)");
        assertEquals(2, scenery.mapFrame());
        // Present, act 1: row 1 of each list, ori.w #$4000 (Log Shadow/Main.asm:60-64; Scenery/Main.asm:59-62).
        assertEquals(List.of(0x418 | 0x4000), PpzObjectArt.logShadowTiles(art()));
        assertEquals(List.of(0x438 | 0x4000), PpzObjectArt.sceneryTiles(art()));
        assertEquals(6, shadow.getPriorityBucket(), "move.b #6,oPriority(a0)");
    }

    @Test
    void waterfallFallsFourPixelsAFrameForOneHundredAndTwentyEightRows() throws Exception {
        boot(CdStagePayload.R11A);
        ObjWaterfall waterfall = add(new ObjWaterfall(spawn(sonic.getCentreX(), 0x105, 0x04, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0x100, waterfall.getY(), "andi.w #$FFF0,oY(a0) (Effects/Main.asm:29)");
        runner.stepIdleFrames(1);
        assertEquals(0x104, waterfall.getY());
        int frames = 2;
        while (!waterfall.isDestroyed() && frames < 200) {
            runner.stepIdleFrames(1);
            frames++;
        }
        // Deleted when oY + 4 reaches oY0 + $180: the 96th move (Effects/Main.asm:36-41).
        assertEquals(1 + 96, frames);
    }

    @org.junit.jupiter.api.Test
    void theLayoutsPlaceTheObjectsTheStageIndexesDeclare() throws Exception {
        boot(CdStagePayload.R11A);
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        assertTrue(level.placements().stream().anyMatch(p -> (p.idByte() & 0x7F) == 0x2C), "the plant is placed");
    }

    /**
     * The waterfall ($04, {@code ObjWaterfall}) is unreachable in shipped data: none of the ten Palmtree Panic stages'
     * object layouts (every entry, before the time-zone filter) places an ID $04, so no native row can show its
     * {@code PlaceBlockAtPos} pass (Effects/Main.asm:45-58).
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = CdStagePayload.class,
            names = {"R11A", "R11B", "R11C", "R11D", "R12A", "R12B", "R12C", "R12D", "R13C", "R13D"})
    void noShippedPalmtreePanicLayoutPlacesTheWaterfall(CdStagePayload payload) throws Exception {
        boot(payload);
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        assertTrue(!level.placements().isEmpty(), payload + " layout read");
        assertEquals(0, level.placements().stream().filter(p -> (p.idByte() & 0x7F) == 0x04).count(),
                payload + ": entries of ID $04");
    }
}
