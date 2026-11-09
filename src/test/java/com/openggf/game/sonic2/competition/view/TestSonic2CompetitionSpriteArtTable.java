package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Raw Init operands, independently read from the shipped REV01; no disassembly assets at runtime. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionSpriteArtTable {
    @Test void everyAdoptedRowHasItsInitMappingArtWidthFlagsAndBucketInRom() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        for(var row:java.util.stream.Stream.concat(Sonic2CompetitionSpriteArtTable.captureRows().stream(),
                Sonic2CompetitionSpriteArtTable.fixedRoleRows().stream()).toList()) {
            try { verifyRow(rom,row); }
            catch(AssertionError failure) { throw new AssertionError(row.role()+": "+failure.getMessage(),failure); }
        }
    }
    @Test void actualMultiSpriteChildInitFieldsAreRomOperands() throws Exception {
        var r=RomByteReader.fromRom(TestEnvironment.currentRom());
        for(var row:Sonic2CompetitionSpriteArtTable.MULTI_ROWS) {
            if(row.id()==0x11) {
                has(r,0xF728,0xF77A,"136800010001"); // copy render_flags
                has(r,0xF728,0xF77A,"08e900060001");
                has(r,0xF728,0xF77A,"137c0040000e");
                assertEquals(0x44,row.renderFlags());assertEquals(64,row.width());
                assertEquals(0,row.height());assertEquals(0,row.mainFrame());assertEquals(0,row.childFrame());
                assertEquals(3,row.bucket());assertEquals(0xFC70,row.mapping());assertEquals(0x43B6,row.artTile1P());
                continue;
            }
            int start=switch(row.id()){case 0x15->0xFD96;case 0x75->0x28C64;case 0x81->0x2A09C;default->0x37F74;};
            int end=switch(row.id()){case 0x15->0xFE30;case 0x75->0x28CCA;case 0x81->0x2A0FE;default->0x37FE8;};
            has(r,start,end,"137c%04x000e".formatted(row.width()));
            has(r,start,end,"137c%04x0014".formatted(row.height()));
            has(r,start,end,"08e900060001");
            has(r,start,end,"08e900040001");
            assertEquals(0x54,row.renderFlags());
            int entry=switch(row.id()){case 0x15->0xFC9C;case 0x75->0x28BC8;case 0x81->0x2A000;default->0x37F6C;};
            has(r,entry,entry+(row.id()==0x9E?8:0x2E),"303c%04x".formatted(row.bucket()*128));
            if(row.id()!=0x81) has(r,start,end,"137c%04x000b".formatted(row.mainFrame()));
            var parent=Sonic2CompetitionSpriteArtTable.captureRows().stream()
                    .filter(p->p.id()==row.id() && p.display().mappingsAddress()==row.mapping()).findFirst().orElseThrow();
            assertEquals(parent.display().artTile1P(),row.artTile1P());
            if(row.id()==0x9E) has(r,start,end,"137c%04x000f".formatted(row.fixedCount()));
            if(row.id()==0x81) has(r,start,end,"72%02x".formatted(row.fixedCount()));
        }
    }

    @Test void reachedSwingAndBrickSelectorsRetainParentVersusChildVariants() {
        for(int subtype:new int[]{0x18,0x38,0x48}) {
            var rows=Sonic2CompetitionSpriteArtTable.captureRows().stream()
                    .filter(r->r.id()==0x15 && r.accepts(subtype)).toList();
            assertEquals(1,rows.size());
            assertEquals(subtype==0x48?0x102DE:0x10256,rows.getFirst().display().mappingsAddress());
        }
        for(int subtype:new int[]{0x0F,0x16,0x17}) {
            var rows=Sonic2CompetitionSpriteArtTable.captureRows().stream()
                    .filter(r->r.id()==0x75 && r.accepts(subtype)).toList();
            assertEquals(1,rows.size());assertEquals(subtype==0x0F?4:5,rows.getFirst().priorityBucket());
        }
    }

    @Test void sharedSpringDataDoesNotTurnTheSecondPointerTableIntoPrimaryFrames() throws Exception {
        var rom=TestEnvironment.currentRom();var r=RomByteReader.fromRom(rom);
        // Obj41_MapUnc_1901C / Obj41_MapUnc_19032 (s2.asm:34486-34514): each declares eleven entries.
        assertEquals(0x19032-0x1901C,r.readU16BE(0x19032));
        assertEquals(2*(0x19032-0x1901C),r.readU16BE(0x1901C));
        assertEquals(11,Sonic2CompetitionSpriteArtTable.nativeFrameLimit(0x1901C));
        assertEquals(11,Sonic2CompetitionSpriteArtTable.nativeFrameLimit(0x19032));
        var sheet=new com.openggf.game.sonic2.Sonic2ObjectArt(rom,r).loadSpringVerticalSheet();
        var renderer=new com.openggf.level.render.PatternSpriteRenderer(sheet,new com.openggf.graphics.GraphicsManager());
        var art=org.mockito.Mockito.mock(com.openggf.game.ObjectArtProvider.class);
        org.mockito.Mockito.when(art.getRenderer("spring_vertical")).thenReturn(renderer);
        var owner=new SpringFrameProbe();
        for(int frame=0;frame<11;frame++) {
            final int f=frame;
            assertEquals(frame,Sonic2CompetitionObjectDisplay.project(owner,art,
                    ()->renderer.drawFrameIndex(f,0,0)).mapping().frame());
        }
        assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.project(owner,art,
                ()->renderer.drawFrameIndex(11,0,0)));
    }
    private static final class SpringFrameProbe extends com.openggf.level.objects.AbstractObjectInstance {
        SpringFrameProbe(){super(new com.openggf.level.objects.ObjectSpawn(0,0,0x41,0,0,false,0),"spring-frame-probe");}
        @Override public void update(int vInt,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static void verifyRow(RomByteReader r,Sonic2CompetitionSpriteArtTable.Row row) {
        var e=row.display(); int id=row.id(), start, end;
        if(id==0x08) {
            int init=0x1DD36,endInit=0x1DD90;
            has(r,init,endInit,"217c%08x0004".formatted(e.mappingsAddress()));
            has(r,init,endInit,"317c%04x0002".formatted(e.artTile1P()));
            has(r,init,endInit,"117c%04x0019".formatted(e.widthPixels()));
            has(r,init,endInit,"117c%04x0018".formatted(row.priorityBucket()));
            has(r,init,endInit,"002800040001");
            assertEquals(4,e.renderFlagsInit());assertEquals(16,e.widthPixels());return;
        }
        if(id==0x35) {
            int init=0x1D9A4;
            assertEquals(e.mappingsAddress(),r.readU32BE(init+24));
            assertEquals(e.artTile1P(),r.readU16BE(init+32));
            has(r,init,0x1DA0C,"137c0010000e");
            has(r,init,0x1DA0C,"137c0002000f");
            has(r,init,0x1DA0C,"137c00040001");
            has(r,init,0x1DA0C,"08e900060001");
            has(r,0x1DA0C,0x1DA80,"303c0080");
            assertEquals(16,e.widthPixels());assertEquals(0x44,e.renderFlagsInit());assertEquals(1,row.priorityBucket());return;
        }
        if(id==0x1C) {
            // Obj1C_InitData subtype2 (s2.asm:24023-24026): objsubdecl packs frame in pointer high byte.
            int p=0x111E6+2*8;
            assertEquals(e.mappingsAddress(),r.readU32BE(p)&0xFFFFFFL);
            assertEquals(e.artTile1P(),r.readU16BE(p+4)); assertEquals(e.widthPixels(),r.readU8(p+6));
            assertEquals(row.priorityBucket(),r.readU8(p+7)); assertEquals(4,e.renderFlagsInit()); return;
        }
        if(id==0x9D || id==0x98 || id==0x9E || id==0xA3 || id==0xC8) {
            // LoadSubObject's ten-byte rows: mapping, art, flags, priority, width, collision.
            int p=switch(id) {case 0x9D->0x37D7E;case 0x98->0x37782;case 0x9E->0x37FE8;
                case 0xA3->0x388AC;default->0x3D440;};
            assertEquals(e.mappingsAddress(),r.readU32BE(p)); assertEquals(e.artTile1P(),r.readU16BE(p+4));
            assertEquals(e.renderFlagsInit(),r.readU8(p+6));assertEquals(row.priorityBucket(),r.readU8(p+7));
            assertEquals(e.widthPixels(),r.readU8(p+8));return;
        }
        if(id==0x28) {
            int set=Integer.parseInt(row.role().split("-")[1]);
            int variant=Integer.parseInt(row.role().split("-")[2]);
            int[] properties={1,6,4,3,0}; // A chicken, B squirrel, C pig, D seal, E rabbit.
            assertEquals(e.mappingsAddress(),r.readU32BE(0x118F0+properties[set]*8+4));
            has(r,0x11A2C,0x11AA0,"317c%04x0002".formatted(e.artTile1P()));
            has(r,0x11A2C,0x11AA0,"117c%04x0019".formatted(e.widthPixels()));
            has(r,0x11A2C,0x11AA0,"117c%04x0018".formatted(row.priorityBucket()));
            has(r,0x11A2C,0x11AA0,"117c%04x0001".formatted(e.renderFlagsInit()));
            assertEquals(4,e.renderFlagsInit());assertEquals(variant==0?0x580:0x594,e.artTile1P());return;
        }
        if(id==0x3E) {
            int rowIndex=row.role().endsWith("body")?0:row.role().endsWith("button")?1:2;
            int p=0x3F1FE+rowIndex*5;
            assertEquals(e.widthPixels(),r.readU8(p+2));assertEquals(row.priorityBucket(),r.readU8(p+3));
            has(r,0x3F228,0x3F272,"237c%08x0004".formatted(e.mappingsAddress()));
            has(r,0x3F228,0x3F272,"337c%04x0002".formatted(e.artTile1P()));
            assertEquals(e.renderFlagsInit(),r.readU16BE(0x3F250));return;
        }
        int[] span=switch(id) {
            case 0x11->new int[]{0xF694,0xF6F8}; case 0x0D->new int[]{0x191DA,0x1924C}; case 0x15->new int[]{0xFCCA,0xFE50};
            case 0x18->new int[]{0x104CE,0x1056A};case 0x1F->new int[]{0x10A24,0x10AD6};
            case 0x25->new int[]{0x19398,0x19406};case 0x26->new int[]{0x12694,0x12710};
            case 0x2A->new int[]{0x115D6,0x11610};case 0x2E->new int[]{0x1286C,0x128EC};
            case 0x38->new int[]{0x1D904,0x1D92C};case 0x36->new int[]{0x15926,0x15996};case 0x40->new int[]{0x2638C,0x26410};
            case 0x41->new int[]{0x188A8,0x1897C};case 0x49->new int[]{0x20BB0,0x20BEA};
            case 0x4B->new int[]{0x2D0C8,0x2D174};case 0x5C->new int[]{0x2D3AA,0x2D3E0};
            case 0x6A->new int[]{0x27AD2,0x27BD0};case 0x75->new int[]{0x28BEE,0x28CCA};
            case 0x77->new int[]{0x28F9A,0x28FC0};case 0x79->new int[]{0x1F0CC,0x1F1C0};
            case 0x7F->new int[]{0x297F6,0x29830};case 0x80->new int[]{0x29990,0x29A66};
            case 0x81->new int[]{0x2A026,0x2A0FE};case 0x86->new int[]{0x2B158,0x2B1AA};
            case 0xD2->new int[]{0x2B53A,0x2B59A};case 0xD4->new int[]{0x2B8FE,0x2B94A};
            case 0x44->new int[]{0x1F742,0x1F770};case 0xD6->new int[]{0x2BB7E,0x2BBA6};
            case 0xD8->new int[]{0x2C6C0,0x2C6FC};case 0xD3->new int[]{0x2BC86,0x2BD48};case 0xDC->new int[]{0x2BD4E,0x2BDF6};
            case 0xD5->new int[]{0x2BA1A,0x2BA66};case 0xD7->new int[]{0x2C45A,0x2C4AC};
            default->throw new AssertionError("Uncertified Init: "+row.role());
        }; start=span[0];end=span[1];
        if(id!=0x2E) {
            boolean second=id==0x25 || id==0xD3 || id==0xDC;
            has(r,start,end,(second?"237c":"217c")+"%08x0004".formatted(e.mappingsAddress()));
        } else has(r,start,end,"227c%08x".formatted(e.mappingsAddress()));
        if(id==0x41) {
            int palette=(row.subtypeValue()&2)!=0?0x2000:0;
            has(r,start,end,"317c%04x0002".formatted(e.artTile1P()^palette));
            if(palette!=0) has(r,start,end,"08e800050002");
        } else has(r,start,end,((id==0x25 || id==0xD3 || id==0xDC)?"337c":"317c")+"%04x0002".formatted(e.artTile1P()));
        if(id==0x36) {
            int index=row.subtypeValue()>>>4;
            assertEquals(e.widthPixels(),r.readU8(0x15916+index*2));
            assertEquals(e.yRadius(),r.readU8(0x15917+index*2));
        } else if(id==0x18) assertEquals(e.widthPixels(),r.readU8(0x104C4));
        else has(r,start,end,((id==0x25 || id==0xD3 || id==0xDC)?"137c":"117c")+"%04x0019".formatted(e.widthPixels()));
        if(row.role().equals("checkpoint-dongle"))
            has(r,0x1F180,0x1F206,"137c%04x0018".formatted(row.priorityBucket()));
        else if(id==0x77) assertEquals(0,row.priorityBucket()); // cleared SST: Init has no priority write.
        else has(r,start,end,((id==0x25 || id==0xD3 || id==0xDC)?"137c":"117c")+"%04x0018".formatted(row.priorityBucket()));
        int baseFlags=e.renderFlagsInit()&~0x10;
        if(id==0x2E) baseFlags=e.renderFlagsInit();
        String suffix=(id==0x25 || id==0xD3 || id==0xDC)?"0001":"0001";
        if(!contains(r,start,end,((id==0x25 || id==0xD3 || id==0xDC)?"137c":"117c")+"%04x".formatted(baseFlags)+suffix))
            has(r,start,end,"0028%04x0001".formatted(baseFlags));
        if((e.renderFlagsInit()&0x10)!=0) {
            has(r,start,end,"08e800040001");
            has(r,start,end,"117c%04x0016".formatted(e.yRadius()));
        }
    }
    private static void has(RomByteReader r,int start,int end,String hex) {
        assertTrue(contains(r,start,end,hex),"Init [$"+Integer.toHexString(start)+",$"+Integer.toHexString(end)+") lacks "+hex);
    }
    private static boolean contains(RomByteReader r,int start,int end,String hex) {
        byte[] needle=java.util.HexFormat.of().parseHex(hex);
        outer:for(int i=start;i+needle.length<=end;i+=2) {
            for(int j=0;j<needle.length;j++) if(r.readU8(i+j)!=(needle[j]&255)) continue outer;
            return true;
        }
        return false;
    }
}
