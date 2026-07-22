package example.romartremix;

import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.ModZoneContribution;
import com.openggf.mods.code.RomArtCompression;
import com.openggf.mods.code.RomArtRequest;

public final class RomArtRemixMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.registerObject("tails-flight-art",
                (spawn, registry) -> new TailsFlightArtObject(spawn));
        context.registerRomObjectArt("tails-flight", new RomArtRequest(
                0x64320, RomArtCompression.UNCOMPRESSED, 0xB8C0,
                0x739E2, 0x7446C, 0, 1));
        context.registerZone(new ModZoneContribution("rom-art-gallery",
                new BakedLevelRef("levels/rom-art-gallery/level.json"), "ehz2", null, false));
    }
}
