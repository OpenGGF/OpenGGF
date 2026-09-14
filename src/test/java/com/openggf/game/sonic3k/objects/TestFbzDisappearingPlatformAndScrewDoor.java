package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestFbzDisappearingPlatformAndScrewDoor {

    @BeforeEach
    void init() {
        AbstractObjectInstance.updateCameraBounds(0xF00, 0, 0x1300, 0x300, 0);
        Sonic3kLevelTriggerManager.reset();
    }

    @AfterEach
    void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        Sonic3kLevelTriggerManager.reset();
    }

    @Test
    void everyPlacedDisappearingSubtypeDecodesExactMaskOffsetAndAnimation() {
        for (int subtype : new int[]{0x79, 0x99, 0xB9, 0xD9, 0xF9, 0x89, 0xA9, 0xC9, 0xE9}) {
            var platform = new FbzDisappearingPlatformObjectInstance(spawn(0x79, subtype));
            int mask = switch (subtype & 0xC) {
                case 0 -> 0x7F;
                case 4 -> 0xFF;
                case 8 -> 0x1FF;
                default -> 0x3FF;
            };
            int offset = ((mask + 1) >> 4) * ((subtype >>> 4) & 0xF);

            assertEquals(mask, platform.phaseMask());
            assertEquals(offset, platform.phaseOffset());
            assertEquals(subtype & 3, platform.animationIndex());
            assertEquals(5, platform.getPriorityBucket());
        }
    }

    @Test
    void everyPlacedScrewSubtypeDecodesRomAxisDirectionDimensionsAndTrigger() {
        for (int subtype : new int[]{0x11, 0x12, 0x20, 0x50, 0x0A, 0x10, 0x14, 0x16,
                0x18, 0x19, 0x1A, 0x1B, 0x1F, 0x2D, 0x43, 0x4C, 0x55, 0x57}) {
            var door = new FbzScrewDoorObjectInstance(spawn(0x7A, subtype));
            int animation = (subtype >>> 4) & 7;
            int[][] sizes = {{8, 0x20}, {0x20, 8}, {0x40, 8}};
            int sizeRow = (animation & 6) / 2;

            assertEquals(animation, door.animationIndex());
            assertEquals(sizes[sizeRow][0], door.nativeWidth());
            assertEquals(sizes[sizeRow][1], door.nativeHeight());
            assertEquals(door.nativeWidth(), door.getBalanceWidthPixels(),
                    "Sonic_Balance must read Obj_FBZScrewDoor width_pixels");
            assertEquals(door.nativeWidth(), door.getOnScreenHalfWidth(),
                    "Render_Sprites must read Obj_FBZScrewDoor width_pixels");
            assertEquals(door.nativeHeight(), door.getOnScreenHalfHeight(),
                    "Render_Sprites must read Obj_FBZScrewDoor height_pixels");
            assertEquals(subtype & 0xF, door.triggerIndex());
            assertEquals((subtype & 0x60) != 0, door.horizontalMode());
            assertEquals((subtype & 0x10) != 0, door.negativeDirection());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,false", "1,true", "16,true", "17,false"})
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void disappearingTopLandingRequiresNegativeNativeOverlap(int overlap, boolean lands) {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var level = com.openggf.game.GameServices.level();
        var manager = level.getObjectManager();
        var platform = manager.createDynamicObject(() -> new FbzDisappearingPlatformObjectInstance(
                new ObjectSpawn(0x0E90, 0x0470, 0x79, 0xE9, 0, false, 0)));
        level.setFrameCounter((-platform.phaseOffset()) & platform.phaseMask());
        for (int i = 0; i <= 6; i++) platform.update(100 + i, fixture.sprite());
        assertTrue(platform.isSolidFor(fixture.sprite()));
        platform.snapshotPreUpdatePosition();
        var player = fixture.sprite();
        player.setRolling(true);
        player.applyRollingRadii(false);
        player.setCentreX((short) platform.getX());
        player.setCentreY((short) (platform.getY() - 0x11 - player.getYRadius() - 4 + overlap));
        player.setAir(true);
        player.setYSpeed((short) 0x1D0);
        int beforeY = player.getCentreY();

        manager.processImmediateInlineSolidCheckpoint(platform, player, List.of());

        assertEquals(lands, manager.isRidingObject(player, platform));
        assertEquals(lands, manager.hasObjectStandingBit(player, platform));
        if (!lands) {
            assertEquals(beforeY, player.getCentreY(), "rejected top overlap performs no position write");
            assertEquals(0x1D0, player.getYSpeed());
        }
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void disappearingLandingKeepsSnapBeforeNonRollingRadiusReset() {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var level = com.openggf.game.GameServices.level();
        var manager = level.getObjectManager();
        var platform = manager.createDynamicObject(() -> new FbzDisappearingPlatformObjectInstance(
                new ObjectSpawn(0x0EF0, 0x0418, 0x79, 0xC9, 0, false, 0)));
        var player = fixture.sprite();
        level.setFrameCounter((-platform.phaseOffset()) & platform.phaseMask());
        for (int i = 0; i <= 6; i++) {
            platform.update(100 + i, player);
        }
        platform.snapshotPreUpdatePosition();
        player.setRolling(false);
        player.applyRollingRadii(false);
        int entryRadius = player.getYRadius();
        player.setCentreX((short) platform.getX());
        player.setCentreY((short) (platform.getY() - 0x11 - entryRadius - 3));
        player.setAir(true);
        player.setYSpeed((short) 0xE0);

        manager.processImmediateInlineSolidCheckpoint(platform, player, List.of());

        assertTrue(manager.isRidingObject(player, platform));
        assertFalse(player.getAir());
        assertFalse(player.getRolling());
        assertEquals(player.getStandYRadius(), player.getYRadius(), "Player_TouchFloor restores radii");
        assertEquals(platform.getY() - 0x11 - entryRadius - 1, player.getCentreY(),
                "loc_1E45A snaps with entry y_radius; non-rolling TouchFloor does not shift Y");
    }

    @Test
    void disappearingActivationReadsLevelClockRatherThanObjectVintClock() {
        var level = mock(com.openggf.level.LevelManager.class);
        for (int subtype : new int[]{0x79, 0x99, 0xB9, 0xD9, 0xF9, 0x89, 0xA9, 0xC9, 0xE9}) {
            var platform = new FbzDisappearingPlatformObjectInstance(spawn(0x79, subtype));
            platform.setServices(new PlayersServices(null, List.of()).withLevelManager(level));
            int activation = (-platform.phaseOffset()) & platform.phaseMask();
            when(level.getFrameCounter()).thenReturn(activation - 1);
            platform.update(activation, null);
            assertEquals(2, platform.mappingFrame(), "VInt equality cannot activate subtype " + subtype);
            when(level.getFrameCounter()).thenReturn(activation);
            platform.update(activation + 11, null);
            assertEquals(1, platform.mappingFrame(), "Level_frame_counter owns loc_3BB08 phase gate");
            for (int i = 0; i < 6; i++) platform.update(activation + 12 + i, null);
            assertTrue(platform.isSolidFor(null), "script duration counts subsequent object dispatches");
        }
    }

    @Test
    void disappearingPlatformRunsExactSixNonSolidThenOneHundredTwentyOneSolidGlobalPhaseCycle() {
        var platform = new FbzDisappearingPlatformObjectInstance(spawn(0x79, 0x79));
        platform.setServices(new PlayersServices(null, List.of()));
        int activation = (-platform.phaseOffset()) & platform.phaseMask();
        platform.update(activation, null);

        for (int i = 0; i < 6; i++) {
            assertEquals(1, platform.mappingFrame());
            assertFalse(platform.isSolidFor(null));
            if (i < 5) {
                platform.update(activation + i + 1, null);
            }
        }

        platform.update(activation + 6, null);
        for (int i = 0; i < 121; i++) {
            assertEquals(0, platform.mappingFrame());
            assertTrue(platform.isSolidFor(null));
            if (i < 120) {
                platform.update(activation + 7 + i, null);
            }
        }

        platform.update(activation + 127, null);
        assertEquals(1, platform.mappingFrame());
        assertFalse(platform.isSolidFor(null));
    }

    @Test
    void everyNonzeroFrameDetachesEveryParticipatingRider() {
        TestSprite main = new TestSprite();
        TestSprite sidekick = new TestSprite();
        main.setOnObject(true);
        main.setAir(false);
        sidekick.setOnObject(true);
        sidekick.setAir(false);
        var platform = new FbzDisappearingPlatformObjectInstance(spawn(0x79, 0x79));
        platform.setServices(new PlayersServices(main, List.of(sidekick)));
        SolidObjectListener listener = platform;
        listener.onSolidContact(main, new SolidContact(true, false, false, true, false), 0);
        listener.onSolidContact(sidekick, new SolidContact(true, false, false, true, false), 0);

        platform.update((-platform.phaseOffset()) & platform.phaseMask(), main);

        assertFalse(main.isOnObject());
        assertTrue(main.getAir());
        assertFalse(sidekick.isOnObject());
        assertTrue(sidekick.getAir());
    }

    @Test
    void disappearingScriptFcReturnsToMappingTwoAndGlobalPhaseWait() {
        var platform = new FbzDisappearingPlatformObjectInstance(spawn(0x79, 0x79));
        platform.setServices(new PlayersServices(null, List.of()));
        int activation = (-platform.phaseOffset()) & platform.phaseMask();
        platform.update(activation, null);
        for (int i = 1; i <= 133; i++) {
            platform.update(activation + i, null);
        }

        assertEquals(2, platform.mappingFrame());
        assertFalse(platform.isSolidFor(null));
        platform.update(activation + 134, null);
        assertEquals(2, platform.mappingFrame());
    }

    @Test
    void screwDoorReadsLevelTriggerOnceMovesOnePixelCounterAndPlaysSingleOpenSfx() {
        var services = new RecordingServices();
        var door = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x20));
        door.setServices(services);
        Sonic3kLevelTriggerManager.setAll(0);

        door.update(0, null);
        assertEquals(0x1000, door.getX());
        door.update(1, null);
        assertEquals(0x1001, door.getX());
        assertEquals(1, services.opens);
        for (int i = 2; i < 128; i++) {
            door.update(i, null);
        }
        assertEquals(1, services.opens);
    }

    @Test
    void bit6ScrewDoorUsesTheFullSpeedHorizontalBranch() {
        var door = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x43));
        door.setServices(new RecordingServices());
        Sonic3kLevelTriggerManager.setAll(3);

        int initialY = door.getY();
        door.update(0, null);
        assertEquals(0x1001, door.getX(), "bit 6 reaches loc_3BCDE without the bit-5 ASR");
        assertEquals(initialY, door.getY(), "bit 6 moves X, not the vertical fallback axis");
        for (int frame = 1; frame < 128; frame++) door.update(frame, null);
        assertEquals(0x1080, door.getX());
        assertEquals(initialY, door.getY());
    }

    @Test
    void negativeHalfSpeedScrewDoorUsesSignedWordArithmeticShift() {
        var vertical = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x12));
        vertical.setServices(new RecordingServices());
        Sonic3kLevelTriggerManager.setAll(2);

        vertical.update(0, null);

        assertEquals(0x7FF, vertical.getY(),
                "neg.w followed by asr.w #1 rounds an odd negative displacement down");
        vertical.update(1, null);
        assertEquals(0x7FF, vertical.getY(),
                "the following even negative displacement keeps the same half-speed pixel");

        var horizontal = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x30));
        horizontal.setServices(new RecordingServices());
        Sonic3kLevelTriggerManager.setAll(0);

        horizontal.update(0, null);

        assertEquals(0x0FFF, horizontal.getX(),
                "bit-5 half-speed horizontal motion uses the same signed ASR semantics");
        horizontal.update(1, null);
        assertEquals(0x0FFF, horizontal.getX(),
                "horizontal even negative displacement remains unchanged by the ASR correction");
    }

    @Test
    void horizontalScrewDoorPassesCurrentXSoRiderGetsNoCarryDelta() {
        var door = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x43));

        assertFalse(door.carriesRiderOnHorizontalMove(null),
                "loc_3BCF2 loads current x_pos into d4 immediately before SolidObjectFull, "
                        + "so MvSonicOnPtfm sees a zero horizontal delta");
    }

    @Test
    void legacyDoorUsesRespawnBitZeroAndProximityOpenIsSilent() {
        ObjectManager manager = mock(ObjectManager.class);
        var services = new LegacyServices(manager);
        when(manager.isSpawnStateBitSet(any(), eq(0))).thenReturn(true);
        var restored = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x89));
        restored.setServices(services);

        restored.update(0, null);

        assertEquals(0x840, restored.getY());
        assertEquals(0, services.opens);

        when(manager.isSpawnStateBitSet(any(), eq(0))).thenReturn(false);
        TestSprite player = new TestSprite();
        player.setCentreX((short) 0x1040);
        player.setCentreY((short) 0x830);
        var fresh = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x89));
        fresh.setServices(services);
        fresh.update(0, player);

        verify(manager).setSpawnStateBit(any(), eq(0));
        assertEquals(0, services.opens);
    }

    @Test
    void legacyRespawnBitAlwaysRestoresPlusFortyYEvenWhenSubtypeDirectionBitIsNegative() {
        ObjectManager manager = mock(ObjectManager.class);
        when(manager.isSpawnStateBitSet(any(), eq(0))).thenReturn(true);
        var door = new FbzScrewDoorObjectInstance(spawn(0x7A, 0x99));
        door.setServices(new LegacyServices(manager));

        door.update(0, null);

        assertEquals(0x840, door.getY());
    }

    @Test
    void legacyRespawnBitBypassesHorizontalSubtypeMotionAndRestoresExactXY() {
        ObjectManager manager = mock(ObjectManager.class);
        when(manager.isSpawnStateBitSet(any(), eq(0))).thenReturn(true);
        var door = new FbzScrewDoorObjectInstance(spawn(0x7A, 0xA9));
        door.setServices(new LegacyServices(manager));

        door.update(0, null);

        assertEquals(0x1000, door.getX());
        assertEquals(0x840, door.getY());
    }

    private static class RecordingServices extends TestObjectServices {
        int opens;

        @Override
        public void playSfx(int id) {
            if (id == Sonic3kSfx.DOOR_OPEN.id) {
                opens++;
            }
        }
    }

    private static final class LegacyServices extends RecordingServices {
        private final ObjectManager manager;

        private LegacyServices(ObjectManager manager) {
            this.manager = manager;
        }

        @Override
        public ObjectManager objectManager() {
            return manager;
        }
    }

    private static final class PlayersServices extends TestObjectServices {
        private final ObjectPlayerQuery query;

        private PlayersServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return query;
        }
    }

    private static final class TestSprite extends AbstractPlayableSprite {
        private TestSprite() {
            super("sonic", (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        public void defineSpeeds() {
        }

        @Override
        protected void createSensorLines() {
        }
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 1);
    }
}
