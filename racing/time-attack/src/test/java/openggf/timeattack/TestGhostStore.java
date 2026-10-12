package openggf.timeattack;

import openggf.timeattack.ghost.GhostFileCodec;
import openggf.timeattack.ghost.GhostHeader;
import openggf.timeattack.ghost.GhostRecording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostStore {
    @Test
    void rejectedCandidateNeverMovesOrReplacesExistingPrimaryPair(@TempDir Path root) throws IOException {
        GhostStore store=new GhostStore(root);
        store.saveIfBest(ghost(0,4000),inputs());
        Path primary=root.resolve("s3k/0-0-sonic.ggfghost"),sidecar=root.resolve("s3k/0-0-sonic.ggfinputs");
        byte[] before=Files.readAllBytes(primary),inputBefore=Files.readAllBytes(sidecar);
        GhostRecording invalid=new GhostRecording(ghost(0,3000).header(),
                new byte[(GhostFileCodec.MAX_FRAMES+1)*7]);
        assertThrows(IOException.class,()->store.saveIfBest(invalid,inputs()));
        assertTrue(Files.exists(primary),"validate the candidate before moving the prior best");
        assertArrayEquals(before,Files.readAllBytes(primary));
        assertArrayEquals(inputBefore,Files.readAllBytes(sidecar));
    }

    @Test
    void malformedLegacySidecarIsSkippedWithoutUncheckedSceneFault(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("s3k"));
        Files.write(root.resolve("s3k/broken.ggfinputs"),new byte[]{0});
        GhostStore store=new GhostStore(root);
        assertTrue(store.findInputRecording(java.util.HexFormat.of().formatHex(inputs().sha256())).isEmpty());
        Files.write(root.resolve("s3k/good.ggfinputs"),inputs().encode());
        assertArrayEquals(inputs().encode(),store.findInputRecording(
                java.util.HexFormat.of().formatHex(inputs().sha256())).orElseThrow());
    }

    @Test
    void rejectedPrimaryUsesPreviousForPlaybackWithoutGrantingOverwrite(@TempDir Path root) throws IOException {
        GhostStore store=new GhostStore(root);
        store.saveIfBest(ghost(0,4000),inputs());
        store.saveIfBest(ghost(0,3800),inputs());
        Path primary=root.resolve("s3k/0-0-sonic.ggfghost");
        byte[] rejected=Files.readAllBytes(primary);rejected[9]=99;Files.write(primary,rejected);
        assertEquals(4000,store.loadBest("s3k",0,0,"sonic").orElseThrow().header().finalTimeFrames());
        assertThrows(IOException.class,()->store.saveIfBest(ghost(0,3000),inputs()));
        assertArrayEquals(rejected,Files.readAllBytes(primary));
    }

    @Test
    void failedAtomicPublicationRetainsOldPrimaryAndLegacyPair(@TempDir Path root) throws IOException {
        GhostStore initial=new GhostStore(root);initial.saveIfBest(ghost(0,4000),inputs());
        Path primary=root.resolve("s3k/0-0-sonic.ggfghost").toAbsolutePath();
        Path alias=root.resolve("s3k/0-0-sonic.ggfinputs");
        byte[] before=Files.readAllBytes(primary),inputBefore=Files.readAllBytes(alias);
        GhostStore failing=new GhostStore(root,(from,to)-> {
            if(to.equals(primary))throw new IOException("injected atomic publication failure");
            Files.move(from,to,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        });
        assertEquals(GhostStore.SaveStatus.FAILED,failing.saveBest(ghost(0,3000),inputs()).status());
        assertArrayEquals(before,Files.readAllBytes(primary));assertArrayEquals(inputBefore,Files.readAllBytes(alias));
        try(var files=Files.walk(root)) { assertFalse(files.anyMatch(p->p.getFileName().toString().endsWith(".tmp"))); }
    }

    @Test
    void hashSidecarKeepsCommittedPairUsableWhenLegacyAliasMaintenanceFails(@TempDir Path root) throws IOException {
        GhostStore initial=new GhostStore(root);initial.saveIfBest(ghost(0,4000),inputs());
        AttemptInputRecording changed=inputs();changed.appendFrame(0x04,false);
        GhostRecording faster=new GhostRecording(new GhostHeader(1,"s3k",0,0,"sonic","x",0,3000,
                new int[0],changed.sha256()),new byte[7]);
        Path alias=root.resolve("s3k/0-0-sonic.ggfinputs").toAbsolutePath();
        byte[] original=Files.readAllBytes(alias);
        GhostStore failingAlias=new GhostStore(root,(from,to)-> {
            if(to.equals(alias))throw new IOException("injected alias maintenance failure");
            Files.move(from,to,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        });
        var saved=failingAlias.saveBest(faster,changed);
        assertTrue(saved.saved());assertFalse(saved.notice().isEmpty());
        assertArrayEquals(original,Files.readAllBytes(alias));
        assertEquals(3000,new GhostStore(root).loadBest("s3k",0,0,"sonic").orElseThrow().header().finalTimeFrames());
        assertArrayEquals(changed.encode(),new GhostStore(root).findInputRecording(
                java.util.HexFormat.of().formatHex(changed.sha256())).orElseThrow());
        assertTrue(new GhostStore(root).saveIfBest(ghost(0,2500),inputs()),"stale advisory alias must not block the next publication");
        Path previous=root.resolve("s3k/0-0-sonic-prev1.ggfinputs");
        assertArrayEquals(changed.encode(),Files.readAllBytes(previous),"history copies the hash-selected pair");
    }

    @Test
    void sidecarLookupBoundsBytesAndVisits(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("s3k"));
        Path big=root.resolve("s3k/big.ggfinputs");
        try(var channel=java.nio.channels.FileChannel.open(big,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(AttemptInputRecording.MAX_ENCODED_BYTES+1);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        assertTrue(new GhostStore(root).findInputRecording("00".repeat(32)).isEmpty());
        for(int i=0;i<GhostStore.MAX_SCAN_SIDECARS;i++)Files.write(root.resolve("s3k/"+i+".ggfinputs"),new byte[]{0});
        assertThrows(IOException.class,()->new GhostStore(root).findInputRecording("00".repeat(32)));
        Path hashed=root.resolve("s3k/0-0-sonic.recordings");Files.createDirectories(hashed);
        String hash=java.util.HexFormat.of().formatHex(inputs().sha256());
        Files.write(hashed.resolve(hash+".ggfinputs"),inputs().encode());
        assertArrayEquals(inputs().encode(),new GhostStore(root).findInputRecording(hash).orElseThrow(),
                "bounded legacy decode exhaustion must still permit hash-directed lookup");
    }

    @Test
    void retainedHashSidecarsStayBoundedToThreeDistinctBestPairs(@TempDir Path root) throws IOException {
        GhostStore store=new GhostStore(root);
        String oldestHash=null;
        for(int i=0;i<8;i++) {
            AttemptInputRecording recording=new AttemptInputRecording(new AttemptStartDescriptor("s3k",0,0,"sonic","fp"));
            recording.appendFrame(i,false);
            GhostRecording candidate=new GhostRecording(new GhostHeader(1,"s3k",0,0,"sonic","x",0,6000-i*500,
                    new int[0],recording.sha256()),new byte[7]);
            assertTrue(store.saveIfBest(candidate,recording));
            if(i==0)oldestHash=java.util.HexFormat.of().formatHex(recording.sha256());
        }
        try(var files=Files.list(root.resolve("s3k/0-0-sonic.recordings"))) { assertEquals(3,files.count()); }
        assertTrue(store.findInputRecording(oldestHash).isEmpty());
        for(String suffix:java.util.List.of("","-prev1","-prev2")) {
            GhostRecording retained=GhostFileCodec.read(root.resolve("s3k/0-0-sonic"+suffix+".ggfghost"));
            assertTrue(store.findInputRecording(java.util.HexFormat.of().formatHex(retained.header().inputRecordingHash())).isPresent());
        }
    }
    private static AttemptInputRecording inputs() {
        AttemptInputRecording rec = new AttemptInputRecording(
                new AttemptStartDescriptor("s3k", 0, 0, "sonic", "fp"));
        rec.appendFrame(0x08, false);
        return rec;
    }

    private static GhostRecording ghost(int firstInput, int finish) {
        byte[] frames = new byte[7];
        // Header hash must match the inputs the store persists alongside (binding enforced).
        return new GhostRecording(new GhostHeader(1, "s3k", 0, 0, "sonic", "p",
                firstInput, finish, new int[0], inputs().sha256()), frames);
    }

    @Test
    void savesFirstRunAsBestWithInputSidecar(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        assertTrue(store.saveIfBest(ghost(0, 3600), inputs()));
        assertTrue(Files.exists(root.resolve("s3k").resolve("0-0-sonic.ggfghost")));
        assertTrue(Files.exists(root.resolve("s3k").resolve("0-0-sonic.ggfinputs")));
        assertEquals(3600, store.loadBest("s3k", 0, 0, "sonic").orElseThrow()
                .header().finalTimeFrames());
    }

    @Test
    void rejectsSlowerRunKeepsBest(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        store.saveIfBest(ghost(0, 3600), inputs());
        assertFalse(store.saveIfBest(ghost(0, 4000), inputs()));
        assertEquals(3600, store.loadBest("s3k", 0, 0, "sonic").orElseThrow()
                .header().finalTimeFrames());
    }

    @Test
    void rotatesPreviousBestsKeepingThree(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        store.saveIfBest(ghost(0, 4000), inputs());
        store.saveIfBest(ghost(0, 3800), inputs());
        store.saveIfBest(ghost(0, 3600), inputs());
        Path dir = root.resolve("s3k");
        assertEquals(3600, GhostFileCodec.read(dir.resolve("0-0-sonic.ggfghost")).header().finalTimeFrames());
        assertEquals(3800, GhostFileCodec.read(dir.resolve("0-0-sonic-prev1.ggfghost")).header().finalTimeFrames());
        assertEquals(4000, GhostFileCodec.read(dir.resolve("0-0-sonic-prev2.ggfghost")).header().finalTimeFrames());
    }

    @Test
    void rejectsGhostWhoseHashDoesNotMatchInputs(@TempDir Path root) {
        GhostStore store = new GhostStore(root);
        GhostRecording mismatched = new GhostRecording(new GhostHeader(1, "s3k", 0, 0, "sonic", "p",
                0, 3600, new int[0], new byte[32]), new byte[7]); // zero hash != inputs().sha256()
        assertThrows(IllegalArgumentException.class, () -> store.saveIfBest(mismatched, inputs()));
    }

    @Test
    void listsImportsSortedOrEmpty(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        assertTrue(store.listImports("s3k").isEmpty());
        Path importDir = root.resolve("s3k").resolve("import");
        Files.createDirectories(importDir);
        GhostFileCodec.write(ghost(0, 100), importDir.resolve("b.ggfghost"));
        GhostFileCodec.write(ghost(0, 100), importDir.resolve("a.ggfghost"));
        Files.writeString(importDir.resolve("readme.txt"), "ignored");
        var imports = store.listImports("s3k");
        assertEquals(2, imports.size());
        assertTrue(imports.get(0).getFileName().toString().startsWith("a"));
    }
}
