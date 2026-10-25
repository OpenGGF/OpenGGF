package com.openggf.game.session;

import com.openggf.LevelFrameResult;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;

// BEGIN HCZ1_DIRECT_FF_CONTROL_IMPORTS
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import java.util.LinkedHashMap;
// END HCZ1_DIRECT_FF_CONTROL_IMPORTS

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent HCZ1 DIRECT-water physical-publication component, originating in
 * root_hcz1_direct_water_component_source (2026-10-19).
 *
 * <p>The world, team, fresh-level assembly, title release and both ordinary
 * frames are production-owned. Appending the original capsule PLC is an explicit
 * SYNTHETIC public component producer; it is not a natural HCZ caller or route.
 * No scene, water, camera, player, queue-head or timing state is installed.
 *
 * <p>Native044 Level initializes H_int_counter to $8AFF. The actual
 * Handle_Onscreen_Water_Height HCZ1 branch writes $FF for unsigned physical
 * Camera_X_pos below $900, and runs after ScreenEvents before
 * Level_NotLRZ's Animate_Tiles/Process_Nem_Queue_Init. VInt_8 takes the DIRECT
 * updates branch for that counter; no deferred H-int acceptance is implied.
 * These source operands, not an engine counter/latch or trace row, define this
 * test's independent inlet. Native Process_Nem_Queue_2 confirms at most three
 * complete patterns from the already prepared head.
 *
 * <p>The oracle compares original-ROM decoded bytes with live level pixels,
 * unchanged Pattern identities and the registered physical journal. It does
 * not certify native VRAM, renderer/GPU output, H-int service, general water
 * scheduling, rewind, a natural PLC submission or a playable route.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHcz1DirectWaterNemesisPublication {
    private static final int ZONE = Sonic3kZoneIds.ZONE_HCZ;
    private static final int ACT = 0;
    private static final int RAW_PLC = 0x083D74;
    private static final int SOURCE = 0x0DD990;
    private static final int DESTINATION_BYTES = 0x9280;
    private static final int DESTINATION_TILE = 0x494;
    private static final int PATTERNS = 70;
    private static final int FIRST_OBSERVED_TILE = DESTINATION_TILE - 1;
    private static final int OBSERVED_PATTERNS = PATTERNS + 2;

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void hcz1DirectWaterFramePublishesThreePreparedNemesisPatterns() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), ZONE, ACT, settings);
            var context = SessionManager.getCurrentGameplayMode();
            assertNotNull(context, "the genuine boot must install its gameplay context");
            var world = context.getWorldSession();
            var manager = context.getLevelManager();
            var objects = manager.getObjectManager();
            assertNotNull(objects);
            var services = objects.getObjectServices();
            assertNotNull(services);
            var level = assertInstanceOf(Sonic3kLevel.class, world.getCurrentLevel());
            var plc = WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
            var journal = context.getKosinskiModuleQueue();
            var water = context.getWaterSystem();
            assertNotNull(journal);
            assertNotNull(water);
            var handler = water.getDynamicWaterHandler(ZONE, ACT);
            assertNotNull(handler, "the loaded HCZ1 world must own its actual dynamic water handler");
            var inlet = new Inlet(context, world, manager, objects, services, level, plc, journal,
                    water, handler, GameServices.camera(), session.player());
            var configuration = GameServices.configuration();
            assertAll("declared fresh solo-Sonic stock HCZ1 entry",
                    () -> assertSame(configuration, EngineServices.current().configuration()),
                    () -> assertEquals("sonic", configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE)),
                    () -> assertEquals("", configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE)),
                    () -> assertNull(world.getSaveSessionContext()),
                    () -> assertEquals("sonic", ActiveGameplayTeamResolver.resolveMainCharacterCode(
                            world, configuration)),
                    () -> assertEquals(List.of(), ActiveGameplayTeamResolver.resolveSidekicks(configuration)),
                    () -> assertInstanceOf(Sonic.class, inlet.player()),
                    () -> assertEquals(320, inlet.camera().getWidth()),
                    () -> assertEquals(224, inlet.camera().getHeight()));
            requireBindings(inlet);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertTrue(manager.hasPendingInitialProcessSpritesPass());
            assertTrue(manager.isTitleCardRequested());
            assertTrue(plc.isBusy(), "the fresh load's real descriptors precede its title loop");

            // Consume the original fresh-level setup token, then the same
            // pending title request. Neither step is an ordinary queue warmup.
            session.step(null);
            assertFalse(manager.hasPendingInitialProcessSpritesPass());
            assertTrue(manager.isTitleCardRequested());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            requireBindings(inlet);
            session.step(null);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
            requireBindings(inlet);
            int titleSteps = 1;
            while (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD && titleSteps < 600) {
                requireBindings(inlet);
                session.step(null);
                titleSteps++;
                requireBindings(inlet);
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(),
                    "the same production title must naturally release within the safety bound");
            requireOrdinaryInlet(inlet, session);
            assertEquals(new NemesisPlcQueueSnapshot(null, List.of()), plc.capture(),
                    "the actual title must leave an empty FIFO; never clear, drain or retry it");
            var registered = context.getRewindRegistry().capture();
            assertTrue(registered.entries().containsKey(plc.key()));
            assertTrue(registered.entries().containsKey(journal.key()));
            assertEquals(plc.capture(), registered.get(plc.key()));
            assertEquals(journal.capture(), registered.get(journal.key()));
            assertTrue(level.getPatternCount() > DESTINATION_TILE + PATTERNS,
                    "both destination guards must actually exist in the loaded level");

            // Read and decode the independent original descriptor/archive from
            // THIS world's ROM. No queue decoder or prepared-head image is used.
            var rom = world.getDataSource().rom().orElseThrow(
                    () -> new AssertionError("the actual loaded world's original ROM is required"));
            assertSame(TestEnvironment.currentRom(), rom);
            assertTrue(rom.getSize() >= RAW_PLC + 8L);
            assertEquals(0, rom.read16BitAddr(RAW_PLC) & 0xFFFF);
            assertEquals(SOURCE, rom.read32BitAddr(RAW_PLC + 2));
            assertEquals(DESTINATION_BYTES, rom.read16BitAddr(RAW_PLC + 6) & 0xFFFF);
            assertEquals(DESTINATION_TILE, DESTINATION_BYTES / 32);
            assertEquals(0x0046, rom.read16BitAddr(SOURCE) & 0xFFFF);
            byte[] decoded;
            try (var channel = Channels.newChannel(new ByteArrayInputStream(
                    rom.readBytes(SOURCE, Math.toIntExact(rom.getSize() - SOURCE))))) {
                decoded = NemesisReader.decompress(channel);
            }
            assertEquals(PATTERNS * 32, decoded.length);
            byte[] expectedPixels = expandNibbles(decoded);
            var baseline = observe(inlet);
            for (int index = 0; index < PATTERNS * 32; index++) {
                assertFalse(baseline.journalPresent()[32 + index],
                        "hard inlet prerequisite: no baseline destination journal coverage, byte=" + index);
            }
            assertFalse(Arrays.equals(Arrays.copyOfRange(baseline.pixels(), 64, 64 + 3 * 64),
                            Arrays.copyOfRange(expectedPixels, 0, 3 * 64)),
                    "the first three original patterns must differ from the actual baseline");

            // This raw append is the sole SYNTHETIC producer. Only the real
            // ordinary GameLoop steps below may prepare or physically service it.
            var runner = new HeadlessTestRunner(inlet.player());
            plc.appendRaw(RAW_PLC);
            var appended = observe(inlet);
            stepOrdinary(inlet, session, runner);
            var prepared = observe(inlet);
            assertEquals(new NemesisPlcQueueSnapshot(entry(PATTERNS), List.of()), prepared.queue(),
                    "the actual post-water/animation Init must prepare this head before the service oracle");
            stepOrdinary(inlet, session, runner);
            var published = observe(inlet);

            // Preserve all observations before semantic comparisons. There is
            // deliberately no prerequisite for the currently missing DIRECT_FF
            // counter/latch: failure to publish belongs at this final oracle.
            assertAll("HCZ1 direct-water ordinary physical Nemesis publication",
                    () -> assertAll("append remains a waiting descriptor with no physical effect",
                            () -> assertEquals(new NemesisPlcQueueSnapshot(null, List.of(entry(PATTERNS))),
                                    appended.queue()),
                            () -> assertImage(baseline, appended, expectedPixels, 0),
                            () -> assertEquals(baseline.wholeJournal(), appended.wholeJournal())),
                    () -> assertAll("the first real frame prepares without publishing",
                            () -> assertImage(baseline, prepared, expectedPixels, 0),
                            () -> assertJournalPrefix(baseline, prepared, decoded, 0)),
                    () -> assertAll("the next real ordinary service confirms exactly three patterns",
                            () -> assertImage(baseline, published, expectedPixels, 3),
                            () -> assertJournalPrefix(baseline, published, decoded, 3),
                            () -> assertEquals(new NemesisPlcQueueSnapshot(entry(PATTERNS - 3), List.of()),
                                    published.queue(), "only confirmed complete patterns retire FIFO progress")));
        }
    }

    private static NemesisPlcQueueSnapshot.Entry entry(int remaining) {
        return new NemesisPlcQueueSnapshot.Entry(SOURCE, DESTINATION_TILE, PATTERNS, remaining);
    }

    private static void requireBindings(Inlet inlet) {
        assertSame(inlet.context(), SessionManager.getCurrentGameplayMode());
        assertSame(inlet.world(), inlet.context().getWorldSession());
        assertSame(inlet.world(), SessionManager.getCurrentWorldSession());
        assertSame(inlet.manager(), inlet.context().getLevelManager());
        assertSame(inlet.manager(), GameServices.level());
        assertSame(inlet.world(), inlet.manager().getWorldSession());
        assertSame(inlet.level(), inlet.world().getCurrentLevel());
        assertSame(inlet.level(), inlet.manager().getCurrentLevel());
        assertTrue(inlet.level().hasStockRomZoneIdentity());
        assertEquals(ZONE, inlet.manager().getCurrentZone());
        assertEquals(ACT, inlet.manager().getCurrentAct());
        assertEquals(ZONE, inlet.manager().getFeatureZoneId());
        assertEquals(ACT, inlet.manager().getFeatureActId());
        assertSame(inlet.objects(), inlet.manager().getObjectManager());
        assertSame(inlet.services(), inlet.objects().getObjectServices());
        assertSame(inlet.world(), inlet.services().worldSession());
        assertSame(inlet.manager(), inlet.services().levelManager());
        assertSame(inlet.level(), inlet.services().currentLevel());
        assertSame(inlet.camera(), GameServices.camera());
        assertSame(inlet.camera(), inlet.services().camera());
        assertSame(inlet.player(), GameServices.sprites().getMainPlayable());
        assertSame(inlet.player(), inlet.services().playerQuery().mainPlayerOrNull());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertFalse(inlet.player().getDead());
        assertSame(inlet.plc(), WorldPlcLifecycleAccess.require(inlet.world(), Sonic3kLevelTitlePlcService.class));
        assertSame(inlet.plc(), inlet.services().gameService(Sonic3kLevelTitlePlcService.class));
        assertSame(inlet.journal(), inlet.context().getKosinskiModuleQueue());
        assertSame(inlet.journal(), inlet.services().kosinskiModuleQueue());
        assertSame(inlet.water(), inlet.context().getWaterSystem());
        assertSame(inlet.water(), inlet.services().waterSystem());
        assertSame(inlet.handler(), inlet.water().getDynamicWaterHandler(ZONE, ACT));
    }

    private static void requireOrdinaryInlet(Inlet inlet, GameplayCaptureSession session) {
        requireBindings(inlet);
        assertSame(inlet.player(), session.player());
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertFalse(inlet.manager().hasPendingInitialProcessSpritesPass());
        assertFalse(inlet.manager().isTitleCardRequested());
        assertFalse(inlet.manager().isLevelInactiveForTransition());
        assertFalse(inlet.manager().advanceWaterLevelBeforePlayerPhysics(),
                "stock S3K owns the actual post-player water producer");
        assertTrue(inlet.water().hasWater(ZONE, ACT));
        assertTrue(inlet.water().isLiveWaterFlagSet(ZONE, ACT));
        assertFalse(inlet.water().isDynamicWaterLocked(ZONE, ACT));
        int physicalCameraX = inlet.camera().getX() & 0xFFFF;
        assertTrue(physicalCameraX < 0x900,
                "hard DIRECT-branch inlet: unsigned physical camera X=" + physicalCameraX);
    }

    private static void stepOrdinary(Inlet inlet, GameplayCaptureSession session, HeadlessTestRunner runner) {
        requireOrdinaryInlet(inlet, session);
        int before = inlet.manager().getFrameCounter() & 0xFFFF;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                runner.stepFrame(false, false, false, false, false),
                "the actual ordinary GameLoop must own preparation and service");
        assertEquals((before + 1) & 0xFFFF, inlet.manager().getFrameCounter() & 0xFFFF);
        requireOrdinaryInlet(inlet, session);
    }

    private static Observation observe(Inlet inlet) {
        var refs = new Pattern[OBSERVED_PATTERNS];
        var pixels = new byte[OBSERVED_PATTERNS * 64];
        for (int tile = 0; tile < refs.length; tile++) {
            refs[tile] = inlet.level().getPattern(FIRST_OBSERVED_TILE + tile);
            assertNotNull(refs[tile], "every original destination/guard Pattern must exist");
            refs[tile].copyInto(pixels, tile * 64);
        }
        var snapshot = inlet.journal().capture();
        var present = new boolean[OBSERVED_PATTERNS * 32];
        var bytes = new byte[present.length];
        int firstAddress = FIRST_OBSERVED_TILE * 32;
        for (var write : snapshot.appliedWrites()) {
            byte[] data = write.data();
            for (int index = 0; index < data.length; index++) {
                long local = (long) write.destinationVramBytes() + index - firstAddress;
                if (local >= 0 && local < bytes.length) {
                    assertFalse(present[(int) local], "registered journal ranges must not overlap");
                    present[(int) local] = true;
                    bytes[(int) local] = data[index];
                }
            }
        }
        return new Observation(inlet.plc().capture(), refs, pixels, snapshot, present, bytes);
    }

    private static byte[] expandNibbles(byte[] decoded) {
        var pixels = new byte[decoded.length * 2];
        for (int index = 0; index < decoded.length; index++) {
            pixels[index * 2] = (byte) ((decoded[index] & 0xFF) >>> 4);
            pixels[index * 2 + 1] = (byte) (decoded[index] & 0x0F);
        }
        return pixels;
    }

    private static void assertImage(Observation baseline, Observation observed,
                                    byte[] expectedPixels, int publishedPatterns) {
        int replacedRefs = 0;
        int differingPixels = 0;
        int firstMismatch = -1;
        for (int tile = 0; tile < OBSERVED_PATTERNS; tile++) {
            if (baseline.refs()[tile] != observed.refs()[tile]) replacedRefs++;
        }
        for (int index = 0; index < observed.pixels().length; index++) {
            int archiveIndex = index - 64;
            byte expected = archiveIndex >= 0 && archiveIndex < publishedPatterns * 64
                    ? expectedPixels[archiveIndex] : baseline.pixels()[index];
            if (expected != observed.pixels()[index]) {
                differingPixels++;
                if (firstMismatch < 0) firstMismatch = index;
            }
        }
        assertEquals(0, replacedRefs, "original destination and guard Pattern identities must survive");
        assertEquals(0, differingPixels,
                "exact prefix/suffix/guard pixels; first local mismatch=" + firstMismatch
                        + ", expected published patterns=" + publishedPatterns);
    }

    private static void assertJournalPrefix(Observation baseline, Observation observed,
                                            byte[] decoded, int publishedPatterns) {
        int mismatches = 0;
        int firstMismatch = -1;
        for (int index = 0; index < observed.journalBytes().length; index++) {
            int archiveIndex = index - 32;
            boolean published = archiveIndex >= 0 && archiveIndex < publishedPatterns * 32;
            boolean expectedPresence = published || baseline.journalPresent()[index];
            byte expected = published ? decoded[archiveIndex] : baseline.journalBytes()[index];
            if (expectedPresence != observed.journalPresent()[index]
                    || expectedPresence && expected != observed.journalBytes()[index]) {
                mismatches++;
                if (firstMismatch < 0) firstMismatch = index;
            }
        }
        assertEquals(0, mismatches,
                "exact confirmed ROM-byte prefix and unchanged suffix/guards; first mismatch=" + firstMismatch);
    }

    // BEGIN HCZ1_DIRECT_FF_CONTROLS: separate snapshot/target controls; original independent oracle is unchanged.
    @Test
    void wetFullRegistryRestoreReplaysTheSamePhysicalPrefixTwice() throws Exception {
        try (var session = bootControlSession()) {
            var control = prepareWetControl(session);
            var inlet = control.inlet();
            var registry = inlet.context().getRewindRegistry();
            var before = registry.capture();
            assertEquals(inlet.plc().ordinaryScheduleState().capture(),
                    before.get(inlet.plc().ordinaryScheduleState().key()));
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 3);
            var after = registry.capture();
            for (int replay = 0; replay < 2; replay++) {
                registry.restore(before);
                requireOrdinaryInlet(inlet, session);
                assertAllRegistryKeys(before, registry.capture());
                assertControlPrefix(control, 0);
                stepOrdinary(inlet, session, control.runner());
                assertControlPrefix(control, 3);
                assertAllRegistryKeys(after, registry.capture());
            }
        }
    }

    @Test
    void countOnlyAndMissingScheduleRestoreRefuseUntilTheNextActualWaterProducer() throws Exception {
        try (var session = bootControlSession()) {
            var control = prepareWetControl(session);
            var inlet = control.inlet();
            var registry = inlet.context().getRewindRegistry();
            var complete = registry.capture();
            var counts = inlet.plc().capture();
            var recipes = inlet.plc().recipeState().capture();

            // Declared public projection restore: actual captured counts and
            // recipes are insufficient to restore the separate producer key.
            inlet.plc().restore(counts);
            inlet.plc().recipeState().restore(recipes);
            assertControlPrefix(control, 0);
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 0);
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 3);

            registry.restore(complete);
            assertAllRegistryKeys(complete, registry.capture());
            assertControlPrefix(control, 0);
            var missing = new LinkedHashMap<>(complete.entries());
            String scheduleKey = inlet.plc().ordinaryScheduleState().key();
            assertNotNull(missing.remove(scheduleKey));
            registry.restore(new CompositeSnapshot(missing));
            assertControlPrefix(control, 0);
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 0);
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 3);
        }
    }

    @Test
    void aForeignWorldScheduleCannotAuthorizeTheCurrentPreparedWetHead() throws Exception {
        Object foreignSchedule;
        WorldSession formerWorld;
        Sonic3kLevelTitlePlcService formerOwner;
        try (var former = bootControlSession()) {
            var prepared = prepareWetControl(former);
            formerWorld = prepared.inlet().world();
            formerOwner = prepared.inlet().plc();
            foreignSchedule = formerOwner.ordinaryScheduleState().capture();
        }
        TestEnvironment.resetAll();
        try (var current = bootControlSession()) {
            var control = prepareWetControl(current);
            assertNotSame(formerWorld, control.inlet().world());
            assertNotSame(formerOwner, control.inlet().plc());
            assertThrows(IllegalArgumentException.class, () -> restoreOpaque(
                    control.inlet().plc().ordinaryScheduleState(), foreignSchedule));
            assertControlPrefix(control, 0);
            stepOrdinary(control.inlet(), current, control.runner());
            assertControlPrefix(control, 0);
            stepOrdinary(control.inlet(), current, control.runner());
            assertControlPrefix(control, 3);
        }
    }

    @Test
    void aFreshReplacementCannotBorrowThePreviousLoadSchedule() throws Exception {
        try (var session = bootControlSession()) {
            var former = prepareWetControl(session);
            Object previousLoad = former.inlet().plc().ordinaryScheduleState().capture();
            byte[] previousPixels = observe(former.inlet()).pixels();
            former.inlet().manager().loadZoneAndActWithTitleCard(ZONE, ACT);
            var replacement = controlInlet(session);
            assertSame(former.inlet().world(), replacement.world());
            assertSame(former.inlet().plc(), replacement.plc());
            assertNotSame(former.inlet().level(), replacement.level(),
                    "the public fresh load must actually install a distinct pattern target");
            releaseControlTitle(session, replacement);
            var control = prepareControlHead(session, replacement);
            restoreOpaque(replacement.plc().ordinaryScheduleState(), previousLoad);
            assertControlPrefix(control, 0);
            stepOrdinary(replacement, session, control.runner());
            assertControlPrefix(control, 0);
            stepOrdinary(replacement, session, control.runner());
            assertControlPrefix(control, 3);
            assertArrayEquals(previousPixels, observe(former.inlet()).pixels(),
                    "a new target cannot repaint the retained previous level");
        }
    }

    @Test
    void theActualUnsupportedProducerRevokesRatherThanRereadingCameraAtPublication() throws Exception {
        try (var session = bootControlSession()) {
            var control = prepareWetControl(session);
            var inlet = control.inlet();
            var camera = inlet.camera();
            int previousX = camera.getX();
            int previousY = camera.getY();
            boolean previousFrozen = camera.getFrozen();
            try {
                // Explicit SYNTHETIC public viewport negative. The earlier
                // actual producer was below $900; this change is not itself a
                // counter write. No player/height/clock/deferred state is set.
                camera.setFrozen(true);
                camera.setX((short) 0x900);
                stepControlAtUnsupportedX(inlet, session, control.runner());
                assertControlPrefix(control, 3);
                // The first real tail has now reached the unsupported branch.
                // Refusal here is not a claim that native H-int is deferred.
                stepControlAtUnsupportedX(inlet, session, control.runner());
                assertControlPrefix(control, 3);
            } finally {
                camera.setX((short) previousX);
                camera.setY((short) previousY);
                camera.setFrozen(previousFrozen);
            }
        }
    }

    // BEGIN HCZ1_DIRECT_FF_SELECTED_CALLER_CONTROL: the public internal carrier grants no caller-reported authority.
    @Test
    void publicCarrierCallsCannotForgeOrRevokeTheActualSelectedWaterIdentity() throws Exception {
        try (var session = bootControlSession()) {
            var control = prepareWetControl(session);
            var inlet = control.inlet();
            var schedule = inlet.plc().ordinaryScheduleState().capture();
            assertThrows(SecurityException.class, () -> inlet.plc().beginWaterScheduleSelection(true));
            assertThrows(SecurityException.class, () -> inlet.plc().beginWaterScheduleSelection(false));
            assertThrows(SecurityException.class, () -> inlet.plc().retainWaterScheduleSelection(
                    inlet.level(), ZONE, ACT, true));
            assertThrows(SecurityException.class, () -> inlet.plc().retainWaterScheduleSelection(
                    inlet.level(), ZONE, ACT, false));
            assertEquals(schedule, inlet.plc().ordinaryScheduleState().capture());
            assertControlPrefix(control, 0);
            stepOrdinary(inlet, session, control.runner());
            assertControlPrefix(control, 3);
        }
    }
    // END HCZ1_DIRECT_FF_SELECTED_CALLER_CONTROL

    private static GameplayCaptureSession bootControlSession() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false);
        var session = new GameplayCaptureSession(settings);
        try {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), ZONE, ACT, settings);
            return session;
        } catch (Exception | Error failure) {
            try { session.close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static Inlet controlInlet(GameplayCaptureSession session) {
        var context = SessionManager.getCurrentGameplayMode();
        assertNotNull(context);
        var world = context.getWorldSession();
        var manager = context.getLevelManager();
        var objects = manager.getObjectManager();
        assertNotNull(objects);
        var services = objects.getObjectServices();
        assertNotNull(services);
        var level = assertInstanceOf(Sonic3kLevel.class, manager.getCurrentLevel());
        var water = context.getWaterSystem();
        assertNotNull(water);
        var handler = water.getDynamicWaterHandler(ZONE, ACT);
        assertNotNull(handler);
        var inlet = new Inlet(context, world, manager, objects, services, level,
                WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class),
                context.getKosinskiModuleQueue(), water, handler, GameServices.camera(), session.player());
        requireBindings(inlet);
        assertNull(world.getSaveSessionContext());
        assertInstanceOf(Sonic.class, inlet.player());
        assertEquals(320, inlet.camera().getWidth());
        assertEquals(224, inlet.camera().getHeight());
        return inlet;
    }

    private static void releaseControlTitle(GameplayCaptureSession session, Inlet inlet) throws Exception {
        requireBindings(inlet);
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertTrue(inlet.manager().hasPendingInitialProcessSpritesPass());
        assertTrue(inlet.manager().isTitleCardRequested());
        assertTrue(inlet.plc().isBusy());
        session.step(null);
        requireBindings(inlet);
        assertFalse(inlet.manager().hasPendingInitialProcessSpritesPass());
        assertTrue(inlet.manager().isTitleCardRequested());
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        session.step(null);
        assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
        int visits = 1;
        while (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD && visits < 600) {
            requireBindings(inlet);
            session.step(null);
            visits++;
        }
        requireOrdinaryInlet(inlet, session);
        assertEquals(new NemesisPlcQueueSnapshot(null, List.of()), inlet.plc().capture());
    }

    private static PreparedWetControl prepareWetControl(GameplayCaptureSession session) throws Exception {
        var inlet = controlInlet(session);
        releaseControlTitle(session, inlet);
        return prepareControlHead(session, inlet);
    }

    private static PreparedWetControl prepareControlHead(GameplayCaptureSession session, Inlet inlet) throws Exception {
        requireOrdinaryInlet(inlet, session);
        assertEquals(new NemesisPlcQueueSnapshot(null, List.of()), inlet.plc().capture());
        var rom = inlet.world().getDataSource().rom().orElseThrow();
        assertSame(TestEnvironment.currentRom(), rom);
        assertEquals(0, rom.read16BitAddr(RAW_PLC) & 0xFFFF);
        assertEquals(SOURCE, rom.read32BitAddr(RAW_PLC + 2));
        assertEquals(DESTINATION_BYTES, rom.read16BitAddr(RAW_PLC + 6) & 0xFFFF);
        assertEquals(0x0046, rom.read16BitAddr(SOURCE) & 0xFFFF);
        byte[] decoded;
        try (var channel = Channels.newChannel(new ByteArrayInputStream(
                rom.readBytes(SOURCE, Math.toIntExact(rom.getSize() - SOURCE))))) {
            decoded = NemesisReader.decompress(channel);
        }
        assertEquals(PATTERNS * 32, decoded.length);
        var baseline = observe(inlet);
        for (int index = 32; index < 32 + PATTERNS * 32; index++)
            assertFalse(baseline.journalPresent()[index], "real destination must be untouched before this raw PLC");
        byte[] expected = expandNibbles(decoded);
        assertFalse(Arrays.equals(Arrays.copyOfRange(baseline.pixels(), 64, 64 + 3 * 64),
                Arrays.copyOfRange(expected, 0, 3 * 64)));
        var registry = inlet.context().getRewindRegistry().capture();
        assertEquals(inlet.plc().capture(), registry.get(inlet.plc().key()));
        assertEquals(inlet.plc().recipeState().capture(), registry.get(inlet.plc().recipeState().key()));
        assertEquals(inlet.plc().ordinaryScheduleState().capture(),
                registry.get(inlet.plc().ordinaryScheduleState().key()));
        assertEquals(inlet.journal().capture(), registry.get(inlet.journal().key()));
        var runner = new HeadlessTestRunner(inlet.player());
        inlet.plc().appendRaw(RAW_PLC); // Sole synthetic art producer, as in the original independent inlet.
        stepOrdinary(inlet, session, runner);
        var control = new PreparedWetControl(inlet, runner, baseline, decoded, expected);
        assertControlPrefix(control, 0);
        return control;
    }

    private static void assertControlPrefix(PreparedWetControl control, int patterns) {
        var actual = observe(control.inlet());
        assertAll("captured prefix, complete journal, original identities and unspent head",
                () -> assertImage(control.baseline(), actual, control.expectedPixels(), patterns),
                () -> assertJournalPrefix(control.baseline(), actual, control.decoded(), patterns),
                () -> assertEquals(new NemesisPlcQueueSnapshot(entry(PATTERNS - patterns), List.of()),
                        actual.queue()));
    }

    private static void assertAllRegistryKeys(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }

    private static <S> void restoreOpaque(RewindSnapshottable<S> adapter, Object actualCapturedState) {
        @SuppressWarnings("unchecked") S typed = (S) actualCapturedState;
        adapter.restore(typed);
    }

    private static void stepControlAtUnsupportedX(Inlet inlet, GameplayCaptureSession session,
                                                 HeadlessTestRunner runner) {
        requireBindings(inlet);
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertFalse(inlet.manager().hasPendingInitialProcessSpritesPass());
        assertFalse(inlet.manager().isTitleCardRequested());
        assertFalse(inlet.manager().isLevelInactiveForTransition());
        assertFalse(inlet.manager().advanceWaterLevelBeforePlayerPhysics());
        assertTrue(inlet.water().hasWater(ZONE, ACT));
        assertTrue(inlet.water().isLiveWaterFlagSet(ZONE, ACT));
        assertEquals(0x900, inlet.camera().getX() & 0xFFFF);
        int before = inlet.manager().getFrameCounter() & 0xFFFF;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, runner.stepFrame(false, false, false, false, false));
        requireBindings(inlet);
        assertEquals((before + 1) & 0xFFFF, inlet.manager().getFrameCounter() & 0xFFFF);
        assertEquals(0x900, inlet.camera().getX() & 0xFFFF,
                "the declared public frozen viewport must actually reach the unsupported producer");
        assertTrue(inlet.water().isLiveWaterFlagSet(ZONE, ACT));
    }

    private record PreparedWetControl(Inlet inlet, HeadlessTestRunner runner, Observation baseline,
                                      byte[] decoded, byte[] expectedPixels) { }
    // END HCZ1_DIRECT_FF_CONTROLS

    private record Inlet(GameplayModeContext context, WorldSession world, LevelManager manager,
                         ObjectManager objects, ObjectServices services, Sonic3kLevel level,
                         Sonic3kLevelTitlePlcService plc, KosinskiModuleQueue journal,
                         WaterSystem water, DynamicWaterHandler handler, Camera camera,
                         AbstractPlayableSprite player) { }

    private record Observation(NemesisPlcQueueSnapshot queue, Pattern[] refs, byte[] pixels,
                               KosinskiModuleQueue.Snapshot wholeJournal,
                               boolean[] journalPresent, byte[] journalBytes) { }
}
