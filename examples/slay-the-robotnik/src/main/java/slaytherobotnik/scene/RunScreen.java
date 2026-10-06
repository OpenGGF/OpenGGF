package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import slaytherobotnik.core.ActDef;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RunState;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.GameOverRoom;
import slaytherobotnik.run.MapRoom;
import slaytherobotnik.run.RestRoom;
import slaytherobotnik.run.RewardRoom;
import slaytherobotnik.run.Room;
import slaytherobotnik.run.Run;
import slaytherobotnik.run.ShopRoom;
import slaytherobotnik.run.StartRoom;
import slaytherobotnik.run.TreasureRoom;
import slaytherobotnik.run.VictoryRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.SmallFont;

/**
 * Everything during a run: draws the room the {@link Run} is in (map, fight, shop...) with
 * the top bar and relic bar above it, and the deck viewer or a deck choice on top.
 */
final class RunScreen implements Screen {
    static final int TOP_BAR = 14;
    static final int RELIC_BAR = 14;
    static final int HUD_HEIGHT = TOP_BAR + RELIC_BAR;

    /** One room's presentation. */
    interface RoomView {
        void update(Shell shell, RunScreen screen);

        void draw(Shell shell, RunScreen screen, SceneCanvas c);

        /** Rooms that fill the screen (the Tornado flight, game over) hide the HUD. */
        default boolean showsHud() {
            return true;
        }
    }

    CardRenderer cards;
    private Room shownRoom;
    /** Room changes dip through black: frames left of the old room fading out, and of the new one fading in. */
    private static final int ROOM_OUT_TICKS = 10;
    private static final int ROOM_IN_TICKS = 14;
    private int roomOut;
    private int roomIn;
    private RoomView view;
    private DeckChoiceView choiceView;
    private DeckViewer deckViewer;
    private MapView mapPeek;
    private String tooltipTitle;
    private String tooltipBody;
    private int tooltipX;
    private int tooltipY;
    private boolean tooltipAbove;

    @Override
    public void enter(Shell shell) {
        cards = new CardRenderer(shell);
    }

    Run run(Shell shell) {
        return shell.run;
    }

    @Override
    public void update(Shell shell) {
        Run run = shell.run;
        if (run.room() != shownRoom) {
            // The old room holds still while it fades out; the first room appears under the shell's fade.
            if (view != null && roomOut == 0) {
                roomOut = ROOM_OUT_TICKS;
            }
            if (roomOut > 0 && --roomOut > 0) {
                tooltipTitle = null;
                return;
            }
            boolean first = view == null;
            shownRoom = run.room();
            view = createView(shell, shownRoom);
            playRoomMusic(shell, shownRoom);
            shell.in.consume();
            roomIn = first ? 0 : ROOM_IN_TICKS;
        }
        if (roomIn > 0) {
            roomIn--;
        }
        tooltipTitle = null;
        if (run.deckChoice() != null) {
            if (choiceView == null || choiceView.choice() != run.deckChoice()) {
                choiceView = new DeckChoiceView(run.deckChoice());
                shell.in.consume();
            }
            choiceView.update(shell, this);
            return;
        }
        choiceView = null;
        if (deckViewer != null) {
            deckViewer.update(shell, this);
            if (deckViewer.closed()) {
                deckViewer = null;
            }
            return;
        }
        if (mapPeek != null) {
            mapPeek.update(shell, this);
            if (shell.in.back || shell.in.map || shell.in.mouse.rightPressed()) {
                mapPeek = null;
                shell.sfx(Sounds.SFX_SWITCH);
            }
            return;
        }
        if (view.showsHud() && hudInput(shell)) {
            return;
        }
        view.update(shell, this);
    }

    /** Top-bar clicks and hotkeys; returns true when the input was used. */
    private boolean hudInput(Shell shell) {
        var in = shell.in;
        boolean onMap = shownRoom instanceof MapRoom;
        int w = shell.width();
        if (in.deck || (in.mouse.leftPressed() && in.mouse.over(w - 46, 1, 20, 12))) {
            openDeck(shell, "YOUR DECK", shell.run.state().deck());
            return true;
        }
        if (!onMap && (in.map || (in.mouse.leftPressed() && in.mouse.over(w - 24, 1, 20, 12)))) {
            mapPeek = new MapView(true);
            shell.sfx(Sounds.SFX_SWITCH);
            return true;
        }
        return false;
    }

    void openDeck(Shell shell, String title, List<slaytherobotnik.core.Card> cardsToShow) {
        deckViewer = new DeckViewer(title, cardsToShow);
        shell.sfx(Sounds.SFX_SWITCH);
    }

