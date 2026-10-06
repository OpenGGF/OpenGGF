package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import slaytherobotnik.art.Art;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.run.Run;
import slaytherobotnik.run.SaveCodec;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Controls;
import slaytherobotnik.ui.SmallFont;

/**
 * Shared state and services for all screens: the scene context, input, font, art, content
 * catalog, the current run, music, saving, and fade transitions between screens.
 */
public final class Shell {
    private static final int FADE_TICKS = 14;
    static final String SAVE_FILE = "run.properties";

    public final SceneContext ctx;
    public final Controls in = new Controls();
    public final SmallFont font;
    public final Art art;
    public final Catalog catalog;
    public final Profile profile;
    public Run run;
    public long ticks;

    private Screen screen;
    private Screen pending;
    private int fadeOut;
    private int fadeIn;
    private int currentMusic = -1;
    private int shake;
    private final CardTips cardTips = new CardTips();

    Shell(SceneContext ctx, SmallFont font, Art art, Catalog catalog) {
        this.ctx = ctx;
        this.font = font;
        this.art = art;
        this.catalog = catalog;
        this.profile = Profile.load(ctx.storage());
    }

    public int width() {
        return ctx.width();
    }

    public int height() {
        return ctx.height();
    }

    public Screen screen() {
        return screen;
    }

    /** Fades to black, switches to {@code next}, and fades back in. */
    public void go(Screen next) {
        if (pending != null) {
            return;
        }
        pending = next;
        fadeOut = FADE_TICKS;
    }

    /** Switches immediately (no fade). */
    public void goNow(Screen next) {
        screen = next;
        pending = null;
        fadeOut = 0;
        in.consume();
        next.enter(this);
    }

    /** True while a transition is running (screens ignore input). */
    public boolean transitioning() {
        return pending != null || fadeOut > 0;
    }

    /** Plays a music track unless it is already playing. */
    public void music(int id) {
        if (profile.get(SettingsScreen.NO_MUSIC) == 1) {
            currentMusic = id;
            return;
        }
        if (id != currentMusic) {
            currentMusic = id;
            ctx.audio().playMusic(id);
        }
    }

    public void stopMusic() {
        currentMusic = -1;
        ctx.audio().fadeOutMusic();
    }

    /** Plays the current track again after the music setting is switched back on. */
    public void resumeMusic() {
        if (currentMusic >= 0 && profile.get(SettingsScreen.NO_MUSIC) == 0) {
            ctx.audio().playMusic(currentMusic);
        }
    }

    public void sfx(int id) {
        if (profile.get(SettingsScreen.NO_SFX) == 1) {
            return;
        }
        ctx.audio().playSfx(id);
    }

    /** Shakes the screen for a few ticks (big hits). */
    public void shake(int ticks) {
        if (profile.get(SettingsScreen.NO_SHAKE) == 1) {
            return;
        }
        shake = Math.max(shake, ticks);
    }

    // ----- saving -----

    public void saveRun() {
        if (run != null && !run.over()) {
            ctx.storage().write(SAVE_FILE, SaveCodec.encode(run));
        }
    }

    public void deleteRun() {
        ctx.storage().delete(SAVE_FILE);
    }

    public boolean hasSavedRun() {
        return ctx.storage().read(SAVE_FILE).map(t -> SaveCodec.decode(catalog, t) != null).orElse(false);
    }

    public Run loadRun() {
        return ctx.storage().read(SAVE_FILE).map(t -> SaveCodec.decode(catalog, t)).orElse(null);
    }

    /** Starts autosaving the run at every save point. */
    public void attach(Run newRun) {
        run = newRun;
        run.setSaver(r -> ctx.storage().write(SAVE_FILE, SaveCodec.encode(r)));
    }

    // ----- loop -----

    void update() {
        ticks++;
        in.read(ctx);
        if (shake > 0) {
            shake--;
        }
        if (fadeOut > 0) {
            fadeOut--;
            if (fadeOut == 0 && pending != null) {
                Screen next = pending;
                pending = null;
                screen = next;
                in.consume();
                next.enter(this);
                fadeIn = FADE_TICKS;
            }
            in.consume();
            return;
        }
        if (fadeIn > 0) {
            fadeIn--;
        }
        if (screen != null) {
            screen.update(this);
        }
    }

    void draw(SceneCanvas canvas) {
        if (screen != null) {
            screen.draw(this, canvas);
        }
        cardTips.draw(this, canvas);
        float dark = 0f;
        if (fadeOut > 0) {
            dark = 1f - fadeOut / (float) FADE_TICKS;
        } else if (fadeIn > 0) {
            dark = fadeIn / (float) FADE_TICKS;
        }
        if (dark > 0f) {
            canvas.fill(0, 0, width(), height(), Colors.alpha(Colors.BLACK, dark));
        }
    }

    /**
     * Explains the gold terms of {@code card} beside it this frame (see {@link CardTips}); views
     * call it for the card they show at full size, giving its position and width.
     */
    public void cardTips(Card card, int x, int y, int w) {
        cardTips.show(this, card, x, y, w);
    }

    /** Horizontal shake offset for this frame. */
    public int shakeOffset() {
        return shake == 0 ? 0 : (int) ((ticks % 4 < 2 ? 1 : -1) * Math.min(3, 1 + shake / 4));
    }
}
