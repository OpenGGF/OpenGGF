package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.route.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteTool {
    @TempDir Path root;
    @Test void unsupportedRequestsReturnTypedDiagnosticsAndNeverPublishOutput() throws Exception {
        Path source=root.resolve("source.stageplay"),out=root.resolve("route.stageplay");
        StageplayStore.write(source,StageplayDocument.empty("s2",0,0,0,0,320,224));
        var output=new ByteArrayOutputStream();
        int code=StageplayRouteTool.execute(new String[]{"--scene",source.toString(),"--out",out.toString(),"--actor","unknown","--frame","1","--x","100","--y","200"},new PrintStream(output));
        assertEquals(2,code);var response=new ObjectMapper().readTree(output.toByteArray());
        assertFalse(response.path("ok").booleanValue());assertEquals("UNSUPPORTED",response.path("result").path("status").textValue());
        assertTrue(response.path("error").isTextual(), "Semantic failures retain the shared CLI error envelope");
        assertFalse(Files.exists(out));try(var files=Files.list(root)){assertEquals(1,files.count());}
    }
    @Test void unknownDuplicateFlagsAndExistingOutputFailBeforeRomOrFilesystemChanges() throws Exception {
        Path out=Files.writeString(root.resolve("occupied.stageplay"),"keep");
        for(var args:new String[][]{{"--out",out.toString()},{"--unknown","value","--out",root.resolve("absent").toString()},
                {"--out",out.toString(),"--out",out.toString()}}){
            var output=new ByteArrayOutputStream();assertEquals(2,StageplayRouteTool.execute(args,new PrintStream(output)));
            assertFalse(new ObjectMapper().readTree(output.toByteArray()).path("ok").booleanValue());assertEquals("keep",Files.readString(out));
        }
    }
    @Test void planningCannotReplaceAnyActiveParentWorldEvenBeforeOpeningTransport() throws Exception {
        var field=com.openggf.game.session.SessionManager.class.getDeclaredField("currentWorldSession");field.setAccessible(true);
        Object old=field.get(null);var parent=org.mockito.Mockito.mock(com.openggf.game.session.WorldSession.class);
        field.set(null,parent);
        try {
            var request=new StageplayRouteRequest("hero",0,100,200,30);
            var inputs=new StageplayRouteInputs.Snapshot(StageplayDocument.empty("s2",0,0,0,0,320,224),request,root,"{}","{}","0".repeat(64));
            var failure=assertThrows(IOException.class,()->StageplayRouteTool.plan(inputs));assertTrue(failure.getMessage().contains("active world"));
            assertSame(parent,field.get(null));
        } finally {field.set(null,old);}
    }
}
