package com.openggf.sprites.playable;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual ROM terrain, player scripts, mapping indexes and appendage state, with no renderer/device. */
@RequiresRom(SonicGame.SONIC_2)
class TestPlayablePuppetRom {
    @Test void nativeTailsRunsJumpsAndReplaysAgainstTheDetachedRomSet() throws Exception {
        SharedLevel bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        try (var library = new SceneRomLibrary(GameServices.module(), GameServices.rom().getRom(), GameServices.rom())) {
            var live = GameServices.camera().getFocusedSprite();
            int liveX = live.getCentreX(), liveY = live.getCentreY();
            var camera = GameServices.camera().capture();
            var terrain = library.level("s2", 0, 0);
            var art = library.characterArt("s2", "tails");
            assertNotNull(art.animationSet()); assertFalse(art.mappingFrames().isEmpty());
            try (var puppet = PlayablePuppet.create(terrain, library.module("s2"), 0, 0,
                    library.module("s2"), "tails", art, liveX, liveY, 0, true, false)) {
                assertEquals(liveX, puppet.sample().x());
                boolean sawAir = false, sawTail = false, sawLanding = false;
                for (int frame = 0; frame < 20; frame++) {
                    puppet.tick(false, true, frame >= 8 && frame < 18, frame);
                    sawAir |= puppet.sample().airborne(); sawTail |= puppet.sample().tail() != null;
                }
                var checkpoint = puppet.capture();
                List<PlayablePuppet.Observation> expected = new ArrayList<>();
                for (int frame = 20; frame < 85; frame++) {
                    puppet.tick(false, frame < 35, false, frame);
                    var pose = puppet.sample(); expected.add(pose);
                    assertTrue(pose.mappingFrame() >= 0 && pose.mappingFrame() < art.mappingFrames().size());
                    sawLanding |= sawAir && pose.onGround();
                }
                assertTrue(sawAir); assertTrue(sawTail, "native spin tail must be sampled during the jump");
                assertTrue(sawLanding);
                puppet.restore(checkpoint);
                for (int frame = 20; frame < 85; frame++) {
                    puppet.tick(false, frame < 35, false, frame);
                    assertEquals(expected.get(frame - 20), puppet.sample());
                }
            }
            assertEquals(liveX, live.getCentreX()); assertEquals(liveY, live.getCentreY());
            assertEquals(camera, GameServices.camera().capture());
        } finally { bootstrap.dispose(); }
    }
}
