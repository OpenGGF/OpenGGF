package com.openggf.game.sonic2.kis2;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;

import java.util.List;
import java.util.Set;

/**
 * Knuckles in Sonic 2: the official S&amp;K lock-on program over Sonic 2,
 * implemented from the s2disasm {@code knuckles-in-sonic-2} branch
 * ({@code docs/kis2/BRANCH_DIFFS.md}). Activates when Knuckles is the
 * requested main character for Sonic 2.
 *
 * <p>Tier one requires only the logical S&amp;K ROM. Chip-resident assets
 * (title, special stage, ending banner, CNZ layouts) are tier two.
 */
public final class Kis2GamePatch implements GamePatch {

    public static final String ID = Kis2Constants.PATCH_ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Knuckles in Sonic 2";
    }

    @Override
    public String baseGameId() {
        return "s2";
    }

    @Override
    public boolean activatesFor(GameplayLaunchRequest request) {
        return "knuckles".equals(request.mainCharacter());
    }

    @Override
    public Set<LogicalRom> romPrerequisites() {
        return Set.of(LogicalRom.SK);
    }

    @Override
    public List<String> providedMainCharacters() {
        return List.of("knuckles");
    }

    @Override
    public GameModule apply(GameModule base, PatchContext context) {
        return new Kis2GameModule(base, context);
    }
}
