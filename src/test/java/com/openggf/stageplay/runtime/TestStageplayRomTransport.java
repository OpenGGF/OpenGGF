package com.openggf.stageplay.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Exact process-boundary ROM windows, without real ROMs, GL or a gameplay session. */
class TestStageplayRomTransport {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void wholeImageRetainsItsBytesAndLeavesOnlyTheOriginalFile() throws Exception {
        var image = image("original.gen", 1, 2, 3, 4);
        var resolution = resolution(RomIdentity.S1, part(RomIdentity.S1, image, 0, 4));
        String descriptor = StageplayRomTransport.encode(Map.of("s1", resolution));
        var reader = StageplayRomTransport.open(descriptor).get("s1");
        assertEquals(4, reader.size());
        assertEquals(0x01020304, reader.readU32BE(0));
        try (var paths = Files.list(directory)) {
            assertEquals(List.of(image.path()), paths.toList(), "Transport must not write a joined/copied ROM");
        }
    }

    @Test void preservesWindowsFromMultipleFilesAndReadsAcrossTheCompositeSeam() throws Exception {
        var lower = image("lower.gen", 99, 10, 11, 12, 98);
        var upper = image("upper.gen", 88, 20, 21, 87);
        var resolution = resolution(RomIdentity.S3K,
                part(RomIdentity.SK, lower, 1, 3), part(RomIdentity.S3, upper, 1, 2));
        var reader = StageplayRomTransport.open(StageplayRomTransport.encode(Map.of("s3k", resolution))).get("s3k");
        assertArrayEquals(new byte[]{10, 11, 12, 20, 21}, reader.slice(0, reader.size()));
        assertEquals(0x0B0C1415, reader.readU32BE(1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readU8(5));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readU8(-1));
    }

    @Test void onePhysicalDumpCanSupplySeveralNamedViewsWithoutReclassifyingThem() throws Exception {
        var dump = image("lock-on.gen", 1, 2, 3, 4, 5, 6);
        String descriptor = StageplayRomTransport.encode(Map.of(
                "s1", resolution(RomIdentity.S1, part(RomIdentity.S1, dump, 2, 2)),
                "s3k", resolution(RomIdentity.S3K, part(RomIdentity.SK, dump, 0, 2), part(RomIdentity.S3, dump, 4, 2))));
        var readers = StageplayRomTransport.open(descriptor);
        assertEquals(0x0304, readers.get("s1").readU16BE(0));
        assertEquals(0x01020506, readers.get("s3k").readU32BE(0));
        assertThrows(UnsupportedOperationException.class, () -> readers.clear());
    }

    @Test void sourceReplacementOrDeletionFailsClosedInsteadOfSelectingAnotherFile() throws Exception {
        var image = image("supplied.gen", 1, 2, 3, 4);
        String descriptor = StageplayRomTransport.encode(Map.of("s2", resolution(RomIdentity.S2, part(RomIdentity.S2, image, 1, 2))));
        Files.write(image.path(), new byte[]{1, 9, 3, 4});
        assertTrue(assertThrows(IOException.class, () -> StageplayRomTransport.open(descriptor)).getMessage().contains("bytes changed"));
        Files.write(image.path(), new byte[]{1, 2, 3});
        assertTrue(assertThrows(IOException.class, () -> StageplayRomTransport.open(descriptor)).getMessage().contains("size changed"));
        Files.delete(image.path());
        assertTrue(assertThrows(IOException.class, () -> StageplayRomTransport.open(descriptor)).getMessage().contains("Missing"));
    }

    @Test void unknownFieldsVersionsIdentitiesAndInvalidWindowBoundsAreRejected() throws Exception {
        var image = image("supplied.gen", 1, 2, 3, 4);
        String encoded = StageplayRomTransport.encode(Map.of("s1", resolution(RomIdentity.S1, part(RomIdentity.S1, image, 0, 4))));
        rejects(encoded, root -> root.put("version", 2));
        rejects(encoded, root -> root.put("unknown", true));
        rejects(encoded, root -> ((ObjectNode) root.path("games").path("s1")).put("rom", "S2"));
        rejects(encoded, root -> firstPart(root).put("rom", "invented"));
        rejects(encoded, root -> firstPart(root).put("path", "relative.gen"));
        rejects(encoded, root -> firstPart(root).put("offset", -1));
        rejects(encoded, root -> firstPart(root).put("offset", 1));
        rejects(encoded, root -> firstPart(root).put("length", 0));
        rejects(encoded, root -> firstPart(root).put("length", 1.5));
        rejects(encoded, root -> firstPart(root).put("size", Long.MAX_VALUE));
        rejects(encoded, root -> firstPart(root).put("sha1", "not-a-fingerprint"));
        rejects(encoded, root -> firstPart(root).put("unknown", true));
        assertThrows(IOException.class, () -> StageplayRomTransport.open("{\"version\":1,\"version\":1,\"games\":{}}"));
        assertThrows(IOException.class, () -> StageplayRomTransport.open(encoded + " {}"));
        assertThrows(IOException.class, () -> StageplayRomTransport.open(" ".repeat(StageplayRomTransport.MAX_DESCRIPTOR_CHARS + 1)));
        assertThrows(IOException.class, () -> StageplayRomTransport.open(null));
        assertThrows(IOException.class, () -> StageplayRomTransport.open("null"));
        assertThrows(IOException.class, () -> StageplayRomTransport.open("{\"version\":1,\"games\":{}}"));
    }

