package com.openggf.tools;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Pixel equality against bypassing the new seam, for every entrance presentation. */
class TestNativeTitleMascotFallbackPixels {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1IsUnchanged() throws Exception { check("sonic1.rom.path"); }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2IsUnchanged() throws Exception { check("sonic2.rom.path"); }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kIsUnchanged() throws Exception { check("s3k.rom.path"); }

    private void check(String property) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","tails","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(Path.of(System.getProperty(property)),0,0,settings);
            var title=session.loop().getTitleScreenProvider();
            var resolved=title.getClass().getDeclaredField("titleCharactersResolved");resolved.setAccessible(true);
            for(int frame=0;frame<900;frame++) {
                session.step(null);
                // Null owners with resolution bypassed are exactly the original native draw branches.
                resolved.setBoolean(title,true);
                var nativePixels=session.render();
                resolved.setBoolean(title,false);
                var fallback=session.render();
                assertArrayEquals(nativePixels.pixels(),fallback.pixels(),property+" presentation "+frame);
            }
        }
    }
}
