package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The same production loop owns fades in a direct capture and the live Engine. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureFadeOwnership {
    @Test
    void aDirectLevelCaptureServicesTheRuntimeOwnedFadeOncePerPresentation() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","tails","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),7,0,settings);
            assertTrue(session.loop().ownsGameplayFadeLifecycle(),"real capture attaches the production runtime");
            assertNotNull(GameServices.graphics().getUiRenderPipeline(),"the legacy extra driver is actually present");
            var fade=GameServices.fade();fade.cancel();
            var completed=new AtomicInteger();
            fade.startFadeToBlack(completed::incrementAndGet,0,90);
            for(int presentation=1;presentation<=4;presentation++) {
                session.step(null);session.renderFrame();
                assertEquals(presentation,fade.getFrameCount(),"one production fade service per presentation");
                assertEquals(0,completed.get(),"a capture may not finish the fade early");
            }
        }
    }
}
