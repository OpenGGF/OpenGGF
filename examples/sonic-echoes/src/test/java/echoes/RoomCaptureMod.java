package echoes;

import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.mods.code.*;
import java.util.*;

/**
 * Capture-only entrypoint for GameplayCaptureTool's --mod seam. Origin: Phase 2 content,
 * 2026-10-24. Input: -Dechoes.capture.room=<room ID>. It selects data before stock boot;
 * gameplay, objects, input recording and snapshot restoration are the ordinary mod's.
 * Package this test source in a temporary capture jar, never the distributed mod jar.
 */
public final class RoomCaptureMod implements GgfMod {
    @Override public void register(ModContext context) {
        new EchoesMod().register(context);
        context.registerGamePatch(new GamePatch() {
            @Override public String id() { return EchoesMod.ID+":capture-selection"; }
            @Override public String displayName() { return "Authored room capture"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s2"); }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext patch) {
                base.getGameService(RoomLauncher.class).launch(System.getProperty("echoes.capture.room","1-1"));
                return base;
            }
        });
    }
}
