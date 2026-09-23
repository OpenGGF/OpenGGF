package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.objects.S3kDezLiftPadObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.level.objects.*;
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
class TestS3kDezLiftPadHeadless {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}

    @Test void allOrientationsUseSixteenPixelRomLinksAndIndependentDrawSlot(){
        boot(320,0x100,0x400);
        for(int orientation:new int[]{0,0x10,0x20,0x30})for(int flags=0;flags<4;flags++){
            var pad=create(0x600,0x500,orientation|7,flags);pad.update(0,null);var arm=pad.armForTest();assertNotNull(arm);
            assertTrue(arm.getSlotIndex()>pad.getSlotIndex());assertEquals(7,arm.countForTest());
            int dx=orientation<0x20?((flags&1)==0?-16:16):0;
            int dy=orientation==0x20?-16:orientation==0x30?16:0;
            for(int i=0;i<7;i++){assertEquals(0x600+dx*(i==2?7:i),arm.jointXForTest(i));assertEquals(0x500+dy*(i==2?7:i),arm.jointYForTest(i));}
            assertEquals(0x600+dx*8+((flags&1)==0?-32:32),pad.getX());assertEquals(0x500+dy*8,pad.getY());
            assertEquals(0x600+dx*2,arm.getX());assertEquals(0x500+dy*2,arm.getY(),"main sprite replaces sub4 in the SAT order");
            assertEquals(0x600+dx*7,arm.sub4XForTest());assertEquals(0x500+dy*7,arm.sub4YForTest());
            var manager=GameServices.level().getObjectManager();pad.onUnload();manager.removeDynamicObject(pad);manager.removeDynamicObject(arm);
        }
    }

    @Test void onlyPrimaryStartsAndItsStandingBitHoldsTheThirtyFrameReturnPause(){
        boot(320,0x100,0x400);var pad=create(0x600,0x500,7,0);pad.update(0,null);var h=control(pad);
        h.standing.set(2);pad.update(0,null);assertFalse(pad.movingForTest());
        h.standing.set(1);h.primary.setDebugMode(true);pad.update(0,null);assertFalse(pad.movingForTest());
        h.primary.setDebugMode(false);
        for(int i=0;i<45;i++)pad.update(0,null);
        assertEquals(0x2058,pad.angleForTest());assertEquals(360,pad.angularVelocityForTest());assertEquals(1,pad.phaseForTest());
        for(int i=0;i<45;i++)pad.update(0,null);
        assertEquals(0x3F48,pad.angleForTest());assertEquals(0,pad.angularVelocityForTest());assertEquals(30,pad.pauseForTest());
        for(int i=0;i<100;i++)pad.update(0,null);assertEquals(30,pad.pauseForTest());assertEquals(1,h.sounds.size());
        h.standing.set(2);for(int i=0;i<30;i++)pad.update(0,null);assertEquals(0,pad.pauseForTest());
        assertEquals(0x3F48,pad.angleForTest());
        for(int i=0;i<90;i++)pad.update(0,null);
        assertEquals(0,pad.angleForTest());assertEquals(0,pad.angularVelocityForTest());assertFalse(pad.movingForTest());
        assertEquals(1,h.sounds.size(),"automatic return does not restart the lift sound");
    }

    @Test void mainJointUsesTheAliasedThirdJointAndCullUsesOriginalAnchor(){
        boot(320,0x100,0x400);var pad=create(0x300,0x500,7,0);pad.update(0,null);var arm=pad.armForTest();
        assertEquals(0x260,pad.getX());assertTrue(pad.isCustomOutOfRange(0));assertFalse(arm.isCustomOutOfRange(0));
        var h=control(pad);h.standing.set(1);
        for(int i=0;i<7;i++)pad.update(0,null);assertEquals(0x2E0,arm.getX());assertEquals(0x500,arm.getY());
        pad.update(0,null);assertEquals(1,pad.angleForTest()>>>8);assertEquals(0x2E0,arm.getX());assertEquals(0x4FF,arm.getY());
        assertEquals(arm.sub4YForTest(),arm.jointYForTest(2));
        pad.onUnload();assertTrue(arm.isDestroyed());assertTrue(pad.isDestroyed());
    }

    @Test void exhaustedForwardAllocationDoesNotRetryOrInventAnArm() throws Exception {
        boot(320,0x100,0x400);var manager=GameServices.level().getObjectManager();var pad=create(0x600,0x500,7,0);
        var fillers=new ArrayList<S3kDezConveyorBeltObjectInstance>();boolean full=false;
        for(int i=0;i<160;i++){var filler=new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0,0,0x50,0,0,false,0));manager.addDynamicObject(filler);if(filler.isDestroyed()){full=true;break;}fillers.add(filler);}
        assertTrue(full);pad.update(0,null);assertNull(pad.armForTest());
        assertEquals(0x200,TestEnvironment.objectServices().romReader().readU16BE(0x16));
        assertEquals(0xE5D0,pad.getX(),"null arm reads 512 links from ROM vector table");assertEquals(0x500,pad.getY());
        manager.removeDynamicObject(fillers.getLast());pad.update(0,null);assertNull(pad.armForTest());assertEquals(0xE5D0,pad.getX());
    }

    @Test void unloadRetiresTheArmWithoutChangingThePadsRespawnDecision(){
        boot(320,0x100,0x400);
        for(boolean respawnable:new boolean[]{false,true}){
            var pad=create(0x600,0x500,7,0);pad.update(0,null);var arm=pad.armForTest();
            if(respawnable)ObjectLifetimeOps.destroyRespawnableOffscreen(pad);
            pad.onUnload();
            assertTrue(pad.isDestroyed());assertEquals(respawnable,pad.isDestroyedRespawnable());
            assertTrue(arm.isDestroyed());assertFalse(arm.isDestroyedRespawnable());
        }
    }

    @Test void mapUsesMiscTwoArtAndHasPadJointAndAnchorFrames() throws Exception {
        var frames=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x47614,3);
        assertEquals(List.of(3,1,1),frames.stream().map(f->f.pieces().size()).toList());
        assertEquals(-24,frames.getFirst().pieces().getFirst().xOffset());
        assertEquals(0xA,frames.get(1).pieces().getFirst().tileIndex());
    }

    @ParameterizedTest @ValueSource(ints={320,800})
    void placedLiftCarriesSonicAndRecreatesTheExactArmThroughRewind(int width){
        var f=boot(width,0x498,0x708);f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(40);
        var pad=placed();assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),pad));assertTrue(pad.movingForTest());
        var arm=pad.armForTest();assertNotNull(arm);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();var rows=frames(f,pad,140);
        assertFalse(f.sprite().getDead());assertEquals(30,pad.pauseForTest());
        pad.setDestroyed(true);f.stepIdleFrames(1);registry.restore(saved);var restored=placed();assertNotSame(pad,restored);assertNotSame(arm,restored.armForTest());
        assertEquals(rows,frames(f,restored,140));
        var paused=registry.capture();f.stepFrame(false,false,false,true,true);var jumped=frames(f,restored,60);
        registry.restore(paused);f.stepFrame(false,false,false,true,true);assertEquals(jumped,frames(f,placed(),60));
    }

    private record Control(AtomicInteger standing,TestPlayableSprite primary,List<Integer> sounds) { }
    private Control control(S3kDezLiftPadObjectInstance pad){
        var primary=new TestPlayableSprite();var secondary=new TestPlayableSprite();var standing=new AtomicInteger();var sounds=new ArrayList<Integer>();
        var manager=mock(ObjectManager.class);when(manager.hasObjectStandingBit(any(),any())).thenAnswer(a->(standing.get()&(a.getArgument(0)==primary?1:2))!=0);
        var services=new TestObjectServices(){@Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->primary,()->List.of(secondary));}@Override public void playSfx(int id){sounds.add(id);}}
                .withDirectObjectManager(manager).withCamera(GameServices.camera());pad.setServices(services);return new Control(standing,primary,sounds);
    }
    private S3kDezLiftPadObjectInstance create(int x,int y,int subtype,int flags){var p=new S3kDezLiftPadObjectInstance(new ObjectSpawn(x,y,0x4E,subtype,flags,false,0));GameServices.level().getObjectManager().addDynamicObject(p);return p;}
    private S3kDezLiftPadObjectInstance placed(){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(o->o instanceof S3kDezLiftPadObjectInstance).map(o->(S3kDezLiftPadObjectInstance)o).findFirst().orElseThrow();}
    private HeadlessTestFixture boot(int width,int x,int y){var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,(width==320?WidescreenAspect.NATIVE_4_3:WidescreenAspect.SUPER_32_9).name());c.resolveDisplayAspect();c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);SessionManager.clear();TestEnvironment.activeGameplayMode();return HeadlessTestFixture.builder().withZoneAndAct(11,0).startPosition((short)x,(short)y).startPositionIsCentre().build();}
    private List<String> frames(HeadlessTestFixture f,S3kDezLiftPadObjectInstance p,int count){var rows=new ArrayList<String>();for(int i=0;i<count;i++){f.stepIdleFrames(1);var player=f.sprite();var a=p.armForTest();rows.add(player.getCentreX()+","+player.getCentreY()+","+player.getYSpeed()+","+player.isOnObject()+":"+p.getX()+","+p.getY()+","+p.angleForTest()+","+p.angularVelocityForTest()+","+p.pauseForTest()+":"+a.getX()+","+a.getY()+","+a.sub4XForTest()+","+a.sub4YForTest()+","+a.jointXForTest(3)+","+a.jointYForTest(3));}return rows;}
}
