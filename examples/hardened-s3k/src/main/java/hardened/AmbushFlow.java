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
    public enum Screen { ENTRY, PLAY, CAUGHT, PAUSED, CLEAR, FAILED, RETRY, EXIT }
    public record Snapshot(Screen screen, int presentationTicks, int selected, boolean titleRequest, boolean freshRetry) { }
    /** Panel slide in/out, in presentation rows (entry) or admitted gameplay ticks (release). */
    static final int SLIDE_TICKS = 16;
    /**
     * Longest native death fall shown before the result panel. S3K routine 6 restarts the
     * level once the corpse passes Camera_Y_pos+$100 (sonic3k.asm:24541); this hold ends
     * at the bottom of the 224-row view, well before that, so the native restart, life
     * loss and game-over owners never run. Our retry menu owns what happens next.
     */
    static final int CAUGHT_MAX_TICKS = 120, CAUGHT_VIEW_BOTTOM = 224 + 4;
    private static final float RESULT_DIM = .56f;
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
    void attachInput(InputHandler input) { liveInput = input; canvas.refreshPrompts(input); }
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
        // The native death arc keeps running on neutral rows, so the hit that ended the
        // run is visible before the result panel covers the world.
        if (screen == Screen.CAUGHT) return true;
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
        if (screen == Screen.CAUGHT) {
            var player = GameServices.sprites().getMainPlayable();
            int below = player == null ? CAUGHT_VIEW_BOTTOM : player.getCentreY() - GameServices.camera().getY();
            if (below >= CAUGHT_VIEW_BOTTOM || presentationTicks >= CAUGHT_MAX_TICKS) change(Screen.FAILED);
            return;
        }
        if (screen != Screen.PLAY) return;
        encounter.afterGameplayTick(GameServices.sprites().getMainPlayable(),
                hasPhysicalPost());
        if (encounter.failed()) change(encounter.aborted() ? Screen.FAILED : Screen.CAUGHT);
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
        switch (screen) {
            case PLAY -> {
                drawStatus();
                drawCoach(coachLine(), coachColour());
                drawExitMarker();
                // The entry card slides away over the first released ticks; movement is live.
                if (encounter.ticks() < SLIDE_TICKS) drawEntryCard(encounter.ticks() * 5);
            }
            case ENTRY -> drawEntryCard(Math.max(0, SLIDE_TICKS - presentationTicks) * 5);
            case CAUGHT -> {
                // A short red wash marks the fatal contact, then the native fall reads clearly.
                if (presentationTicks < 10) canvas.rect(0, 0, Canvas.WIDTH, Canvas.HEIGHT, 0xC0201A,
                        .32f * (10 - presentationTicks) / 10f);
                drawStatus();
                drawCoach("CAUGHT!", Canvas.ALERT);
            }
            // Continue from the result panel's dim backdrop to black, with no brightness pop.
            case RETRY, EXIT -> canvas.rect(0, 0, Canvas.WIDTH, Canvas.HEIGHT, 0x000000,
                    RESULT_DIM + (1 - RESULT_DIM) * Math.min(1, presentationTicks / 18f));
            default -> drawResult();
        }
        canvas.flush();
    }

    /** Top-right volley pips, clear of the native SCORE/TIME/RINGS digits. */
    private void drawStatus() {
        canvas.rect(214, 6, 98, 16, Canvas.INK, .86f);
        canvas.border(214, 6, 98, 16, 0x4E7A66);
        canvas.label("VOLLEYS", 220, 9, Canvas.SAGE);
        for (int volley = 0; volley < EncounterPlan.VOLLEY_COUNT; volley++) {
            int x = 287 + volley * 12;
            boolean spent = encounter.volleys() > volley;
            canvas.rect(x - 1, 9, 10, 10, 0x061012);
            canvas.rect(x, 10, 8, 8, spent ? Canvas.AMBER : 0x2C4A40);
        }
    }

    /** One short coaching line driven by the encounter's published phase. */
    private String coachLine() {
        var state = encounter;
        boolean post = state.checkpointTouched();
        return switch (state.phase()) {
            case WAITING -> post ? "WALK IN. WATCH THE TELL" : "TOUCH THE STARPOST";
            case TELL -> "WAIT FOR THE LOCK...";
            case LOCKED -> "LOCKED! MOVE NOW!";
            case VOLLEY_ONE -> "DODGE! ONE MORE VOLLEY";
            case VOLLEY_TWO -> "LAST VOLLEY! KEEP CLEAR";
            case RECOVERY -> "HOLD ON. EXIT OPENING";
            // Clear also requires the physical post (EncounterState.afterGameplayTick).
            case RESTING -> post ? "GO! REACH THE EXIT >" : "TOUCH THE POST, THEN EXIT";
        };
    }
    private int coachColour() {
        return switch (encounter.phase()) {
            case LOCKED -> presentationTicks / 4 % 2 == 0 ? Canvas.ALERT : Canvas.GOLD;
            case VOLLEY_ONE, VOLLEY_TWO -> Canvas.ALERT;
            case RESTING -> encounter.checkpointTouched() ? Canvas.CLEAR : Canvas.MINT;
            default -> Canvas.MINT;
        };
    }
    /** Bottom bar that starts right of the native lives icon (x 16..60). */
    private void drawCoach(String line, int colour) {
        canvas.rect(68, 198, 244, 17, Canvas.INK, .86f);
        canvas.rect(68, 198, 244, 1, 0x4E7A66);
        canvas.label(line, 68 + (244 - canvas.width(line, 1)) / 2, 202, colour);
    }
    private void drawExitMarker() {
        boolean open = encounter.volleys() >= EncounterPlan.VOLLEY_COUNT && encounter.checkpointTouched()
                && encounter.phase() == EncounterState.Phase.RESTING;
        int x = EncounterPlan.EXIT_X - GameServices.camera().getX();
        int y = EncounterPlan.EXIT_Y - GameServices.camera().getY() - 42;
        if (x < 24 || x >= 300 || y < 30 || y >= 184) return;
        int colour = open ? Canvas.CLEAR : 0x9FB4AA;
        int nudge = open ? presentationTicks / 8 % 3 : 0;
        // Outlined arrow pointing along the shelf, with its state word above.
        canvas.rect(x - 13 + nudge, y - 1, 22, 5, 0x061012);
        canvas.rect(x + 3 + nudge, y - 4, 5, 11, 0x061012);
        canvas.rect(x - 12 + nudge, y, 20, 3, colour);
        canvas.rect(x + 4 + nudge, y - 3, 3, 9, colour);
        canvas.rect(x + 7 + nudge, y - 1, 2, 5, colour);
        String word = open ? "EXIT" : "WAIT";
        canvas.label(word, x - canvas.width(word, 1) / 2, y - 14, colour);
    }

    private void drawEntryCard(int drop) {
        if (drop >= 80) return;
        canvas.panel(146 + drop, 66);
        canvas.center("POST TWO AMBUSH", 154 + drop, Canvas.GOLD, 2);
        canvas.center("TOUCH THE POST. WATCH THE TELL.", 180 + drop, Canvas.MINT);
        canvas.center("DODGE 2 VOLLEYS, THEN EXIT.", 194 + drop, Canvas.MINT);
    }

    private void drawResult() {
        var state = encounter;
        int rise = Math.max(0, SLIDE_TICKS - presentationTicks) * 2;
        canvas.rect(0, 0, Canvas.WIDTH, Canvas.HEIGHT, 0x071319, RESULT_DIM);
        int top = 46 + rise;
        canvas.panel(top, 138);
        boolean aborted = state.aborted();
        boolean post = hasPhysicalPost();
        String title = screen == Screen.CLEAR ? "AMBUSH CLEARED" : aborted ? "UNAVAILABLE"
                : screen == Screen.FAILED ? "CAUGHT!" : "PAUSED";
        int titleColour = screen == Screen.CLEAR ? Canvas.CLEAR : screen == Screen.FAILED && !aborted
                ? Canvas.ALERT : Canvas.GOLD;
        canvas.headline(title, top + 9, titleColour);
        if (screen == Screen.CLEAR) {
            canvas.center("BOTH VOLLEYS DODGED", top + 36, Canvas.MINT);
            // Admitted gameplay ticks since movement was released; the native HUD
            // TIME also counts the entry hold, so this is labelled as seconds.
            int centis = state.ticks() * 100 / 60;
            var player = GameServices.sprites().getMainPlayable();
            int rings = player == null ? 0 : player.getRingCount();
            canvas.center(String.format(java.util.Locale.ROOT, "%d.%02d SEC   RINGS %d",
                    centis / 100, centis % 100, rings), top + 50, Canvas.SAGE);
        } else if (aborted) {
            canvas.centerWrapped(state.failureReason(), top + 36, Canvas.MINT, 2);
        } else if (screen == Screen.FAILED) {
            canvas.center("MOVE THE MOMENT IT LOCKS", top + 36, Canvas.MINT);
            canvas.center(post ? "THE STARPOST KEEPS YOUR PLACE" : "NO STARPOST YET: FRESH START",
                    top + 50, Canvas.SAGE);
        } else {
            canvas.center("THE AMBUSH WAITS FOR YOU", top + 36, Canvas.MINT);
            canvas.center(post ? "RETRY RETURNS TO THE STARPOST" : "RETRY STARTS FRESH",
                    top + 50, Canvas.SAGE);
        }
        int count = screen == Screen.PAUSED ? 3 : 2;
        for (int row = 0; row < count; row++) {
            String text = screen == Screen.PAUSED && row == 0 ? "RESUME" : row == count - 1 ? "RETURN TO TITLE"
                    : post ? "RETRY FROM STARPOST" : "START FRESH";
            canvas.option(text, top + 72 + row * 17, row == selected, presentationTicks);
        }
        canvas.center(canvas.directionPrompt() + " CHOOSE   " + canvas.confirmPrompt() + " SELECT",
                top + 124, Canvas.SAGE);
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
