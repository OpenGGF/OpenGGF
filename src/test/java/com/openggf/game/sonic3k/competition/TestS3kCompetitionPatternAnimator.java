package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionPatternAnimator {
    private Rom rom;
    private Level level;
    private Pattern[] tiles;
    private GraphicsManager graphics;
    @BeforeEach void setup() throws Exception {
        rom=RomManager.getInstance().getRom();level=mock(Level.class);graphics=mock(GraphicsManager.class);
        tiles=new Pattern[2048];Arrays.setAll(tiles,i->new Pattern());
        when(level.getPattern(anyInt())).thenAnswer(call->tiles[call.getArgument(0,Integer.class)]);
        when(level.getPatternCount()).thenReturn(tiles.length);
    }
    private S3kCompetitionPatternAnimator animator(Arena arena) throws Exception {
        return new S3kCompetitionPatternAnimator(rom,level,arena,()->null,graphics);
    }
    private S3kCompetitionScroll cameras(int x1,int x2) throws Exception {
        var scroll=new S3kCompetitionScroll(rom,Arena.DESERT_PALACE,5);
        scroll.initialize(new S3kCompetitionScroll.CameraPair(new S3kCompetitionScroll.CameraPosition(x1,0x148),
                new S3kCompetitionScroll.CameraPosition(x2,0x148)));return scroll;
    }
    @Test void lockedOnDispatchAndCompleteAnimatedArtSpansMatchNativeOwners() throws Exception {
        var reader=RomByteReader.fromRom(rom);int table=0x27778;
        int[] functions={0x286E4,0x286E4,0x283E2,0x27838,0x27838};
        int[] scripts={0x28BD8,0x28BE6,0x28C1C,0x28C3E,0x28C3E};
        for(int zone=0xE;zone<=0x12;zone++) for(int act=0;act<2;act++) {
            int entry=table+zone*8+act*4;
            assertEquals(functions[zone-0xE],table+(short)reader.readU16BE(entry));
            assertEquals(scripts[zone-0xE],table+(short)reader.readU16BE(entry+2));
        }
        assertEquals(0x2C0180,reader.readU32BE(0x2841A));
        int[] address={0x2BE8C0,0x2BEEC0,0x2BF400,0x2BF900,0x2BF9C0,0x2BFA00,0x2C0180};
        int[] size={1536,1344,1280,192,64,1920,8192};
        String[] sha={"e746234bba347a23d7e61bb82f8baf72c7d3ca7ed6252f351016f41524a8ae0b",
                "14819f70aeefc190a103309250c07b673df4dc4a24c4c0141404a726779df9ef",
                "4c7fc17322db2316bae1ecd239ea0df3da3dd1cd33f87f73fa68a0ae3063e950",
                "4c1415e7726eec2252002bf75e8965168f71e53e036576ee0d19e635251e7ac7",
                "acffa5355115ab29c16da062c4008d11cf824a31c455695fe0291f819b093c32",
                "e1fd5285a7ce2fc28ebb61e6fbc96b97a5c06dc3fddcb61a6f6a7b4eae4903d7",
                "ac89408191682561b93348779a75f0f63119196011bc44d8082ed629912b32ea"};
        for(int i=0;i<address.length;i++) assertEquals(sha[i],HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rom.readBytes(address[i],size[i]))));
    }
    @Test void azureSubmitsFirstFrameThenWaitsTenPassesAndPublishesOnlyAtVblank() throws Exception {
        var owner=animator(Arena.AZURE_LAKE);byte[] before=encode(0x238,16);
        owner.runInitialLevelAnimationPass();assertEquals(1,owner.pendingTransfers().size());
        assertArrayEquals(before,encode(0x238,16));
        assertArrayEquals(rom.readBytes(0x2BE8C0,512),owner.pendingTransfers().getFirst().bytes());
        owner.publishAtVBlank();assertArrayEquals(rom.readBytes(0x2BE8C0,512),encode(0x238,16));
        for(int i=0;i<9;i++) owner.update();assertTrue(owner.pendingTransfers().isEmpty());
        owner.update();assertEquals(1,owner.pendingTransfers().size());
        assertArrayEquals(rom.readBytes(0x2BE8C0+512,512),owner.pendingTransfers().getFirst().bytes());
        owner.publishAtVBlank();verify(graphics,never()).updatePatternTexture(any(),anyInt());
    }
    @Test void balloonChannelsRetainNativeScriptOrderAndIndependentDurations() throws Exception {
        var owner=animator(Arena.BALLOON_PARK);owner.update();
        assertEquals(List.of(0x118,0x11F,0x127,0x12A),owner.pendingTransfers().stream().map(S3kCompetitionPatternAnimator.Upload::destination).toList());
        assertEquals(List.of(224,256,96,32),owner.pendingTransfers().stream().map(u->u.bytes().length).toList());
        owner.publishAtVBlank();for(int i=0;i<2;i++)owner.update();assertTrue(owner.pendingTransfers().isEmpty());
        owner.update();assertEquals(List.of(0x127),owner.pendingTransfers().stream().map(S3kCompetitionPatternAnimator.Upload::destination).toList());
        assertArrayEquals(rom.readBytes(0x2BF900+96,96),owner.pendingTransfers().getFirst().bytes());
    }
    @Test void chromeAndEndlessHaveNoAnimatedTileProducer() throws Exception {
        for(Arena arena:List.of(Arena.CHROME_GADGET,Arena.ENDLESS_MINE)) {
            var owner=animator(arena);for(int i=0;i<300;i++)owner.update();
            assertTrue(owner.pendingTransfers().isEmpty());verify(level,never()).ensurePatternCapacity(anyInt());
        }
    }
    @Test void desertQueuesBothCameraSplitUploadsBeforeItsScriptAndSkipsUnchangedPhases() throws Exception {
        var scroll=cameras(0x40,0x20);
        var owner=new S3kCompetitionPatternAnimator(rom,level,Arena.DESERT_PALACE,()->scroll,graphics);
        owner.update();var pending=owner.pendingTransfers();
        assertEquals(List.of(0x153,0x15F,0x173,0x17B,0x147),pending.stream().map(S3kCompetitionPatternAnimator.Upload::destination).toList());
        assertEquals(List.of(384,640,256,768,384),pending.stream().map(u->u.bytes().length).toList());
        assertArrayEquals(rom.readBytes(0x2C0180+0x280,384),pending.getFirst().bytes());
        assertArrayEquals(rom.readBytes(0x2C0180,640),pending.get(1).bytes());
        owner.publishAtVBlank();owner.update();assertTrue(owner.pendingTransfers().isEmpty());
    }
    @Test void everyDesertCameraPhaseProducesExactlyIts32TilesWithoutCrossingEitherSlotBank() throws Exception {
        AtomicReference<S3kCompetitionScroll> scroll=new AtomicReference<>(cameras(0,0));
        var owner=new S3kCompetitionPatternAnimator(rom,level,Arena.DESERT_PALACE,scroll::get,graphics);
        for(int phase=1;phase<64;phase++) {
            int background=0;while((((background>>2)-background)&63)!=phase)background++;
            scroll.set(cameras(background*2,background*2));owner.update();
            for(int slot=0;slot<2;slot++) {
                int start=0x153+slot*0x20;var uploads=owner.pendingTransfers().stream().filter(u->u.destination()>=start&&u.destination()<start+32).toList();
                assertEquals(1024,uploads.stream().mapToInt(u->u.bytes().length).sum(),"phase "+phase+" slot "+slot);
                for(var upload:uploads)assertTrue(upload.destination()+upload.bytes().length/32<=start+32);
            }
            owner.publishAtVBlank();
        }
    }
    @Test void uploadPayloadsAreDefensiveAndRealGraphicsReceivesChangedDestinationTextures() throws Exception {
        when(graphics.isGlInitialized()).thenReturn(true);var owner=animator(Arena.AZURE_LAKE);owner.update();
        byte[] payload=owner.pendingTransfers().getFirst().bytes();payload[0]^=0xF;
        owner.publishAtVBlank();assertArrayEquals(rom.readBytes(0x2BE8C0,512),encode(0x238,16));
        for(int tile=0x238;tile<0x248;tile++)verify(graphics).updatePatternTexture(same(tiles[tile]),eq(tile));
        clearInvocations(graphics);owner.publishAtVBlank();verifyNoInteractions(graphics);
    }
    @Test void rewindRestoresPresentedAndPendingBytesAndBothCameraPhaseCachesBeforeForwardReplay() throws Exception {
        AtomicReference<S3kCompetitionScroll> scroll=new AtomicReference<>(cameras(0x40,0x20));
        var owner=new S3kCompetitionPatternAnimator(rom,level,Arena.DESERT_PALACE,scroll::get,graphics);
        owner.update();owner.publishAtVBlank();for(int i=0;i<12;i++)owner.update();
        byte[] saved=owner.capture();owner.publishAtVBlank();scroll.set(cameras(0x60,0x30));owner.update();
        owner.publishAtVBlank();byte[] expected=owner.capture();
        owner.restore(saved);assertArrayEquals(saved,owner.capture());
        owner.publishAtVBlank();owner.update();owner.publishAtVBlank();assertArrayEquals(expected,owner.capture());
        assertThrows(IllegalArgumentException.class,()->animator(Arena.AZURE_LAKE).restore(saved));
    }
    private byte[] encode(int start,int count) {
        byte[] out=new byte[count*32];for(int i=0;i<count;i++)for(int y=0;y<8;y++)for(int x=0;x<8;x+=2)
            out[i*32+y*4+x/2]=(byte)(tiles[start+i].getPixel(x,y)<<4|tiles[start+i].getPixel(x+1,y));return out;
    }
}
