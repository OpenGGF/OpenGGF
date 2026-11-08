package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual-media data/format checks only. No loading, Frame, Ready, gameplay or rendering claim. */
class TestCdR4DataLayout {
    private static final Map<CdR4DataLayout.Programme, CdMmdImage> images = new EnumMap<>(CdR4DataLayout.Programme.class);

    @BeforeAll
    static void actualOriginalUsaMedia() throws Exception {
        String root = System.getProperty("soniccd.media.root", "");
        Path cue = Path.of(root, "USA", "Sonic CD (US).cue");
        assumeTrue(!root.isEmpty() && Files.isRegularFile(cue), "Actual USA media required");
        assertEquals(CdR4DataLayout.USA_CUE_SHA256, hash(Files.readAllBytes(cue)));
        try (var programmes = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assertEquals(CdR4DataLayout.USA_DISC_SHA256, programmes.identity().normalizedSha256());
            assertTrue(programmes.hasExpectedTrackShape());
            for (var pcm : CdR4DataLayout.Pcm.values()) {
                assertEquals(pcm.isoLba(), programmes.entry(pcm.file()).extentBlock());
                assertEquals(pcm.fileBytes(), programmes.entry(pcm.file()).size());
                assertEquals(pcm.sha256(), hash(programmes.read(pcm.file(), 0, pcm.fileBytes())));
            }
            for (var p : CdR4DataLayout.Programme.values()) {
                assertEquals(p.payload(), programmes.catalogue().orElseThrow().stage(p.payload().stage()));
                var entry = programmes.entry(p.file());
                assertEquals(p.isoLba(), entry.extentBlock());
                assertEquals(p.fileBytes(), entry.size());
                assertEquals(p.payload().stage().actNumber() == 3 ? CdR4DataLayout.Pcm.BOSS : CdR4DataLayout.Pcm.R4, p.pcm());
                images.put(p, programmes.mmd(p.file()));
            }
        }
    }

