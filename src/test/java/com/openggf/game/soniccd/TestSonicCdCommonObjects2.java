package com.openggf.game.soniccd;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimal;
import com.openggf.game.soniccd.objects.common.CdBigRing;
import com.openggf.game.soniccd.objects.common.CdBoulder;
import com.openggf.game.soniccd.objects.common.CdCheckpoint;
import com.openggf.game.soniccd.objects.common.CdFloatBlock;
import com.openggf.game.soniccd.objects.common.CdFlower;
import com.openggf.game.soniccd.objects.common.CdGameOver;
import com.openggf.game.soniccd.objects.common.CdGoalPost;
import com.openggf.game.soniccd.objects.common.CdPlcQueue;
import com.openggf.game.soniccd.objects.common.CdResults;
import com.openggf.game.soniccd.objects.common.CdRobotGenerator;
import com.openggf.game.soniccd.objects.common.CdSignpost;
import com.openggf.game.soniccd.objects.common.CdTestBadnik;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the second common-object batch on stages loaded from the disc; each case
 * cites the routine it checks. Native comparisons: {@link TestSonicCdCommonObjectsNative} and
 * {@link TestSonicCdSolidObjectTrace}.
 */
class TestSonicCdCommonObjects2 {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
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

    private void boot(CdStagePayload payload) throws Exception {
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        sonic = CdFixtureReplay.boot(tempDir, disc, payload);
        runner = new HeadlessTestRunner(sonic);
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    private ObjectSpawn at(int dx, int dy, int id, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, id, subtype, 0, false, 0);
    }

    private CdLevel level() { return (CdLevel) GameServices.level().getCurrentLevel(); }

    @Test
    void boulderPushBitOnlyWhileMovingIntoIt() throws Exception {
        boot(CdStagePayload.R11A);
        // Boulder $10 wide: d1 = $1A; Sonic $19 left of centre is inside the left edge by 1.
        add(new CdBoulder(at(0x19, -3, 0x1B, 0)));
        runner.stepIdleFrames(2);
        // .PlayerLeft with xVel 0 is .StopPush: no push bit (Object Solid.asm:292-324).
        assertFalse(sonic.getPushing(), "standing still against the side clears bit 5");
        sonic.setXSpeed((short) 0x100);
        sonic.setGSpeed((short) 0x100);
        runner.stepFrame(false, false, false, true, false);
        // .HaltOnX: grounded, sets bit 5 and stops (:303-315).
        assertTrue(sonic.getPushing(), "moving into the side sets bit 5");
        assertEquals(0, sonic.getXSpeed());
        runner.stepIdleFrames(1);
        assertFalse(sonic.getPushing(), "with xVel 0 the next call is .StopPush again");
    }

    @Test
    void floatingBlockAppearsInThePresentOnlyWhileStoodOn() throws Exception {
        boot(CdStagePayload.R11A);
        CdFloatBlock block = add(new CdFloatBlock(at(0, 0x13 + 0xC - 0x10, 0x12, 0)));
        runner.stepIdleFrames(3);
        // ObjFloatBlock_Main present: stood on -> routine 6, oAnim 1 (Floating Block/Main.asm:64-71).
        assertTrue(sonic.isOnObject(), "TopSolidObject seats Sonic");
        assertEquals(0x12, GameServices.level().getObjectManager().getRidingObject(sonic).getSpawn().objectId());
        sonic.setYSpeed((short) -0x600);
        sonic.setAir(true);
        runner.stepIdleFrames(1);
        assertFalse(sonic.isOnObject(), "rising: TopSolidObject releases (Object Solid.asm:221-226)");
        assertFalse(block.isDestroyed());
    }

    @Test
    void floatingBlockFallsInTheFutureWhenStoodOn() throws Exception {
        boot(CdStagePayload.R11D);
        CdFloatBlock block = add(new CdFloatBlock(at(0, 0x13 + 0xC - 0x10, 0x12, 0)));
        runner.stepIdleFrames(2);
        int y = block.getY();
        runner.stepIdleFrames(1);
        // ObjFloatBlock_Fall: addq.w #2,oY(a0) (Floating Block/Main.asm:84-95).
        assertEquals(y + 2, block.getY());
    }

