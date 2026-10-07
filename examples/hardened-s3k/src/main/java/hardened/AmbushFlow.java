package hardened;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.Level;

/** Session-owned presentation holds around ordinary native gameplay and checkpoint reloads. */
public final class AmbushFlow implements GameplayFrameController, RewindSnapshottable<AmbushFlow.Snapshot> {
    public enum Screen { ENTRY, PLAY, PAUSED, CLEAR, FAILED, RETRY, EXIT }
    public record Snapshot(Screen screen, int presentationTicks, int selected, boolean titleRequest, boolean freshRetry) { }
    private final EncounterState encounter;
    private final Canvas canvas;
    private Level installedLevel;
    private Screen screen = Screen.ENTRY;
    private int presentationTicks;
    private int selected;
    private boolean titleRequest;
    private boolean freshRetry;
    private InputHandler liveInput;

    AmbushFlow(EncounterState encounter, Canvas canvas) { this.encounter = encounter; this.canvas = canvas; }
    public Screen screen() { return screen; }
    void attachInput(InputHandler input) { liveInput = input; }
    boolean ownsMenuInput(InputHandler input) { return screen != Screen.PLAY || input.logical().menuStart(); }
    void newLaunch() {
        installedLevel = null; titleRequest = false; freshRetry = false; liveInput = null;
        encounter.resetForLoad(); change(Screen.ENTRY);
    }
    private boolean hasPhysicalPost() {
        var post = GameServices.level().getCheckpointState();
        return post.isActive() && post.getLastCheckpointIndex() == EncounterPlan.POST_INDEX;
    }
    private void change(Screen next) { screen = next; presentationTicks = 0; selected = 0; }

    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        var level = GameServices.level().getCurrentLevel();
        if (installedLevel != level) {
            installedLevel = level;
            encounter.resetForLoad();
            boolean nativePost = level != null && level.getObjects().stream().anyMatch(spawn ->
                    spawn.objectId() == 0x34 && spawn.subtype() == EncounterPlan.POST_INDEX
                    && spawn.x() == EncounterPlan.POST_X && spawn.y() == EncounterPlan.POST_Y);
            if (nativePost) change(Screen.ENTRY);
            else {
                encounter.abort("The surveyed checkpoint is unavailable");
                change(Screen.FAILED);
            }
        }
        presentationTicks++;
        if (screen == Screen.ENTRY) {
            // Our entry panel owns presentation. A native checkpoint restart
            // still queues its Level: card, whose omitted owner must complete
            // its real teardown and enemy-art handoff through this boundary.
            course.finishInitialPresentation();
            // Neutral ordinary rows let the native fade, terrain and enemy-art
            // owners finish. Releasing input happens only after the completed row.
            return true;
        }
        if (screen == Screen.PLAY) {
            if (input.menuStart()) { change(Screen.PAUSED); return false; }
            return true;
        }
        if (screen == Screen.RETRY) {
            if (presentationTicks >= 18) {
                // Native Level: re-entry consumes the actual physically saved starpost.
                // No fabricated checkpoint, position patch or outgoing-world rollback.
                if (freshRetry) course.loadLevel(7, 0);
                else GameServices.level().restartCurrentLevelAfterDeath();
                encounter.resetForLoad(); change(Screen.ENTRY);
                GameServices.fade().startFadeFromBlack(null);
            }
            return false;
        }
        if (screen == Screen.EXIT) {
            if (presentationTicks >= 18) titleRequest = true;
            return false;
        }
        boolean localMenu = liveInput != null && !liveInput.hasLogicalOverride();
        boolean back = localMenu ? MenuInput.back(liveInput) : input.menuBack();
        boolean up = localMenu ? MenuInput.up(liveInput) : input.menuUp();
        boolean down = localMenu ? MenuInput.down(liveInput) : input.menuDown();
        boolean accept = localMenu ? MenuInput.accept(liveInput) : input.menuAccept();
        if (back) {
            if (screen == Screen.PAUSED) change(Screen.PLAY);
            else change(Screen.EXIT);
            return false;
        }
        if (up || down) {
            selected = Math.floorMod(selected + (down ? 1 : -1), screen == Screen.PAUSED ? 3 : 2);
            GameServices.audio().playSfx(0x5B);
        }
        if (accept && presentationTicks > 8) {
            if (screen == Screen.PAUSED && selected == 0) change(Screen.PLAY);
            else if ((screen == Screen.PAUSED && selected == 1) || (screen != Screen.PAUSED && selected == 0)) {
                freshRetry = !hasPhysicalPost();
                GameServices.audio().playSfx(0x63); change(Screen.RETRY);
            } else change(Screen.EXIT);
        }
        return false;
    }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (!advanced) return;
        if (screen == Screen.ENTRY) {
            if (presentationTicks >= 45 && course.presentationReady()
                    && GameServices.runtimeArtCoordinator().levelEntryArtReady()) {
                encounter.begin(); change(Screen.PLAY);
            }
            return;
        }
        if (screen != Screen.PLAY) return;
        encounter.afterGameplayTick(GameServices.sprites().getMainPlayable(),
                hasPhysicalPost());
        if (encounter.failed()) change(Screen.FAILED);
        else if (encounter.cleared()) {
            GameServices.audio().playSfx(0x6A); // Native goal cue; this is an encounter, not act results.
            change(Screen.CLEAR);
        }
    }
    @Override public boolean nativePlayerInput() { return screen == Screen.PLAY; }
    // The world holds in menus; the ambient ROM music and navigation cues continue.
    // Independent host pause still selects native silent presentation.
    @Override public boolean presentationPaused() { return false; }
    @Override public boolean consumeTitleRequest() {
        boolean requested = titleRequest; titleRequest = false; return requested;
    }
    @Override public void drawOverlay() {
        var state = encounter;
        if (screen == Screen.PLAY) {
            canvas.rect(101, 2, 214, 20, 0x102326, .86f);
            canvas.label("POST TWO  " + state.volleys() + "/2 VOLLEYS", 108, 7, 0xFFE3A3, .65f);
            canvas.rect(9, 195, 302, 21, 0x102326, .88f);
            String cue = state.checkpointTouched() ? "WATCH THE SPIKES. MOVE AFTER AIM LOCKS" : "TOUCH THE STARPOST BEFORE THE TRIAL";
            boolean exitOpen = state.volleys() >= 2 && state.phase() == EncounterState.Phase.RESTING;
            if (state.volleys() >= 2) cue = exitOpen
                    ? "FOLLOW THE EXIT ARROW. KEEP TO THE UPPER PATH." : "SECOND VOLLEY! WAIT FOR RECOVERY.";
            canvas.center(cue, 202, 0xD5EFDC, .6f);
            int x = EncounterPlan.EXIT_X - GameServices.camera().getX();
            int y = EncounterPlan.EXIT_Y - GameServices.camera().getY() - 42;
            if (x >= 12 && x < 300 && y >= 30 && y < 184) {
                int colour = exitOpen ? 0xA4E4A6 : 0xB9CEC5;
                canvas.rect(x - 12, y, 20, 3, colour);
                canvas.rect(x + 4, y - 3, 3, 9, colour);
                canvas.label(exitOpen ? "EXIT" : "WAIT", x - 17, y - 16, colour, .65f);
            }
        } else if (screen == Screen.ENTRY) {
            canvas.panel(153, 61);
            canvas.center("POST TWO AMBUSH", 162, 0xFFE3A3, 1);
            canvas.center("TOUCH THE POST. WAIT FOR THE TELL.", 183, 0xD5EFDC, .65f);
            canvas.center("THEN DODGE BOTH VOLLEYS AND REACH THE EXIT.", 198, 0xD5EFDC, .6f);
        } else if (screen == Screen.RETRY || screen == Screen.EXIT) {
            canvas.rect(0, 0, 320, 224, 0x000000, Math.min(1, presentationTicks / 18f));
        } else {
            int rise = Math.max(0, 16 - presentationTicks);
            canvas.rect(0, 0, 320, 224, 0x071319, .56f);
            canvas.panel(60 + rise, 140);
            boolean aborted = state.status() == EncounterState.Status.ABORTED;
            String title = screen == Screen.CLEAR ? "AMBUSH CLEARED" : aborted ? "ENCOUNTER UNAVAILABLE"
                    : screen == Screen.FAILED ? "TRY THE TELL AGAIN" : "TAKE A BREATHER";
            canvas.center(title, 73 + rise, 0xFFE3A3, 1.2f);
            canvas.center(screen == Screen.CLEAR ? "CHECKPOINT SECURED. TWO VOLLEYS SURVIVED."
                    : aborted ? state.failureReason() : hasPhysicalPost() ? "THE POST KEEPS YOUR SAFE RETRY."
                    : "NO CHECKPOINT YET. START A FRESH ATTEMPT.", 98 + rise, 0xD5EFDC, .6f);
            int count = screen == Screen.PAUSED ? 3 : 2;
            for (int row = 0; row < count; row++) {
                int y = 122 + row * 20 + rise;
                if (row == selected) canvas.rect(26, y - 4, 268, 18, 0x3E6650);
                String text = screen == Screen.PAUSED && row == 0 ? "RESUME" : row == count - 1 ? "RETURN TO TITLE"
                        : hasPhysicalPost() ? "RETRY FROM CHECKPOINT" : "START A FRESH ATTEMPT";
                canvas.center(text, y, row == selected ? 0xFFE3A3 : 0xB9CEC5, .8f);
            }
            canvas.center("ARROWS / D-PAD  CHOOSE   ENTER / A  SELECT", 182 + rise, 0xA4D0BB, .6f);
        }
        canvas.flush();
    }
    @Override public String key() { return "hardened-s3k:flow"; }
    @Override public Snapshot capture() { return new Snapshot(screen, presentationTicks, selected, titleRequest, freshRetry); }
    @Override public void restore(Snapshot saved) {
        screen = saved.screen(); presentationTicks = saved.presentationTicks(); selected = saved.selected();
        titleRequest = saved.titleRequest(); freshRetry = saved.freshRetry();
    }
    @Override public void resetForMissingSnapshot() { newLaunch(); }
    @Override public void close() { encounter.abort("Encounter closed"); canvas.close(); }
}
