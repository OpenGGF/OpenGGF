package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Knuckles in Sonic 2: the official S&amp;K lock-on program over Sonic 2,
 * implemented from the s2disasm {@code knuckles-in-sonic-2} branch
 * ({@code docs/kis2/BRANCH_DIFFS.md}). Activates when Knuckles is the
 * requested main character for Sonic 2.
 *
 * <p>Tier one requires only the logical S&amp;K ROM. Tier two additionally
 * reads the 256 KiB chip through the {@link LogicalRom#KIS2} lock-on address
 * space when the user-supplied S&amp;K + Sonic 2 dump is available: CNZ
 * layouts, the chip-resident art patches and the chip palettes. Without the
 * dump the patch runs tier one unchanged.
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

    /** The lock-on dump is optional: it upgrades the patch from tier one to tier two. */
    @Override
    public Set<LogicalRom> optionalRomPrerequisites() {
        return Set.of(LogicalRom.KIS2);
    }

    /** Whether this launch can use KiS2 but falls back to tier one without the chip. */
    public boolean usesTierOne(GameplayLaunchRequest request, Supplier<RomImageCatalogue> catalogue) {
        if (!baseGameId().equals(request.gameId()) || !activatesFor(request)) return false;
        RomImageCatalogue roms = catalogue.get();
        if (!roms.isAvailable(RomIdentity.SK)) return false;
        try {
            return roms.open(RomIdentity.KIS2).size() < Kis2Constants.CHIP_WINDOW_END;
        } catch (IOException unavailable) {
            return true;
        }
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
