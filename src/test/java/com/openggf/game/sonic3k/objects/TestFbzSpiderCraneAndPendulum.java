package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzSpiderCraneAndPendulum {
    @Test
    void spiderTravelAndPendulumGraphDecodeExactly() {
        FbzSpiderCraneObjectInstance spider = new FbzSpiderCraneObjectInstance(spawn(0xE5, 0x2C));
        assertEquals(0xB0, spider.horizontalTravel());
        FbzMagneticPendulumObjectInstance normal = new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        FbzMagneticPendulumObjectInstance horizontal = new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0x80));
        assertEquals(3, normal.totalGraphSlots());
        assertEquals(-0x80, normal.initialAngle());
        assertEquals(-0x40, horizontal.initialAngle());
        assertFalse(normal.consumesMagneticPolarity());
        assertEquals(ObjectPlayerParticipationPolicy.MAIN_ONLY_NATIVE, normal.participationPolicy());
    }

    @Test
    void pendulumPreservesEightBitAngleFractionAndUsesExactEndpointRadius() throws Exception {
        FbzMagneticPendulumObjectInstance pivot = new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery())
                .thenReturn(new ObjectPlayerQuery(() -> null, List::of));
        pivot.setServices(services);
        setField(pivot, "graphAllocationAttempted", true);
        setField(pivot, "swinging", true);
        setField(pivot, "angularVelocity", 0x1E6);
        pivot.update(1, null);
        assertEquals(-0x7F, pivot.angleValue());
        assertEquals(0xE6, pivot.angleFraction());

        FbzMagneticPendulumObjectInstance fresh = new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        FbzMagneticPendulumEndpointObjectInstance endpoint =
                new FbzMagneticPendulumEndpointObjectInstance(spawn(0xFF, 0), fresh);
        endpoint.setServices(services);
        endpoint.update(1, null);
        assertEquals(0x1000 - 0x70, endpoint.getX());
        assertEquals(0x700, endpoint.getY());
    }

    @Test
    void pendulumCaptureUsesExactAsymmetricStrictP1Windows() {
        var player = org.mockito.Mockito.mock(com.openggf.game.PlayableEntity.class);
        org.mockito.Mockito.when(player.getYSpeed()).thenReturn((short) -0x400);
        org.mockito.Mockito.when(player.getCentreY()).thenReturn((short) 0x700);
        org.mockito.Mockito.when(player.getCentreX()).thenReturn((short) (0x1000 - 0x22));
        FbzMagneticPendulumObjectInstance vertical =
                new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        vertical.tryCapture(player, 0x1000, 0x700);
        assertTrue(vertical.isSwinging());

        var outside = org.mockito.Mockito.mock(com.openggf.game.PlayableEntity.class);
        org.mockito.Mockito.when(outside.getYSpeed()).thenReturn((short) -0x400);
        org.mockito.Mockito.when(outside.getCentreY()).thenReturn((short) 0x700);
        org.mockito.Mockito.when(outside.getCentreX()).thenReturn((short) (0x1000 - 0x21));
        FbzMagneticPendulumObjectInstance rejected =
                new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        rejected.tryCapture(outside, 0x1000, 0x700);
        assertFalse(rejected.isSwinging(), "nonrolling near edge is -$22, not a symmetric box");
    }

    @Test
    void pendulumAttachedOffsetsUseTheExactSignedFixedPointShiftSequence() {
        assertEquals(148, FbzMagneticPendulumObjectInstance.attachedOffset(0x100, false));
        assertEquals(-148, FbzMagneticPendulumObjectInstance.attachedOffset(-0x100, false));
        assertEquals(143, FbzMagneticPendulumObjectInstance.attachedOffset(0x100, true));
        assertEquals(-143, FbzMagneticPendulumObjectInstance.attachedOffset(-0x100, true));

        assertEquals(104, FbzMagneticPendulumObjectInstance.attachedOffset(181, false));
        assertEquals(-105, FbzMagneticPendulumObjectInstance.attachedOffset(-181, false));
        assertEquals(101, FbzMagneticPendulumObjectInstance.attachedOffset(181, true));
        assertEquals(-102, FbzMagneticPendulumObjectInstance.attachedOffset(-181, true));
    }

    @Test
    void spiderRunsCaptureRetractTravelReleaseAgainstMainOnlyWithThreeSidekicks() {
        AbstractPlayableSprite main = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        org.mockito.Mockito.when(main.getCentreX()).thenReturn((short) 0x1000);
        org.mockito.Mockito.when(main.getCentreY()).thenReturn((short) 0x745);
        AbstractPlayableSprite first = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite second = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite third = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        ObjectManager manager = org.mockito.Mockito.mock(ObjectManager.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.objectManager()).thenReturn(manager);
        org.mockito.Mockito.when(services.playerQuery()).thenReturn(
                new ObjectPlayerQuery(() -> main, () -> List.of(first, second, third)));
        FbzSpiderCraneObjectInstance crane = new FbzSpiderCraneObjectInstance(spawn(0xE5, 1));
        crane.setServices(services);

        for (int frame = 0; frame < 1000 && !"INERT".equals(crane.stateName()); frame++) {
            crane.update(frame, null);
        }
        assertEquals("INERT", crane.stateName());
        assertNotNull(crane.companionMember());
        org.mockito.Mockito.verify(main).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl());
        org.mockito.Mockito.verify(main).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState.none());
        org.mockito.Mockito.verify(main).setAir(true);
        for (AbstractPlayableSprite sidekick : List.of(first, second, third)) {
            org.mockito.Mockito.verify(sidekick, org.mockito.Mockito.never())
                    .applyObjectControlState(org.mockito.Mockito.any());
            org.mockito.Mockito.verify(sidekick, org.mockito.Mockito.never())
                    .setCentreXPreserveSubpixel(org.mockito.Mockito.anyShort());
        }
    }

    @Test
    void pendulumCaptureAndSwingIgnoreThreeConfiguredSidekicks() throws Exception {
        AbstractPlayableSprite main = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        org.mockito.Mockito.when(main.getYSpeed()).thenReturn((short) -0x400);
        org.mockito.Mockito.when(main.getCentreY()).thenReturn((short) 0x700);
        org.mockito.Mockito.when(main.getCentreX()).thenReturn((short) 0xF6E);
        AbstractPlayableSprite first = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite second = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite third = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery()).thenReturn(
                new ObjectPlayerQuery(() -> main, () -> List.of(first, second, third)));
        FbzMagneticPendulumObjectInstance pivot =
                new FbzMagneticPendulumObjectInstance(spawn(0xFF, 0));
        pivot.setServices(services);
        setField(pivot, "graphAllocationAttempted", true);

        pivot.tryCapture(main, 0xF90, 0x700);
        assertTrue(pivot.isSwinging());
        pivot.update(1, null);
        org.mockito.Mockito.verify(main).applyObjectControlState(
                com.openggf.sprites.playable.ObjectControlState
                        .nativeBits0To6CpuAllowedMovementSuppressed());
        for (AbstractPlayableSprite sidekick : List.of(first, second, third)) {
            org.mockito.Mockito.verifyNoInteractions(sidekick);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x700, id, subtype, 0, false, 1);
    }
}