    @Test
    void checkpointActivationSavesThePlayerPositionAndSpinsTheBall() throws Exception {
        boot(CdStagePayload.R11A);
        CdCheckpoint post = add(new CdCheckpoint(at(0x40, 0, 0x13, 3)));
        runner.stepIdleFrames(1);
        assertEquals(1, live(0x13).size() - 1, "the ball is a second $13 (Checkpoint/Main.asm:92-94)");
        assertEquals(0xE3, post.getCollisionFlags(), "$C0|$23 before activation");
        assertTrue(post.usesS3kTouchSpecialPropertyResponse(),
                "$23 is Player_TouchSpecial's flag-only type, never a boss touch (Player Object Collision.asm:315-316)");
        GameServices.level().getLevelGamestate().setTimerFrames(6 * 3600L);
        post.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        CheckpointState state = (CheckpointState) GameServices.level().getCheckpointState();
        assertEquals(3, state.getLastCheckpointIndex(), "move.b oSubtype(a0),checkpoint");
        assertEquals(sonic.getCentreX(), state.getSavedX(), "savedX is the player's X (Checkpoint/Main.asm:19)");
        assertEquals(5 * 3600L, state.getSavedTimerFrames(), "time capped at 5:00 (Checkpoint/Main.asm:35-41)");
        assertEquals(0, post.getCollisionFlags());
        var ball = (CdCheckpoint.Ball) live(0x13).stream().filter(o -> o instanceof CdCheckpoint.Ball).findFirst().orElseThrow();
        int cx = post.getX();
        runner.stepIdleFrames(8);
        // ObjCheckpoint_Ball: angle +8 a frame, radius 8 around Y - 24 (Checkpoint/Main.asm:159-174).
        assertTrue(Math.abs(ball.getX() - cx) <= 8 && ball.getY() >= post.getY() - 32 && ball.getY() <= post.getY() - 16);
    }

    @Test
    void flowerSeedFallsTwoPixelsThenPlants() throws Exception {
        boot(CdStagePayload.R11A);
        CdFlower flower = add(new CdFlower(at(0x30, -0x40, 0x1F, 1)));
        runner.stepIdleFrames(1);
        int y = flower.getY();
        runner.stepIdleFrames(1);
        assertEquals(y + 2, flower.getY(), "ObjFlower_Seed .Fall (Flower/Main.asm:88-89)");
        runner.stepIdleFrames(60);
        // The planted script ends with $FC, then the growing script does too.
        runner.stepIdleFrames(40);
        assertEquals(3, flower.anim(), "AnimateObject advances planted -> growing -> fully grown");
        runner.stepIdleFrames(40);
        assertEquals(3, flower.anim(), "the grown flower loops");
    }

    @Test
    void monitorItemsDrawOneStaticPieceAndYellowDiagonalSpringSelectsItsTiles() throws Exception {
        boot(CdStagePayload.R11A);
        var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
        var sheet = provider.getSheet(CdObjectArtProvider.MONITOR_ITEM);
        int table = provider.entry().monitorMappings();
        for (int item = 0; item < 8; item++) {
            var frame = sheet.getFrame(item);
            assertEquals(1, frame.pieces().size(), "sprite flag bit 5 draws a single piece");
            int piece = table + (short) provider.image().u16(table + item * 2) + 1;
            assertEquals(provider.image().u16(piece + 2) & 0x7FF, frame.pieces().getFirst().tileIndex());
            assertEquals((byte) provider.image().u8(piece + 4), frame.pieces().getFirst().xOffset());
        }
        var spring = add(new com.openggf.game.soniccd.objects.common.CdSpring(at(0x40, -0x20, 0x0A, 0x0A)));
        runner.stepIdleFrames(1);
        var tileField = com.openggf.game.soniccd.objects.common.CdObject.class.getDeclaredField("tileWord");
        tileField.setAccessible(true);
        int tile = tileField.getInt(spring);
        assertEquals(provider.objectTileId(CdObjectArtProvider.TILE_ID_DIAGONAL_SPRING, 0) | 0x2000, tile);
        assertNotNull(provider.getSheet(CdObjectArtProvider.diagonalSpringKey(tile & ~0x2000)));
    }

