package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.objects.S3kDezTiltingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezTiltingBridgeHeadless {
    @AfterEach void reset() {SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}

    @Test void eightIndependentPartsKeepOriginalAnchorAndShareExactParent() {
        boot(320,0x100,0x400);var root=create(0x270,0x400);root.update(0,null);
        var parts=family(root);assertEquals(8,parts.size());
        for(int i=0;i<8;i++) {
            var p=parts.get(i);assertEquals(0x200+i*32,p.getX());assertEquals(i+1,p.partIndexForTest());
            assertEquals(0x400,p.getY());assertEquals(5,p.getPriorityBucket());
            if(i>0){assertSame(root,p.parentForTest());assertTrue(p.getSlotIndex()>root.getSlotIndex());}
            assertFalse(p.isCustomOutOfRange(0));assertTrue(p.isCustomOutOfRange(-128));
        }
    }
    @Test void partialSlotAllocationStillRunsParentAndKeepsSuccessfulSections() {
        boot(320,0x100,0x400);var manager=GameServices.level().getObjectManager();var root=create(0x200,0x400);
        var fillers=new ArrayList<S3kDezConveyorBeltObjectInstance>();boolean full=false;
        for(int i=0;i<160;i++) {
            var filler=new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0,0,0x50,0,0,false,0));manager.addDynamicObject(filler);
            if(filler.isDestroyed()){full=true;break;}fillers.add(filler);
        }
        assertTrue(full);for(int i=0;i<3;i++)manager.removeDynamicObject(fillers.removeLast());
        root.update(0,null);assertEquals(4,family(root).size());assertEquals(0x190,root.getX());
        root.update(1,null);assertEquals(4,family(root).size(),"failed allocations are not retried");
    }
    @Test void everyStandingRowUsesSignedRomAccelerationAndDelayedAggregate() throws Exception {
        boot(320,0x100,0x400);
        int[] rowScale={16,12,8,4,-4,-8,-12,-16};
        for(int row=1;row<=8;row++) {
            var root=create(0x200,0x400);root.update(0,null);var parts=family(root);
            controlledStanding(parts,row,0);
            advance(parts);assertEquals(0,root.standingPartForTest(0));
            for(var p:parts)assertEquals(0,p.velocityForTest(),"new standing bits are aggregated for the next pass");
            advance(parts);assertEquals(row,root.standingPartForTest(0));
            for(int i=0;i<8;i++) {
                int acceleration=rowScale[row-1]*(7-2*i)*2;
                assertEquals(acceleration,parts.get(i).velocityForTest());
                assertEquals((0x400<<16)+acceleration,parts.get(i).yFixedForTest());
            }
            for(var p:parts){p.setDestroyed(true);GameServices.level().getObjectManager().removeDynamicObject(p);}
        }
    }
    @Test void nativePlayersAddOrCancelIndependentlyAndExtraFollowersDoNotTilt() throws Exception {
        boot(320,0x100,0x400);
        for(int second:new int[]{1,8}) {
            var root=create(0x200,0x400);root.update(0,null);var parts=family(root);
            controlledStanding(parts,1,second);advance(parts);advance(parts);
            assertEquals(1,root.standingPartForTest(0));assertEquals(second,root.standingPartForTest(1));
            assertEquals(second==1?448:0,root.velocityForTest());
            assertEquals(second==1?-448:0,parts.getLast().velocityForTest());
        }
    }
    @Test void collapseInstallsNextPassThenMultipliesVelocityBeforeGravity() throws Exception {
        boot(320,0x100,0x400);var root=create(0x200,0x400);root.update(0,null);var parts=family(root);
        var contacts=controlledStanding(parts,1,0);
        int steps=0;while(root.routineForTest()==0&&steps++<400)advance(parts);
        assertTrue(steps<400);assertEquals(1,root.routineForTest());
        verify(contacts,never()).checkPlayerReleaseFromObjectFloor(any());
        int velocity=root.velocityForTest(),y=root.yFixedForTest();
        root.update(0,null);
        assertEquals(2,root.routineForTest());assertEquals((velocity<<2)+0x1000,root.velocityForTest());
        assertEquals(y+root.velocityForTest(),root.yFixedForTest());
        verify(contacts).checkPlayerReleaseFromObjectFloor(any());
        velocity=root.velocityForTest();root.update(0,null);assertEquals(velocity+0x1000,root.velocityForTest());
        int fallSteps=0;while(root.getX()!=0x7F00&&fallSteps++<1000)root.update(0,null);
        assertTrue(fallSteps<1000);assertTrue(root.isCustomOutOfRange(0),"camera-bottom retirement replaces the original anchor");
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void placedBridgeTiltsCollapsesAndRecreatesItsGraphWhileCarryingSonic(int width) {
        var f=boot(width,0x410,0x930);f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(40);
        var root=placedRoot();assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),root));
        assertTrue(root.velocityForTest()>0);var parts=family(root);assertEquals(8,parts.size());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var rows=frames(f,root,350);assertEquals(2,root.routineForTest(),"root exceeds the native $70 tilt limit");
        assertFalse(f.sprite().getDead());
        assertFalse(f.sprite().isOnObject(),"falling section releases Sonic onto real terrain");
        for(var p:parts)p.setDestroyed(true);f.stepIdleFrames(1);
        registry.restore(before);var restored=placedRoot();assertNotSame(root,restored);
        for(var p:family(restored))if(p!=restored)assertSame(restored,p.parentForTest());
        assertEquals(rows,frames(f,restored,350));
    }
    @Test void invertedActTwoStandingStillFeedsTiltAndReplays() {
        TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x1050,(short)0x950).startPositionIsCentre().build();
        // Declared component entry; earlier route switches own this state.
        GameServices.gameState().setReverseGravityActive(true);
        f.sprite().setAir(true);f.sprite().setRingCount(7);f.stepIdleFrames(40);
        var root=placedRoot();assertEquals(0x1050,root.getX());
        assertTrue(GameServices.level().getObjectManager().isRidingObject(f.sprite(),root));
        assertTrue(root.velocityForTest()>0,"standing bit is independent of gravity direction");
        assertTrue(f.sprite().getCentreY()>root.getY(),"rider is underneath the bridge");
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var rows=frames(f,root,120);assertFalse(f.sprite().getDead());
        registry.restore(before);assertEquals(rows,frames(f,placedRoot(),120));
        assertTrue(GameServices.gameState().isReverseGravityActive());
    }
    @Test void artUsesBridgeBankRatherThanStaircaseBank() throws Exception {
        boot(320,0x100,0x400);
        var maps=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x46F7A,1);
        assertEquals(1,maps.getFirst().pieces().size());var p=maps.getFirst().pieces().getFirst();
        assertEquals(-16,p.xOffset());assertEquals(-16,p.yOffset());assertEquals(4,p.widthTiles());assertEquals(4,p.heightTiles());
        for(int act=0;act<2;act++) {
            var e=Sonic3kPlcArtRegistry.getPlan(11,act).levelArt().stream().filter(x->x.key().equals(Sonic3kObjectArtKeys.DEZ_TILTING_BRIDGE)).findFirst().orElseThrow();
            assertEquals(0x34D,e.artTileBase());assertEquals(1,e.palette());assertEquals(1,e.mappingFrameCount());
        }
    }
    private ObjectManager controlledStanding(List<S3kDezTiltingBridgeObjectInstance> parts,int p1Part,int p2Part) throws Exception {
        var p1=new TestPlayableSprite();var p2=new TestPlayableSprite();var extra=new TestPlayableSprite();
        var manager=mock(ObjectManager.class);
        when(manager.hasObjectStandingBit(any(),any())).thenAnswer(a->{
            var part=(S3kDezTiltingBridgeObjectInstance)a.getArgument(1);var p=a.getArgument(0);
            return part.partIndexForTest()==(p==p1?p1Part:p==p2?p2Part:1);
        });
        var services=new TestObjectServices(){@Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->p1,()->List.of(p2,extra));}}
                .withDirectObjectManager(manager).withRomReader(TestEnvironment.objectServices().romReader()).withCamera(GameServices.camera());
        for(var p:parts)p.setServices(services);
        return manager;
    }
    private void advance(List<S3kDezTiltingBridgeObjectInstance> parts){for(var p:parts)p.update(0,null);}
    private List<String> frames(HeadlessTestFixture f,S3kDezTiltingBridgeObjectInstance root,int count) {
        var rows=new ArrayList<String>();for(int i=0;i<count;i++){f.stepIdleFrames(1);var p=f.sprite();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getYSpeed()+","+p.isOnObject()+":"+family(root).stream().map(o->o.partIndexForTest()+","+o.getX()+","+o.yFixedForTest()+","+o.velocityForTest()+","+o.routineForTest()).toList());}return rows;
    }
    private S3kDezTiltingBridgeObjectInstance placedRoot(){return GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(o->o instanceof S3kDezTiltingBridgeObjectInstance p&&p.parentForTest()==null&&p.partIndexForTest()==1)
            .map(o->(S3kDezTiltingBridgeObjectInstance)o).findFirst().orElseThrow();}
    private List<S3kDezTiltingBridgeObjectInstance> family(S3kDezTiltingBridgeObjectInstance root){return GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(o->o==root||o instanceof S3kDezTiltingBridgeObjectInstance p&&p.parentForTest()==root)
            .map(o->(S3kDezTiltingBridgeObjectInstance)o).sorted(Comparator.comparingInt(S3kDezTiltingBridgeObjectInstance::partIndexForTest)).toList();}
    private S3kDezTiltingBridgeObjectInstance create(int x,int y){var p=new S3kDezTiltingBridgeObjectInstance(new ObjectSpawn(x,y,0x4B,0,0,false,0));GameServices.level().getObjectManager().addDynamicObject(p);return p;}
    private HeadlessTestFixture boot(int width,int x,int y){var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,(width==320?WidescreenAspect.NATIVE_4_3:WidescreenAspect.SUPER_32_9).name());c.resolveDisplayAspect();c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);SessionManager.clear();TestEnvironment.activeGameplayMode();var f=HeadlessTestFixture.builder().withZoneAndAct(11,0).startPosition((short)x,(short)y).startPositionIsCentre().build();assertEquals(width,GameServices.camera().getWidth());return f;}
}
