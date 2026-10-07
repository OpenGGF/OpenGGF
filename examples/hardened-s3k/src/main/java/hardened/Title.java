package hardened;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;

/** Animated encounter lesson and title; selection is held by its session module. */
public final class Title implements TitleScreenProvider {
    private final Canvas canvas;
    private final Runnable launch;
    private State state = State.INACTIVE;
    private int ticks;
    private boolean lesson;
    private int selected;

    Title(Canvas canvas, Runnable launch) { this.canvas = canvas; this.launch = launch; }
    @Override public void initialize() {
        state = State.ACTIVE; ticks = 0; lesson = false; selected = 0;
        GameServices.audio().playMusic(0x0F); // S&K music table: MHZ1, loaded through the native driver.
    }
    @Override public void update(InputHandler input) {
        ticks++;
        if (state != State.ACTIVE || input == null) return;
        boolean replay = input.hasLogicalOverride();
        var logical = input.logical();
        boolean back = replay ? logical.menuBack() : MenuInput.back(input);
        boolean up = replay ? logical.menuUp() : MenuInput.up(input);
        boolean down = replay ? logical.menuDown() : MenuInput.down(input);
        boolean accept = replay ? logical.menuAccept() : MenuInput.accept(input);
        if (back) { lesson = false; return; }
        if (!lesson && (up || down)) {
            selected = 1 - selected;
            GameServices.audio().playSfx(0x5B); // Native switch cue.
        }
        if (accept) {
            if (!lesson && selected == 1) { lesson = true; GameServices.audio().playSfx(0x33); }
            else {
                launch.run();
                GameServices.audio().playSfx(0x63); // Native starpost cue accompanies the entry fade.
                state = State.EXITING;
            }
        }
    }
    @Override public void draw() {
        if (state == State.INACTIVE) return;
        canvas.rect(0, 0, 320, 224, 0x0B191E);
        // Subtle sliding canopy bands and spores belong only to menu presentation.
        for (int band = 0; band < 8; band++) {
            int x = Math.floorMod(band * 47 - ticks / 3, 368) - 48;
            canvas.rect(x, 20, 18, 188, 0x193B35, .35f);
        }
        canvas.rect(0, 0, 320, 3, 0xFFCF74);
        canvas.center("POST TWO AMBUSH", 18, 0xFFE3A3, 1.6f);
        canvas.center("MUSHROOM HILL  /  ONE SHORT TRIAL", 40, 0xA4D0BB, .65f);
        canvas.warning(160, 73 + (ticks / 20 % 2), ticks, true);
        int rise = Math.max(0, 24 - ticks);
        canvas.panel(92 + rise, 103);
        if (lesson) {
            canvas.center("WATCH. WAIT. MOVE.", 102 + rise, 0xFFCF74, 1);
            canvas.label("1  TOUCH THE REAL CHECKPOINT.", 20, 121 + rise, 0xD5EFDC, .65f);
            canvas.label("2  FLASHING SPIKES AIM. MOVE AFTER THE LOCK.", 20, 134 + rise, 0xD5EFDC, .65f);
            canvas.label("3  DODGE TWO VOLLEYS. FOLLOW THE EXIT.", 20, 147 + rise, 0xD5EFDC, .65f);
            canvas.label("RINGS PROTECT ONCE. STAY ON THE UPPER PATH.", 20, 162 + rise, 0xFFCF74, .65f);
            canvas.center("ENTER / A / START  BEGIN", 180 + rise, 0xFFFFFF, .65f);
        } else {
            canvas.center("SOLO SONIC  /  NO SAVE", 104 + rise, 0xA4D0BB, .65f);
            for (int row = 0; row < 2; row++) {
                int y = 123 + row * 24 + rise;
                if (row == selected) { canvas.rect(35, y - 4, 250, 21, 0x3E6650); }
                canvas.center(row == 0 ? "BEGIN AMBUSH" : "HOW TO PLAY", y, row == selected ? 0xFFE3A3 : 0xB9CEC5, 1);
            }
            canvas.center("ARROWS / D-PAD  CHOOSE   ENTER / A  SELECT", 180 + rise, 0xA4D0BB, .65f);
        }
        if (rise == 0) canvas.center("MOVE + JUMP AS USUAL. START OPENS THE RETRY MENU.", 207, 0xA4D0BB, .6f);
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
