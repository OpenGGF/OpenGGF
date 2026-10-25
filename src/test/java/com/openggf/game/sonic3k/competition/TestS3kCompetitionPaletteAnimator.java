package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native all-64-word fade, arena AnPal dispatch, ownership phase, and rewind oracles. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionPaletteAnimator {
    private Rom rom;
    private RomByteReader reader;
    @BeforeEach void loadRom() throws Exception {rom=RomManager.getInstance().getRom();reader=RomByteReader.fromRom(rom);}

    @Test void owningRomInstructionsSelectAll64WordsTwentyTwoCallsAndTheFiveArenaDispatches() {
        assertEquals(0x31FC,reader.readU16BE(0x665A));assertEquals(0x3F,reader.readU16BE(0x665C));assertEquals(0xF626,reader.readU16BE(0x665E));
        assertEquals(0x3B80,reader.readU32BE(0x6662));assertEquals(0x31FC,reader.readU16BE(0x6666));
        assertEquals(0x16,reader.readU16BE(0x6668));assertEquals(0xEE50,reader.readU16BE(0x666A));
        assertEquals(0x0200,reader.readU16BE(0x3B60));assertEquals(0x0020,reader.readU16BE(0x3B6E));assertEquals(0x5458,reader.readU16BE(0x3B78));
        int[] dispatch={0x21BC,0x281A,0x21BC,0x2884,0x28BA};
        for(int i=0;i<5;i++)for(int act=0;act<2;act++)
            assertEquals(dispatch[i],0x215C+(short)reader.readU16BE(0x215C+(0xE+i)*4+act*2));
        int[][] pointers={{0x2828,0x34CC},{0x285C,0x34DE},{0x2892,0x355C},{0x28C8,0x35AC},{0x28F6,0x35E8}};
        for(int[] pointer:pointers)assertEquals(pointer[1],reader.readU32BE(pointer[0]));
    }

    @Test void initialPassSeesOnlyTargetLineZeroThenArmingClearsAllSixtyFourCurrentWords() throws Exception {
        for(var arena:S3kCompetitionSession.Arena.values()) {
            var world=world(arena);int[] target=target(arena);
            for(int i=0;i<64;i++) {
                assertEquals(target[i],world.owner.targetColor(i/16,i%16));
                assertEquals(i<16?target[i]:0,world.owner.readCurrentColor(i/16,i%16));
                assertEquals(target[i],word(world.registry.targetSegaData(i/16,i%16,1),0));
            }
            assertEquals(0xFFFF,world.owner.fadeTimer());world.registry.beginFrame();
            world.owner.writeCurrentColor(2,14,0xCAFE);assertEquals(0xCAFE,world.owner.readCurrentColor(2,14));
            world.owner.armLevelFade();assertEquals(22,world.owner.fadeTimer());assertEquals(64,world.owner.lastNativeWrites().size());
            for(int line=0;line<4;line++)for(int color=0;color<16;color++)assertEquals(0,world.owner.readCurrentColor(line,color));
        }
    }

    @Test void fadeRunsTwentyTwoPassesBeforeEitherCycleCounterCanAdvance() throws Exception {
        var world=world(BALLOON_PARK);world.owner.armLevelFade();int[] expected=new int[64],target=target(BALLOON_PARK);
        for(int tick=1;tick<=22;tick++) {
            frame(world);
            for(int i=0;i<64;i++){expected[i]=oracleAdd(expected[i],target[i]);assertEquals(expected[i],world.owner.readCurrentColor(i/16,i%16),"fade tick "+tick+" word "+i);}
            var state=world.owner.capture();assertEquals(0,state.counter0());assertEquals(0,state.counter2());assertEquals(0,state.timer1());assertEquals(0,state.timer2());
            assertEquals(22-tick,state.fadeTimer());
        }
        assertArrayEquals(target,expected);frame(world);
        assertEquals(6,world.owner.capture().counter0());assertEquals(6,world.owner.capture().counter2());assertEquals(6,world.owner.lastNativeWrites().size());
        assertTable(world,2,13,0x34CC,3);assertTable(world,3,2,0x34DE,3);
    }

    @Test void unsignedWordFadeCarriesThroughHiddenRamBitsAndNeverClampsToRgbChannels() throws Exception {
        int[][] cases={{0,0xEEE,0x200},{0xE00,0xEEE,0xE20},{0xEE0,0xEEE,0xEE2},
                {0xA00,0x800,0xA02},{0xFE00,0,0},{0xFFE0,0,0},{0xFFFE,0,0},{0xE,0x20,0x10}};
        for(int[] c:cases)assertEquals(c[2],S3kCompetitionPaletteAnimator.addColor(c[0],c[1]));
        var world=world(AZURE_LAKE);int[] target=new int[64];target[42]=0x20;world.owner.prepareInitialPalette(target);world.owner.armLevelFade();
        world.registry.beginFrame();world.owner.writeCurrentColor(2,10,0xE);world.owner.update();
        assertEquals(0x10,world.owner.readCurrentColor(2,10));assertEquals(0,PaletteWriteSupport.segaWordFromColor(world.palettes[2].getColor(10)));
        var before=world.owner.capture();frame(world);assertEquals(0x12,world.owner.readCurrentColor(2,10));
        world.registry.beginFrame();world.owner.restore(before);world.owner.update();assertEquals(0x12,world.owner.readCurrentColor(2,10));
        assertEquals(0x20,world.owner.targetColor(2,10));
    }

    @Test void bpzBothWordTimersUseReadBeforeIncrementOffsetsAndTheirSeparateNativeWraps() throws Exception {
        var world=cycling(BALLOON_PARK);
        for(int frame=0;frame<379;frame++) {
            frame(world);assertTable(world,2,13,0x34CC+(frame/8%3)*6,3);assertTable(world,3,2,0x34DE+(frame/18%21)*6,3);
            assertEquals((frame%8==0?3:0)+(frame%18==0?3:0),world.owner.lastNativeWrites().size());
        }
        assertEquals(6,world.owner.capture().counter2(),"frame378 reads row0 after the21-row wrap");
    }

    @Test void cgzAdvancesFourColorsEveryTenPassesAndWrapsAfterTenRows() throws Exception {
        var world=cycling(CHROME_GADGET);
        for(int frame=0;frame<111;frame++) {
            frame(world);assertTable(world,2,2,0x355C+(frame/10%10)*8,4);
            assertEquals(frame%10==0?4:0,world.owner.lastNativeWrites().size());
            assertEquals(0,world.owner.capture().timer2());assertEquals(0,world.owner.capture().counter2());
        }
    }

    @Test void emzGlowReadsPastItsNamedTableAndBackgroundWrapExcludesItsLastDeclaredPair() throws Exception {
        var world=cycling(ENDLESS_MINE);
        for(int frame=0;frame<417;frame++) {
            frame(world);assertTable(world,2,14,0x35AC+4+(frame/8%30)*2,1);assertTable(world,3,9,0x35E8+(frame/32%13)*4,2);
            assertEquals((frame%8==0?1:0)+(frame%32==0?2:0),world.owner.lastNativeWrites().size());
            if(frame==232)assertEquals(reader.readU16BE(0x35EA),world.owner.readCurrentColor(2,14),"last glow row reads the next table");
        }
    }

    @Test void actualNativeWriteWinsAfterTheEmzButtonButIdlePassAndNoopArenasLeaveTheObjectOwner() throws Exception {
        var world=cycling(ENDLESS_MINE);world.registry.beginFrame();world.owner.writeCurrentColor(2,14,0xEEE);world.owner.update();
        assertEquals(reader.readU16BE(0x35B0),world.owner.readCurrentColor(2,14));
        assertEquals(S3kCompetitionPaletteAnimator.NATIVE_OWNER,world.registry.ownerAt(PaletteSurface.NORMAL,2,14));
        world.registry.beginFrame();world.owner.writeCurrentColor(2,14,0xEEE);world.owner.update();
        assertEquals(0xEEE,world.owner.readCurrentColor(2,14));assertTrue(world.owner.lastNativeWrites().isEmpty());
        assertEquals(S3kCompetitionPaletteAnimator.OBJECT_OWNER,world.registry.ownerAt(PaletteSurface.NORMAL,2,14));
        for(var arena:List.of(AZURE_LAKE,DESERT_PALACE)) {
            var idle=cycling(arena);idle.registry.beginFrame();idle.owner.writeCurrentColor(2,14,0x1234);idle.owner.update();
            assertEquals(0x1234,idle.owner.readCurrentColor(2,14));assertTrue(idle.owner.lastNativeWrites().isEmpty());
            assertEquals(S3kCompetitionPaletteAnimator.OBJECT_OWNER,idle.registry.ownerAt(PaletteSurface.NORMAL,2,14));
            assertEquals(0,idle.owner.capture().timer1());assertEquals(0,idle.owner.capture().timer2());
        }
    }

    @Test void precedingQueuedClaimsAreReadBeforeFadeAndEqualWordsDoNotGainAFalseNativeClaim() throws Exception {
        var world=world(AZURE_LAKE);int[] target=new int[64];target[32]=0xEEE;target[33]=0x222;world.owner.prepareInitialPalette(target);world.owner.armLevelFade();
        world.registry.beginFrame();world.registry.submit(PaletteWrite.normal("earlier.sst",200,2,0,new byte[]{0x0E,0,2,0x22}));world.owner.update();
        assertEquals(0xE20,world.owner.readCurrentColor(2,0));assertEquals(0x222,world.owner.readCurrentColor(2,1));
        assertEquals(S3kCompetitionPaletteAnimator.NATIVE_OWNER,world.registry.ownerAt(PaletteSurface.NORMAL,2,0));
        assertEquals("earlier.sst",world.registry.ownerAt(PaletteSurface.NORMAL,2,1));
        assertEquals(List.of(new S3kCompetitionPaletteAnimator.ColorWrite(2,0,0xE20)),world.owner.lastNativeWrites());
    }

    @Test void rawBanksCycleCountersAndQueueDescriptionsRecreateAndReplaySeventeenPasses() throws Exception {
        for(var arena:S3kCompetitionSession.Arena.values())for(boolean duringFade:new boolean[]{false,true}) {
            var world=world(arena);world.owner.armLevelFade();for(int i=0;i<(duringFade?7:47);i++)frame(world);
            var before=world.owner.capture();var ownershipBefore=world.registry.capture();
            for(int i=0;i<17;i++)frame(world);var forward=world.owner.capture();var ownershipForward=world.registry.capture();
            var fresh=world(arena);fresh.registry.restore(ownershipBefore);fresh.owner.restore(before);
            assertEquals(before,fresh.owner.capture());for(int i=0;i<17;i++)frame(fresh);
            assertEquals(forward,fresh.owner.capture());assertArrayEquals(ownershipForward.owners(),fresh.registry.capture().owners());
            for(int line=0;line<4;line++)assertTrue(world.palettes[line].dataEquals(fresh.palettes[line]));
        }
    }

    @Test void publicationUsesOnlyTheInjectedExistingPaletteUploadQueue() throws Exception {
        var level=mock(Level.class);var palettes=palettes();when(level.getZoneIndex()).thenReturn(0xE);when(level.getPaletteCount()).thenReturn(4);
        for(int line=0;line<4;line++)when(level.getPalette(line)).thenReturn(palettes[line]);
        var uploadQueue=new Object[1];
        var graphics=mock(GraphicsManager.class,withSettings().useConstructor().defaultAnswer(invocation->{
            if(invocation.getMethod().getName().equals("paletteTextures")) {
                if(uploadQueue[0]==null) {
                    var existing=invocation.callRealMethod();
                    uploadQueue[0]=mock(existing.getClass(),org.mockito.AdditionalAnswers.delegatesTo(existing));
                }
                return uploadQueue[0];
            }
            return CALLS_REAL_METHODS.answer(invocation);
        }));
        graphics.setHeadlessMode(true);
        when(graphics.isGlInitialized()).thenReturn(true);var registry=new PaletteOwnershipRegistry();
        var owner=new S3kCompetitionPaletteAnimator(rom,level,registry,graphics);owner.prepareInitialPalette(target(AZURE_LAKE));
        var uploads=uploadQueue[0];
        assertNotEquals("s3k.global-animation",owner.key());assertEquals("sonic3k.competition.animated-palette",owner.key());
        var before=owner.capture();registry.beginFrame();clearInvocations(graphics,uploads);owner.armLevelFade();
        assertEquals(4L,mockingDetails(uploads).getInvocations().stream().filter(call->call.getMethod().getName().equals("cacheLatchablePaletteTexture")).count());
        assertFalse(mockingDetails(uploads).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("setPaletteUploadLatched")
                ||call.getMethod().getName().equals("cacheImmediatePaletteTexture"))
                ||mockingDetails(graphics).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("cachePaletteTexture")),
                "the CPU owner cannot advance V-int or force an immediate upload");
        registry.beginFrame();clearInvocations(graphics,uploads);owner.restore(before);
        assertEquals(4L,mockingDetails(uploads).getInvocations().stream().filter(call->call.getMethod().getName().equals("cacheLatchablePaletteTexture")).count());
        for(int line=0;line<4;line++)assertEquals(target(AZURE_LAKE)[line*16],owner.targetColor(line,0));
    }

    private record World(Palette[] palettes,PaletteOwnershipRegistry registry,S3kCompetitionPaletteAnimator owner) { }
    private World world(S3kCompetitionSession.Arena arena) throws Exception {
        var level=mock(Level.class);var palettes=palettes();when(level.getPaletteCount()).thenReturn(4);when(level.getZoneIndex()).thenReturn(arena.zoneId());
        for(int i=0;i<4;i++)when(level.getPalette(i)).thenReturn(palettes[i]);var registry=new PaletteOwnershipRegistry();
        var owner=new S3kCompetitionPaletteAnimator(rom,level,registry);owner.prepareInitialPalette(target(arena));registry.beginFrame();return new World(palettes,registry,owner);
    }
    private World cycling(S3kCompetitionSession.Arena arena) throws Exception {var world=world(arena);world.owner.armLevelFade();for(int i=0;i<22;i++)frame(world);return world;}
    private static Palette[] palettes(){return new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};}
    private static void frame(World world){world.registry.beginFrame();world.owner.update();}
    private int[] target(S3kCompetitionSession.Arena arena) {
        int[] arenaSources={0xA99DC,0xA9A3C,0xA9A9C,0xA9AFC,0xA9B5C};int[] sources={0xA8A3C,0x6C0C,arenaSources[arena.zoneId()-0xE]+32,arenaSources[arena.zoneId()-0xE]+64};
        int[] words=new int[64];for(int line=0;line<4;line++)for(int color=0;color<16;color++)words[line*16+color]=reader.readU16BE(sources[line]+color*2);return words;
    }
    private void assertTable(World world,int line,int color,int address,int count){for(int i=0;i<count;i++)assertEquals(reader.readU16BE(address+i*2),world.owner.readCurrentColor(line,color+i));}
    private static int word(byte[] bytes,int offset){return (bytes[offset]&255)<<8|(bytes[offset+1]&255);}
    private static int oracleAdd(int current,int target){
        if(current==target)return current;for(int step:new int[]{0x200,0x20}){int next=(current+step)&0xFFFF;if(Integer.compareUnsigned(next,target)<=0)return next;}return (current+2)&0xFFFF;
    }
}
