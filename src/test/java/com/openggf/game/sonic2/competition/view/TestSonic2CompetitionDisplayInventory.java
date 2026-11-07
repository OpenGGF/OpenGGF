package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native layout coverage is an inventory, not route qualification or activation. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionDisplayInventory {
    @Test void admittedSheetsMatchEveryOrdinaryRomMappingPiece() throws Exception {
        var rom=TestEnvironment.currentRom();
        var reader=RomByteReader.fromRom(rom);
        var art=new com.openggf.game.sonic2.Sonic2ObjectArt(rom,reader);
        for(var row:Sonic2CompetitionSpriteArtTable.entries().entrySet()) {
            var sheet=row.getKey()==0x27 ? art.loadExplosionSheet() : art.loadPointsSheet();
            assertNotNull(sheet);
            var graphics=new RecordingGraphics();
            var renderer=new com.openggf.level.render.PatternSpriteRenderer(sheet,graphics);
            renderer.ensurePatternsCached(graphics,512);
            var renderManager=mock(com.openggf.level.objects.ObjectRenderManager.class);
            when(renderManager.getExplosionRenderer()).thenReturn(renderer);
            when(renderManager.getPointsRenderer()).thenReturn(renderer);
            var services=new com.openggf.level.objects.StubObjectServices() {
                @Override public com.openggf.level.objects.ObjectRenderManager renderManager(){return renderManager;}
            };
            com.openggf.level.objects.AbstractObjectInstance owner=row.getKey()==0x27
                    ? new com.openggf.level.objects.ExplosionObjectInstance(0x27,100,80,renderManager)
                    : new com.openggf.game.sonic2.objects.PointsObjectInstance(
                            new ObjectSpawn(100,80,0x29,0,0,false,0),services,100);
            var provider=mock(com.openggf.game.ObjectArtProvider.class);
            when(provider.getRenderer(row.getValue().expectedArtKey())).thenReturn(renderer);
            var slot=Sonic2CompetitionObjectDisplay.project(owner,provider,
                    ()->owner.appendRenderCommands(new ArrayList<>()));
            assertNotNull(slot,"The actual spawned generic owner must emit one mapping frame");
            assertEquals(row.getValue().mappingsAddress(),slot.mapping().address());
            assertEquals(0,slot.mapping().frame());
            assertEquals(100,slot.x()); assertEquals(80,slot.y());
            for(int frame=0;frame<sheet.getFrameCount();frame++) {
                final int mappingFrame=frame;
                graphics.tiles.clear();
                renderer.drawFrameIndex(frame,100,80,true,false);
                var before=List.copyOf(graphics.tiles);
                var captured=com.openggf.level.render.SpriteFrameCaptureInternalAccess.capture(
                        ()->renderer.drawFrameIndex(mappingFrame,100,80,true,false));
                assertEquals(before,graphics.tiles,"Capture emits no pieces on the real S2 sheet");
                assertEquals(1,captured.calls().size());
                graphics.tiles.clear();
                renderer.drawFrameIndex(frame,100,80,true,false);
                assertEquals(before,graphics.tiles,"Inactive capture preserves actual S2 sheet output");
                int base=row.getValue().mappingsAddress();
                int addr=base+reader.readS16BE(base+frame*2);
                var pieces=sheet.getFrame(frame).pieces();
                assertEquals(reader.readU16BE(addr),pieces.size());
                for(int i=0;i<pieces.size();i++) {
                    int p=addr+2+i*8;
                    var piece=pieces.get(i);
                    int size=reader.readU8(p+1), word=reader.readU16BE(p+2);
                    assertEquals((byte)reader.readU8(p),piece.yOffset());
                    assertEquals(reader.readS16BE(p+6),piece.xOffset());
                    assertEquals((size>>2)+1,piece.widthTiles());
                    assertEquals((size&3)+1,piece.heightTiles());
                    assertEquals(word&2047,piece.tileIndex());
                    assertEquals((word&0x800)!=0,piece.hFlip());
                    assertEquals((word&0x1000)!=0,piece.vFlip());
                    assertEquals((word>>13)&3,piece.paletteIndex());
                    assertEquals((word&0x8000)!=0,piece.priority());
                }
            }
        }
    }

    private static final class RecordingGraphics extends com.openggf.graphics.GraphicsManager {
        final List<String> tiles=new ArrayList<>();
        @Override public void cachePatternTexture(com.openggf.level.Pattern pattern,int index) { }
        @Override public void renderPatternWithId(int index,com.openggf.level.PatternDesc desc,int x,int y) {
            tiles.add(index+":"+desc.get()+":"+x+":"+y);
        }
    }

    @Test void allSixNativeLayoutsAndReachableChildrenAreAccountedFor() throws Exception {
        var placement=new Sonic2ObjectPlacement(RomByteReader.fromRom(TestEnvironment.currentRom()));
        var registry=new Sonic2ObjectRegistry();
        var observed=new TreeSet<Integer>();
        var explicit=new TreeSet<Integer>();
        for(int zone:new int[]{0,11,12}) for(int act=0;act<2;act++) {
            var level=mock(com.openggf.level.Level.class);
            when(level.getZoneIndex()).thenReturn(zone);
            var services=new com.openggf.level.objects.StubObjectServices() {
                @Override public com.openggf.level.Level currentLevel(){return level;}
            };
            var spawns=placement.load(new ZoneAct(zone,act),true);
            assertFalse(spawns.isEmpty());
            var ids=new TreeSet<Integer>();
            for(var spawn:spawns) {
                ids.add(spawn.objectId());
                // Construct every placement subtype: no updates, fallback draw or fake lifecycle.
                var owner=com.openggf.level.objects.ObjectConstructionContext.construct(services,
                        ()->registry.create(spawn));
                assertNotNull(owner);
                if(owner instanceof Sonic2NativeDisplaySource) explicit.add(spawn.objectId());
                assertTrue(owner instanceof Sonic2NativeDisplaySource
                                || Sonic2CompetitionSpriteArtTable.entry(spawn.objectId())!=null
                                || Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.containsKey(spawn.objectId()),
                        ()->String.format("Unaccounted native placement $%02X",spawn.objectId()));
            }
            observed.addAll(ids);
            System.out.printf("DISPLAY_INVENTORY zone=%d act=%d count=%d ids=%s%n",zone,act,spawns.size(),ids);
        }
        // Death/monitor paths: Obj37 lost rings; Obj27 explosion; Obj28 animals;
        // Obj29 points; Obj2E monitor contents. Broken parts reuse their parent id.
        // loc_19398 (s2.asm:34772-34799): signpost sparkle Obj25.
        // Obj9D_CreateCoconut (s2.asm:75366-75386): Obj98, not the engine helper's parent ID.
        // loc_2BC86 (s2.asm:59221-59262), loc_2BD4E (s2.asm:59272-59313): slot prize children.
        // Obj0D_Main_State2 (s2.asm:34759-34770) bypasses Obj3A in 2P. Retain the separate
        // capsule path conservatively: loc_3F406 (s2.asm:85128-85139) calls Load_EndOfAct.
        for(int id:new int[]{0x25,0x27,0x28,0x29,0x2E,0x37,0x3A,0x98,0xD3,0xDC}) {
            observed.add(id);
            var childSpawn=new ObjectSpawn(100,80,id,0,0,false,0);
            // The actual generic explosion and points owners are constructed and compared above.
            var child=switch(id) {
                case 0x27,0x29 -> null;
                case 0x25 -> new com.openggf.level.objects.SignpostSparkleObjectInstance(100,80);
                case 0x28 -> new com.openggf.level.objects.AnimalObjectInstance(
                        childSpawn,new com.openggf.level.objects.StubObjectServices());
                case 0x2E -> com.openggf.level.objects.ObjectConstructionContext.construct(
                        new com.openggf.level.objects.StubObjectServices(),()->registry.create(childSpawn));
                case 0x37 -> com.openggf.level.rings.LostRingObjectInstance.create(childSpawn);
                case 0x3A -> new com.openggf.game.sonic2.objects.ResultsScreenObjectInstance(0,0,1,false);
                case 0x98 -> new com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance(
                        childSpawn,com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance.ProjectileType.COCONUT,
                        100,80,0,-0x100,true,false);
                case 0xD3 -> new com.openggf.game.sonic2.objects.BombPrizeObjectInstance(100,80,100,80,30,new int[]{1});
                case 0xDC -> new com.openggf.game.sonic2.objects.RingPrizeObjectInstance(100,80,100,80,26,new int[]{1});
                default -> throw new IllegalArgumentException("Unclassified native child");
            };
            if(id!=0x27 && id!=0x29) assertNotNull(child);
            if(child instanceof Sonic2NativeDisplaySource) explicit.add(id);
            assertTrue(child instanceof Sonic2NativeDisplaySource
                    || Sonic2CompetitionSpriteArtTable.entry(id)!=null
                    || Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.containsKey(id));
        }
        var classified=new TreeSet<>(Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.keySet());
        classified.addAll(Sonic2CompetitionSpriteArtTable.entries().keySet());
        classified.addAll(explicit);
        assertEquals(observed,classified,"No silently unclassified IDs or stale pending entries");
        assertEquals(52,observed.size(),"Native layout plus child ID inventory");
        Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(row->System.out.printf("PENDING_ADOPTION $%02X | %s | %s | %s%n",row.getKey(),
                        row.getValue().name(),row.getValue().initCitation(),row.getValue().reason()));
    }
}
