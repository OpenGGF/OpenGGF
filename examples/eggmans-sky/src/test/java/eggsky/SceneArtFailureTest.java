package eggsky;

import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.mods.testing.ModTestKit;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual packaged scene callbacks against original fixture art; no ROM equivalence claim. */
class SceneArtFailureTest {
    @TempDir Path work;
    private ModTestKit open(ArtFixture fixture) throws Exception {
        Path classes=Path.of(EggmansSkyMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ModTestKit kit=ModTestKit.packageAndOpen(classes,work.resolve("mods"),work.resolve("saves"));
        var module=kit.launch(new Sonic3kGameModule(),new GameplayLaunchRequest("s3k","sonic",List.of()));
        kit.openScene(module,400,224,new SceneServices(null,fixture==null?null:fixture.rom,
                work.resolve("saves"),null,null,null));
        return kit;
    }
    @Test void optionalRomAbsenceDrawsFallbackAndHasDistinctStatus() throws Exception {
        try(var kit=open(null)) {
            kit.tick();assertFalse(kit.draw().isEmpty());
            Object game=kit.scene().getClass().getMethod("game").invoke(kit.scene());
            Object art=game.getClass().getField("art").get(game);
            List<?> failures=(List<?>)art.getClass().getMethod("failures").invoke(art);
            assertTrue(failures.stream().anyMatch(entry->{
                try{return entry.getClass().getMethod("kind").invoke(entry).equals("OPTIONAL_ROM_MISSING");}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}
            }));
            assertTrue(kit.findings().isEmpty());assertTrue(kit.disabledOwners().isEmpty());
            kit.sceneHost().close();assertNull(kit.scene());
        }
    }
    @Test void programmingFaultInArtDisablesAndAttributesTheActualSceneOwner() throws Exception {
        ArtFixture fixture=new ArtFixture();fixture.spriteFailure=new NullPointerException("fixture decoder programming fault");
        try(var kit=open(fixture)) {
            kit.tick();
            var failure=assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,kit::draw);
            assertInstanceOf(NullPointerException.class,failure.getCause());
            assertEquals("fixture decoder programming fault",failure.getCause().getMessage());
            assertTrue(kit.disabledOwners().contains("eggmans-sky"));
            assertTrue(kit.findings().containsKey("eggmans-sky"),kit.findings()::toString);
            assertTrue(kit.findings().get("eggmans-sky").stream()
                    .anyMatch(finding->finding.code().equals("MOD_CALLBACK_FAILED")));
            kit.sceneHost().close();assertNull(kit.scene());
        }
    }
}
