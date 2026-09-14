package com.openggf.game.patch;

import com.openggf.game.GameModule;
import com.openggf.game.ModApi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A module decorator contributed by an explicitly tagged owner.
 *
 * <p>The future engine-owned {@code ModuleResolutionService} orders eligible
 * registrations by owner and applies each patch over the previous result.
 */
@ModApi
public interface GamePatch {

    /** Stable namespaced patch identifier. */
    String id();

    String displayName();

    String baseGameId();

    boolean activatesFor(GameplayLaunchRequest request);

    Set<LogicalRom> romPrerequisites();

    /**
     * Logical ROMs the patch reads when they are available but does not
     * require. Resolution never gates on these; a patch opens each one through
     * {@link PatchContext#openLogicalRom(LogicalRom)} and falls back to the
     * behaviour its required ROMs support when the open fails.
     */
    default Set<LogicalRom> optionalRomPrerequisites() {
        return Set.of();
    }

    /**
     * Main-character codes contributed to launch UI; each is lowercase, nonblank,
     * and contains no leading or trailing whitespace.
     */
    List<String> providedMainCharacters();

    /** Registry metadata for provided characters; availability remains owned by the code list. */
    default Map<com.openggf.game.CharacterKey, com.openggf.game.CharacterDefinition>
    providedCharacterDefinitions() {
        return Map.of();
    }

    /** Decorates the module produced by all earlier eligible patches. */
    GameModule apply(GameModule base, PatchContext context);
}
