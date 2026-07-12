package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.rewind.RewindRoundTripHarness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.AbstractObjectInstance;
import java.util.List;
import com.openggf.level.objects.TouchResponseProvider;

class TestFbzSnakeAndRotatingPlatforms {
    @Test void snakeAllocatesTheExactFourSlotDelayTrainForEveryUsedRoute() {
        for (int subtype = 0; subtype < 8; subtype++) {
            var snake = new FbzSnakePlatformObjectInstance(spawn(0x75, subtype));
            assertArrayEquals(new int[]{1,0x19,0x31,0x49}, snake.segmentDelays());
            assertEquals(4, snake.requiredSlotCount());
            assertEquals(0x140, snake.routeSpeed());
            assertEquals(5,snake.getPriorityBucket());
            assertTrue(snake.routeWordCount() >= 4, "route $" + Integer.toHexString(subtype));
        }
    }

    @Test void rotatingUsedSubtypeRowsPreserveAllocationOrderAndSpecialFirstMember() {
        var six = new FbzRotatingPlatformObjectInstance(spawn(0x77, 0x00));
        assertArrayEquals(new int[]{0x5C,0x44,0x2C,0xD4,0xBC,0xA4}, six.memberRadii());
        assertArrayEquals(new boolean[]{false,false,false,false,false,false}, six.specialMembers());
        var two = new FbzRotatingPlatformObjectInstance(spawn(0x77, 0x0C));
        assertArrayEquals(new int[]{0x44,0x2C}, two.memberRadii());
        assertArrayEquals(new boolean[]{true,false}, two.specialMembers());
        assertEquals(1, two.angleStep());
        assertEquals(5,two.getPriorityBucket());
        assertEquals(-1, new FbzRotatingPlatformObjectInstance(
                new ObjectSpawn(0x1000,0x800,0x77,0x0C,1,true,3)).angleStep());
    }

    @Test void rotatingCoordinatesUseTheGenesisSineCosineRadiusProduct() {
        assertEquals(0x1000+0x44,FbzRotatingPlatformObjectInstance.positionX(0x1000,0x44,0));
        assertEquals(0x800,FbzRotatingPlatformObjectInstance.positionY(0x800,0x44,0));
        assertEquals(0x1000,FbzRotatingPlatformObjectInstance.positionX(0x1000,0x44,0x40));
        assertEquals(0x800+0x44,FbzRotatingPlatformObjectInstance.positionY(0x800,0x44,0x40));
    }

