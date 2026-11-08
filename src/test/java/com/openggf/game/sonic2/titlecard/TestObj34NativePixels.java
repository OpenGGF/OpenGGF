package com.openggf.game.sonic2.titlecard;

import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production 1P renderer, native-time entry and leave cuts. No reference state hydration. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestObj34NativePixels {
    @Test void capturesEntryAndDepartureFromTheSingleObj34Owner() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","tails","off",null,null,null,
                null,true,false);
        try(var capture=new GameplayCaptureSession(settings)) {
            capture.boot(Path.of(System.getProperty("sonic2.rom.path")),0,0,settings);
            var title=(TitleCardManager)GameServices.module().getTitleCardProvider();
            title.initialize(0,0);
            GameServices.fade().cancel();
            GameServices.graphics().clearPaletteFadePresentation();
            capture.loop().setGameMode(GameMode.TITLE_CARD);
            var output=Path.of("target/obj34-native-pixels");Files.createDirectories(output);
            for(int f=761;f<=895;f++) {
                title.obj34.consumeFills();
                // Explicit native Level_TtlCard / LoadZoneTiles / leave-loop boundaries.
                if(f<=805 || f>=874) title.obj34.tick();
                if(f==874) {
                    title.obj34.armLeave();
                    var field=TitleCardManager.class.getDeclaredField("state");field.setAccessible(true);
                    field.set(title,TitleCardState.EXIT_LEFT_SWOOSH);
                }
                if(f==770 || f==785 || f==795 || f==805 || f==810 || f==880 || f==895) {
                    var before=title.capture();
                    var image=capture.render();
                    assertEquals(before.obj34(),title.capture().obj34(),"drawing is read-only");
                    ScreenshotCapture.savePNG(image,output.resolve("f"+f+"-engine.png"));
                    assertTrue(java.util.Arrays.stream(image.pixels()).distinct().count()>=3);
                }
            }
        }
    }
}
