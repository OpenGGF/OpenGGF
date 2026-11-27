package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.boss.ttz.TtzChaseEggman;
import com.openggf.game.soniccd.objects.mmz.MmzPohBee;
import com.openggf.game.soniccd.objects.mmz.MmzScarab;
import com.openggf.game.soniccd.objects.ttz.ObjFallSpikes;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCdDeleteOtherObject {
    @TempDir Path tempDir;
    private EngineContext previous;
    private CdDiscDataSource disc;
    private AbstractPlayableSprite sonic;
    private ObjectManager objects;

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
        objects = GameServices.level().getObjectManager();
    }

    @Test
    void entryBarrierReleasesGearOnArenaEntry() throws Exception {
        boot(CdStagePayload.R33C);
        CdBossPart barrier = addBoss(0x30, 0x430, 0x540);
        barrier.update(0, null);
        int gearSlot = part(barrier, "gear").getSlotIndex();
        barrier.setRoutine(6);
        field(barrier, "sequenceTimer").setInt(barrier, 0x78);
        NativePositionOps.writeXPosResetSubpixel(sonic, 0x480);
        NativePositionOps.writeYPosResetSubpixel(sonic, 0x51C);

        barrier.update(0, sonic);

        assertReusable(gearSlot);
    }

    @Test
    void bossMachineReleasesGearOnEncounterExit() throws Exception {
        boot(CdStagePayload.R33D);
        CdBossPart machine = addBoss(0x31, 0x700, 0x150);
        machine.update(0, null);
        int gearSlot = part(machine, "gear").getSlotIndex();
        machine.setRoutine(0xA);
        field(machine, "sequenceTimer").setInt(machine, 0x78);
        field(machine, "flags").setInt(machine, 1);
        field(machine, "attackCount").setInt(machine, 4);

        machine.update(0, null);

        assertReusable(gearSlot);
    }

    @Test
    void eggmanReleasesTheMobileAtTheEscapeBoundary() throws Exception {
        boot(CdStagePayload.R43C);
        CdBossPart eggman = addBoss(0x4C, 0xB2F, 0x500);
        eggman.update(0, null);
        int mobileSlot = part(eggman, "mobile").getSlotIndex();
        eggman.setRoutine(0x14);
        field(eggman, "timer").setInt(eggman, 0x3B);

        eggman.update(0, null);

        assertEquals(4, eggman.routine());
        assertReusable(mobileSlot);
    }

    @Test
    void chaseEggmanReleasesOneDoorPerUpdate() throws Exception {
        boot(CdStagePayload.R43D);
        CdBossPart mobile = addBoss(0x4A, 0x6C0, 0x450);
        mobile.update(0, sonic);
        var eggman = objects.getActiveObjects().stream()
                .filter(TtzChaseEggman.class::isInstance).map(TtzChaseEggman.class::cast)
                .findFirst().orElseThrow();
        eggman.update(0, sonic);
        var upper = part(eggman, "upperDoor");
        var lower = part(eggman, "lowerDoor");
        int upperSlot = upper.getSlotIndex();
        int lowerSlot = lower.getSlotIndex();
        eggman.setRoutine(4);
        NativePositionOps.writeXPosResetSubpixel(sonic, 0x840);
        NativePositionOps.writeYPosResetSubpixel(sonic, 0x550);

        eggman.update(0, sonic);
        assertReusable(upperSlot);
        assertEquals(lower.getSpawn().objectId(), objects.objectIdInSlot(lowerSlot));

        eggman.update(0, sonic);
        assertReusable(lowerSlot);
        assertFalse(eggman.isDestroyed());
        eggman.update(0, sonic);
        assertTrue(eggman.isDestroyed());
    }

    @Test
    void fallingSpikesReleaseTheTipOnFloorContact() throws Exception {
        boot(CdStagePayload.R41A);
        int x = sonic.getCentreX();
        int y = sonic.getCentreY() + sonic.getYRadius() - 0x40;
        while (ObjectTerrainUtils.checkFloorDist(x, y, 0x40).distance() >= 0
                && y < sonic.getCentreY()) {
            y++;
        }
        assertTrue(ObjectTerrainUtils.checkFloorDist(x, y, 0x40).distance() < 0, "floor contact");
        var spikes = add(new ObjFallSpikes(spawn(0x32, 0, x, y)));
        int allocatorSlot = objects.allocateSlotAfter(spikes.getSlotIndex());
        spikes.update(0, sonic);
        int tipSlot = part(spikes, "tip").getSlotIndex();
        assertTrue(allocatorSlot >= 0 && allocatorSlot < tipSlot, "allocate before the walk reaches the tip");
        ObjectSpawn probeSpawn = spawn(0x7F, 0, x, y);
        var replacement = new UpdateProbe(probeSpawn, () -> {});
        objects.addDynamicObjectAtSlot(
                new UpdateProbe(probeSpawn, () -> objects.addDynamicObjectAfterCurrent(replacement)),
                allocatorSlot);
        field(spikes, "routine").setInt(spikes, 8);
        field(spikes, "renderPassSeen").setBoolean(spikes, true);
        field(spikes, "drawnOnScreen").setBoolean(spikes, true);

        objects.update(GameServices.camera().getX(), sonic, List.of(), 1);

        assertEquals(10, field(spikes, "routine").getInt(spikes));
        assertEquals(tipSlot, replacement.getSlotIndex(), "the next object can reuse the tip's slot");
        assertEquals(1, replacement.updates, "the replacement runs when the walk reaches the cleared slot");
    }

    @Test
    void scarabReleasesTheBeetleWhenTheMonitorDespawns() throws Exception {
        assertScarabReleasesSurvivor("item", "body");
    }

    @Test
    void scarabReleasesTheMonitorWhenTheBeetleDespawns() throws Exception {
        assertScarabReleasesSurvivor("body", "item");
    }

    private void assertScarabReleasesSurvivor(String despawnedPart, String survivorPart) throws Exception {
        boot(CdStagePayload.R81B);
        var scarab = add(new MmzScarab(spawn(0x2F, 0x80, sonic.getCentreX() + 0x50, sonic.getCentreY())));
        scarab.update(0, sonic);
        var survivor = part(scarab, survivorPart);
        int survivorSlot = survivor.getSlotIndex();
        objects.removeDynamicObject(part(scarab, despawnedPart));

        scarab.update(0, sonic);

        assertReusable(survivorSlot);
    }

    @Test
    void pohBeeWeightReleasesTheBodyOnLinkAllocationFailure() throws Exception {
        boot(CdStagePayload.R81B);
        int x = sonic.getCentreX() + 0x70;
        int y = sonic.getCentreY();
        var placement = new ObjectSpawn(x, y, 0x2E, 1, 0, false, y, 0);
        objects.resyncSpawnList(List.of(placement));
        objects.reset(GameServices.camera().getX());
        objects.preloadInitialSpawnsForHydration();
        var bee = assertInstanceOf(MmzPohBee.class, objects.getActiveObjectForRewind(placement));
        bee.update(0, sonic);
        int bodySlot = bee.getSlotIndex();
        var weight = objects.getActiveObjects().stream()
                .filter(o -> o != bee && o.getSpawn().objectId() == 0x2E)
                .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
        int weightSlot = weight.getSlotIndex();
        objects.reserveAllButNFreeSlots(0);

        weight.update(0, sonic);

        assertReusable(bodySlot);
        assertEquals(0x2E, objects.objectIdInSlot(weightSlot), "only the body is cleared on allocation failure");
        weight.update(0, sonic);
        assertTrue(weight.isDestroyed(), "the weight retires the next time it checks the body's slot");
    }

    private void assertReusable(int slot) {
        assertTrue(slot >= 0, "target occupied an admitted slot");
        assertEquals(slot, objects.allocateSlotAfter(slot - 1),
                "the next allocation can already reuse the cleared slot");
    }

    private CdBossPart addBoss(int id, int x, int y) {
        var registry = GameModuleRegistry.getCurrent().createObjectRegistry();
        return add((CdBossPart) registry.create(spawn(id, 0, x, y)));
    }

    private <T extends AbstractObjectInstance> T add(T object) {
        objects.addDynamicObject(object);
        return object;
    }

    private static ObjectSpawn spawn(int id, int subtype, int x, int y) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private static final class UpdateProbe extends AbstractObjectInstance {
        private final Runnable onUpdate;
        private int updates;

        private UpdateProbe(ObjectSpawn spawn, Runnable onUpdate) {
            super(spawn, "slot probe");
            this.onUpdate = onUpdate;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
            onUpdate.run();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static AbstractObjectInstance part(AbstractObjectInstance object, String name) throws Exception {
        return assertInstanceOf(AbstractObjectInstance.class, field(object, name).get(object), "linked " + name);
    }

    private static Field field(AbstractObjectInstance object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(name);
    }
}
