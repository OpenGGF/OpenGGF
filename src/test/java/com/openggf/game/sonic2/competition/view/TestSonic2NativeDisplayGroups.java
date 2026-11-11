package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.objects.MCZDrawbridgeObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2SignpostSparkleObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeDisplayGroups {
    @Test void drawbridgeUsesAllocatedAfterParentSlotAndEachGroupCullsAtItsOwnCentre() throws Exception {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            ObjectManager[] holder={null};
            var services=new StubObjectServices(){@Override public ObjectManager objectManager(){return holder[0];}};
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),services);
            holder[0]=manager;
            var bridge=new MCZDrawbridgeObjectInstance(new ObjectSpawn(100,160,0x81,0,0,false,0),"drawbridge");
            manager.addDynamicObjectAtSlot(bridge,40);
            var intervening=new Sonic2SignpostSparkleObjectInstance(new ObjectSpawn(8,8,0x25,0,0,false,0));
            manager.addDynamicObjectAtSlot(intervening,41);
            bridge.update(0,null);
            var groups=Sonic2CompetitionObjectDisplay.projectAll(bridge,null,()->fail("explicit source must not draw"));
            // Obj81_Init (s2.asm:57063-57115): AllocateObjectAfterCurrent skips occupied slot41;
            // parent width8 / empty frame0, child sub6 centre, eight log-frame1 children, width/height64.
            assertEquals(List.of(40,42),groups.stream().map(Sonic2NativeDisplaySource.DisplaySlot::slot).toList());
            assertEquals(List.of(5,5),groups.stream().map(Sonic2NativeDisplaySource.DisplaySlot::priorityBucket).toList());
            assertNull(groups.getFirst().occupant().multiSprite());
            var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
            assertEquals(0,rom.readU16BE(0x2A24E+rom.readS16BE(0x2A24E)),"ROM parent frame0 is empty");
            var child=groups.getLast().occupant();
            assertEquals(8,child.multiSprite().children().size());
            for(int i=0;i<8;i++) assertEquals(new Child(100,160-(i+1)*16,1),child.multiSprite().children().get(i));
            assertEquals(100,child.x()); assertEquals(80,child.y());
            assertEquals(64,child.multiSprite().width());assertEquals(64,child.multiSprite().height());
            var admissions=new java.util.ArrayList<Admission>();
            for(int i=0;i<groups.size();i++) {var g=groups.get(i);admissions.add(new Admission(g.slot(),g.priorityBucket(),i,g.occupant()));}
            var hud=new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0);
            var empty=new RingWindow(List.of());
            // At x=150, parent dx=-50 exceeds width8, child dx=-50 is still inside width64.
            var input=new Build(new Sonic2CompetitionDisplayInputs.Camera(150,0),
                    new Sonic2CompetitionDisplayInputs.Camera(150,0),hud,empty,empty,0,admissions,false);
            var page=new Sonic2CompetitionSatBuilder(RomByteReader.fromRom(TestEnvironment.currentRom()))
                    .buildPlayer1(input);
            assertEquals(List.of(new Visibility(40,false),new Visibility(42,true)),page.visibility());
            assertEquals(10,page.spriteCount(),"two native mask sprites plus eight child logs");
            var progressive = Sonic2CompetitionFieldSprites.project(page);
            assertEquals(page.entries().size(), progressive.size());
            for (int index = 0; index < progressive.size(); index++) {
                var nativeEntry = page.entries().get(index);
                var piece = progressive.get(index);
                assertEquals(nativeEntry.y(), piece.geometry().piece().y(), "native full-resolution Y");
                assertEquals(nativeEntry.heightCells() * 16,
                        piece.geometry().piece().heightTiles() * 8, "ROM 8x16 cell footprint");
                assertEquals(nativeEntry.widthCells() * nativeEntry.heightCells() * 2, piece.fragments().size());
            }
            var registry=new com.openggf.game.rewind.RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            var saved=registry.capture();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.removeDynamicObject(bridge);
            registry.restore(saved);
            var restored=manager.getActiveObjects().stream().filter(MCZDrawbridgeObjectInstance.class::isInstance)
                    .map(MCZDrawbridgeObjectInstance.class::cast).findFirst().orElseThrow();
            assertNotSame(bridge,restored);
            assertEquals(groups,restored.nativeDisplaySlots(),"actual child reservation and geometry survive destructive restore");
        } finally {graphics.resetState();}
    }
    @Test void bridgeProjectsActualChildSlotsInNativeOrderAndCullsEachGroupAtItsRomCentre() throws Exception {
        var r=RomByteReader.fromRom(TestEnvironment.currentRom());
        assertArrayEquals(java.util.HexFormat.of().parseHex("3004d040d04430310010514033400008"),r.slice(0xF716,16));
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            for(int subtype:new int[]{8,10,12,16}) {
                ObjectManager[] holder={null};
                var services=new StubObjectServices(){
                    @Override public ObjectManager objectManager(){return holder[0];}
                    @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->null,List::of);}
                };
                var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),services);holder[0]=manager;
                var bridge=new com.openggf.game.sonic2.objects.BridgeObjectInstance(new ObjectSpawn(200,80,0x11,subtype,0,false,0),"bridge");
                manager.addDynamicObjectAtSlot(bridge,40);
                manager.addDynamicObjectAtSlot(new Sonic2SignpostSparkleObjectInstance(new ObjectSpawn(8,8,0x25,0,0,false,0)),41);
                bridge.update(0,null);
                assertTrue(Sonic2CompetitionObjectDisplay.projectAll(bridge,null,()->fail("parent must not draw")).isEmpty());
                var groups=manager.getActiveObjects().stream()
                        .filter(com.openggf.game.sonic2.objects.BridgeSegmentObjectInstance.class::isInstance)
                        .map(com.openggf.game.sonic2.objects.BridgeSegmentObjectInstance.class::cast)
                        .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                        .flatMap(o->Sonic2CompetitionObjectDisplay.projectAll(o,null,()->fail("child must not use ordinary drawing")).stream()).toList();
                assertEquals(subtype==8?List.of(42):List.of(42,43),groups.stream().map(Sonic2NativeDisplaySource.DisplaySlot::slot).toList());
                int left=200-(subtype/2)*16;
                for(int group=0;group<groups.size();group++) {
                    var g=groups.get(group);var display=g.occupant();int count=group==0?8:subtype-8,first=left+group*128;
                    assertEquals(3,g.priorityBucket());assertEquals(0x44,display.renderFlags());
                    assertEquals(first+(count/2)*16-8,display.x());assertEquals(80,display.y());
                    assertEquals(count,display.multiSprite().children().size());assertEquals(64,display.multiSprite().width());
                    assertEquals(0,display.multiSprite().height());assertEquals(0,display.multiSprite().mainFrame());
                    assertEquals(0xFC70,display.mapping().address());assertEquals(0x41DB,display.artTile());
                    for(int i=0;i<count;i++)assertEquals(new Child(first+i*16,80,0),display.multiSprite().children().get(i));
                }
                if(subtype==16) {
                    var admissions=new java.util.ArrayList<Admission>();
                    for(int i=0;i<groups.size();i++){var g=groups.get(i);admissions.add(new Admission(g.slot(),g.priorityBucket(),i,g.occupant()));}
                    var hud=new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0);var empty=new RingWindow(List.of());
                    var input=new Build(new Sonic2CompetitionDisplayInputs.Camera(200,0),new Sonic2CompetitionDisplayInputs.Camera(200,0),hud,empty,empty,0,admissions,false);
                    var page=new Sonic2CompetitionSatBuilder(r).buildPlayer1(input);
                    assertEquals(List.of(new Visibility(42,false),new Visibility(43,true)),page.visibility());
                    assertEquals(10,page.spriteCount(),"only the admitted eight-log group plus the two native masks");
                    var registry=new com.openggf.game.rewind.RewindRegistry();registry.register(manager.rewindSnapshottable());var saved=registry.capture();
                    manager.setRewindInPlaceRestoreEnabledForTest(false);manager.removeDynamicObject(bridge);registry.restore(saved);
                    var restored=manager.getActiveObjects().stream().filter(com.openggf.game.sonic2.objects.BridgeSegmentObjectInstance.class::isInstance)
                            .map(com.openggf.game.sonic2.objects.BridgeSegmentObjectInstance.class::cast)
                            .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                            .flatMap(o->o.nativeDisplaySlots().stream()).toList();
                    assertEquals(groups,restored,"actual child slots and native geometry survive destructive restore");
                }
            }
        } finally {graphics.resetState();}
    }
    @Test void drawbridgeParentWidthFollowsRomCompletionWrite() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        // loc_2A154 (s2.asm:57141-57150) writes width64 on the completion execution.
        assertArrayEquals(java.util.HexFormat.of().parseHex("117c00400019"),rom.slice(0x2A164,6));
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        var triggers=com.openggf.game.sonic2.ButtonVineTriggerManager.snapshot();
        try {
            com.openggf.game.sonic2.ButtonVineTriggerManager.reset();
            ObjectManager[] holder={null};
            var services=new StubObjectServices(){@Override public ObjectManager objectManager(){return holder[0];}};
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),services);holder[0]=manager;
            var bridge=new MCZDrawbridgeObjectInstance(new ObjectSpawn(100,160,0x81,0,0,false,0),"drawbridge");
            manager.addDynamicObjectAtSlot(bridge,40);bridge.update(0,null);
            assertEquals(8,bridge.nativeDisplaySlots().getFirst().occupant().width());
            com.openggf.game.sonic2.ButtonVineTriggerManager.setTrigger(0,true);
            // Obj81_BridgeUp checks angle before adding $100 to its angle word.
            for(int i=0;i<64;i++)bridge.update(i+1,null);
            assertEquals(8,bridge.nativeDisplaySlots().getFirst().occupant().width());
            bridge.update(65,null);
            var groups=bridge.nativeDisplaySlots();
            assertEquals(64,groups.getFirst().occupant().width());
            assertEquals(172,groups.getFirst().occupant().x());assertEquals(160,groups.getFirst().occupant().y());
            assertEquals(180,groups.getLast().occupant().x());assertEquals(160,groups.getLast().occupant().y());
        } finally {com.openggf.game.sonic2.ButtonVineTriggerManager.restore(triggers);graphics.resetState();}
    }
    @Test void defaultSingleOwnerProjectionRetainsSameObjectAndAllocatedIdentity() {
        var owner=new Sonic2SignpostSparkleObjectInstance(new ObjectSpawn(100,80,0x25,0,0,false,0));
        owner.setSlotIndex(51);
        var single=Sonic2CompetitionObjectDisplay.project(owner,null,()->fail("must not draw"));
        var all=Sonic2CompetitionObjectDisplay.projectAll(owner,null,()->fail("must not draw"));
        assertEquals(List.of(new Sonic2NativeDisplaySource.DisplaySlot(51,2,single)),all);
    }
}