    private RoomView createView(Shell shell, Room room) {
        return switch (room) {
            case StartRoom start -> new StartView(start);
            case MapRoom map -> new MapView(false);
            case CombatRoom combat -> new CombatView(shell, combat);
            case RewardRoom rewards -> new RewardView(rewards);
            case EventRoom event -> new EventView(event);
            case ShopRoom shop -> new ShopView(shop);
            case RestRoom rest -> new RestView(rest);
            case TreasureRoom treasure -> new TreasureView(treasure);
            case GameOverRoom over -> new EndView(shell, over.killedBy(), over.score(), false);
            case VictoryRoom win -> new EndView(shell, null, win.score(), true);
        };
    }

    private void playRoomMusic(Shell shell, Room room) {
        ActDef act = shell.run.act();
        switch (room) {
            case StartRoom s -> shell.music(Sounds.MUSIC_TORNADO);
            case CombatRoom c -> {
                switch (c.roomType()) {
                    case "Boss" -> shell.music(shell.run.state().act() >= shell.catalog.actCount()
                            ? Sounds.MUSIC_FINAL_BOSS : act.bossMusic());
                    case "Elite" -> shell.music(act.eliteMusic());
                    default -> shell.music(act.mapMusic());
                }
            }
            case ShopRoom s -> shell.music(Sounds.MUSIC_SHOP);
            case RestRoom r -> shell.music(Sounds.MUSIC_REST);
            case GameOverRoom g -> shell.music(Sounds.MUSIC_GAME_OVER);
            case VictoryRoom v -> shell.music(Sounds.MUSIC_CREDITS);
            case RewardRoom r -> { }
            default -> shell.music(act.mapMusic());
        }
    }

    /** Shows a tooltip this frame (views call this while drawing). */
    void tooltip(String title, String body, int x, int y) {
        tooltipTitle = title;
        tooltipBody = body;
        tooltipX = x;
        tooltipY = y;
        tooltipAbove = false;
    }

    /** Shows a tooltip this frame with its bottom edge just above {@code bottom}, for things low on the screen. */
    void tooltipAbove(String title, String body, int x, int bottom) {
        tooltip(title, body, x, bottom);
        tooltipAbove = true;
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        if (view == null) {
            return;
        }
        view.draw(shell, this, c);
        if (view.showsHud()) {
            drawHud(shell, c);
        }
        if (mapPeek != null) {
            mapPeek.draw(shell, this, c);
        }
        if (deckViewer != null) {
            deckViewer.draw(shell, this, c);
        }
        if (choiceView != null && shell.run.deckChoice() != null) {
            choiceView.draw(shell, this, c);
        }
        if (tooltipTitle != null) {
            drawTooltip(shell, c);
        }
        float dark = roomOut > 0 ? 1f - roomOut / (float) ROOM_OUT_TICKS
                : roomIn > 0 ? roomIn / (float) ROOM_IN_TICKS : 0f;
        if (dark > 0f) {
            c.fill(0, 0, shell.width(), shell.height(), Colors.alpha(Colors.BLACK, dark));
        }
    }

    // ------------------------------------------------------------------ HUD

