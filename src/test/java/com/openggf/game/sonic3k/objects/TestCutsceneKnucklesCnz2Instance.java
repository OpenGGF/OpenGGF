package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.CnzObjectEventBridge;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void registryRoutesCnzVacuumTubeButtonSubtypeToCnzHandler() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_CNZ);

        ObjectInstance button = registry.create(new ObjectSpawn(
                0x4780, 0x0728, Sonic3kObjectIds.CUTSCENE_BUTTON, 6, 0, false, 0));

        assertInstanceOf(Cnz2CutsceneButtonInstance.class, button,
                "Obj_CutsceneButton subtype $06 is the CNZ2 second-encounter button "
                        + "(off_65C40[$06] -> loc_65CAC, docs/skdisasm/sonic3k.asm:133951-134019); "
                        + "it must spawn the vacuum tubes instead of falling through to the AIZ cutscene button");
    }

    @Test
    void cnzCutsceneButtonPressesWaterAndPaletteRoute_notLevelTriggerRoute() {
        RecordingCnzBridge bridge = new RecordingCnzBridge();
        Camera buttonCamera = new Camera();
        buttonCamera.setY((short) 0x0280);
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(new ObjectSpawn(
                0x1D00, 0x027C, Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));
        button.setServices(new TestObjectServices() {
            @Override
            public LevelEventProvider levelEventProvider() {
                return bridge;
            }
        }.withCamera(buttonCamera));
        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        knuckles.forceButtonImpactForTest();
        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(knuckles);

        button.update(0, null);

        assertEquals(0x0350, bridge.waterTargetY,
                "Obj_CutsceneButton subtype $04 dispatches to loc_65C78: Target_water_level=$350 "
                        + "and a CNZ palette flash, not loc_65C72's Level_trigger_array+8 branch (subtype $02)");
        assertEquals(true, bridge.waterButtonArmed);
        assertEquals(0x0280 + 0x100, bridge.waterMeanLevel,
                "loc_65C78 seeds Mean_water_level = Camera_Y + $100 so the flood is already risen");
        assertEquals(0x14, bridge.screenShakeFrames,
                "loc_65C78 also writes Screen_shake_flag=$14");
        assertNotNull(button.getSpawnedFlashForTest(),
                "loc_65C78 spawns the loc_62480 lights-off flash child (subtype 0, no restore)");
        assertFalse(button.getSpawnedFlashForTest().restoresAfterForTest(),
                "the cutscene button's flash leaves the dark palette in place (lights stay off)");
    }

    @Test
    void firstCnzCutsceneButtonWaitsForSecondLandingImpact() {
        RecordingCnzBridge bridge = new RecordingCnzBridge();
        Camera buttonCamera = new Camera();
        buttonCamera.setY((short) 0x0280);
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(new ObjectSpawn(
                0x1E00, 0x0338, Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));
        button.setServices(new TestObjectServices() {
            @Override
            public LevelEventProvider levelEventProvider() {
                return bridge;
            }
        }.withCamera(buttonCamera));
        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1E00, 0x0338, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(knuckles);

        button.update(0, null);

        assertEquals(0, bridge.waterTargetY,
                "The first CNZ2 button must not trigger from proximity during Knuckles' first rightward jump");

        knuckles.forceButtonImpactForTest();
        button.update(1, null);

        assertEquals(0x0350, bridge.waterTargetY,
                "The button fires once CutsceneKnux_CNZ2A reaches the second-landing button impact");
    }

    @Test
    void cnzVacuumTubeButtonSpawnsTubeControllersFromRomAction() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        CutsceneKnucklesCnz2BInstance knuckles = new CutsceneKnucklesCnz2BInstance(
                new ObjectSpawn(0x4780, 0x072C, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));
        knuckles.setServices(TestEnvironment.objectServices());
        knuckles.update(0, fixture.sprite());

        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(
                new ObjectSpawn(0x4780, 0x0728, Sonic3kObjectIds.CUTSCENE_BUTTON, 6, 0, false, 0));
        button.setServices(TestEnvironment.objectServices());
        button.update(0, fixture.sprite());

        long tubeCount = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(CnzVacuumTubeInstance.class::isInstance)
                .count();
        assertTrue(tubeCount >= 2,
                "Obj_CutsceneButton subtype $06 dispatches to loc_65CAC, which allocates "
                        + "the two Obj_CNZVacuumTube controllers at $4740/$0828 and $4740/$0A28");
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

    @Test
    void firstCnzCutsceneSpawnsBlockingWallAtRomChildOffset() {
        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        knuckles.setServices(TestEnvironment.objectServices());

        knuckles.update(0, null);

        CutsceneKnuxCnz2WallInstance wall = knuckles.getSpawnedWallForTest();
        assertNotNull(wall,
                "CutsceneKnux_CNZ2A init creates ChildObjDat_66560 -> loc_62458, the invisible "
                        + "SolidObjectFull2 wall that blocks Sonic (docs/skdisasm/sonic3k.asm:129076,129175,134968)");
        assertEquals(0x1D00 - 0x20, wall.getX(),
                "CreateChild1_Normal applies the ChildObjDat x offset -$20 (sonic3k.asm:134971,176931-176936)");
        assertEquals(0x0280 - 0x6C, wall.getY(),
                "CreateChild1_Normal applies the ChildObjDat y offset -$6C (sonic3k.asm:134971,176937-176942)");
    }

    @Test
    void secondCnzCutsceneExitDoesNotEnterPlayableKnucklesTeleporterRoute() throws Exception {
        RecordingCnzBridge bridge = new RecordingCnzBridge();
        Camera camera = new Camera();
        camera.setY((short) 0x0600);
        camera.setMinX((short) 0x4300);
        camera.setMaxX((short) 0x5000);
        AbstractObjectInstance.updateCameraBounds(0x4700, 0x0600, 0x4840, 0x06E0, 0);

        CutsceneKnucklesCnz2BInstance knuckles = new CutsceneKnucklesCnz2BInstance(
                new ObjectSpawn(0x45C0, 0x0720, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));
        knuckles.setServices(new TestObjectServices() {
            @Override
            public com.openggf.game.LevelEventProvider levelEventProvider() {
                return bridge;
            }
        }.withCamera(camera));
        setPrivateEnumField(knuckles, "phase", "EXIT_RIGHT");
        setPrivateIntField(knuckles, "currentX", 0x4900);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4760, (short) 0x0700);
        player.setObjectControlled(true);
        player.setControlLocked(true);

        knuckles.update(0, player);
        knuckles.update(1, player);

        assertEquals(0, bridge.beginTeleporterRouteCalls,
                "CutsceneKnux_CNZ2B loc_625E2 restores Sonic/Tails control and level music; "
                        + "the $4750-$48E0 teleporter clamp belongs only to CNZ2_ScreenEvent after Player_mode==3");
        assertEquals(0x4300, camera.getMinX() & 0xFFFF,
                "The Sonic/Tails rival-Knuckles handoff must leave the current camera min X alone");
        assertEquals(0x5000, camera.getMaxX() & 0xFFFF,
                "The Sonic/Tails rival-Knuckles handoff must not install the playable-Knuckles teleporter max X");
        assertFalse(player.isObjectControlled(),
                "loc_625E2 clears Player_1 object_control before loc_6261A starts forcing left");
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, player.getForcedInputMask(),
                "loc_6261A writes left into Ctrl_1_logical so Sonic/Tails walks into the vacuum tube");
    }

    @AfterEach
    void tearDown() {
        CutsceneKnucklesCnz2AInstance.clearActiveInstanceForTests();
        CutsceneKnucklesCnz2BInstance.clearActiveInstanceForTests();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    private static void setPrivateEnumField(Object target, String fieldName, String enumConstant)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> value = Enum.valueOf((Class<? extends Enum>) field.getType(), enumConstant);
        field.set(target, value);
    }

    private static void setPrivateIntField(Object target, String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
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
        private int waterMeanLevel;
        private int screenShakeFrames;
        private int beginTeleporterRouteCalls;

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
        @Override public void setWaterMeanLevel(int meanY) { waterMeanLevel = meanY; }
        @Override public void triggerScreenShake(int frames) { screenShakeFrames = frames; }
        @Override public void beginKnucklesTeleporterRoute() { beginTeleporterRouteCalls++; }
        @Override public void endKnucklesTeleporterRoute() {}
        @Override public void markTeleporterBeamSpawned() {}
    }
}