    @Test void producerRejectsMismatchedGameAndInvalidParts() throws Exception {
        var image = image("supplied.gen", 1, 2, 3, 4);
        assertThrows(IOException.class, () -> StageplayRomTransport.encode(Map.of("s2", resolution(RomIdentity.S1, part(RomIdentity.S1, image, 0, 4)))));
        assertThrows(IOException.class, () -> StageplayRomTransport.encode(Map.of("s1", resolution(RomIdentity.S1))));
        assertThrows(IOException.class, () -> StageplayRomTransport.encode(Map.of("s1", resolution(RomIdentity.S1, part(RomIdentity.S1, image, 3, 2)))));
        assertThrows(IOException.class, () -> StageplayRomTransport.encode(Map.of()));
    }

    @Test void gamesIncludeCrossGameCastAndRomAudioButNotVoicePlaceholderGames() {
        var scene=StageplayDocument.empty("s1",0,0,0,0,320,224);
        var actor=new StageplayDocument.Actor("guest","Guest","s2","tails",List.of(
                new StageplayDocument.ActorKey(0,0,0,0,-1,false,true,1,StageplayDocument.Interpolation.HOLD)));
        scene=scene.withActors(List.of(actor)).withAudio(List.of(
                new StageplayDocument.AudioCue("voice",0,StageplayDocument.AudioKind.VOICE,"s1",-1,"voice.wav",1),
                new StageplayDocument.AudioCue("music",0,StageplayDocument.AudioKind.ROM_MUSIC,"s3k",1,"",1)));
        assertEquals(List.of("s1","s2","s3k"),StageplayRomTransport.requiredGames(scene));
    }

    @Test void producerRejectsAggregateLogicalOverflowBeforeLoadingThePhysicalImage() throws Exception {
        Path file=directory.resolve("large.gen");
        try(var channel=java.nio.channels.FileChannel.open(file,java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(64L*1024*1024-1);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        var image=PhysicalImage.probe(file);
        // Removing the file after probing proves that rejection precedes any hashing/body read.
        Files.delete(file);
        var resolution=resolution(RomIdentity.S3K,part(RomIdentity.SK,image,0,32*1024*1024),
                part(RomIdentity.S3,image,32*1024*1024,32*1024*1024),part(RomIdentity.S3,image,0,1));
        assertTrue(assertThrows(IOException.class,()->StageplayRomTransport.encode(Map.of("s3k",resolution)))
                .getMessage().contains("Logical ROM exceeds limit"));
    }

    @Test void metadataFileReaderChecksRegularFileBytesCharactersAndUtf8() throws Exception {
        var image=image("original.gen",1,2,3,4);
        String encoded=StageplayRomTransport.encode(Map.of("s1",resolution(RomIdentity.S1,part(RomIdentity.S1,image,0,4))));
        Path descriptor=directory.resolve("descriptor.json");
        Files.writeString(descriptor,encoded);
        assertEquals(0x01020304,StageplayRomTransport.openFile(descriptor).get("s1").readU32BE(0));
        Files.writeString(descriptor,encoded+" {}");
        assertThrows(IOException.class,()->StageplayRomTransport.openFile(descriptor));
        Files.write(descriptor,new byte[StageplayRomTransport.MAX_DESCRIPTOR_CHARS*4+1]);
        assertThrows(IOException.class,()->StageplayRomTransport.openFile(descriptor));
        Files.writeString(descriptor," ".repeat(StageplayRomTransport.MAX_DESCRIPTOR_CHARS+1));
        assertThrows(IOException.class,()->StageplayRomTransport.openFile(descriptor));
        Files.write(descriptor,new byte[]{(byte)0xC3,(byte)0x28});
        assertThrows(IOException.class,()->StageplayRomTransport.openFile(descriptor));
        assertThrows(IOException.class,()->StageplayRomTransport.openFile(directory));
    }

    private void rejects(String encoded, Consumer<ObjectNode> mutate) throws Exception {
        var root = (ObjectNode) JSON.readTree(encoded);
        mutate.accept(root);
        assertThrows(IOException.class, () -> StageplayRomTransport.open(root.toString()), root.toString());
    }

    private static ObjectNode firstPart(ObjectNode root) {
        return (ObjectNode) root.path("games").path("s1").path("parts").get(0);
    }

    private PhysicalImage image(String name, int... values) throws IOException {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return PhysicalImage.probe(Files.write(directory.resolve(name), bytes));
    }

    private static RomImageCatalogue.Part part(RomIdentity identity, PhysicalImage image, int offset, int length) {
        return new RomImageCatalogue.Part(identity, image, offset, length);
    }

    private static RomImageCatalogue.Resolution resolution(RomIdentity identity, RomImageCatalogue.Part... parts) {
        return new RomImageCatalogue.Resolution(identity, List.of(parts), RomImageCatalogue.Source.COMPOSITE);
    }
}
