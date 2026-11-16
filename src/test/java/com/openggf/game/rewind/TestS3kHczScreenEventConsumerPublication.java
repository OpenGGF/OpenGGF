package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.LevelEventSnapshot;

import org.junit.jupiter.api.function.Executable;

import com.openggf.sprites.playable.NativePlayableRoutine;

import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;

import com.openggf.game.rewind.identity.ObjectRefId;

import com.openggf.LevelFrameResult;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HCZ2WallObjectInstance;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.level.LevelManager;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableRuntimeContext;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Actual P1/P2 sensor consumption of HCZ's completed publication, original-E 2026-11-10.
 * A declared local Sonic/Tails component start supplies no trace data or event fields.
 * Native Process_Sprites precedes ScreenEvents; wall slots read Events_bg before its
 * next update (sonic3k.asm:7920-7930,106180-106223,106277-106290).
 * Y remains a formula invariant here, not a timing-discriminating value.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHczScreenEventConsumerPublication {
    private static final int DISCOVERY_LIMIT = 256;
    private static final int HELD_CAPTURE_INDEX = 3;
    private static final int REFRESH_CAPTURE_INDEX = 17;
    private static final int OBSERVATION_DISPATCHES = 0x40; // complete native LF table period
    @AfterEach
    void closeFixture() {
        TestEnvironment.resetAll();
    }

    @Test
    void actualP1AndTailsReadsUseThePrecedingCompletedPublication() throws Exception {
        var fixture = fixtureAtConsumerStart();
        assertEquals(320, fixture.camera().getWidth());
        var mode = fixture.gameplayMode();
        var main = GameServices.sprites().getMainPlayable();
        var sidekicks = GameServices.sprites().getRegisteredSidekicks();
        assertSame(fixture.sprite(), main);
        assertEquals(1, sidekicks.size(), "native Sonic/Tails component team");
        var tails = sidekicks.getFirst();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        var provider = mode.getBackgroundPlaneCollisionProvider();
        assertNotNull(provider);
        ordinaryFrame(fixture); // initial ordinary BG publication, no fabricated phase/clock
        assertEquals(4, events.getAct2BgRoutine());
        var handler = assertInstanceOf(SwScrlHcz.class,
                GameServices.parallax().getHandler(GameServices.level().getFeatureZoneId()));
        assertEquals(SwScrlHcz.Hcz2BgPhase.WALL_CHASE, handler.getHcz2BgPhase());

        var registry = mode.getRewindRegistry();
        var worldA = registry.capture();
        var controls = new ArrayList<CompletedFrame>();
        for (int frame = 0; frame < OBSERVATION_DISPATCHES; frame++) {
            var before = provider.state(GameServices.level());
            int wallBefore = events.getWallOffsetFixed();
            ordinaryFrame(fixture);
            controls.add(new CompletedFrame(before, provider.state(GameServices.level()), wallBefore,
                    "camera=" + fixture.camera().getX() + "," + fixture.camera().getY()
                            + " bg=" + handler.getBgCameraX() + "," + handler.getVscrollFactorBG()
                            + " phase=" + handler.getHcz2BgPhase() + " wall=" + events.getWallOffsetFixed()
                            + " P1=" + main.getCentreX() + "," + main.getCentreY()
                            + " P2=" + tails.getCentreX() + "," + tails.getCentreY()));
        }
        var worldB = registry.capture();
        ordinaryFrame(fixture);
        var worldC = registry.capture();
        registry.restore(worldA);
        assertWorldEqual(worldA, registry.capture(), "unobserved A restores before observation");
        assertSame(main, GameServices.sprites().getMainPlayable(), "restored actual primary");
        assertSame(tails, GameServices.sprites().getRegisteredSidekicks().getFirst(), "restored actual P2");

        var reads = new ArrayList<Read>();
        try (var observer = new SensorObserver(mode, provider, main, tails, reads)) {
            for (int frame = 0; frame < controls.size(); frame++) {
                observer.beginFrame(frame, controls.get(frame).before());
                ordinaryFrame(fixture);
            }
        }
        // Observer qualification precedes the expected timing failures. Every
        // registry key uses complete equality; no preview or selected-key proxy.
        assertWorldEqual(worldB, registry.capture(), "observed and unobserved complete B worlds");
        assertSame(provider, mode.getBackgroundPlaneCollisionProvider(), "restore actual provider");
        var wall = GameServices.level().getObjectManager().activeObjectsOfType(HCZ2WallObjectInstance.class);
        assertEquals(1, wall.size());
        int observedWallX = wall.getFirst().getX();
        ordinaryFrame(fixture);
        assertWorldEqual(worldC, registry.capture(), "closed observer preserves the next ordinary C world");
        for (String role : List.of("P1", "P2")) {
            assertTrue(reads.stream().anyMatch(read -> read.role().equals(role)
                            && read.expected().active() && controls.get(read.frame()).after().active()
                            && read.expected().cameraDiffX()
                            != controls.get(read.frame()).after().cameraDiffX()),
                    () -> role + " must make an actual active read with a changing X publication; "
                            + readControlSummary(role, controls, reads));
        }
        int expectedWallX = 0x5BE - (controls.getLast().wallBefore() >> 16);
        var oracles = new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (Read read : reads) {
            oracles.add(() -> assertEquals(read.expected(), read.observed(),
                    read.role() + " actual terrain-provider read at ordinary dispatch " + read.frame()));
        }
        oracles.add(() -> assertEquals(expectedWallX, observedWallX,
                "wall object latches the preceding accumulator in its own slot"));
        assertAll("retained HCZ publication at actual consumers", oracles);
    }

    @Test
    void beforeFirstOrdinaryFrameRestoresAndReplaysWithoutConsumingInitialization() {
        var fixture = fixtureAtConsumerStart();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        var before = registry.capture();
        byte[] eventBefore = events.captureHczState();
        ordinaryFrame(fixture);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertWorldEqual(before, registry.capture(), "before-first immediate full world " + replay);
            assertArrayEquals(eventBefore, events.captureHczState(),
                    "derived rebuild cannot consume background init, redraw or shake setup");
            ordinaryFrame(fixture);
            assertWorldEqual(after, registry.capture(), "before-first ordinary suffix " + replay);
        }
    }

    @Test
    void actualSeamlessReloadKeepsTheCompletingActOneDeformation() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0)
                .withSkippedZoneIntro().startPosition((short) 0x3680, (short) 0x620)
                .startPositionIsCentre().build();
        var manager = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        var completingAct = manager.getHczEvents();
        // Enter the production reload after its separately tested readiness gate.
        // No target publication, phase, camera or clock is supplied by this test.
        var transition = completingAct.getClass().getDeclaredMethod("requestHcz2Transition");
        transition.setAccessible(true);
        transition.invoke(completingAct);
        assertEquals(1, GameServices.level().getFeatureActId());
        assertNotSame(completingAct, manager.getHczEvents());
        var handler = assertInstanceOf(SwScrlHcz.class,
                GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_HCZ));
        var camera = fixture.camera();
        int[] actual = new int[224];
        handler.update(actual, camera.getX(), camera.getY(), 0, 1);
        assertNotEquals(0, camera.getYCopy(), "the actual transition has a nonzero vertical camera");
        assertEquals(camera.getYCopy(), handler.getVscrollFactorFG(),
                "the completing HCZ1 dispatch retains Camera_Y_pos_copy");
        int[] actOne = new int[224];
        var reference = new SwScrlHcz(GameServices.rom().getRom().readBytes(
                com.openggf.game.sonic3k.constants.Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR,
                com.openggf.game.sonic3k.constants.Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_SIZE));
        reference.update(actOne, camera.getXCopy(), camera.getYCopy(), 0, 0);
        assertArrayEquals(actOne, actual,
                "HCZ1BGE_DoTransition ends in HCZ1_Deform, before any HCZ2 state-0 dispatch");
        assertEquals(reference.getVscrollFactorBG(), handler.getVscrollFactorBG());
        assertEquals(0, manager.getHczEvents().getAct2BgRoutine());
        assertFalse(manager.getHczEvents().backgroundCollisionState().active());
    }

    @Test
    void realManagerSamplesTheLevelClockWhenItsDispatchCounterDiffers() {
        var fixture = fixtureAtConsumerStart();
        var manager = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        var events = manager.getHczEvents();
        ordinaryFrame(fixture);
        for (int remaining = 0x40;
                (GameServices.level().getFrameCounter() & 0x3F) != 2 && remaining > 0;
                remaining--) {
            ordinaryFrame(fixture);
        }
        assertEquals(2, GameServices.level().getFrameCounter() & 0x3F);
        assertEquals(4, events.getAct2BgRoutine());
        assertTrue(events.getScreenShakeFlag() < 0);

        // Component discrimination: restore only this non-authoritative dispatch
        // counter. The actual level clock, actors, camera and HCZ words are kept.
        var before = manager.capture();
        manager.restore(new LevelEventSnapshot(
                before.currentZone(), before.currentAct(), before.eventRoutineFg(),
                before.eventRoutineBg(), 0, before.timerFrames(), before.bossActive(),
                before.eventDataFg(), before.eventDataBg(), before.extra()));
        assertEquals(0, manager.capture().frameCounter());
        assertEquals(2, GameServices.level().getFrameCounter() & 0x3F);

        ordinaryFrame(fixture); // LF & $3F == 3; dispatch counter == 1
        assertEquals(1, manager.capture().frameCounter());
        assertEquals(3, GameServices.level().getFrameCounter() & 0x3F);
        ordinaryFrame(fixture); // publishes the preceding LF-3 setup
        assertEquals(2, manager.capture().frameCounter());
        assertEquals(4, GameServices.level().getFrameCounter() & 0x3F);
        assertEquals(3, events.screenPublication().shakeOffset(),
                "native ScreenShakeArray2[3] is 3; dispatch-counter table[1] is 2");
    }

    @Test
    void freshLoadOutsideTheWallArenaStartsNormalBeforeFrameOne() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                .withSkippedZoneIntro().startPosition((short) 0x200, (short) 0x300)
                .startPositionIsCentre().build();
        assertTrue((fixture.camera().getY() & 0xFFFF) < 0x500, "actual fresh camera is outside the arena");
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        assertFreshBackgroundBeforeFirstPlayerRead(fixture, 0x10, 0);
        assertEquals(0x10, events.getAct2BgRoutine(), "fresh init never enters the state-0 wall/redraw path");
    }

    @Test
    void freshWallBackgroundExistsBeforeTheFirstPlayerPhysicsRead() throws Exception {
        assertFreshBackgroundBeforeFirstPlayerRead(fixtureAtConsumerStart(), 4, 1);
    }

    private static void assertFreshBackgroundBeforeFirstPlayerRead(
            HeadlessTestFixture fixture, int expectedRoutine, int expectedWalls) throws Exception {
        var mode = fixture.gameplayMode();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        var main = GameServices.sprites().getMainPlayable();
        var tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
        try (var observer = new SensorObserver(mode, mode.getBackgroundPlaneCollisionProvider(),
                main, tails, new ArrayList<>())) {
            observer.onFirstP1Read = () -> {
                called.set(true);
                assertEquals(expectedRoutine, events.getAct2BgRoutine(),
                        "real initial Process_Sprites prepares BackgroundInit before P1 physics");
                assertEquals(expectedWalls, GameServices.level().getObjectManager()
                        .activeObjectsOfType(HCZ2WallObjectInstance.class).size());
                assertFalse(events.backgroundCollisionState().active(), "no ordinary ScreenEvents yet");
                var words = java.nio.ByteBuffer.wrap(events.captureHczState());
                assertEquals(0, words.getInt(words.limit() - 8), "initialization never calls shake setup");
                assertEquals(0, words.getInt(words.limit() - 4));
            };
            observer.beginFrame(0, mode.getBackgroundPlaneCollisionProvider().state());
            ordinaryFrame(fixture);
        }
        assertTrue(called.get(), "the actual first P1 sensor read must reach this boundary");
    }

    private static HeadlessTestFixture fixtureAtConsumerStart() {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        // A full load installs parallax before ordinary HCZ events. Shared-level
        // reuse resets parallax without repeating that load and is not this entry.
        return HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                .withSkippedZoneIntro()
                .startPosition((short) 0x680, (short) 0x700).startPositionIsCentre().build();
    }

    private static String readControlSummary(String role, List<CompletedFrame> controls, List<Read> reads) {
        var actorReads = reads.stream().filter(read -> read.role().equals(role)).toList();
        return "observed reads=" + actorReads.size()
                + ", active reads=" + actorReads.stream().filter(read -> read.observed().active()).count()
                + ", active completed frames=" + controls.stream().filter(frame -> frame.before().active()).count()
                + ", changing active X frames=" + controls.stream().filter(frame -> frame.before().active()
                        && frame.after().active() && frame.before().cameraDiffX() != frame.after().cameraDiffX()).count()
                + ", completed states=" + controls.stream().map(CompletedFrame::before).distinct().limit(8).toList()
                + ", read states=" + actorReads.stream().map(Read::observed).distinct().limit(8).toList()
                + ", boundary states=" + controls.stream().map(CompletedFrame::context).limit(8).toList();
    }

    private static void ordinaryFrame(HeadlessTestFixture fixture) {
        int before = GameServices.level().getFrameCounter() & 0xFFFF;
        fixture.stepFrame(false, false, false, false, false);
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                "exactly one admitted ordinary loop");
        assertFalse(fixture.sprite().getDead(), "unrelated death is not a timing witness");
        assertFalse(fixture.sprite().isObjectControlled(), "unrelated control takeover");
    }

    private static void assertWorldEqual(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label + " keys");
        var differences = new ArrayList<String>();
        for (String key : new LinkedHashSet<>(expected.entries().keySet())) {
            if (!RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key))) {
                differences.add(key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
            }
        }
        assertTrue(differences.isEmpty(), () -> label + ": " + differences);
    }

    private record CompletedFrame(BackgroundPlaneCollisionProvider.State before,
                                  BackgroundPlaneCollisionProvider.State after, int wallBefore, String context) { }
    private record Read(String role, int frame, BackgroundPlaneCollisionProvider.State expected,
                        BackgroundPlaneCollisionProvider.State observed) { }

    /** Test-only attribution: unchanged real scans and exact real provider returns. */
    private static final class SensorObserver implements AutoCloseable {
        private final GameplayModeContext mode;
        private final BackgroundPlaneCollisionProvider original;
        private final List<Binding> bindings = new ArrayList<>();
        private final ThreadLocal<String> actor = new ThreadLocal<>();
        private final List<Read> reads;
        private int frame;
        private BackgroundPlaneCollisionProvider.State expected;
        private Runnable onFirstP1Read;

        SensorObserver(GameplayModeContext mode, BackgroundPlaneCollisionProvider original,
                       AbstractPlayableSprite main, AbstractPlayableSprite tails, List<Read> reads)
                throws Exception {
            this.mode = mode;
            this.original = original;
            this.reads = reads;
            var observed = new BackgroundPlaneCollisionProvider() {
                @Override public State state() { return record(original.state()); }
                @Override public State state(LevelManager level) { return record(original.state(level)); }
                @Override public int backgroundX(State state, int x, Direction direction) {
                    return original.backgroundX(state, x, direction);
                }
                @Override public int backgroundY(State state, int y) { return original.backgroundY(state, y); }
                @Override public int selectNearerDistance(int foreground, int background) {
                    return original.selectNearerDistance(foreground, background);
                }
            };
            try {
                mode.attachBackgroundPlaneCollisionProvider(observed);
                assertSame(observed, PlayableRuntimeContext.background(main));
                assertSame(observed, PlayableRuntimeContext.background(tails));
                bind(main.getGroundSensors(), "P1");
                bind(main.getCeilingSensors(), "P1");
                bind(tails.getGroundSensors(), "P2");
                bind(tails.getCeilingSensors(), "P2");
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        void beginFrame(int frame, BackgroundPlaneCollisionProvider.State expected) {
            this.frame = frame;
            this.expected = expected;
        }

        private BackgroundPlaneCollisionProvider.State record(BackgroundPlaneCollisionProvider.State state) {
            if ("P1".equals(actor.get()) && onFirstP1Read != null) {
                Runnable action = onFirstP1Read;
                onFirstP1Read = null;
                action.run();
            }
            if (actor.get() != null) {
                reads.add(new Read(actor.get(), frame, expected, state));
            }
            return state;
        }

        private void bind(Sensor[] sensors, String role) throws Exception {
            assertNotNull(sensors);
            for (int index = 0; index < sensors.length; index++) {
                Sensor originalSensor = sensors[index];
                assertInstanceOf(GroundSensor.class, originalSensor);
                Sensor observed = spy(originalSensor);
                for (String name : List.of("reusableResult", "bgScanResult", "bgWallScanResult",
                        "wallResult1", "wallResult2", "verticalScan")) {
                    Field field = GroundSensor.class.getDeclaredField(name);
                    field.setAccessible(true);
                    assertSame(field.get(originalSensor), field.get(observed), "shared actual scan kernel/pool " + name);
                }
                doAnswer(call -> {
                    assertNull(actor.get(), "no nested actor attribution");
                    actor.set(role);
                    try {
                        Object returned = call.callRealMethod();
                        assertSame(returned, observed.getCurrentResult(), "unchanged actual scan result");
                        return returned;
                    } finally {
                        actor.remove();
                    }
                }).when(observed).scan(anyShort(), anyShort());
                bindings.add(new Binding(sensors, index, originalSensor, observed));
                sensors[index] = observed;
            }
        }

        @Override
        public void close() throws Exception {
            try {
                for (Binding binding : bindings) {
                    // A spy owns its primitive/current-result fields. Keep
                    // their completed values before restoring the real sensor.
                    for (Class<?> type = binding.original().getClass();
                         Sensor.class.isAssignableFrom(type); type = type.getSuperclass()) {
                        for (Field field : type.getDeclaredFields()) {
                            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                                field.setAccessible(true);
                                field.set(binding.original(), field.get(binding.observed()));
                            }
                        }
                    }
                }
            } finally {
                for (Binding binding : bindings) {
                    binding.array()[binding.index()] = binding.original();
                }
                actor.remove();
                mode.attachBackgroundPlaneCollisionProvider(original);
            }
        }

        private record Binding(Sensor[] array, int index, Sensor original, Sensor observed) { }
    }
    @Test
    void heldAndRefreshWorldsRestoreWithoutConsumingRowsAndReplayTheirOrdinarySuffixes() throws Exception {
        var fixture = nearEndCandidate();
        var main = GameServices.sprites().getMainPlayable();
        assertSame(fixture.sprite(), main);
        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        var tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var oracles = new ArrayList<Executable>();

        // Finish the existing fresh-assembly inlet before attributing a wall read
        // to an ordinary frame. HeadlessTestRunner retries SETUP_ONLY internally.
        var initialPublication = new java.util.concurrent.atomic.AtomicReference<PulseSource>();
        var mode = fixture.gameplayMode();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        try (var observer = new SensorObserver(mode, mode.getBackgroundPlaneCollisionProvider(),
                main, tails, new ArrayList<>())) {
            observer.onFirstP1Read = () -> {
                assertEquals(4, events.getAct2BgRoutine(), "actual fresh BackgroundInit before P1 physics");
                initialPublication.set(new PulseSource(events.getWallOffsetFixed(),
                        events.getScreenShakeFlag(), events.screenPublication()));
            };
            observer.beginFrame(0, mode.getBackgroundPlaneCollisionProvider().state());
            ordinaryFrame(fixture, main, tails, false);
        }
        var previous = observe(fixture);
        assertEquals(1, previous.walls().size(), "the real wall executes before the completing ScreenEvents");
        // A valid edge inlet can reach the end on its first ordinary frame.
        // The initial publication above is read-only evidence, never a restored world.
        Point firstHeld = previous.routine() == 8 ? previous : null;
        PulseSource beforePulse = firstHeld == null ? null : initialPublication.get();
        if (firstHeld == null) {
            assertEquals(4, previous.routine(), "candidate actually enters real wall chase");
            assertTrue((fixture.camera().getX() & 0xFFFF) < 0xC00);
        }
        for (int dispatch = 1; firstHeld == null && dispatch <= DISCOVERY_LIMIT; dispatch++) {
            var completed = advance(fixture, main, tails, previous, oracles, "discovery " + dispatch);
            if (completed.routine() == 8) {
                firstHeld = completed;
                beforePulse = new PulseSource(previous.accumulator(), previous.shakeFlag(), previous.publication());
                break;
            }
            assertEquals(4, completed.routine(), "no fabricated or bypassed transition: " + completed.route());
            previous = completed;
        }
        assertNotNull(firstHeld, "UNPROVEN candidate must reach stage 8 through ordinary Right within "
                + DISCOVERY_LIMIT + " dispatches; last actual state=" + previous.route());

        // firstHeld itself consumed held row one. Indices 0..14 remain state 8;
        // index 15 is held row sixteen AND refresh pair one (native fall-through).
        var original = new ArrayList<Point>();
        original.add(firstHeld);
        for (int index = 1; index <= 24; index++) {
            original.add(advance(fixture, main, tails, original.getLast(), oracles, "original suffix " + index));
        }
        var pulseSource = beforePulse;
        var pulseResult = firstHeld;
        oracles.add(() -> assertTrue((pulseResult.cameraX() & 0xFFFF) >= 0xC00, "actual camera raised the end pulse"));
        oracles.add(() -> assertEquals(pulseSource.accumulator(), pulseResult.accumulator(), "end dispatch does not move the wall again"));
        int retainedFlag = pulseSource.shakeFlag() < 0 ? 0 : pulseSource.shakeFlag();
        for (int index = 0; index < original.size(); index++) {
            Point point = original.get(index);
            int expectedRoutine = index < 15 ? 8 : index < 22 ? 0xC : 0x10;
            String label = "source-derived row boundary " + index;
            oracles.add(() -> assertEquals(expectedRoutine, point.routine(), label));
            oracles.add(() -> assertFalse(point.endPulse(), label + " pulse consumed once, never reset"));
            oracles.add(() -> assertFalse(point.collisionActive(), label + " background collision stays off"));
            if (index < 15) {
                oracles.add(() -> assertTrue(point.publication().wallPlane(), label + " held wall plane"));
                oracles.add(() -> assertEquals(pulseSource.publication().backgroundX(), point.publication().backgroundX(), label + " held BG X"));
                oracles.add(() -> assertEquals(pulseSource.publication().backgroundY(), point.publication().backgroundY(), label + " held BG Y"));
                oracles.add(() -> assertEquals(retainedFlag, point.shakeFlag(), label + " no shake setup in held redraw"));
            } else {
                oracles.add(() -> assertFalse(point.publication().wallPlane(), label + " normal plane after held row sixteen"));
            }
        }
        // Genuine middle points, not just a boundary state or fabricated payload.
        assertEquals(8, original.get(HELD_CAPTURE_INDEX).routine(), "held capture discovery");
        assertEquals(0xC, original.get(REFRESH_CAPTURE_INDEX).routine(), "refresh capture discovery");

        for (int captureIndex : List.of(HELD_CAPTURE_INDEX, REFRESH_CAPTURE_INDEX)) {
            Point saved = original.get(captureIndex);
            for (int replay = 1; replay <= 2; replay++) {
                String branch = "capture index " + captureIndex + " branch " + replay;
                registry.restore(saved.world()); // actual registered adapters + derived reconciliation
                var restored = observe(fixture);
                addEquality(oracles, saved, restored, branch + " immediate");
                assertSame(main, GameServices.sprites().getMainPlayable(), "actual native primary remains bound");
                assertSame(tails, GameServices.sprites().getRegisteredSidekicks().getFirst(), "actual native Tails remains bound");
                for (int index = captureIndex + 1; index < original.size(); index++) {
                    var next = advance(fixture, main, tails, restored, oracles, branch + " step " + index);
                    addEquality(oracles, original.get(index), next, branch + " complete ordinary world " + index);
                    restored = next;
                }
            }
        }
        // Equality/phase/slot contract failures do not preempt either replay branch.
        // Ordinary-frame, death/control or capture exceptions stop immediately.
        assertAll("held/refresh full-world restore, row count, pulse and own-slot retirement", oracles);
    }

    private static HeadlessTestFixture nearEndCandidate() {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        // Builder's existing UNCLASSIFIED_ENTRY custom-position semantics are
        // intentional. POSITIONED_FRESH_SPAWN would select ROM start-camera rules.
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                // Declared edge inlet: forced 320px camera starts one pixel
                // before $C00; $82C is the existing stationary HCZ2 component Y.
                // The earlier C80/700 inlet died before reaching the transition.
                .withSkippedZoneIntro().startPosition((short) 0xC9F, (short) 0x82C)
                .startPositionIsCentre().build();
        assertEquals(320, fixture.camera().getWidth());
        assertTrue((fixture.camera().getX() & 0xFFFF) < 0xC00
                        && (fixture.camera().getY() & 0xFFFF) >= 0x500,
                "actual initialized geometry must select wall background; no live camera correction");
        return fixture;
    }

    private static Point advance(HeadlessTestFixture fixture, AbstractPlayableSprite main,
                                 AbstractPlayableSprite tails, Point previous,
                                 List<Executable> oracles, String label) {
        ordinaryFrame(fixture, main, tails, true); // up,down,left,RIGHT,jump: ordinary input only
        var point = observe(fixture);
        if (previous.routine() == 4) {
            int expectedX = (short) (0x5BE - (previous.accumulator() >> 16));
            var expectedWalls = previous.walls().stream()
                    .map(wall -> new Wall(wall.id(), wall.slot(), expectedX, 0x700)).toList();
            oracles.add(() -> assertEquals(expectedWalls, point.walls(), label + " own wall slot latches preceding accumulator"));
        } else {
            // On firstHeld the real wall still exists; its NEXT own-slot update
            // observes stage 8 and retires it. Events/restoration must not do so.
            oracles.add(() -> assertTrue(point.walls().isEmpty(), label + " wall retires on the next own-slot pass, no resurrection"));
        }
        return point;
    }

    private static void ordinaryFrame(HeadlessTestFixture fixture, AbstractPlayableSprite main,
                                      AbstractPlayableSprite tails, boolean right) {
        int before = GameServices.level().getFrameCounter() & 0xFFFF;
        var result = fixture.runner().stepFrame(false, false, false, right, false);
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result, "no held, paused or setup-only result hidden");
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                "exactly one admitted ordinary level frame");
        assertEquals(Sonic3kZoneIds.ZONE_HCZ, GameServices.level().getFeatureZoneId());
        assertEquals(1, GameServices.level().getFeatureActId());
        for (var player : List.of(main, tails)) {
            assertFalse(player.getDead(), () -> "death is not a rewind witness: role="
                    + (player == main ? "P1" : "P2") + " x=" + player.getCentreX()
                    + " y=" + player.getCentreY() + " camera=" + fixture.camera().getX()
                    + "," + fixture.camera().getY() + " LF=" + GameServices.level().getFrameCounter());
            assertFalse(player.isDrowningPreDeath(), "drowning is not a rewind witness");
            assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player), "raw routine gate must remain live");
            assertFalse(player.isObjectControlled(), "unrelated control takeover");
            assertFalse(player.isDebugMode(), "ordinary native character path");
        }
    }

    private static Point observe(HeadlessTestFixture fixture) {
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getHczEvents();
        var handler = assertInstanceOf(SwScrlHcz.class,
                GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_HCZ));
        var objects = GameServices.level().getObjectManager();
        var identities = objects.captureIdentityContext().requireIdentityTable();
        var walls = objects.activeObjectsOfType(HCZ2WallObjectInstance.class).stream()
                .map(wall -> new Wall(identities.idFor(wall), wall.getSlotIndex(), wall.getX(), wall.getY())).toList();
        return new Point(fixture.gameplayMode().getRewindRegistry().capture(), events.captureHczState(),
                events.getAct2BgRoutine(), events.getWallOffsetFixed(), events.getScreenShakeFlag(),
                events.isEventsFg5(), GameServices.gameState().isBackgroundCollisionFlag(),
                events.screenPublication(), walls, fixture.camera().getX(), fixture.camera().getY(),
                handler.getBgCameraX(), handler.getVscrollFactorBG(), handler.getShakeOffsetY(),
                GameServices.parallax().getHScroll().clone(),
                "P1=" + fixture.sprite().getCentreX() + "," + fixture.sprite().getCentreY()
                        + " camera=" + fixture.camera().getX() + "," + fixture.camera().getY()
                        + " bgRoutine=" + events.getAct2BgRoutine());
    }

    private static void addEquality(List<Executable> oracles, Point expected, Point actual, String label) {
        var differences = differences(expected.world(), actual.world());
        oracles.add(() -> assertTrue(differences.isEmpty(), () -> label + " full registry: " + differences));
        oracles.add(() -> assertArrayEquals(expected.hczBytes(), actual.hczBytes(), label + " exact HCZ payload; derived rebuild consumes no row/setup"));
        oracles.add(() -> assertEquals(expected.publication(), actual.publication(), label + " publication"));
        oracles.add(() -> assertEquals(expected.walls(), actual.walls(), label + " managed wall membership/id/slot/pose"));
        int expectedBgX = actual.publication().wallPlane()
                ? actual.publication().backgroundX() : Integer.MIN_VALUE;
        oracles.add(() -> assertEquals(expectedBgX, actual.handlerBgX(), label + " phase-specific BG anchor"));
        oracles.add(() -> assertArrayEquals(expected.scrollWords(), actual.scrollWords(),
                label + " every actual horizontal scroll word"));
        oracles.add(() -> assertEquals((short) actual.publication().backgroundY(), actual.handlerBgY(), label + " derived BG Y"));
        oracles.add(() -> assertEquals(actual.publication().shakeOffset(), actual.handlerShake(), label + " derived shake"));
    }

    private static List<String> differences(CompositeSnapshot expected, CompositeSnapshot actual) {
        var differences = new ArrayList<String>();
        if (!expected.entries().keySet().equals(actual.entries().keySet())) differences.add("registry key sets differ");
        var keys = new LinkedHashSet<>(expected.entries().keySet());
        keys.addAll(actual.entries().keySet());
        for (var key : keys) {
            if (!RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key))) {
                differences.add(key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
            }
        }
        return List.copyOf(differences);
    }

    private record PulseSource(int accumulator, int shakeFlag, Sonic3kHCZEvents.ScreenPublication publication) { }
    private record Wall(ObjectRefId id, int slot, int x, int y) { }
    private record Point(CompositeSnapshot world, byte[] hczBytes, int routine, int accumulator,
                         int shakeFlag, boolean endPulse, boolean collisionActive,
                         Sonic3kHCZEvents.ScreenPublication publication, List<Wall> walls,
                         int cameraX, int cameraY, int handlerBgX, short handlerBgY,
                         int handlerShake, int[] scrollWords, String route) { }
}
