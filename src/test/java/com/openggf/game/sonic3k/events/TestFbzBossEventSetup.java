package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.FbzCloudInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzBossEventSetup {
    @Test
    void normalSetupIsOneForwardAllocationChainWithNoArtQueueOrMotionSideEffects() {
        Sonic3kFBZEvents events = act2();
        RecordingSetup effects = new RecordingSetup(-1);

        events.setUpAct2BossEvent(effects, false);

        assertEquals(List.of("controller", "pillar", "clear",
                "cloud:9", "cloud:8", "cloud:7", "cloud:6", "cloud:5",
                "cloud:4", "cloud:3", "cloud:2", "cloud:1", "cloud:0", "palette"), effects.log);
        assertEquals(0, effects.artQueues);
        assertEquals(0, events.getBossBackgroundOffsetX());
        assertEquals(0, events.getBossBackgroundOffsetY());
        assertEquals(Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY, events.getCollisionMode());
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL, events.getPlaneAssignmentMode());
        for (int slot = 0; slot < 10; slot++) {
            assertEquals(ObjectRefId.dynamic(20 + slot, 1, slot), events.getCloudRewindId(slot));
        }
    }

    @Test
    void allocationFailureRetainsPrefixDoesNotRetryAndStillClearsAddressesAndPatchesPalette() {
        Sonic3kFBZEvents events = act2();
        RecordingSetup effects = new RecordingSetup(4);

        events.setUpAct2BossEvent(effects, false);
        events.setUpAct2BossEvent(effects, false);

        assertEquals(List.of("controller", "pillar", "clear", "cloud:9", "cloud:8", "palette"), effects.log);
        assertNull(events.getCloudRewindId(2));
        assertTrue(events.getCloudRewindIds().subList(2, 10).stream().allMatch(java.util.Objects::isNull));
    }

    @Test
    void reentryRunsSetupBeforeCloudThenPillarArtQueue() {
        Sonic3kFBZEvents events = act2();
        RecordingSetup effects = new RecordingSetup(-1);

        events.setUpAct2BossEvent(effects, true);

        assertEquals("palette", effects.log.get(13));
        assertEquals(List.of("art:cloud", "art:pillar"), effects.log.subList(14, 16));
        assertEquals(2, effects.artQueues);
    }

    @Test
    void controllerOrPillarFailureSkipsCloudClearButPaletteRemainsUnconditional() {
        Sonic3kFBZEvents controllerFailEvents = act2();
        RecordingSetup controllerFail = new RecordingSetup(1);
        controllerFailEvents.setUpAct2BossEvent(controllerFail, false);
        assertEquals(List.of("controller", "palette"), controllerFail.log);

        Sonic3kFBZEvents pillarFailEvents = act2();
        RecordingSetup pillarFail = new RecordingSetup(2);
        pillarFailEvents.setUpAct2BossEvent(pillarFail, false);
        assertEquals(List.of("controller", "pillar", "palette"), pillarFail.log);
    }

    @Test
    void palettePatchIsExactNineWordLineFourPayloadForNormalAndTargetBanks() {
        byte[] outdoor = new byte[16];
        for (int i = 0; i < outdoor.length; i++) outdoor[i] = (byte) (0x40 + i);
        byte[] patch = Sonic3kFBZEvents.bossApproachPalettePatch(outdoor);
        assertArrayEquals(new byte[]{0x0E, (byte) 0xEE,
                0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,
                0x48,0x49,0x4A,0x4B,0x4C,0x4D,0x4E,0x4F}, patch);
    }

    @Test
    void reentryArtQueueUsesNativeCloudThenPillarDestinations() {
        var entries = Sonic3kPlcLoader.fbz2SubbossDefeatKosmEntries();
        assertEquals(Sonic3kConstants.ART_KOSM_FBZ_CLOUD_ADDR, entries.get(0).sourceAddress());
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_CLOUD * 32, entries.get(0).destinationVramBytes());
        assertEquals(Sonic3kConstants.ART_KOSM_FBZ_BOSS_PILLAR_ADDR, entries.get(1).sourceAddress());
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_BOSS_PILLAR * 32, entries.get(1).destinationVramBytes());
    }

    @Test
    void realObjectManagerExhaustionRejectsTheUnadoptedObject() {
        ObjectManager manager = s3kObjectManager();
        manager.reserveAllButNFreeSlots(0);

        FbzCloudInstance rejected = manager.createDynamicObject(() -> new FbzCloudInstance(9));

        assertNull(rejected, "AllocateObject failure must be observable by the forward setup chain");
        assertTrue(manager.activeObjectsOfType(FbzCloudInstance.class).isEmpty());
    }

    @Test
    void productionAllocationEffectsStopAtRealSstExhaustionAndRetainOnlyThePrefix() {
        Sonic3kFBZEvents events = act2();
        ObjectManager manager = s3kObjectManager();
        manager.reserveAllButNFreeSlots(4);
        List<String> sideEffects = new ArrayList<>();

        events.setUpAct2BossEvent(events.bossEventSetupEffects(manager,
                () -> sideEffects.add("palette"),
                () -> sideEffects.add("cloud-art"),
                () -> sideEffects.add("pillar-art")), false);

        assertEquals(1, manager.activeObjectsOfType(
                com.openggf.game.sonic3k.objects.FbzEndBossEventControlInstance.class).size());
        assertEquals(1, manager.activeObjectsOfType(
                com.openggf.game.sonic3k.objects.FbzBossPillarInstance.class).size());
        assertEquals(2, manager.activeObjectsOfType(FbzCloudInstance.class).size());
        assertNotNull(events.getCloudRewindId(0));
        assertNotNull(events.getCloudRewindId(1));
        assertTrue(events.getCloudRewindIds().subList(2, 10).stream().allMatch(java.util.Objects::isNull));
        assertEquals(List.of("palette"), sideEffects);
    }

    @Test
    void rewindingAcrossSetupReplaysTheExactLivePrefixIdsSlotsAndNullTail() {
        Sonic3kFBZEvents events = act2();
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        ObjectManager manager = s3kObjectManager();
        manager.reserveAllButNFreeSlots(4);
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot beforeSetup = rewind.capture();
        byte[] beforeSetupEvents = runtime.captureBytes();

        Sonic3kFBZEvents.BossEventSetupEffects effects = events.bossEventSetupEffects(
                manager, () -> { }, () -> { }, () -> { });
        events.setUpAct2BossEvent(effects, false);
        List<ObjectRefId> expectedIds = events.getCloudRewindIds();
        int[] expectedSlots = manager.getActiveObjects().stream()
                .mapToInt(object -> ((com.openggf.level.objects.AbstractObjectInstance) object).getSlotIndex())
                .sorted().toArray();
        assertEquals(2, expectedIds.stream().filter(java.util.Objects::nonNull).count());

        rewind.restore(beforeSetup);
        runtime.restoreBytes(beforeSetupEvents);
        // reserveAllButNFreeSlots is deliberately test-only pressure and is not
        // gameplay SST state; recreate the same external capacity condition.
        manager.reserveAllButNFreeSlots(4);
        events.setUpAct2BossEvent(events.bossEventSetupEffects(
                manager, () -> { }, () -> { }, () -> { }), false);

        assertEquals(expectedIds, events.getCloudRewindIds());
        assertArrayEquals(expectedSlots, manager.getActiveObjects().stream()
                .mapToInt(object -> ((com.openggf.level.objects.AbstractObjectInstance) object).getSlotIndex())
                .sorted().toArray());
        assertTrue(events.getCloudRewindIds().subList(2, 10).stream()
                .allMatch(java.util.Objects::isNull));
    }

    private static ObjectManager s3kObjectManager() {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "test"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        holder[0] = new ObjectManager(List.of(), registry, 0, null, null,
                GraphicsManager.getInstance(), null, services);
        return holder[0];
    }

    private static Sonic3kFBZEvents act2() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        return events;
    }

    private static final class RecordingSetup implements Sonic3kFBZEvents.BossEventSetupEffects {
        private final int failAllocationNumber;
        private final List<String> log = new ArrayList<>();
        private int allocations;
        private int artQueues;

        private RecordingSetup(int failAllocationNumber) {
            this.failAllocationNumber = failAllocationNumber;
        }

        @Override public boolean createController() { log.add("controller"); return succeeds(); }
        @Override public boolean createPillar() { log.add("pillar"); return succeeds(); }
        @Override public void clearCloudAddressPairs() { log.add("clear"); }
        @Override public ObjectRefId createCloud(int selector, int addressSlot) {
            log.add("cloud:" + selector);
            if (!succeeds()) return null;
            return ObjectRefId.dynamic(20 + addressSlot, 1, addressSlot);
        }
        @Override public void applyPalettePatch() { log.add("palette"); }
        @Override public void queueCloudArt() { log.add("art:cloud"); artQueues++; }
        @Override public void queuePillarArt() { log.add("art:pillar"); artQueues++; }

        private boolean succeeds() {
            return failAllocationNumber < 0 || ++allocations != failAllocationNumber;
        }
    }
}
