package echoes;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.mode.*;
import com.openggf.game.patch.*;
import com.openggf.mods.code.*;
import java.util.*;

/** Test-only Sonic Echoes promo player-action schedule, 2026-10-24.
 * Input: echoes.capture.actions=loop:loopFrame:ECHO|RESET|UNDO,... (zero-based loop).
 * Composes the shared observer unchanged. Commands follow RoomActions' command route;
 * no gameplay state, recording, position or drift values are injected.
 */
public final class PromoActionCaptureMod implements GgfMod {
    @Override public void register(ModContext context) {
        new PromoCaptureMod().register(context);
        context.registerGamePatch(new GamePatch() {
            public String id() { return EchoesMod.ID + ":promo-player-actions"; }
            public String displayName() { return "Scheduled promo player actions"; }
            public String baseGameId() { return "s2"; }
            public boolean activatesFor(GameplayLaunchRequest r) { return r.gameId().equals("s2"); }
            public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
            public List<String> providedMainCharacters() { return List.of(); }
            public GameModule apply(GameModule base, PatchContext patch) { return new ActionModule(base); }
        });
    }
    static final class ActionModule extends DelegatingGameModule {
        final RoomSession session;
        final List<String> schedule = Arrays.stream(System.getProperty("echoes.capture.actions", "").split(","))
                .filter(s -> !s.isBlank()).toList();
        int next;
        GameplayFrameController controller;
        ActionModule(GameModule base) {
            super(base, "sonic-echoes:promo-player-actions");
            session = base.getGameService(RoomSession.class);
        }
        public GameplayFrameController gameplayFrameController() {
            if (controller != null) return controller;
            var delegate = super.gameplayFrameController();
            if (delegate == null) return null;
            return controller = new GameplayFrameController() {
                public boolean beforeTick(CourseControl c, LogicalInputSnapshot r) { return delegate.beforeTick(c,r); }
                public GameplayFrameDisposition beforeFrame(CourseControl c, LogicalInputSnapshot r) {
                    if (next < schedule.size() && session.rewindHold() == 0 && !session.cleared()) {
                        String[] action = schedule.get(next).split(":");
                        if (session.loopCount() == Integer.parseInt(action[0]) && session.frame() == Integer.parseInt(action[1])) {
                            session.command(RoomSession.Command.valueOf(action[2]));
                            next++;
                        }
                    }
                    return delegate.beforeFrame(c,r);
                }
                public void afterTick(CourseControl c, boolean a) { delegate.afterTick(c,a); }
                public boolean allowsDebugRewind() { return delegate.allowsDebugRewind(); }
                public boolean consumeTitleRequest() { return delegate.consumeTitleRequest(); }
                public void drawOverlay() { delegate.drawOverlay(); }
                public void close() { delegate.close(); }
            };
        }
    }
}