    @Test
    void tenIndependentProgrammeImagesAndEveryDeclaredExtentMatchOriginalSource() throws Exception {
        assertEquals(List.of("R41A", "R41B", "R41C", "R41D", "R42A", "R42B", "R42C", "R42D", "R43C", "R43D"),
                Arrays.stream(CdR4DataLayout.Programme.values()).map(Enum::name).toList());
        var hashes = new HashSet<String>();
        for (var p : CdR4DataLayout.Programme.values()) {
            var image = images.get(p);
            assertEquals(262144, image.fileBytes());
            assertEquals(p.fileSha256(), hash(image.readFile(0, image.fileBytes())), p.name());
            assertTrue(hashes.add(p.fileSha256()));
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, p.domain());
            assertTrue(image.relocatedCopy().isEmpty());
            assertEquals(new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112), image.header());
            assertEquals(p.startAddress(), bytes(image, 0x102, 4).unsignedLong(0));
            assertEquals(p.hblankAddress(), bytes(image, 0x10E, 4).unsignedLong(0));
            assertEquals(p.vblankAddress(), bytes(image, 0x114, 4).unsignedLong(0));
            var keys = new HashSet<String>();
            for (var r : p.ranges()) {
                assertTrue(keys.add(r.key()), p + ":" + r.key());
                assertEquals(r.sha256(), hash(image.readFile(r.fileOffset(), r.length())), p + ":" + r.key());
                assertEquals(0x200000 + r.fileOffset(), r.nativeAddress());
            }
            assertThrows(IllegalArgumentException.class, () -> p.range("UNOWNED"));
            assertThrows(UnsupportedOperationException.class, () -> p.ranges().clear());
        }
    }

    @Test
    void all191SignedRelativePlcRootsAndFullOrderedEntriesRetainNativeAliases() {
        int roots = 0;
        for (var p : CdR4DataLayout.Programme.values()) {
            var image = images.get(p); var directory = p.range("GfxLists");
            assertEquals(p == CdR4DataLayout.Programme.R42A ? 20 : 19, p.plcs().size());
            assertEquals(p.plcs().size() * 2, directory.length());
            for (var plc : p.plcs()) {
                roots++;
                int relative = (short) bytes(image, directory.fileOffset() + plc.id()*2, 2).unsignedWord(0);
                assertEquals(plc.fileOffset(), directory.fileOffset() + relative);
                var body = bytes(image, plc.fileOffset(), 2 + plc.entries().size()*6);
                assertEquals(plc.entries().size(), (short) body.unsignedWord(0) + 1);
                assertEquals(p.range(plc.body()).fileOffset(), plc.fileOffset());
                for (int i = 0; i < plc.entries().size(); i++) {
                    var e = plc.entries().get(i);
                    assertEquals(e.nativeAddress(), body.unsignedLong(2 + 6*i));
                    assertEquals(e.destinationWord(), body.unsignedWord(6 + 6*i));
                    assertEquals(p.range(e.source()).nativeAddress(), e.nativeAddress());
                }
            }
            assertEquals(p.plcs().get(0).fileOffset(), p.plcs().get(3).fileOffset());
        }
        assertEquals(191, roots);
        var r42b = CdR4DataLayout.Programme.R42B;
        assertEquals(8, r42b.plcs().get(4).entries().size());
        // The retained GoalGfx record immediately follows gfxEnd; it is not entry9 of list4.
        var end = bytes(images.get(r42b), r42b.plcs().get(4).fileOffset()+50, 6);
        assertEquals(r42b.range("GoalGfx").nativeAddress(), end.unsignedLong(0));
        assertEquals(0x9E40, end.unsignedWord(4));
        assertEquals("GoalGfx", CdR4DataLayout.Programme.R42A.plcs().get(19).entries().getFirst().source());
        for (var p : List.of(CdR4DataLayout.Programme.R43C, CdR4DataLayout.Programme.R43D)) {
            assertEquals("BossBubbleGfx", p.plcs().get(4).entries().getFirst().source());
            assertEquals(0x63C0, p.plcs().get(4).entries().getFirst().destinationWord());
            assertEquals("CapsuleGfx", p.plcs().get(18).entries().getFirst().source());
        }
    }

    @Test
    void original188VisualsDecodeEveryFrameWithoutBorrowing167Or198Boundaries() {
        for (var p : CdR4DataLayout.Programme.values()) {
            byte[] maps = range(p, "PlayerSprites"), dplc = range(p, "PlayerGfxScript"), art = range(p, "PlayerArtReadView");
            var v = CdSonicVisualAssets.decode(p.sonicFormat(), maps, dplc, art);
            assertEquals(3493, v.mappings().length()); assertEquals(1862, v.dplcs().length());
            assertEquals(188, v.mappingFrames().size()); assertEquals(188, v.dplcFrames().size());
            assertEquals(76928, v.rawArt().length()); assertEquals(82944, v.artReadView().length());
            assertThrows(IllegalStateException.class, v::alignmentByte);
            int maximum = 0;
            for (int f = 0; f < 188; f++) {
                var frame = v.dplcFrames().get(f); assertTrue(frame.outputBytes() <= 0x300);
                for (int i = 0; i < frame.pieces().size(); i++) {
                    var piece = frame.pieces().get(i);
                    maximum = Math.max(maximum, piece.artByteOffset()+piece.byteLength());
                    assertArrayEquals(Arrays.copyOfRange(art, piece.artByteOffset(), piece.artByteOffset()+piece.byteLength()), v.artPiece(f,i).copy());
                }
            }
            assertEquals(82944, maximum);
            assertArrayEquals(range(p, "PlayerGfx"), v.rawArt().copy());
            // Source .GfxScript29 reads512 bytes beginning exactly at the raw art endpoint.
            assertEquals(0xF964, v.dplcFrames().get(41).pieces().getFirst().rawWord());
            assertEquals(76928, v.dplcFrames().get(41).pieces().getFirst().artByteOffset());
            assertArrayEquals(images.get(p).readFile(0x32C80, 512), v.artPiece(41,0).copy());
            // Source .GfxScriptB3/B4 read the last32 actual bytes of the finite view.
            assertEquals(0x0A1F, v.dplcFrames().get(179).pieces().getFirst().rawWord());
            assertArrayEquals(images.get(p).readFile(0x343E0,32), v.artPiece(179,0).copy());
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(p.sonicFormat(), maps, dplc, range(p,"PlayerGfx")));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, dplc, art));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167, maps, dplc, art));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(p.sonicFormat(), maps, dplc, Arrays.copyOf(art, art.length+1)));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(p.sonicFormat(), maps, dplc, Arrays.copyOf(art, art.length-1)));
        }
    }

    @Test
    void actualPaletteRowsIncludeTheWaterTitlePaletteAndFutureSixthRow() {
        for (var p : CdR4DataLayout.Programme.values()) {
            var r = p.range("PaletteTable"); var table = bytes(images.get(p), r.fileOffset(), r.length());
            boolean future = p.name().endsWith("C") || p.name().endsWith("D");
            assertEquals(future ? 48 : 40, table.length());
            assertEquals(p.range("S1TitlePalette").nativeAddress(), table.unsignedLong(0));
            assertEquals(0xFB00, table.unsignedWord(4)); assertEquals(0x1F, table.unsignedWord(6));
            assertEquals(p.range("PlayerPalette").nativeAddress(), table.unsignedLong(24));
            assertEquals(7, table.unsignedWord(30));
            assertEquals(128, p.range("PlayerPaletteFullRead").length());
            assertEquals(p.range("PlayerPalette").fileOffset(), p.range("PlayerPaletteFullRead").fileOffset());
            assertArrayEquals(range(p,"PlayerPalette"), Arrays.copyOf(range(p,"PlayerPaletteFullRead"),32));
            assertArrayEquals(range(p,"StagePalette"), Arrays.copyOfRange(range(p,"PlayerPaletteFullRead"),32,128));
            assertEquals(p.range("StagePalette").nativeAddress(), table.unsignedLong(32));
            assertEquals(0xFB20, table.unsignedWord(36)); assertEquals(0x17, table.unsignedWord(38));
            for (int at = 0; at < table.length(); at += 8) {
                long address = table.unsignedLong(at); int count = (table.unsignedWord(at+6)+1)*4;
                // Row2 deliberately reads 128 contiguous bytes beginning at the 32-byte player palette.
                assertDoesNotThrow(() -> images.get(p).nativeSlice(p.domain(), (int) address, count));
            }
        }
        assertFalse(Arrays.equals(range(CdR4DataLayout.Programme.R41C,"StagePalette"), range(CdR4DataLayout.Programme.R41D,"StagePalette")));
    }

    @Test
    void finiteTerrainOperandsAndPlacementWidthsRemainDataWithoutRuntimeAdmission() throws Exception {
        int programmeIndex = 0; int[] activeRows = {190,162,184,184,215,189,223,223,32,32};
        for (var p : CdR4DataLayout.Programme.values()) {
            var t = p.terrain(); byte[] fg = range(p,"StageMapFg"), bg = range(p,"StageMapBg");
            var reader = TestCdR4TerrainData.reader(p); var known = reader.terrain();
            assertSame(known, reader.terrain()); assertEquals(t.blocksBytes(), known.blocks().length());
            assertEquals(t.collisionIndexBytes(), known.collision().ids().length());
            assertThrows(UnsupportedOperationException.class, known::shape);
            assertThrows(UnsupportedOperationException.class, reader::ordinaryZoneActWord);
            assertEquals(t.foregroundColumns(), Byte.toUnsignedInt(fg[0])+1);
            assertEquals(t.foregroundRows(), Byte.toUnsignedInt(fg[1])+1);
            assertEquals(t.backgroundColumns(), Byte.toUnsignedInt(bg[0])+1);
            assertEquals(t.backgroundRows(), Byte.toUnsignedInt(bg[1])+1);
            assertEquals(t.chunksBytes(), p.range("StageChunks").length());
            assertEquals(t.collisionIndexBytes(), p.range("StageCollision").length());
            assertEquals(t.blocksBytes(), (CdAssetBytes.copyOf(range(p,"StageBlocks")).unsignedWord(0)&0x7FFF)*32);
            assertEquals(t.patternsBytes(), (CdAssetBytes.copyOf(range(p,"StageGfx")).unsignedWord(0)&0x7FFF)*32);
            var maps = CdAssetBytes.copyOf(range(p,"StageMaps"));
            assertEquals(p.range("StageMapFg").fileOffset(), p.range("StageMaps").fileOffset()+(short)maps.unsignedWord(0));
            assertEquals(p.range("StageMapBg").fileOffset(), p.range("StageMaps").fileOffset()+(short)maps.unsignedWord(2));
            var rows = CdAssetBytes.copyOf(range(p,"StageObjects"));
            int expectedRows = p.name().startsWith("R41") ? 360 : p.name().startsWith("R42") ? 426 : 32;
            assertEquals((expectedRows+1)*8, rows.length());
            int previous = -1, active = 0;
            int mask = switch (p.payload().stage().period()) {
                case PAST -> 0x20;
                case PRESENT -> 0x40;
                case GOOD_FUTURE, BAD_FUTURE -> 0x80;
            };
            for (int at = 0; at < expectedRows*8; at += 8) {
                assertTrue(rows.unsignedWord(at)>=previous); previous=rows.unsignedWord(at);
                int id = rows.unsignedByte(at+4)&0x7F;
                assertTrue(id>=1 && id<=82); // full retained raw source, including other eras.
                if ((rows.unsignedByte(at+6)&mask)!=0) {
                    active++;
                    assertTrue(id<=p.dispatch().size());
                }
            }
            assertEquals(activeRows[programmeIndex++],active);
            assertEquals(0xFFFF, rows.unsignedWord(expectedRows*8));
        }
    }

    @Test
    void completeLocalDispatchAndCodeAddressOperandsMatchEachOriginalProgramme() {
        for (var p : CdR4DataLayout.Programme.values()) {
            var d = CdAssetBytes.copyOf(range(p,"ObjectIndex"));
            assertEquals(p.dispatch().size()*4, d.length());
            var repeated = new HashMap<String,Integer>();
            for (var e : p.dispatch()) {
                assertEquals(e.nativeAddress(), d.unsignedLong((e.id()-1)*4));
                Integer old = repeated.putIfAbsent(e.handler(), e.nativeAddress());
                if (old != null) assertEquals(old.intValue(), e.nativeAddress());
            }
            assertEquals("PlayerObject", p.dispatch().getFirst().handler());
            assertTrue(repeated.containsKey("NullObject"));
            for (var o : p.operands()) {
                byte[] encoded = HexFormat.of().parseHex(o.encodedHex());
                assertArrayEquals(encoded, images.get(p).readFile(o.fileOffset(), encoded.length));
                assertEquals(p.range(o.target()).nativeAddress(), CdAssetBytes.copyOf(encoded).unsignedLong(2));
            }
        }
    }

    @Test
    void currentsWaterHeightsAndSectionBoundariesAreNativeDataOnly() {
        int[] counts = {73,55,92,92,28,27,47,47,0,0}; int i=0;
        for (var p : CdR4DataLayout.Programme.values()) {
            var currents = CdAssetBytes.copyOf(range(p,"WaterCurrents")); int count=counts[i++];
            assertEquals(count*14+2, currents.length()); assertEquals(0xFFFF,currents.unsignedWord(count*14));
            for (int r = 0; r < count; r++) {
                int at=r*14; assertTrue(currents.unsignedWord(at)<currents.unsignedWord(at+4));
                if (p == CdR4DataLayout.Programme.R41B && r == 52) {
                    // Native zero-height rectangle is retained; it cannot match the upper-exclusive test.
                    assertEquals(0xF00,currents.unsignedWord(at));
                    assertEquals(0x420,currents.unsignedWord(at+2));
                    assertEquals(0xF40,currents.unsignedWord(at+4));
                    assertEquals(0x420,currents.unsignedWord(at+6));
                } else assertTrue(currents.unsignedWord(at+2)<currents.unsignedWord(at+6));
                assertTrue(currents.unsignedByte(at+12)<=2);
            }
            var heights = CdAssetBytes.copyOf(range(p,"WaterHeights"));
            assertEquals(24, heights.length());
            assertEquals(0x300,heights.unsignedWord(0)); assertEquals(0x280,heights.unsignedWord(2));
            assertEquals(0x1D0,heights.unsignedWord(4)); assertEquals(0xFFF,heights.unsignedWord(6));
            assertEquals(0x490,heights.unsignedWord(20));
        }
        assertEquals(6,CdR4DataLayout.Programme.R41A.range("SectionGfxRanges").length());
        assertEquals(6,CdR4DataLayout.Programme.R41A.range("SectionGfxInitLists").length());
        assertEquals(6,CdR4DataLayout.Programme.R41A.range("SectionGfxUpdateLists").length());
        assertEquals(10,CdR4DataLayout.Programme.R42B.range("SectionGfxRanges").length());
        assertEquals(10,CdR4DataLayout.Programme.R42B.range("SectionGfxInitLists").length());
        assertEquals(10,CdR4DataLayout.Programme.R42B.range("SectionGfxUpdateLists").length());
    }

    private static byte[] range(CdR4DataLayout.Programme p, String key) {
        var r = p.range(key); return images.get(p).readFile(r.fileOffset(),r.length());
    }
    private static CdAssetBytes bytes(CdMmdImage image, int offset, int length) {
        return CdAssetBytes.copyOf(image.readFile(offset,length));
    }
    private static String hash(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
