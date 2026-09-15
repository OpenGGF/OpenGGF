package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen locked-on placement inventory. Missing families remain explicit. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozObjectInventory {
    @Test void romPlacementsAndRemainingFactoryGapsMatchInventory() throws Exception {
        try (var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            verify(rom,0x1F4866,3600,599,"72f954524c0d68b213be273d6b5317621c60c1a4aab8949ffc2cc72482dc66db",178);
            verify(rom,0x1F5676,2946,490,"3cca36d2d4db8db480f36c67fafa46833f4f7a63ff1cab6d6835d2380bb225c0",180);
        }
    }
    @Test void vineMappingPointerAndSinglePieceMatchTheRom() throws Exception {
        try (var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            assertArrayEquals(new byte[]{0x21,0x7C,0,4,0x0B,0x0C},rom.readBytes(0x40786,6));
            var frames=com.openggf.game.sonic3k.S3kSpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom),0x40B0C,1);
            assertEquals(1,frames.size()); assertEquals(1,frames.getFirst().pieces().size());
            var piece=frames.getFirst().pieces().getFirst();
            assertEquals(2,piece.widthTiles()); assertEquals(2,piece.heightTiles());
            assertEquals(0,piece.tileIndex()); assertEquals(-8,piece.xOffset()); assertEquals(-8,piece.yOffset());
        }
    }
    @Test void sandRockMappingPointerAndBreakupFramesMatchTheRom() throws Exception {
        try (var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            assertArrayEquals(new byte[]{0x21,0x7C,0,4,0x18,0x2E},rom.readBytes(0x41702,6));
            var frames=com.openggf.game.sonic3k.S3kSpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom),0x4182E,5);
            assertEquals(5,frames.size());
            for(var frame:frames) assertEquals(2,frame.pieces().size());
            assertEquals(3,frames.getFirst().pieces().getFirst().widthTiles());
            assertEquals(4,frames.getFirst().pieces().getFirst().heightTiles());
            assertEquals(0x33,frames.getLast().pieces().getFirst().tileIndex());
        }
    }
    @Test void pushableRockMappingAndTrackPointersMatchTheRom() throws Exception {
        try (var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            assertArrayEquals(new byte[]{0x21,0x7C,0,4,7,0x76},rom.readBytes(0x40546,6));
            assertEquals(0x1E3FD8,rom.read32BitAddr(0x40574));
            assertEquals(0x1F6CCA,rom.read32BitAddr(0x1E3FD8+9*4));
            int[] expected={0x630,0x460,0x652,0x4F0,0xFFFF};
            for(int i=0;i<expected.length;i++) assertEquals(expected[i],rom.read16BitAddr(0x1F6CCA+i*2));
            var frames=com.openggf.game.sonic3k.S3kSpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom),0x40776,1);
            assertEquals(1,frames.size()); assertEquals(2,frames.getFirst().pieces().size());
            for(var piece:frames.getFirst().pieces()) {
                assertEquals(2,piece.widthTiles()); assertEquals(3,piece.heightTiles());
                assertEquals(0x25,piece.tileIndex());
            }
        }
    }
    private void verify(Rom rom,int address,int size,int count,String sha,int missing) throws Exception {
        var bytes=rom.readBytes(address,size);
        assertEquals(sha,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var placements=CommonPlacementParser.parseObjectRecords(new RomByteReader(bytes),0);
        assertEquals(count,placements.size());
        var registry=new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return 8; }
        };
        assertEquals(missing,placements.stream().map(registry::create)
                .filter(PlaceholderObjectInstance.class::isInstance).count(),
                "update the explicit inventory as families are implemented; zero is not yet expected");
        placements.stream().filter(p -> p.objectId()==0x38).forEach(p ->
                assertInstanceOf(SozQuicksandObjectInstance.class,registry.create(p)));
    }
}
