package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-game regression for the shared capture tool's native skipped-title boundary. */
class TestGameplayCaptureSkippedTitles {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1CaptureStillExecutesOrdinaryGameplayAfterOmittedTitle() throws Exception {
        movingCapture(RomTestUtils.ensureSonic1RomAvailable().toPath(),"s1");
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2CaptureStillExecutesOrdinaryGameplayAfterOmittedTitle() throws Exception {
        movingCapture(RomTestUtils.ensureSonic2RomAvailable().toPath(),"s2");
    }
    private void movingCapture(Path rom,String game) throws Exception {
        var input=TestSessionOutputPaths.diagnostics("capture-skipped-title-"+game).resolve("input.txt");
        InputLogAuthorTool.author("80 R",input,game);
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(input);
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(rom,0,0,settings);int start=session.player().getCentreX();
            for(int i=0;i<movie.getFrameCount();i++)session.step(movie.getFrame(i));
            assertTrue(session.player().getCentreX()>start+32,"ordinary right input must move the native player");
            assertFalse(session.player().getDead());assertEquals(320,session.render().width());
        }
    }
}
