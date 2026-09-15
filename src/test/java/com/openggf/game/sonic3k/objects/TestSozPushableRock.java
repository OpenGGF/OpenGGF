package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestSozPushableRock {
    private MockedStatic<ObjectTerrainUtils> floor;
    private final List<Integer> sounds = new ArrayList<>();
    private final List<Integer> samples = new ArrayList<>();
    private int distance;
    private LevelManager level;
    private Camera camera;
    private Rom rom;
    private ObjectSolidExecutionContext context;
    private StubObjectServices services;

    @BeforeEach void setup() throws Exception {
        floor = mockStatic(ObjectTerrainUtils.class);
        floor.when(() -> ObjectTerrainUtils.checkFloorDist(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenAnswer(i -> {
                    assertFalse((Boolean)i.getArgument(2),"ObjCheckFloorDist2 always uses primary d5=$C");
                    samples.add(i.getArgument(3)); return new TerrainCheckResult(distance, (byte) 0, 0);
                });
        level = mock(LevelManager.class); when(level.getFrameCounter()).thenReturn(1);
        camera = mock(Camera.class); when(camera.getMaxY()).thenReturn((short) 0x1000);
        rom = mock(Rom.class);
        when(rom.read32BitAddr(anyLong())).thenReturn(0x100);
        int[] track = {105, 103, 108, 0xFFFF};
        when(rom.read16BitAddr(anyLong())).thenAnswer(i -> track[((Long)i.getArgument(0)).intValue() / 2 - 0x80]);
        context = mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(null, Map.of()));
        services = new StubObjectServices() {
            @Override public Rom rom() { return rom; }
            @Override public boolean useSecondaryTerrainCollisionPath() { return true; }
            @Override public LevelManager levelManager() { return level; }
            @Override public Camera camera() { return camera; }
            @Override public ObjectSolidExecutionContext solidExecution() { return context; }
            @Override public void playSfx(int id) { sounds.add(id); }
        };
    }
    @AfterEach void teardown() { floor.close(); }

    private TestablePlayableSprite player(int x) {
        var p = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        p.setCentreX((short) x); p.setCentreY((short) 100); p.setPushing(true);
        return p;
    }
    private SozPushableRockObjectInstance rock(int subtype, TestablePlayableSprite... players) {
        var r = new SozPushableRockObjectInstance(new ObjectSpawn(100,100,0x3E,subtype,0,false,0));
        services.withPlayerQuery(new ObjectPlayerQuery(() -> players[0],
                () -> new ArrayList<PlayableEntity>(Arrays.asList(players).subList(1,players.length))));
        r.setServices(services);
        return r;
    }
    @Test void pointerSetBindingPreservesHcz() {
        for (int zone : new int[]{1,8}) {
            var registry = new Sonic3kObjectRegistry() { @Override protected int currentRomZoneId() { return zone; } };
            var object = registry.create(new ObjectSpawn(100,100,0x3E,9,0,false,0));
            if (zone == 8) assertInstanceOf(SozPushableRockObjectInstance.class,object);
            else assertInstanceOf(HCZConveyorBeltObjectInstance.class,object);
        }
    }
    @Test void newPushContactNeedsTheSavedPlayerStatusFromBeforeCollision() {
        var p = player(80); p.setPushing(false); var r = rock(9,p);
        when(context.resolveSolidNowAll()).thenAnswer(i -> {
            r.setPlayerPushing(p,true); p.setPushing(true);
            return new SolidCheckpointBatch(r,Map.of());
        });
        r.update(0,p); assertEquals(100,r.getX());
        r.update(1,p); assertEquals(101,r.getX()); assertEquals(81,p.getCentreX());
    }
    @Test void sharedTimerMovesOnceEveryFiveEligiblePassesAndP1Wins() {
        var p = player(80); var p2 = player(120); var r = rock(9,p,p2);
        r.setPlayerPushing(p,true); r.setPlayerPushing(p2,true);
        r.update(0,p); assertEquals(101,r.getX());
        for (int i=1;i<=4;i++) { r.update(i,p); assertEquals(101,r.getX()); }
        r.update(5,p); assertEquals(102,r.getX());
        assertEquals(82,p.getCentreX()); assertEquals(120,p2.getCentreX());
        assertEquals(List.of(Sonic3kSfx.PUSH_BLOCK.id,Sonic3kSfx.PUSH_BLOCK.id),sounds);
        assertEquals(List.of(85,86),samples); // Rightward push probes trailing left edge.
    }
    @Test void retainedP2BitWorksWithoutFreshContactButPlayerResidueAloneDoesNot() {
        var p = player(80); var p2 = player(120); var r = rock(0x87,p,p2);
        r.update(0,p); assertEquals(100,r.getX());
        r.setPlayerPushing(p2,true); r.update(1,p);
        assertEquals(99,r.getX()); assertEquals(119,p2.getCentreX()); assertEquals(List.of(115),samples);
    }
    @Test void lowFiveSubtypeBitsChooseTheRomTrack() throws Exception {
        var p = player(80); var r = rock(0x87,p); r.update(0,p);
        verify(rom).read32BitAddr(Sonic3kConstants.SOZ_ROCK_RIDE_INFO_ADDR + 7 * 4L);
        verify(rom).read16BitAddr(0x100L);
    }
    @Test void floorSnapIncludesFourteenButFifteenStartsFalling() {
        var p = player(80); var r = rock(9,p); r.setPlayerPushing(p,true); distance=14;
        r.update(0,p); assertEquals(114,r.getY()); assertFalse(r.carriesRiderOnHorizontalMove(p));
        var falling=rock(9,p); falling.setPlayerPushing(p,true); distance=15;
        falling.update(0,p); assertEquals(100,falling.getY());
        falling.update(1,p); assertEquals(100,falling.getY()); // first MoveSprite uses zero velocity
        assertFalse(falling.carriesRiderOnHorizontalMove(p));
    }
    @Test void fallRideFallUsesRomTargetsAndRestoresItsTrackCursor() {
        var p=player(80); var r=rock(9,p); r.setPlayerPushing(p,true); distance=15;
        r.update(0,p);
        int pass=1;
        while (r.getY()<105 && pass<30) r.update(pass++,p);
        assertEquals(105,r.getY()); assertEquals(101,r.getX());
        var snapshot=r.captureRewindState();
        var replay=rock(9,p); replay.restoreRewindState(snapshot);
        for (int i=0;i<30;i++) {
            r.update(pass+i,p); replay.update(pass+i,p);
            assertEquals(r.getX(),replay.getX()); assertEquals(r.getY(),replay.getY());
        }
        assertEquals(108,r.getY());
        int stoppedX=r.getX(); r.update(100,p); assertEquals(stoppedX,r.getX());
        assertFalse(r.carriesRiderOnHorizontalMove(p));
    }
    @Test void fallEqualityWaitsForStrictOvershootAndSoundUsesLevelClock() throws Exception {
        when(rom.read16BitAddr(0x100L)).thenReturn(100);
        var p=player(80); var r=rock(9,p); r.setPlayerPushing(p,true); distance=15;
        r.update(999,p);
        for(int pass=1;pass<=3;pass++) {
            r.update(999,p); assertEquals(100,r.getY()); assertEquals(101,r.getX());
        }
        verify(rom,never()).read16BitAddr(0x102L);
        r.update(999,p); // accumulated fractional fall finally crosses target Y
        assertEquals(100,r.getY()); verify(rom).read16BitAddr(0x102L);
        when(level.getFrameCounter()).thenReturn(32);
        r.update(999,p); assertEquals(102,r.getX());
        assertTrue(sounds.contains(Sonic3kSfx.BLOCK_CONVEYOR.id));
        sounds.clear(); when(level.getFrameCounter()).thenReturn(33);
        r.update(32,p); assertEquals(103,r.getX());
        assertFalse(sounds.contains(Sonic3kSfx.BLOCK_CONVEYOR.id),"V-int 32 must not replace LFC 33");
    }
    @Test void leftwardTrackRetainsVelocityAcrossTheNextFall() throws Exception {
        when(rom.read16BitAddr(0x102L)).thenReturn(99);
        var p=player(80); var r=rock(9,p); r.setPlayerPushing(p,true); distance=15;
        r.update(0,p);
        for(int pass=1;pass<35;pass++) r.update(pass,p);
        assertEquals(108,r.getY()); assertTrue(r.getX()<99,"second fall must retain leftward track velocity");
    }
    @Test void belowCameraFallUsesDeletionXWithoutCarryingTheRider() {
        var p=player(80); var r=rock(9,p); r.setPlayerPushing(p,true); distance=15;
        r.update(0,p); when(camera.getMaxY()).thenReturn((short) -188); // +$120 == current Y
        r.update(1,p); assertEquals(0x7F00,r.getX());
        assertFalse(r.carriesRiderOnHorizontalMove(p)); assertTrue(r.isCustomOutOfRange(0));
    }
}
