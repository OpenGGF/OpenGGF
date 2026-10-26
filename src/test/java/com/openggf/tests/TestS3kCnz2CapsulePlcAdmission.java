package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Positioned CNZ2 producer oracle for native044 loc_5216C and PLC_EggCapsule.
 * Public service/registry snapshot reads are explicit test actions. The fixture
 * uses the existing direct level-load component inlet and retains its actual FIFO.
 * No clearing, draining or title-loop warmup establishes the queue prerequisite.
 * This checks only successful append, preserving every preceding descriptor and
 * its progress/order. It does not qualify preparation stage, tile publication,
 * complete physical queue capacity or a native route/caller.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz2CapsulePlcAdmission {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void successfulSecondAllocationAppendsTheOriginalRawDescriptor() throws Exception {
        var fixture = inlet();
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var service = requireRegisteredQueue(fixture);
        var before = service.capture();
        assertNull(before.activeEntry(), "no prepared head may consume VBlank work before this producer");
        var rom = objects.getObjectServices().romReader();
        // Independent original ROM data: signed count-minus-one, source, byte destination.
        assertEquals(0, rom.readU16BE(0x083D74), "PLC_EggCapsule has one raw descriptor");
        int source = rom.readU32BE(0x083D76);
        int destination = rom.readU16BE(0x083D7A);
        assertEquals(0x0DD990, source);
        assertEquals(0x9280, destination);
        int header = rom.readU16BE(source);
        assertEquals(0x0046, header, "original Nemesis header, not an implementation count");
        int patterns = header & 0x7FFF;
        var capsuleEntry = new NemesisPlcQueueSnapshot.Entry(
                source, destination / 32, patterns, patterns);

        stepAdmission(fixture);
        var teleporters = objects.activeObjectsOfType(CnzTeleporterInstance.class);
        var capsules = objects.activeObjectsOfType(CnzEggCapsuleInstance.class);
        // Establish the actual successful producer branch before its final queue oracle.
        assertEquals(1, teleporters.size(), "first AllocateObject succeeds");
        assertEquals(1, capsules.size(), "second CreateNewSprite4 succeeds");
        var teleporter = teleporters.getFirst();
        var capsule = capsules.getFirst();
        assertTrue(teleporter.getSlotIndex() >= 0);
        assertTrue(capsule.getSlotIndex() > teleporter.getSlotIndex());
        assertFalse(teleporter.isDestroyed());
        assertFalse(capsule.isDestroyed());
        assertEquals(0x4980, capsule.getSpawn().x());
        assertEquals(0x0A20, capsule.getSpawn().y());
        assertSame(service, requireRegisteredQueue(fixture));
        var expected = new ArrayList<>(logicalEntries(before));
        expected.add(capsuleEntry);
        // Load_PLC_Raw finds the first free descriptor and appends. Moving an
        // unchanged existing head into the prepared slot does not reorder it.
        assertEquals(expected, logicalEntries(service.capture()),
                "preserve the full prior FIFO and append exactly one original 70-pattern descriptor");
    }

    @ParameterizedTest(name = "actual free slots={0}")
    @ValueSource(ints = {0, 1})
    void eitherAllocationFailurePreservesTheActualLogicalFifo(int freeSlots) throws Exception {
        var fixture = inlet();
        var objects = GameServices.level().getObjectManager();
        var service = requireRegisteredQueue(fixture);
        var before = service.capture();
        assertNull(before.activeEntry(), "no prepared head may consume VBlank work before this producer");
        // Existing public resource-exhaustion inlet; no actor slot is assigned by the test.
        objects.reserveAllButNFreeSlots(freeSlots);
        assertEquals(freeSlots, objects.getObjectSlotCapacity() - objects.getActiveObjectSlotCount());
        stepAdmission(fixture);
        assertEquals(freeSlots, objects.activeObjectsOfType(CnzTeleporterInstance.class).size());
        assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty(),
                "the second allocation did not succeed");
        assertSame(service, requireRegisteredQueue(fixture));
        assertEquals(logicalEntries(before), logicalEntries(service.capture()),
                "failed allocation preserves every preceding descriptor, progress field and FIFO position");
    }

    private static HeadlessTestFixture inlet() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40).startPositionIsCentre().build();
        var primary = assertInstanceOf(Knuckles.class, fixture.sprite());
        var level = GameServices.level();
        assertNotNull(level.getCurrentLevel());
        assertEquals(Sonic3kZoneIds.ZONE_CNZ, level.getCurrentZone());
        assertEquals(1, level.getCurrentAct());
        assertSame(primary, GameServices.sprites().getMainPlayable());
        assertSame(primary, fixture.camera().getFocusedSprite());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertEquals(Sonic3kCNZEvents.FG_ACT2_ENTRY, events().getForegroundRoutine());
        requirePlayerInlet(fixture);
        assertFalse(primary.isControlLocked());
        assertTrue(level.getObjectManager().activeObjectsOfType(CnzTeleporterInstance.class).isEmpty());
        assertTrue(level.getObjectManager().activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty());
        return fixture;
    }

    private static Sonic3kLevelTitlePlcService requireRegisteredQueue(HeadlessTestFixture fixture) {
        var service = com.openggf.game.session.WorldPlcLifecycleAccess.require(GameServices.level().getWorldSession(), Sonic3kLevelTitlePlcService.class);
        assertNotNull(service, "actual S3K Nemesis service is required");
        assertSame(service, com.openggf.game.session.WorldPlcLifecycleAccess.service(GameServices.level().getWorldSession()));
        var snapshot = service.capture();
        var world = fixture.gameplayMode().getRewindRegistry().capture();
        assertTrue(world.entries().containsKey(service.key()), "actual queue owner must be registered");
        assertEquals(snapshot, world.get(service.key()));
        return service;
    }

    /** Logical FIFO projection only: every complete entry and its original order are retained. */
    private static List<NemesisPlcQueueSnapshot.Entry> logicalEntries(NemesisPlcQueueSnapshot snapshot) {
        var entries = new ArrayList<NemesisPlcQueueSnapshot.Entry>();
        if (snapshot.activeEntry() != null) entries.add(snapshot.activeEntry());
        entries.addAll(snapshot.queuedEntries());
        return List.copyOf(entries);
    }

    private static Sonic3kCNZEvents events() {
        var manager = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        return assertInstanceOf(Sonic3kCNZEvents.class, manager.getCnzEvents());
    }

    private static void requirePlayerInlet(HeadlessTestFixture fixture) {
        assertFalse(fixture.sprite().getDead());
        assertTrue((fixture.sprite().getCentreX() & 0xFFFF) >= 0x4880
                && (fixture.sprite().getCentreX() & 0xFFFF) < 0x4A38
                && (fixture.sprite().getCentreY() & 0xFFFF) >= 0x0B00,
                "declared Player_1 admission inlet remains below the separate teleporter arm");
    }

    private static void stepAdmission(HeadlessTestFixture fixture) {
        int before = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        requirePlayerInlet(fixture);
        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events().getForegroundRoutine());
        assertEquals(0x4750, fixture.camera().getMinX() & 0xFFFF);
        assertEquals(0x48E0, fixture.camera().getMaxX() & 0xFFFF);
        assertFalse(GameServices.gameState().isEndOfLevelFlag());
        assertFalse(fixture.sprite().isControlLocked());
    }
}
