package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.CheckpointState;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RespawnState;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void realConvertedEndSignControllerAllocatesExactWorkerPrefixAndRunsFirstTwoUpdates(
            int freeCapacity) {
        HeadlessTestFixture fixture = routeBoundaryFixture();
        ObjectManager act1Objects = GameServices.level().getObjectManager();
        FbzMinibossInstance boss = awaitPlacedBoss(fixture, act1Objects, 240);
        reachPlungerByInput(fixture, act1Objects, boss);
        driveBossRoute(fixture, act1Objects, boss);
        await(fixture, 4, boss::hasConvertedToEndSign,
                "the production defeated boss did not convert into Obj_EndSignControl");

        S3kSignpostInstance sign = awaitObject(fixture, act1Objects,
                S3kSignpostInstance.class, 180);
        await(fixture, 600, sign::isLanded,
                "the production Obj_EndSign did not land before results");
        S3kResultsScreenObjectInstance results = awaitObject(fixture, act1Objects,
                S3kResultsScreenObjectInstance.class, 240);

        // loc_2DD06 mutates the results SST into Obj_TitleCard before its
        // 90-frame Wait2/title-child poll. At this point all result elements
        // have retired, so reserving the remaining free SSTs models stable
        // capacity at the later Change_Act2Sizes call (62244-62279).
        await(fixture, 2_500,
                () -> results.carriedTitlePhase()
                        != S3kResultsScreenObjectInstance.CarriedTitlePhase.RESULTS,
                "the real results owner did not mutate into its carried title routine");
        assertTrue(boss.resultsObserved(),
                "the converted controller must first observe the real results owner completing");
        assertFalse(boss.isDestroyed(),
                "the converted controller must remain live until Change_Act2Sizes allocation");
        assertFalse(GameServices.module().getTitleCardProvider().isComplete());
        assertFalse(GameServices.gameState().isEndOfLevelFlag());
        assertFalse(results.isDestroyed(),
                "Obj_TitleCardWait2 has not yet published its terminal byte");

        ObjectManager act2Objects = GameServices.level().getObjectManager();
        assertTrue(act2Objects.activeObjectsOfType(FbzAct2CameraResizeWorker.class).isEmpty(),
                "the publisher is later in SST order, so the controller consumes it next frame");
        int minXTarget = fixture.camera().getMinXTarget() & 0xFFFF;
        int maxXTarget = fixture.camera().getMaxXTarget() & 0xFFFF;
        int minYTarget = fixture.camera().getMinYTarget() & 0xFFFF;
        // Obj_TitleCardWait2 publishes End_of_level_flag only after its title
        // children finish; the later Obj_EndSignControlDoStart SST may consume
        // that byte in the same ExecuteObjects pass (62244-62279,
        // 180420-180424).
        await(fixture, 2_500,
                () -> results.carriedTitlePhase()
                        == S3kResultsScreenObjectInstance.CarriedTitlePhase.TITLE_CARD_WAIT2
                        && results.carriedTitleWaitTimer() == 0
                        && !GameServices.gameState().isEndOfLevelFlag(),
                "the carried title owner did not reach its final flag-clear dispatch edge");
        // WAIT2 decrements 1 -> 0 and returns. The title provider advances
        // before objects; it may become complete only on the later dispatch
        // that publishes the flag, so provider-complete + flag-clear is not
        // an observable frame boundary here.
        // Capture capacity only at the final dispatch edge. The title children
        // own real SSTs during Wait2, so an earlier snapshot becomes stale as
        // they retire. AllocateObjectAfterCurrent only sees holes after a0
        // (176924-176953): fill every live hole, then reopen exactly N freshly
        // observed holes after the converted controller.
        BitSet usedAtDispatchEdge = BitSet.valueOf(
                act2Objects.rewindSnapshottable().capture().usedSlotsBits());
        List<Integer> controlledAfterCurrentSlots = new ArrayList<>();
        int firstDynamicSlot = ObjectSlotLayout.SONIC_3K.firstDynamicSlot();
        for (int slot = boss.getSlotIndex() + 1;
             slot < ObjectSlotLayout.SONIC_3K.lastDynamicSlotExclusive(); slot++) {
            if (!usedAtDispatchEdge.get(slot - firstDynamicSlot)) {
                controlledAfterCurrentSlots.add(slot);
            }
        }
        assertTrue(controlledAfterCurrentSlots.size() >= 3,
                "the real route must expose three controllable AllocateObjectAfterCurrent slots");
        act2Objects.reserveAllButNFreeSlots(0);
        for (int i = 0; i < freeCapacity; i++) {
            act2Objects.releaseDynamicSlot(controlledAfterCurrentSlots.get(i));
        }
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(0, results.carriedTitleWaitTimer(),
                "Obj_TitleCardWait2 remains on its child-completion poll after the timer expires");
        assertFalse(GameServices.gameState().isEndOfLevelFlag(),
                "the gameplay flag must remain clear until the external title children finish");
        assertFalse(results.isDestroyed(),
                "the carried title owner remains live while its visual children exit");
        await(fixture, 240, results::isDestroyed,
                "the external title children did not complete and publish the carried title flag");

        List<FbzAct2CameraResizeWorker> workers = act2Objects.activeObjectsOfType(
                FbzAct2CameraResizeWorker.class);
        assertTrue(GameServices.module().getTitleCardProvider().isComplete());
        assertTrue(GameServices.gameState().isEndOfLevelFlag(),
                "the title owner must publish End_of_level_flag on this dispatch");
        assertTrue(results.isDestroyed(),
                "the carried title owner deletes after publishing its terminal byte");
        int expectedCount = Math.min(freeCapacity, 3);
        assertEquals(expectedCount, workers.size(),
                "CreateChild1 must preserve the successful prefix and stop at failure ordinal "
                        + freeCapacity + "; controllerSlot=" + boss.getSlotIndex()
                        + ", occupancy=" + act2Objects.occupiedDynamicSlotIds());
        assertEquals(List.of(
                        FbzAct2CameraResizeWorker.MAX_X,
                        FbzAct2CameraResizeWorker.MIN_Y,
                        FbzAct2CameraResizeWorker.MAX_Y).subList(0, expectedCount),
                workers.stream().map(worker -> worker.getSpawn().subtype()).toList(),
                "CreateChild1_Normal (176924-176949) must allocate MAX_X, MIN_Y, MAX_Y in "
                        + "table order, stop at the first failure, and retain the successful prefix");
        assertTrue(boss.isDestroyed(),
                "Obj_EndSignControlDoStart (180420-180424) deletes after Change_Act2Sizes even "
                        + "when the first worker allocation fails");
        assertEquals(GameServices.level().getCurrentLevel().getMaxY(),
                fixture.camera().getMaxYTarget() & 0xFFFF,
                "Change_Act2Sizes publishes the stored Act-2 target before allocation");
        assertEquals(minXTarget, fixture.camera().getMinXTarget() & 0xFFFF);
        assertEquals(maxXTarget, fixture.camera().getMaxXTarget() & 0xFFFF);
        assertEquals(minYTarget, fixture.camera().getMinYTarget() & 0xFFFF);
        assertEquals(fixture.camera().getX(), fixture.camera().getXCopy(),
                "ScreenEvents copies the live camera X after the first worker update");
        assertEquals(fixture.camera().getY(), fixture.camera().getYCopy(),
                "ScreenEvents copies the live camera Y after the first worker update");

        int maxXAfterFirst = fixture.camera().getMaxX() & 0xFFFF;
        int minYAfterFirst = fixture.camera().getMinY() & 0xFFFF;
        int maxYAfterFirst = fixture.camera().getMaxY() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false);

        if (expectedCount >= 1) {
            assertEquals((maxXAfterFirst + 2) & 0xFFFF,
                    fixture.camera().getMaxX() & 0xFFFF,
                    "$4000 + $4000 contributes no integer worker delta on update two; "
                            + "the concurrent camera-target easing contributes +2");
        }
        if (expectedCount >= 2) {
            assertEquals(minYAfterFirst, fixture.camera().getMinY() & 0xFFFF,
                    "$4000 + $4000 has not yet moved MIN_Y on worker update two");
        }
        if (expectedCount >= 3) {
            assertEquals((maxYAfterFirst + 3) & 0xFFFF,
                    fixture.camera().getMaxY() & 0xFFFF,
                    "$8000 + $8000 contributes +1 on worker update two while the "
                            + "concurrent camera-target easing contributes +2");
        }
        assertEquals(minXTarget, fixture.camera().getMinXTarget() & 0xFFFF);
        assertEquals(maxXTarget, fixture.camera().getMaxXTarget() & 0xFFFF);
        assertEquals(minYTarget, fixture.camera().getMinYTarget() & 0xFFFF);
        assertEquals(GameServices.level().getCurrentLevel().getMaxY(),
                fixture.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(fixture.camera().getX(), fixture.camera().getXCopy());
        assertEquals(fixture.camera().getY(), fixture.camera().getYCopy());
    }

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
    void resultsOwnedReloadAndTitleLifecycleSupportsWidthsDonationsAndEveryTeamShape() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        var graphics = GameServices.graphics();
        int previousWidth = graphics.getViewportWidth();
        String previousSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        record CompatibilityCase(int width, String donor, String sidekicks, int expectedSidekicks) { }
        record TeamShape(String codes, int count) { }
        List<CompatibilityCase> cases = new java.util.ArrayList<>();
        List<TeamShape> teams = List.of(
                new TeamShape("", 0),
                new TeamShape("tails", 1),
                new TeamShape("tails,knuckles", 2),
                new TeamShape("tails,knuckles,sonic", 3),
                new TeamShape("tails,tails", 2));
        for (int width : List.of(320, 352, 400, 528, 800)) {
            for (String donor : List.of("off", "s1", "s2")) {
                for (TeamShape team : teams) {
                    cases.add(new CompatibilityCase(width, donor, team.codes(), team.count()));
                }
            }
        }
        // Must be last: rebuild a native-off session after all 75 combinations,
        // proving no donor/team/viewport state leaks.
        cases.add(new CompatibilityCase(320, "off", "", 0));
        try {
            for (CompatibilityCase compatibility : cases) {
                configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                        compatibility.sidekicks());
                configuration.clearSessionOverrides();
                configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,
                        compatibility.width());
                configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,
                        !compatibility.donor().equals("off"));
                configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,
                        compatibility.donor().equals("off") ? "" : compatibility.donor());
                SessionManager.clear();

                HeadlessTestFixture fixture = routeBoundaryFixture();
                graphics.setViewport(0, 0, compatibility.width(), 224);
                List<AbstractPlayableSprite> players = new java.util.ArrayList<>();
                players.add(fixture.sprite());
                players.addAll(GameServices.sprites().getSidekicks());
                assertEquals(compatibility.expectedSidekicks() + 1, players.size());
                if (compatibility.sidekicks().equals("tails,tails")) {
                    assertEquals(List.of("tails_p2", "tails_p3"),
                            GameServices.sprites().getSidekicks().stream()
                                    .map(AbstractPlayableSprite::getCode).toList(),
                            "duplicate-character bootstrap must retain distinct participants/banks");
                }
                for (int i = 0; i < players.size(); i++) {
                    // Native end-arena position: the real fight finishes to the right
                    // of the pre-arena fixture origin before the sign/results lock.
                    players.get(i).setCentreX((short) (BOSS_X + 0x65 - i * 0x10));
                    players.get(i).setCentreY((short) PRE_ARENA_Y);
                }
                int[] beforeX = new int[players.size()];
                int[] beforeY = new int[players.size()];
                int[] seamlessBoundaryCount = {0};
                int cameraWidth = fixture.camera().getWidth() & 0xFFFF;
                fixture.camera().setMinX((short) 0x2E20);
                fixture.camera().setMaxX((short) 0x2EA0);
                fixture.camera().setMinY((short) 0x0540);
                fixture.camera().setMaxY((short) 0x0540);
                fixture.camera().setMinXTarget((short) 0x2E20);
                fixture.camera().setMaxXTarget((short) 0x2EA0);
                fixture.camera().setMinYTarget((short) 0x0540);
                fixture.camera().setMaxYTarget((short) 0x0540);
                fixture.camera().setX((short) 0x2EA0);
                fixture.camera().setY((short) 0x0540);
                // The real fight spends many frames at the arena camera before Results.
                // Give the Act-1 placement cursors that same production camera frame so
                // stale pre-arena occupants are not carried across the synchronous reload.
                fixture.stepFrame(false, false, false, false, false);
                players.forEach(player -> {
                    player.setControlLocked(true);
                    ObjectControlState.nativeBit7FullControl().applyTo(player);
                });
                for (int i = 0; i < GameServices.sprites().getSidekicks().size(); i++) {
                    AbstractPlayableSprite expectedLeader = i == 0
                            ? fixture.sprite() : GameServices.sprites().getSidekicks().get(i - 1);
                    assertSame(expectedLeader,
                            GameServices.sprites().getSidekicks().get(i).getCpuController().getLeader(),
                            "team bootstrap must retain the daisy-chain leader for " + compatibility);
                }
                fixture.gameplayMode().setRewindBoundaryReporter(boundary -> {
                    if (boundary != RewindBoundary.SEAMLESS_LEVEL_TRANSITION) return;
                    if (seamlessBoundaryCount[0]++ == 0) {
                        for (int i = 0; i < players.size(); i++) {
                            beforeX[i] = players.get(i).getCentreX();
                            beforeY[i] = players.get(i).getCentreY();
                        }
                    }
                });

                FbzMinibossInstance completionConsumer = seedConvertedBossCompletionConsumer(fixture);
                S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                        TestEnvironment.objectServices(),
                        () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE, 0));
                GameServices.level().getObjectManager().addDynamicObject(results);
                GameServices.gameState().setEndOfLevelActive(true);
                for (int frame = 0; frame < 120 && GameServices.level().getCurrentAct() == 0; frame++) {
                    for (int i = 0; i < players.size(); i++) {
                        assertFalse(players.get(i).getDead(),
                                "participant died before the transition in " + compatibility);
                    }
                    fixture.stepFrame(false, false, false, false, false);
                }

                assertEquals(1, GameServices.level().getCurrentAct(),
                        "Obj_LevelResultsCreate did not publish the compatibility transition for "
                                + compatibility);
                assertEquals(0, GameServices.level().getApparentAct());
                assertEquals(2, seamlessBoundaryCount[0],
                        "results publication and completed event tail must bracket the reload");
                assertEquals(cameraWidth, fixture.camera().getWidth() & 0xFFFF,
                        "viewport width must survive the results-owned reload for " + compatibility);
                assertEquals(compatibility.width(), graphics.getViewportWidth());
                assertEquals(0x0022, fixture.camera().getMinX() & 0xFFFF,
                        "the native +2 gradual lock step runs between results publication and ScreenEvents copy");
                assertEquals(0x00A2, fixture.camera().getMaxX() & 0xFFFF,
                        "the paired right-bound gradual step must remain width-independent");
                assertEquals(0x0540, fixture.camera().getMinY() & 0xFFFF);
                assertEquals(0x0540, fixture.camera().getMaxY() & 0xFFFF);
                assertEquals(fixture.camera().getX(),
                        GameServices.level().getRingManager().capture().placementLastCameraX(),
                        "Load_Rings must window from the copied post-offset camera");
                assertEquals(!compatibility.donor().equals("off"),
                        configuration.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
                assertEquals(compatibility.donor().equals("off") ? "" : compatibility.donor(),
                        configuration.getString(SonicConfiguration.CROSS_GAME_SOURCE));
                for (int i = 0; i < players.size(); i++) {
                    assertEquals((beforeX[i] - 0x2E00) & 0xFFFF,
                            players.get(i).getCentreX() & 0xFFFF,
                            "every participant must receive the native X offset for " + compatibility);
                    assertEquals(beforeY[i] & 0xFFFF, players.get(i).getCentreY() & 0xFFFF,
                            "the FBZ transition must preserve participant Y for " + compatibility);
                    assertTrue(players.get(i).isControlLocked(),
                            "ScreenEvents must preserve the sign/results control lock for " + compatibility);
                }
                for (int i = 0; i < GameServices.sprites().getSidekicks().size(); i++) {
                    AbstractPlayableSprite expectedLeader = i == 0
                            ? fixture.sprite() : GameServices.sprites().getSidekicks().get(i - 1);
                    assertSame(expectedLeader,
                            GameServices.sprites().getSidekicks().get(i).getCpuController().getLeader(),
                            "seamless reload must preserve the daisy chain for " + compatibility);
                }
                fixture.stepFrame(false, false, false, false, false);
                ObjectManager act2Objects = GameServices.level().getObjectManager();
                List<FbzDezPlayerLauncherObjectInstance> launchers =
                        act2Objects.activeObjectsOfType(FbzDezPlayerLauncherObjectInstance.class);
                if (compatibility.width() == 320) {
                    assertEquals(1, launchers.size(),
                            "the native-width next ExecuteObjects frame must materialize exactly one "
                                    + "shifted FBZ2 launcher for " + compatibility);
                } else if (launchers.isEmpty()) {
                    int firstLauncherX = GameServices.level().getCurrentLevel().getObjects().stream()
                            .filter(spawn -> spawn.objectId()
                                    == Sonic3kObjectIds.FBZ_DEZ_PLAYER_LAUNCHER)
                            .mapToInt(spawn -> spawn.x()).min().orElseThrow();
                    int visibleRight = (fixture.camera().getX() & 0xFFFF)
                            + (fixture.camera().getWidth() & 0xFFFF);
                    assertTrue(firstLauncherX >= visibleRight,
                            "widescreen must never omit a launcher already inside its visible route; "
                                    + compatibility + " launcherX=0x"
                                    + Integer.toHexString(firstLauncherX) + " visibleRight=0x"
                                    + Integer.toHexString(visibleRight) + " cameraY=0x"
                                    + Integer.toHexString(fixture.camera().getY() & 0xFFFF)
                                    + " minY=0x"
                                    + Integer.toHexString(fixture.camera().getMinY() & 0xFFFF)
                                    + " activeSpawns=" + act2Objects.getActiveSpawns().stream()
                                    .map(spawn -> "0x" + Integer.toHexString(spawn.objectId())
                                            + "@(0x" + Integer.toHexString(spawn.x()) + ",0x"
                                            + Integer.toHexString(spawn.y()) + ")").toList());
                }

                for (int frame = 0; frame < 2_000 && !results.isDestroyed(); frame++) {
                    for (AbstractPlayableSprite player : players) {
                        assertFalse(player.getDead(),
                                "widescreen/donation compatibility exposed a fatal route gap in "
                                        + compatibility);
                    }
                    fixture.stepFrame(false, false, false, false, false);
                }
                assertTrue(results.isDestroyed(),
                        "the carried results/title owner did not complete for " + compatibility);
                assertEquals(1, GameServices.level().getCurrentAct());
                assertEquals(1, GameServices.level().getApparentAct());
                assertTrue(GameServices.module().getTitleCardProvider().isComplete());
                assertTrue(GameServices.gameState().isEndOfLevelFlag(),
                        "the external title gameplay owner must publish End_of_level_flag");
                assertTrue(completionConsumer.resultsObserved(),
                        "the converted native boss root must consume results completion");
                assertTrue(GameServices.level().getObjectManager().getActiveObjects()
                                .contains(completionConsumer)
                                || booleanField(completionConsumer, "act2SizeWorkersSpawned"),
                        "the converted boss completion consumer must remain in its carried SST slot until "
                                + "Change_Act2Sizes; " + compatibility + " phase="
                                + intField(completionConsumer, "phaseOrdinal")
                                + " destroyed=" + completionConsumer.isDestroyed());
                assertEveryParticipantReleasedOrCpuOwned(players, compatibility.toString(),
                        "immediately after results observation");
                await(fixture, 4, () -> booleanField(completionConsumer, "act2SizeWorkersSpawned"),
                        "the completion consumer did not observe the carried title End_of_level_flag; phase="
                                + intField(completionConsumer, "phaseOrdinal")
                                + " end=" + GameServices.gameState().isEndOfLevelFlag()
                                + " destroyed=" + completionConsumer.isDestroyed());
                assertTrue(booleanField(completionConsumer, "act2SizeWorkersSpawned"),
                        "the completion consumer must launch Change_Act2Sizes workers");
                assertEquals(3, GameServices.level().getObjectManager()
                                .activeObjectsOfType(FbzAct2CameraResizeWorker.class).size(),
                        "Change_Act2Sizes must allocate its exact three-worker prefix");
                assertEveryParticipantReleasedOrCpuOwned(players, compatibility.toString(),
                        "after Change_Act2Sizes worker allocation");
            }
        } finally {
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, previousSidekicks);
            configuration.clearSessionOverrides();
            graphics.setViewport(0, 0, previousWidth, 224);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    @Test
    void convertedBossControlCleanupVisitsEveryConfiguredParticipantBeforeNextCpuTick() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        String previousSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        try {
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    "tails,knuckles,sonic,tails");
            SessionManager.clear();

            HeadlessTestFixture fixture = routeBoundaryFixture();
            FbzMinibossInstance boss = awaitPlacedBoss(
                    fixture, GameServices.level().getObjectManager(), 60);
            List<AbstractPlayableSprite> participants = new java.util.ArrayList<>();
            participants.add(fixture.sprite());
            participants.addAll(GameServices.sprites().getSidekicks());
            assertEquals(5, participants.size(),
                    "the cleanup checkpoint must cover P1 plus every configured sidekick");

            Set<AbstractPlayableSprite> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            for (AbstractPlayableSprite participant : participants) {
                assertTrue(identities.add(participant),
                        "the configured cleanup roster must contain each participant identity exactly once");
            }
            List<?> queried = TestEnvironment.objectServices().playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
            assertEquals(participants.size(), queried.size(),
                    "ALL_ENGINE_PLAYERS must expose the exact configured roster to the boss cleanup");
            for (int i = 0; i < participants.size(); i++) {
                assertSame(participants.get(i), queried.get(i),
                        "boss cleanup traversal must retain exact participant identity/order at index " + i);
            }

            participants.forEach(participant -> {
                participant.setControlLocked(true);
                ObjectControlState.nativeBit7FullControl().applyTo(participant);
            });
            invokeNoArg(boss, "restoreAllPlayerControls");

            for (int i = 0; i < participants.size(); i++) {
                AbstractPlayableSprite participant = participants.get(i);
                assertFalse(participant.isControlLocked(),
                        "boss cleanup must release configured participant " + i + " before the next CPU tick");
                assertFalse(participant.isObjectControlled(),
                        "boss cleanup must clear native object control for configured participant " + i);
                assertFalse(participant.isObjectControlSuppressesMovement(),
                        "boss cleanup must clear native $81 movement suppression for configured participant " + i);
            }
        } finally {
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, previousSidekicks);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    private static void assertEveryParticipantReleasedOrCpuOwned(
            List<AbstractPlayableSprite> players, String compatibility, String phase) {
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            AbstractPlayableSprite player = players.get(playerIndex);
            if (playerIndex == 0 || (!player.isControlLocked() && !player.isObjectControlled())) {
                assertFalse(player.isControlLocked(),
                        "P1 and unowned sidekicks must remain released " + phase + "; " + compatibility
                                + " player[" + playerIndex + "]=" + player.getCode());
                assertFalse(player.isObjectControlled(),
                        "native object control must remain clear " + phase + " for " + compatibility
                                + " player[" + playerIndex + "]=" + player.getCode());
                continue;
            }

            assertTrue(player.isCpuControlled(),
                    "only a CPU sidekick may reacquire control after boss cleanup; " + compatibility
                            + " player[" + playerIndex + "]=" + player.getCode());
            SidekickCpuController controller = player.getCpuController();
            assertNotNull(controller);
            assertTrue(controller.getState() != SidekickCpuController.State.NORMAL,
                    "NORMAL sidekick control would be a stale boss owner " + phase + "; " + compatibility
                            + " player[" + playerIndex + "]=" + player.getCode());
            assertTrue(player.isControlLocked(),
                    "non-NORMAL CPU respawn ownership must apply the complete native $81 lock");
            assertTrue(player.isObjectControlled(),
                    "non-NORMAL CPU respawn ownership must apply native object control");
            assertFalse(player.isObjectControlAllowsCpu(),
                    "native $81 must retain bit-7 ownership rather than the CPU-allowed bit-6 form");
            assertTrue(player.isObjectControlSuppressesMovement(),
                    "native $81 must retain its movement-suppression bit");
            assertSame(players.get(playerIndex - 1), controller.getLeader(),
                    "post-results CPU reacquisition must preserve the configured daisy-chain identity; "
                            + compatibility + " player[" + playerIndex + "]=" + player.getCode());
        }
    }

    @Test
    void placedBossAutomaticallyReachesSignLandingResultsCompletionAndEventsFg5() throws Exception {
        HeadlessTestFixture fixture = routeBoundaryFixture();
        var owningSession = fixture.gameplayMode();
        ObjectManager objects = GameServices.level().getObjectManager();
        var act1Rings = List.copyOf(GameServices.level().getCurrentLevel().getRings());
        CheckpointState checkpoint = (CheckpointState) GameServices.level().getCheckpointState();
        checkpoint.saveCheckpoint(7, PRE_ARENA_X, PRE_ARENA_Y, false);
        GameServices.level().setBonusStageReturnCheckpointIndex(7);
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

        Set<ObjectInstance> resultsChildrenAtPublication =
                Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<ObjectInstance, int[]> resultsWordsAtPublication = new IdentityHashMap<>();
        IdentityHashMap<ObjectInstance, int[]> resultsWordsAfterReloadTail = new IdentityHashMap<>();
        owningSession.setRewindBoundaryReporter(boundary -> {
            if (boundary != RewindBoundary.SEAMLESS_LEVEL_TRANSITION) return;
            ObjectManager boundaryObjects = GameServices.level().getObjectManager();
            IdentityHashMap<ObjectInstance, int[]> destination =
                    GameServices.level().getCurrentAct() == 0
                            ? resultsWordsAtPublication : resultsWordsAfterReloadTail;
            if (!destination.isEmpty()) return;
            for (S3kResultsElementObjectInstance child
                    : boundaryObjects.activeObjectsOfType(S3kResultsElementObjectInstance.class)) {
                if (child.parentResults() != results) continue;
                resultsChildrenAtPublication.add(child);
                destination.put(child,
                        new int[]{child.getSlotIndex(), child.getX(), child.getY()});
            }
        });

        boolean transitionObserved = false;
        for (int frame = 0; frame < 30; frame++) {
            Set<ObjectInstance> beforePublication = Collections.newSetFromMap(new IdentityHashMap<>());
            beforePublication.addAll(GameServices.level().getObjectManager().getActiveObjects());
            IdentityHashMap<ObjectInstance, Integer> originalSlots = new IdentityHashMap<>();
            IdentityHashMap<ObjectInstance, int[]> originalPositions = new IdentityHashMap<>();
            beforePublication.forEach(candidate -> {
                if (candidate instanceof com.openggf.level.objects.AbstractObjectInstance object) {
                    originalSlots.put(candidate, object.getSlotIndex());
                    if (candidate instanceof com.openggf.level.objects.RomWorldPositionedObject
                            || candidate.getSpawn() != null) {
                        originalPositions.put(candidate, new int[]{candidate.getX(), candidate.getY()});
                    }
                }
            });
            // Poison the prior frame's scroll output immediately before the
            // real Obj_LevelResultsCreate publication dispatch. The synchronous
            // ScreenEvents reload must replace both words through the ordinary
            // ResetActual -> FBZ_Deform -> ResetEffective -> GoDeform tail
            // (sonic3k.asm:108794-108850), not leave stale Act-1 output.
            var parallaxBeforePublication = GameServices.parallax();
            java.util.Arrays.fill(parallaxBeforePublication.getHScroll(), 0x1234_5678);
            setField(parallaxBeforePublication, "vscrollFactorBG", (short) 0x7777);
            byte[] retainedPlaneBeforePublication =
                    GameServices.level().captureBackgroundVdpPlane();
            int roundedBgBeforePublication = events.getLastRoundedBackgroundY();
            fixture.stepFrame(false, false, false, false, false);
            if (GameServices.level().getCurrentAct() != 1) continue;
            transitionObserved = true;
            assertEquals(12, resultsWordsAtPublication.size(),
                    "Obj_LevelResultsCreate (62591-62614) must allocate all 12 child SSTs "
                            + "during Process_Sprites before ScreenEvents");
            assertEquals(12, resultsWordsAfterReloadTail.size(),
                    "FBZ1BGE_Normal must carry the complete results child family");
            for (ObjectInstance childObject : resultsChildrenAtPublication) {
                S3kResultsElementObjectInstance child =
                        assertInstanceOf(S3kResultsElementObjectInstance.class, childObject);
                int[] publicationWords = resultsWordsAtPublication.get(child);
                int[] tailWords = resultsWordsAfterReloadTail.get(child);
                assertNotNull(tailWords,
                        "the post-reload family must contain the exact publication identity");
                assertEquals(publicationWords[0], tailWords[0]);
                assertEquals(publicationWords[1] + (child.entryIndex() < 2 ? 16 : -16),
                        tailWords[1],
                        "slot-70+ child execution between publication and ScreenEvents is exactly "
                                + "one LevResults_SlideIn step, never the -$2E00 world offset");
                assertEquals(publicationWords[2], tailWords[2]);
                beforePublication.add(child);
                originalSlots.put(child, publicationWords[0]);
                originalPositions.put(child, new int[]{tailWords[1], tailWords[2]});
            }
            assertTrue(results.traceDebugDetails().contains("sig=true"),
                    "the carried results owner must be the writer that published Events_fg_5");
            assertFalse(events.isEventsFg5(),
                    "the same FBZ ScreenEvents call must consume the published Events_fg_5 byte");
            assertEquals(1, GameServices.level().getCurrentAct(),
                    "the publication frame must complete the synchronous FBZ2 reload");
            assertEquals(0, GameServices.level().getApparentAct(),
                    "Apparent_act remains Act 1 until the carried title owner reaches loc_2DD06");
            assertSame(owningSession, SessionManager.getCurrentGameplayMode(),
                    "FBZ ScreenEvents reloads Level state inside the existing gameplay session");
            assertEquals(0x0022, fixture.camera().getMinX() & 0xFFFF,
                    "the publication frame must include the native +2 gradual-lock event tail");
            assertEquals(0x00A2, fixture.camera().getMaxX() & 0xFFFF,
                    "the paired right bound must advance in the same production frame");
            assertEquals(0x0540, fixture.camera().getMinY() & 0xFFFF);
            assertEquals(0x0540, fixture.camera().getMaxY() & 0xFFFF);
            var publicationParallax = GameServices.parallax();
            assertEquals(0xFF60_FFE2, publicationParallax.getHScroll()[0],
                    "FBZ1BGE_GoDeform must replace the poisoned line 0 with FBZ2 state");
            assertEquals(0xFF60_FFBA, publicationParallax.getHScroll()[31]);
            assertEquals(0xFF60_FFC4, publicationParallax.getHScroll()[95]);
            assertEquals(0xFF60_FFA6, publicationParallax.getHScroll()[159]);
            assertEquals(0x028B, publicationParallax.getVscrollFactorBG() & 0xFFFF,
                    "FBZ_Deform must replace the poisoned VScroll before GoDeform");
            assertEquals(70, publicationParallax.getMinScroll());
            assertEquals(130, publicationParallax.getMaxScroll());
            assertEquals(roundedBgBeforePublication, events.getLastRoundedBackgroundY(),
                    "the native Draw_TileRow feed retains its rounded cursor when no row boundary crosses");
            assertEquals(publicationParallax.getVscrollFactorBG() & 0xFF0,
                    events.getLastRoundedBackgroundY(),
                    "Draw_TileRow must consume the post-reload FBZ_Deform BG copy/rounded feed");
            byte[] retainedPlaneAfterReloadTail =
                    GameServices.level().captureBackgroundVdpPlane();
            assertFalse(java.util.Arrays.equals(retainedPlaneBeforePublication,
                            retainedPlaneAfterReloadTail),
                    "Load_Level must replace the retained ring with the FBZ2 level-map source");
            assertRetainedPlaneMatchesFbz2LoadLevelSource(retainedPlaneAfterReloadTail);
            Sonic3kLevel act2Level = assertInstanceOf(Sonic3kLevel.class,
                    GameServices.level().getCurrentLevel());
            assertEquals(List.of(0x1C, 0x01, 0x4E), act2Level.getPatternLoadCueSchedule(),
                    "FBZ1BGE_Normal must queue $1C once, then FBZ2 primary/$4E, with no $1D alias");
            assertEquals(0, act2Level.getPatternLoadCueSchedule().stream()
                            .filter(id -> id == 0x1D).count(),
                    "the request-local secondary-level PLC suppression must omit $1D exactly");
            assertFbz2PaletteSurfaceFromPalPointers13();
            ObjectManager act2Objects = GameServices.level().getObjectManager();
            assertNotSame(objects, act2Objects);
            Set<Class<?>> eligibleBoundaryFamilies = new java.util.HashSet<>();
            for (ObjectInstance candidate : beforePublication) {
                if (!(candidate instanceof com.openggf.level.objects.AbstractObjectInstance object)) continue;
                int originalSlot = originalSlots.get(candidate);
                assertTrue(act2Objects.getActiveObjects().stream().anyMatch(live -> live == candidate),
                        "every live SST identity must survive the FBZ Load_Level call");
                assertEquals(originalSlot, object.getSlotIndex(),
                        "the same identity must retain its exact original SST slot");
                int[] position = originalPositions.get(candidate);
                boolean shifted = originalSlot >= 4 && originalSlot < 94
                        && candidate.participatesInRomWorldTransitionOffset();
                if (shifted) {
                    eligibleBoundaryFamilies.add(candidate.getClass());
                    assertInstanceOf(com.openggf.level.objects.RomWorldPositionedObject.class, candidate);
                    boolean updatesAfterResultsSlot = candidate instanceof FbzMinibossArmChild
                            || candidate instanceof FbzMinibossChainLink;
                    if (!updatesAfterResultsSlot) {
                        assertEquals((position[0] - 0x2E00) & 0xFFFF, candidate.getX() & 0xFFFF,
                                candidate.getClass().getSimpleName() + " native X word shifted exactly once");
                        assertEquals(position[1] & 0xFFFF, candidate.getY() & 0xFFFF,
                                candidate.getClass().getSimpleName() + " native Y word is unchanged");
                    }
                } else {
                    if (position != null) {
                        assertEquals(position[0] & 0xFFFF, candidate.getX() & 0xFFFF,
                                candidate.getClass().getSimpleName() + " outside/bit2-clear X is unchanged");
                        assertEquals(position[1] & 0xFFFF, candidate.getY() & 0xFFFF,
                                candidate.getClass().getSimpleName() + " outside/bit2-clear Y is unchanged");
                    }
                }
            }
            assertEquals(Set.of(
                    FbzMinibossInstance.class,
                    FbzMinibossArmChild.class,
                    FbzMinibossChainLink.class,
                    FbzMinibossPlungerChild.class,
                    FbzMinibossPrisonChild.class,
                    S3kHiddenMonitorInstance.class,
                    S3kSignpostInstance.class,
                    S3kSignpostStubChild.class), eligibleBoundaryFamilies,
                    "real route snapshot is the guard list for every bit2-set FBZ boundary family");
            List<String> newOccupants = act2Objects.getActiveObjects().stream()
                    .filter(candidate -> !beforePublication.contains(candidate))
                    .map(candidate -> candidate.getClass().getSimpleName() + "@" +
                            (candidate instanceof com.openggf.level.objects.AbstractObjectInstance object
                                    ? object.getSlotIndex() : -1))
                    .toList();
            assertTrue(newOccupants.isEmpty(),
                    "FBZ2 placement instantiation belongs to the next ExecuteObjects frame; new="
                            + newOccupants);
            var act2Rings = GameServices.level().getCurrentLevel().getRings();
            assertFalse(act2Rings.isEmpty(), "FBZ2 must expose its authored ring source");
            assertEquals(GameServices.camera().getX(),
                    GameServices.level().getRingManager().capture().placementLastCameraX(),
                    "the full publication frame must include the post-ScreenEvents Load_Rings phase");
            for (var ring : act2Rings) {
                assertEquals(ring, GameServices.level().getRingManager()
                                .resolveCanonicalSpawn(ring.x(), ring.y()),
                        "Load_Rings must install every canonical FBZ2 ring during ScreenEvents");
            }
            var act1OnlyRing = act1Rings.stream()
                    .filter(old -> act2Rings.stream()
                            .noneMatch(next -> next.x() == old.x() && next.y() == old.y()))
                    .findFirst().orElseThrow();
            assertEquals(null, GameServices.level().getRingManager()
                            .resolveCanonicalSpawn(act1OnlyRing.x(), act1OnlyRing.y()),
                    "the publication frame must no longer resolve the replaced FBZ1 ring source");
            assertTrue(GameServices.level().getCheckpointState().isActive(),
                    "Load_Level must preserve Last_star_post_hit until loc_2DD06");
            assertTrue(GameServices.level().isBonusStageReturn(),
                    "bonus return state must remain carried until the title handoff");

            fixture.stepFrame(false, false, false, false, false);
            List<FbzDezPlayerLauncherObjectInstance> launchers =
                    GameServices.level().getObjectManager()
                            .activeObjectsOfType(FbzDezPlayerLauncherObjectInstance.class);
            assertEquals(1, launchers.size(),
                    "the next ExecuteObjects pass must materialize the shifted FBZ2 placement window once");
            Set<Integer> carriedSlots = new java.util.HashSet<>();
            for (ObjectInstance candidate : beforePublication) {
                if (candidate instanceof com.openggf.level.objects.AbstractObjectInstance object) {
                    carriedSlots.add(object.getSlotIndex());
                }
            }
            int firstFreeSlot = 4;
            while (carriedSlots.contains(firstFreeSlot)) firstFreeSlot++;
            assertEquals(firstFreeSlot, launchers.getFirst().getSlotIndex(),
                    "fresh FBZ2 FindFreeObj state must begin after every preserved SST occupant");
            break;
        }
        assertTrue(transitionObserved,
                "Obj_LevelResultsCreate did not publish FBZ1 Events_fg_5 through the real event bridge");
        assertFalse(boss.resultsObserved(),
                "the converted boss waits for completed results, not merely Events_fg_5 publication");

        await(fixture, 2_000, results::isDestroyed,
                "the production S3K results sequence did not finish its tally/wait/exit queue");
        await(fixture, 4, boss::resultsObserved,
                "the converted boss did not observe the completed results lifecycle");
        assertTrue(boss.resultsObserved(),
                "the converted boss must observe the results-owned End_of_level_effect clear");
        assertFalse(GameServices.level().getCheckpointState().isActive(),
                "loc_2DD06 must clear Last_star_post_hit");
        assertFalse(GameServices.level().isBonusStageReturn(),
                "loc_2DD06 must clear the carried bonus-return byte");
        assertTrue(results.traceDebugDetails().contains("sig=true"),
                "the completed carried results owner must retain its one-shot writer latch");
        assertFalse(events.isEventsFg5(),
                "FBZ1 ScreenEvents must clear the publication after the in-call reload");
        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(1, GameServices.level().getApparentAct(),
                "the carried Obj_TitleCard owner must publish Apparent_act=1 at loc_2DD06");
        assertTrue(GameServices.module().getTitleCardProvider().isComplete(),
                "the carried results SST owner must wait for the real in-level title children");
        assertSame(owningSession, SessionManager.getCurrentGameplayMode());
        // A sidekick killed by the real boss may still be in Kill_Character's
        // DEAD_FALLING dispatch when the title owner completes. Let the real
        // CPU routine reach either released play or its native $81 respawn
        // owner before distinguishing legitimate CPU ownership from a stale
        // boss lock.
        await(fixture, 240, () -> allPlayers(fixture).stream()
                        .allMatch(TestFbzAct1RouteHeadless::hasReleasedOrNativeCpuOwnership),
                "a post-title sidekick never left its transient death dispatch");
        assertEveryParticipantReleasedOrCpuOwned(allPlayers(fixture),
                "native production route", "after title completion");

        // A real post-boundary death reload must retain the physical/apparent
        // Act 2 identity and a newly activated FBZ2 checkpoint, while removing
        // the dead title/results presentation and rebuilding object slots.
        ObjectManager beforeAct2Death = GameServices.level().getObjectManager();
        int checkpointX = fixture.sprite().getCentreX() & 0xFFFF;
        int checkpointY = fixture.sprite().getCentreY() & 0xFFFF;
        CheckpointState act2Checkpoint = (CheckpointState) GameServices.level().getCheckpointState();
        act2Checkpoint.saveCheckpoint(9, checkpointX, checkpointY, false);
        fixture.sprite().setControlLocked(false);
        ObjectControlState.none().applyTo(fixture.sprite());
        int killPlane = Math.max(fixture.camera().getMaxY() & 0xFFFF,
                fixture.camera().getMaxYTarget() & 0xFFFF) + 224;
        fixture.sprite().setCentreY((short) (killPlane + 1));
        fixture.stepFrame(false, false, false, false, false);
        assertTrue(fixture.sprite().getDead(),
                "the post-transition route must enter the real Player_LevelBound/Kill_Character path");
        GameServices.level().respawnPlayer();

        assertNotSame(beforeAct2Death, GameServices.level().getObjectManager(),
                "FBZ2 death must rebuild the ObjectManager and its slot allocator");
        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(1, GameServices.level().getApparentAct());
        assertSame(owningSession, SessionManager.getCurrentGameplayMode());
        assertTrue(GameServices.level().getCheckpointState().isActive());
        assertEquals(9, GameServices.level().getCheckpointState().getLastCheckpointIndex());
        assertEquals(checkpointX, fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(checkpointY, fixture.sprite().getCentreY() & 0xFFFF);
        assertFalse(fixture.sprite().getDead());
        assertEquals(0, fixture.sprite().getDeathCountdown());
        assertFalse(fixture.sprite().isControlLocked());
        assertFalse(fixture.sprite().isObjectControlled());
        assertTrue(GameServices.module().getTitleCardProvider().isComplete(),
                "death restart does not create a second in-level Act 2 title owner");
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                        S3kResultsScreenObjectInstance.class).isEmpty(),
                "the completed results/title SST must not survive the FBZ2 death reload");
    }

    private static List<AbstractPlayableSprite> allPlayers(HeadlessTestFixture fixture) {
        List<AbstractPlayableSprite> players = new java.util.ArrayList<>();
        players.add(fixture.sprite());
        players.addAll(GameServices.sprites().getSidekicks());
        return players;
    }

    private static boolean hasReleasedOrNativeCpuOwnership(AbstractPlayableSprite player) {
        if (!player.isControlLocked() && !player.isObjectControlled()) return true;
        return player.isCpuControlled()
                && player.getCpuController() != null
                && player.getCpuController().getState() != SidekickCpuController.State.NORMAL
                && player.isControlLocked()
                && player.isObjectControlled()
                && !player.isObjectControlAllowsCpu()
                && player.isObjectControlSuppressesMovement();
    }

    private static void assertFbz2PaletteSurfaceFromPalPointers13() throws Exception {
        var rom = GameServices.rom().getRom();
        int entry = Sonic3kConstants.PAL_POINTERS_ADDR
                + 0x13 * Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
        int source = rom.read32BitAddr(entry) & 0x00FF_FFFF;
        int ramDest = rom.read16BitAddr(entry + 4) & 0xFFFF;
        int byteCount = ((rom.read16BitAddr(entry + 6) & 0xFFFF) + 1) * 4;
        int startLine = (ramDest & 0xFF) / Palette.PALETTE_SIZE_IN_ROM;
        byte[] bytes = rom.readBytes(source, byteCount);
        for (int offset = 0; offset < byteCount; offset += Palette.PALETTE_SIZE_IN_ROM) {
            int line = startLine + offset / Palette.PALETTE_SIZE_IN_ROM;
            Palette expected = new Palette();
            expected.fromSegaFormat(java.util.Arrays.copyOfRange(
                    bytes, offset, offset + Palette.PALETTE_SIZE_IN_ROM));
            Palette actual = GameServices.level().getCurrentLevel().getPalette(line);
            for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
                assertEquals(expected.getColor(color).r, actual.getColor(color).r,
                        "PalPointers $13 red channel line=" + line + " color=" + color);
                assertEquals(expected.getColor(color).g, actual.getColor(color).g,
                        "PalPointers $13 green channel line=" + line + " color=" + color);
                assertEquals(expected.getColor(color).b, actual.getColor(color).b,
                        "PalPointers $13 blue channel line=" + line + " color=" + color);
            }
        }
    }

    private static void assertRetainedPlaneMatchesFbz2LoadLevelSource(byte[] retainedPlane) {
        assertEquals(64 * 32 * 4, retainedPlane.length);
        for (int tileY = 0; tileY < 32; tileY++) {
            for (int tileX = 0; tileX < 64; tileX++) {
                int planeCell = tileY * 64 + tileX;
                int descriptor = GameServices.level().getBackgroundTileDescriptorAtWorld(
                        tileX * 8, tileY * 8);
                int offset = planeCell * 4;
                int patternIndex = descriptor & 0x7FF;
                int expectedG = ((patternIndex >>> 8) & 0x7)
                        | (((descriptor >>> 13) & 0x3) << 3)
                        | ((descriptor & 0x0800) != 0 ? 0x20 : 0)
                        | ((descriptor & 0x1000) != 0 ? 0x40 : 0)
                        | ((descriptor & 0x8000) != 0 ? 0x80 : 0);
                assertEquals(patternIndex & 0xFF, retainedPlane[offset] & 0xFF,
                        "FBZ2 Load_Level Plane-B pattern at " + tileX + "," + tileY);
                assertEquals(expectedG, retainedPlane[offset + 1] & 0xFF,
                        "FBZ2 Load_Level Plane-B attributes at " + tileX + "," + tileY);
                assertEquals(0, retainedPlane[offset + 2] & 0xFF);
                assertEquals(0xFF, retainedPlane[offset + 3] & 0xFF);
            }
        }
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

    /**
     * Starts the real converted boss root at its native
     * END_SIGN_AWAIT_RESULTS routine. The matrix is about the boundary/title
     * contract, so replaying the identical six-hit fight in every one of 75
     * environment cells would add no coverage; the non-matrix route test above
     * reaches this routine exclusively through production combat.
     */
    private static FbzMinibossInstance seedConvertedBossCompletionConsumer(
            HeadlessTestFixture fixture) {
        FbzMinibossInstance boss = awaitPlacedBoss(
                fixture, GameServices.level().getObjectManager(), 60);
        setField(boss, "initialized", true);
        setField(boss, "initialChildrenSpawned", true);
        setField(boss, "bossSlotConverted", true);
        setField(boss, "signSpawned", true);
        setField(boss, "defeated", true);
        setField(boss, "rootControlBits", 1 << FbzMinibossInstance.ROOT_DEFEAT_RELEASE);
        setField(boss, "phaseOrdinal", 6); // END_SIGN_AWAIT_RESULTS
        return boss;
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

    private static boolean booleanField(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void invokeNoArg(Object owner, String name) {
        try {
            var method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(owner);
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
