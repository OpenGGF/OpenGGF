package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.soniccd.resources.CdBlockDefinitionStaging;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Actual selected R11 ingress only; authored Java UNRUN. Uses Root's existing EGL fixture. */
@Isolated class TestCdBlockDefinitionOriginalIngress {
    @TempDir Path root;
    private final TestCdOriginalR11LoadingWorld fixture=new TestCdOriginalR11LoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void currentOriginalClearIsPresentOnlyAfterGenuineSelectedHandoff()throws Exception{
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,
                SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        // Read-only inspection of the actual owner. No field write or constructor bypass.
        var field=SonicCdGameModule.class.getDeclaredField("originalBoot");field.setAccessible(true);
        var owner=(CdOriginalBootPrefix)field.get(module);var earlier=owner.state();
        assertThrows(CdBlockDefinitionStaging.UnknownRead.class,()->earlier.blockDefinitions().unsignedByte(0));
        try(var boot=TestCdOriginalR11LoadingWorld.boot()){
            boot.bootSonicCdSelectedProfile(source,attempt);
            var after=owner.state();
            assertTrue(after.calls().contains(CdOriginalBootPrefix.Call.PROGRAMME_FILE_CLEAR));
            assertTrue(after.calls().indexOf(CdOriginalBootPrefix.Call.PROGRAMME_FILE_CLEAR)
                    <after.calls().indexOf(CdOriginalBootPrefix.Call.R11_CONTINUATION_ISSUED));
            for(int offset=0;offset<8192;offset+=2)assertEquals(0,after.blockDefinitions().unsignedWord(offset));
            assertThrows(CdBlockDefinitionStaging.UnknownRead.class,()->earlier.blockDefinitions().unsignedByte(0));
        }
    }
}
