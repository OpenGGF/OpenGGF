package paradise;

import com.openggf.game.GameModule;
import com.openggf.game.patch.*;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.util.List;
import java.util.Set;

/** ROM-backed Sonic 2 Mini Golf creator entry point. */
public final class PuttPuttParadiseMod implements GgfMod {
    @Override public void register(ModContext context) { context.registerGamePatch(new Patch()); }

    public static final class Patch implements GamePatch {
        @Override public String id() { return "putt-putt-paradise:golf"; }
        @Override public String displayName() { return "Putt Putt Paradise"; }
        @Override public String baseGameId() { return "s2"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals("s2")
                    && (request.mainCharacter().equals("sonic") || request.mainCharacter().equals("tails"));
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new GolfModule(base); }
    }
}
