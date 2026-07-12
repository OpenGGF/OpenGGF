package example.phase2sample;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.BakedSheetRef;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModZoneContribution;

public final class Phase2SampleMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerObject("sample-badnik", (spawn, registry) -> new SampleBadnik(spawn));
        context.registerObjectArt("sample-badnik", new BakedSheetRef("art/sample.ggfs"));
        context.registerObjectPreview("sample-badnik", "sample-badnik");
        context.registerCharacter("sample-character",
                SampleCharacter.definition(context.ownerModId()));
        context.registerZone(new ModZoneContribution("sample-zone",
                new BakedLevelRef("levels/sample/level.json"), "mtz3", null));
    }
}
