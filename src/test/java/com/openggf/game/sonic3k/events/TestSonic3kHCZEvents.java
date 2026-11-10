package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;

import com.openggf.audio.AudioManager;

import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.TestEnvironment;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ScrollHandlerProvider.ZoneConstants;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.level.Level;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.sonic3k.objects.HczTransitionBubbleInstance;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.openggf.level.SeamlessLevelTransitionRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kHCZEvents {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @AfterEach
    void tearDown() throws IOException {
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        deleteRecursively(Path.of("saves").resolve("test_hcz_transition_save"));
    }

    @Test
    void act1TransitionWritesProgressionSaveForActiveSlot() throws Exception {
        SessionManager.clear();

        String gameCode = "test_hcz_transition_save";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule sessionModule = new DelegatingGameModule(new Sonic3kGameModule(), "test:hcz-transition") {
            @Override
            public SaveSnapshotProvider getSaveSnapshotProvider() {
                return (reason, ctx) -> Map.of("marker", "hcz_transition");
            }

            @Override
            public GameRng.Flavour rngFlavour() {
                return GameRng.Flavour.S3K;
            }
        };

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of("tails")), 1, 0);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(sessionModule, saveContext);
        TestEnvironment.activeGameplayMode();

        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(0);
        events.setEventsFg5(true);
        GameServices.gameState().setEndOfLevelFlag(true);

        var timing = GameServices.hardwareTiming();
        serviceBoundary(HardwareServiceBoundary.VINT_SERVICE);
        serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
        events.update(0, 0);
        assertEquals(2,
                timing.incompleteCount(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE),
                "HCZ transition must queue chunks and blocks before its KosM art");
        serviceBoundary(HardwareServiceBoundary.POST_OBJECTS);
        assertFalse(events.isTransitionRequested());

        int publicationFrame = -1;
        int transitionFrame = -1;
        for (int frame = 1;
                frame < 100_000 && !events.isTransitionRequested();
                frame++) {
            // Kos_modules_left is decremented only by Process_Kos_Module_Queue
            // (docs/skdisasm/sonic3k.asm:2782-2784), which LevelLoop reaches at 7908 --
            // after ScreenEvents (7898) and the object pass (7900-7906). Neither
            // VINT_SERVICE nor Process_Kos_Queue (7887, decompression queue only) can
            // advance it.
            int beforeFrame = timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE);
            serviceBoundary(HardwareServiceBoundary.VINT_SERVICE);
            assertEquals(beforeFrame,
                    timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                    "VINT_SERVICE does not run the module state step");
            serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertEquals(beforeFrame,
                    timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                    "Process_Kos_Queue (7887) advances only the decompression queue and "
                            + "must not retire an HCZ2 secondary art archive");
            events.update(0, frame);
            if (publicationFrame >= 0 && events.isTransitionRequested()) {
                transitionFrame = frame;
            }
            serviceBoundary(HardwareServiceBoundary.POST_OBJECTS);
            int afterState = timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE);
            assertTrue(afterState <= beforeFrame,
                    "module readiness must never regress across a LevelLoop iteration");
            if (beforeFrame > 0 && afterState == 0) {
                assertFalse(events.isTransitionRequested(),
                        "retirement alone cannot request the transition — only the "
                                + "ScreenEvents dispatch that observes it may");
                publicationFrame = frame;
            }
        }

        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertTrue(events.isTransitionRequested());
        assertEquals(publicationFrame + 1, transitionFrame,
                "Process_Kos_Module_Queue (sonic3k.asm:7908) runs after ScreenEvents "
                        + "(7898), so the retiring iteration's own dispatch cannot see "
                        + "the retirement — the next one is the first that may consume it");
        // In-game saves encode on the frame but write on the save-writer thread.
        com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
        assertTrue(Files.exists(saveDir.resolve("slot1.json")));
    }

    @Test
    void act1TransitionKeepsTheArenaXLockUntilTheCarrierReleases() {
        // HCZ's miniboss arena locks both X bounds at $3680 before the transition.
        GameServices.camera().setX((short) 0x3680);
        GameServices.camera().setMinX((short) 0x3680);
        GameServices.camera().setMaxX((short) 0x3680);
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(0);
        events.setEventsFg5(true);
        GameServices.gameState().setEndOfLevelFlag(true);
        for (int frame = 0; frame < 100_000 && !events.isTransitionRequested(); frame++) {
            serviceBoundary(HardwareServiceBoundary.VINT_SERVICE);
            serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
            events.update(0, frame);
            serviceBoundary(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertTrue(events.isTransitionRequested());
        SeamlessLevelTransitionRequest request = GameServices.level().consumeSeamlessTransitionRequest();
        assertNotNull(request);
        assertEquals(com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE,
                request.inLevelTitleCardResetAdditionalDispatches(),
                "Obj_TitleCardWait consumes its actual children's movement latch after HCZ Load_Level");
        int lock = 0x3680 - 0x3600;
        assertEquals(lock, request.postTransitionMinX());
        assertEquals(lock, request.postTransitionMaxX());
        // Change_Act2Sizes stores HCZ2's sizes without applying them
        // (sonic3k.asm:180671-180686); only loc_6A8AE's Make_LevelSizeObj
        // later widens the bounds. The reload must not leave act-2 targets
        // for the camera to ease toward in the meantime.
        assertEquals(lock, request.postTransitionMinXTarget(),
                "the live min-X lock has no act-2 target before the carrier releases");
        assertEquals(lock, request.postTransitionMaxXTarget(),
                "the live max-X lock has no act-2 target before the carrier releases");
    }

    private static void serviceBoundary(HardwareServiceBoundary boundary) {
        HardwareBoundaryPump.service(boundary);
    }

    @Test
    void wallShakePublishesPreviousSetupAndRestoresThePreparedOffset() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();
        SwScrlHcz handler = installEventScroll(events);

        // HCZ2_ScreenEvent reads the previous ShakeScreen_Setup result.
        tickAct2(events, 3);
        assertEquals(0, events.screenPublication().shakeOffset(), "first screen event precedes first setup");
        compose(handler, 3);
        assertEquals(0, handler.getShakeOffsetY());
        var saved = events.captureHczState();
        tickAct2(events, 4);
        assertEquals(3, events.screenPublication().shakeOffset(), "ScreenShakeArray2[3], not [4]");
        tickAct2(events, 5);
        assertEquals(1, events.screenPublication().shakeOffset(), "ScreenShakeArray2[4]");
        events.restoreHczState(saved);
        tickAct2(events, 4);
        assertEquals(3, events.screenPublication().shakeOffset(), "prepared native offset survives rewind");
        compose(handler, 4);
        assertEquals(3, handler.getShakeOffsetY(), "renderer reads the restored completed publication");
    }

    @Test
    void naturalWallStopPublishesThePreviousContinuousThenTheSignedTimedSequence() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();
        int nextFrame = advanceWallToStop(events);

        // 1229 subtractions of $14000 cross -$600 without clamping the fraction.
        assertEquals(-0x6004000, events.getWallOffsetFixed());
        assertEquals(-0x601, events.getWallOffsetPixels(), "the signed high word keeps the overshoot");
        assertEquals(0x0D, events.getShakeTimer(), "the stop installs $E, then its reached setup decrements it");
        assertTrue(events.screenPublication().shakeOffset() >= 0 && events.screenPublication().shakeOffset() <= 3,
                "the stop screen still consumes the preceding continuous setup");

        // ScreenShakeArray (sonic3k.asm:104277-104279) supplies these signed
        // entries, indexed from 13 down to 0 after the native $0E stop flag.
        int[] timedScreenOffsets = {-4, 4, -3, 3, -3, 3, -2, 2, -2, 2, -1, 1, -1, 1};
        for (int index = 0; index < timedScreenOffsets.length; index++) {
            tickAct2(events, nextFrame++);
            assertEquals(timedScreenOffsets[index], events.screenPublication().shakeOffset(),
                    "retained timed setup " + index);
            assertEquals(Math.max(0, 0x0C - index), events.getShakeTimer(), "one reached setup per dispatch");
            assertEquals(-0x6004000, events.getWallOffsetFixed(), "a stopped wall cannot restart or clamp");
        }
        tickAct2(events, nextFrame);
        assertEquals(0, events.screenPublication().shakeOffset(), "the zero setup follows the final timed screen");
        assertEquals(0, events.getShakeTimer());
    }

    @Test
    void timedWallExitHoldsThePositiveFlagAndBackgroundUntilNativeRefreshResumes() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();
        int frame = advanceWallToStop(events);
        tickAct2(events, frame++); // publish timed table[13] = -4, prepare table[12] = +4
        int offset = events.getWallOffsetFixed();
        var held = events.screenPublication();
        assertEquals(12, events.getShakeTimer());
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag());

        GameServices.camera().setX((short) 0x0C00);
        tickAct2(events, frame++); // end branch consumes the first held row, not another setup
        assertFalse(events.isEventsFg5());
        assertEquals(8, events.getAct2BgRoutine());
        assertEquals(offset, events.getWallOffsetFixed());
        assertEquals(12, events.getShakeTimer());
        assertEquals(4, events.screenPublication().shakeOffset(), "FG consumes the last prepared offset");
        assertEquals(held.backgroundX(), events.screenPublication().backgroundX());
        assertEquals(held.backgroundY(), events.screenPublication().backgroundY());
        assertFalse(GameServices.gameState().isBackgroundCollisionFlag());
        assertTrue(events.isWallChaseBgOverlayActive(), "held wall picture retains its overlay");

        for (int dispatch = 2; dispatch <= 15; dispatch++) {
            tickAct2(events, frame++);
            assertEquals(8, events.getAct2BgRoutine());
            assertEquals(12, events.getShakeTimer(), "held rows never reach ShakeScreen_Setup");
            assertEquals(4, events.screenPublication().shakeOffset());
            assertEquals(held.backgroundX(), events.screenPublication().backgroundX());
            assertEquals(held.backgroundY(), events.screenPublication().backgroundY());
        }
        tickAct2(events, frame++); // sixteenth held row falls through to the first refresh pair
        assertEquals(0x0C, events.getAct2BgRoutine());
        assertEquals(11, events.getShakeTimer(), "first refresh reaches setup exactly once");
        assertEquals(4, events.screenPublication().shakeOffset());
        assertFalse(events.screenPublication().wallPlane());
        assertFalse(events.isWallChaseBgOverlayActive());
        tickAct2(events, frame);
        assertEquals(0x0C, events.getAct2BgRoutine());
        assertEquals(-3, events.screenPublication().shakeOffset(), "next FG consumes timed table[11]");
        assertEquals(10, events.getShakeTimer());
    }

    @Test
    void singleScreenEventDispatchAdvancesAndPublishesThePreviousSetup() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();

        events.update(1, 3);
        assertEquals(4, events.getAct2BgRoutine());
        assertEquals(-0x14000, events.getWallOffsetFixed(), "init falls through to its first fast step");
        assertEquals(0, events.screenPublication().shakeOffset());
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag());

        events.update(1, 4);
        assertEquals(-0x28000, events.getWallOffsetFixed());
        assertEquals(3, events.screenPublication().shakeOffset(), "one dispatch publishes the preceding LF setup");

        GameServices.camera().setX((short) 0x0C00);
        events.update(1, 5);
        assertEquals(8, events.getAct2BgRoutine());
        assertEquals(-0x28000, events.getWallOffsetFixed(), "FG signals before this same BG dispatch");
        assertEquals(1, events.screenPublication().shakeOffset());
        assertFalse(events.isEventsFg5());
        assertFalse(GameServices.gameState().isBackgroundCollisionFlag());
    }

    @Test
    void endPulseIsConsumedByTheSameNativeBackgroundDispatch() throws Exception {
        Sonic3kHCZEvents events = screenEventWallEvents();
        events.update(1, 3);
        int beforeEnd = events.getWallOffsetFixed();

        // HCZ2_Resize raises the signal, then loc_50F64 consumes it before
        // another wall/setup step (sonic3k.asm:106099-106139).
        GameServices.camera().setX((short) 0x0C00);
        events.update(1, 4);
        assertAll("one admitted ScreenEvents dispatch",
                () -> assertFalse(events.isEventsFg5(), "the native FG pulse is consumed once"),
                () -> assertEquals(8, events.getAct2BgRoutine()),
                () -> assertEquals(beforeEnd, events.getWallOffsetFixed(), "no wall step on the end branch"),
                () -> assertFalse(GameServices.gameState().isBackgroundCollisionFlag()));
    }

    @Test
    void normalTransitionHoldsSixteenSoftwareRowDispatchesBeforeRefresh() throws Exception {
        Sonic3kHCZEvents events = screenEventWallEvents();
        events.update(1, 3);
        int heldBgX = events.screenPublication().backgroundX();
        int heldBgY = events.screenPublication().backgroundY();
        GameServices.camera().setX((short) 0x0C00);
        events.update(1, 4); // first transition row is consumed on the end dispatch
        assertEquals(8, events.getAct2BgRoutine());

        // Draw_delayed_rowcount starts at $F. The single-row branch returns
        // while its decremented word is nonnegative: sixteen dispatches,
        // not sixteen fitted movie frames (sonic3k.asm:103487-103503,106124-106148).
        for (int dispatch = 2; dispatch <= 15; dispatch++) {
            GameServices.camera().setX((short) (0x0C00 + dispatch));
            GameServices.camera().setY((short) (0x0600 + dispatch));
            events.update(1, dispatch + 3);
            assertEquals(8, events.getAct2BgRoutine(), "held transition dispatch " + dispatch);
            assertEquals(heldBgX, events.screenPublication().backgroundX(), "hold the preceding BG X publication");
            assertEquals(heldBgY, events.screenPublication().backgroundY(), "hold the preceding BG Y publication");
        }
        events.update(1, 19); // sixteenth single-row step, then the first refresh pair
        assertEquals(0x0C, events.getAct2BgRoutine());
        for (int refresh = 2; refresh <= 7; refresh++) {
            events.update(1, 18 + refresh);
            assertEquals(0x0C, events.getAct2BgRoutine(), "two-row refresh dispatch " + refresh);
        }
        events.update(1, 26); // eighth pair completes Draw_PlaneVertBottomUp
        assertEquals(0x10, events.getAct2BgRoutine());
    }

    @Test
    void endTransitionKeepsPositiveShakeFlagUntilSetupIsReached() throws Exception {
        Sonic3kHCZEvents events = screenEventWallEvents();
        int nextFrame = advanceWallToStop(events);
        int timedBeforeEnd = events.getShakeTimer();
        assertTrue(timedBeforeEnd > 0, "ordinary movement reached the native timed stop");
        GameServices.camera().setX((short) 0x0C00);
        events.update(1, nextFrame);
        assertEquals(timedBeforeEnd, events.getShakeTimer(),
                "loc_50F64 clears only a negative flag; held redraw does not call setup");
    }

    private static Sonic3kHCZEvents screenEventWallEvents() {
        return movingWallEvents();
    }

    @Test
    void missingScrollHandlerDoesNotHoldPreparedShakeWhileTheWallContinuesMoving() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();
        SwScrlHcz handler = installEventScroll(events);
        tickAct2(events, 3);
        compose(handler, 3);
        assertEquals(0, handler.getShakeOffsetY());

        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, null);
        tickAct2(events, 4);
        assertEquals(-0x28000, events.getWallOffsetFixed(), "scroll absence does not hold event motion");
        assertEquals(3, events.screenPublication().shakeOffset(), "producer advances without a consumer");
        assertEquals(0, handler.getShakeOffsetY(), "an unsampled consumer retains only its old picture cache");

        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, handler);
        tickAct2(events, 5);
        compose(handler, 5);
        assertEquals(1, handler.getShakeOffsetY(), "returning consumer reads the current publication");
        tickAct2(events, 6);
        compose(handler, 6);
        assertEquals(2, handler.getShakeOffsetY());
    }

    @Test
    void wallStartedWithoutAScrollHandlerStillPreparesShakeForItsReturn() throws Exception {
        Sonic3kHCZEvents events = movingWallEvents();
        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, null);
        tickAct2(events, 3);
        assertEquals(-0x14000, events.getWallOffsetFixed());

        SwScrlHcz handler = installEventScroll(events);
        tickAct2(events, 4);
        compose(handler, 4);
        assertEquals(3, handler.getShakeOffsetY(), "the producer prepared table[3] without a scroll handler");
        tickAct2(events, 5);
        compose(handler, 5);
        assertEquals(1, handler.getShakeOffsetY());
    }

    @Test
    void act2WallChasePublishesRetainedCollisionAfterItsScreenEvent() {
        Sonic3kHCZEvents events = movingWallEvents();
        var player = GameServices.sprites().getMainPlayable();

        tickAct2(events, 0);
        assertTrue(events.screenPublication().wallPlane());
        assertEquals(0x05FE, events.screenPublication().backgroundX(),
                "wall init falls through and subtracts its first fast movement step");
        assertEquals(new com.openggf.physics.BackgroundPlaneCollisionProvider.State(true, 0x202, 0x500),
                events.backgroundCollisionState());

        tickAct2(events, 1);
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag());
        assertEquals(0x05FD, events.screenPublication().backgroundX());
        assertEquals(1, events.screenPublication().shakeOffset());

        player.setCentreX((short) 0x0B20);
        tickAct2(events, 2);
        assertEquals(0x05FC, events.screenPublication().backgroundX(),
                "BG camera X publishes the completed Events_bg+$00 high word");
        assertEquals(0x0102, events.screenPublication().backgroundY(),
                "the same retained shake is applied to foreground and wall background");
        assertEquals(new com.openggf.physics.BackgroundPlaneCollisionProvider.State(true, 0x204, 0x500),
                events.backgroundCollisionState(), "common vertical shake cancels in the published differences");
    }

    @Test
    void act2WallChaseConsumesFgEndSignalInSameFrame() {
        Sonic3kHCZEvents events = movingWallEvents();
        tickAct2(events, 0);
        tickAct2(events, 1);
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag());
        int beforeEndOffset = events.getWallOffsetFixed();

        GameServices.camera().setX((short) 0x0C00);
        tickAct2(events, 2);
        assertFalse(events.isEventsFg5(), "the same BG dispatch consumes the one-shot FG pulse");
        assertFalse(GameServices.gameState().isBackgroundCollisionFlag());
        assertEquals(8, events.getAct2BgRoutine());
        assertEquals(beforeEndOffset, events.getWallOffsetFixed(), "the end branch never advances wall movement");
        assertEquals(2, events.screenPublication().shakeOffset(), "FG consumes the previously prepared table[1]");
        assertEquals(0, events.getScreenShakeFlag(), "the end branch clears a negative constant flag");
    }

    @Test
    void act2WallChaseUsesFastSpeedAtNativeThreshold() {
        Sonic3kHCZEvents events = movingWallEvents();
        GameServices.sprites().getMainPlayable().setCentreX((short) 0x0A88);
        tickAct2(events, 0);
        assertEquals(-0x14000, events.getWallOffsetFixed(),
                "ROM cmpi/blo selects the fast wall speed when Player_1 x_pos equals $A88");
    }

    @Test
    void wallTriggerAndSpeedRespectBothNativeThresholds() {
        Sonic3kHCZEvents events = movingWallEvents();
        var primary = GameServices.sprites().getMainPlayable();
        primary.setCentreX((short) 0x067F);
        tickAct2(events, 0);
        assertEquals(0, events.getWallOffsetFixed(), "below $680 cannot start the wall");
        assertEquals(0, events.getScreenShakeFlag());

        primary.setCentreX((short) 0x0680);
        tickAct2(events, 1);
        assertEquals(-0xE000, events.getWallOffsetFixed(), "at $680 starts with the native base step");
        primary.setCentreX((short) 0x0A87);
        tickAct2(events, 2);
        assertEquals(-0x1C000, events.getWallOffsetFixed(), "below $A88 keeps the base step");
        primary.setCentreX((short) 0x0A88);
        tickAct2(events, 3);
        assertEquals(-0x30000, events.getWallOffsetFixed(), "at $A88 selects the fast step");
    }

    @Test
    void wallMovementAndCollisionUseRegisteredPrimaryInsteadOfCameraFocus() {
        Sonic3kHCZEvents events = movingWallEvents();
        var primary = GameServices.sprites().getMainPlayable();
        primary.setCentreX((short) 0x067F);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        sidekick.setCentreX((short) 0x0B00);
        sidekick.setCentreY((short) 0x0700);
        sidekick.setCpuControlled(true);
        sidekick.setObjectRoutineOverride(6);
        GameServices.sprites().addSprite(sidekick, "tails");
        GameServices.camera().setFocusedSprite(sidekick);
        assertSame(primary, GameServices.sprites().getMainPlayable());

        tickAct2(events, 3);
        assertEquals(0, events.getWallOffsetFixed(), "a focused follower beyond the trigger is not P1");
        primary.setCentreX((short) 0x0B00);
        sidekick.setCentreX((short) 0x0100);
        sidekick.setCentreY((short) 0x0500);
        tickAct2(events, 4);
        assertEquals(-0x14000, events.getWallOffsetFixed(), "normal P1 moves despite focused routine >=6");
        assertTrue(events.backgroundCollisionState().active(), "P1's corridor gates collision, not the focus");
    }

    @Test
    void absentPrimaryNeverBorrowsTheFocusedActor() {
        TestablePlayableSprite focused = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        focused.setCentreX((short) 0x0B00);
        focused.setCentreY((short) 0x0700);
        GameServices.camera().setFocusedSprite(focused);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);
        assertNull(GameServices.sprites().getMainPlayable(), "focused actor is deliberately unregistered");
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);

        tickAct2(events, 3);
        assertEquals(0, events.getWallOffsetFixed());
        assertEquals(0, events.getScreenShakeFlag());
        assertFalse(events.backgroundCollisionState().active());
        assertEquals(0x0600, events.screenPublication().backgroundX(), "absent P1 still reaches camera publication");
        assertEquals(0x0100, events.screenPublication().backgroundY());
    }

    @Test
    void nativeRoutineGateHoldsMotionAndFlagButStillPublishesCameraCopies() {
        Sonic3kHCZEvents events = movingWallEvents();
        var primary = GameServices.sprites().getMainPlayable();
        tickAct2(events, 3); // prepare continuous table[3] = 3
        int offset = events.getWallOffsetFixed();
        int flag = events.getScreenShakeFlag();
        assertTrue(flag < 0);
        primary.setObjectRoutineOverride(6);
        assertFalse(primary.getDead(), "raw primary routine, not the managed dead hint, supplies this gate");
        GameServices.camera().setX((short) 0x0900);
        GameServices.camera().setY((short) 0x0700);

        tickAct2(events, 4);
        assertEquals(offset, events.getWallOffsetFixed());
        assertEquals(flag, events.getScreenShakeFlag(), "dead/restart setup retains the flag");
        assertEquals(3, events.screenPublication().shakeOffset(), "FG consumes the preceding prepared offset");
        assertEquals(0x0703, events.screenPublication().foregroundY());
        assertEquals(0x0203, events.screenPublication().backgroundY());
        assertEquals(0x06FE, events.screenPublication().backgroundX());
        assertEquals(new com.openggf.physics.BackgroundPlaneCollisionProvider.State(true, 0x202, 0x500),
                events.backgroundCollisionState(), "routine gate does not suppress copied-word publication");
        tickAct2(events, 5);
        assertEquals(0, events.screenPublication().shakeOffset(), "the reached dead setup prepared zero");
        assertEquals(offset, events.getWallOffsetFixed());
        assertEquals(flag, events.getScreenShakeFlag());
    }

    @Test
    void continuousFlagPreservesTheLowByteAndSetupUsesItsSuppliedLevelClock() {
        Sonic3kHCZEvents events = movingWallEvents();
        events.setShakeTimer(0x000E);
        events.startContinuousScreenShake();
        assertEquals((short) 0xFF0E, events.getScreenShakeFlag(), "native ST changes only the high byte");
        assertNotEquals(3, GameServices.level().getFrameCounter() & 0xFFFF,
                "direct component dispatch deliberately differs from the live manager clock");
        tickAct2(events, 3);
        tickAct2(events, 0x11);
        assertEquals(3, events.screenPublication().shakeOffset(), "previous setup sampled supplied LF 3");
        tickAct2(events, 0x12);
        assertEquals(0, events.screenPublication().shakeOffset(), "LF $11 samples the zero table entry");
        tickAct2(events, 0x13);
        assertEquals(3, events.screenPublication().shakeOffset(), "LF $12 samples the following nonzero entry");
        assertEquals(-0x50000, events.getWallOffsetFixed(), "one movement step per dispatch, independent of clock gaps");
    }

    @Test
    void postTransitionCarrierUsesNativePlayersAndFollowsRomArc() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x07F0);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setX((short) 0x0080);
        player.setCpuControlled(false);
        player.setInWater(true);
        GameServices.sprites().addSprite(player, "sonic");
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x07F8);
        sidekick.setCpuControlled(true);
        sidekick.setInWater(true);
        GameServices.sprites().addSprite(sidekick, "tails");
        TestablePlayableSprite secondSidekick = new TestablePlayableSprite("knuckles", (short) 0x0100, (short) 0x07F8);
        secondSidekick.setCpuControlled(true);
        secondSidekick.setInWater(true);
        GameServices.sprites().addSprite(secondSidekick, "knuckles");

        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        GameServices.water().setDynamicWaterLocked(Sonic3kZoneIds.ZONE_HCZ, 1, true);

        events.startPostTransitionCutscene();

        assertFalse(GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_HCZ, 1),
                "loc_6A7C4 clears the miniboss water lock when a carrier initializes");
        assertCarrierControl(player);
        assertCarrierControl(sidekick);
        assertFalse(secondSidekick.isObjectControlled(),
                "ROM creates carriers only for the native P1/P2 slots");

        int playerStartX = player.getCentreX();
        int playerStartY = player.getCentreY();
        events.update(1, 0);
        assertEquals(playerStartX, player.getCentreX(),
                "loc_6A7C4 installs loc_6A872 without moving on its init dispatch");
        assertEquals(playerStartY, player.getCentreY());

        events.updateRetainedCarrierObjectPass(1);
        assertNotEquals(playerStartX, player.getCentreX());
        assertEquals(playerStartY + 2, player.getCentreY());

        for (int i = 0; i < 64 && events.isCutsceneActive(); i++) {
            events.updateRetainedCarrierObjectPass(1);
        }
        assertFalse(events.isCutsceneActive());
        assertPlainCarrierRelease(player);
        assertPlainCarrierRelease(sidekick);
        assertFalse(secondSidekick.isObjectControlled());
    }

    @Test
    void postTransitionCarrierSwingsAboutTheTargetStoredAtCapture() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x00F6, (short) 0x07F0);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setX((short) 0x0080);
        player.setCpuControlled(false);
        player.setInWater(true);
        GameServices.sprites().addSprite(player, "sonic");

        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        events.startPostTransitionCutscene();
        assertCarrierControl(player);
        int startX = player.getCentreX();
        events.updateRetainedCarrierObjectPass(1);
        assertEquals(startX, player.getCentreX(), "the init dispatch does not move the carrier");

        // loc_6A80C writes $3A = Camera_X_pos+$A0 once (sonic3k.asm:140097).
        // The camera moving afterwards must not move the swing centre that
        // sub_6A916 compares against (sonic3k.asm:140182).
        int target = 0x0080 + 0xA0;
        GameServices.camera().setX((short) 0x0010);

        // sub_6A940: |2*(x - target)| below $100, shifted left 4, signed like x - target.
        int delta = startX - target;
        int velocity = Math.max(0, 0x100 - Math.abs(2 * delta)) << 4;
        if (delta < 0) velocity = -velocity;
        boolean side = startX < target;
        int xFixed = startX << 8;
        int passes = 0;
        while (events.isCutsceneActive() && passes < 64) {
            // sub_6A916 then MoveSprite2.
            boolean current = (xFixed >> 8) < target;
            int acceleration = current ? 0x100 : -0x100;
            velocity += acceleration;
            if (current != side) {
                velocity += acceleration;
                side = current;
            }
            xFixed += velocity;
            events.updateRetainedCarrierObjectPass(1);
            passes++;
            assertEquals(xFixed >> 8, player.getCentreX(), "carrier x after pass " + passes);
        }
        assertTrue(passes > 16, "the arc must cross the stored target at least once: " + passes);
    }

    @Test
    void retainedCarrierTargetsSurviveManagerRestoreAndReplayThroughRelease() throws Exception {
        TestablePlayableSprite p1 = new TestablePlayableSprite("sonic", (short) 0x00F6, (short) 0x07F0);
        TestablePlayableSprite p2 = new TestablePlayableSprite("tails", (short) 0x0140, (short) 0x07E8);
        p2.setCpuControlled(true);
        for (var player : List.of(p1, p2)) {
            player.setInWater(true);
            GameServices.sprites().addSprite(player, player == p1 ? "sonic" : "tails");
        }
        var camera = GameServices.camera();
        camera.setFocusedSprite(p1);
        camera.setX((short) 0x0080);
        camera.setMinX((short) 0x0080);
        camera.setMaxX((short) 0x0080);
        Level level = mock(Level.class);
        when(level.getMaxX()).thenReturn(0x4000);
        when(level.getMaxY()).thenReturn(0x0E00);
        // This local object-pass fixture supplies only the stored act-2 sizes;
        // installing a decoded world is covered by the transition/trace tests.
        Field levelField = com.openggf.level.LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(GameServices.level(), level);
        Sonic3kLevelEventManager manager = new Sonic3kLevelEventManager();
        manager.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        Sonic3kHCZEvents events = manager.getHczEventsForTest();
        events.startPostTransitionCutscene();
        camera.setX((short) 0x0010);
        // Before release and after P1 release: use the real manager sidecar,
        // playable and camera snapshots, restore twice, then replay both arcs.
        for (int checkpoint : new int[] {6, 32}) {
            for (int i = 0; i < checkpoint; i++) events.updateRetainedCarrierObjectPass(1);
            var eventSnapshot = manager.capture();
            var p1Snapshot = p1.captureRewindState();
            var p2Snapshot = p2.captureRewindState();
            var cameraSnapshot = camera.capture();
            List<List<Integer>> expected = carrierReplay(events, p1, p2, 40);
            for (int repeat = 0; repeat < 2; repeat++) {
                manager.restore(eventSnapshot);
                p1.restoreRewindState(p1Snapshot);
                p2.restoreRewindState(p2Snapshot);
                camera.restore(cameraSnapshot);
                assertEquals(0x0120, events.getCarrierP1TargetX());
                assertEquals(0x0120, events.getCarrierP2TargetX());
                assertEquals(expected, carrierReplay(events, p1, p2, 40));
            }
            assertFalse(events.isCutsceneActive());
            assertPlainCarrierRelease(p1);
            assertPlainCarrierRelease(p2);
        }
    }

    private static List<List<Integer>> carrierReplay(Sonic3kHCZEvents events,
            AbstractPlayableSprite p1, AbstractPlayableSprite p2, int passes) {
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < passes; i++) {
            events.updateRetainedCarrierObjectPass(1);
            result.add(List.of(events.getCarrierP1XFixed(), events.getCarrierP1YFixed(),
                    events.getCarrierP1XVelocity(), events.getCarrierP2XFixed(), events.getCarrierP2YFixed(),
                    events.getCarrierP2XVelocity(), (int) p1.getCentreX(), (int) p1.getCentreY(),
                    (int) p2.getCentreX(), (int) p2.getCentreY(), p1.isObjectControlled() ? 1 : 0,
                    p2.isObjectControlled() ? 1 : 0, events.isLevelSizeTransitionActive() ? 1 : 0,
                    (int) GameServices.camera().getMaxX(), (int) GameServices.camera().getMaxXTarget()));
        }
        return result;
    }

    @Test
    void postTransitionControllerSpawnsOneBubbleForEachOfFortyEightDispatches() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0100, (short) 0x07F0);
        player.setCpuControlled(false);
        player.setInWater(true);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setX((short) 0x0080);
        GameServices.sprites().addSprite(player, "sonic");
        int firstRandom = 0x0015_0081;
        AtomicInteger randomDraws = new AtomicInteger();
        IntSupplier randomWords = () -> {
            randomDraws.incrementAndGet();
            return firstRandom;
        };

        RecordingHczEvents events = new RecordingHczEvents(randomWords);
        events.init(1);
        events.startPostTransitionCutscene();

        events.updateRetainedCarrierObjectPass(1);
        assertEquals(0, events.transitionBubbleCount(),
                "the retained carrier init dispatch sets the controller flag for the next pass");

        for (int dispatch = 0; dispatch < 0x30; dispatch++) {
            events.updateRetainedCarrierObjectPass(1);
        }

        assertEquals(0x30, events.transitionBubbleCount(),
                "Obj_Wait $2F produces $30 loc_6A2A0 child-creation dispatches");
        assertEquals(0, events.getTransitionBubbleSpawnFrames());
        HczTransitionBubbleInstance firstBubble = events.firstTransitionBubble();
        int expectedAxisX = 0x0080 + 0xA0;
        assertEquals(expectedAxisX + (byte) firstRandom, firstBubble.getX());
        assertEquals(GameServices.water().getWaterLevelY(Sonic3kZoneIds.ZONE_HCZ, 1)
                        + 8 + ((firstRandom >>> 16) & 0x1F),
                firstBubble.getY());
        assertEquals(0x30, randomDraws.get(),
                "each child must consume exactly one injected Random_Number result");

        events.updateRetainedCarrierObjectPass(1);
        assertEquals(0x30, events.transitionBubbleCount());
    }

    @Test
    void rumbleSamplesLfMinusOneAndTheRawRoutineGateSuppressesAudio() {
        var audio = mock(AudioManager.class);
        Sonic3kHCZEvents events = movingWallEventsWithAudio(audio);
        tickAct2(events, 1);
        verify(audio).playSfx(
                Sonic3kSfx.RUMBLE_2.id);
        tickAct2(events, 16); // LF - 1 is not divisible by $10
        verifyNoMoreInteractions(audio);
        tickAct2(events, 17);
        verify(audio, times(2)).playSfx(
                Sonic3kSfx.RUMBLE_2.id);
        assertEquals(-0x3C000, events.getWallOffsetFixed());

        var primary = GameServices.sprites().getMainPlayable();
        primary.setObjectRoutineOverride(6);
        int flag = events.getScreenShakeFlag();
        GameServices.camera().setX((short) 0x0900);
        GameServices.camera().setY((short) 0x0700);
        tickAct2(events, 33); // cadence would fire without the routine gate
        verifyNoMoreInteractions(audio);
        assertEquals(-0x3C000, events.getWallOffsetFixed());
        assertEquals(flag, events.getScreenShakeFlag());
        assertEquals(0x06FC, events.screenPublication().backgroundX(),
                "the routine gate still publishes camera $900 - $200 plus high word -4");
        assertEquals(0x0200, events.screenPublication().backgroundY());
    }

    @Test
    void stopDispatchCrashesOnceWithoutRumblingOrDiscardingTheFraction() {
        var audio = mock(AudioManager.class);
        Sonic3kHCZEvents events = movingWallEventsWithAudio(audio);
        tickAct2(events, 3); // movement without a rumble slot
        events.setWallOffsetFixed(-0x06004000);
        events.startContinuousScreenShake();

        tickAct2(events, 33); // eligible cadence, but signed stop wins first
        verify(audio).playSfx(
                Sonic3kSfx.CRASH.id);
        verifyNoMoreInteractions(audio);
        assertEquals(-0x06004000, events.getWallOffsetFixed());
        assertEquals(13, events.getScreenShakeFlag(), "$E installed, then one reached setup");
        tickAct2(events, 49);
        verifyNoMoreInteractions(audio);
        assertEquals(-0x06004000, events.getWallOffsetFixed());
        assertEquals(12, events.getScreenShakeFlag());
    }

    private static Sonic3kHCZEvents movingWallEventsWithAudio(AudioManager audio) {
        placePlayer(0x0B00, 0x0700);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0) {
            @Override
            protected AudioManager audio() {
                return audio;
            }
        };
        events.init(1);
        return events;
    }

    @Test
    void freshBackgroundInitUsesTheClearedNativeClockWithoutPreparingShake() {
        var audio = mock(AudioManager.class);
        var events = movingWallEventsWithAudio(audio);
        GameServices.level().setFrameCounter(1); // previous level's still-live clock during load
        events.initializeAct2BackgroundAtLoad(GameServices.sprites().getMainPlayable());
        verifyNoInteractions(audio);
        assertEquals(1, GameServices.level().getFrameCounter(), "init cannot rewrite the live load clock");
        assertEquals(4, events.getAct2BgRoutine());
        assertEquals(-0x14000, events.getWallOffsetFixed());
        assertEquals(0, events.screenPublication().shakeOffset());
        assertFalse(events.backgroundCollisionState().active());
        var saved = java.nio.ByteBuffer.wrap(events.captureHczState());
        assertEquals(0, saved.getInt(saved.limit() - 8), "fresh init never calls shake setup");
        assertEquals(0, saved.getInt(saved.limit() - 4));
    }

    @Test
    void failedWallAllocationIsNotRetriedAfterRestoringItsActualPayload() {
        var attempts = new AtomicInteger();
        placePlayer(0x0B00, 0x0700);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);
        var events = new Sonic3kHCZEvents(() -> 0) {
            @Override
            protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
                attempts.incrementAndGet();
                return null;
            }
        };
        events.init(1);
        tickAct2(events, 1);
        assertEquals(1, attempts.get());
        assertEquals(4, events.getAct2BgRoutine());
        byte[] failedAdmission = events.captureHczState();
        for (int replay = 0; replay < 2; replay++) {
            events.restoreHczState(failedAdmission);
            assertArrayEquals(failedAdmission, events.captureHczState());
            for (int frame = 2; frame < 0x42; frame++) {
                tickAct2(events, frame);
            }
            assertEquals(1, attempts.get(), "state 4 never retries the failed state-0 allocation");
        }
    }

    private static void tickAct2(Sonic3kHCZEvents events, int frame) {
        events.update(1, frame);
    }

    private static Sonic3kHCZEvents movingWallEvents() {
        placePlayer(0x0B00, 0x0700);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        return events;
    }

    private static SwScrlHcz installEventScroll(Sonic3kHCZEvents events) throws Exception {
        SwScrlHcz handler = new SwScrlHcz(null, events::screenPublication);
        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, handler);
        return handler;
    }

    private static void compose(SwScrlHcz handler, int frame) {
        var camera = GameServices.camera();
        handler.update(new int[224], camera.getX(), camera.getY(), frame, 1);
    }

    private static int advanceWallToStop(Sonic3kHCZEvents events) {
        int nextFrame = 0;
        // Reaching the signed stop high word precedes its stop dispatch. Wait
        // until that dispatch installs $E and its setup decrements it to $D.
        while ((!events.isWallStopped() || events.getScreenShakeFlag() <= 0) && nextFrame < 0x800) {
            tickAct2(events, nextFrame++);
        }
        assertTrue(events.isWallStopped(), "ordinary BG dispatch reaches the native stop");
        assertTrue(events.getScreenShakeFlag() > 0, "ordinary stop dispatch installs the positive flag");
        return nextFrame;
    }

    private static AbstractPlayableSprite placePlayer(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setCpuControlled(false);
        GameServices.sprites().addSprite(player, "sonic");
        GameServices.camera().setFocusedSprite(player);
        assertSame(player, GameServices.sprites().getMainPlayable(), "actual registered primary fixture owner");
        return player;
    }

    @Test
    void retainedCarrierUsesItsCaptureTargetAfterTheLiveCameraMoves() {
        var pair = carrierPlayers();
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        events.startPostTransitionCutscene();
        events.updateRetainedCarrierObjectPass(1); // init dispatch installs its movement handler
        GameServices.camera().setX((short) 0);
        events.updateRetainedCarrierObjectPass(1);
        for (var player : pair) {
            assertEquals(0x00F5, player.getCentreX(),
                    "retained target $120: old -$C00 velocity plus $100, rather than a false side crossing");
            assertEquals(0x07F2, player.getCentreY());
        }
    }

    @Test
    void registeredCarrierRestoreRetainsTargetBeforeAndAfterMotion() {
        var pair = carrierPlayers();
        Sonic3kHCZEvents events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        var registry = new com.openggf.game.rewind.RewindRegistry();
        registry.register(new com.openggf.game.rewind.RewindSnapshottable<byte[]>() {
            public String key() { return "hcz-events"; }
            public byte[] capture() {
                return events.captureHczState();
            }
            public void restore(byte[] state) {
                events.restoreHczState(state);
            }
        });
        for (int index = 0; index < pair.size(); index++) {
            var player = pair.get(index);
            String key = "player-" + index;
            registry.register(new com.openggf.game.rewind.RewindSnapshottable<com.openggf.level.objects.PerObjectRewindSnapshot>() {
                public String key() { return key; }
                public com.openggf.level.objects.PerObjectRewindSnapshot capture() { return player.captureRewindState(); }
                public void restore(com.openggf.level.objects.PerObjectRewindSnapshot state) { player.restoreRewindState(state); }
            });
        }
        var before = registry.capture();
        events.startPostTransitionCutscene();
        var pending = registry.capture();
        events.updateRetainedCarrierObjectPass(1);
        events.updateRetainedCarrierObjectPass(1);
        var moving = registry.capture();
        GameServices.camera().setX((short) 0x400);
        events.startPostTransitionCutscene(); // overwrite the live captured target in a divergent future
        registry.restore(pending);
        GameServices.camera().setX((short) 0);
        events.updateRetainedCarrierObjectPass(1);
        events.updateRetainedCarrierObjectPass(1);
        for (var player : pair) assertEquals(0xF5, player.getCentreX());
        registry.restore(moving);
        events.updateRetainedCarrierObjectPass(1);
        for (var player : pair) {
            assertEquals(0xEB, player.getCentreX());
            assertEquals(0x7F4, player.getCentreY());
        }
        registry.restore(before);
        assertFalse(events.isCutsceneActive());
        for (var player : pair) assertFalse(player.isObjectControlled());
        GameServices.camera().setX((short) 0x80);
        events.startPostTransitionCutscene();
        events.updateRetainedCarrierObjectPass(1);
        events.updateRetainedCarrierObjectPass(1);
        for (var player : pair) assertEquals(0xF5, player.getCentreX());
    }

    private static List<TestablePlayableSprite> carrierPlayers() {
        var p1 = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x7F0);
        var p2 = new TestablePlayableSprite("tails", (short) 0x100, (short) 0x7F0);
        GameServices.camera().setFocusedSprite(p1);
        GameServices.camera().setX((short) 0x80);
        p2.setCpuControlled(true);
        for (var player : List.of(p1, p2)) {
            player.setInWater(true);
            GameServices.sprites().addSprite(player, player.getCode());
        }
        return List.of(p1, p2);
    }

    private static void assertCarrierControl(AbstractPlayableSprite sprite) {
        assertTrue(sprite.isObjectControlled());
        assertTrue(sprite.isObjectControlAllowsCpu());
        assertTrue(sprite.isObjectControlSuppressesMovement());
        assertFalse(sprite.isControlLocked());
        assertTrue(sprite.getAir());
        assertEquals(Sonic3kAnimationIds.FLOAT2.id(), sprite.getForcedAnimationId());
        assertEquals((short) 0, sprite.getXSpeed());
        assertEquals((short) 0, sprite.getYSpeed());
        assertEquals((short) 0, sprite.getGSpeed());
    }

    private static void assertPlainCarrierRelease(AbstractPlayableSprite sprite) {
        assertFalse(sprite.isObjectControlled());
        assertFalse(sprite.isObjectControlAllowsCpu());
        assertFalse(sprite.isObjectControlSuppressesMovement());
        assertFalse(sprite.isControlLocked());
        assertFalse(sprite.getAir());
        assertEquals(-1, sprite.getForcedAnimationId());
        assertEquals(5, sprite.getAnimationId());
        assertEquals(0, sprite.getAnimationFrameIndex());
        assertEquals(0, sprite.getAnimationTick());
        assertEquals((short) 0, sprite.getXSpeed());
        assertEquals((short) 0, sprite.getYSpeed());
    }

    private static void installParallaxHandler(int zoneId, ZoneScrollHandler handler) throws Exception {
        ScrollHandlerProvider provider = new ScrollHandlerProvider() {
            @Override
            public void load(com.openggf.data.Rom rom) {
            }

            @Override
            public ZoneScrollHandler getHandler(int zoneIndex) {
                return zoneIndex == zoneId ? handler : null;
            }

            @Override
            public ZoneConstants getZoneConstants() {
                return mock(ZoneConstants.class);
            }
        };
        ParallaxManager parallaxManager = GameServices.parallax();
        Field field = ParallaxManager.class.getDeclaredField("scrollProvider");
        field.setAccessible(true);
        field.set(parallaxManager, provider);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private static final class RecordingHczEvents extends Sonic3kHCZEvents {
        private final List<ObjectInstance> spawnedObjects = new ArrayList<>();

        private RecordingHczEvents(IntSupplier randomWordSource) {
            super(randomWordSource);
        }

        @Override
        protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
            T object = factory.get();
            spawnedObjects.add(object);
            return object;
        }

        private long transitionBubbleCount() {
            return spawnedObjects.stream()
                    .filter(HczTransitionBubbleInstance.class::isInstance)
                    .count();
        }

        private HczTransitionBubbleInstance firstTransitionBubble() {
            return spawnedObjects.stream()
                    .filter(HczTransitionBubbleInstance.class::isInstance)
                    .map(HczTransitionBubbleInstance.class::cast)
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Test
    void directedEntryReadinessFollowsNativeWallTransitionWithoutDispatchingIt() throws Exception {
        AbstractPlayableSprite player = placePlayer(0x0B00, 0x0700);
        var camera = GameServices.camera();
        camera.setX((short) 0x0800);
        camera.setY((short) 0x0600);
        Sonic3kLevelEventManager manager = new Sonic3kLevelEventManager();
        manager.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        Sonic3kHCZEvents events = manager.getHczEventsForTest();
        SwScrlHcz handler = installEventScroll(events);
        List<DirectedEntryState> states = new ArrayList<>();

        tickAct2(events, 0);
        camera.setX((short) 0x0BFF);
        states.add(new DirectedEntryState("wall before $C00", manager.capture(), camera.capture(), true));
        camera.setX((short) 0x0C00);
        states.add(new DirectedEntryState("at $C00 before FG dispatch", manager.capture(), camera.capture(), false));
        camera.setX((short) 0x0C01);
        states.add(new DirectedEntryState("after $C00 before FG dispatch", manager.capture(), camera.capture(), false));

        tickAct2(events, 1); // end dispatch consumes the first of sixteen held rows
        assertFalse(events.isEventsFg5());
        assertEquals(8, events.getAct2BgRoutine());
        camera.setX((short) 0x0BFF);
        states.add(new DirectedEntryState("held transition below $C00 after pulse consumption",
                manager.capture(), camera.capture(), false));
        camera.setX((short) 0x0C00);
        for (int dispatch = 2; dispatch <= 15; dispatch++) {
            tickAct2(events, dispatch);
            assertEquals(8, events.getAct2BgRoutine());
        }
        states.add(new DirectedEntryState("last held row before continuation",
                manager.capture(), camera.capture(), false));
        tickAct2(events, 16); // last held row, first refresh pair
        assertEquals(0x0C, events.getAct2BgRoutine());
        assertEquals(SwScrlHcz.Hcz2BgPhase.NORMAL, handler.getHcz2BgPhase());
        camera.setX((short) 0x0BFF);
        states.add(new DirectedEntryState("normal deformation awaiting refresh below $C00",
                manager.capture(), camera.capture(), false));
        camera.setX((short) 0x0C00);
        for (int refresh = 2; refresh <= 7; refresh++) {
            tickAct2(events, 15 + refresh);
            assertEquals(0x0C, events.getAct2BgRoutine());
        }
        tickAct2(events, 23); // eighth refresh pair completes
        assertEquals(0x10, events.getAct2BgRoutine());
        assertFalse(events.isEventsFg5());
        states.add(new DirectedEntryState("normal after complete refresh", manager.capture(), camera.capture(), true));

        for (DirectedEntryState state : states) {
            manager.restore(state.events());
            camera.restore(state.camera());
            compose(handler, 23);
            assertReadinessQueryLeavesState(manager, events, List.of(player), handler,
                    state.settled(), state.description());
        }
    }

    @Test
    void directedEntryReadinessDoesNotAdvanceRetainedCarriers() {
        TestablePlayableSprite p1 = new TestablePlayableSprite("sonic", (short) 0x00F6, (short) 0x07F0);
        TestablePlayableSprite p2 = new TestablePlayableSprite("tails", (short) 0x0140, (short) 0x07E8);
        p2.setCpuControlled(true);
        p1.setInWater(true);
        p2.setInWater(true);
        GameServices.sprites().addSprite(p1, "sonic");
        GameServices.sprites().addSprite(p2, "tails");
        GameServices.camera().setFocusedSprite(p1);
        GameServices.camera().setX((short) 0x0080);
        Sonic3kLevelEventManager manager = new Sonic3kLevelEventManager();
        manager.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        Sonic3kHCZEvents events = manager.getHczEventsForTest();
        events.startPostTransitionCutscene();
        assertTrue(events.isCutsceneActive());
        assertReadinessQueryLeavesState(manager, events, List.of(p1, p2), null, true,
                "readiness before the wall does not service the retained carrier object pass");
    }

    private static void assertReadinessQueryLeavesState(Sonic3kLevelEventManager manager,
            Sonic3kHCZEvents events, List<? extends AbstractPlayableSprite> players,
            SwScrlHcz handler, boolean settled, String description) {
        var before = manager.capture();
        var camera = GameServices.camera().capture();
        var playerStates = players.stream().map(TestSonic3kHCZEvents::entryPlayerState).toList();
        boolean bgCollision = GameServices.gameState().isBackgroundCollisionFlag();
        var scroll = handler == null ? null
                : List.of(handler.getHcz2BgPhase(), handler.getBgCameraX(), handler.getVscrollFactorBG());
        for (int query = 0; query < 3; query++) {
            assertEquals(settled, events.isDirectedEntrySettled(), description);
        }
        var after = manager.capture();
        assertEquals(List.of(before.currentZone(), before.currentAct(), before.eventRoutineFg(),
                        before.eventRoutineBg(), before.frameCounter(), before.timerFrames(), before.bossActive()),
                List.of(after.currentZone(), after.currentAct(), after.eventRoutineFg(),
                        after.eventRoutineBg(), after.frameCounter(), after.timerFrames(), after.bossActive()), description);
        org.junit.jupiter.api.Assertions.assertArrayEquals(before.eventDataFg(), after.eventDataFg(), description);
        org.junit.jupiter.api.Assertions.assertArrayEquals(before.eventDataBg(), after.eventDataBg(), description);
        org.junit.jupiter.api.Assertions.assertArrayEquals(before.extra(), after.extra(),
                description + ": native event and retained-carrier sidecars");
        assertEquals(camera, GameServices.camera().capture(), description);
        assertEquals(playerStates, players.stream().map(TestSonic3kHCZEvents::entryPlayerState).toList(), description);
        assertEquals(bgCollision, GameServices.gameState().isBackgroundCollisionFlag(), description);
        if (handler != null) {
            assertEquals(scroll, List.of(handler.getHcz2BgPhase(), handler.getBgCameraX(),
                    handler.getVscrollFactorBG()), description);
        }
    }

    private static List<Object> entryPlayerState(AbstractPlayableSprite player) {
        return List.of(player.getCentreX(), player.getCentreY(), player.getXSpeed(), player.getYSpeed(),
                player.getGSpeed(), player.isObjectControlled(), player.isObjectControlAllowsCpu(),
                player.isObjectControlSuppressesMovement(), player.isControlLocked(), player.getAir(),
                player.getForcedAnimationId(), player.getAnimationId(), player.getAnimationFrameIndex(),
                player.getAnimationTick());
    }

    private record DirectedEntryState(String description,
            com.openggf.game.rewind.snapshot.LevelEventSnapshot events,
            com.openggf.game.rewind.snapshot.CameraSnapshot camera, boolean settled) { }
}
