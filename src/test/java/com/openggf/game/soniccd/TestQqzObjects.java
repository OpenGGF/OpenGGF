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
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.qqz.ObjBreakWall;
import com.openggf.game.soniccd.objects.qqz.ObjBridge;
import com.openggf.game.soniccd.objects.qqz.ObjBridgePiece;
import com.openggf.game.soniccd.objects.qqz.ObjCirclePlatform;
import com.openggf.game.soniccd.objects.qqz.ObjConveyorSwitch;
import com.openggf.game.soniccd.objects.qqz.ObjKemusi;
import com.openggf.game.soniccd.objects.qqz.ObjKumoKumo;
import com.openggf.game.soniccd.objects.qqz.ObjKumoWeb;
import com.openggf.game.soniccd.objects.qqz.ObjLargeBreakWall;
import com.openggf.game.soniccd.objects.qqz.ObjNoroNoro;
import com.openggf.game.soniccd.objects.qqz.ObjNoroSpikes;
import com.openggf.game.soniccd.objects.qqz.ObjSasori;
import com.openggf.game.soniccd.objects.qqz.ObjSasoriGun;
import com.openggf.game.soniccd.objects.qqz.ObjSasoriMissile;
import com.openggf.game.soniccd.objects.qqz.ObjSinePlatform;
import com.openggf.game.soniccd.objects.qqz.ObjStalactite;
import com.openggf.game.soniccd.objects.qqz.QqzObjectArt;
import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
import com.openggf.game.soniccd.player.CdSonicAnimation;
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
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Quartz Quadrant objects on the loaded stages (their art, tables and terrain come
 * from the disc). Each case cites the routine it checks in {@code src/objects/r5/<name>.asm}; every expected
 * value is a constant or closed form from those routines, never the implementation's own output. The native
 * comparisons are {@link TestQqzObjectsAppearances} and the frontier table.
 */
class TestQqzObjects {
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
        sonic.setInvulnerableFrames(0x7000);   // keep the player out of the way of the hurt boxes
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

    private QqzScrollHandler handler() {
        return (QqzScrollHandler) GameServices.parallax().getHandler(
                SonicCdZoneRegistry.zoneIndex(CdStagePayload.R51A));
    }

    private void place(int x, int y) {
        sonic.setCentreX((short) x);
        sonic.setCentreYPreserveSubpixel((short) y);
    }

    // ---- art ---------------------------------------------------------------------------------------

    @Test
    void sheetsComeFromTheStageFile() throws Exception {
        boot(CdStagePayload.R51A);
        CdObjectArtProvider art = art();
        // gfx_lists_1a.asm: PLC 1 holds the bridge ($6000), PLC 2 the badniks (Noro Noro $7200 = tile $390).
        for (String[] kind : new String[][] {{QqzObjectArt.BRIDGE, "" + QqzObjectArt.BRIDGE_TILE},
                {QqzObjectArt.NORO1, "" + QqzObjectArt.NORO_TILE},
                {QqzObjectArt.KEMUSI, "" + QqzObjectArt.KEMUSI_TILE},
                {QqzObjectArt.SASORI1, "" + QqzObjectArt.SASORI_TILE},
                {QqzObjectArt.KUMO1, "" + QqzObjectArt.KUMO_TILE},
                {QqzObjectArt.LARGE_BREAK_WALL, "" + QqzObjectArt.LARGE_BREAK_WALL_TILE},
                {QqzObjectArt.CONVEYOR_SWITCH, "" + QqzObjectArt.CONVEYOR_SWITCH_TILE},
                {QqzObjectArt.SINE_PLATFORM, "" + QqzObjectArt.SINE_PLATFORM_TILE},
                {QqzObjectArt.STALACTITE, "" + QqzObjectArt.STALACTITE_TILE}}) {
            String key = QqzObjectArt.key(art, kind[0], Integer.parseInt(kind[1]), 0);
            assertNotNull(key, kind[0]);
            boolean ink = false;
            for (var pattern : art.getSheet(key).getPatterns()) {
                for (int i = 0; i < 64 && !ink; i++) ink = pattern.getPixel(i % 8, i / 8) != 0;
            }
            assertTrue(ink, kind[0] + " sheet is blank");
        }
    }

