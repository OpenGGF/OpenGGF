package com.openggf.level.objects;

import com.openggf.tools.modsdk.PlayableSheetWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestPlayableSheetV2 {
    private static final String GOLDEN = "474746500002000442415345000000350000003147474653000100000001" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "000100000000004d4554410000000c0000000000000001000000004652414d00000012" +
            "000100000000001000100001000000010000414e494d000000100001000469646c65" +
            "0001000000010100";

    @Test void canonicalGoldenBytesRoundTrip() throws Exception {
        PlayableSheetReader.PlayableSheet sheet = minimal();
        byte[] encoded = PlayableSheetWriter.write(sheet);
        assertEquals(GOLDEN, HexFormat.of().formatHex(encoded));
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "mods/playable-v2-minimal.hex")) {
            assertNotNull(input);
            assertEquals(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim(),
                    HexFormat.of().formatHex(encoded));
        }
        assertEquals(sheet, PlayableSheetReader.read(encoded));
    }

    @Test void rejectsDuplicateMissingOutOfOrderReservedAndDanglingData() throws Exception {
        byte[] valid = PlayableSheetWriter.write(minimal());
        byte[] badVersion = valid.clone(); badVersion[5] = 3;
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(badVersion));
        byte[] flags = valid.clone();
        int metaPayload = indexOf(flags, "META".getBytes()) + 8; flags[metaPayload + 9] = 1;
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(flags));
        byte[] dangling = valid.clone();
        int animPayload = indexOf(dangling, "ANIM".getBytes()) + 8;
        dangling[animPayload + 10] = 0; dangling[animPayload + 11] = 2;
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(dangling));
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(java.util.Arrays.copyOf(valid, valid.length - 1)));

        List<Section> sections = sections(valid);
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(container(List.of(
                sections.get(0), sections.get(0), sections.get(2), sections.get(3)))));
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(container(List.of(
                sections.get(0), sections.get(2), sections.get(3),
                new Section(0x80000001, new byte[0])))));
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(container(List.of(
                sections.get(1), sections.get(0), sections.get(2), sections.get(3)))));
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(container(List.of(
                sections.get(0), sections.get(1), sections.get(2), new Section(0x5A5A5A5A, sections.get(3).payload())))));

        byte[] invalidUtf8 = valid.clone();
        int idle = indexOf(invalidUtf8, "idle".getBytes()); invalidUtf8[idle] = (byte) 0xFF;
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(invalidUtf8));
        var tooSmall = com.openggf.io.ModInputLimits.loweringBuilder()
                .maxAssetBytes(valid.length - 1L).build();
        assertThrows(java.io.IOException.class, () -> PlayableSheetReader.read(valid, tooSmall));
    }

    @Test void rejectsNonCanonicalAnimationAndAppendageKeys() throws Exception {
        byte[] invalidAnimation = PlayableSheetWriter.write(minimal()).clone();
        invalidAnimation[indexOf(invalidAnimation, "idle".getBytes())] = 'I';
        assertThrows(java.io.IOException.class,
                () -> PlayableSheetReader.read(invalidAnimation));

        PlayableSheetReader.PlayableSheet invalidAppendage = new PlayableSheetReader.PlayableSheet(
                minimalGgfs(), new PlayableSheetReader.Meta(0, 1, 0),
                minimal().frames(), minimal().animations(), Map.of("owner::tail", List.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayableSheetWriter.write(invalidAppendage));
    }

    @Test void unsignedUtf8ComparatorUsesEncodedByteOrder() {
        assertTrue(PlayableSheetReader.compareUtf8("\uE000", "\uD83D\uDE00") < 0,
                "UTF-8 sorts U+E000 before supplementary U+1F600");
        assertTrue("\uE000".compareTo("\uD83D\uDE00") > 0,
                "Fixture must distinguish UTF-16 ordering");
    }

    @Test void canonicalWriterRejectsValuesThatBinaryFieldsWouldMask() throws Exception {
        var base = minimal();
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(
                withFrame(base, new PlayableSheetReader.Frame(32768, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 0))))));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(
                withFrame(base, new PlayableSheetReader.Frame(0, 0, 65537, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 0))))));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(
                withFrame(base, new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(65536, 1, 0))))));

        var badStep = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                base.frames(), Map.of("idle", new PlayableSheetReader.Animation(
                List.of(new PlayableSheetReader.AnimationStep(65536, 1, true)))), Map.of());
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(badStep));

        var badAppendage = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                base.frames(), base.animations(), Map.of("owner:tail", List.of(65536)));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(badAppendage));
    }

    @Test void rejectsMetaPatternBankSpanOverflow() throws Exception {
        var base = minimal();
        var overflow = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(),
                new PlayableSheetReader.Meta(Integer.MAX_VALUE, 1, 0), base.frames(),
                base.animations(), base.appendages());
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(overflow));
    }

    @Test void rejectsHostileHugeBankSizeFromTinyContainerBeforeRuntimeAllocation() throws Exception {
        byte[] hostile = PlayableSheetWriter.write(minimal()).clone();
        int metaPayload = indexOf(hostile, "META".getBytes()) + 8;
        java.nio.ByteBuffer.wrap(hostile, metaPayload + 4, 4).putInt(Integer.MAX_VALUE);

        java.io.IOException rejected = assertThrows(java.io.IOException.class,
                () -> PlayableSheetReader.read(hostile));

        assertTrue(rejected.getMessage().contains("bank size exceeds limit"), rejected.getMessage());
    }

    @Test void appendagesRoundTripAndUnknownOptionalSectionsAreSkipped() throws Exception {
        var base = minimal();
        var withAppendage = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                base.frames(), base.animations(), Map.of("owner:tail.v1", List.of(0)));
        byte[] encoded = PlayableSheetWriter.write(withAppendage);
        assertEquals(withAppendage, PlayableSheetReader.read(encoded));
        List<Section> values = sections(encoded);
        values = new java.util.ArrayList<>(values);
        values.add(new Section(0x80000001, new byte[]{1, 2, 3}));
        assertEquals(withAppendage, PlayableSheetReader.read(container(values)));
    }

    @Test void exactFrameAndDplcBoundariesRejectMismatchAndOnePastEnd() throws Exception {
        var base = minimal();
        var noFrames = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                List.of(), base.animations(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(noFrames));
        assertDoesNotThrow(() -> PlayableSheetWriter.write(base));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(withFrame(base,
                new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(1, 1, 0))))));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(withFrame(base,
                new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 1))))));
    }

    @Test void sharedKeyGrammarAcceptsDotsAndRejectsUnicodeAndMalformedNamespaces() throws Exception {
        var base = minimal();
        var dotted = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                base.frames(), Map.of("idle.fast", base.animations().get("idle")),
                Map.of("owner:tail.v1", List.of(0)));
        assertDoesNotThrow(() -> PlayableSheetWriter.write(dotted));
        assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(
                new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(), base.frames(),
                        Map.of("idé", base.animations().get("idle")), Map.of())));
        for (String bad : List.of("owner::tail", "Owner:tail", "owner:", "owner:../tail", "tail")) {
            var invalid = new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                    base.frames(), base.animations(), Map.of(bad, List.of(0)));
            assertThrows(IllegalArgumentException.class, () -> PlayableSheetWriter.write(invalid), bad);
        }
    }

    private static PlayableSheetReader.PlayableSheet withFrame(
            PlayableSheetReader.PlayableSheet base, PlayableSheetReader.Frame frame) {
        return new PlayableSheetReader.PlayableSheet(base.baseSheetV1(), base.meta(),
                List.of(frame), base.animations(), base.appendages());
    }

    private static PlayableSheetReader.PlayableSheet minimal() throws Exception {
        return new PlayableSheetReader.PlayableSheet(minimalGgfs(),
                new PlayableSheetReader.Meta(0, 1, 0),
                List.of(new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 0)))),
                Map.of("idle", new PlayableSheetReader.Animation(
                        List.of(new PlayableSheetReader.AnimationStep(0, 1, true)))), Map.of());
    }

    private static byte[] minimalGgfs() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFS"); out.writeShort(1); out.writeInt(1); out.write(new byte[32]);
            out.writeShort(1); out.writeShort(0); out.writeShort(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) { for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer; return i; }
        throw new AssertionError("missing tag");
    }

    private static List<Section> sections(byte[] container) throws Exception {
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(container))) {
            in.readInt(); in.readUnsignedShort(); int count = in.readUnsignedShort();
            java.util.ArrayList<Section> result = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) { int tag = in.readInt(); int length = in.readInt(); result.add(new Section(tag, in.readNBytes(length))); }
            return result;
        }
    }

    private static byte[] container(List<Section> sections) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFP"); out.writeShort(2); out.writeShort(sections.size());
            for (Section section : sections) { out.writeInt(section.tag()); out.writeInt(section.payload().length); out.write(section.payload()); }
        }
        return bytes.toByteArray();
    }

    private record Section(int tag, byte[] payload) {}
}
