package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-only packet; Java/media/runtime verification UNRUN.
 * Native1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Spring/Main.asm:29-54,76-93 and
 * Spring/Data/Animations (Wheel).asm, Mappings (Wheel).asm. Original USA operands
 * select209A90/209A96. Existing Std wheel art/current ownership is unchanged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aMovingSpringAssets {
    private static final HexFormat HEX = HexFormat.of();
    private static final CdR12aProfile.Asset[] KEYS = {ANI_MOVING_SPRING, MAP_SPR_MOVING_SPRING};
    private static final String[] RAW = {"0002080001ff", "0004000a01f8050000f801f8050004f8"};
    private static final String[] HASHES = {
            "e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a",
            "f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d"};

    @Test void appendedDescriptorsRetainExactOriginalSourceProvenanceAndLiveOperands() throws Exception {
        var r = retail(); var o = r.terrain().objects();
        assertSame(r.terrain().identity(), o.identity()); assertSame(r.terrain().profile(), o.profile());
        assertEquals(CdReleaseVariant.USA, o.identity().variant());
        assertEquals(CdStagePayload.R12A.stage(), o.identity().stage());
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7", o.identity().mmdSha256());
        assertEquals(146, CdR12aProfile.Asset.values().length);
        assertEquals(138, ANI_ROBOT_GENERATOR.ordinal()); assertEquals(141, ROBOT_GENERATOR_BASE_TILES.ordinal());
        int[] offsets = {0x9A90, 0x9A96}, lengths = {6, 16};
        for (int i = 0; i < KEYS.length; i++) {
            var key = KEYS[i]; var span = o.profile().range(key);
            assertEquals(142 + i, key.ordinal()); assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertEquals(CdNativeFile.R12A, span.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, span.domain());
            assertEquals(0x200000 + offsets[i], span.nativeAddress()); assertEquals(offsets[i] + lengths[i], span.endExclusive());
            assertSame(o.view(key), o.view(key)); assertArrayEquals(HEX.parseHex(RAW[i]), o.view(key).copy());
            assertEquals(HASHES[i], CdR11aProfile.sha256(o.view(key).copy()));
            assertArrayEquals(r.original().readFile(offsets[i], lengths[i]), o.view(key).copy());
        }
        assertEquals("0020948a", HEX.formatHex(r.original().readFile(0x3D00, 4)));
        assertEquals("217c00209a960004", HEX.formatHex(r.original().readFile(0x94CE, 8)));
        assertEquals("43f900209a90", HEX.formatHex(r.original().readFile(0x9576, 6)));
    }

    @Test void bothCompleteFramesAndOneScriptKeepAllPiecesAndTheEightTileBound() throws Exception {
        var o = retail().terrain().objects(); var v = o.movingSpring(); assertSame(v, o.movingSpring());
        assertEquals(0, v.mappingTableOffset()); assertEquals(2, v.mappingFrames().size());
        assertArrayEquals(HEX.parseHex(RAW[1]), v.mappings().copy());
        for (int i = 0; i < 2; i++) {
            var f = v.mappingFrames().get(i); assertEquals(4 + 6 * i, f.relativeOffset());
            assertEquals(1, f.pieces().size()); assertEquals(6, f.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(HEX.parseHex(RAW[1]), 4 + 6 * i, 10 + 6 * i), f.bytes().copy());
            var p = f.pieces().getFirst(); assertEquals(-8, p.x()); assertEquals(-8, p.y());
            assertEquals(5, p.sizeByte()); assertEquals(i * 4, p.rawTileWord()); assertEquals(i * 4, p.tileOffset());
            assertEquals(2, p.widthTiles()); assertEquals(2, p.heightTiles()); assertEquals(4, p.tileCount());
            assertTrue(p.tileOffset() + p.tileCount() <= 8);
        }
        assertEquals(8, v.mappingFrames().get(1).pieces().getFirst().tileOffset() + v.mappingFrames().get(1).pieces().getFirst().tileCount());
        assertArrayEquals(HEX.parseHex(RAW[0]), v.animationTable().copy()); assertEquals(1, v.animations().size());
        var s = v.animations().getFirst(); assertEquals(2, s.relativeOffset()); assertEquals(8, s.delay());
        assertEquals(List.of(0, 1), s.frameBytes()); assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, s.terminal());
        assertEquals("080001ff", HEX.formatHex(s.bytes().copy()));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(HEX.parseHex(RAW[1]), 2, HEX.parseHex(RAW[0]), 1, 7));
    }

    @Test void lazyCachedVisualReadsOnlyTwoRawViewsWithoutArtReadersOrPlcDecode() throws Exception {
        var r = retail(); var o = new CdR12aObjectAssets(r.terrain(), r.original());
        assertNull(field(o, "movingSpring")); assertEquals(Map.of(), field(o, "views"));
        var v = o.movingSpring(); assertSame(v, o.movingSpring()); assertSame(v, field(o, "movingSpring"));
        assertEquals(Set.of(KEYS), ((Map<?, ?>) field(o, "views")).keySet());
        for (String name : List.of("decoded", "javaInputs", "nativeInputs")) assertEquals(Map.of(), field(o, name));
        for (String name : List.of("plcs", "sections", "placements")) assertNull(field(o, name));
        assertThrows(UnsupportedOperationException.class, () -> v.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.mappingFrames().getFirst().pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.animations().getFirst().frameBytes().clear());
        byte[] changed = v.mappings().copy(); changed[0] ^= 1; assertArrayEquals(HEX.parseHex(RAW[1]), v.mappings().copy());
        changed = v.animationTable().copy(); changed[0] ^= 1; assertArrayEquals(HEX.parseHex(RAW[0]), v.animationTable().copy());
    }

    @Test void existingReadersListsSectionsAndWheelSourceStayWithTheirExistingOwners() throws Exception {
        var o = retail().terrain().objects(); var readers = o.readerProfiles(); var plcs = o.plcLists(); var sections = o.sections();
        var held = Arrays.stream(CdR12aProfile.Asset.values()).limit(142).map(o::view).toList();
        String readerBefore = readers.toString(), plcBefore = plcs.toString(), sectionsBefore = sections.toString();
        o.movingSpring();
        assertSame(readers, o.readerProfiles()); assertSame(plcs, o.plcLists()); assertSame(sections, o.sections());
        assertEquals(readerBefore, readers.toString()); assertEquals(plcBefore, plcs.toString()); assertEquals(sectionsBefore, sections.toString());
        assertEquals(35, readers.size()); assertEquals(19, plcs.size());
        for (int i = 0; i < 142; i++) assertSame(held.get(i), o.view(CdR12aProfile.Asset.values()[i]));
        var p = o.readerProfile(ART_SPRING_WHEEL); assertEquals(0x37272, p.sourceBlobRange().fileOffset());
        assertEquals(182, p.sourceBlobRange().length()); assertEquals(p.sourceBlobRange(), p.javaInputRange());
        assertEquals(p.sourceBlobRange(), p.nativeInputRange()); assertEquals(181, p.javaConsumedInputBytes()); assertEquals(256, p.outputBytes());
        assertEquals("b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee", CdR11aProfile.sha256(o.view(ART_SPRING_WHEEL).copy()));
        var e = plcs.get(1).entries().get(1); assertEquals(ART_SPRING_WHEEL, e.source()); assertEquals(p.sourceBlobRange(), e.sourceRange());
        assertEquals(0x6700, e.destinationWord()); assertEquals("002372726700", HEX.formatHex(retail().original().readFile(0xF0EA, 6)));
        for (var key : KEYS) {
            assertThrows(IllegalArgumentException.class, () -> o.readerProfile(key));
            for (var kind : CdR12aObjectAssets.ReaderKind.values()) assertThrows(IllegalArgumentException.class, () -> o.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> o.decode(key));
            assertTrue(plcs.stream().flatMap(l -> l.entries().stream()).noneMatch(row -> row.source() == key));
        }
    }

    @Test void changedOriginalsFiniteOverreadsAndMalformedTablesHaveNoFallback() throws Exception {
        var r = retail(); var o = r.terrain().objects();
        for (var key : KEYS) {
            var raw = o.view(key); assertThrows(IndexOutOfBoundsException.class, () -> raw.unsignedByte(raw.length()));
            byte[] corrupt = r.original().readFile(0, 262144); corrupt[key.fileOffset()] ^= 1;
            assertThrows(IOException.class, () -> o.profile().verifyProgram(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, CdMmdImage.parse(corrupt)));
        }
        byte[] map = HEX.parseHex(RAW[1]), ani = HEX.parseHex(RAW[0]);
        for (int kind = 0; kind < 4; kind++) {
            byte[] bad = map.clone();
            switch (kind) {
                case 0 -> bad[1] = 3;
                case 1 -> bad[4] = 2;
                case 2 -> bad[6] = 16;
                case 3 -> bad[14] = 5;
            }
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 2, ani, 1, 8));
        }
        for (int kind = 0; kind < 3; kind++) {
            byte[] bad = ani.clone();
            switch (kind) { case 0 -> bad[1] = 1; case 1 -> bad[4] = 2; case 2 -> bad[5] = 0; }
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(map, 2, bad, 1, 8));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(Arrays.copyOf(map, 15), 2, ani, 1, 8));
    }

    private static Object field(CdR12aObjectAssets o, String name) throws Exception {
        var f = CdR12aObjectAssets.class.getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media root");
        assertFalse(root.isBlank()); Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Retail(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A));
        }
        return retained;
    }
}