    // ---- Noro Noro ---------------------------------------------------------------------------------

    @Test
    void noroNoroFallsToTheFloorWalksAndMakesSpikesWhenThePlayerIsNear() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;                    // outside the player's +-$78 box
        ObjNoroNoro noro = add(new ObjNoroNoro(spawn(x0, sonic.getCentreY() - 0x30, 0x20, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0x2F, noro.getCollisionFlags(), "move.b #$2F,obj.collide_type(a0) (objects/r5/noro_noro.asm:33)");
        int steps = 0;
        int y = noro.getY();
        while (noro.routine() < 4 && steps++ < 120) {
            runner.stepIdleFrames(1);
            if (noro.routine() < 4) assertEquals(y + 1, noro.getY(), "addi.l #$10000,obj.y(a0) (objects/r5/noro_noro.asm:44)");
            y = noro.getY();
        }
        assertEquals(4, noro.routine());
        assertEquals(0, com.openggf.physics.ObjectTerrainUtils.checkFloorDist(noro.getX(), noro.getY(), 0xF).distance(), "the radius point sits on the floor");
        // Walk: -$4000 a frame (objects/r5/noro_noro.asm:35, 56-58), so eight frames move two pixels left.
        int x = noro.getX();
        runner.stepIdleFrames(8);                               // the player is far away: no spikes
        assertEquals(x - 2, noro.getX());
        assertTrue(live(ObjNoroSpikes.class).isEmpty());
        // The player inside the +-$78 box: animation 1 and a spikes child $14 above (objects/r5/noro_noro.asm:64-101, 126-138).
        place(noro.getX() + 0x40, noro.getY());
        runner.stepIdleFrames(1);
        List<ObjectInstance> spikes = live(ObjNoroSpikes.class);
        assertEquals(1, spikes.size());
        assertEquals(0xB0, ((ObjNoroSpikes) spikes.getFirst()).getCollisionFlags());
        assertEquals(0x20, spikes.getFirst().getSpawn().objectId());
        assertEquals(0xFF, spikes.getFirst().getSpawn().subtype());
        assertEquals(noro.getY() - 0x14, spikes.getFirst().getY());
        // The player leaves: DeleteOtherObject.
        place(noro.getX() + 0x200, noro.getY());
        runner.stepIdleFrames(2);
        assertTrue(live(ObjNoroSpikes.class).isEmpty());
    }

    @Test
    void noroNoroTurnsAfterFiftyPixels() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        ObjNoroNoro noro = add(new ObjNoroNoro(spawn(x0, sonic.getCentreY() - 0x10, 0x20, 0)));
        int steps = 0;
        while (noro.routine() < 4 && steps++ < 120) runner.stepIdleFrames(1);
        assertEquals(4, noro.routine());
        // Out to |dx| = $50 at a quarter pixel a frame: $140 frames, then neg.l oVar2C and bchg #0 (objects/r5/noro_noro.asm:61-70, 118-122).
        int flip = noro.spriteFlip() & 1;
        steps = 0;
        while ((noro.spriteFlip() & 1) == flip && steps++ < 400) runner.stepIdleFrames(1);
        assertTrue(steps >= 0x100 && steps <= 0x150, "turned after " + steps + " frames");
        assertEquals(x0 - 0x50, noro.getX(), 1);
    }

    // ---- break walls -------------------------------------------------------------------------------

