package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Animal layouts must follow the ROM's species table, even when a wrong layout still decodes. */
class TestEggmansSkyFauna {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static List<?> fauna;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/eggmans-sky"), work);
        Class<?> type = harness.loader().loadClass("eggsky.art.FaunaCatalog");
        fauna = (List<?>) type.getMethod("all").invoke(type.getConstructor().newInstance());
    }

    @AfterAll static void close() throws Exception {
        if (harness != null) harness.close();
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1AnimalsUseAnmlVariables() throws Exception {
        check(GameId.S1, 0x95E4, Map.of("pocky1", 0, "cucky1", 1, "pecky1", 2,
                "rocky1", 3, "picky1", 4, "flicky1", 5, "ricky1", 6), 20);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2AnimalsUseObj28Properties() throws Exception {
        check(GameId.S2, 0x118F0, Map.of("flicky2", 5, "pocky2", 0, "tocky2", 10), 22);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kAnimalsUseWord2C7EA() throws Exception {
        check(GameId.S3K, 0x2C7EA, Map.of("pocky3", 0, "cucky3", 1, "pecky3", 2,
                "rocky3", 3, "picky3", 4, "flicky3", 5, "ricky3", 6), 41);
    }

    private void check(GameId game, int table, Map<String, Integer> animals, int expectedBodies) throws Exception {
        var rom = GameServices.rom().getRom();
        var reader = RomByteReader.fromRom(rom);
        var art = SceneRomArtFactory.create(rom, game, null, null);
        int[] palette = new int[64];
        // Opaque distinct indices make a missing tile different from any visible colour.
        for (int i = 0; i < palette.length; i++) palette[i] = 0xFF000000 | i * 0x030303;
        int bodies = 0;
        int checkedAnimals = 0;
        for (Object def : fauna) {
            Class<?> type = def.getClass();
            if (!game.code().equals(type.getMethod("game").invoke(def))) continue;
            bodies++;
            String key = (String) type.getMethod("key").invoke(def);
            var request = (RomSpriteRequest) type.getMethod("request").invoke(def);
            if ((boolean) type.getMethod("animal").invoke(def)) {
                assertTrue(animals.containsKey(key), "unverified animal " + key);
                // Each ROM entry is x velocity, y velocity, then a long mapping pointer.
                int address = table + animals.get(key) * 8 + 4;
                int mapping = reader.readU16BE(address) << 16 | reader.readU16BE(address + 2);
                assertEquals(mapping, request.mappingAddress(), key + " must use its ROM species layout");
                checkedAnimals++;
            }
            var set = art.sprites(request, palette);
            for (int frame : (int[]) type.getMethod("frames").invoke(def)) {
                assertTrue(frame >= 0 && frame < set.frameCount(), key + " frame " + frame);
                var sprite = set.frame(frame);
                boolean visible = false;
                for (int y = 0; y < sprite.height(); y++) {
                    for (int x = 0; x < sprite.width(); x++) {
                        visible |= (sprite.image().pixel(x, y) >>> 24) != 0;
                    }
                }
                assertTrue(visible, key + " frame " + frame + " has no visible art");
            }
        }
        assertEquals(animals.size(), checkedAnimals);
        assertEquals(expectedBodies, bodies);
    }
}
