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
import com.openggf.game.soniccd.objects.ppz.ObjBreakableWall;
import com.openggf.game.soniccd.objects.ppz.ObjCollapsePlatform;
import com.openggf.game.soniccd.objects.ppz.ObjSonicHole;
import com.openggf.game.soniccd.objects.ppz.ObjSpinningDisc;
import com.openggf.game.soniccd.objects.ppz.ObjSpringBoard;
import com.openggf.game.soniccd.objects.ppz.PpzObjectArt;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Palmtree Panic objects B on the loaded stages (art, tables and collision come from the
 * disc): the collapsing platform and its pieces, the springboard, the breakable wall, the spinning disc, the Sonic
 * hole and the blank earthquake and missile objects. Each case cites the routine it checks in
 * {@code Level/Palmtree Panic/Objects/<name>/Main.asm}; every expected number is read off the assembly or its data
 * tables there, not off the implementation's output. The native comparisons are {@link TestPpzObjectsBAppearances},
 * {@code TestSonicCdFixtureFrontier} and {@link TestPpzObjectsBPictures}.
 */
class TestPpzObjectsB {
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
        sonic.setInvulnerableFrames(0x7000);
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

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private <T> List<T> live(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && type.isInstance(o)).map(type::cast).toList();
    }

    private CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    /** Puts Sonic in the air above an empty stretch of the start area, at rest. */
    private void dropSonicAt(int x, int y) {
        sonic.setCentreX((short) x);
        sonic.setCentreY((short) y);
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0);
        sonic.setGSpeed((short) 0);
        sonic.setAir(true);
    }

    // ---- art and layouts ---------------------------------------------------------------------------

    @Test
    void sheetsComeFromTheStageFile() throws Exception {
        boot(CdStagePayload.R12B);
        CdObjectArtProvider art = art();
        // SetObjectTileID 6 and 7 (Spinning Disc/Main.asm:42-43, Springboard/Main.asm:135).
        assertTrue(art.objectTileId(PpzObjectArt.TILE_ID_SPINNING_DISC, 0) > 0, "spinning disc tile");
        assertTrue(art.objectTileId(PpzObjectArt.TILE_ID_SPRINGBOARD, 0) > 0, "springboard tile");
        // SectionRanges bounds a camera X of 0 to the last section (R12B's own); look in every section.
        int[] cameraXs = {0, 0x400, 0x800, 0xC00, 0x1000, 0x1800, 0x2000, 0x2800, 0x3000, 0x7000};
        String[][] kinds = {
                {PpzObjectArt.PPZ_COLLAPSE1, "" + PpzObjectArt.PLATFORM_TILE},
                {PpzObjectArt.PPZ_COLLAPSE2, "" + PpzObjectArt.PLATFORM_TILE},
                {PpzObjectArt.PPZ_COLLAPSE3, "" + PpzObjectArt.PLATFORM_TILE},
                {PpzObjectArt.PPZ_COLLAPSE4, "" + PpzObjectArt.PLATFORM_TILE},
                {PpzObjectArt.PPZ_WALL, "" + PpzObjectArt.PLATFORM_TILE},
                {PpzObjectArt.PPZ_DISC, "" + art.objectTileId(PpzObjectArt.TILE_ID_SPINNING_DISC, 0)},
                {PpzObjectArt.PPZ_SPRINGBOARD, "" + art.objectTileId(PpzObjectArt.TILE_ID_SPRINGBOARD, 0)},
                {PpzObjectArt.PPZ_HOLE, "" + PpzObjectArt.HOLE_TILE},
                {PpzObjectArt.PPZ_HOLE, "" + PpzObjectArt.HOLE_PAST_TILE}};
        List<String> blank = new ArrayList<>();
        for (String[] kind : kinds) {
            boolean ink = false;
            boolean found = false;
            for (int cameraX : cameraXs) {
                String key = PpzObjectArt.key(art, kind[0], Integer.parseInt(kind[1]), cameraX);
                if (key == null) continue;
                found = true;
                for (var pattern : art.getSheet(key).getPatterns()) {
                    for (int i = 0; i < 64 && !ink; i++) ink = pattern.getPixel(i % 8, i / 8) != 0;
                }
            }
            if (!found || !ink) blank.add(kind[0] + "/" + kind[1] + (found ? " blank" : " missing"));
        }
        assertTrue(blank.isEmpty(), "sheets without art in any section: " + blank);
    }

    @Test
    void layoutsPlaceNoBlankObjectsAndListWhatTheyPlace() throws Exception {
        Map<Integer, List<String>> byId = new TreeMap<>();
        for (CdStagePayload payload : new CdStagePayload[] {CdStagePayload.R11A, CdStagePayload.R11B,
                CdStagePayload.R11C, CdStagePayload.R11D, CdStagePayload.R12A, CdStagePayload.R12B,
                CdStagePayload.R12C, CdStagePayload.R12D, CdStagePayload.R13C, CdStagePayload.R13D}) {
            try {
                boot(payload);
                CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
                int timeZone = CdLevel.nativeTimeZone(payload.stage().period());
                Map<Integer, Integer> counts = new TreeMap<>();
                for (var placement : level.placements()) {
                    if (placement.spawnsIn(timeZone)) counts.merge(placement.idByte() & 0x7F, 1, Integer::sum);   // bit 7 marks a saved-flags entry (Object Spawner.asm:71, 297-299)
                }
                counts.forEach((id, n) -> byId.computeIfAbsent(id, k -> new ArrayList<>()).add(payload + "x" + n));
                StringBuilder where = new StringBuilder();
                for (var placement : level.placements()) {
                    int id = placement.idByte() & 0x7F;
                    if (placement.spawnsIn(timeZone) && (id == 0x09 || id == 0x20 || id == 0x31 || id == 0x36)) {
                        where.append(String.format(" $%02X@(%04X,%04X)s%02X", id, placement.x(), placement.yWord() & 0xFFF,
                                placement.subtype()));
                    }
                }
                System.out.println("PPZOBJB placements " + payload + ":" + where);
            } finally {
                cleanup();
            }
        }
        StringBuilder text = new StringBuilder();
        byId.forEach((id, stages) -> text.append(String.format("%n$%02X: %s", id, String.join(" ", stages))));
        System.out.println("PPZOBJB layout IDs per stage (object ID without the layout's bit 7, placements that spawn in the stage's time zone):" + text);
        // Effects/Main.asm:65-75 and Missile/Main.asm:7-10: bare rts routines nothing places.
        for (int blank : new int[] {0x05, 0x08, 0x23}) {
            assertFalse(byId.containsKey(blank), String.format("$%02X is placed: %s", blank, byId.get(blank)));
        }
    }

    // ---- collapsing platform -------------------------------------------------------------------------

    /** The data bytes of Data (Present).asm:12-26 for ObjCollapsePlatform_Sizes1 entry 0: 5 by 4 cells, $FF a gap. */
    private static final int[][] MULTI_FRAMES = {
            {-1, -1, 0, 0, 0}, {1, 2, 3, 3, 4}, {0, 5, 5, 5, 5}, {6, 6, 6, 6, 6}};

    @Test
    void multiRowPlatformBreaksIntoPiecesWithTheDelaysOfTheSizesTable() throws Exception {
        boot(CdStagePayload.R11A);
        int px = sonic.getCentreX(), py = sonic.getCentreY() - 0x80;
        ObjCollapsePlatform platform = add(new ObjCollapsePlatform(spawn(px, py, 0x20, 0)));
        runner.stepIdleFrames(1);
        // ObjCollapsePlatform_Init: (4+1)*8 wide; (3+1)*8+2 high (Collapsing Platform/Main.asm:55-68).
        assertEquals(0x28, platform.solidWidth());
        assertEquals(0x22, platform.solidYRadius());
        assertEquals(0, platform.mapFrame());
        dropSonicAt(px, py - 0x22 - 0x13 - 0x20);
        int frames = 0;
        while (!platform.isDestroyed() && frames++ < 200) runner.stepIdleFrames(1);
        assertTrue(platform.isDestroyed(), "standing on it broke it (TopSolidObject -> .StandOn)");
        List<ObjCollapsePlatform> pieces = new ArrayList<>(live(ObjCollapsePlatform.class).stream()
                .filter(ObjCollapsePlatform::isPiece).toList());
        // 20 cells minus the two gaps: 18 pieces, one slot each (BreakUp_MultiRow .Loop2, Main.asm:174-211).
        assertEquals(18, pieces.size());
        int deltaCheck = Integer.MIN_VALUE;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int frame = MULTI_FRAMES[row][col];
                int x = px - 0x20 + 0x10 * col;                  // d0 = -(4 << 3), +$10 a column (Main.asm:147-155)
                int y = py + 0x18 - 0x10 * row;                  // (3 << 3) + oY, -$10 a row (Main.asm:161-165, 209)
                var found = pieces.stream().filter(p -> p.getX() == x && p.getY() == y).toList();
                if (frame < 0) {
                    assertTrue(found.isEmpty(), "gap at row " + row + " col " + col);
                    continue;
                }
                assertEquals(1, found.size(), "piece at row " + row + " col " + col);
                ObjCollapsePlatform piece = found.getFirst();
                assertEquals(frame, piece.mapFrame(), "frame at row " + row + " col " + col);
                int expectedDelay = 9 + 5 * row + 12 * col;      // d2 = 9, +5 a row; d1 = d2, +$C a column
                int behind = expectedDelay - piece.delayFrames();
                if (deltaCheck == Integer.MIN_VALUE) deltaCheck = behind;
                assertEquals(deltaCheck, behind, "every piece has run the same number of Delay updates");
                assertEquals(row == 3 ? 0x8 : 0, piece.solidWidth(), "only the top row is solid (Main.asm:194-198)");
            }
        }
        assertTrue(deltaCheck == 0 || deltaCheck == 1, "created in this frame's object pass: " + deltaCheck);
    }

    @Test
    void singleRowPlatformBreaksIntoOnePieceRowAndThePiecesFall() throws Exception {
        boot(CdStagePayload.R11A);
        int px = sonic.getCentreX(), py = sonic.getCentreY() - 0x80;
        // Subtype $80 selects Sizes2 entry 0: byte_20C790 = 5, 1, then six frame 0 bytes (Data (Present).asm:30-47).
        ObjCollapsePlatform platform = add(new ObjCollapsePlatform(spawn(px, py, 0x20, 0x80)));
        runner.stepIdleFrames(1);
        assertEquals(0x30, platform.solidWidth());
        assertEquals(0x12, platform.solidYRadius(), "(1+1)*8+2");
        dropSonicAt(px, py - 0x12 - 0x13 - 0x20);
        int frames = 0;
        while (!platform.isDestroyed() && frames++ < 200) runner.stepIdleFrames(1);
        assertTrue(platform.isDestroyed());
        List<ObjCollapsePlatform> pieces = new ArrayList<>(live(ObjCollapsePlatform.class).stream()
                .filter(ObjCollapsePlatform::isPiece).sorted(Comparator.comparingInt(ObjCollapsePlatform::getX)).toList());
        assertEquals(6, pieces.size());
        int behind = Integer.MIN_VALUE;
        for (int i = 0; i < 6; i++) {
            ObjCollapsePlatform piece = pieces.get(i);
            assertEquals(px - 0x28 + 0x10 * i, piece.getX(), "d4 = -(5 << 3) + oX, +$10 (Main.asm:242-272, 290-295)");
            assertEquals(py, piece.getY());
            assertEquals(0, piece.mapFrame());
            assertEquals(8, piece.solidWidth(), "oWidth 8 (Main.asm:287-288)");
            if (behind == Integer.MIN_VALUE) behind = 9 - piece.delayFrames();
            assertEquals(behind, 9 + 12 * i - piece.delayFrames(), "d2 = 9, +$C a piece (Main.asm:272, 294-295)");
        }
        // The piece with the shortest delay falls first: $20000 + $4000 a frame, 16.16 (Main.asm:115-117, 282).
        ObjCollapsePlatform first = pieces.getFirst();
        int y0 = first.getY();
        int guard = 0;
        while (first.getY() == y0 && guard++ < 80) runner.stepIdleFrames(1);
        assertTrue(first.getY() > y0, "the first piece started to fall");
        int n = 1;
        for (; n <= 6; n++) {
            int expected = (int) Math.floor(2.0 * n + n * (n - 1) / 8.0);
            assertEquals(y0 + expected, first.getY(), "fall step " + n);
            runner.stepIdleFrames(1);
        }
        // ObjCollapsePlatform_Fall deletes it once oY - playerY > $200 (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:119-122).
        sonic.setCentreY((short) (first.getY() - 0x300));
        runner.stepIdleFrames(1);
        assertTrue(first.isDestroyed());
    }

    @Test
    void platformStandsDownInTheBossAct() throws Exception {
        boot(CdStagePayload.R13C);
        ObjSonicHole hole = add(new ObjSonicHole(spawn(sonic.getCentreX(), sonic.getCentreY(), 0x31, 0)));
        ObjCollapsePlatform platform = add(new ObjCollapsePlatform(spawn(sonic.getCentreX(), sonic.getCentreY() - 0x40, 0x20, 0)));
        ObjBreakableWall wall = add(new ObjBreakableWall(spawn(sonic.getCentreX() + 0x80, sonic.getCentreY(), 0x36, 0)));
        runner.stepIdleFrames(2);
        // Object Index (Act 3).asm: $20, $31 and $36 are ObjNull or boss parts: the first run deletes the object.
        assertTrue(hole.isDestroyed() && platform.isDestroyed() && wall.isDestroyed());
    }

    // ---- springboard -----------------------------------------------------------------------------------

    @Test
    void springboardBouncesALandingPlayerAtTheCappedSpeed() throws Exception {
        boot(CdStagePayload.R12B);
        int bx = sonic.getCentreX(), by = sonic.getCentreY() - 0x80;
        ObjSpringBoard board = add(new ObjSpringBoard(spawn(bx, by, 0x28, 0)));
        runner.stepIdleFrames(1);
        assertFalse(board.isFlipped(), "subtype 0, no flip flag: idle routine 2 (Springboard/Main.asm:140-151)");
        // Hit box 5 (idle, normal) is x in [bx, bx+24), y in [by-4, by+4) (.Hitboxes, Main.asm:93-101).
        dropSonicAt(bx + 0x0C, by - 0x20);
        sonic.setYSpeed((short) 0x900);
        int frames = 0;
        while (sonic.getYSpeed() >= 0 && frames++ < 40) runner.stepIdleFrames(1);
        // .SetYVel: ySpeed + $100 is at least $A00, so it is capped: -$A00 (Main.asm:226-229, 331-334).
        assertTrue(sonic.getYSpeed() < 0, "the board threw Sonic up");
        assertEquals(-0xA00, sonic.getYSpeed());
        assertTrue(sonic.getAir());
        assertTrue(sonic.getRolling(), "a non-rolling player is made to roll: radii $E/7 (Main.asm:344-356)");
    }

    @Test
    void flippedSpringboardUsesTheFlippedRoutine() throws Exception {
        boot(CdStagePayload.R12B);
        ObjSpringBoard board = add(new ObjSpringBoard(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY() - 0x80, 0x28, 1)));
        runner.stepIdleFrames(1);
        assertTrue(board.isFlipped(), "a non-zero subtype takes the flipped routine and animation 4 (Main.asm:140-149)");
    }

    // ---- breakable wall ----------------------------------------------------------------------------------

    /** ObjBreakableWall_PieceFrames row 0 and the first speeds (Breakable Wall/Main.asm:131-152). */
    private static final int[] WALL_FRAMES_0 = {8, 9, 8, 0xC, 0xD, 0xC};

    @Test
    void breakableWallBreaksWhenARollingPlayerTouchesItAndSplitsIntoSixPieces() throws Exception {
        boot(CdStagePayload.R11A);
        int wx = sonic.getCentreX() + 0x40, wy = sonic.getCentreY() - 0x80;
        ObjBreakableWall wall = add(new ObjBreakableWall(spawn(wx, wy, 0x36, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0, wall.mapFrame(), "move.b oSubtype(a0),oMapFrame(a0) (Main.asm:31)");
        assertFalse(wall.isPiece());
        dropSonicAt(wx - 0x30, wy);
        sonic.setAnimationId(2);                    // Sonic1AnimationIds.ROLL: .CheckIfRoll tests oAnim == 2
        sonic.setRollingFlagPreserveRadii(true);
        sonic.setXSpeed((short) 0x400);
        int frames = 0;
        while (!wall.isPiece() && frames++ < 80) {
            sonic.setAnimationId(2);
            runner.stepIdleFrames(1);
            sonic.setYSpeed((short) 0);             // hold the air so the roll meets the wall
            sonic.setCentreY((short) wy);
        }
        assertTrue(wall.isPiece(), "a roll into the wall broke it");
        List<ObjBreakableWall> pieces = live(ObjBreakableWall.class);
        assertEquals(6, pieces.size(), "the wall and five FindObjSlot pieces (.BreakUp, Main.asm:56-103)");
        // Piece 0 is the wall itself moved by the first delta (-8, -$10); the others start from that position
        // (Main.asm:79-80, 91-94) and add their own delta. ObjBreakableWall_PieceDeltas (scddisasm/src/Level/Palmtree Panic/Objects/Breakable Wall/Main.asm:140-146).
        int[][] deltas = {{-8, -16}, {0, 16}, {0, 32}, {16, 0}, {16, 16}, {16, 32}};
        int baseX = wx - 8, baseY = wy - 16;
        for (int i = 1; i < 6; i++) {
            int fx = baseX + deltas[i][0], fy = baseY + deltas[i][1];
            long near = pieces.stream().filter(p -> Math.abs(p.getX() - fx) <= 6 && Math.abs(p.getY() - fy) <= 12).count();
            assertTrue(near >= 1, "piece " + i + " near " + fx + "," + fy);
        }
        List<Integer> shown = pieces.stream().map(ObjBreakableWall::mapFrame).sorted().toList();
        List<Integer> expected = new ArrayList<>();
        for (int f : WALL_FRAMES_0) expected.add(f);
        expected.sort(Integer::compare);
        assertEquals(expected, shown, "ObjBreakableWall_PieceFrames row 0");
    }

    // ---- Sonic hole --------------------------------------------------------------------------------------

    @Test
    void sonicHoleShowsOnceAnObjectControlledPlayerIsInsideItsBox() throws Exception {
        boot(CdStagePayload.R11A);
        int hx = sonic.getCentreX() + 0x200, hy = sonic.getCentreY() - 0x200;
        ObjSonicHole hole = add(new ObjSonicHole(spawn(hx, hy, 0x31, 0)));
        runner.stepIdleFrames(1);
        assertFalse(hole.isDisplayed(), "ObjSonicHole_Main returns before drawing (Tunnel Path/Main.asm:50-77)");
        sonic.setObjectControlled(true);           // tst.b oPlayerCtrl(a6)
        for (int[] outside : new int[][] {{0x20, 0}, {-0x21, 0}, {0, 0x20}, {0, -0x21}}) {
            sonic.setCentreX((short) (hx + outside[0]));
            sonic.setCentreY((short) (hy + outside[1]));
            runner.stepIdleFrames(1);
            sonic.setObjectControlled(true);
            assertFalse(hole.isDisplayed(), "outside the box " + outside[0] + "," + outside[1]);
        }
        sonic.setCentreX((short) (hx - 0x20));     // addi.w #$20,d0; bmi/cmpi.w #$40,d0; bcc (Tunnel Path/Main.asm:59-66)
        sonic.setCentreY((short) (hy + 0x1F));
        runner.stepIdleFrames(1);
        assertTrue(hole.isDisplayed(), "inside: dx -$20, dy +$1F");
    }

    @Test
    void sonicHoleIsHiddenWithoutPlayerControl() throws Exception {
        boot(CdStagePayload.R11A);
        int hx = sonic.getCentreX(), hy = sonic.getCentreY();
        ObjSonicHole hole = add(new ObjSonicHole(spawn(hx, hy, 0x31, 0)));
        runner.stepIdleFrames(3);
        assertFalse(hole.isDisplayed(), "tst.b oPlayerCtrl(a6); beq.s .End (Tunnel Path/Main.asm:52-53)");
    }

    // ---- spinning disc -----------------------------------------------------------------------------------

    @Test
    void spinningDiscCarriesALandedPlayerAroundItsCentreAndReleasesItWithAJump() throws Exception {
        boot(CdStagePayload.R12B);
        int dx = sonic.getCentreX(), dy = sonic.getCentreY() - 0x80;
        ObjSpinningDisc disc = add(new ObjSpinningDisc(spawn(dx, dy, 0x09, 0)));
        runner.stepIdleFrames(2);
        dropSonicAt(dx + 8, dy - 8 - 0x13 - 0x10);
        int frames = 0;
        while (!disc.holdsPlayer() && frames++ < 120) runner.stepIdleFrames(1);
        assertTrue(disc.holdsPlayer(), "SolidObject top contact: bset #0,oPlayerCtrl(a1) (Spinning Disc/Main.asm:55)");
        assertTrue(sonic.isObjectControlled());
        int angle = disc.rotationAngle();
        runner.stepIdleFrames(1);
        // addq.b #8,oPlayerRotAngle every frame (Main.asm:82).
        assertEquals((angle + 8) & 0xFF, disc.rotationAngle());
        // oX(a1) = oX(a0) + (dist * cos(angle)) >> 8 (Main.asm:83-91).
        int cos = com.openggf.physics.TrigLookupTable.cosHex(disc.rotationAngle());
        assertEquals((dx + ((disc.rotationDistance() * cos) >>> 8)) & 0xFFFF, sonic.getCentreX() & 0xFFFF);
        // A tapped button releases the player: ySpeed + sin($C0) * $680 >> 8 = -$680 (CheckJump, Main.asm:157-190).
        sonic.setYSpeed((short) 0);
        runner.stepFrame(false, false, false, false, true);
        assertFalse(sonic.isObjectControlled(), "clr.b oPlayerCtrl(a1)");
        assertEquals(-0x680, sonic.getYSpeed());
        assertTrue(sonic.getAir());
    }
}
