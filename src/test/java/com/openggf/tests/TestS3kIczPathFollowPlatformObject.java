package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.IczPathFollowPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3kIczPathFollowPlatformObject {

    @Test
    void registryCreatesIczPathFollowPlatformInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_PATH_FOLLOW_PLATFORM, 0, 0, false, 0));

        assertInstanceOf(IczPathFollowPlatformObjectInstance.class, instance);
    }

    @Test
    void objectUsesRomSolidDimensionsAndArt() {
        IczPathFollowPlatformObjectInstance platform = create(0);

        SolidObjectParams params = platform.getSolidParams();
        assertEquals(0x2B, params.halfWidth());
        assertEquals(0x14, params.airHalfHeight());
        assertEquals(0x14, params.groundHalfHeight());
        assertEquals(0, platform.getMappingFrameForTesting());
        assertEquals(Sonic3kObjectArtKeys.ICZ_PLATFORMS, platform.getArtKeyForTesting());
        assertEquals(5, platform.getPriorityBucket());
    }

    @Test
    void subtypePairsMapToRomRoutineBytes() {
        assertEquals(0x02, create(0).getRoutineByteForTesting());
        assertEquals(0x02, create(1).getRoutineByteForTesting());
        assertEquals(0x06, create(2).getRoutineByteForTesting());
        assertEquals(0x06, create(3).getRoutineByteForTesting());
        assertEquals(0x0C, create(4).getRoutineByteForTesting());
        assertEquals(0x0C, create(5).getRoutineByteForTesting());
        assertEquals(0x0E, create(6).getRoutineByteForTesting());
        assertEquals(0x0E, create(7).getRoutineByteForTesting());
    }

    @Test
    void subtypeZeroStandTriggerJittersThenStartsFalling() {
        IczPathFollowPlatformObjectInstance platform = create(0);
        PlayableEntity player = mock(PlayableEntity.class);

        platform.onSolidContact(player, standingContact(), 0);
        platform.update(0, player);

        assertEquals(0x04, platform.getRoutineByteForTesting());
        assertEquals(0x0F, platform.getWaitTimerForTesting());

        for (int frame = 1; frame <= 16; frame++) {
            platform.update(frame, player);
        }

        assertEquals(0x0A, platform.getRoutineByteForTesting());
        platform.update(17, player);
        assertEquals(0x38, platform.getYVelocityForTesting());
    }

    @Test
    void outOfRangeReferenceUsesLiveXLikeSpriteOnScreenTest() {
        IczPathFollowPlatformObjectInstance platform = create(0);
        PlayableEntity player = mock(PlayableEntity.class);

        platform.onSolidContact(player, standingContact(), 0);
        platform.update(0, player);
        platform.update(1, player);

        assertEquals(platform.getX(), platform.getOutOfRangeReferenceX());
    }

    @Test
    void subtypeTwoPushTriggerStartsPathMotionAfterRomDelay() {
        IczPathFollowPlatformObjectInstance platform = create(2);
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x1180);

        for (int frame = 0; frame < 16; frame++) {
            platform.onSolidContact(player, pushingContact(), frame);
            platform.update(frame, player);
        }

        assertEquals(0x08, platform.getRoutineByteForTesting());
        assertEquals(-0x80, platform.getXVelocityForTesting());
    }

    @Test
    void movingPlatformRequestsFastVerticalCameraScrollWhenRidden() {
        Camera camera = mock(Camera.class);
        IczPathFollowPlatformObjectInstance platform = create(2);
        platform.setServices(new StubObjectServices() {
            @Override
            public Camera camera() {
                return camera;
            }
        });
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x1180);

        for (int frame = 0; frame < 16; frame++) {
            platform.onSolidContact(player, riddenPushingContact(), frame);
            platform.update(frame, player);
        }

        verify(camera).requestFastVerticalScroll();
    }

    @Test
    void subtypeSixSinksWhileRiddenThenReboundsToStartY() {
        IczPathFollowPlatformObjectInstance platform = create(6);
        PlayableEntity player = mock(PlayableEntity.class);

        platform.onSolidContact(player, standingContact(), 0);
        platform.update(0, player);
        assertEquals(0x10, platform.getRoutineByteForTesting());

        for (int frame = 1; frame <= 3; frame++) {
            platform.onSolidContact(player, standingContact(), frame);
            platform.update(frame, player);
        }
        assertEquals(0x0703, platform.getY());

        platform.update(4, player);
        assertEquals(0x12, platform.getRoutineByteForTesting());

        for (int frame = 5; frame < 40; frame++) {
            platform.update(frame, player);
        }

        assertEquals(0x0E, platform.getRoutineByteForTesting());
        assertEquals(0x0700, platform.getY());
    }

    @Test
    void renderUsesSharedIczPlatformLevelArt() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        TestableIczPathFollowPlatform platform = new TestableIczPathFollowPlatform(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_PATH_FOLLOW_PLATFORM, 0, 0, false, 0),
                renderer);

        platform.appendRenderCommands(new ArrayList<GLCommand>());

        verify(renderer).drawFrameIndex(0, 0x1200, 0x0700, false, false, 2);
    }

    @Test
    void profileMarksIczPathFollowPlatformImplementedForS3klOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();

        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.ICZ_PATH_FOLLOW_PLATFORM));
    }

    private static IczPathFollowPlatformObjectInstance create(int subtype) {
        return new IczPathFollowPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_PATH_FOLLOW_PLATFORM,
                        subtype, 0, false, 0));
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static SolidContact pushingContact() {
        return new SolidContact(false, true, false, false, true);
    }

    private static SolidContact riddenPushingContact() {
        return new SolidContact(true, true, false, true, true);
    }

    private static final class TestableIczPathFollowPlatform extends IczPathFollowPlatformObjectInstance {
        private final PatternSpriteRenderer renderer;

        private TestableIczPathFollowPlatform(ObjectSpawn spawn, PatternSpriteRenderer renderer) {
            super(spawn);
            this.renderer = renderer;
        }

        @Override
        protected PatternSpriteRenderer getRenderer(String artKey) {
            assertEquals(Sonic3kObjectArtKeys.ICZ_PLATFORMS, artKey);
            return renderer;
        }
    }
}
