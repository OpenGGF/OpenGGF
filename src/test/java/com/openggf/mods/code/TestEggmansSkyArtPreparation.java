package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded source-qualified preparation probe from P17; no timing threshold or worker-thread decoder. */
@RequiresRom(SonicGame.SONIC_3K)
class TestEggmansSkyArtPreparation {
    @TempDir Path work;
    @Test void measureThreeFreshSceneVisitsAndFirstLandingThroughOwnedCallbacks() throws Exception {
        SharedLevel level=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        try(ExampleModHarness harness=ExampleModHarness.build(Path.of("examples/eggmans-sky"),work.resolve("build"))) {
            var module=harness.apply(GameServices.module());
            for(int run=0;run<3;run++) {
                harness.host().close();long started=System.nanoTime();
                harness.open(module,work.resolve("saves-"+run),400,224);
                long enterNanos=System.nanoTime()-started;
                Object game=harness.scene().getClass().getMethod("game").invoke(harness.scene());
                int biomes=((java.util.List<?>)game.getClass().getField("usableBiomes").get(game)).size();
                assertTrue(biomes>0);assertTrue(harness.debugJump("new:"+(42+run)));
                long maximumTick=0;String maximumPhase="";
                for(int tick=0;tick<6;tick++) {
                    String phase=game.getClass().getMethod("mode").invoke(game).getClass().getSimpleName();
                    started=System.nanoTime();harness.tick();long nanos=System.nanoTime()-started;
                    if(nanos>maximumTick){maximumTick=nanos;maximumPhase=phase;}
                    harness.host().draw(null,null);
                }
                assertEquals("SurfaceMode",game.getClass().getMethod("mode").invoke(game).getClass().getSimpleName());
                assertTrue(harness.findings().isEmpty(),harness.findings()::toString);
                System.out.printf(java.util.Locale.ROOT,
                        "EGG_PREP visit=%d biomes=%d scene_enter_ms=%.3f maximum_owned_tick_ms=%.3f phase=%s%n",
                        run,biomes,enterNanos/1_000_000d,maximumTick/1_000_000d,maximumPhase);
                harness.host().close();assertNull(harness.scene());
            }
        } finally { level.dispose(); }
    }
}
