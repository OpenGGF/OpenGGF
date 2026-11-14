package com.openggf.game.sonic3k.bluespheres;

import com.openggf.audio.AudioManager;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeMenuRaster;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The perfect screen's difficulty showcase (Difficulty_Sprites,
 * sonic3k.asm:102115-102127) counted up to 12 against the locked-on S3&K ROM.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSpheresShowcase {
    private S3kBlueSpheresAssets assets;

    @BeforeEach
    void open() throws Exception {
        assets = new S3kBlueSpheresAssets(RomByteReader.fromRom(GameServices.rom().getRom()));
    }

    @AfterEach
    void clear() {
        TestEnvironment.resetAll();
    }

    @Test
    void countsToTwelveSpawningEveryDifficultyObject() throws Exception {
        var session = new S3kBlueSpheresSession();
        session.difficultyWord = 0x000C;
        var palette = new S3kBlueSpheresPalette();
        var perfect = new S3kBlueSpheresPerfect(assets, session, AudioManager.createSilent(), palette);
        // The perfect screen follows the results screen, whose VRAM it keeps.
        var resultsVram = S3kBlueSpheresAssets.emptyVram();
        S3kBlueSpheresAssets.load(resultsVram, S3kBlueSpheresAssets.TILE_MISC,
                assets.kosinski(S3kBlueSpheresAssets.ART_KOS_NORMAL_TEXT));
        int[] plane = new int[S3kBlueSpheresAssets.PLANE_WIDTH * S3kBlueSpheresAssets.PLANE_HEIGHT];
        perfect.showFrom(new S3kBlueSpheresScene(resultsVram, plane, plane, null, null, 0, 0,
                java.util.List.of(), palette.cram()));
        S3kBlueSpheresShowcase showcase = perfect.showcase();

        int frames = 0;
        int shown = 0;
        while (((session.difficultyWord >>> 8) & 0xFF) < 12) {
            assertFalse(perfect.update(0), "the loop never ends without a press");
            assertTrue(++frames < 1000, "difficulty stalled at " + (session.difficultyWord >>> 8));
            int reached = showcase.spawns().size();
            if (reached != shown) {
                shown = reached;
                assertEquals(shown, (session.difficultyWord >>> 8) & 0xFF, "one object per step");
                if (shown == 3 || shown == 6 || shown == 9 || shown == 11) {
                    for (int i = 0; i < 20; i++) perfect.update(0);
                    dump("showcase-" + shown, perfect.scene(palette));
                }
            }
        }
        String[] kinds = {"Sonic", "Tails", "Knuckles", "Eggman", "MechaSonic", "Ship",
                "SpawnChickens", "SpawnSquirrels", "SpawnRabbits", "MasterEmerald", "SKLogo", "SuperSonic"};
        List<S3kBlueSpheresShowcase.Spawn> spawns = showcase.spawns();
        assertEquals(12, spawns.size());
        for (int i = 0; i < 12; i++) {
            assertEquals(i + 1, spawns.get(i).difficulty());
            assertEquals(kinds[i], spawns.get(i).kind());
        }

        // The Super Sonic step: palette fade, then a $14-frame wait, then AniSuperSonic05.
        for (int i = 0; i < 90; i++) perfect.update(0);
        dump("showcase-12", perfect.scene(palette));
        assertTrue(showcase.superSonic(), "Obj_Difficulty_SuperSonic sets Super_Sonic_Knux_flag");
        assertEquals(0xFF, showcase.superPaletteStatus(), "the fade-in is done");
        assertTrue(session.paletteFrame >= 0x24 && session.paletteFrame < 0x36, "the Super cycle runs");

        S3kBlueSpheresObject sonic = spawns.get(0).object();
        assertNotNull(sonic.routine);
        assertEquals(5, sonic.anim, "loc_4E222 settles on anim 5");
        assertTrue(sonic.frame == 0xBA || sonic.frame == 0xBB, "AniSuperSonic05 frame, got " + sonic.frame);
        assertEquals(assets.mappings(S3kBlueSpheresAssets.MAP_SUPER_SONIC).size(), sonic.mappings.size());

        // Every drawn object by art tile: the spawners and Super Sonic are gone, the rest remain.
        Map<Integer, Integer> drawn = new TreeMap<>();
        for (S3kBlueSpheresObject slot : showcase.slots()) {
            if (slot.routine != null && slot.drawn) drawn.merge(slot.artTile & 0x7FF, 1, Integer::sum);
        }
        int base = S3kBlueSpheresAssets.TILE_DIFFICULTY;
        assertEquals(1, drawn.get(S3kBlueSpheresAssets.TILE_PLAYER_1), "Sonic " + drawn);
        assertEquals(1, drawn.get(S3kBlueSpheresAssets.TILE_TAILS), "Tails " + drawn);
        assertEquals(1, drawn.get(S3kBlueSpheresAssets.TILE_PLAYER_2), "Knuckles " + drawn);
        assertEquals(1, drawn.get(base + 0xB2), "Eggman " + drawn);
        assertEquals(1, drawn.get(S3kBlueSpheresAssets.TILE_MECHA_SONIC), "Mecha Sonic " + drawn);
        assertEquals(2, drawn.get(base + 0xEF), "ship and head " + drawn);
        assertEquals(4, drawn.get(base + 0x141), "chickens " + drawn);
        assertEquals(4, drawn.get(base + 0x14F), "squirrels " + drawn);
        assertEquals(4, drawn.get(base + 0x161), "rabbits " + drawn);
        assertEquals(1, drawn.get(S3kBlueSpheresAssets.TILE_SSZ_MASTER_EMERALD), "Master Emerald " + drawn);
        assertEquals(1, drawn.get(base), "SK logo " + drawn);
        assertEquals(1, drawn.get(base + 0x48), "TM " + drawn);

        // SuperHyper_PalCycle owns colours 2-4 of line 0.
        int offset = (session.paletteFrame - 6 < 0x24 ? 0x30 : session.paletteFrame - 6);
        for (int i = 0; i < 3; i++) {
            assertEquals(assets.word(S3kBlueSpheresAssets.PAL_CYCLE_SUPER_SONIC + offset + i * 2) & 0x0EEE,
                    palette.normalWord(2 + i), "Super Sonic colour " + i);
        }
        assertTrue(perfect.update(0x80), "a press after the target ends the loop");
    }

    private static void dump(String name, S3kBlueSpheresScene scene) throws Exception {
        NativeMenuRaster.Picture picture = scene.rasterize();
        int[] rgb = picture.rgb(scene.cram(), new PaletteFadePresentation(),
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL);
        BufferedImage image = new BufferedImage(NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT, rgb, 0, NativeMenuRaster.WIDTH);
        File dir = new File("target/blue-spheres");
        dir.mkdirs();
        ImageIO.write(image, "png", new File(dir, name + ".png"));
    }
}
