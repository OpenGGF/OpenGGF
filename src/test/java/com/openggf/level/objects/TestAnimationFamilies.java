package com.openggf.level.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** ROM scripts own timing; family metadata supplies only drawing correspondence. */
@ExtendWith(SingletonResetExtension.class) @FullReset
class TestAnimationFamilies {
    @TempDir Path temporary;
    @Test @RequiresRom(SonicGame.SONIC_1) void s1() throws Exception { verify(new Sonic1GameModule()); }
    @Test @RequiresRom(SonicGame.SONIC_2) void s2() throws Exception { verify(new Sonic2GameModule()); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void s3k() throws Exception { verify(new Sonic3kGameModule()); }

    private void verify(GameModule module) throws Exception {
        TestEnvironment.activeGameplayMode();
        var caps = module.getDonorCapabilities();
        var nativeArt = caps.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom())).loadPlayerSpriteArt("sonic");
        assertEquals(nativeArt.animationSet().getAllScripts().keySet(), caps.getAnimationFamilies().keySet(),
                "Every native Sonic script must have a family or explicit N/A");
        Path project = Path.of("examples/mica-and-talon/src/main/mod"), encoded = temporary.resolve("mica.ggfp");
        assertEquals(0, GgfModCli.run(new String[]{"convert", "art", "--playable", "--image",
                project.resolve("mica.png").toString(), "--sheet", project.resolve("mica-sheet.yaml").toString(),
                "--out", encoded.toString()}, System.out));
        var sheet = PlayableSheetReader.read(Files.readAllBytes(encoded));
        var source = PlayableSheetMaterializer.read(Files.readAllBytes(encoded)).art();
        var bound = PlayableSheetAnimationBinding.bind(source, caps, nativeArt);
        int relocation = bound.mappingFrames().size() - source.mappingFrames().size();
        for (var entry : caps.getAnimationFamilies().entrySet()) {
            var binding = entry.getValue();
            var nativeScript = nativeArt.animationSet().getScript(entry.getKey());
            assertEquals(nativeScript.frames().size(), binding.phases().size(), "phase order " + entry.getKey());
            if (binding.family() == null) { assertNull(binding.animation()); continue; }
            if (java.util.stream.IntStream.range(0, binding.phases().size())
                    .anyMatch(i -> !sheet.nativeFrames().containsKey(binding.phaseKey(i)))) continue;
            var script = bound.animationSet().getScript(entry.getKey());
            assertEquals(nativeScript.delay(), script.delay(), "delay " + binding.animation());
            assertEquals(nativeScript.endAction(), script.endAction(), "end " + binding.animation());
            assertEquals(nativeScript.endParam(), script.endParam(), "parameter " + binding.animation());
            for (int step = 0; step < binding.phases().size(); step++) {
                int authored = sheet.nativeFrames().get(binding.phaseKey(step));
                int selected = script.frames().get(step);
                var originalPieces = source.mappingFrames().get(authored).pieces();
                var selectedPieces = bound.mappingFrames().get(selected).pieces();
                assertEquals(originalPieces.size(), selectedPieces.size());
                for (int piece = 0; piece < originalPieces.size(); piece++) {
                    var original = originalPieces.get(piece); var actual = selectedPieces.get(piece);
                    assertEquals(new com.openggf.level.render.SpriteMappingPiece(
                            original.xOffset() + binding.offsetX(), original.yOffset() + binding.offsetY(),
                            original.widthTiles(), original.heightTiles(), original.tileIndex(), original.hFlip(),
                            original.vFlip(), original.paletteIndex(), original.priority()), actual);
                }
                assertSame(source.dplcFrames().get(authored), bound.dplcFrames().get(selected));
            }
        }
        assertSame(nativeArt, PlayableSheetAnimationBinding.bind(nativeArt, caps, nativeArt));
    }
}
