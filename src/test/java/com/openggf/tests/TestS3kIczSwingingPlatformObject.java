package com.openggf.tests;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.IczSwingingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;

class TestS3kIczSwingingPlatformObject {

    // Clear any gameplay session leaked by a prior test in this fork so the registry
    // resolves the S3KL zone set (not a leaked SKL zone). Parallel-suite flake fix.
    @BeforeEach
    void clearLeakedGameplaySession() {
        TestEnvironment.resetAll();
    }

    @Test
    void registryCreatesIczSwingingPlatformInstance() {
        ObjectInstance instance = new IczRegistry().create(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));

        assertInstanceOf(IczSwingingPlatformObjectInstance.class, instance);
    }

    @Test
    void objectExposesRomSolidPieces() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));

        assertInstanceOf(MultiPieceSolidProvider.class, platform);
        assertEquals(2, platform.getPieceCount());

        SolidObjectParams lower = platform.getPieceParams(0);
        assertEquals(0x1200, platform.getPieceX(0));
        assertEquals(0x0708, platform.getPieceY(0));
        assertEquals(0x2B, lower.halfWidth());
        assertEquals(0x20, platform.getPieceLandingHalfWidth(0));
        assertEquals(8, lower.airHalfHeight());
        assertEquals(8, lower.groundHalfHeight());

        SolidObjectParams upper = platform.getPieceParams(1);
        assertEquals(0x121C, platform.getPieceX(1));
        assertEquals(0x06F8, platform.getPieceY(1));
        assertEquals(0x0F, upper.halfWidth());
        assertEquals(0x30, platform.getPieceLandingHalfWidth(1));
        assertEquals(8, upper.airHalfHeight());
        assertEquals(8, upper.groundHalfHeight());

        assertEquals(0x1200, platform.getX());
        assertEquals(0x0700, platform.getY());
        assertEquals(1, platform.getPriorityBucket());
        assertTrue(platform.usesPieceScopedStandingBits());
        assertTrue(platform.airborneStaleStandingBitReturnsNoContact(mock(PlayableEntity.class)));
        assertTrue(platform.sidekickCpuStalePushGraceKeepsFollowSteeringWhileRiding(
                mock(PlayableEntity.class)));
        assertFalse(platform.usesCollisionHalfWidthForTopLanding(),
                "SolidObjectFull re-reads each child slot's width_pixels after the broad overlap");
        assertTrue(platform.usesInclusiveRightEdge(),
                "SolidObjectFull's broad child overlap rejects only values above the right edge");
    }

    @Test
    void hFlipMirrorsAdjustedUpperSolidPiece() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 1, false, 0));

        assertEquals(0x11E4, platform.getPieceX(1));
        assertEquals(0x06F8, platform.getPieceY(1));
    }

    @Test
    void standingHighVelocityTriggerStartsSwingAndClampsPlayerVelocity() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getXSpeed()).thenReturn((short) 0x1000);

        platform.onPieceContact(0, player, standingContact(), 0);
        assertEquals(0x2B, platform.getPieceLandingHalfWidth(0),
                "the armed child keeps its broad continued-contact window while its standing bit owns the rider");
        platform.update(1, player);

        assertEquals(0x1200, platform.getX(),
                "ROM loc_8AD20 only arms the swing on the trigger frame; circular motion starts next update");
        assertEquals(0x0700, platform.getY());
        platform.update(2, player);

        assertTrue(platform.getX() > 0x1200);
        verify(player).setXSpeed((short) 0x0800);
        verify(player).setGSpeed((short) 0x0800);
    }

    @Test
    void continuedStandingTriggerHalvesSpeedBeforeSwingClamp() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getXSpeed()).thenReturn((short) 0x08F8);

        platform.onPieceContact(0, player, standingContact(), 0, true);

        verify(player).setXSpeed((short) 0x047C);
        verify(player).setGSpeed((short) 0x047C);
    }

    @Test
    void wrongDirectionDoesNotStartSwing() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getXSpeed()).thenReturn((short) -0x0400);

        platform.onPieceContact(0, player, standingContact(), 0);
        platform.update(1, player);

        assertEquals(0x1200, platform.getX());
        assertEquals(0x0700, platform.getY());
        verify(player, never()).setXSpeed(org.mockito.ArgumentMatchers.anyShort());
        verify(player, never()).setGSpeed(org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    void swingReturnsSmoothlyAfterVelocityChangesSign() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0));
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getXSpeed()).thenReturn((short) 0x0800);

        platform.onPieceContact(0, player, standingContact(), 0);
        for (int frame = 1; frame <= 65; frame++) {
            platform.update(frame, player);
        }

        assertTrue(platform.getX() > 0x1200,
                "platform should still be returning from the far swing arc, not snapped to spawn");
    }

    @Test
    void releasedPlatformPreservesCircularFixedPointFraction() {
        IczSwingingPlatformObjectInstance platform = new IczSwingingPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 1, 0, false, 0));
        platform.setServices(new StubObjectServices());
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getXSpeed()).thenReturn((short) 0x0800);

        platform.onPieceContact(0, player, standingContact(), 0);
        for (int frame = 1; frame <= 100 && platform.getX() <= 0x1280; frame++) {
            platform.update(frame, player);
        }

        assertEquals(0x1283, platform.getX());
        assertEquals(0x066A, platform.getY());
        assertEquals(0, platform.getXSubpixelForTesting());
        assertEquals(0x8000, platform.getYSubpixelForTesting(),
                "MoveSprite must retain the half-pixel produced by MoveSprite_CircularSimple");
        assertEquals(0x1283, platform.getOutOfRangeReferenceX(),
                "Sprite_CheckDeleteTouch2 checks the moving x_pos, including after chain release");
    }

    @Test
    void releaseUsesUnsignedNativeVelocityWordInBothFacingBranches() throws Exception {
        for (boolean flip : new boolean[] {true, false}) {
            RecordingServices services = new RecordingServices();
            var platform = new IczSwingingPlatformObjectInstance(new ObjectSpawn(
                    0x1200, 0x700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 1, flip ? 1 : 0, false, 0));
            platform.setServices(services);
            PlayableEntity rider = mock(PlayableEntity.class);
            when(rider.getXSpeed()).thenReturn((short) (flip ? -0x800 : 0x800));
            platform.onPieceContact(0, rider, standingContact(), 0);
            platform.update(1, rider);
            setInt(platform, "angleAccumulator", flip ? -0x4700 : 0x4900);
            setInt(platform, "swingVelocity", flip ? -0x180 : 0);
            platform.update(2, rider);
            assertEquals(8, services.debris.size(),
                    "cmp.w #$200/bhs accepts the negative velocity word in either native facing branch");
        }
    }

    @Test
    void neighboringAngleOrPositiveVelocityDoesNotReleaseEarly() throws Exception {
        for (boolean flip : new boolean[] {true, false}) {
            RecordingServices services = new RecordingServices();
            var platform = new IczSwingingPlatformObjectInstance(new ObjectSpawn(
                    0x1200, 0x700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 1, flip ? 1 : 0, false, 0));
            platform.setServices(services);
            PlayableEntity rider = mock(PlayableEntity.class);
            when(rider.getXSpeed()).thenReturn((short) (flip ? -0x800 : 0x800));
            platform.onPieceContact(0, rider, standingContact(), 0);
            platform.update(1, rider);
            setInt(platform, "angleAccumulator", flip ? -0x4600 : 0x4700);
            setInt(platform, "swingVelocity", flip ? -0x180 : 0x180);
            platform.update(2, rider);
            assertTrue(services.debris.isEmpty(), flip
                    ? "bhi skips the release before the negative angle threshold"
                    : "positive velocity below $200 still fails the unsigned bhs test");
        }
    }

    @Test
    void releasingChainReplacesSuspendedLinksWithIndependentFallingDebris() {
        for (boolean flip : new boolean[] {false, true}) {
            PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
            var platform = new TestableIczSwingingPlatform(
                    new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM,
                            1, flip ? 1 : 0, false, 0), renderer);
            RecordingServices services = new RecordingServices();
            platform.setServices(services);
            PlayableEntity player = mock(PlayableEntity.class);
            when(player.getXSpeed()).thenReturn((short) (flip ? -0x0800 : 0x0800));
            platform.onPieceContact(0, player, standingContact(), 0);
            for (int frame = 1; frame < 50 && services.debris.isEmpty(); frame++) {
                platform.update(frame, player);
            }
            assertEquals(8, services.debris.size(), "ChildObjDat_8B178 allocates eight links once");
            platform.appendRenderCommands(new ArrayList<>());
            verify(renderer).drawFrameIndex(8, 0x1200, 0x0680, flip, false, 2);
            clearInvocations(renderer);
            platform.update(50, player);
            platform.appendRenderCommands(new ArrayList<>());
            verify(renderer, never()).drawFrameIndex(
                    org.mockito.ArgumentMatchers.eq(8), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
            AbstractObjectInstance link = (AbstractObjectInstance) services.debris.get(7);
            int oldX = link.getX();
            int oldY = link.getY();
            assertEquals(5, link.getPriorityBucket(), "ObjDat3_8B14C uses priority word $280");
            assertEquals(0, link.getSpawn().renderFlags(), "CreateChild6_Simple does not copy render flips");
            AbstractObjectInstance.updateCameraBounds(0x1100, 0x0600, 320, 224, 0);
            link.update(50, null);
            assertEquals(oldX, link.getX(), "loc_8AF3E initializes and returns before MoveChkDel");
            link.update(51, null);
            assertEquals(oldX + (flip ? -4 : 4), link.getX());
            assertEquals(oldY - 4, link.getY(), "word_8AF86's eighth velocity is -$400 vertically");
            clearInvocations(renderer);
            platform.appendRenderCommands(new ArrayList<>());
            verify(renderer, never()).drawFrameIndex(
                    org.mockito.ArgumentMatchers.eq(8), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
            assertEquals(8, services.debris.size(), "detachment must not create links again");
        }
    }

    @Test
    void renderUsesRomPaletteLinesForParentAndChainChildren() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        TestableIczSwingingPlatform platform = new TestableIczSwingingPlatform(
                new ObjectSpawn(0x1200, 0x0700, Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, 0, 0, false, 0),
                renderer);

        platform.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(8, 0x1200, 0x0680, false, false, 2);
        verify(renderer).drawFrameIndex(7, 0x1200, 0x0700, false, false, 1);
        verify(renderer).drawFrameIndex(0x27, 0x1200, 0x0708, false, false, 1);
        verify(renderer).drawFrameIndex(0x27, 0x121C, 0x06F8, false, false, 1);
    }

    @Test
    void profileMarksIczSwingingPlatformImplementedForS3klOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();

        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.ICZ_SWINGING_PLATFORM));
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static final class TestableIczSwingingPlatform extends IczSwingingPlatformObjectInstance {
        private final PatternSpriteRenderer renderer;

        private TestableIczSwingingPlatform(ObjectSpawn spawn, PatternSpriteRenderer renderer) {
            super(spawn);
            this.renderer = renderer;
        }

        @Override
        protected PatternSpriteRenderer getRenderer(String artKey) {
            assertEquals(Sonic3kObjectArtKeys.ICZ_PLATFORMS, artKey);
            return renderer;
        }
    }

    private static final class IczRegistry extends Sonic3kObjectRegistry {
        @Override
        protected com.openggf.level.Level currentLevel() {
            return null;
        }

        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_ICZ;
        }
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<ObjectInstance> debris = new ArrayList<>();
        private final ObjectManager objectManager = mock(ObjectManager.class);

        private RecordingServices() {
            doAnswer(invocation -> {
                ObjectInstance child = invocation.getArgument(0);
                ((AbstractObjectInstance) child).setServices(this);
                debris.add(child);
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(any());
        }

        @Override public ObjectManager objectManager() { return objectManager; }
    }
}
