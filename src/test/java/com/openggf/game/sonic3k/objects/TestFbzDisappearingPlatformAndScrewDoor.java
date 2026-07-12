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
            assertEquals(subtype & 0xF, door.triggerIndex());
            assertEquals((subtype & 0x20) != 0, door.horizontalMode());
            assertEquals((subtype & 0x10) != 0, door.negativeDirection());
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
