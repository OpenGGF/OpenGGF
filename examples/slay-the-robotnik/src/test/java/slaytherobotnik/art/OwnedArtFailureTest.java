package slaytherobotnik.art;

import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.mods.testing.ModTestKit;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import slaytherobotnik.SlayTheRobotnikMod;
import static org.junit.jupiter.api.Assertions.*;

/** Packaged creator callbacks retain their real owner and host lifecycle; no ROM-equivalence claim. */
class OwnedArtFailureTest {
    @TempDir Path work;
    private ModTestKit open(ArtRetryTest.Fixture fixture) throws Exception {
        Path classes = Path.of(SlayTheRobotnikMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ModTestKit kit = ModTestKit.packageAndOpen(classes, work.resolve("mods"), work.resolve("saves"));
        var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k", "sonic", List.of()));
        kit.openScene(module, 400, 224, new SceneServices(null, fixture == null ? null : fixture.rom,
                work.resolve("saves"), null, null, null));
        return kit;
    }
    @Test void optionalArtFallbackIsInspectableAndProperHostCloseTerminatesFacade() throws Exception {
        try (var kit = open(null)) {
            kit.tick();
            assertFalse(kit.draw().isEmpty());
            Object shell = kit.scene().getClass().getMethod("shell").invoke(kit.scene());
            Object art = shell.getClass().getField("art").get(shell);
            List<?> failures = (List<?>) art.getClass().getMethod("failures").invoke(art);
            assertTrue(failures.stream().anyMatch(entry -> {
                try { return entry.getClass().getMethod("kind").invoke(entry).toString().equals("OPTIONAL_ROM_MISSING"); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            }));
            assertTrue(kit.findings().isEmpty());
            assertTrue(kit.disabledOwners().isEmpty());
            kit.sceneHost().close();
            assertNull(kit.scene());
            var closed = assertThrows(InvocationTargetException.class, () -> art.getClass().getMethod("rom", String.class).invoke(art, "ring"));
            assertInstanceOf(IllegalStateException.class, closed.getCause());
        }
    }
    @Test void decoderProgrammingFaultReachesTheActualOwnerBoundary() throws Exception {
        ArtRetryTest.Fixture fixture = new ArtRetryTest.Fixture();
        fixture.failure = new NullPointerException("fixture decoder programming fault");
        try (var kit = open(fixture)) {
            kit.tick();
            var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, kit::draw);
            assertInstanceOf(NullPointerException.class, aborted.getCause());
            assertTrue(kit.disabledOwners().contains("slay-the-robotnik"));
            assertTrue(kit.findings().get("slay-the-robotnik").stream().anyMatch(f -> f.code().equals("MOD_CALLBACK_FAILED")));
            kit.sceneHost().close();
            assertNull(kit.scene());
        }
    }
}