    @Test
    void palmtreeRobotGeneratorUsesTheLastSectionsArtAndStageName() throws Exception {
        boot(CdStagePayload.R11A);
        var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
        int tile = com.openggf.game.soniccd.objects.common.CommonObjectArt.robotGeneratorTile(provider.image(),
                CdStagePayload.R11A, provider.entry().offset("robotGeneratorBaseTiles"));
        String key = com.openggf.game.soniccd.objects.ppz.PpzObjectArt.key(provider,
                com.openggf.game.soniccd.objects.ppz.PpzObjectArt.PPZ_GENERATOR, tile, 0x2000);
        assertNotNull(key);
        var expected = provider.vram().copy();
        // SectionInitPLCs entry 4 = PLC 7; PLC_Cam5_Full includes Art_RobotGenWithered.
        expected.loadPlc(provider.image(), com.openggf.game.soniccd.level.CdStageCatalogue.entry(
                CdStagePayload.R11A).plcLists(), 7);
        byte[] wanted = new byte[64], actual = new byte[64];
        var patterns = provider.getSheet(key).getPatterns();
        boolean nonblank = false;
        for (int i = 0; i < patterns.length; i++) {
            expected.tile((tile & 0x7FF) + i).copyInto(wanted, 0);
            patterns[i].copyInto(actual, 0);
            org.junit.jupiter.api.Assertions.assertArrayEquals(wanted, actual);
            for (byte pixel : actual) nonblank |= pixel != 0;
        }
        assertTrue(nonblank);
        assertEquals("Robot generator", new com.openggf.game.soniccd.objects.SonicCdObjectRegistry().getPrimaryName(0x2D));
    }

    @Test
    void resultsUploadDecimalBonusesAndScoreIntoTheirOwnSheets() throws Exception {
        boot(CdStagePayload.R11A);
        var results = add(new com.openggf.game.soniccd.objects.common.CdResults(1, 50000, 1200));
        // Hold the native wait condition without moving the camera.
        sonic.setX((short) (GameServices.camera().getX() + 400));
        for (int i = 0; i < 100; i++) results.update(i, sonic);
        for (var object : live(0x3A)) {
            for (int i = 0; i < 12; i++) object.update(i, sonic);
            object.appendRenderCommands(new java.util.ArrayList<>());
        }
        var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
        var tiles = provider.getSheet(com.openggf.game.soniccd.objects.common.CommonObjectArt.RESULTS_BAD).getPatterns();
        // Last score digit remains zero; the least significant bonus digit is also always zero.
        byte[] expected = new byte[128];
        var bytes = provider.image().slice(provider.entry().hudNumbersArt(), 64);
        var pattern = new com.openggf.level.Pattern();
        pattern.fromSegaFormat(java.util.Arrays.copyOfRange(bytes, 0, 32));
        pattern.copyInto(expected, 0);
        pattern.fromSegaFormat(java.util.Arrays.copyOfRange(bytes, 32, 64));
        pattern.copyInto(expected, 64);
        for (int cell : new int[]{0xB060 / 32 - 0x3C4 + 12, 0x8780 / 32 - 0x3C4 + 8,
                0x88C0 / 32 - 0x3C4 + 8}) {
            byte[] actual = new byte[128];
            tiles[cell].copyInto(actual, 0);
            tiles[cell + 1].copyInto(actual, 64);
            org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
        }
    }

    @Test
    void animalsLeaveTheBadFutureAndFlyInTheGoodFuture() throws Exception {
        boot(CdStagePayload.R11D);
        CdAnimal gone = add(new CdAnimal(at(0x20, -0x20, 0x24, 0)));
        runner.stepIdleFrames(1);
        assertTrue(gone.isDestroyed(), "CheckAnimalPrescence: future without goodFuture deletes");
        level().flags().goodFuture = true;
        CdAnimal flying = add(new CdAnimal(at(0x20, -0x20, 0x24, 0)));
        int x0 = flying.getX();
        runner.stepIdleFrames(3);
        assertFalse(flying.isDestroyed());
        // ObjFlyingAnimal_Move: x0 + cos/2 of an angle near 0 (Animal/Main.asm:109-118).
        assertTrue(flying.getX() > x0 + 0x70, "circles at x0 + cos(angle) / 2");
    }

    @Test
    void pastRobotGeneratorBreaksIntoTheGoodFuture() throws Exception {
        boot(CdStagePayload.R11B);
        CdRobotGenerator generator = add(new CdRobotGenerator(at(0x80, -0x20, 0x2D, 0)));
        int y0 = generator.getY();
        runner.stepIdleFrames(1);
        assertEquals(y0 - 0x10, generator.getY(), "subi.w #$10,oY(a0) (Robot Generator/Main.asm:85)");
        assertEquals(0xFA, generator.getCollisionFlags());
        assertTrue(generator.usesS3kTouchSpecialPropertyResponse(),
                "$3A is Player_TouchSpecial's roll-only flag type, never a boss touch (Player Object Collision.asm:319-320)");
        sonic.setAnimationId(CdSonicAnimation.ROLL.id());
        generator.onTouchResponse(sonic, null, 0);
        int score = GameServices.gameState().getScore();
        runner.stepIdleFrames(1);
        assertTrue(level().flags().goodFuture, "move.b #1,goodFuture (Robot Generator/Main.asm:102)");
        assertEquals(score + 1500, GameServices.gameState().getScore(), "AddPoints #$96");
        assertEquals(0, generator.getCollisionFlags());
        runner.stepIdleFrames(3);
        assertFalse(live(0x18).isEmpty(), "the explosion schedule's first record fires at tick 1");
    }

