package com.openggf.stageplay.route;

import com.openggf.data.*;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayRomTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteInputs {
    @TempDir Path directory;
    @Test void exactCompositeWindowsAndProjectContentsBindTheRouteAndFreshDiskChangesAreDetected() throws Exception {
        Path file=Files.write(directory.resolve("rom.gen"),new byte[]{9,1,2,3,4,8});
        var physical=PhysicalImage.probe(file);
        var resolution=new RomImageCatalogue.Resolution(RomIdentity.S2,List.of(new RomImageCatalogue.Part(RomIdentity.S2,physical,1,2),
                new RomImageCatalogue.Part(RomIdentity.S2,physical,3,2)),RomImageCatalogue.Source.COMPOSITE);
        var scene=StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(new StageplayDocument.SetSource("s2",0,0,"","level.ogfproject"));
        var request=new StageplayRouteRequest("hero",0,100,200,30);
        Path project=Files.write(directory.resolve("level.ogfproject"),new byte[]{1,2,3});
        var first=StageplayRouteInputs.capture(scene,request,directory,Map.of("s2",resolution));
        assertEquals(0x01020304,StageplayRomTransport.open(first.romDescriptor()).get("s2").readU32BE(0));
        assertEquals(first.fingerprint(),StageplayRouteInputs.capture(scene,request,directory,Map.of("s2",resolution)).fingerprint());
        Files.write(project,new byte[]{1,2,4});
        assertNotEquals(first.fingerprint(),StageplayRouteInputs.capture(scene,request,directory,Map.of("s2",resolution)).fingerprint());
        Files.write(file,new byte[]{9,1,2,7,4,8});
        assertThrows(java.io.IOException.class,()->StageplayRouteInputs.capture(scene,request,directory,Map.of("s2",resolution)),
                "A memoized catalogue fingerprint cannot certify fresh physical bytes");
        try(var files=Files.list(directory)) { assertEquals(2,files.count(),"No joined ROM or snapshot files are written"); }
    }
}
