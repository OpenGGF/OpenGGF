package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.CheckpointState;
import com.openggf.game.RespawnState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production FBZ1 route boundary: ROM placement, solids, boss graph, sign and results. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAct1RouteHeadless {
    private static final int BOSS_X = 0x2F00;
    private static final int BOSS_Y = 0x05E0;
    private static final int PRE_ARENA_X = BOSS_X - 0x1F;
    private static final int PRE_ARENA_Y = 0x0540;

    @Test
    void loadedFbz1PlacementStartsOnlyWhenP1ReallyStandsOnItsPlunger() {
        HeadlessTestFixture fixture = routeBoundaryFixture();
        AbstractPlayableSprite player = fixture.sprite();
        ObjectManager objects = GameServices.level().getObjectManager();

        FbzMinibossInstance boss = awaitPlacedBoss(fixture, objects, 240);
        assertEquals(BOSS_X, boss.getSpawn().x());
        assertEquals(BOSS_Y, boss.getSpawn().y());
        assertEquals(Sonic3kObjectIds.FBZ_MINIBOSS, boss.getSpawn().objectId());

        reachPlungerByInput(fixture, objects, boss);

        assertTrue(boss.isPlungerStarted(),
                "normal falling/solid resolution at the authored route boundary must set the native P1 standing bit");
        assertTrue(objects.getRidingObject(player) instanceof FbzMinibossPlungerChild,
                "the production solid-contact registry must identify the placed boss plunger as P1's support");
        assertTrue(player.getCentreY() < BOSS_Y,
                "P1 should be standing above the boss after the real SolidObject contact");

        for (int frame = 0; frame < 12; frame++) {
            fixture.stepFrame(false, false, false, false, true);
        }
        await(fixture, 120,
                () -> objects.getRidingObject(player) instanceof FbzMinibossPlungerChild,
                "a normal jump from the real plunger did not reacquire its production solid support");
    }

    @Test
    void sixHundredFortyPixelViewportKeepsTheNativeArenaLockAndDonationNeutralRoute() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        var graphics = GameServices.graphics();
        int previousViewportX = graphics.getViewportX();
        int previousViewportY = graphics.getViewportY();
        int previousViewportWidth = graphics.getViewportWidth();
        int previousViewportHeight = graphics.getViewportHeight();
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 640);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "s1");
        SessionManager.clear();
        try {
            HeadlessTestFixture fixture = routeBoundaryFixture();
            graphics.setViewport(0, 0, 640, 224);
            ObjectManager objects = GameServices.level().getObjectManager();
            FbzMinibossInstance boss = awaitPlacedBoss(fixture, objects, 240);

            assertTrue(configuration.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED),
                    "the compatibility route must execute with cross-game donation enabled");
            assertEquals("s1", configuration.getString(SonicConfiguration.CROSS_GAME_SOURCE),
                    "the compatibility route must exercise the Sonic 1 donation profile");
            assertEquals(640, fixture.camera().getWidth() & 0xFFFF,
                    "the route must run with a fresh 640-native-pixel gameplay camera");
            assertEquals(640, graphics.getViewportWidth(),
                    "the headless viewport must exercise the same 640-pixel horizontal extent");

            reachPlungerAtWideWidthByInput(fixture, objects, boss);

            assertEquals(0x2E20, fixture.camera().getMinX() & 0xFFFF,
                    "widescreen must not shift the ROM-authored FBZ1 left arena boundary");
            assertEquals(0x2EA0, fixture.camera().getMaxX() & 0xFFFF,
                    "widescreen must not widen or recenter the ROM-authored arena lock");
            assertEquals(0x0540, fixture.camera().getMinY() & 0xFFFF,
                    "widescreen must preserve the ROM-authored arena floor camera boundary");
            assertFalse(fixture.sprite().getDead(),
                    "the wider camera must not expose a route gap or let P1 fall out of the arena");
            assertTrue(objects.getRidingObject(fixture.sprite()) instanceof FbzMinibossPlungerChild,
                    "normal route input must still reach the production plunger at 640 pixels");
            assertFalse(fixture.sprite().getSpindash(),
                    "FBZ1's route must remain completable with donation-neutral movement and jump input only");
        } finally {
            configuration.clearSessionOverrides();
            graphics.setViewport(previousViewportX, previousViewportY,
                    previousViewportWidth, previousViewportHeight);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    @Test
    void p2AndExtraSidekickCanRideTheRealPlungerButOnlyP1CanStartIt() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        String previousSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        try {
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails,knuckles");
            HeadlessTestFixture fixture = routeBoundaryFixture();
            ObjectManager objects = GameServices.level().getObjectManager();
            FbzMinibossInstance boss = awaitPlacedBoss(fixture, objects, 240);
            convergeBossCameraByInput(fixture, boss);

            List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
            assertEquals(2, sidekicks.size(),
                    "the production team bootstrap must register native P2 plus one extension sidekick");
            AbstractPlayableSprite p2 = sidekicks.get(0);
            AbstractPlayableSprite extra = sidekicks.get(1);
            assertEquals("tails_p2", p2.getCode());
            assertEquals("knuckles_p3", extra.getCode());
            assertTrue(p2.isCpuControlled());
            assertTrue(extra.isCpuControlled());

            moveP1AwayFromPlungerByInput(fixture);
            FbzMinibossPlungerChild plunger = awaitObject(
                    fixture, objects, FbzMinibossPlungerChild.class, 30);
            prepareRealFallingContact(p2, plunger);
            prepareRealFallingContact(extra, plunger);

            fixture.runner().stepFrame(false, false, false, false, false, 0, false);

            assertSame(plunger, objects.getRidingObject(p2),
                    "native P2 must participate in the real plunger SolidObject collision");
            assertSame(plunger, objects.getRidingObject(extra),
                    "the extension sidekick must have an independent production standing latch");
            assertTrue(objects.hasObjectStandingBit(p2, plunger));
            assertTrue(objects.hasObjectStandingBit(extra, plunger));
            assertFalse(p2.getDead());
            assertFalse(extra.getDead());
            assertFalse(boss.isPlungerStarted(),
                    "P2 and extension standing bits must not alias the native P1 start-authority bit");

            landP1OnPlungerByInput(fixture, objects, boss);

            assertTrue(boss.isPlungerStarted(),
                    "normal P1 SolidObject contact must retain exclusive authority to start the encounter");
            assertSame(plunger, objects.getRidingObject(fixture.sprite()));
        } finally {
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, previousSidekicks);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    @Test
    void placedBossAutomaticallyReachesSignLandingResultsCompletionAndEventsFg5() {
        HeadlessTestFixture fixture = routeBoundaryFixture();
        ObjectManager objects = GameServices.level().getObjectManager();
        FbzMinibossInstance boss = awaitPlacedBoss(fixture, objects, 240);
        reachPlungerByInput(fixture, objects, boss);
        RouteMilestones milestones = driveBossRoute(fixture, objects, boss);
        assertEquals(FbzMinibossInstance.peakGraphSlots(), milestones.maxFamily,
                "the real ObjectManager must reach the full persistent graph plus its transient palette child");
        assertTrue(boss.isDefeated(),
                "the two real five-link arm graphs did not deliver six automatic terminal impacts");
        assertEquals(6, boss.scriptedImpactCount());
        assertEquals(0, boss.remainingHits());

        await(fixture, 4, boss::hasConvertedToEndSign,
                "the defeated placed boss slot did not convert to Obj_EndSignControl");
        assertFalse(boss.isSolidFor(fixture.sprite()));

        S3kSignpostInstance sign = awaitObject(fixture, objects, S3kSignpostInstance.class, 180);
        boolean sawFalling = !sign.isLanded();
        await(fixture, 600, sign::isLanded, "the real falling sign never landed on FBZ1 terrain");
        assertTrue(sawFalling, "the route must observe the sign's falling state before landing");

        S3kResultsScreenObjectInstance results = awaitObject(
                fixture, objects, S3kResultsScreenObjectInstance.class, 240);
        Sonic3kLevelEventManager eventManager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kFBZEvents events = eventManager.getFbzEvents();
        assertFalse(events.isEventsFg5(),
                "the boss/sign must not publish Events_fg_5 before Obj_LevelResultsCreate");

        await(fixture, 30, events::isEventsFg5,
                "Obj_LevelResultsCreate did not publish FBZ1 Events_fg_5 through the real event bridge");
        assertFalse(boss.resultsObserved(),
                "the converted boss waits for completed results, not merely Events_fg_5 publication");

        await(fixture, 2_000, results::isDestroyed,
                "the production S3K results sequence did not finish its tally/wait/exit queue");
        await(fixture, 4, boss::resultsObserved,
                "the converted boss did not observe the completed results lifecycle");
        assertTrue(boss.resultsObserved(),
                "the converted boss must observe the results-owned End_of_level_effect clear");
        assertTrue(events.isEventsFg5(),
                "FBZ1 background transition signal must remain owned by the completed results flow");
    }

    @Test
    void checkpointDeathReloadRecreatesPristinePlacedBossAndClearsEncounterTransients() {
        HeadlessTestFixture fixture = routeBoundaryFixture();
        ObjectManager firstManager = GameServices.level().getObjectManager();
        RespawnState respawn = GameServices.level().getCheckpointState();
        assertTrue(respawn instanceof CheckpointState);
        ((CheckpointState) respawn).saveCheckpoint(7, PRE_ARENA_X, PRE_ARENA_Y, false);

        int savedCameraX = fixture.camera().getX() & 0xFFFF;
        int savedCameraY = fixture.camera().getY() & 0xFFFF;
        int savedMaxY = fixture.camera().getMaxY() & 0xFFFF;
        Palette preEncounterPalette = GameServices.level().getCurrentLevel().getPalette(1).deepCopy();
        FbzMinibossInstance firstBoss = awaitPlacedBoss(fixture, firstManager, 240);
        reachPlungerByInput(fixture, firstManager, firstBoss);
        awaitGraph(fixture, firstManager, 18);
        assertFalse(preEncounterPalette.dataEquals(GameServices.level().getCurrentLevel().getPalette(1)),
                "the real boss initialization must install Pal_FBZMiniboss before restart can prove cleanup");
        for (int frame = 0; frame < 20_000 && !fixture.sprite().getDead(); frame++) {
            int playerX = fixture.sprite().getCentreX() & 0xFFFF;
            fixture.stepFrame(false, false, playerX > BOSS_X + 2, playerX < BOSS_X - 2, false);
        }
        assertTrue(fixture.sprite().getDead(),
                "the live terminal hazard did not drive the checkpoint route through production death; "
                        + encounterState(fixture, firstManager, firstBoss));
        assertTrue(firstBoss.scriptedImpactCount() > 0,
                "death must occur after the automatic terminal graph has published a real boss impact");
        assertTrue(anyCapturedTarget(firstManager),
                "the live encounter must capture a P1 terminal target before restart can prove it is transient");
        assertEquals(0x2E20, fixture.camera().getMinX() & 0xFFFF);
        assertEquals(0x2EA0, fixture.camera().getMaxX() & 0xFFFF);

        GameServices.level().respawnPlayer();

        ObjectManager restartedManager = GameServices.level().getObjectManager();
        assertNotSame(firstManager, restartedManager,
                "death restart must rebuild the production ObjectManager");
        assertEquals(savedCameraX, fixture.camera().getX() & 0xFFFF);
        assertEquals(savedCameraY, fixture.camera().getY() & 0xFFFF);
        assertEquals(savedMaxY, fixture.camera().getMaxY() & 0xFFFF);
        assertFalse(hasMinibossPaletteOwner(),
                "full level reload must clear the old encounter's palette ownership");
        assertTrue(preEncounterPalette.dataEquals(GameServices.level().getCurrentLevel().getPalette(1)),
                "death reload must restore the pre-encounter FBZ palette instead of retaining boss flash colors");
        FbzMinibossInstance restartedBoss = awaitPlacedBoss(fixture, restartedManager, 240);
        reachPlungerByInput(fixture, restartedManager, restartedBoss);
        awaitGraph(fixture, restartedManager, 18);
        assertNotSame(firstBoss, restartedBoss);
        assertSame(restartedManager, GameServices.level().getObjectManager());
        assertFalse(restartedBoss.hasConvertedToEndSign());
        assertFalse(restartedBoss.isDefeated());
        assertFalse(restartedBoss.resultsObserved());
        assertEquals(6, restartedBoss.remainingHits());
        assertEquals(0, restartedBoss.scriptedImpactCount());
        assertFalse(anyCapturedTarget(restartedManager),
                "newly placed arm terminals must not inherit the dead encounter graph's captured target");
        assertEquals(18, countMinibossFamily(restartedManager),
                "restart must recreate one exact persistent family with no stale/duplicate children");
    }

    private static HeadlessTestFixture routeBoundaryFixture() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                .startPosition((short) PRE_ARENA_X, (short) PRE_ARENA_Y)
                .startPositionIsCentre()
                .build();
        fixture.sprite().setRingCount(50);
        return fixture;
    }

    private static FbzMinibossInstance awaitPlacedBoss(
            HeadlessTestFixture fixture, ObjectManager objects, int maxFrames) {
        assertNotNull(objects);
        for (int frame = 0; frame < maxFrames; frame++) {
            FbzMinibossInstance boss = objects.activeObjectsOfType(FbzMinibossInstance.class).stream()
                    .filter(candidate -> candidate.getSpawn() != null
                            && candidate.getSpawn().objectId() == Sonic3kObjectIds.FBZ_MINIBOSS)
                    .findFirst().orElse(null);
            if (boss != null) return boss;
            fixture.stepFrame(false, false, false, false, false);
        }
        throw new AssertionError("authored FBZ1 $AA placement did not enter the real ObjectManager spawn window");
    }

    private static void awaitPlungerStart(HeadlessTestFixture fixture, FbzMinibossInstance boss) {
        await(fixture, 240, boss::isPlungerStarted,
                "normal P1/plunger solid contact did not start the fight");
    }

    private static void reachPlungerByInput(
            HeadlessTestFixture fixture, ObjectManager objects, FbzMinibossInstance boss) {
        convergeBossCameraByInput(fixture, boss);
        landP1OnPlungerByInput(fixture, objects, boss);
    }

    private static void convergeBossCameraByInput(
            HeadlessTestFixture fixture, FbzMinibossInstance boss) {
        for (int frame = 0; frame < 600 && (fixture.camera().getX() & 0xFFFF) < 0x2E20; frame++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        assertTrue((fixture.camera().getX() & 0xFFFF) >= 0x2E20,
                "normal pre-arena traversal did not carry the camera to the boss lock threshold");
        await(fixture, 600, () -> intField(boss, "phaseOrdinal") == 1,
                "boss camera approach did not converge before the plunger route boundary");
        assertFalse(boss.isPlungerStarted(),
                "pre-arena setup must not touch the P1-only plunger before camera convergence");
        assertEquals(0, boss.scriptedImpactCount());
    }

    private static void reachPlungerAtWideWidthByInput(
            HeadlessTestFixture fixture, ObjectManager objects, FbzMinibossInstance boss) {
        boolean sawSpindash = false;
        for (int frame = 0; frame < 600 && (fixture.camera().getX() & 0xFFFF) < 0x2E20; frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            int x = player.getCentreX() & 0xFFFF;
            boolean risingJump = player.isJumping() && player.getYSpeed() < 0;
            boolean jump = x >= BOSS_X - 0x28 && (!player.getAir() || risingJump);
            fixture.stepFrame(false, false, false, true, jump);
            sawSpindash |= player.getSpindash();
        }
        assertTrue((fixture.camera().getX() & 0xFFFF) >= 0x2E20,
                "normal widescreen jump traversal did not reach the native camera lock threshold");

        for (int frame = 0; frame < 900; frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            if (objects.getRidingObject(player) instanceof FbzMinibossPlungerChild) break;
            int x = player.getCentreX() & 0xFFFF;
            boolean risingJump = player.isJumping() && player.getYSpeed() < 0;
            boolean jump = Math.abs(x - BOSS_X) > 8 && (!player.getAir() || risingJump);
            fixture.stepFrame(false, false, x > BOSS_X, x < BOSS_X, jump);
            sawSpindash |= player.getSpindash();
            assertFalse(player.getDead(),
                    "ordinary widescreen jump traversal fell below the FBZ1 arena route");
        }
        assertTrue(objects.getRidingObject(fixture.sprite()) instanceof FbzMinibossPlungerChild,
                "normal widescreen movement/jump input did not land on the real plunger solid");
        assertFalse(sawSpindash,
                "the 640-pixel route must stay independent of a donated spindash implementation");
        awaitPlungerStart(fixture, boss);
        settleOnPlunger(fixture, objects);
    }

    private static void landP1OnPlungerByInput(
            HeadlessTestFixture fixture, ObjectManager objects, FbzMinibossInstance boss) {
        int minY = Integer.MAX_VALUE;
        int airFrames = 0;
        int jumpCommands = 0;
        boolean sawSpindash = false;
        for (int frame = 0; frame < 900; frame++) {
            if (objects.getRidingObject(fixture.sprite()) instanceof FbzMinibossPlungerChild) break;
            int x = fixture.sprite().getCentreX() & 0xFFFF;
            boolean risingJump = fixture.sprite().isJumping() && fixture.sprite().getYSpeed() < 0;
            boolean jump = x < BOSS_X - 8 && (!fixture.sprite().getAir() || risingJump);
            if (jump) jumpCommands++;
            fixture.stepFrame(false, false, x > BOSS_X, x < BOSS_X, jump);
            sawSpindash |= fixture.sprite().getSpindash();
            minY = Math.min(minY, fixture.sprite().getCentreY() & 0xFFFF);
            if (fixture.sprite().getAir()) airFrames++;
        }
        assertTrue(objects.getRidingObject(fixture.sprite()) instanceof FbzMinibossPlungerChild,
                "normal right+jump traversal did not land P1 on the real plunger solid; player=(0x"
                        + Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF) + ",0x"
                        + Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF) + ") dead="
                        + fixture.sprite().getDead() + " minY=0x" + Integer.toHexString(minY)
                        + " airFrames=" + airFrames + " jumpCommands=" + jumpCommands);
        assertFalse(sawSpindash,
                "the authored FBZ1 route must not require a spindash-only cross-game move");
        awaitPlungerStart(fixture, boss);
        settleOnPlunger(fixture, objects);
    }

    private static void moveP1AwayFromPlungerByInput(HeadlessTestFixture fixture) {
        for (int frame = 0; frame < 240; frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            int x = player.getCentreX() & 0xFFFF;
            boolean left = x > BOSS_X - 0x48 || player.getXSpeed() > 0x20;
            if (!left && Math.abs(player.getXSpeed()) <= 0x20) return;
            fixture.stepFrame(false, false, left, false, false);
        }
        throw new AssertionError("normal left input did not clear P1 from the sidekick plunger authority check");
    }

    private static void prepareRealFallingContact(
            AbstractPlayableSprite sidekick, FbzMinibossPlungerChild plunger) {
        sidekick.setCentreX((short) plunger.getX());
        sidekick.setCentreY((short) (plunger.getY() - 8 - sidekick.getYRadius()));
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0x100);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(true);
        sidekick.setDead(false);
        sidekick.setHurt(false);
    }

    private static void settleOnPlunger(HeadlessTestFixture fixture, ObjectManager objects) {
        for (int frame = 0; frame < 600; frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            int dx = player.getCentreX() - BOSS_X;
            int xSpeed = player.getXSpeed();
            if (objects.getRidingObject(player) instanceof FbzMinibossPlungerChild
                    && Math.abs(dx) <= 2 && Math.abs(xSpeed) <= 0x20) return;
            boolean left = dx > 2 || (Math.abs(dx) <= 2 && xSpeed > 0x20);
            boolean right = dx < -2 || (Math.abs(dx) <= 2 && xSpeed < -0x20);
            fixture.stepFrame(false, false, left, right, false);
        }
        throw new AssertionError("normal input did not settle P1 on the plunger; player=(0x"
                + Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF) + ",0x"
                + Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF) + ") xSpeed=0x"
                + Integer.toHexString(fixture.sprite().getXSpeed() & 0xFFFF));
    }

    private static RouteMilestones driveBossRoute(
            HeadlessTestFixture fixture, ObjectManager objects, FbzMinibossInstance boss) {
        RouteMilestones milestones = new RouteMilestones();
        EncounterInputDriver driver = new EncounterInputDriver(boss);
        boolean sawRide = false;
        for (int frame = 0; frame < 20_000 && !boss.isDefeated(); frame++) {
            int absoluteFrame = fixture.frameCount();
            int phase = intField(boss, "phaseOrdinal");
            if (phase == 1 && milestones.waitPlunger < 0) milestones.waitPlunger = absoluteFrame;
            if (phase == 2 && milestones.musicWait < 0) milestones.musicWait = absoluteFrame;
            if (boss.isPlungerStarted() && milestones.rootBit0 < 0) milestones.rootBit0 = absoluteFrame;
            if (boss.scriptedImpactCount() > 0 && milestones.firstImpact < 0) milestones.firstImpact = absoluteFrame;
            ObjectInstance riding = objects.getRidingObject(fixture.sprite());
            if (riding instanceof FbzMinibossPlungerChild) sawRide = true;
            if (sawRide && riding == null && milestones.firstUnseat < 0) milestones.firstUnseat = absoluteFrame;
            if ((fixture.sprite().isHurt() || fixture.sprite().getDead()) && milestones.firstHurt < 0) {
                milestones.firstHurt = absoluteFrame;
            }
            milestones.maxFamily = Math.max(milestones.maxFamily, countMinibossFamily(objects));

            AbstractPlayableSprite player = fixture.sprite();
            int playerX = player.getCentreX() & 0xFFFF;
            RouteInput input = driver.next(player, objects);
            fixture.stepFrame(false, false,
                    playerX > input.targetX() + 2, playerX < input.targetX() - 2,
                    input.jump());
        }
        if (!boss.isDefeated()) {
            throw new AssertionError("route milestones=" + milestones + "; " + encounterState(fixture, objects, boss));
        }
        return milestones;
    }

    private static final class EncounterInputDriver {
        private final FbzMinibossInstance boss;
        private boolean evading;
        private boolean outwardSeen;
        private int impactAtExit;

        private EncounterInputDriver(FbzMinibossInstance boss) {
            this.boss = boss;
        }

        RouteInput next(AbstractPlayableSprite player, ObjectManager objects) {
            boolean onPlunger = objects.getRidingObject(player) instanceof FbzMinibossPlungerChild;
            boolean normalFan = objects.activeObjectsOfType(FbzMinibossChainLink.class).stream()
                    .anyMatch(link -> link.linkIndex() == 4 && intField(link, "stateOrdinal") == 9);
            boolean outwardActive = objects.activeObjectsOfType(FbzMinibossChainLink.class).stream()
                    .anyMatch(link -> {
                        int state = intField(link, "stateOrdinal");
                        return link.linkIndex() == 4 && state >= 12 && state <= 16;
                    });
            if (!evading && onPlunger && normalFan) {
                evading = true;
                outwardSeen = false;
                impactAtExit = boss.scriptedImpactCount();
            }
            if (!evading) {
                return new RouteInput(BOSS_X, false);
            }
            if (outwardActive) outwardSeen = true;
            if (boss.scriptedImpactCount() <= impactAtExit || !outwardSeen) {
                return new RouteInput(BOSS_X + (outwardActive ? 0x65 : 0x45), false);
            }
            if (onPlunger) {
                evading = false;
                outwardSeen = false;
                return new RouteInput(BOSS_X, false);
            }
            boolean risingJump = player.isJumping() && player.getYSpeed() < 0;
            return new RouteInput(BOSS_X, !player.getAir() || risingJump);
        }
    }

    private record RouteInput(int targetX, boolean jump) { }

    private static final class RouteMilestones {
        int waitPlunger = -1;
        int rootBit0 = -1;
        int musicWait = -1;
        int firstImpact = -1;
        int firstHurt = -1;
        int firstUnseat = -1;
        int maxFamily;

        @Override public String toString() {
            return "wait=" + waitPlunger + ",root=" + rootBit0 + ",music=" + musicWait
                    + ",impact=" + firstImpact + ",hurt=" + firstHurt + ",unseat=" + firstUnseat
                    + ",maxFamily=" + maxFamily;
        }
    }

    private static String encounterState(
            HeadlessTestFixture fixture, ObjectManager objects, FbzMinibossInstance boss) {
        StringBuilder state = new StringBuilder()
                .append("impacts=").append(boss.scriptedImpactCount())
                .append(" phase=").append(intField(boss, "phaseOrdinal"))
                .append(" rootBits=0x").append(Integer.toHexString(intField(boss, "rootControlBits")))
                .append(" player=(0x").append(Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF))
                .append(",0x").append(Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF))
                .append(") air=").append(fixture.sprite().getAir())
                .append(" rings=").append(fixture.sprite().getRingCount())
                .append(" dead=").append(fixture.sprite().getDead())
                .append(" riding=").append(objects.getRidingObject(fixture.sprite()) == null
                        ? "none" : objects.getRidingObject(fixture.sprite()).getClass().getSimpleName());
        for (FbzMinibossArmChild arm : objects.activeObjectsOfType(FbzMinibossArmChild.class)) {
            state.append(" arm").append(intField(arm, "side"))
                    .append("[state=").append(intField(arm, "stateOrdinal"))
                    .append(",bits=0x").append(Integer.toHexString(intField(arm, "controlBits")))
                    .append(",timer=").append(intField(arm, "timer")).append(']');
        }
        return state.toString();
    }

    private static void awaitGraph(HeadlessTestFixture fixture, ObjectManager objects, int count) {
        await(fixture, 240, () -> countMinibossFamily(objects) == count,
                "real ObjectManager did not assemble the exact 18-slot persistent miniboss graph; count="
                        + countMinibossFamily(objects));
    }

    private static int countMinibossFamily(ObjectManager objects) {
        int count = 0;
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!object.isDestroyed() && object.getClass().getSimpleName().startsWith("FbzMiniboss")) count++;
        }
        return count;
    }

    private static boolean anyCapturedTarget(ObjectManager objects) {
        for (FbzMinibossChainLink link : objects.activeObjectsOfType(FbzMinibossChainLink.class)) {
            if (intField(link, "targetX") != 0 || intField(link, "targetY") != 0) return true;
        }
        return false;
    }

    private static int intField(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean hasMinibossPaletteOwner() {
        var registry = GameServices.paletteOwnershipRegistry();
        for (PaletteSurface surface : PaletteSurface.values()) {
            for (int line = 0; line < 4; line++) {
                for (int color = 0; color < 16; color++) {
                    if (S3kPaletteOwners.FBZ_MINIBOSS.equals(registry.ownerAt(surface, line, color))) return true;
                }
            }
        }
        return false;
    }

    private static <T> T awaitObject(
            HeadlessTestFixture fixture, ObjectManager objects, Class<T> type, int maxFrames) {
        for (int frame = 0; frame < maxFrames; frame++) {
            for (ObjectInstance object : objects.getActiveObjects()) {
                if (!object.isDestroyed() && type.isInstance(object)) return type.cast(object);
            }
            fixture.stepFrame(false, false, false, false, false);
        }
        throw new AssertionError(type.getSimpleName() + " did not appear in the production ObjectManager");
    }

    private static void await(
            HeadlessTestFixture fixture, int maxFrames, java.util.function.BooleanSupplier condition,
            String failureMessage) {
        for (int frame = 0; frame < maxFrames; frame++) {
            if (condition.getAsBoolean()) return;
            fixture.stepFrame(false, false, false, false, false);
        }
        assertTrue(condition.getAsBoolean(), failureMessage);
    }
}