    @Test
    void timeWarpStarsTouchAsInvincibility() throws Exception {
        boot(CdStagePayload.R11A);
        var routines = (com.openggf.game.internal.PlayerStageRoutines) GameServices.module().getPhysicsProvider();
        assertFalse(routines.touchesAsInvincible(sonic));
        var timeTravel = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(GameServices.module());
        var s = timeTravel.capture();
        timeTravel.restore(new com.openggf.game.soniccd.timetravel.CdTimeTravel.Snapshot(s.timeZone(), s.warping(),
                s.warpDirection(), s.warpTimer(), true, s.pendingWarp(), s.warpReady(), s.goodFuture(),
                s.goodFutureFlags(), s.goodFutures(), s.projectorDestroyed(), s.loadedPayload(),
                s.nextEntry()));
        // tst.b timeWarp beside tst.b invincible in Player_TouchEnemy and Player_TouchHazard
        // (Player Object Collision.asm:130-134, 211-216).
        assertTrue(routines.touchesAsInvincible(sonic));
    }

    @Test
    void testBadnikBecomesAnExplosionInItsSlot() throws Exception {
        boot(CdStagePayload.R11A);
        CdTestBadnik badnik = add(new CdTestBadnik(at(0x60, -0x20, 0x06, 0)));
        runner.stepIdleFrames(1);
        int slot = badnik.getSlotIndex();
        badnik.onPlayerAttack(sonic, null);
        assertTrue(badnik.isDestroyed());
        var explosion = (AbstractObjectInstance) live(0x18).getFirst();
        assertEquals(slot, explosion.getSlotIndex(), ".KillEnemy rewrites the slot (Player Object Collision.asm:174-178)");
    }

    @Test
    void firstBadnikDisplaysOneHundredOnTheHud() throws Exception {
        boot(CdStagePayload.R11A);
        CdTestBadnik badnik = add(new CdTestBadnik(at(0x60, -0x20, 0x06, 0)));
        runner.stepIdleFrames(1);
        badnik.onPlayerAttack(sonic, null);
        runner.stepIdleFrames(1);

        assertEquals(100, GameServices.gameState().getScore());
        var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
        var tiles = provider.getSheet(CdObjectArtProvider.HUD).getPatterns();
        int firstCell = 0xB060 / 32 - (CdObjectArtProvider.HUD_TILE & 0x7FF);
        int[] digits = {-1, -1, -1, -1, 1, 0, 0};
        for (int cell = 0; cell < digits.length; cell++) {
            int digit = digits[cell];
            byte[] art = digit < 0 ? new byte[64]
                    : provider.image().slice(provider.entry().hudNumbersArt() + digit * 64, 64);
            for (int half = 0; half < 2; half++) {
                var pattern = new Pattern();
                pattern.fromSegaFormat(Arrays.copyOfRange(art, half * 32, (half + 1) * 32));
                byte[] expected = new byte[64];
                byte[] actual = new byte[64];
                pattern.copyInto(expected, 0);
                tiles[firstCell + cell * 2 + half].copyInto(actual, 0);
                assertArrayEquals(expected, actual, "SCORE cell " + cell + ", tile " + half);
            }
        }
    }

    @Test
    void levelScoreAwardsOneUpAtFiftyThousandPoints() throws Exception {
        boot(CdStagePayload.R11A);
        var game = GameServices.gameState();
        int startingLives = game.getLives();
        game.addScore(49_990);
        assertEquals(startingLives, game.getLives());
        game.addScore(10);
        assertEquals(startingLives + 1, game.getLives());
    }

    @Test
    void actResultsTallyAddsBothBonusesInPoints() throws Exception {
        boot(CdStagePayload.R11A);
        var results = add(new CdResults(1, 100, 100));
        sonic.setX((short) (GameServices.camera().getX() + 400));
        for (int frame = 0; frame < 100; frame++) results.update(frame, sonic);
        assertEquals(200, GameServices.gameState().getScore());
    }

