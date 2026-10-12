package threeislands.art;

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
import threeislands.ThreeIslandsMod;
import static org.junit.jupiter.api.Assertions.*;

/** Actual packaged scene callbacks with original fixture art, closed through the owner boundary. */
class OwnedArtFailureTest {
    @TempDir Path work;
    private ModTestKit open(ArtRetryTest.Fixture fixture) throws Exception {
        Path classes = Path.of(ThreeIslandsMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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
            Object game = kit.scene().getClass().getMethod("game").invoke(kit.scene());
            Object art = game.getClass().getField("art").get(game);
            List<?> failures = (List<?>) art.getClass().getMethod("failures").invoke(art);
            assertTrue(failures.stream().anyMatch(entry -> {
                try { return entry.getClass().getMethod("kind").invoke(entry).toString().equals("OPTIONAL_ROM_MISSING"); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            }));
            assertTrue(kit.findings().isEmpty());
            assertTrue(kit.disabledOwners().isEmpty());
            kit.sceneHost().close();
            assertNull(kit.scene());
            var closed = assertThrows(InvocationTargetException.class, () -> art.getClass().getMethod("sprites", String.class).invoke(art, "s3k:ring"));
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
            assertTrue(kit.disabledOwners().contains("three-islands"));
            assertTrue(kit.findings().get("three-islands").stream().anyMatch(f -> f.code().equals("MOD_CALLBACK_FAILED")));
            kit.sceneHost().close();
            assertNull(kit.scene());
        }
    }
}
