package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayDeque;
import java.util.Deque;
import starpost.art.Art;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveCodec;
import starpost.ui.Controls;
import starpost.ui.Text;

/**
 * Shared state and services for every screen: the scene context, input, ROM art, the catalogue,
 * the current game, music, saving, and fades between screens. Overlays (menus, dialogue) stack
 * over the current screen; only the top one is updated.
 *
 * <p>Fields are public on purpose: everything is single-threaded and every screen reads the same
 * tick's state from here.
 */
public final class Shell {
    private static final int FADE_TICKS = 16;

    public final SceneContext ctx;
    public final Controls in = new Controls();
    public final Art art;
    public final Catalog catalog;
    public final Music music;
    public Game game;
    public long ticks;
    /** When the hotbar selection last changed (its label shows for two seconds). */
    public long hotbarChangedAt = -1000;

    private Screen screen;
    private Screen pending;
    private final Deque<Screen> overlays = new ArrayDeque<>();
    private int fadeOut;
    private int fadeIn;
    private String toast = "";
    private long toastAt = -1000;

    Shell(SceneContext ctx, Art art, Catalog catalog) {
        this.ctx = ctx;
        this.art = art;
        this.catalog = catalog;
        this.music = new Music(ctx);
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

    /** Fades to black, switches to {@code next} (closing any overlays), and fades back in. */
    public void go(Screen next) {
        if (pending == null) {
            pending = next;
            fadeOut = FADE_TICKS;
        }
    }

    public void goNow(Screen next) {
        overlays.clear();
        screen = next;
        pending = null;
        fadeOut = 0;
        in.consume();
        next.enter(this);
    }

    public void push(Screen overlay) {
        overlays.push(overlay);
        in.consume();
        overlay.enter(this);
    }

    public void pop() {
        overlays.poll();
        in.consume();
    }

    public boolean hasOverlay() {
        return !overlays.isEmpty();
    }

    public boolean transitioning() {
        return pending != null;
    }

    public void sfx(int id) {
        ctx.audio().playSfx(id);
    }

    public void toast(String text) {
        toast = text;
        toastAt = ticks;
    }

    public void save() {
        if (game != null) {
            ctx.storage().write(SaveCodec.FILE, SaveCodec.encode(game));
        }
    }

    public Game load() {
        return ctx.storage().read(SaveCodec.FILE).map(t -> SaveCodec.decode(catalog, t, Systems.sections(this))).orElse(null);
    }

    /** A brand-new game with every system installed. */
    public Game newGame(long seed, String farmer) {
        Game fresh = Game.fresh(catalog, seed, farmer);
        fresh.sections.addAll(Systems.sections(this));
        return fresh;
    }

    void update() {
        ticks++;
        in.read(ctx);
        music.update();
        if (fadeOut > 0) {
            if (--fadeOut == 0) {
                Screen next = pending;
                goNow(next);
                fadeIn = FADE_TICKS;
            }
            return;
        }
        if (fadeIn > 0) {
            fadeIn--;
        }
        Screen top = overlays.peek();
        if (top != null) {
            top.update(this);
        } else if (screen != null) {
            screen.update(this);
        }
    }

    void draw(SceneCanvas canvas) {
        if (screen != null) {
            screen.draw(this, canvas);
        }
        // Overlays draw bottom-up over the screen.
        Object[] stack = overlays.toArray();
        for (int i = stack.length - 1; i >= 0; i--) {
            ((Screen) stack[i]).draw(this, canvas);
        }
        if (ticks - toastAt < 120 && !toast.isEmpty()) {
            int w = canvas.textWidth(toast) + 16;
            Text.panel(canvas, (width() - w) / 2, 36, w, 18);
            Text.centred(canvas, toast, 41, Text.WHITE);
        }
        int fade = fadeOut > 0 ? (FADE_TICKS - fadeOut) * 255 / FADE_TICKS : fadeIn * 255 / FADE_TICKS;
        if (fade > 0) {
            canvas.fill(0, 0, width(), height(), Math.min(255, fade) << 24);
        }
    }
}
