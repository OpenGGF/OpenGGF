package hardened;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;

/** Animated encounter lesson and title; selection is held by its session module. */
public final class Title implements TitleScreenProvider {
    private static final int RISE_TICKS = 24, LESSON_TOP = 86, MENU_TOP = 92;
    private final Canvas canvas;
    private final Runnable launch;
    private State state = State.INACTIVE;
    private int ticks;
    private boolean lesson;
    private int selected;
    /** Rows since the lesson page opened or closed; drives its short page slide. */
    private int pageTicks;

    Title(Canvas canvas, Runnable launch) { this.canvas = canvas; this.launch = launch; }
    @Override public void initialize() {
        state = State.ACTIVE; ticks = 0; lesson = false; selected = 0; pageTicks = RISE_TICKS;
        GameServices.audio().playMusic(0x0F); // S&K music table: MHZ1, loaded through the native driver.
    }
    @Override public void update(InputHandler input) {
        ticks++; pageTicks++;
        if (state != State.ACTIVE || input == null) return;
        canvas.refreshPrompts(input);
        boolean replay = input.hasLogicalOverride();
        var logical = input.logical();
        boolean back = replay ? logical.menuBack() : MenuInput.back(input);
        boolean up = replay ? logical.menuUp() : MenuInput.up(input);
        boolean down = replay ? logical.menuDown() : MenuInput.down(input);
        boolean accept = replay ? logical.menuAccept() : MenuInput.accept(input);
        if (back) {
            if (lesson) { lesson = false; pageTicks = 0; GameServices.audio().playSfx(0x5B); }
            return;
        }
        if (!lesson && (up || down)) {
            selected = 1 - selected;
            GameServices.audio().playSfx(0x5B); // Native switch cue.
        }
        if (accept) {
            if (!lesson && selected == 1) { lesson = true; pageTicks = 0; GameServices.audio().playSfx(0x33); }
            else {
                launch.run();
                GameServices.audio().playSfx(0x63); // Native starpost cue accompanies the entry fade.
                state = State.EXITING;
            }
        }
    }
    @Override public void draw() {
        if (state == State.INACTIVE) return;
        canvas.rect(0, 0, Canvas.WIDTH, Canvas.HEIGHT, 0x0B191E);
        // Soft canopy columns and drifting spores belong only to menu presentation.
        for (int band = 0; band < 7; band++) {
            int x = Math.floorMod(band * 53 - ticks / 4, 371) - 40;
            canvas.rect(x, 0, 26, Canvas.HEIGHT, 0x14302B, .55f);
            canvas.rect(x + 9, 0, 8, Canvas.HEIGHT, 0x1B3D35, .45f);
        }
        for (int spore = 0; spore < 16; spore++) {
            int x = Math.floorMod(spore * 71 + ticks / (3 + spore % 3) - spore * spore, Canvas.WIDTH);
            int y = Canvas.HEIGHT - Math.floorMod(spore * 43 + ticks * (1 + spore % 2) / 3, Canvas.HEIGHT + 16);
            canvas.rect(x, y, 2, 2, Canvas.GOLD, .28f + spore % 3 * .1f);
        }
        canvas.rect(0, 0, Canvas.WIDTH, 3, 0xFFCF74);
        canvas.rect(0, Canvas.HEIGHT - 3, Canvas.WIDTH, 3, 0xFFCF74);
        canvas.center("POST TWO AMBUSH", 13, Canvas.GOLD, 2);
        canvas.center("MUSHROOM HILL ZONE  ACT 1", 39, Canvas.SAGE);
        canvas.warning(160, 70 + (ticks / 20 % 2), ticks, true);
        int rise = Math.max(0, RISE_TICKS - ticks) * 4;
        // Lesson and menu pages share one panel; switching nudges the page up into place.
        int page = Math.max(0, 6 - pageTicks) * 3;
        if (lesson) {
            int top = LESSON_TOP + rise + page;
            canvas.panel(top, 116);
            canvas.center("WATCH. WAIT. MOVE.", top + 7, Canvas.AMBER);
            canvas.label("1 TOUCH THE STARPOST", 22, top + 25, Canvas.MINT);
            // Shape, motion and sound carry each rule; colour only reinforces them.
            canvas.label("2 A SIGHT CLOSES IN ON YOU", 22, top + 38, Canvas.MINT);
            canvas.label("3 IT FLASHES AND LOCKS: MOVE!", 22, top + 51, Canvas.MINT);
            canvas.label("4 DODGE 2 VOLLEYS, THEN EXIT", 22, top + 64, Canvas.MINT);
            canvas.center("THE RING BY THE POST SAVES YOU", top + 81, Canvas.AMBER);
            canvas.center(canvas.confirmPrompt() + " BEGIN   " + canvas.backPrompt() + " BACK", top + 100, Canvas.SAGE);
        } else {
            int top = MENU_TOP + rise + page;
            canvas.panel(top, 104);
            canvas.center("SOLO SONIC  /  NO SAVE", top + 8, Canvas.SAGE);
            for (int row = 0; row < 2; row++) {
                canvas.option(row == 0 ? "BEGIN AMBUSH" : "HOW TO PLAY", top + 31 + row * 21, row == selected, ticks);
            }
            canvas.center(canvas.directionPrompt() + " CHOOSE   " + canvas.confirmPrompt() + " SELECT",
                    top + 86, Canvas.SAGE);
        }
        if (rise == 0) canvas.center(canvas.startPrompt() + " IN PLAY: RETRY MENU", 207, Canvas.SAGE);
        canvas.flush();
    }
    @Override public void setClearColor() { }
    @Override public void reset() { state = State.INACTIVE; }
    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }
    @Override public TitleScreenAction consumeExitAction() { return TitleScreenAction.ONE_PLAYER; }
    @Override public int startZoneIndex() { return 7; }
    @Override public int startActIndex() { return 0; }
}
