package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.solid.*;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal BreakObjectToPieces2 graph; declared bottom checkpoint, not a physical route claim. */
class TestCorkFloorNativeFragmentGraph {
    @BeforeEach void init(){GraphicsManager.getInstance().initHeadless();}
    @AfterEach void reset(){AbstractObjectInstance.resetCameraBoundsForTests();GraphicsManager.getInstance().resetState();}

    @ParameterizedTest @ValueSource(ints={0,1})
    void pieceZeroReusesTheParentAndOnlyElevenHigherSlotsAreAllocated(int flip) {
        var h=new Harness(flip);h.arm();h.step(1);
        var floor=h.floor();assertTrue(floor.isBroken(),"literal bottom checkpoint reached loc_2A6BC");
        assertSame(floor,NativeSstSlotAccess.currentOwner(h.manager,35));
        assertEquals(0x4FF,floor.getX(),"a1=a0 takes table entry zero then MoveSprite2 in this dispatch");
        assertEquals(0x4FE,floor.getY(),"old -$200 velocity moves before +$18 gravity");
        assertEquals(11,h.fragments().size(),"12 pieces comprise a0 plus N-1 checked siblings");
        assertEquals(java.util.stream.IntStream.rangeClosed(36,46).boxed().toList(),
                h.fragments().stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertNull(NativeSstSlotAccess.currentOwner(h.manager,3),"AllocateObjectAfterCurrent cannot borrow lower free SST");
        assertEquals(0x501,h.fragments().getFirst().getX(),"first sibling uses velocity entry1, not entry0");
        assertEquals(0x4FE,h.fragments().getFirst().getY(),"the higher sibling also dispatches this pass");
    }

    @Test void parentPieceStillConvertsWhenEveryHigherSlotIsBusy() {
        var h=new Harness(0);
        for(int slot=36;slot<110;slot++)h.manager.createDynamicObjectAtSlot(
                ()->new Blocker(new ObjectSpawn(0x500,0x500,0,0,0,false,0)),slot);
        h.arm();h.step(1);
        assertEquals(0x4FF,h.floor().getX(),"piece0 is not contingent on AllocateObjectAfterCurrent success");
        assertEquals(0x4FE,h.floor().getY());assertTrue(h.fragments().isEmpty());
        assertNull(NativeSstSlotAccess.currentOwner(h.manager,3));
        assertSame(h.floor(),NativeSstSlotAccess.currentOwner(h.manager,35));
    }

    @Test void bothNativeRolesPurelyRecreateAndReplayTheirMovingFractionalGraphTwice() {
        var h=new Harness(1);h.arm();var rewind=h.manager.rewindSnapshottable();
        var before=rewind.capture();h.step(1);assertEquals(11,h.fragments().size());var born=rewind.capture();
        for(int frame=2;frame<=46;frame++)h.step(frame);var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);same(before,rewind.capture());assertFalse(h.floor().isBroken());
            h.step(1);same(born,rewind.capture());assertTrue(h.floor().isBroken());
            for(int frame=2;frame<=46;frame++)h.step(frame);same(after,rewind.capture());
            rewind.restore(before);same(before,rewind.capture());
            rewind.restore(born);same(born,rewind.capture());
            for(int frame=2;frame<=46;frame++)h.step(frame);same(after,rewind.capture());
        }
    }

    @Test void nativeParentAndClearedSiblingPriorityBytesAreDistinct() {
        var h=new Harness(0);h.arm();h.step(1);
        assertEquals(1,h.floor().getPriorityBucket(),"BreakObjectToPieces writes parent priority word $80");
        assertTrue(h.fragments().stream().allMatch(f->f.getPriorityBucket()==0),
                "FixBugs=0 copies only the high priority byte to a cleared sibling");
    }

    @Test void partialHigherPoolKeepsOnlyTheSuccessfulMappingPrefix() {
        var h=new Harness(0);
        for(int slot=36;slot<110;slot++)if(slot!=37&&slot!=39)h.manager.createDynamicObjectAtSlot(
                ()->new Blocker(new ObjectSpawn(0x500,0x500,0,0,0,false,0)),slot);
        h.arm();h.step(1);
        assertEquals(List.of(37,39),h.fragments().stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(0x501,h.fragments().getFirst().getX(),"first successful sibling is entry1");
        assertEquals(0x4FF,h.fragments().getLast().getX(),"second successful sibling is entry2 (-$E0)");
        assertSame(h.floor(),NativeSstSlotAccess.currentOwner(h.manager,35));
        assertNull(NativeSstSlotAccess.currentOwner(h.manager,3));
    }

    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void previousRenderBitRetiresBothFragmentRolesAfterTheNativeEdgeAndReplays(int width) {
        for(boolean rightEdge:new boolean[]{false,true}) {
            var h=new Harness(0,width);assertEquals(width,h.camera.getWidth());h.arm();h.step(1);
            int slot=rightEdge?36:35;
            var owner=NativeSstSlotAccess.currentOwner(h.manager,slot);
            var rewind=h.manager.rewindSnapshottable();var born=rewind.capture();
            // Render_Sprites: inclusive left edge, exclusive right edge.
            h.cameraX=rightEdge?owner.getX()-width-15:owner.getX()+16;
            h.step(2);assertNotNull(NativeSstSlotAccess.currentOwner(h.manager,slot),
                    "loc2A5F8 consumes the preceding visible bit before this new edge clears it");
            var crossed=rewind.capture();h.step(3);
            assertNull(NativeSstSlotAccess.currentOwner(h.manager,slot),"next dispatch consumes the cleared bit");
            var retired=rewind.capture();
            for(int cycle=0;cycle<2;cycle++) {
                rewind.restore(born);same(born,rewind.capture());h.step(2);same(crossed,rewind.capture());
                h.step(3);same(retired,rewind.capture());rewind.restore(born);same(born,rewind.capture());
                rewind.restore(crossed);same(crossed,rewind.capture());h.step(3);same(retired,rewind.capture());
            }
        }
    }

    @Test void everyNativeFragmentDrawsOneStaticPieceWithArtBit15() {
        var h=new Harness(0);h.arm();h.step(1);
        h.floor().appendRenderCommands(new ArrayList<>());
        h.fragments().forEach(f->f.appendRenderCommands(new ArrayList<>()));
        @SuppressWarnings("rawtypes") ArgumentCaptor<List> pieces=ArgumentCaptor.forClass(List.class);
        verify(h.renderer,times(12)).drawPieces(pieces.capture(),anyInt(),anyInt(),anyBoolean(),eq(false));
        assertTrue(pieces.getAllValues().stream().allMatch(v->v.size()==1
                &&((SpriteMappingPiece)v.getFirst()).priority()),"BreakObjectToPieces ORs high_priority for each single mapping");
    }

    @ParameterizedTest @CsvSource({"0,-320,-2560,14", "2,-288,-2304,12", "4,-256,-2048,10",
            "6,-224,-1792,8", "8,-192,-1536,6", "26,-512,-512,4"})
    void iczNativeNibbleSelectsItsLiteralVelocitySuffixAndBothRolesReplay(int subtype,int vx,int vy,int pieces) {
        var h=new Harness(0,320,5,subtype);h.main.setTopSolidBit((byte)0x0E);
        h.floor().appendRenderCommands(new ArrayList<>());
        verify(h.renderer).drawFrameIndex(subtype&15,0x500,0x500,false,false);
        h.arm();var rewind=h.manager.rewindSnapshottable();var before=rewind.capture();h.step(1);
        assertTrue(h.floor().isBroken());
        assertEquals(0x500+(vx>>8),h.floor().getX());assertEquals(0x500+(vy>>8),h.floor().getY());
        assertEquals(pieces-1,h.fragments().size(),"native mapping count includes the reused a0");
        assertEquals(0x500+((-vx)>>8),h.fragments().getFirst().getX());
        assertEquals(0x500+(vy>>8),h.fragments().getFirst().getY());var born=rewind.capture();
        for(int frame=2;frame<=46;frame++)h.step(frame);var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);same(before,rewind.capture());h.step(1);same(born,rewind.capture());
            for(int frame=2;frame<=46;frame++)h.step(frame);same(after,rewind.capture());
            rewind.restore(before);same(before,rewind.capture());rewind.restore(born);same(born,rewind.capture());
            for(int frame=2;frame<=46;frame++)h.step(frame);same(after,rewind.capture());
        }
    }

    @Test void iczSuffixSurvivesCheckedPartialPressureAndRecreatesWithoutAllocatingGhostPieces() {
        var h=new Harness(0,320,5,4);h.main.setTopSolidBit((byte)0x0E);
        for(int slot=38;slot<110;slot++)h.manager.createDynamicObjectAtSlot(
                ()->new Blocker(new ObjectSpawn(0x500,0x500,0,0,0,false,0)),slot);
        h.arm();var rewind=h.manager.rewindSnapshottable();var before=rewind.capture();h.step(1);
        assertEquals(0x4FF,h.floor().getX());assertEquals(0x4F8,h.floor().getY());
        assertEquals(List.of(36,37),h.fragments().stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(0x501,h.fragments().getFirst().getX());assertEquals(0x4F8,h.fragments().getFirst().getY());
        assertEquals(0x4FF,h.fragments().getLast().getX());assertEquals(0x4F9,h.fragments().getLast().getY());
        var born=rewind.capture();h.step(2);var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);same(before,rewind.capture());h.step(1);same(born,rewind.capture());h.step(2);same(after,rewind.capture());
            rewind.restore(before);rewind.restore(born);same(born,rewind.capture());h.step(2);same(after,rewind.capture());
        }
    }

    @ParameterizedTest @ValueSource(ints={0,128})
    void iczAlternatePlaneAndSignedSubtypeAdmissionRemainIndependent(int subtype) {
        var h=new Harness(0,320,5,subtype);h.main.setTopSolidBit((byte)0x0C);h.arm();h.step(1);
        assertEquals(subtype==128,h.floor().isBroken(),"loc2A77A tests signed subtype before the alternate plane");
        if(subtype==0)assertTrue(h.fragments().isEmpty());
    }

    private static void same(Object expected,Object actual){
        var diff=RewindSnapshotDiff.diffKey("object-manager",expected,actual);
        assertTrue(diff.isEmpty(),()->diff.toString());
    }

    static final class BottomCheckpointFloor extends CorkFloorObjectInstance {
        private boolean armed;
        BottomCheckpointFloor(ObjectSpawn spawn){super(spawn);}
        @Override protected SolidCheckpointBatch checkpointAll(){
            var main=services().playerQuery().mainPlayerOrNull();
            if(!armed)return new SolidCheckpointBatch(this,Map.of());
            var contact=new PlayerSolidContactResult(ContactKind.BOTTOM,false,false,false,false,
                    new PreContactState((short)0,(short)-0x200,false,true,0),PostContactState.ZERO,0);
            return new SolidCheckpointBatch(this,Map.of(main,contact));
        }
        @Override public BottomCheckpointFloor recreateForRewind(RewindRecreateContext ctx){
            return ObjectConstructionContext.construct(ctx.objectServices(),()->new BottomCheckpointFloor(ctx.spawn()));
        }
    }
    static final class SlopedCheckpointFloor extends CorkFloorObjectInstance {
        private boolean armed;
        SlopedCheckpointFloor(ObjectSpawn spawn){super(spawn);}
        @Override protected SolidCheckpointBatch checkpointAll(){
            var main=services().playerQuery().mainPlayerOrNull();
            if(!armed)return new SolidCheckpointBatch(this,Map.of());
            var contact=new PlayerSolidContactResult(ContactKind.TOP,true,false,false,false,
                    new PreContactState((short)0,(short)0,true,false,2),PostContactState.ZERO,0);
            return new SolidCheckpointBatch(this,Map.of(main,contact));
        }
        @Override public SlopedCheckpointFloor recreateForRewind(RewindRecreateContext ctx){
            return ObjectConstructionContext.construct(ctx.objectServices(),()->new SlopedCheckpointFloor(ctx.spawn()));
        }
    }
    static final class Blocker extends AbstractObjectInstance implements RewindRecreatable {
        Blocker(ObjectSpawn spawn){super(spawn,"Blocker");}
        @Override public void update(int clock,PlayableEntity main){}
        @Override public void appendRenderCommands(List<GLCommand> commands){}
        @Override public boolean isPersistent(){return !isDestroyed();}
        @Override public Blocker recreateForRewind(RewindRecreateContext ctx){return new Blocker(ctx.spawn());}
    }
    static final class Harness {
        final Camera camera=mock(Camera.class);
        final TestablePlayableSprite main=new TestablePlayableSprite("sonic",(short)0,(short)0);
        final ObjectManager manager;
        final PatternSpriteRenderer renderer=mock(PatternSpriteRenderer.class);
        int cameraX=0x400;
        Harness(int flip) {this(flip,320);}
        Harness(int flip,int width) {this(flip,width,0,0);}
        Harness(int flip,int width,int zone,int subtype) {
            when(camera.getX()).thenAnswer(invocation->(short)cameraX);when(camera.getY()).thenReturn((short)0x480);
            when(camera.getXCopy()).thenAnswer(invocation->(short)cameraX);when(camera.getYCopy()).thenReturn((short)0x480);
            when(camera.getWidth()).thenReturn((short)width);when(camera.getHeight()).thenReturn((short)224);
            when(camera.getFocusedSprite()).thenReturn(main);
            var render=mock(ObjectRenderManager.class);
            var pieces=new ArrayList<SpriteMappingPiece>();
            for(int i=0;i<12;i++)pieces.add(new SpriteMappingPiece(-16+(i&1)*16,-40+(i>>1)*8,2,1,i*2,false,false,2));
            var frames=new ArrayList<SpriteMappingFrame>();
            if(zone==5)for(int frame=0;frame<12;frame++) {
                int count=frame>=10?4:(frame%2==1?15-frame:(14-frame)/2);
                var iczPieces=new ArrayList<SpriteMappingPiece>();
                for(int i=0;i<count;i++)iczPieces.add(new SpriteMappingPiece((i&1)*16-16,0,2,1,i*2,false,false,2));
                frames.add(new SpriteMappingFrame(iczPieces));
            } else {frames.add(new SpriteMappingFrame(List.of()));frames.add(new SpriteMappingFrame(pieces));}
            var sheet=new ObjectSpriteSheet(new Pattern[0],frames,2,1);
            String artKey=zone==5?Sonic3kObjectArtKeys.CORK_FLOOR_ICZ:Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1;
            when(render.getSheet(artKey)).thenReturn(sheet);
            when(render.getRenderer(artKey)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            var query=new ObjectPlayerQuery(()->main,List::of);
            var services=new TestObjectServices(){
                @Override public ObjectRenderManager renderManager(){return render;}
                @Override public ObjectPlayerQuery playerQuery(){return query;}
                @Override public int romZoneId(){return zone;}
                @Override public int currentAct(){return zone==5?1:0;}
            }
                    .withCamera(camera).withGraphicsManager(GraphicsManager.getInstance()).withGameModule(new Sonic3kGameModule());
            manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,
                    GraphicsManager.getInstance(),camera,services);services.withDirectObjectManager(manager);
            manager.reset(0x400);manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.createDynamicObjectAtSlot(()->zone==5?new SlopedCheckpointFloor(
                    new ObjectSpawn(0x500,0x500,0x2A,subtype,flip,false,0)):new BottomCheckpointFloor(
                    new ObjectSpawn(0x500,0x500,0x2A,subtype,flip,false,0)),35);
        }
        CorkFloorObjectInstance floor(){return (CorkFloorObjectInstance)NativeSstSlotAccess.currentOwner(manager,35);}
        void arm(){step(0);if(floor() instanceof BottomCheckpointFloor f)f.armed=true;
            else if(floor() instanceof SlopedCheckpointFloor f)f.armed=true;}
        void step(int frame){manager.update(cameraX,main,List.of(),frame);manager.refreshPostCameraRenderState();}
        List<CorkFloorObjectInstance.CorkFloorFragment> fragments(){return manager.getActiveObjects().stream()
                .filter(o->!o.isDestroyed()).filter(CorkFloorObjectInstance.CorkFloorFragment.class::isInstance)
                .map(CorkFloorObjectInstance.CorkFloorFragment.class::cast)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();}
    }
}
