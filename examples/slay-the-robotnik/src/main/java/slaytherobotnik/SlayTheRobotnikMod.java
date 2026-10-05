package slaytherobotnik;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import slaytherobotnik.scene.SlayScene;

/**
 * Mod entry point. Registration does three things:
 * <ol>
 *   <li>reads the text-art assets (font and icons) while the mod's files are open,</li>
 *   <li>registers {@link SlayScene} as the startup scene, so choosing Sonic 3 &amp; Knuckles on
 *       the master title opens the card game instead of the stock title screen, and</li>
 *   <li>registers a small patch asking for the 16:9 (400-pixel) display the layout is drawn for.</li>
 * </ol>
 * Everything else happens inside the scene.
 */
public final class SlayTheRobotnikMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        byte[] font = asset(context, "art/font.txt");
        byte[] icons = asset(context, "art/icons.txt");
        byte[] cards = asset(context, "art/cards.txt");
        context.registerStartupScene(() -> new SlayScene(font, icons, cards));
        context.registerGamePatch(new WidescreenPatch());
    }

    private static byte[] asset(ModContext context, String path) {
        try {
            return context.modAssets().readBounded(path, 1 << 20);
        } catch (IOException e) {
            throw new IllegalStateException("Slay the Robotnik is missing its asset " + path, e);
        }
    }

    /** Requests the 16:9 display for the whole session (the player's own setting returns at the master title). */
    public static final class WidescreenPatch implements GamePatch {
        @Override public String id() { return "widescreen"; }
        @Override public String displayName() { return "Slay the Robotnik display"; }
        @Override public String baseGameId() { return "s3k"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s3k"); }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }

        @Override
        public GameModule apply(GameModule base, PatchContext context) {
            return new DelegatingGameModule(base, id()) {
                @Override
                public String requiredDisplayAspect() {
                    return "WIDE_16_9";
                }
            };
        }
    }
}
