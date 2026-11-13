package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.objects.CNZRectBlocksObjectInstance;
import com.openggf.game.sonic2.objects.MCZRotPformsObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeDisplayPhases {
    @Test void rectangularBlocksUseDisplayCallsAndLiveRomWidthsRatherThanTheOrdinaryVisibilityPredicate() throws Exception {
        var r=RomByteReader.fromRom(TestEnvironment.currentRom());
        // ObjD2_Main (s2.asm:58718-58734): countdown branches to MarkObjGone3.
        bytes(r,0x2B57E,"4a680038670853680038600002aa");
        // loc_2B5E2 (s2.asm:58748-58750): wrap branches directly to the position helper.
        bytes(r,0x2B5E2,"3168003000086022");
        // loc_2B60C (s2.asm:58768-58794): frame's byte width becomes width_pixels.
        bytes(r,0x2B64A,"11410019314200364e75");
        for(int flip=0;flip<2;flip++) {
            var owner=new CNZRectBlocksObjectInstance(new ObjectSpawn(200,100,0xD2,1,flip,false,0),"rect");
            assertNull(owner.nativeDisplaySlot());
            for(int tick=0;tick<16;tick++) {owner.update(tick,null);assertNull(owner.nativeDisplaySlot());}
            assertFalse(owner.isInvisible(),"legacy ordinary visibility has already changed on the last hidden execution");
            for(int frame=1;frame<16;frame++) for(int held=0;held<16;held++) {
                owner.update(16+(frame-1)*16+held,null);
                var display=owner.nativeDisplaySlot(); assertNotNull(display);
                int data=0x2B654+frame*4;
                assertEquals(frame,display.mapping().frame());assertEquals(0x2B694,display.mapping().address());
                assertEquals(r.readU8(data+2),display.width());
                int dx=(byte)r.readU8(data);
                assertEquals(200+(flip==0?dx:-dx),display.x());
                assertEquals(100+(byte)r.readU8(data+1),display.y());
                assertEquals(4|flip,display.renderFlags());assertEquals(0x41BE,display.artTile());
            }
            owner.update(256,null);assertNull(owner.nativeDisplaySlot(),"wrap executes only the position helper");
            assertTrue(owner.isInvisible());
            for(int tick=0;tick<16;tick++) {owner.update(257+tick,null);assertNull(owner.nativeDisplaySlot());}
            owner.update(273,null);
            assertNotNull(owner.nativeDisplaySlot());assertEquals(0,owner.nativeDisplaySlot().mapping().frame());
        }
    }
    @Test void mczCrateChildrenUseActualSlotsAndDoNotInheritParentRenderFlags() throws Exception {
        var r=RomByteReader.fromRom(TestEnvironment.currentRom());
        // Obj6A_InitSubObject (s2.asm:54213-54220): full helper copies status, never render_flags.
        bytes(r,0x27B9E,"1368000000003368000800083368000c000c3368000800323368000c00301368002200224e75");
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            ObjectManager[] holder={null};
            var services=new StubObjectServices(){
                @Override public ObjectManager objectManager(){return holder[0];}
                @Override public int currentZone(){return 11;}
            };
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),services);holder[0]=manager;
            var parent=new MCZRotPformsObjectInstance(new ObjectSpawn(200,100,0x6A,0x18,1,false,0),"crate");
            manager.addDynamicObjectAtSlot(parent,40);parent.update(0,null);
            var crates=manager.getActiveObjects().stream().filter(MCZRotPformsObjectInstance.class::isInstance)
                    .map(MCZRotPformsObjectInstance.class::cast).sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
            assertEquals(List.of(40,41,42),crates.stream().map(AbstractObjectInstance::getSlotIndex).toList());
            assertEquals(List.of(5,4,4),crates.stream().map(o->o.nativeDisplaySlot().renderFlags()).toList());
            assertEquals(List.of(200,264,136),crates.stream().map(o->o.nativeDisplaySlot().x()).toList());
            assertEquals(List.of(100,164,164),crates.stream().map(o->o.nativeDisplaySlot().y()).toList());
            for(var crate:crates) {
                var display=crate.nativeDisplaySlot();assertEquals(0x61EA,display.artTile());
                assertEquals(32,display.width());assertEquals(0x27D30,display.mapping().address());
            }
        } finally {graphics.resetState();}
    }
    @Test void rectangularDisplayRequestAndLiveWidthSurviveDestructiveRestore() {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),new StubObjectServices());
            var owner=new CNZRectBlocksObjectInstance(new ObjectSpawn(200,100,0xD2,1,0,false,0),"rect");
            manager.addDynamicObjectAtSlot(owner,40);
            for(int tick=0;tick<116;tick++)owner.update(tick,null);
            var savedDisplay=owner.nativeDisplaySlot();assertNotNull(savedDisplay);
            var registry=new com.openggf.game.rewind.RewindRegistry();registry.register(manager.rewindSnapshottable());var saved=registry.capture();
            var expected=new java.util.ArrayList<Sonic2CompetitionDisplayInputs.ObjectSlot>();
            for(int tick=116;tick<136;tick++){owner.update(tick,null);expected.add(owner.nativeDisplaySlot());}
            manager.setRewindInPlaceRestoreEnabledForTest(false);manager.removeDynamicObject(owner);registry.restore(saved);
            var restored=manager.getActiveObjects().stream().filter(CNZRectBlocksObjectInstance.class::isInstance)
                    .map(CNZRectBlocksObjectInstance.class::cast).findFirst().orElseThrow();
            assertNotSame(owner,restored);assertEquals(savedDisplay,restored.nativeDisplaySlot());
            for(int i=0;i<20;i++){restored.update(116+i,null);assertEquals(expected.get(i),restored.nativeDisplaySlot());}
        } finally {graphics.resetState();}
    }
    private static void bytes(RomByteReader r,int address,String hex){assertArrayEquals(java.util.HexFormat.of().parseHex(hex),r.slice(address,hex.length()/2));}
}
