package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.objects.S3kDezConveyorPadObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezConveyorPadHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); SessionManager.clear(); }

    @Test void activationWaitsOnePassThenCarriesBothNativePlayersBeforeChangingDirection() throws Exception {
        for (int flags=0;flags<4;flags++) for(boolean reverse:new boolean[]{false,true}) {
            var h=new Harness(1,flags); GameServices.gameState().setReverseGravityActive(reverse);
            h.pad.update(55,null);assertEquals(0,h.pad.routineForTest());assertEquals(1,h.pad.mappingFrameForTest());
            h.standing.set(7);int p1x=h.p1.getCentreX(),p2x=h.p2.getCentreX(),extraX=h.extra.getCentreX();
            h.p1.setSubpixelRaw(0x1234,0x5678);
            h.pad.update(55,null);assertEquals(1,h.pad.routineForTest());assertEquals(0x500,h.pad.getY());
            assertEquals(p1x,h.p1.getCentreX());
            h.pad.update(55,null);int dx=((flags&1)==0?2:-2)*(reverse?-1:1);
            assertEquals(p1x+dx,h.p1.getCentreX());assertEquals(p2x+dx,h.p2.getCentreX());
            assertEquals(extraX,h.extra.getCentreX());assertEquals(0x1234,h.p1.getXSubpixelRaw());
            assertEquals((flags&1)^1,h.pad.animationForTest());assertEquals(0x501,h.pad.getY());
            h.pad.update(55,null);assertEquals(p1x,h.p1.getCentreX());assertEquals((flags&1)^1,h.pad.animationForTest());
            h.standing.set(0);h.pad.update(55,null);h.standing.set(3);h.pad.update(55,null);
            assertEquals(flags&1,h.pad.animationForTest(),"simultaneous new riders toggle once");
        }
    }

    @Test void verticalDistanceStopsExactlyButConveyorAndNewRiderEdgesContinue() throws Exception {
        for(int subtype:new int[]{1,0x81,0x80}) {
            var h=new Harness(subtype,0);h.standing.set(1);h.pad.update(123,null);
            int y=h.pad.getY();for(int i=0;i<12;i++)h.pad.update(123,null);
            assertEquals(y+(subtype==1?8:subtype==0x81?-8:0),h.pad.getY());
            assertEquals(0,h.pad.remainingForTest());assertEquals(subtype==0x80?0x40:0x80,h.pad.getOnScreenHalfWidth());
            int x=h.p1.getCentreX();h.pad.update(123,null);assertEquals(x-2,h.p1.getCentreX());
            assertEquals(subtype==0x80?0:8,h.sounds.size(),"vertical sound uses level clock, without visibility");
        }
    }

    @Test void horizontalTerrainBoundariesDelayedFallAndWallTurnsMatchRom() throws Exception {
        var h=new Harness(0,0);h.standing.set(1);h.pad.update(0,null);
        AtomicInteger floor=new AtomicInteger(0),left=new AtomicInteger(0),right=new AtomicInteger(0);
        try(var terrain=mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(()->ObjectTerrainUtils.checkFloorDist(any(LevelManager.class),anyInt(),anyInt()))
                    .thenAnswer(a->new TerrainCheckResult(floor.get(),(byte)0,0));
            terrain.when(()->ObjectTerrainUtils.checkLeftWallDist(any(LevelManager.class),anyInt(),anyInt()))
                    .thenAnswer(a->new TerrainCheckResult(left.get(),(byte)0,0));
            terrain.when(()->ObjectTerrainUtils.checkRightWallDist(any(LevelManager.class),anyInt(),anyInt()))
                    .thenAnswer(a->new TerrainCheckResult(right.get(),(byte)0,0));
            int px=h.p1.getCentreX();h.pad.update(0,null);
            assertEquals(px,h.p1.getCentreX(),"horizontal route has no extra conveyor carry");
            assertEquals(0x501,h.pad.getY());assertEquals(0,h.pad.floorRoutineForTest());assertEquals(0x401,h.pad.getX());
            floor.set(-2);h.pad.update(0,null);assertEquals(1,h.pad.floorRoutineForTest());assertEquals(0x500,h.pad.getY());
            floor.set(14);h.pad.update(0,null);assertEquals(0x50E,h.pad.getY());assertEquals(1,h.pad.floorRoutineForTest());
            floor.set(15);h.pad.update(0,null);assertEquals(2,h.pad.floorRoutineForTest());assertEquals(0,h.pad.yVelocityForTest());
            int oldY=h.pad.yFixedForTest();h.pad.update(0,null);assertEquals(oldY,h.pad.yFixedForTest());assertEquals(0x38,h.pad.yVelocityForTest());
            h.pad.update(0,null);assertEquals(oldY+(0x38<<8),h.pad.yFixedForTest());assertEquals(0x70,h.pad.yVelocityForTest());
            floor.set(-1);h.pad.update(0,null);assertEquals(1,h.pad.floorRoutineForTest());assertEquals(0,h.pad.yVelocityForTest());
            floor.set(0);int x=h.pad.getX();left.set(-3);h.pad.update(0,null);assertEquals(x-1+3,h.pad.getX());assertEquals(0,h.pad.animationForTest());
            left.set(0);right.set(-4);x=h.pad.getX();h.pad.update(0,null);assertEquals(x+1-4,h.pad.getX());assertEquals(1,h.pad.animationForTest());
            assertTrue(h.sounds.isEmpty(),"horizontal sound requires preceding render visibility");
        }
    }

    @Test void nativeSecondPlayerStartsThePadButAnExtraFollowerDoesNot() throws Exception {
        var h=new Harness(0x80,0);h.standing.set(4);h.pad.update(0,null);
        assertEquals(0,h.pad.routineForTest());
        h.standing.set(2);h.pad.update(0,null);assertEquals(1,h.pad.routineForTest());
        int x=h.p2.getCentreX(),primary=h.p1.getCentreX();h.pad.update(0,null);
        assertEquals(x+2,h.p2.getCentreX());assertEquals(primary,h.p1.getCentreX());
    }

    @Test void bothAnimationDirectionsLoopTheRomFramesAtTwoFrameIntervals() throws Exception {
        for(int flags=0;flags<2;flags++) {
            var h=new Harness(0x80,flags);h.standing.set(1);h.pad.update(0,null);h.pad.update(0,null);
            int[] expected=flags==0?new int[]{3,3,2,2,1,1,3,3,2,2,1,1}:new int[]{1,1,2,2,3,3,1,1,2,2,3,3};
            for(int frame:expected){assertEquals(frame,h.pad.mappingFrameForTest());h.pad.update(0,null);}
        }
    }

    @Test void mappingTablesExposeNativeNarrowAndWideShapes() throws Exception {
        var rom=TestEnvironment.objectServices().romReader();
        var narrow=S3kSpriteDataLoader.loadMappingFrames(rom,0x47C08,5);
        var wide=S3kSpriteDataLoader.loadMappingFrames(rom,0x47CA8,4);
        assertEquals(5,narrow.size());assertEquals(4,wide.size());
        for(int i=0;i<4;i++){assertEquals(8,narrow.get(i).pieces().size());assertEquals(16,wide.get(i).pieces().size());}
        assertEquals(16,narrow.get(4).pieces().size(),"fifth pointer aliases first wide frame");
    }

    @ParameterizedTest @ValueSource(ints={320,800})
    void actualHorizontalPadCarriesAndRecreatesThroughRewind(int width) {
        var f=boot(width,0,0x2000,0x5D0);f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(50);
        var pad=placed();assertEquals(2,pad.routineForTest());assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),pad));
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        var rows=frames(f,pad,120);assertFalse(f.sprite().getDead());
        pad.setDestroyed(true);f.stepIdleFrames(1);registry.restore(saved);var restored=placed();assertNotSame(pad,restored);
        assertEquals(rows,frames(f,restored,120));
    }

    @ParameterizedTest @ValueSource(ints={320,800})
    void actualVerticalPadMovesUpAndReplaysItsFiniteTravel(int width) {
        var f=boot(width,0,0x1540,0x910);f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(40);
        var pad=placed();assertEquals(1,pad.routineForTest());assertTrue(pad.getY()<0x950);
        assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),pad));
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();var rows=frames(f,pad,160);
        registry.restore(saved);assertEquals(rows,frames(f,placed(),160));
    }

    @Test void declaredInvertedAct2EntryRidesTheUndersideAndReplays() throws Exception {
        var f=boot(320,1,0x640,0x630);GameServices.gameState().setReverseGravityActive(true);
        f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(40);var pad=placed();
        assertEquals(1,pad.routineForTest());assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),pad));
        assertTrue(f.sprite().getCentreY()>pad.getY());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();var rows=frames(f,pad,80);
        registry.restore(saved);assertEquals(rows,frames(f,placed(),80));assertTrue(GameServices.gameState().isReverseGravityActive());
    }

    private static final class Harness {
        final TestPlayableSprite p1=new TestPlayableSprite(),p2=new TestPlayableSprite(),extra=new TestPlayableSprite();
        final AtomicInteger standing=new AtomicInteger();final List<Integer> sounds=new ArrayList<>();
        final S3kDezConveyorPadObjectInstance pad;
        Harness(int subtype,int flags) throws Exception {
            GameServices.camera().setMaxY((short)0x1000);
            var manager=mock(ObjectManager.class);var level=mock(LevelManager.class);when(level.getFrameCounter()).thenReturn(16);when(level.getObjectManager()).thenReturn(manager);
            when(manager.hasObjectStandingBit(any(),any())).thenAnswer(a->(standing.get()&(a.getArgument(0)==p1?1:a.getArgument(0)==p2?2:4))!=0);
            var services=new TestObjectServices(){
                @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->p1,()->List.of(p2,extra));}
                @Override public void playSfx(int id){sounds.add(id);}
            }.withDirectObjectManager(manager).withLevelManager(level).withGameState(GameServices.gameState())
                    .withCamera(GameServices.camera()).withRomReader(TestEnvironment.objectServices().romReader());
            pad=new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x400,0x500,0x53,subtype,flags,false,0));pad.setServices(services);
        }
    }
    private HeadlessTestFixture boot(int width,int act,int x,int y) {
        var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,(width==320?WidescreenAspect.NATIVE_4_3:WidescreenAspect.SUPER_32_9).name());
        c.resolveDisplayAspect();c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);SessionManager.clear();TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(11,act).startPosition((short)x,(short)y).startPositionIsCentre().build();
    }
    private S3kDezConveyorPadObjectInstance placed(){return GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(o->o instanceof S3kDezConveyorPadObjectInstance).map(o->(S3kDezConveyorPadObjectInstance)o)
            .min(java.util.Comparator.comparingInt(o->Math.abs(o.getX()-GameServices.camera().getX()-GameServices.camera().getWidth()/2))).orElseThrow();}
    private List<String> frames(HeadlessTestFixture f,S3kDezConveyorPadObjectInstance pad,int count){var rows=new ArrayList<String>();for(int i=0;i<count;i++){f.stepIdleFrames(1);var p=f.sprite();rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getXSpeed()+","+p.getYSpeed()+","+p.isOnObject()+":"+pad.getX()+","+pad.yFixedForTest()+","+pad.yVelocityForTest()+","+pad.remainingForTest()+","+pad.routineForTest()+","+pad.floorRoutineForTest()+","+pad.animationForTest()+","+pad.mappingFrameForTest());}return rows;}
}
