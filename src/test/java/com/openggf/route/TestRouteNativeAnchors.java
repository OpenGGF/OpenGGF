package com.openggf.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TestRouteNativeAnchors {
    @TempDir Path dir;

    @Test void nativePrefixReplacesEngineBootRowsWithoutMovingGameplayEdges() throws Exception {
        var boot=InputTape.of(List.of(PadRow.NEUTRAL,new PadRow(128,0),PadRow.NEUTRAL));
        var body=InputTape.of(List.of(new PadRow(8,4),new PadRow(8,4),PadRow.NEUTRAL,new PadRow(16,32)));
        var route=RouteArtifact.of(launch(),boot.append(body),new Provenance("test","","",List.of()));
        var prefix=InputTape.of(List.of(new PadRow(2,0),PadRow.NEUTRAL,new PadRow(128,0),PadRow.NEUTRAL,PadRow.NEUTRAL));
        var witness=dir.resolve("witness.script");Files.writeString(witness,RouteFiles.scriptText(prefix));
        var entry=new NativeFacts.NativeEntry(witness.toString(),5,prefix.hash(),3,"first-ordinary-level-lfc-zero");
        var anchored=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(Optional.of(entry),List.of())),route.tape());
        assertEquals(route.content(),anchored.content(),"native metadata never changes engine inputs");
        var roundtrip=RouteCodec.parseTransport(RouteCodec.formatTransport(anchored));
        assertEquals(anchored,roundtrip);
        var movie=RouteFiles.exportBk2(roundtrip,dir.resolve("composed.bk2"));
        var exported=RouteFiles.tapeFromMovie(movie,9);
        assertEquals(prefix,exported.slice(0,5));
        assertEquals(body,exported.slice(5,9));
        assertNotEquals(route.tape(),exported,"negative control: raw engine title timing is not the export");
        assertEquals(new PadRow(8,4),exported.row(5));
        assertEquals(PadRow.NEUTRAL,exported.row(7));
        assertEquals(new PadRow(16,32),exported.row(8));
        Files.writeString(witness,RouteFiles.scriptText(InputTape.of(List.of(PadRow.NEUTRAL)).append(prefix.slice(1,5))));
        assertThrows(java.io.IOException.class,()->RouteFiles.exportBk2(anchored,dir.resolve("changed.bk2")),
                "negative control: a changed witness cannot retain its old hash");
    }

    @Test void provisionalAndUnanchoredTitleExportsAreRefused() throws Exception {
        var route=RouteArtifact.of(launch(),InputTape.of(List.of(PadRow.NEUTRAL)),new Provenance("test","","",List.of()));
        assertThrows(java.io.IOException.class,()->RouteFiles.exportBk2(route,dir.resolve("raw.bk2")));
        var blocked=new RouteArtifact(route.manifest().withNativeFacts(new NativeFacts(Optional.empty(),List.of(),"engine pause frontier")),route.tape());
        var roundtrip=RouteCodec.parseTransport(RouteCodec.formatTransport(blocked));
        assertEquals("engine pause frontier",roundtrip.manifest().nativeFacts().exportBlocker());
        var error=assertThrows(java.io.IOException.class,()->RouteFiles.exportBk2(roundtrip,dir.resolve("blocked.bk2")));
        assertTrue(error.getMessage().contains("engine pause frontier"));
        assertFalse(Files.exists(dir.resolve("blocked.bk2")));
        assertThrows(IllegalArgumentException.class,()->new NativeFacts.NativeEntry("x",5,route.tape().hash(),3,""));
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(RouteCodec.formatTransport(blocked));
        var incomplete=((com.fasterxml.jackson.databind.node.ObjectNode)json.get("native")).putObject("entry");
        incomplete.put("movie","x").put("prefixRows",5).put("inputHash",route.tape().hash().sha256()).put("engineAnchorRows",3);
        String partial=json.toString();
        assertThrows(java.io.IOException.class,()->RouteCodec.parseTransport(partial));
    }

    private static LaunchContract launch() {
        return new LaunchContract(RomIdentity.standard("s2"),new Inlet.Title(),new Roster("sonic","tails"),320,"off",new InitialState(0,Seeds.NONE));
    }
}
