package openggf.racing.server.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import openggf.racing.server.tools.TrackProfileExportTool;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestTrackProfileArenaExport {
    @Test void exportFiveNativeArenaProfiles() throws Exception {
        Path output = Path.of("target/lane-f/track-validation-profiles.json");
        Files.createDirectories(output.getParent());
        Files.copy(Path.of("racing/server/src/main/resources/net/track-validation-profiles.json"),output,
                StandardCopyOption.REPLACE_EXISTING);
        TrackProfileExportTool.main(new String[]{"--arenas-only","--output",output.toString()});
        var entries = new ObjectMapper().readTree(output.toFile()).path("profiles");
        int arenas=0;
        for(var entry:entries) if(entry.path("key").asText().contains(":arena:")) {
            arenas++;
            assertTrue(entry.path("levelWidthPx").asInt()>0);
            assertTrue(entry.path("wrapWidthPx").asInt()>0);
            assertEquals(5,entry.path("lapCount").asInt());
            assertEquals(12,entry.path("maxSwapSources").asInt());
        }
        assertEquals(5,arenas);
    }
}
