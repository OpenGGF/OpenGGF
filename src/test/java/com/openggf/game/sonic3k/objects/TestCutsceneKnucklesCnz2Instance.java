package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.CnzObjectEventBridge;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_3K)
class TestCutsceneKnucklesCnz2Instance {
    @Test
    void registryRoutesCnzAct2CutsceneSubtypesToCnzHandlers() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance first = registry.create(new ObjectSpawn(
                0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        ObjectInstance second = registry.create(new ObjectSpawn(
                0x45C0, 0x0720, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));

        assertInstanceOf(CutsceneKnucklesCnz2AInstance.class, first,
                "Subtype $0C is CutsceneKnux_CNZ2A, not the AIZ2 fallback");
        assertInstanceOf(CutsceneKnucklesCnz2BInstance.class, second,
                "Subtype $10 is CutsceneKnux_CNZ2B, not the AIZ2 fallback");
    }

    @Test
    void cnzCutsceneObjectsStartAtTheirLayoutPositions() {
        CutsceneKnucklesCnz2AInstance first = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        CutsceneKnucklesCnz2BInstance second = new CutsceneKnucklesCnz2BInstance(
                new ObjectSpawn(0x45C0, 0x0720, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));

        assertEquals(0x1D00, first.getX());
        assertEquals(0x0280, first.getY());
        assertEquals(0x45C0, second.getX());
        assertEquals(0x0720, second.getY());
    }

    @Test
    void registryRoutesCnzCutsceneButtonSubtypeToCnzHandler() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_CNZ);

        ObjectInstance button = registry.create(new ObjectSpawn(
                0x1E00, 0x0338, Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));

        assertInstanceOf(Cnz2CutsceneButtonInstance.class, button,
                "Obj_CutsceneButton subtype $04 is the CNZ2 first-encounter button "
                        + "(off_65C40[$04] -> loc_65C78, docs/skdisasm/sonic3k.asm:133916-133994); "
                        + "the CNZ2 layout places it at X=$1E00 with subtype $04 "
                        + "(Levels/CNZ/Object Pos/2.bin), not the AIZ-only handler");
    }

    @Test
    void cnzCutsceneButtonPressesWaterAndPaletteRoute_notLevelTriggerRoute() {
        RecordingCnzBridge bridge = new RecordingCnzBridge();
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(new ObjectSpawn(
                0x1D00, 0x027C, Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));
        button.setServices(new TestObjectServices() {
            @Override
            public LevelEventProvider levelEventProvider() {
                return bridge;
            }
        });
        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(knuckles);

        button.update(0, null);

        assertEquals(0x0350, bridge.waterTargetY,
                "Obj_CutsceneButton subtype $04 dispatches to loc_65C78: Target_water_level=$350 "
                        + "and a CNZ palette flash, not loc_65C72's Level_trigger_array+8 branch (subtype $02)");
        assertEquals(true, bridge.waterButtonArmed);
        assertEquals(0x14, bridge.screenShakeFrames,
                "loc_65C78 also writes Screen_shake_flag=$14");
        assertNotNull(button.getSpawnedFlashForTest(),
                "loc_65C78 spawns the loc_62480 lights-off flash child (subtype 0, no restore)");
        assertFalse(button.getSpawnedFlashForTest().restoresAfterForTest(),
                "the cutscene button's flash leaves the dark palette in place (lights stay off)");
    }

    @Test
    void firstCnzCutsceneStoresCameraTargetsWithoutSnappingImmediately() {
        Camera camera = GameServices.camera();
        camera.setX((short) 0x1B80);
        camera.setY((short) 0x0180);
        camera.setMinX((short) 0x1B00);
        camera.setMaxX((short) 0x1C40);
        camera.setMinY((short) 0x0100);
        camera.setMaxYTarget((short) 0x0400);

        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        knuckles.setServices(TestEnvironment.objectServices());

        knuckles.update(0, null);

        assertEquals(0x1B00, camera.getMinX() & 0xFFFF,
                "CutsceneKnux_CNZ2A calls loc_85D70 first; it stores the target lock and old bounds, "
                        + "but does not snap Camera_min_X_pos to $1D00 on the init frame");
        assertNotEquals(0x1D00, camera.getMaxX() & 0xFFFF,
                "Camera_max_X_pos must remain on the pre-cutscene bound until loc_85CA4 observes "
                        + "Camera_X_pos reaching the target");
    }

    @AfterEach
    void tearDown() {
        CutsceneKnucklesCnz2AInstance.clearActiveInstanceForTests();
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }

    private static final class RecordingCnzBridge implements LevelEventProvider, CnzObjectEventBridge {
        private boolean waterButtonArmed;
        private int waterTargetY;
        private int screenShakeFrames;

        @Override public void initLevel(int zone, int act) {}
        @Override public void update() {}
        @Override public void setPendingArenaChunkDestruction(int chunkWorldX, int chunkWorldY) {}
        @Override public void setBossScrollState(int offsetY, int velocityY) {}
        @Override public void signalMinibossDefeatedForScrollControl() {}
        @Override public boolean consumeMinibossDefeatSignalForScrollControl() { return false; }
        @Override public void advanceMinibossBackgroundRoutineAfterScrollSnap() {}
        @Override public void setBossFlag(boolean value) {}
        @Override public void setEventsFg5(boolean value) {}
        @Override public void setWallGrabSuppressed(boolean value) {}
        @Override public void setWaterButtonArmed(boolean value) { waterButtonArmed = value; }
        @Override public boolean isWaterButtonArmed() { return waterButtonArmed; }
        @Override public void setWaterTargetY(int targetY) { waterTargetY = targetY; }
        @Override public void triggerScreenShake(int frames) { screenShakeFrames = frames; }
        @Override public void beginKnucklesTeleporterRoute() {}
        @Override public void endKnucklesTeleporterRoute() {}
        @Override public void markTeleporterBeamSpawned() {}
    }
}
