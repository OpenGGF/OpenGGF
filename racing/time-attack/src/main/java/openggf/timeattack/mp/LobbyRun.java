package openggf.timeattack.mp;

import com.openggf.mods.run.GhostPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunStep;
import com.openggf.net.identity.PlayerAppearance;

import java.util.List;
import java.util.Objects;

/**
 * Host of a room's lobby level: the run plays behind the lobby menu (the Time Attack scene stays
 * open over it), streams this player's pose to the room as lobby presence and draws the other
 * players as named ghosts in their looks. The menu owns the controller while it is shown; hiding
 * it hands the controller to the character. Nothing is timed or recorded, and an act completion
 * restarts the act.
 */
public final class LobbyRun implements RunHost {
    private final MultiplayerRaceCoordinator coordinator;
    private final PlayerAppearance look;
    private RunHandle handle;
    private boolean menuShown = true;
    private boolean ended;
    private RunEndReason endReason;

    public LobbyRun(MultiplayerRaceCoordinator coordinator, PlayerAppearance look) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.look = Objects.requireNonNull(look, "look");
    }

    /** The engine's handle for this run; the menu starts shown, so the controls start held. */
    public void attachHandle(RunHandle handle) {
        this.handle = handle;
        if (handle != null) {
            handle.setPlayerAppearance(look);
            handle.holdControls(menuShown);
            handle.setLevelCovered(menuShown);
        }
    }

    /** Shows the lobby menu (the character gets no input) or hides it (the menu gets none). */
    public void setMenuShown(boolean shown) {
        menuShown = shown;
        if (handle != null) {
            handle.holdControls(shown);
            handle.setLevelCovered(shown);
        }
    }

    public boolean isMenuShown() {
        return menuShown;
    }

    /** Asks the engine to end the lobby run at its next safe boundary. */
    public void leave() {
        if (handle != null && handle.isActive()) {
            handle.leave();
        }
    }

    /** True until the run has ended. */
    public boolean isActive() {
        return !ended && (handle == null || handle.isActive());
    }

    /** Why the run ended, or null while it plays. */
    public RunEndReason endReason() {
        return endReason;
    }

    @Override
    public void onLevelReady(RunLevelStart start) {
        // Every load (launch, act restart) starts a fresh presence stream at the lobby spawn.
        coordinator.beginLobbyPresence();
    }

    @Override
    public void afterStep(RunStep step) {
        coordinator.publishLobbyPose(step.player());
        coordinator.advanceLobbyGhosts();
    }

    @Override
    public boolean retryOnActCompletion() {
        return true;
    }

    @Override
    public List<GhostPose> ghosts() {
        return coordinator.lobbyGhosts();
    }

    @Override
    public void onRunEnded(RunEndReason reason) {
        ended = true;
        endReason = reason;
        coordinator.endLobbyPresence();
    }
}
