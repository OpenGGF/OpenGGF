package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST; Java/runtime/pixels UNRUN. Component vectors below are labelled as such.
 * The public obligation counts only a healthy returned row with an initialized FloatBlock.
 * No partially returned Plant row, arbitrary UnsupportedOperationException, pose/clock edit,
 * supplied snapshot, private Frame/World construction or projection grants invocation authority. */
@Isolated
class TestCdOriginalR11FloatBlockWorld extends TestCdOriginalR11LoadingWorld {
    @Test void actualPublicControllerMustReturnFloatBlockAndMatchHealthyFullAndCourseReplay()throws Exception {
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var full=registry.capture();var course=registry.captureCourse();var services=module.programServicesState();
            assertFalse(returned(module),"Ready alone is not a returned FloatBlock operation");
            int rows=0;
            while(rows<4096&&!returned(module)){step(loop,8);rows++;assertFalse(frame.closed(),"A failed/closed row is never success");}
            assertTrue(returned(module),"Require a returned original FloatBlock $12 operation, not a later invented endpoint");
            assertTrue(frame.stageSource().ownsPresent(source.assets()));
            assertTrue(module.originalR11WorldImage().orElseThrow().cpu().isPresent(),"The complete physical walk and CPU drain must return");
            var expected=image(module);int returnedRows=rows;
            registry.restore(full);for(int i=0;i<returnedRows;i++)step(loop,8);
            assertTrue(returned(module));assertFalse(frame.closed());CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            registry.restoreCourse(course);for(int i=0;i<returnedRows;i++)step(loop,8);
            assertTrue(returned(module));assertFalse(frame.closed());CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(module));
            assertSame(frame,module.originalR11EntryFrame());assertSame(services.loads(),module.programServicesState().loads());
            assertEquals(services.completedCalls(),module.programServicesState().completedCalls());
            SessionManager.clear();assertTrue(frame.closed());
            assertThrows(IllegalStateException.class,()->registry.restore(full));
            assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(course));
        }
    }
    @Test void originalSourceAndCurrentGeometryGuardRejectsDifferentOwnersAndChangedBytesBeforeWrites()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=source.assets();var bound=CdStagePlayerSource.fromPresent(a);
        var blocks=a.terrain().blocks();var layout=a.terrain().layout().ram();
        CdWorld.requireOriginalR11FloatBlockTerrain(bound,a,blocks,layout);
        var badBlocks=blocks.copy();badBlocks[0]^=1;var badLayout=layout.copy();badLayout[0]^=1;
        assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalR11FloatBlockTerrain(bound,a,CdAssetBytes.copyOf(badBlocks),layout));
        assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalR11FloatBlockTerrain(bound,a,blocks,CdAssetBytes.copyOf(badLayout)));
        assertThrows(NullPointerException.class,()->CdWorld.requireOriginalR11FloatBlockTerrain(bound,a,null,layout));
        // Same media read anew has equal source metadata, but is a distinct retained aggregate owner.
        java.nio.file.Path cue;
        try(var files=java.nio.file.Files.list(java.nio.file.Path.of(System.getProperty("soniccd.media.root")).resolve("USA"))){
            cue=files.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).findFirst().orElseThrow();
        }
        var other=CdStockLaunch.prepare(cue,CdStockLaunch.Request.r11a()).assets();
        assertNotSame(a,other);
        assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalR11FloatBlockTerrain(bound,other,blocks,layout));
        var declaredPlacement=new CdObjectPlacementRuntime(a.placements());
        assertThrows(IllegalArgumentException.class,()->CdFloatBlock.fromOriginalPresentAssets(declaredPlacement,a,c->{fail("No Top");},(p,id)->fail("No Draw")));
        assertArrayEquals(blocks.copy(),a.terrain().blocks().copy());assertArrayEquals(layout.copy(),a.terrain().layout().ram().copy());
    }
    @Test void constructedSharedTopUsesGenuineFramePreparationAndRejectsStaleCompletePair()throws Exception {
        var a=TestCdOriginalBootPrefix.source().assets();
        // Deliberately a component-only DEV Frame, NOT the actual public World/controller test above.
        try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
            var placement=CdObjectPlacementRuntime.fromAssets(a);var pool=placement.pool();
            var object=pool.state(40).moveByte(ID,0x12).moveByte(ROUTINE,2);
            pool.write(object);var s=frame.state();var e=frame.environment();var h=s.header();var g=e.movement().globals();
            var player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());
            var globals=new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());
            var cut=pool.capture();var outcome=CdSolidObject.fromAssets(a).runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT,object,player,new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),globals,cut);
            var result=assertInstanceOf(CdSolidObject.Completed.class,outcome).completedResult();
            var prepared=frame.prepareObjectSolidResult(player,globals,result);
            assertSame(s,frame.state());assertSame(e,frame.environment());assertEquals(cut.slots(),pool.capture().slots());
            frame.validatePreparedObjectSolidPublication(prepared);
            var angle=frame.prepareObjectPrimaryAngle(g.primaryAngle(),g.primaryAngle()^1);
            frame.applyPreparedObjectSolidPublication(angle);
            assertThrows(IllegalArgumentException.class,()->frame.applyPreparedObjectSolidPublication(prepared));
            assertEquals(cut.slots(),pool.capture().slots(),"Preparation failure cannot publish pool writes");
        }
    }
    private static void ready(GameLoop loop,SonicCdGameModule module){
        for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);
        assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
    }
    private static boolean returned(SonicCdGameModule module){
        return module.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==CdFloatBlock.OBJECT_ID
                &&s.routine()!=0&&s.longBits(CdObjectState.LongField.MAPPINGS)==0x20A3FA
                &&s.unsignedByte(PRIORITY)==1&&s.unsignedByte(WIDTH)==12&&s.unsignedByte(Y_RADIUS)==12);
    }
    private static Object image(SonicCdGameModule module){
        var frame=module.originalR11EntryFrame();var world=module.originalR11WorldImage().orElseThrow();
        var placement=module.originalR11PlacementImage();
        return List.of(placement.status(),placement.pool().slots(),placement.savedFlags(),frame.captureActor(),frame.captureShared(),module.originalR11LoadingPrefix(),module.originalR11LoadingImage(),world.cpu(),world.sounds(),module.selectedProfileState(),module.programServicesState());
    }
}