    @Test void carrierFamiliesExposeCompletePrimitiveRewindState() {
        for (Class<?> type : new Class<?>[]{FbzWireCageObjectInstance.class,
                FbzWireCageStationaryObjectInstance.class, FbzFloatingPlatformObjectInstance.class,
                FbzChainLinkObjectInstance.class, FbzSnakePlatformObjectInstance.class,
                FbzBentPipeObjectInstance.class, FbzRotatingPlatformObjectInstance.class,
                FbzDezPlayerLauncherObjectInstance.class}) {
            assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.Passed.class,
                    RewindRoundTripHarness.probeClass(type.getName()), type.getName());
        }
    }

    @Test void firstUpdateUsesAfterCurrentAllocationInExactSnakeAndRotatorOrder() {
        ObjectManager manager=mock(ObjectManager.class);
        var snake=new FbzSnakePlatformObjectInstance(spawn(0x75,3));snake.setServices(new ManagerServices(manager));snake.update(0,null);
        ArgumentCaptor<AbstractObjectInstance> snakeChildren=ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(manager,times(3)).addDynamicObjectAfterCurrent(snakeChildren.capture());
        assertEquals(List.of(0x19,0x31,0x49),snakeChildren.getAllValues().stream().map(v->((FbzSnakePlatformObjectInstance)v).segmentDelay()).toList());

        reset(manager);
        var six=new FbzRotatingPlatformObjectInstance(spawn(0x77,0));six.setServices(new ManagerServices(manager));six.update(0,null);
        ArgumentCaptor<AbstractObjectInstance> sixChildren=ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(manager,times(5)).addDynamicObjectAfterCurrent(sixChildren.capture());
        assertEquals(List.of(0x44,0x2C,0xD4,0xBC,0xA4),sixChildren.getAllValues().stream().map(v->((FbzRotatingPlatformObjectInstance)v).memberRadius()).toList());

        reset(manager);
        var two=new FbzRotatingPlatformObjectInstance(spawn(0x77,0x0C));two.setServices(new ManagerServices(manager));two.update(0,null);
        ArgumentCaptor<AbstractObjectInstance> twoChildren=ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(manager).addDynamicObjectAfterCurrent(twoChildren.capture());
        assertEquals(0x2C,((FbzRotatingPlatformObjectInstance)twoChildren.getValue()).memberRadius());
        assertTrue(two.specialMember());assertFalse(((FbzRotatingPlatformObjectInstance)twoChildren.getValue()).specialMember());
    }

    @Test void snakeUsesOriginalAnchorCoarseCullAndRememberedRespawnLifetime() {
        var snake=new FbzSnakePlatformObjectInstance(spawn(0x75,0));
        assertTrue(snake.usesCustomOutOfRangeCheck());
        assertEquals(0x118C,snake.getOutOfRangeReferenceX());
        assertFalse(snake.isCustomOutOfRange(0x1000));
        assertTrue(snake.isCustomOutOfRange(0x2000));
    }

    @Test void subtype0CSpecialFirstMemberHasNativeMagneticTouchRoleOnly() {
        var special=new FbzRotatingPlatformObjectInstance(spawn(0x77,0x0C));
        TouchResponseProvider touch=assertInstanceOf(TouchResponseProvider.class,special);
        assertEquals(0x86,touch.getCollisionFlags());assertEquals(0,touch.getCollisionProperty());
        assertEquals(0,special.renderFrameIndex(),"filtered native mapping {1} is reindexed to sheet frame 0");
        var normal=new FbzRotatingPlatformObjectInstance(spawn(0x77,0x00));
        TouchResponseProvider normalTouch=assertInstanceOf(TouchResponseProvider.class,normal);
        assertEquals(0,normalTouch.getCollisionFlags());
    }

    @Test void snakeAllocationFailureDoesNotConsumeTheNextDelayValue() {
        ObjectManager manager=mock(ObjectManager.class);final int[] calls={0};
        doAnswer(inv->{if(calls[0]++==0)((AbstractObjectInstance)inv.getArgument(0)).setDestroyed(true);return null;}).when(manager).addDynamicObjectAfterCurrent(any());
        var snake=new FbzSnakePlatformObjectInstance(spawn(0x75,0));snake.setServices(new ManagerServices(manager));snake.update(0,null);
        ArgumentCaptor<AbstractObjectInstance> cap=ArgumentCaptor.forClass(AbstractObjectInstance.class);verify(manager,times(3)).addDynamicObjectAfterCurrent(cap.capture());
        assertEquals(List.of(0x19,0x19,0x31),cap.getAllValues().stream().map(v->((FbzSnakePlatformObjectInstance)v).segmentDelay()).toList());
    }

    @Test void routeZeroTeleportsToFirstPointWaitsSixtyAndMovesOnRestartTick() {
        ObjectManager manager=mock(ObjectManager.class);var snake=new FbzSnakePlatformObjectInstance(spawn(0x75,0));snake.setServices(new ManagerServices(manager));
        snake.update(0,null);assertEquals(0x118A,snake.getX(),"timer 1 decrements to zero and falls through MoveSprite2 on the first object update");
        for(int frame=1;frame<=123;frame++)snake.update(frame,null);
        assertEquals(0x118C,snake.getX());assertEquals(0x770,snake.getY());assertEquals(0x3C,snake.routeWait());
        for(int frame=124;frame<=182;frame++)snake.update(frame,null);assertEquals(0x118C,snake.getX());assertEquals(1,snake.routeWait());
        snake.update(183,null);assertEquals(0,snake.routeWait());assertEquals(0x118A,snake.getX());assertEquals(-0x140,snake.xVelocity());
    }

    @Test void initialObjWaitIsNotSolidButSentinelWaitRemainsSolid() {
        ObjectManager manager=mock(ObjectManager.class);com.openggf.camera.Camera camera=mock(com.openggf.camera.Camera.class);when(camera.getX()).thenReturn((short)0x1000);var snake=new FbzSnakePlatformObjectInstance(spawn(0x75,0));ManagerServices services=new ManagerServices(manager);services.withCamera(camera);snake.setServices(services);
        assertFalse(snake.isSolidFor(null));snake.update(0,null);assertTrue(snake.isSolidFor(null));for(int frame=1;frame<=123;frame++)snake.update(frame,null);assertEquals(0x3C,snake.routeWait());assertTrue(snake.isSolidFor(null));
    }

    @Test void rotatingAllocationFailureRetriesSameRadiusAndSpecialBit() {
        ObjectManager manager=mock(ObjectManager.class);final int[] call={0};doAnswer(inv->{if(call[0]++==0)((AbstractObjectInstance)inv.getArgument(0)).setDestroyed(true);return null;}).when(manager).addDynamicObjectAfterCurrent(any());
        var rot=new FbzRotatingPlatformObjectInstance(spawn(0x77,0));rot.setServices(new ManagerServices(manager));rot.update(0,null);ArgumentCaptor<AbstractObjectInstance> cap=ArgumentCaptor.forClass(AbstractObjectInstance.class);verify(manager,times(5)).addDynamicObjectAfterCurrent(cap.capture());
        assertEquals(List.of(0x44,0x44,0x2C,0xD4,0xBC),cap.getAllValues().stream().map(v->((FbzRotatingPlatformObjectInstance)v).memberRadius()).toList());
    }

    private static final class ManagerServices extends TestObjectServices {private final ObjectManager manager;ManagerServices(ObjectManager manager){this.manager=manager;}@Override public ObjectManager objectManager(){return manager;}}

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 3);
    }
}
