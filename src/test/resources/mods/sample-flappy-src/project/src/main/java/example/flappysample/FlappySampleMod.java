package example.flappysample;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.BakedSheetRef;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModZoneContribution;
import com.openggf.mods.code.RomArtCompression;
import com.openggf.mods.code.RomArtRequest;

public final class FlappySampleMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerObject("controller", (spawn, registry) -> new FlappyController(spawn));
        context.registerObject("pipe", (spawn, registry) -> new FlappyPipe(spawn));
        context.registerObjectArt("pipe", new BakedSheetRef("art/pipe.ggfs"));
        context.registerObjectPreview("pipe", "pipe");
        // Tails' flying body frames, materialized from the player's ROM at launch.
        // Literals verified against Sonic2Constants.java:112-117 and ART_TILE_TAILS
        // (0x07A0 -> palette bits 13-14 = line 0). Re-confirm at implementation.
        context.registerRomObjectArt("bird", new RomArtRequest(
                0x64320 /* ART_UNC_TAILS_ADDR */, RomArtCompression.UNCOMPRESSED,
                0xB8C0 /* ART_UNC_TAILS_SIZE */,
                0x739E2 /* MAP_UNC_TAILS_ADDR */,
                0x7446C /* MAP_R_UNC_TAILS_ADDR (DPLC) */,
                0 /* palette line from ART_TILE_TAILS */, 1));
        context.registerZone(new ModZoneContribution("flappy-garden",
                new BakedLevelRef("levels/flappy/level.json"), "ehz2", null));
    }
}
