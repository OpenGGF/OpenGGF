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
import com.openggf.game.soniccd.objects.ssz.ObjBooster;
import com.openggf.game.soniccd.objects.ssz.ObjJumpSpikeBall;
import com.openggf.game.soniccd.objects.ssz.ObjKabasira;
import com.openggf.game.soniccd.objects.ssz.ObjKanabun;
import com.openggf.game.soniccd.objects.ssz.ObjLamp;
import com.openggf.game.soniccd.objects.ssz.ObjMesh;
import com.openggf.game.soniccd.objects.ssz.ObjSpringLauncher;
import com.openggf.game.soniccd.objects.ssz.ObjSwapPlatform;
import com.openggf.game.soniccd.objects.ssz.ObjPathSwap;
import com.openggf.game.soniccd.objects.ssz.ObjSearchlight;
import com.openggf.game.soniccd.objects.ssz.ObjSparks;
import com.openggf.game.soniccd.objects.ssz.SszStageLayer;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Stardust Speedway objects on the loaded R71A stage (object art and tables come from
 * the disc). Every expected value is derived by hand from the cited routine in {@code objects/r7/*.asm}
 * (scddisasm pin 1ecc5f3af), never from the implementation's own output; the native comparisons are
 * {@link TestSszObjectsAppearances} and {@link TestSszObjectsPictures}. Objects are stepped by calling
 * {@code update} directly, so each count below is a count of that object's executions.
 */
class TestSszObjects {
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
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R71A),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R71A));
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

    /** Objects with the ID that are live now and not in {@code before} (the stage's own placements are excluded). */
    private List<ObjectInstance> since(List<ObjectInstance> before, int objectId) {
        return live(objectId).stream().filter(o -> !before.contains(o)).toList();
    }

    private SszStageLayer layer() {
        return SszStageLayer.of((CdLevel) GameServices.level().getCurrentLevel());
    }

    private void settleOnGround() {
        int frames = 0;
        while (sonic.getAir() && frames++ < 240) runner.stepIdleFrames(1);
        assertFalse(sonic.getAir(), "Sonic stands on the R71A start ground");
    }

    private ObjectSpawn at(int objectId, int dx, int dy, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, objectId, subtype, 0, false, 0);
    }

    @Test
    void boosterThrowsAGroundedPlayerAwayFromItself() {
        settleOnGround();
        int x0 = sonic.getCentreX() - 8;
        int y0 = sonic.getCentreY();
        // The pad is left of Sonic (obj.x < player x): loc_20C396 throws him left at -$1000 with the same ground
        // speed and the left-facing bit (launch.asm:1058-1068); the sparks (ID 9) appear six pixels up and $10
        // right, flipped, and a subtype-1 copy (ID 8) is the falling piece.
        List<ObjectInstance> boosters = live(8);
        List<ObjectInstance> sparksBefore = live(9);
        ObjBooster booster = add(new ObjBooster(new ObjectSpawn(x0, y0, 8, 0, 0, false, 0)));
        booster.update(1, sonic);
        assertEquals(4, booster.routine(), "the hit adds 2 to routine 2 (:1055)");
        assertEquals(-0x1000, sonic.getXSpeed());
        assertEquals(-0x1000, sonic.getGSpeed());
        assertEquals(Direction.LEFT, sonic.getDirection());
        assertEquals(1, since(sparksBefore, 9).size(), "sparks");
        ObjectInstance sparks = since(sparksBefore, 9).getFirst();
        assertEquals(x0 + 0x10, sparks.getX());
        assertEquals(y0 - 6, sparks.getY());
        assertEquals(2, since(boosters, 8).size(), "the pad and its falling piece");
        // The pad slides +$800 (8 pixels) for six frames, reverses, and slides back: six more moves reach the rest X, and
        // the same run's compare against oVar2C returns it to routine 2 (routine 4, :970-991).
        for (int i = 0; i < 6; i++) booster.update(1, sonic);
        assertEquals(x0 + 48, booster.getX());
        assertEquals(4, booster.routine());
        for (int i = 0; i < 5; i++) booster.update(1, sonic);
        assertEquals(x0 + 8, booster.getX());
        assertEquals(4, booster.routine());
        booster.update(1, sonic);
        assertEquals(x0, booster.getX());
        assertEquals(2, booster.routine(), "x == oVar2C: subq.b #2,oRoutine (:983-989)");
    }

    @Test
    void boosterPieceFallsAndDriftsTowardTheThrownSide() {
        settleOnGround();
        int x0 = sonic.getCentreX() - 8;
        int y0 = sonic.getCentreY();
        List<ObjectInstance> boosters = live(8);
        ObjBooster pad = add(new ObjBooster(new ObjectSpawn(x0, y0, 8, 0, 0, false, 0)));
        pad.update(1, sonic);
        ObjBooster piece = (ObjBooster) since(boosters, 8).stream().filter(o -> o != pad).findFirst().orElseThrow();
        piece.update(1, sonic);
        // Routine0: frame 0, oYVel -$800, routine 6 (:956-961); Routine6: oYVel += $40 then MoveObject:
        // y moves -$7C0/256 = -7.75 pixels (floor: y0 - 8), x moves +$200/256 = +2 pixels (:994-995, :1063).
        assertEquals(6, piece.routine());
        assertEquals(0, piece.mapFrame());
        assertEquals(y0 - 8, piece.getY());
        assertEquals(x0 + 2, piece.getX());
    }

    @Test
    void boosterIgnoresAnAirbornePlayer() {
        settleOnGround();
        sonic.setAir(true);
        ObjBooster booster = add(new ObjBooster(new ObjectSpawn(sonic.getCentreX() - 8, sonic.getCentreY(), 8, 0, 0, false, 0)));
        List<ObjectInstance> sparksBefore = live(9);
        booster.update(1, sonic);
        assertEquals(2, booster.routine(), "sub_20C33A: btst #1,obj.flags(a2) returns no hit (:1022-1025)");
        assertEquals(0, since(sparksBefore, 9).size());
    }

    @Test
    void sparksPlayTheirScriptAndDelete() {
        ObjSparks sparks = add(new ObjSparks(at(9, 0x40, 0, 0)));
        // Ani_Sparks: delay 3, frames 0 1 0 1, $FC (the bytes at the table: 03 00 01 00 01 FC); a frame lasts
        // four runs. $FC raises the routine on run 17 and Routine4 deletes on run 18 (sparks.asm:25-32).
        int[] frames = {0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1};
        for (int run = 1; run <= 16; run++) {
            sparks.update(run, sonic);
            assertEquals(frames[run - 1], sparks.mapFrame(), "run " + run);
        }
        sparks.update(17, sonic);
        assertEquals(4, sparks.routine());
        assertFalse(sparks.isDestroyed());
        sparks.update(18, sonic);
        assertTrue(sparks.isDestroyed());
    }

    @Test
    void meshPriorityFollowsTheStageLayer() {
        ObjMesh mesh = add(new ObjMesh(at(4, 0x80, 0, 0)));
        mesh.update(1, sonic);
        assertEquals(4, mesh.getPriorityBucket(), "layer 0: move.b #4,oPriority (mesh.asm:26)");
        layer().toggle();
        mesh.update(2, sonic);
        assertTrue(mesh.getPriorityBucket() < 4, "layer 1: move.b #0,oPriority (:29)");
    }

    @Test
    void pathSwapTogglesTheLayerFlashesAndIdles() {
        ObjPathSwap swap = add(new ObjPathSwap(at(0x1D, 0, 0, 0)));
        assertFalse(layer().isSet());
        swap.update(1, sonic);
        // Overlap in both axes (radii 9 + $18 and $13 + $18): eori.b #1,stage_layer, var_2a = $3C, routine 4 (:50-76).
        assertTrue(layer().isSet());
        assertEquals(4, swap.routine());
        assertEquals(0x3C, swap.cooldownFrames());
        // PathSwapAnims: delay 4, frames 0 1 2 3 4 3 2 1 0, $FC: a frame lasts five runs, frame i is first shown on
        // run 2 + 5i, the $FC arrives on run 47 and Routine6 settles on run 48 (path_swap.asm:106-123).
        int[] shown = {0, 1, 2, 3, 4, 3, 2, 1, 0};
        for (int run = 2; run <= 47; run++) {
            swap.update(run, sonic);
            int index = (run - 2) / 5;
            if (index < shown.length) assertEquals(shown[index], swap.mapFrame(), "run " + run);
        }
        assertEquals(6, swap.routine());
        swap.update(48, sonic);
        assertEquals(2, swap.routine(), "subq.b #4,oRoutine");
        assertTrue(layer().isSet(), "settling does not touch the layer");
        // The cooldown counts down in routine 2 only: runs 49..108 consume $3C, run 109 sees the player again.
        for (int run = 49; run <= 108; run++) swap.update(run, sonic);
        assertTrue(layer().isSet());
        swap.update(109, sonic);
        assertFalse(layer().isSet(), "a second overlap toggles the layer back");
    }

    @Test
    void pathSwapFlashesWhenAnotherSwapChangedTheLayer() {
        ObjPathSwap swap = add(new ObjPathSwap(at(0x1D, 0x200, 0, 0)));      // far from Sonic
        swap.update(1, sonic);
        assertEquals(2, swap.routine(), "layer 0 and a high tile: agree, wait (loc_20B8E8, :83-95)");
        layer().toggle();
        swap.update(2, sonic);
        assertEquals(4, swap.routine(), "tile priority bit set while the layer is set: flash (:84-88, :99-102)");
        assertEquals(0x3C, swap.cooldownFrames());
    }

    @Test
    void swapPlatformIsSolidOnlyWhileTheLayerIsSet() {
        settleOnGround();
        ObjSwapPlatform platform = add(new ObjSwapPlatform(new ObjectSpawn(sonic.getCentreX(),
                sonic.getCentreY() + sonic.getYRadius() + 0x11 - 0x10, 0x35, 0, 0, false, 0)));
        runner.stepIdleFrames(4);
        assertFalse(sonic.isOnObject(), "layer 0: SolidObject is not called (swap_platform.asm:15-16)");
        layer().toggle();
        runner.stepIdleFrames(4);
        assertTrue(sonic.isOnObject(), "layer 1: SolidObject seats Sonic on the platform (:17-19)");
        layer().toggle();
        runner.stepIdleFrames(2);
        assertFalse(sonic.isOnObject(), "layer 0 again: GetOffObject once (:24-29)");
        assertFalse(platform.isDestroyed());
    }

    @Test
    void searchlightSweepsItsTable() {
        ObjSearchlight light = add(new ObjSearchlight(new ObjectSpawn(sonic.getCentreX() + 0x100, sonic.getCentreY(),
                0x38, 3, 0, false, 0)));
        int x0 = light.getX();
        int camY = GameServices.camera().getY();
        // Subtype 3 walks byte_20F830: (frame 0, $1E, no flip, +0), (1, $1E, flip, -$30), (2, $1E, flip, -$5C),
        // (1, $1E, flip, -$30), each held $1E runs, then it wraps (searchlight.asm:177-188, 107-139).
        light.update(1, sonic);
        assertEquals(0, light.mapFrame());
        assertEquals(x0, light.getX());
        assertEquals((camY + 0x70) & 0xFFFF, light.getY(), "oY = camera Y + $70 (:40-42)");
        for (int run = 2; run <= 30; run++) light.update(run, sonic);
        assertEquals(0, light.mapFrame());
        light.update(31, sonic);
        assertEquals(1, light.mapFrame());
        assertEquals(x0 - 0x30, light.getX());
        assertEquals(1, light.spriteFlip() & 1);
        for (int run = 32; run <= 60; run++) light.update(run, sonic);
        light.update(61, sonic);
        assertEquals(2, light.mapFrame());
        assertEquals(x0 - 0x5C, light.getX());
        for (int run = 62; run <= 90; run++) light.update(run, sonic);
        light.update(91, sonic);
        assertEquals(1, light.mapFrame());
        for (int run = 92; run <= 120; run++) light.update(run, sonic);
        light.update(121, sonic);
        assertEquals(0, light.mapFrame(), "var_3C reached var_3D (4): clr.b (:132-136)");
        assertEquals(x0, light.getX());
        assertEquals(0, light.spriteFlip() & 1, "bclr #0,oSprFlags (:122)");
    }

    @Test
    void jumpSpikeBallLauncherSpawnsABallEveryNinetySixFramesAndTheBallSweeps() {
        List<ObjectInstance> placed = live(0x26);
        ObjJumpSpikeBall launcher = add(new ObjJumpSpikeBall(at(0x26, 0x300, -0x40, 0)));
        int y0 = launcher.getY();
        for (int run = 1; run <= 149; run++) launcher.update(run, sonic);
        assertEquals(1, since(placed, 0x26).size(), "oVar2A counts $96 down from run 1 (jump_spike_ball.asm:14, :20-21)");
        launcher.update(150, sonic);
        assertEquals(2, since(placed, 0x26).size());
        ObjJumpSpikeBall ball = (ObjJumpSpikeBall) since(placed, 0x26).stream().filter(o -> o != launcher).findFirst().orElseThrow();
        assertEquals(0xB5, ball.getCollisionFlags());
        assertEquals(0, launcher.getCollisionFlags());
        // Subtype 0 (bit 0 clear): moves along Y at -$8000 (half a pixel) for $20 runs: y = y0 - ceil(n / 2).
        ball.update(1, sonic);
        assertEquals(y0 - 1, ball.getY());
        for (int run = 2; run <= 32; run++) ball.update(run, sonic);
        assertEquals(y0 - 16, ball.getY(), "routine 2 ends after $20 runs (:84-91)");
        // Routine 4 waits $1E runs (runs 33..62); the sweep then starts at -$60000 (-6 pixels) with +$4000
        // acceleration, so n sweep runs from -16 pixels reach -16 - 6n + n(n+1)/8: negative through n = 49
        // (-3.75) and non-negative at n = 50 (+2.75), which deletes the ball on run 112 (:98-129).
        for (int run = 33; run <= 111; run++) ball.update(run, sonic);
        assertFalse(ball.isDestroyed());
        ball.update(112, sonic);
        assertTrue(ball.isDestroyed());
    }

    @Test
    void kanabunFliesAndTurnsAtSixtyFourPixels() {
        ObjKanabun beetle = add(new ObjKanabun(at(0x21, 0x180, -0x40, 0)));
        int x0 = beetle.getX();
        beetle.update(1, sonic);
        // First run: horizontal speed -$4000 (a quarter pixel), Ani frame 6 (the script after its delay byte 01 is
        // 06 07 06 07 06 07 06 07 04 05 ...): collision table index 6 >> 1 = 3 gives 0 (kanabun.asm:30, :66-70, :93-97).
        assertEquals(x0 - 1, beetle.getX());
        assertEquals(6, beetle.mapFrame());
        assertEquals(0, beetle.getCollisionFlags());
        for (int run = 2; run <= 17; run++) beetle.update(run, sonic);
        assertEquals(4, beetle.mapFrame(), "frame 4 is the ninth script entry, first shown on run 17 (delay 1: two runs a frame)");
        assertEquals(0x30, beetle.getCollisionFlags());
        for (int run = 18; run <= 256; run++) beetle.update(run, sonic);
        assertEquals(x0 - 64, beetle.getX());
        assertEquals(0, beetle.spriteFlip() & 1);
        beetle.update(257, sonic);      // distance 65 > $40 (:44-45): speed reverses, the position is undone, flip toggles
        assertEquals(x0 - 64, beetle.getX());
        assertEquals(1, beetle.spriteFlip() & 1);
        beetle.update(258, sonic);
        assertEquals(x0 - 64, beetle.getX(), "+$4000: floor(-64 + 0.25) = -64");
        for (int run = 259; run <= 261; run++) beetle.update(run, sonic);
        assertEquals(x0 - 63, beetle.getX());
    }

    @Test
    void kabasiraHeadSpawnsThreeFollowersThenShootsEveryTwoHundredFortyFrames() {
        settleOnGround();
        placed = live(0x22);
        ObjKabasira head = add(new ObjKabasira(new ObjectSpawn(sonic.getCentreX() - 0x20, sonic.getCentreY(), 0x22, 0, 0,
                false, 0)));
        head.update(1, sonic);
        assertEquals(4, since(placed, 0x22).size(), "head + three SpawnObjectAfter followers (kabasira.asm:63-99)");
        assertEquals(0x31, head.getCollisionFlags());
        // Routine 2: the player is within [-$78,$78) x [-$60,$60) with oVar3A = 0: a shot appears (collision $F1),
        // and oVar3A reloads to $F0 (:117-140).
        head.update(2, sonic);
        assertEquals(5, since(placed, 0x22).size());
        assertEquals(1, shots());
        // oVar3A counts down on runs 3..242 and the next shot is on run 243 (:121-125).
        for (int run = 3; run <= 242; run++) head.update(run, sonic);
        assertEquals(1, shots());
        head.update(243, sonic);
        assertEquals(2, shots());
        // A follower is deleted once its head's slot no longer holds ID $22 (:12-18).
        ObjectInstance follower = since(placed, 0x22).stream().filter(o -> o != head
                && ((ObjKabasira) o).getCollisionFlags() == 0).findFirst().orElseThrow();
        head.setDestroyed(true);
        ((ObjKabasira) follower).update(244, sonic);
        assertTrue(follower.isDestroyed());
    }

    private List<ObjectInstance> placed;

    private long shots() {
        return since(placed, 0x22).stream().filter(o -> ((ObjKabasira) o).getCollisionFlags() == 0xF1).count();
    }

    @Test
    void lampColumnTracksTheCameraInThreeHundredTwentyPixelSteps() {
        // sub_20D4FA: ((camX / $140) + 1) * $140 + $100 - (camX % $140) * $100 / $140 (scddisasm/src/objects/r7/lamp.asm:87-107).
        assertEquals(0x240, ObjLamp.column(0));
        assertEquals(0x380, ObjLamp.column(0x140));
        assertEquals(0x334, ObjLamp.column(0x1A0), "remainder $60: $6000 / $140 = $4C (76.8 truncates), $380 - $4C");
        // A quotient above $30 wraps to 0 but keeps the remainder (:92-95).
        assertEquals(0x240 - 0x0A * 0x100 / 0x140, ObjLamp.column(0x31 * 0x140 + 0x0A));
    }

    @Test
    void lampHeadAndPoleFollowEachOther() {
        List<ObjectInstance> placed = live(0x36);
        ObjLamp head = add(new ObjLamp(at(0x36, 0x100, 0, 0)));
        head.update(1, sonic);                  // routine 0 returns without running routine 2 (lamp.asm:25-40)
        assertEquals(2, since(placed, 0x36).size(), "the pole is spawned with SpawnObjectAfter (:26-33)");
        ObjLamp pole = (ObjLamp) since(placed, 0x36).stream().filter(o -> o != head).findFirst().orElseThrow();
        pole.update(1, sonic);
        assertEquals(4, pole.routine());
        assertEquals(1, pole.mapFrame());
        head.update(2, sonic);
        int camX = GameServices.camera().getX();
        int camY = GameServices.camera().getY();
        assertEquals(ObjLamp.column(camX), head.getX());
        int headY = 0x160 - (camY - 0x160) / 3;
        boolean extended = camY >= headY + 0xC;
        assertEquals(extended ? headY + 0x15C : headY, head.getY(), "lamp.asm:57-83");
        pole.update(2, sonic);
        assertEquals(head.getX(), pole.getX());
        assertEquals(headY + 0x7C, pole.getY());
    }

    @Test
    void springLauncherParentRotatesItsHeadingOnTheLaunchScript() {
        List<ObjectInstance> placed = live(0x1E);
        ObjSpringLauncher parent = add(new ObjSpringLauncher(at(0x1E, 0x200, -0x80, 0)));
        // LaunchAnims: delay 5, eight entries of frame 1, then $FF (the bytes 05 01 01 01 01 01 01 01 01 FF): a script
        // step every six runs, heading = number of entries shown, 1 to 8, then back to 1 (launch.asm:151-167).
        parent.update(1, sonic);
        assertEquals(1, parent.parentHeading());
        assertEquals(4, since(placed, 0x1E).size(), "parent + arm + two gates (:89-149)");
        for (int run = 2; run <= 6; run++) parent.update(run, sonic);
        assertEquals(1, parent.parentHeading());
        parent.update(7, sonic);
        assertEquals(2, parent.parentHeading());
        for (int run = 8; run <= 43; run++) parent.update(run, sonic);
        assertEquals(8, parent.parentHeading());
        for (int run = 44; run <= 49; run++) parent.update(run, sonic);
        assertEquals(1, parent.parentHeading(), "$FF resets oAnimFrame to 0 and the frame it shows makes it 1");
    }

    @Test
    void springLauncherArmCatchesHoldsAndReleasesThePlayer() {
        settleOnGround();
        int x = sonic.getCentreX();
        int y = sonic.getCentreY();
        List<ObjectInstance> placed = live(0x1E);
        // Subtype 2 is a fixed launcher at heading 1 (horizontal, unflipped): the arm sits at parent + (8, 0)
        // (word_20BC82 row 1), the player lands at arm + (0x18, 0) (unk_20C224[1] = 0 -> word_20C24C row 0) and leaves
        // at +$1000 with y speed 0 (launch.asm:80-87, :279-281, :850-858, :871-926).
        ObjSpringLauncher parent = add(new ObjSpringLauncher(new ObjectSpawn(x - 8, y, 0x1E, 2, 0, false, 0)));
        parent.update(1, sonic);
        ObjSpringLauncher arm = (ObjSpringLauncher) since(placed, 0x1E).stream()
                .filter(o -> o != parent && ((ObjSpringLauncher) o).routine() == 4).findFirst().orElseThrow();
        assertEquals(1, parent.parentHeading());
        arm.update(1, sonic);
        assertEquals(6, arm.routine(), "the catch adds 2 to routine 4 (:816)");
        assertEquals(x, arm.getX());
        assertEquals(0x1000, sonic.getXSpeed());
        assertEquals(0, sonic.getYSpeed());
        assertEquals(x + 0x18, sonic.getCentreX());
        assertEquals(y, sonic.getCentreY());
        assertTrue(sonic.isObjectControlled(), "bset #0,obj.var_2c(a2)");
        assertTrue(sonic.getRolling());
        assertTrue(sonic.getAir());
        // oVar32 = $3C is set by the catch (run 1) and counts down in routine 6; the release (bclr #0, FM_CE) comes on the
        // $3C-th run after it, run $3D (:336-345).
        for (int run = 2; run <= 0x3C; run++) arm.update(run, sonic);
        assertTrue(sonic.isObjectControlled());
        arm.update(0x3D, sonic);
        assertFalse(sonic.isObjectControlled());
        assertEquals(0x1000, sonic.getXSpeed(), "the velocity was set at the catch and survives the hold");
    }
}
