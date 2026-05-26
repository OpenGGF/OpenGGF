package com.openggf.tests;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.IczStalagtiteObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kIczStalagtiteObject {

    @Test
    void registryCreatesIczStalagtiteInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x3200, 0x05C0, Sonic3kObjectIds.ICZ_STALAGTITE, 0, 0, false, 0));

        assertInstanceOf(IczStalagtiteObjectInstance.class, instance);
    }

    @Test
    void objectUsesRomSolidDimensionsAndMappingFrame() {
        IczStalagtiteObjectInstance stalagtite = createStalagtite(0x3200, 0x05C0);

        SolidObjectParams params = stalagtite.getSolidParams();
        assertEquals(0x1B, params.halfWidth());
        assertEquals(0x20, params.airHalfHeight());
        assertEquals(0x20, params.groundHalfHeight());
        assertEquals(7, stalagtite.getMappingFrameForTesting());
        assertEquals(Sonic3kObjectArtKeys.ICZ_WALL_AND_COLUMN, stalagtite.getArtKeyForTesting());
        assertEquals(0, stalagtite.getCollisionFlags());
        assertEquals(0, stalagtite.getMultiTouchRegions().length);
        assertTrue(stalagtite.isSolidFor(null));
    }

    @Test
    void nearestPlayerInsideRomRangeStartsSixteenFrameShakeThenFall() {
        IczStalagtiteObjectInstance stalagtite = createStalagtite(0x3200, 0x05C0);
        PlayableEntity player = playerAt(0x326F);

        stalagtite.update(10, player);

        assertEquals("SHAKING", stalagtite.getPhaseNameForTesting());
        assertEquals(15, stalagtite.getTimerForTesting());
        assertEquals(0, stalagtite.getCollisionFlags());
        assertFalse(stalagtite.isSolidFor(player));

        int startX = stalagtite.getX();
        for (int i = 0; i < 15; i++) {
            stalagtite.update(11 + i, player);
            assertEquals("SHAKING", stalagtite.getPhaseNameForTesting());
        }

        stalagtite.update(26, player);

        assertEquals("FALLING", stalagtite.getPhaseNameForTesting());
        assertEquals(0x82, stalagtite.getCollisionFlags());
        assertEquals(startX, stalagtite.getX(),
                "ROM alternates +2/-2 x_pos during the 16 shake frames, netting back to the trigger X");
    }

    @Test
    void highBitObjectXUsesRomSignedWordDistanceForTriggerRange() {
        IczStalagtiteObjectInstance stalagtite = createStalagtite(0xFFE0, 0x05C0);
        PlayableEntity player = playerAt(0x0020);

        stalagtite.update(10, player);

        assertEquals("SHAKING", stalagtite.getPhaseNameForTesting(),
                "Find_SonicTails subtracts 16-bit x_pos words, so $FFE0-$0020 becomes a $40 trigger distance");
    }

    @Test
    void fallingUsesMoveSpriteGravityOrderAndHurtsWithSingleTouchRegion() {
        IczStalagtiteObjectInstance stalagtite = createStalagtite(0x3200, 0x05C0);
        stalagtite.forceFallingForTesting();

        stalagtite.update(1, null);

        assertEquals(0x05C0, stalagtite.getY(), "MoveSprite moves using old y_vel before applying gravity");
        assertEquals(0x38, stalagtite.getYVelocityForTesting());
        assertEquals(0x82, stalagtite.getCollisionFlags());
        assertEquals(0x3200, stalagtite.getMultiTouchRegions()[0].x());
        assertEquals(0x05C0, stalagtite.getMultiTouchRegions()[0].y());
    }

    @Test
    void declaresExplicitMultiRegionTouchProfileWhileRegionsRemainPhaseDependent() throws Exception {
        IczStalagtiteObjectInstance stalagtite = createStalagtite(0x3200, 0x05C0);

        assertEquals(new TouchResponseProfile(
                TouchCategoryDecodeMode.NORMAL,
                false,
                true,
                true,
                TouchShieldDeflectCapability.NONE,
                0,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY),
                stalagtite.getTouchResponseProfile());
        assertEquals(0, stalagtite.getMultiTouchRegions().length);

        stalagtite.forceFallingForTesting();

        assertEquals(1, stalagtite.getMultiTouchRegions().length);
        assertEquals(0x82, stalagtite.getMultiTouchRegions()[0].collisionFlags());
        assertEquals(stalagtite.getTouchResponseProfile(), stalagtite.getTouchResponseProfile(false));
        assertEquals(TouchResponseProfile.class,
                IczStalagtiteObjectInstance.class.getDeclaredMethod("getTouchResponseProfile").getReturnType());
    }

    @Test
    void ceilingImpactClearsTouchSpawnsTwelveDebrisAndPlaysFloorThump() {
        RecordingServices services = new RecordingServices();
        TestableStalagtite stalagtite = new TestableStalagtite(
                new ObjectSpawn(0x3200, 0x05C0, Sonic3kObjectIds.ICZ_STALAGTITE, 0, 0, false, 0));
        stalagtite.setServices(services);
        stalagtite.forceFallingForTesting();
        stalagtite.ceilingDistance = -1;

        stalagtite.update(1, null);

        assertEquals("LANDED", stalagtite.getPhaseNameForTesting());
        assertEquals(0, stalagtite.getCollisionFlags());
        assertEquals(List.of(Sonic3kSfx.FLOOR_THUMP.id), services.playedSfx);
        assertEquals(12, services.children.size());
    }

    @Test
    void profileMarksIczStalagtiteImplementedOnlyForS3kl() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();

        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.ICZ_STALAGTITE));
    }

    private static IczStalagtiteObjectInstance createStalagtite(int x, int y) {
        return new IczStalagtiteObjectInstance(
                new ObjectSpawn(x, y, Sonic3kObjectIds.ICZ_STALAGTITE, 0, 0, false, 0));
    }

    private static PlayableEntity playerAt(int x) {
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) x);
        return player;
    }

    private static final class TestableStalagtite extends IczStalagtiteObjectInstance {
        private int ceilingDistance = 1;

        private TestableStalagtite(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        protected int checkCeilingDistanceForTesting() {
            return ceilingDistance;
        }
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();
        private final List<AbstractObjectInstance> children = new ArrayList<>();
        private final ObjectManager objectManager = mock(ObjectManager.class);

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }

        private RecordingServices() {
            org.mockito.Mockito.doAnswer(invocation -> {
                children.add(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(org.mockito.ArgumentMatchers.any());
        }
    }
}
