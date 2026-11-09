package mica;

import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.mods.code.ModContext;

/** Phase 2 registration, shared by every supported host. */
final class TalonMod {
    static void register(ModContext context, PlayableSheetMaterializer.MaterializedArt micaPlaceholder) {
        // TEMPORARY COPY: TALON uses MICA's materialized sheet until the art lane lands.
        context.registerCharacter("talon", TalonCharacter.definition(context.ownerModId(), micaPlaceholder));
    }
}
