package com.openggf.stageplay.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayPreviewAssets {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static StageplayDocument authored() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224)
                .withSet(new StageplayDocument.SetSource("s2",0,0,"","set.ogfproject"));
    }

    @Test void editorTransfersCanonicalRootMetadataWithoutReadingOrWritingItsAssets() throws Exception {
        var document=authored();
        String encoded=StageplayPreviewAssets.source(directory,null).encode(document);
        var source=StageplayPreviewAssets.decode(encoded,document);
        assertEquals(directory.toRealPath(),source.projectRoot());
        assertNull(source.reader());
        assertTrue(JSON.readTree(encoded).path("projects").isEmpty());
        try(var paths=Files.list(directory)) { assertEquals(0,paths.count()); }
    }

    @Test void registeredSnapshotsUseOnlyPreparedOwnerBytesAndRemainIndependentAfterTransport() throws Exception {
        var document=authored();
        byte[] original={1,2,3,4};
        var reads=new AtomicInteger();
        var source=StageplayPreviewAssets.source(directory,path->{
            assertEquals("set.ogfproject",path);reads.incrementAndGet();return original;
        });
        var child=StageplayPreviewAssets.decode(source.encode(document),document);
        original[0]=9;
        assertNull(child.projectRoot(),"A registered reader cannot fall back to a filesystem root");
        assertArrayEquals(new byte[]{1,2,3,4},child.reader().read("set.ogfproject"));
        byte[] returned=child.reader().read("set.ogfproject");returned[0]=7;
        assertEquals(1,child.reader().read("set.ogfproject")[0]);
        assertEquals(1,reads.get());
        assertThrows(IOException.class,()->child.reader().read("undeclared.ogfproject"));
        try(var paths=Files.list(directory)) { assertEquals(0,paths.count()); }
    }

    @Test void missingAuthorityMissingSnapshotsAndOversizedProjectsFailExplicitly() throws Exception {
        var document=authored();
        assertThrows(IOException.class,()->StageplayPreviewAssets.source(null,null).encode(document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.source(null,path->null).encode(document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.source(null,path->new byte[0]).encode(document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.source(null,path->new byte[AuthoredLevelResources.MAX_ENCODED_BYTES+1]).encode(document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode("{\"version\":1,\"root\":\"\",\"projects\":{}}",document));
    }

    @Test void sourceWithoutAuthoredProjectsNeedsNoLocalRootOrReader() throws Exception {
        var document=StageplayDocument.empty("s1",0,0,0,0,320,224);
        assertDoesNotThrow(()->StageplayPreviewAssets.decode(StageplayPreviewAssets.source(null,null).encode(document),document));
    }

    @Test void admittedPreparedProjectLargerThanDefaultJacksonStringLimitRoundTrips() throws Exception {
        byte[] project=new byte[16*1024*1024];
        project[0]=42;project[project.length-1]=17;
        var document=authored();
        String encoded=StageplayPreviewAssets.source(null,path->project).encode(document);
        assertTrue(encoded.length()>20_000_000,"Exercise a base64 value above Jackson's default string bound");
        var source=StageplayPreviewAssets.decode(encoded,document);
        assertArrayEquals(project,source.reader().read("set.ogfproject"));
    }

    @Test void descriptorRejectsUnknownFieldsVersionsRootsAndUnexpectedAssets() throws Exception {
        var document=authored();
        String encoded=StageplayPreviewAssets.source(null,path->new byte[]{1}).encode(document);
        var tree=(ObjectNode)JSON.readTree(encoded);
        tree.put("unknown",true);
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(tree.toString(),document));
        tree.remove("unknown");tree.put("version",2);
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(tree.toString(),document));
        tree.put("version",1);tree.put("root",directory.toString());
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(tree.toString(),document));
        ((ObjectNode)tree.get("projects")).removeAll();tree.put("root","relative");
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(tree.toString(),document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(encoded+" {}",document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(encoded.replace("AQ==","?"),document));
        assertThrows(IOException.class,()->StageplayPreviewAssets.decode(null,document));
    }
}
