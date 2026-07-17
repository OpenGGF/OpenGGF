package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rules.GameRules;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.AbstractObjectInstance;
import java.lang.reflect.Field;
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

    @Test void rotatingMembersKeepSolidStateInTheirLiveNativeSlot() {
        var rotating = new FbzRotatingPlatformObjectInstance(spawn(0x77, 0x0C));
        assertTrue(rotating.usesInstanceSolidStateLatchKey(),
                "Obj77 rewrites its dynamic spawn while ROM keeps standing/pushing bits in each live SST slot");
    }

    @Test void rotatingMovementDoesNotOrphanThePriorPushLatch() throws Exception {
        final int cameraX = 0x0F60;
        Camera camera = new Camera() {
            @Override public short getX() { return (short) cameraX; }
            @Override public short getY() { return (short) 0x0780; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        TestObjectServices services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(),
                0, null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(cameraX);
        FbzRotatingPlatformObjectInstance rotating = manager.createDynamicObject(
                () -> new FbzRotatingPlatformObjectInstance(spawn(0x77, 0x0C)));
        // Isolate the first native member: child allocation is independently
        // covered above, while this regression targets one SST status owner.
        Field childrenSpawned = FbzRotatingPlatformObjectInstance.class
                .getDeclaredField("childrenSpawned");
        childrenSpawned.setAccessible(true);
        childrenSpawned.setBoolean(rotating, true);

        SolidProbePlayer player = new SolidProbePlayer();
        player.setCentreX((short) (0x1044 - 0x17));
        player.setCentreY((short) 0x0800);
        player.setAir(false);
        manager.update(cameraX, player, List.of(), 0, false, true, false);
        assertTrue(player.getPushing());
        assertTrue(manager.hasObjectPushingBit(player));

        // The next Obj77 callback changes angle and updateDynamicSpawn replaces
        // its coordinate-bearing ObjectSpawn. loc_1E0A2 must still find and
        // clear the bit owned by this same live SST slot.
        player.setCentreX((short) 0x0F00);
        manager.update(cameraX, player, List.of(), 1, false, true, false);
        assertFalse(player.getPushing());
        assertFalse(manager.hasObjectPushingBit(player));
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

    @Test void snakeSolidObjectFullConsumesAnAirborneStaleStandingBitBeforeReseating() {
        var snake = new FbzSnakePlatformObjectInstance(spawn(0x75, 3));
        assertTrue(snake.airborneStaleStandingBitReturnsNoContact(null),
                "SolidObjectFull loc_1DC98 must return after clearing an airborne rider");
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

    private static final class SolidProbePlayer extends AbstractPlayableSprite {
        private SolidProbePlayer() {
            super("FBZ_ROTATING_PLATFORM_SOLID_TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
        @Override protected void defineSpeeds() {
            runAccel=0;runDecel=0;friction=0;max=0;jump=0;angle=0;
            slopeRunning=0;slopeRollingDown=0;slopeRollingUp=0;rollDecel=0;
            minStartRollSpeed=0;minRollSpeed=0;maxRoll=0;rollHeight=28;runHeight=38;
            standXRadius=9;standYRadius=19;rollXRadius=7;rollYRadius=14;
        }
        @Override protected void createSensorLines() {
            groundSensors=new Sensor[0];ceilingSensors=new Sensor[0];pushSensors=new Sensor[0];
        }
        @Override public void draw() { }
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 3);
    }
}
