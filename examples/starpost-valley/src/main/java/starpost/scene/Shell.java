package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.state.ModSaveCodec;
import com.openggf.mods.state.ModSaveLoadResult;
import com.openggf.mods.state.ModSaveSlot;
import com.openggf.mods.state.ModSaveWriteResult;
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
    final Settings settings;
    public Game game;
    private final ModSaveSlot<String> farmSlot;
    private ModSaveLoadResult.Status farmStatus;
    private String pendingCheckpoint;
    private String farmNotice;
    private long farmNoticeAt;
    java.util.function.Consumer<PlayScreen> townAct;
    java.util.function.BiConsumer<PlayScreen,Integer> ruinsAct;
    public boolean startRuinsAct(PlayScreen play,int number) {
        if(ruinsAct==null) throw new IllegalStateException("Real Ruins act is unavailable");
        ruinsAct.accept(play,number); return true;
    }

    /** Farm fold integration; the scene-only valley remains available through debug. */
    boolean startTownAct(PlayScreen play) {
        if (townAct == null) throw new IllegalStateException("Real town act is unavailable");
        townAct.accept(play);
        return true;
    }
    public record ActivityLaunch(PlayScreen play,String kind,int seconds,
            java.util.function.Consumer<com.openggf.mods.scene.ActResult> done) {}
    java.util.function.Consumer<ActivityLaunch> activityAct;
    public void startActivity(PlayScreen play,String kind,int seconds,
            java.util.function.Consumer<com.openggf.mods.scene.ActResult> done) {
        if(activityAct==null) throw new IllegalStateException("Real activity act is unavailable");
        activityAct.accept(new ActivityLaunch(play,kind,seconds,done));
    }
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
        this.settings = Settings.load(ctx.storage());
        this.farmSlot = new ModSaveSlot<>(ctx.storage(), SaveCodec.FILE, new ModSaveCodec<>() {
            public String encode(String text) { return text; }
            public ModSaveLoadResult<String> decode(String text) {
                for (String line : text.split("\n")) {
                    int equals = line.indexOf('=');
                    if (equals < 0) continue;
                    String key = line.substring(0, equals).trim();
                    if (key.equals("version") || key.startsWith("s.") && key.endsWith(".version")) {
                        try {
                            if (Integer.parseInt(line.substring(equals + 1).trim()) != SaveCodec.VERSION)
                                return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.INCOMPATIBLE);
                        } catch (NumberFormatException invalid) {
                            return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                        }
                    }
                }
                return SaveCodec.decode(catalog, text, Systems.sections(Shell.this)) == null
                        ? ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT)
                        : ModSaveLoadResult.valid(text);
            }
        });
        farmStatus = farmSlot.load().status();
        if (!farmSlot.writable()) farmNotice = "FARM PRESERVED - NEW GAME TO REPLACE";
        apply();
    }

    /** Applies the options to the music and the current game. */
    void apply() {
        music.setEnabled(settings.music);
        if (game != null) {
            game.calendar.setDayMinutes(settings.dayMinutes);
            game.stamina = settings.stamina;
        }
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

    /** Closes every overlay (debug captures; the morning post, menus). */
    public void closeOverlays() {
        overlays.clear();
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

    /** The act exit uses the same day-end screen as scene free play. */
    public void endActDay(boolean fainted) { go(new DayEndScreen(fainted)); }

    /** Captures one accepted bedtime; failed publication retains these exact immutable bytes. */
    public boolean save() {
        if (game == null) return true;
        String checkpoint = SaveCodec.encode(game);
        ModSaveWriteResult result = farmSlot.save(checkpoint);
        if (farmSlot.pending() && result != ModSaveWriteResult.REPLACEMENT_REQUIRED
                && result != ModSaveWriteResult.TOO_LARGE) pendingCheckpoint = checkpoint;
        return farmOutcome(result);
    }

    private boolean farmOutcome(ModSaveWriteResult result) {
        farmNoticeAt = ticks;
        if (result.saved()) {
            farmStatus = ModSaveLoadResult.Status.VALID;
            pendingCheckpoint = null;
            farmNotice = "FARM SAVED";
        } else {
            farmNotice = switch (result) {
                case REPLACEMENT_REQUIRED -> "FARM PRESERVED - NEW GAME TO REPLACE";
                case TOO_LARGE -> "FARM TOO LARGE - SAVE NOT CONFIRMED";
                default -> "FARM SAVE PENDING - RETRYING";
            };
        }
        return result.saved();
    }

    /** Owner resume/exit flush; never repeats sleep, encodes mutable daytime state or advances RNG. */
    public boolean flushPendingSaves() {
        boolean farmSaved = !farmSlot.pending() || farmOutcome(farmSlot.retry());
        return settings.flush() && farmSaved;
    }

    public boolean hasStoredFarm() {
        return farmStatus != ModSaveLoadResult.Status.ABSENT || farmSlot.pending();
    }

    public boolean farmSavePending() { return farmSlot.pending(); }

    public Game load() {
        String text;
        if (farmSlot.pending() && pendingCheckpoint != null) {
            // Continue a refused bedtime from memory rather than rolling back to its old primary.
            text = pendingCheckpoint;
        } else {
            var result = farmSlot.load();
            farmStatus = result.status();
            if (!farmSlot.writable()) farmNotice = "FARM PRESERVED - NEW GAME TO REPLACE";
            text = result.value().orElse(null);
        }
        Game loaded = text == null ? null : SaveCodec.decode(catalog, text, Systems.sections(this));
        if (loaded != null) {
            loaded.calendar.setDayMinutes(settings.dayMinutes);
            loaded.stamina = settings.stamina;
        }
        return loaded;
    }

    /** Called only by the player's final New Game/farmer confirmation, never by a model factory. */
    void beginNewGame(long seed, String farmer) {
        Game fresh = newGame(seed, farmer);
        farmSlot.allowReplacement();
        pendingCheckpoint = null;
        farmNotice = null;
        game = fresh;
    }

    /** A brand-new game with every system installed. */
    public Game newGame(long seed, String farmer) {
        Game fresh = Game.fresh(catalog, seed, farmer);
        fresh.sections.addAll(Systems.sections(this));
        fresh.calendar.setDayMinutes(settings.dayMinutes);
        fresh.stamina = settings.stamina;
        return fresh;
    }

    void update() {
        ticks++;
        if (ticks % 60 == 0) flushPendingSaves();
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
        if (ticks - toastAt < 120 && !toast.isEmpty() && !(overlays.peek() instanceof PromoCard)) {
            // Over a menu the toast takes the top line (the HUD's), clear of the menu's own rows.
            int w = canvas.textWidth(toast) + 16, y = overlays.isEmpty() ? 36 : 1;
            Text.panel(canvas, (width() - w) / 2, y, w, 18);
            Text.centred(canvas, toast, y + 5, Text.WHITE);
        }
        if (farmNotice != null && (!farmSlot.writable() || farmSlot.pending() || ticks - farmNoticeAt < 120)) {
            Text.centred(canvas, Text.fit(canvas, farmNotice, width() - 20), height() - 24, Text.YELLOW);
        }
        String optionsNotice = settings.notice(ticks);
        if (!optionsNotice.isEmpty()) {
            Text.centred(canvas, Text.fit(canvas, optionsNotice, width() - 20), height() - 12, Text.YELLOW);
        }
        int fade = fadeOut > 0 ? (FADE_TICKS - fadeOut) * 255 / FADE_TICKS : fadeIn * 255 / FADE_TICKS;
        if (fade > 0) {
            canvas.fill(0, 0, width(), height(), Math.min(255, fade) << 24);
        }
    }
}
