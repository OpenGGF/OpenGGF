package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native ROM decoding plus real scene input and owner boundaries; model tests live with the mod. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarfallFrontierScene {
    @TempDir Path work;
    @Test void realRomHeroPanelsCavernsAndWardensDrawWithoutOwnerFaults() throws Exception {
        SharedLevel level=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        try(ExampleModHarness harness=ExampleModHarness.build(Path.of("examples/starfall-frontier"),work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()),work.resolve("saves"),528,224);
            harness.host().draw(null,null);
            harness.press(GLFW_KEY_ENTER);
            assertEquals("PLAY",harness.scene().getClass().getMethod("screen").invoke(harness.scene()));
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
            for(int i=0;i<90;i++)harness.tick();
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE);harness.tick();
            for(String command:List.of("craft","inventory","journal","map","cavern","warden","victory")) {
                assertTrue(harness.debugJump(command));for(int i=0;i<15;i++)harness.tick();
                harness.host().draw(null,null);
                assertFalse(harness.host().recordedFrame().isEmpty());
            }
            assertTrue(harness.findings().isEmpty(),harness.findings()::toString);
        }finally{level.dispose();}
    }
}
