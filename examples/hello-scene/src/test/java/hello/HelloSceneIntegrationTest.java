package hello;

import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.testing.ModTestKit;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Runs without a ROM: it checks production loading, input and recorded drawing; ROM art has separate tests. */
class HelloSceneIntegrationTest {
    @TempDir Path work;

    @Test void packagedSceneLoadsTicksDrawsAndClosesWithoutFaults() throws Exception {
        try (ModTestKit kit = ModTestKit.packageAndOpen(Path.of("target/classes"),
                work.resolve("mods"), work.resolve("saves"))) {
            var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k", "sonic", List.of()));
            kit.openScene(module, 320, 224);
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, true);
            for (int frame = 0; frame < 30; frame++) kit.tick();
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, false);
            kit.tick();
            assertFalse(kit.draw().isEmpty());
            assertTrue(kit.findings().isEmpty(), kit.findings()::toString);
            assertTrue(kit.disabledOwners().isEmpty());
        }
    }
}