    @Test
    void shieldAndStarsUseTheSharedPowerUpsWithCdArt() throws Exception {
        boot(CdStagePayload.R11A);
        sonic.giveShield();
        sonic.giveInvincibility(1320);
        runner.stepIdleFrames(1);
        var shield = (AbstractObjectInstance) sonic.getShieldObject();
        var stars = (AbstractObjectInstance) sonic.getInvincibilityObject();
        assertEquals(6, shield.getSlotIndex(), "objShieldSlot (Level/_Definitions.inc:137)");
        assertEquals(8, stars.getSlotIndex(), "objInvStar1Slot (Level/_Definitions.inc:139)");
        var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
        assertNotNull(provider.getSheet(com.openggf.level.objects.ObjectArtKeys.SHIELD));
        assertTrue(GameServices.module().hasTrailInvincibilityStars());
    }

    @Test
    void gameOverSlidesInOnlyWithoutLives() throws Exception {
        boot(CdStagePayload.R11A);
        CdGameOver ignored = add(new CdGameOver(at(0, 0, 0x3B, 0)));
        runner.stepIdleFrames(1);
        assertTrue(ignored.isDestroyed(), "lives left and no time over: .Destroy (Game Over/Main.asm:47-49)");
        GameServices.gameState().restoreSaveProgress(0, 0, List.of(), List.of());
        add(new CdGameOver(at(0, 0, 0x3B, 0)));
        runner.stepIdleFrames(3);
        assertEquals(2, live(0x3B).size(), "GAME and OVER (Game Over/Main.asm:51-83)");
    }

    @Test
    void goalPostSignpostAndResultsTallyTheBonuses() throws Exception {
        boot(CdStagePayload.R11A);
        CdGoalPost post = add(new CdGoalPost(at(-0x10, 0, 0x16, 0)));
        runner.stepIdleFrames(1);
        assertEquals(GameServices.camera().getX(), GameServices.camera().getMinX(), "leftBound = cameraX");
        GameServices.level().getLevelGamestate().setRings(12);
        GameServices.level().getLevelGamestate().setTimerFrames(20 * 60L);
        CdSignpost sign = add(new CdSignpost(at(-0x10, 0, 0x17, 0)));
        runner.stepIdleFrames(1 + 120 + 60);
        // StartResults: 0:20 -> index 1 -> 50000; rings * 100 (Level End/Main.asm:846-859).
        assertTrue(level().flags().ctrlLocked);
        assertEquals(0x808, level().flags().playerCtrlHold);
        assertEquals(1, live(0x3A).size(), "results spawned with FindObjSlot");
        assertFalse(post.isDestroyed());
        assertFalse(sign.isDestroyed());
        assertTrue(GameServices.gameState().isActCompletionSignalActive(), "hosted runs observe the native goal crossing");
    }

    @Test
    void bigRingNeedsFiftyRingsAndSendsThePlayerOffScreen() throws Exception {
        boot(CdStagePayload.R11A);
        CdBigRing ring = add(new CdBigRing(at(0, 0, 0x14, 0)));
        runner.stepIdleFrames(1);
        assertFalse(level().flags().specialStage, "fewer than 50 rings: despawn check only");
        GameServices.level().getLevelGamestate().setRings(50);
        int cameraX = GameServices.camera().getX();
        runner.stepIdleFrames(1);
        assertTrue(level().flags().specialStage);
        assertTrue(level().flags().scrollLock);
        assertEquals(cameraX + 336, sonic.getCentreX(), "addi.w #336,d0 (Level End/Main.asm:497)");
        assertEquals(1, live(0x14).stream().filter(o -> o != ring).count(), "the flash, subtype 1");
    }

    @Test
    void titleCardReleasesWhenPlcTwoHasDrained() throws Exception {
        boot(CdStagePayload.R11A);
        var image = ((CdObjectArtProvider) GameServices.level().getObjectRenderManager()
                .getArtProvider()).image();
        int drain = CdPlcQueue.framesToDrain(image, CdStagePayload.R11A, 2, CdPlcQueue.FAST_BATCH);
        int frames = 0;
        while (!level().flags().scrollLock && frames < 400) {
            runner.stepIdleFrames(1);
            frames++;
        }
        assertTrue(level().flags().ctrlLocked, "locked from LevelStart until the wait (Level/Main.asm:274)");
        runner.stepIdleFrames(drain - 1);
        assertTrue(level().flags().ctrlLocked, "the queue still holds PLC 2");
        runner.stepIdleFrames(1);
        assertFalse(level().flags().ctrlLocked);
        assertFalse(level().flags().scrollLock);
    }
}
