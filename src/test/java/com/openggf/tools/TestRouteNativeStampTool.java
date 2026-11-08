package com.openggf.tools;

import com.openggf.route.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestRouteNativeStampTool {
    @TempDir Path dir;

    @Test void namedNativeEvidenceMustMatchTheExactAnchoredExport() throws Exception {
        // Reuse genuinely qualified frozen rows, not fabricated engine evidence.
        Path source=Path.of("src/test/resources/routes/s2/competition/cnz1-slot-prefix.route.json");
        var route=RouteFiles.read(source);
        Path manifest=dir.resolve(source.getFileName());
        Files.copy(source,manifest);
        Files.copy(source.resolveSibling("cnz1-slot-prefix.script"),dir.resolve("cnz1-slot-prefix.script"));
        Path movie=RouteFiles.exportBk2(route,dir.resolve("native.bk2"));
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(movie)));
        var anchor=route.manifest().nativeFacts().entry().orElseThrow();
        var verdict=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        verdict.put("format","openggf-native-goals/1")
                .put("nativeRows",anchor.prefixRows()+route.tape().size()-anchor.engineAnchorRows())
                .put("romSha1",route.launch().rom().sha1()).put("movieSha256",hash).put("harness","test").put("passed",true);
        verdict.putArray("coverage").add("slot-in-use-in-view");
        Path result=dir.resolve("verdict.json");Files.writeString(result,verdict.toString());
        RouteNativeStampTool.main(new String[]{manifest.toString(),result.toString()});
        var stamped=RouteFiles.read(manifest);
        assertEquals(List.of("slot-in-use-in-view"),stamped.manifest().qualification().getLast().coverage());
        assertEquals(route.manifest().nativeFacts(),stamped.manifest().nativeFacts());
        assertEquals(route.content(),stamped.content());
        assertTrue(stamped.engineQualified());
        verdict.put("movieSha256","0".repeat(64));Files.writeString(result,verdict.toString());
        assertThrows(IllegalArgumentException.class,()->RouteNativeStampTool.main(new String[]{manifest.toString(),result.toString()}));
    }
}
