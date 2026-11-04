package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original-USA Results raw views/operands only; all authored Java is UNRUN.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:42-148,155-343,349-366,
 * complete Data/Mappings.asm and PP Data (Act 1 Past).asm:219-222,343-345.
 * No Results body, allocation, bonus/score/sound execution, PLC/current upload, World or act completion.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bResultsAssets {
    private static final List<CdR11bAssets.Asset> KEYS = List.of(RESULTS_INIT_DATA, MAP_SPR_RESULTS);
    private static final byte[] INIT_DATA = hex("00cc0000012000000110020000f0000100cc000001200002");
    private static final byte[] MAPPINGS = hex(
            "002801de0052008600ba001e021c0048007c00b000da01ca0122015a019200d0020801180150018808ec050000bcec05"
            + "0004ccec050008dcec01000cecec05000ef4ec05001214ec05000424ec05001634000a040500169c0405001aac040500"
            + "1ebc04050004cc04050022dc04050012ec0405001afc040d00261c0409002e3c040100345c000a040500169c0405001a"
            + "ac0405001ebc04050004cc04050022dc04050012ec0405001afc040d00261c0409002e3c0405004a5c000a040500169c"
            + "0405001aac0405001ebc04050004cc04050022dc04050012ec0405001afc040d00261c0409002e3c0405004e5c000eec"
            + "05000080ec05000490ec050008a0ec01000cb0ec05000eb8ec050036d0ec05003ae0ec05003ef0ec05004200ec05003a"
            + "20ec05001240ec05000450ec05000460ec05003e70000b040500469004050022a004050016b004050022c00405001ed0"
            + "04050042e00401000c000405000808040d0026280409002e4804010034680b040500469004050022a004050016b00405"
            + "0022c00405001ed004050042e00401000c000405000808040d0026280409002e480405004a680b040500469004050022"
            + "a004050016b004050022c00405001ed004050042e00401000c000405000808040d0026280409002e480405004e680ee0"
            + "0d0052d4e001005af4f80d005cd4f80d0064fcf805006c1c100d0070d4100d0064fc1005006c1cf80d008258f801008a"
            + "78100d0078581001008078e00901bf48e00d01c560000ee00d0052d4e001005af4f80d005cd4f80d0064fcf805006c1c"
            + "100d0070d4100d0064fc1005006c1cf80d008258f801008a78100d0078581001008078e009029148e00d02976000");
    private Fixture retained;
    private record Fixture(CdR11bAssets assets, CdTimeWarpPrograms programs, CdMmdImage present) { }

    @Test void onlyTwoCompleteRawViewsAppendAfter131WithoutNewPlcSourcesOrReaders() throws Exception {
        var f = retail(); var a = f.assets();
        int[] offsets = {0xE268, 0xE280}; int[] lengths = {24, 622};
        String[] labels = {"ObjResults_InitData", "MapSpr_ResultsBad/MapSpr_ResultsBadSSZ3/MapSpr_ResultsGood/MapSpr_ResultsGoodSSZ3"};
        String[] hashes = {"8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8", "3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9"};
        assertEquals(133, CdR11bAssets.Asset.values().length);
        assertEquals(KEYS, Arrays.asList(CdR11bAssets.Asset.values()).subList(131, 133));
        for (int i = 0; i < KEYS.size(); i++) {
            var key = KEYS.get(i); var view = a.view(key);
            assertEquals(131 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(lengths[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(CdNativeFile.R11B, view.range().file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, view.range().domain());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertEquals(hashes[i], sha(view.bytes().copy()));
            assertArrayEquals(f.programs().r11b().readFile(offsets[i], lengths[i]), view.bytes().copy());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IllegalArgumentException.class, () -> new CdR11bLoadPlan.PlcEntry(key, key.range(), 0x7880));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(key.length()));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
    }

    @Test void all131PriorDescriptorsAndImplementationOwnedSourcesRemainExact() throws Exception {
        var metadata = new StringBuilder();
        for (var key : Arrays.asList(CdR11bAssets.Asset.values()).subList(0, 131)) {
            metadata.append(key.ordinal()).append('|').append(key.name()).append('|').append(key.nativeLabel())
                    .append('|').append(key.fileOffset()).append('|').append(key.length()).append('|').append(key.isPlcSource()).append('|');
            if (key.readerProfile().isPresent()) {
                var p = key.readerProfile().orElseThrow();
                metadata.append(p.javaInputRange().length()).append('|').append(p.nativeInputRange().length())
                        .append('|').append(p.outputBytes()).append('|').append(p.javaConsumedInputBytes());
                assertEquals(key.range(), p.sourceBlobRange());
            } else metadata.append('-');
            metadata.append('\n');
        }
        assertEquals("8fe17e372f6417746c6641d09d1e0b7908f78a3924a2eb28e6b98128c2a1b002", sha(metadata.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(127, ANI_BIG_RING.ordinal()); assertEquals(130, MAP_SPR_BIG_RING_FLASH.ordinal());
        assertEquals(125, ANI_SIGNPOST.ordinal()); assertEquals(126, LEVEL_END_TIME_BONUSES.ordinal());
    }

    @Test void actualId3ADispatchAuthenticatesByteDelayWordTimersAndReachedOwnerOperands() throws Exception {
        var f = retail(); var image = f.programs().r11b();
        assertEquals(0x20DFA8L, f.assets().view(OBJECT_INDEX).bytes().unsignedLong((0x3A - 1) * 4));
        assertArrayEquals(hex("0020dfa8"), image.readFile(0x3D1C, 4));
        assertArrayEquals(hex("700010280024303b00064efb0002"), image.readFile(0xDFA8, 14));
        var directory = CdAssetBytes.copyOf(image.readFile(0xDFB6, 10));
        int[] routines = {0x20DFC0, 0x20DFD4, 0x20E0A0, 0x20E0DA, 0x20E18E};
        for (int i = 0; i < routines.length; i++) assertEquals(routines[i], 0x20DFB6 + (short) directory.unsignedWord(i * 2));
        assertArrayEquals(hex("4ab8f680661c0c79050200ff150667144df8d0003038f70006400150b06e000865024e75"), image.readFile(0xDFD4, 36));
        assertArrayEquals(hex("53280032"), image.readFile(0xDFC0, 4)); // byte_init_delay
        assertArrayEquals(hex("70104eb9002024e0"), image.readFile(0xDFC8, 8)); // load_results_cue
        assertArrayEquals(hex("317c01e00032"), image.readFile(0xE004, 6)); // parent_usa_timer
        assertArrayEquals(hex("337c01e00032"), image.readFile(0xE012, 6)); // row_usa_timer
        assertArrayEquals(hex("4eb9002076a0"), image.readFile(0xE00C, 6)); // find_row_slot
        assertArrayEquals(hex("4a680032670453680032"), image.readFile(0xE0A0, 10)); // word_timer_test_decrement
        assertArrayEquals(hex("082800000032"), image.readFile(0xE16E, 6)); // high_timer_byte_btst
        assertArrayEquals(hex("13fc009a00a01c09"), image.readFile(0xE146, 8)); // driver_kaching
        assertArrayEquals(hex("303c00bd4eb900202316"), image.readFile(0xE176, 10)); // tally_fm
        assertArrayEquals(hex("303c00c84eb900202316"), image.readFile(0xE108, 10)); // special_stage_warp_fm
        assertArrayEquals(hex("33fc000200ff1502"), image.readFile(0xE18E, 8)); // next_level_first_store
    }

    @Test void completeThreeRowInitMacroAndSelectedLeaDisambiguateTheDuplicateData() throws Exception {
        var image = retail().programs().r11b(); var bytes = retail().assets().view(RESULTS_INIT_DATA).bytes();
        assertArrayEquals(INIT_DATA, bytes.copy());
        int[][] rows = {{204, 0, 288, 0}, {272, 512, 240, 1}, {204, 0, 288, 2}};
        for (int row = 0; row < rows.length; row++) for (int word = 0; word < 4; word++)
            assertEquals(rows[row][word], bytes.unsignedWord(row * 8 + word * 2));
        assertArrayEquals(hex("45f90020e268"), image.readFile(0xDFF8, 6));
        assertArrayEquals(INIT_DATA, image.readFile(0xF4BC, 24)); // Equal source bytes do not choose this unselected copy.
        assertArrayEquals(hex("3401e74a33722000000a33722002000833722004002a13722007001a0c010002660a143900ff1507d529001a520151ceff704e75"), image.readFile(0xE06C, 52));
        assertEquals(MAP_SPR_RESULTS.range().nativeAddress(), RESULTS_INIT_DATA.range().endExclusive());
    }

    @Test void completeSharedMappingsKeepFourRootsEveryAliasAllCountedPiecesAndRealEvenBytes() throws Exception {
        var image = retail().programs().r11b(); var bytes = retail().assets().view(MAP_SPR_RESULTS).bytes();
        assertArrayEquals(MAPPINGS, bytes.copy()); assertArrayEquals(MAPPINGS, image.readFile(0xF4D4, 622));
        int[] roots = {0, 10, 20, 30};
        int[][] targets = {{40, 478, 82, 134, 186}, {40, 550, 82, 134, 186},
                {238, 478, 310, 366, 422}, {238, 550, 310, 366, 422}};
        int[][] counts = {{8, 14, 10, 10, 10}, {8, 14, 10, 10, 10}, {14, 14, 11, 11, 11}, {14, 14, 11, 11, 11}};
        int[][] tileEnds = {{26, 461, 54, 78, 82}, {26, 671, 54, 78, 82}, {70, 461, 74, 78, 82}, {70, 671, 74, 78, 82}};
        var tables = new ArrayList<CdObjectMappings.DeclaredTable>();
        for (int root : roots) tables.add(new CdObjectMappings.DeclaredTable(MAP_SPR_RESULTS.range().nativeAddress(), bytes, root, 5));
        var mappings = CdObjectMappings.declaredSourceViews(tables, List.of());
        for (int table = 0; table < roots.length; table++) for (int frame = 0; frame < 5; frame++) {
            assertEquals(targets[table][frame], roots[table] + (short) bytes.unsignedWord(roots[table] + 2 * frame));
            var body = assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,
                    mappings.resolve(MAP_SPR_RESULTS.range().nativeAddress() + roots[table], false, frame));
            assertEquals(counts[table][frame], body.countByte()); assertEquals(counts[table][frame], body.pieces().size());
            int max = body.pieces().stream().mapToInt(p -> (p.rawTileWord() & 0x7FF)
                    + ((p.sizeByte() >>> 2) + 1) * ((p.sizeByte() & 3) + 1)).max().orElseThrow();
            assertEquals(tileEnds[table][frame], max);
        }
        for (int at : new int[]{81, 133, 185, 237, 309, 549, 621}) assertEquals(0, MAPPINGS[at]);
        assertEquals(0x60, MAPPINGS[620] & 255); // Last real X byte; outer even at621 is genuine.
        assertEquals(0x20E4EE, MAP_SPR_RESULTS.range().endExclusive());
        assertArrayEquals(hex("237c0020e28a0004"), image.readFile(0xE03A, 8));
        assertArrayEquals(hex("237c0020e29e0004"), image.readFile(0xE04A, 8));
        assertArrayEquals(hex("237c0020e2800004"), image.readFile(0xE054, 8));
        assertArrayEquals(hex("237c0020e2940004"), image.readFile(0xE064, 8));
    }

    @Test void existingCue16ResultsReaderAndHudPatternIntervalsAlreadyOwnTheActualPastClosure() throws Exception {
        var a = retail().assets(); var cue = a.loadPlan().plcs().get(0x10);
        assertEquals(List.of(ART_RESULTS), cue.entries().stream().map(CdR11bLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x7880), cue.entries().stream().map(CdR11bLoadPlan.PlcEntry::vramAddress).toList());
        assertEquals(0x33432, ART_RESULTS.fileOffset()); assertEquals(1354, ART_RESULTS.length());
        var profile = ART_RESULTS.readerProfile().orElseThrow();
        assertEquals(1354, profile.javaInputRange().length()); assertEquals(1354, profile.nativeInputRange().length());
        assertEquals(1354, profile.javaConsumedInputBytes()); assertEquals(120 * 32, profile.outputBytes());
        assertEquals(0x8078, a.view(ART_RESULTS).bytes().unsignedWord(0)); // Existing XOR-form120-tile Nemesis source.
        var ordinary = CdR11bCameraArtResidency.Region.AT_6B40;
        assertTrue(0x7880 >= ordinary.destinationWord()); assertTrue(0x8A00 <= ordinary.destinationWord() + ordinary.byteLength());
        assertEquals(0x8780, (0x03C4 + 0x78) * 32); assertEquals(0x88C0, (0x03C4 + 0x82) * 32);
        assertEquals(0xB060, (0x03C4 + 0x1BF) * 32); assertEquals(0xB220, (0x03C4 + 0x1CD) * 32);
        var score = CdR11bCameraArtResidency.Region.AT_B060;
        assertTrue(0xB060 >= score.destinationWord()); assertTrue(0xB220 <= score.destinationWord() + score.byteLength());
        assertEquals(1024, ART_HUD_NUMBERS.length()); // Existing glyph source and HUD writer, no Results fallback art.
    }

    @Test void rawViewsStayImmutableAndRetainEveryExistingAssetOwnerIdentity() throws Exception {
        var f = retail(); var a = f.assets(); var terrain = a.terrain(); var plan = a.loadPlan();
        var placements = a.placements(); var sonic = a.sonic(); var player = a.player();
        for (var key : KEYS) {
            var bytes = a.view(key).bytes(); var copy = bytes.copy(); int first = bytes.unsignedByte(0); copy[0] ^= 0x55;
            assertEquals(first, a.view(key).bytes().unsignedByte(0)); assertNotEquals(first, copy[0] & 255);
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 1));
            assertNotEquals(sha(bytes.copy()), sha(f.present().readFile(key.fileOffset(), key.length())));
        }
        assertSame(terrain, a.terrain()); assertSame(plan, a.loadPlan()); assertSame(placements, a.placements());
        assertSame(sonic, a.sonic()); assertSame(player, a.player());
    }

    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var programs = CdTimeWarpPrograms.fromPrograms(source); var assets = CdR11bAssets.fromPrograms(programs);
            assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", assets.identity().mmdSha256());
            retained = new Fixture(assets, programs, source.mmd(CdNativeFile.R11A));
        }
        return retained;
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