    @Test
    void largeBreakWallBreaksIntoEightPiecesWhenRolledInto() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjLargeBreakWall wall = add(new ObjLargeBreakWall(spawn(x0, y0, 0x2B, 0)));
        runner.stepIdleFrames(2);
        assertEquals(0xEE, wall.getCollisionFlags(), "move.b #$EE,obj.collide_type(a0) (objects/r5/large_break_wall.asm:25)");
        // A walking player does not break it; a rolling one does (loc_20726E: cmpi.b #2,obj.anim_id).
        sonic.setAnimationId(CdSonicAnimation.WALK);
        wall.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        assertEquals(2, wall.routine());
        sonic.setAnimationId(CdSonicAnimation.ROLL);
        wall.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        List<ObjectInstance> pieces = live(ObjLargeBreakWall.class);
        assertEquals(8, pieces.size(), "dbf d6 with d6 = 7: eight pieces including the wall (objects/r5/large_break_wall.asm:58-92)");
        assertEquals(0, wall.getCollisionFlags());
        // unk_20EBA2 frames and word_20EBAA offsets: positions accumulate from the first piece's offset.
        List<Integer> frames = new ArrayList<>();
        for (ObjectInstance piece : pieces) frames.add(((ObjLargeBreakWall) piece).mapFrame());
        assertEquals(new TreeSet<>(List.of(2, 5, 3, 5, 4, 5, 7, 5)).toString(), new TreeSet<>(frames).toString());
        assertEquals(List.of(2, 3, 4, 5, 5, 5, 5, 7), frames.stream().sorted().toList());
        // Every piece follows x = X0 - 8 + dx + k * vx, y = y0 - $18 + dy + k * vy + $4000 * k(k+1)/2 (16.16), for k updates.
        int[][] offsets = {{-8, -0x18}, {0x10, 0}, {0, 0x10}, {0x10, 0x10}, {0, 0x20}, {0x10, 0x20}, {0, 0x30}, {0x10, 0x30}};
        int[][] velocities = {{-0x12D2D, -0x20000}, {0x12D2D, -0x20000}, {-0x28000, -0x18000}, {0x28000, -0x18000},
                {-0x50000, 0x8000}, {0x50000, 0x8000}, {-0x28000, 0x10000}, {0x28000, 0x10000}};
        int[] pieceFrames = {2, 5, 3, 5, 4, 5, 7, 5};
        runner.stepIdleFrames(10);
        // After 10 more frames each piece has made 10 or 11 updates (the ones in later slots ran on the break frame).
        for (ObjectInstance o : live(ObjLargeBreakWall.class)) {
            ObjLargeBreakWall piece = (ObjLargeBreakWall) o;
            int index = matchPiece(piece, x0, y0, offsets, velocities, pieceFrames);
            assertTrue(index >= 0, "piece at " + piece.getX() + "," + piece.getY() + " frame " + piece.mapFrame()
                    + " follows the table");
        }
    }

    /** The piece index whose closed form matches within 9 to 12 updates, or -1. */
    private static int matchPiece(ObjLargeBreakWall piece, int x0, int y0, int[][] offsets, int[][] velocities,
                                  int[] frames) {
        for (int i = 0; i < 8; i++) {
            if (frames[i] != piece.mapFrame()) continue;
            for (int k = 9; k <= 12; k++) {
                // The first piece is the wall at its own offset; a copy takes that position, then adds its own offset.
                int ox = i == 0 ? 0 : offsets[i][0];
                int oy = i == 0 ? 0 : offsets[i][1];
                long xLong = ((long) (x0 + offsets[0][0] + ox) << 16) + (long) k * velocities[i][0];
                long yLong = ((long) (y0 + offsets[0][1] + oy) << 16) + (long) k * velocities[i][1]
                        + 0x4000L * k * (k + 1) / 2;
                if (piece.getX() == (int) (xLong >> 16) && piece.getY() == (int) (yLong >> 16)) return i;
            }
        }
        return -1;
    }

    @Test
    void breakWallBreaksIntoThreePiecesThrownAwayFromTheRunDirection() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjBreakWall wall = add(new ObjBreakWall(spawn(x0, y0, 0x32, 1)));
        runner.stepIdleFrames(2);
        assertEquals(0xFC, wall.getCollisionFlags(), "move.b #$FC,obj.collide_type(a0) (objects/r5/break_wall.asm:25)");
        assertEquals(1, wall.mapFrame(), "frame = subtype & 1 (objects/r5/break_wall.asm:28-32)");
        sonic.setAnimationId(CdSonicAnimation.ROLL);
        sonic.setXSpeed((short) 0x600);
        wall.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        List<ObjectInstance> pieces = live(ObjBreakWall.class);
        assertEquals(3, pieces.size(), "moveq #2,d6 (objects/r5/break_wall.asm:77)");
        List<Integer> frames = pieces.stream().map(p -> ((ObjBreakWall) p).mapFrame()).sorted().toList();
        assertEquals(List.of(5, 5, 5), frames, "an odd subtype takes byte_20F8C2 + 3 (objects/r5/break_wall.asm:68-69)");
        // The player ran right, so the pieces go left: x velocities -$2F684, -$3DA12, -$2F684 (dword_20F8C8).
        for (ObjectInstance o : pieces) assertTrue(o.getX() <= x0, "thrown left");
        assertEquals(0, wall.getCollisionFlags());
    }

    // ---- Kemusi -----------------------------------------------------------------------------------

    @Test
    void kemusiMakesFourSegmentsLandsTogetherAndCrawls() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        ObjKemusi head = add(new ObjKemusi(spawn(x0, sonic.getCentreY() - 0x20, 0x22, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> all = group(head);
        assertEquals(4, all.size(), "the head and three SpawnObjectAfter segments (objects/r5/kemusi.asm:90-118)");
        List<Integer> xs = all.stream().map(ObjectInstance::getX).sorted().toList();
        assertEquals(List.of(x0, x0 + 0xC, x0 + 0x18, x0 + 0x24), xs);
        assertEquals(0x33, head.getCollisionFlags());
        // Segments of subtype 0 carry the hurt box $B4 (objects/r5/kemusi.asm:78-80).
        assertEquals(3, all.stream().filter(o -> o != head && ((ObjKemusi) o).getCollisionFlags() == 0xB4).count());
        int steps = 0;
        while (head.routine() < 8 && steps++ < 200) runner.stepIdleFrames(1);
        assertEquals(8, head.routine(), "all four landed, then the head started them (objects/r5/kemusi.asm:155-181)");
        for (ObjectInstance o : all) assertEquals(8, ((ObjKemusi) o).routine());
        // One cycle is $24 frames (objects/r5/kemusi.asm:174-181, 204-209); the head toggles its frame once per cycle.
        int frame = head.mapFrame();
        runner.stepIdleFrames(0x25);
        assertEquals(frame ^ 1, head.mapFrame());
        // A segment that is no longer object $22 removes the others (objects/r5/kemusi.asm:8-18).
        ((ObjKemusi) all.get(2)).setDestroyed(true);
        runner.stepIdleFrames(2);
        assertTrue(group(head).isEmpty());
    }

    /** The four segments made here, not the stage's own caterpillars (they hang on a ledge far above). */
    private List<ObjectInstance> group(ObjKemusi head) {
        int floorY = sonic.getCentreY();
        return live(ObjKemusi.class).stream().filter(o -> Math.abs(o.getY() - floorY) < 0x40).toList();
    }

    // ---- conveyor switch ---------------------------------------------------------------------------

    @Test
    void conveyorSwitchTogglesTheReverseFlagAndAnimates() throws Exception {
        boot(CdStagePayload.R51A);
        assertFalse(handler().conveyorReverse(), "clr.b r5_conveyor_reverse (src/r5/main.asm:24)");
        int x0 = sonic.getCentreX();
        int y0 = sonic.getCentreY();
        ObjConveyorSwitch panel = add(new ObjConveyorSwitch(spawn(x0, y0, 0x31, 0)));
        runner.stepIdleFrames(1);
        // |dy| < $10 and |dx| < 8 (objects/r5/conveyor_switch.asm:36-62): toggled on the first update.
        assertTrue(handler().conveyorReverse());
        assertEquals(4, panel.routine());
        int frame1At = -1;
        for (int i = 1; i <= 16 && panel.routine() == 4; i++) {
            runner.stepIdleFrames(1);
            if (panel.mapFrame() == 1 && frame1At < 0) frame1At = i;
        }
        assertEquals(6, panel.routine(), "after 14 frames the panel waits for the player to leave");
        assertEquals(2, panel.mapFrame(), "the settled frame is 2 while reversed (objects/r5/conveyor_switch.asm:99-103)");
        assertTrue(frame1At > 0, "frame 1 at the 7th frame");
        // Standing on it does not press it again; leaving and coming back toggles it back.
        runner.stepIdleFrames(3);
        assertTrue(handler().conveyorReverse());
        place(x0 + 0x80, y0);
        runner.stepIdleFrames(1);
        assertEquals(2, panel.routine());
        assertTrue(handler().conveyorReverse());
        place(x0, y0);
        runner.stepIdleFrames(1);
        assertFalse(handler().conveyorReverse());
    }

    // ---- platforms ---------------------------------------------------------------------------------

    @Test
    void circlePlatformMakesEightPlatformsOnAnNinetySixPixelCircle() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x60;
        add(new ObjCirclePlatform(spawn(x0, y0, 0x27, 0)));
        runner.stepIdleFrames(2);
        List<ObjectInstance> platforms = live(ObjCirclePlatform.class);
        assertEquals(8, platforms.size(), "four quadrants of two (objects/r5/circle_platform.asm:23-69)");
        // Present speed $C0 (CirclePtfmProperties). The head ran once (angle byte $00 after the move: the axis
        // point x0 + $60); a platform made in a later slot also ran on the creation frame, so each is at angle
        // start + k * $C0 for k = 1 or 2. Every one lies on the radius-$60 circle (sin and cos times 0.375 * 256).
        boolean head = false;
        for (ObjectInstance p : platforms) {
            double radius = Math.hypot(p.getX() - x0, p.getY() - y0);
            assertTrue(radius >= 95 && radius <= 97.5, "radius " + radius + " at " + p.getX() + "," + p.getY());
            head |= p.getX() == x0 + 0x60 && p.getY() == y0;
        }
        assertTrue(head, "the first platform is the one at angle 0 after one move");
    }

    @Test
    void sinePlatformMakesTwoPlatformsThatSlideLeftHalfAPixelAFrame() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x60;
        add(new ObjSinePlatform(spawn(x0, y0, 0x29, 0)));
        runner.stepIdleFrames(2);
        List<ObjectInstance> platforms = live(ObjSinePlatform.class);
        assertEquals(2, platforms.size());
        // Made at x0 + $40 and x0 - $40; each moved $8000 a frame for one update (the first slot) or two (a platform
        // made in a later slot also ran on the creation frame): x * 65536 + sub = start * 65536 - k * $8000.
        TreeSet<Long> expected = new TreeSet<>();
        for (int start : new int[] {x0 + 0x40, x0 - 0x40}) {
            for (int k : new int[] {1, 2}) expected.add(((long) start << 16) - (long) k * 0x8000);
        }
        for (ObjectInstance p : platforms) {
            long xLong = ((long) p.getX() << 16) | ((ObjSinePlatform) p).xSubpixel();
            assertTrue(expected.contains(xLong), "x " + Long.toHexString(xLong));
            // Y stays within the cosine amplitude 0.375 * 256 = 96 of the origin (objects/r5/sine_platform.asm:112-123).
            assertTrue(Math.abs(p.getY() - y0) <= 0x60, "y " + p.getY());
        }
        runner.stepIdleFrames(100);
        for (ObjectInstance p : live(ObjSinePlatform.class)) {
            long xLong = ((long) p.getX() << 16) | ((ObjSinePlatform) p).xSubpixel();
            assertTrue(xLong % 0x8000 == 0, "half-pixel steps");
        }
    }

    // ---- stalactite --------------------------------------------------------------------------------

    @Test
    void stalactiteFallsWhenThePlayerIsBelowAndHurts() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x90;
        ObjStalactite spike = add(new ObjStalactite(spawn(x0, y0, 0x25, 0)));
        place(x0 + 0x40, sonic.getCentreY());
        runner.stepIdleFrames(3);
        assertEquals(0, spike.getCollisionFlags(), "waits until the player is under it");
        assertEquals(y0, spike.getY());
        // sub_20DE9A: py - y - $18 < $68 and px - x + 8 < $10.
        place(x0, y0 + 0x18 + 0x30);
        runner.stepIdleFrames(1);
        assertEquals(4, spike.routine());
        assertEquals(0xB9, spike.getCollisionFlags(), "move.b #$B9,obj.collide_type(a0) (objects/r5/stalactite.asm:46)");
        int yBefore = spike.getY();
        runner.stepIdleFrames(4);
        // Velocity 0, $4000, $8000, $C000 added in turn (objects/r5/stalactite.asm:96-101): 0.75 pixel in four frames.
        assertTrue(spike.getY() >= yBefore, "falling");
    }

    @Test
    void thisLanesNullIdsDeleteThemselvesInTheBossAct() throws Exception {
        boot(CdStagePayload.R53C);
        ObjStalactite spike = add(new ObjStalactite(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY(), 0x25, 0)));
        runner.stepIdleFrames(2);
        assertTrue(spike.isDestroyed(), "$25 is ObjNull in r5/object_index_3.asm: move.b #0,(a0)");
        ObjNoroNoro noro = add(new ObjNoroNoro(spawn(sonic.getCentreX() + 0x100, sonic.getCentreY(), 0x20, 0)));
        runner.stepIdleFrames(2);
        assertFalse(noro.isDestroyed(), "$20 is the boss lane's");
        assertEquals(0, noro.routine(), "and this lane's Noro Noro does nothing there");
        assertEquals(0, noro.getCollisionFlags());
    }

    // ---- bridge ------------------------------------------------------------------------------------

    @Test
    void bridgeMakesEightPiecesInSlotOrderLeftToRight() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x100;
        int y0 = sonic.getCentreY() - 0x40;
        ObjBridge bridge = add(new ObjBridge(spawn(x0, y0, 0x2F, 0)));
        runner.stepIdleFrames(2);
        List<ObjectInstance> pieces = live(ObjBridgePiece.class);
        assertEquals(8, pieces.size());
        List<Integer> xs = pieces.stream().sorted((a, b) -> Integer.compare(((AbstractObjectInstance) a).getSlotIndex(),
                ((AbstractObjectInstance) b).getSlotIndex())).map(ObjectInstance::getX).toList();
        // objects/r5/bridge.asm:92: x + $30 ... x - $40 for d1 = 0..7, spawned for d1 = 7 down to 0: left to right.
        assertEquals(List.of(x0 - 0x40, x0 - 0x30, x0 - 0x20, x0 - 0x10, x0, x0 + 0x10, x0 + 0x20, x0 + 0x30), xs);
        for (ObjectInstance p : pieces) assertEquals(y0, p.getY());
        assertEquals(1, bridge.mapFrame(), "move.b #1,obj.sprite_frame(a0) (objects/r5/bridge.asm:49)");
    }

    // ---- Kumo Kumo and Sasori ----------------------------------------------------------------------

    @Test
    void kumoKumoSpitsAWebTowardThePlayerAndTheWebHoldsHim() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x40;
        ObjKumoKumo spider = add(new ObjKumoKumo(spawn(x0, sonic.getCentreY() - 0x20, 0x23, 0)));
        int steps = 0;
        while (live(ObjKumoWeb.class).isEmpty() && steps++ < 600) {
            place(sonic.getCentreX(), spider.getY());
            runner.stepIdleFrames(1);
        }
        List<ObjectInstance> webs = live(ObjKumoWeb.class);
        assertEquals(1, webs.size(), "KumoKumoObject_0_RoutineC makes one web (objects/r5/kumo_kumo.asm:151-181)");
        ObjKumoWeb web = (ObjKumoWeb) webs.getFirst();
        assertEquals(0xF7, web.getCollisionFlags());
        assertEquals(0x23, web.getSpawn().objectId());
        assertEquals(0xFF, web.getSpawn().subtype());
        assertEquals(spider.getY(), web.getY());
        int apart = Math.abs(web.getX() - spider.getX());
        assertTrue(apart == 0x18 || apart == 0x1B, "$18 pixels either side, plus $30000 if it also ran on the creation frame: " + apart);
        int x = web.getX();
        runner.stepIdleFrames(1);
        assertEquals(3, Math.abs(web.getX() - x), "$30000 a frame");
        // A touch catches the player for $78 frames (objects/r5/kumo_kumo.asm:187-229). The web is clear of the spider first,
        // whose own enemy box would hurt a player without invulnerability.
        runner.stepIdleFrames(12);
        sonic.setInvulnerableFrames(0);
        place(web.getX(), web.getY());
        web.onTouchResponse(sonic, null, 0);
        sonic.setXSpeed((short) 0x400);
        runner.stepIdleFrames(1);
        assertEquals(0, sonic.getXSpeed(), "clr.w obj.x_speed(a1)");
        int held = 0;
        while (!web.isDestroyed() && held++ < 200) runner.stepIdleFrames(1);
        assertTrue(held >= 0x76 && held <= 0x7A, "held " + held + " frames");
    }

    @Test
    void sasoriPatrolsWithAGunAndShootsAMissileAtAPlayerInFront() throws Exception {
        boot(CdStagePayload.R51A);
        int x0 = sonic.getCentreX() + 0x60;
        ObjSasori sasori = add(new ObjSasori(spawn(x0, sonic.getCentreY() - 0x20, 0x21, 0)));
        int steps = 0;
        while (sasori.routine() < 4 && steps++ < 120) runner.stepIdleFrames(1);
        assertEquals(4, sasori.routine());
        assertEquals(0x31, sasori.getCollisionFlags());
        runner.stepIdleFrames(1);                                // the first patrol frame places the gun
        List<ObjectInstance> guns = live(ObjSasoriGun.class);
        assertEquals(1, guns.size());
        assertEquals(sasori.getY() - 0x10, guns.getFirst().getY() + 0, 1, "the gun rides 16 above the body");
        int x = sasori.getX();
        runner.stepIdleFrames(4);
        assertEquals(x - 4, sasori.getX(), "-$10000 a frame for subtype 0 (objects/r5/sasori.asm:56-60, 104-106)");
        // The player ahead, inside the window: the firing sequence runs and a missile is made (objects/r5/sasori.asm:130-242).
        place(sasori.getX() - 0x30, sasori.getY());
        steps = 0;
        while (live(ObjSasoriMissile.class).isEmpty() && steps++ < 120) {
            place(sasori.getX() - 0x30, sasori.getY());
            runner.stepIdleFrames(1);
        }
        List<ObjectInstance> missiles = live(ObjSasoriMissile.class);
        assertEquals(1, missiles.size());
        assertEquals(0xB2, ((ObjSasoriMissile) missiles.getFirst()).getCollisionFlags());
        int missileX = missiles.getFirst().getX();
        runner.stepIdleFrames(2);
        assertEquals(6, missileX - missiles.getFirst().getX(), "-$30000 a frame");
    }
}