    private void drawHud(Shell shell, SceneCanvas c) {
        RunState s = shell.run.state();
        int w = shell.width();
        SmallFont f = shell.font;
        Gfx.gradient(c, 0, 0, w, TOP_BAR, 0xF0182C5C, 0xF00A1430);
        c.fill(0, TOP_BAR - 1, w, 1, Colors.PANEL_EDGE_DARK);
        var mouse = shell.in.mouse;
        String hero = s.character().name().toUpperCase();
        f.drawShadowed(c, hero, 4, 5, Colors.GOLD);
        if (mouse.over(2, 1, f.width(hero) + 4, 12)) {
            tooltip(hero, s.character().blurb() + " Key stat: " + s.character().primaryStat() + ".", 4, TOP_BAR + 2);
        }
        int x = 4 + f.width(hero) + 8;
        c.draw(shell.art.icon("ui_heart"), x, 3);
        String hp = s.hp() + "/" + s.maxHp();
        boolean low = s.hp() * 3 < s.maxHp();
        f.drawShadowed(c, hp, x + 10, 5, low ? Colors.TEXT_BAD : Colors.TEXT);
        if (mouse.over(x, 1, 10 + f.width(hp), 12)) {
            tooltip("HP", "Your health, carried from room to room. The run ends if it reaches 0; rest at a "
                    + "Starpost to heal.", x, TOP_BAR + 2);
        }
        x += 10 + f.width(hp) + 10;
        HudIcons.ring(shell, c, x, 1, 12, shell.ticks);
        f.drawShadowed(c, Integer.toString(s.rings()), x + 12, 5, Colors.RING);
        if (mouse.over(x, 1, 12 + f.width(Integer.toString(s.rings())), 12)) {
            tooltip("RINGS", "Won in fights and events. Spend them in the Egg Robo's shop on cards, relics and item "
                    + "monitors.", x, TOP_BAR + 2);
        }
        // Potions.
        for (int i = 0; i < s.potionSlots(); i++) {
            int px = potionSlotX(shell, i);
            PotionDef potion = s.potionAt(i);
            HudIcons.potion(shell, c, potion, px, 1, 12);
            if (shell.in.mouse.over(px, 1, 12, 12)) {
                if (potion != null) {
                    tooltip(potion.name().toUpperCase(), potion.describe(s.potionPotency(potion)), px, TOP_BAR + 2);
                } else {
                    tooltip("EMPTY MONITOR SLOT", "Item monitors you win or buy wait here, ready to use in a fight.",
                            px, TOP_BAR + 2);
                }
            }
        }
        // Right side: act, floor, deck, map.
        ActDef act = shell.run.act();
        String where = act.zoneName().toUpperCase() + "  FLOOR " + Math.max(0, s.floor());
        f.drawShadowed(c, where, w - 52 - f.width(where), 5, Colors.TEXT);
        if (mouse.over(w - 54 - f.width(where), 1, f.width(where) + 4, 12)) {
            int acts = shell.catalog.actCount();
            tooltip(act.zoneName().toUpperCase(), "Act " + s.act() + " of " + acts + (s.act() >= acts ? ", the last" : "")
                    + ". The floor counts every room you have reached; the act's boss waits at the far end of its "
                    + "map (M).", w - 150, TOP_BAR + 2);
        }
        c.draw(shell.art.icon("ui_deck"), w - 46, 2);
        f.drawOutlined(c, Integer.toString(s.deck().size()), w - 38, 7, Colors.WHITE, 1);
        c.draw(shell.art.icon("ui_map"), w - 22, 3);
        if (shell.in.mouse.over(w - 46, 1, 20, 12)) {
            tooltip("DECK (D)", "View your deck.", w - 120, TOP_BAR + 2);
        } else if (shell.in.mouse.over(w - 24, 1, 20, 12)) {
            tooltip("MAP (M)", "View the map.", w - 120, TOP_BAR + 2);
        }
        // Relic bar.
        c.fill(0, TOP_BAR, w, RELIC_BAR, 0x90000818);
        int rx = 4;
        for (Relic relic : s.relics()) {
            HudIcons.relic(shell, c, relic, rx, TOP_BAR + 1);
            if (shell.in.mouse.over(rx, TOP_BAR + 1, 12, 12)) {
                tooltip(relic.name().toUpperCase(), relic.description(), rx, TOP_BAR + RELIC_BAR + 2);
            }
            rx += 14;
        }
    }

    /** X of potion slot {@code i} in the top bar (shared with the combat view's potion hotspots). */
    static int potionSlotX(Shell shell, int i) {
        RunState s = shell.run.state();
        SmallFont f = shell.font;
        int x = 4 + f.width(s.character().name().toUpperCase()) + 8;
        x += 10 + f.width(s.maxHp() + "/" + s.maxHp()) + 10;
        x += 12 + f.width("9999") + 12;
        return x + i * 13;
    }

    private void drawTooltip(Shell shell, SceneCanvas c) {
        SmallFont f = shell.font;
        int width = 140;
        List<String> lines = f.wrap(tooltipBody == null ? "" : tooltipBody, width - 10);
        int h = 14 + lines.size() * SmallFont.LINE;
        int x = Math.max(2, Math.min(shell.width() - width - 2, tooltipX));
        int y = Math.max(2, Math.min(shell.height() - h - 2, tooltipAbove ? tooltipY - h - 2 : tooltipY));
        Gfx.panel(c, x, y, width, h, 0xF0081028, Colors.PANEL_EDGE);
        f.drawShadowed(c, tooltipTitle, x + 5, y + 4, Colors.GOLD);
        int ly = y + 12;
        for (String line : lines) {
            f.drawMarkup(c, line, x + 5, ly, Colors.TEXT);
            ly += SmallFont.LINE;
        }
    }
}
