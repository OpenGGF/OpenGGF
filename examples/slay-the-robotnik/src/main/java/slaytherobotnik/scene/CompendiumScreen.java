package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/**
 * Every card, relic and item monitor in the game, browsable from the title screen (Slay the
 * Spire's Compendium). Cards can be shown upgraded.
 */
final class CompendiumScreen implements Screen {
    private final String[] TABS = {"SONIC", "TAILS", "KNUCKLES", "COLORLESS", "RELICS", "MONITORS"};
    private final String[] CARD_COLORS = {CardColor.BLUE, CardColor.ORANGE, CardColor.RED, CardColor.COLORLESS};
    private final String[] RARITY_ORDER = {CardRarity.BASIC, CardRarity.COMMON, CardRarity.UNCOMMON,
            CardRarity.RARE, CardRarity.SPECIAL, CardRarity.CURSE};
    private final String[] TIER_ORDER = {RelicTier.STARTER, RelicTier.COMMON, RelicTier.UNCOMMON,
            RelicTier.RARE, RelicTier.BOSS, RelicTier.SHOP, RelicTier.EVENT};
    private static final int ICON_COLUMNS = 10;
    private static final int ICON_CELL = 26;
    private static final int GRID_TOP = 44;

    private final Hotspots spots = new Hotspots();
    private CardRenderer renderer;
    private int tab;
    private boolean upgraded;
    private CardGrid grid;
    private List<Card> cards = List.of();
    private List<Relic> relics = List.of();
    private List<PotionDef> potions = List.of();
    private int iconScroll;

    /** Opens on a tab (0-5), for screenshots and debugging. */
    CompendiumScreen(int tab) {
        this.tab = tab;
    }

    CompendiumScreen() {
        this(0);
    }

    @Override
    public void enter(Shell shell) {
        renderer = new CardRenderer(shell);
        Catalog catalog = shell.catalog;
        List<Relic> allRelics = new ArrayList<>();
        for (Catalog.RelicEntry entry : catalog.allRelics()) {
            allRelics.add(catalog.newRelic(entry.id()));
        }
        allRelics.sort(Comparator.comparingInt((Relic r) -> indexOf(TIER_ORDER, r.tier())).thenComparing(Relic::name));
        relics = allRelics;
        List<PotionDef> allPotions = new ArrayList<>(catalog.allPotions());
        allPotions.sort(Comparator.comparingInt((PotionDef p) -> indexOf(RARITY_ORDER, p.rarity()))
                .thenComparing(PotionDef::name));
        potions = allPotions;
        selectTab(shell, tab);
    }

    private void selectTab(Shell shell, int index) {
        tab = index;
        iconScroll = 0;
        if (tab < CARD_COLORS.length) {
            List<CardDef> defs = new ArrayList<>();
            for (CardDef def : shell.catalog.allCards()) {
                boolean colorless = tab == 3 && (def.color().equals(CardColor.CURSE));
                if (def.color().equals(CARD_COLORS[tab]) || colorless) {
                    defs.add(def);
                }
            }
            defs.sort(Comparator.comparingInt((CardDef d) -> indexOf(RARITY_ORDER, d.rarity()))
                    .thenComparing(CardDef::type).thenComparing(CardDef::name));
            List<Card> list = new ArrayList<>();
            for (CardDef def : defs) {
                Card card = new Card(def);
                if (upgraded && card.canUpgrade()) {
                    card.upgrade();
                }
                list.add(card);
            }
            cards = list;
            grid = new CardGrid(cards, GRID_TOP, 2, 5);
        }
    }

    private int indexOf(String[] order, String value) {
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(value)) {
                return i;
            }
        }
        return order.length;
    }

    private int iconCount() {
        return tab == 4 ? relics.size() : tab == 5 ? potions.size() : 0;
    }

    private void layout(Shell shell) {
        spots.clear();
        int x = 12;
        for (int i = 0; i < TABS.length; i++) {
            int w = shell.font.width(TABS[i]) + 10;
            spots.add("tab" + i, x, 26, w, 13);
            x += w + 3;
        }
        if (tab < CARD_COLORS.length) {
            grid.layout(spots, shell);
            spots.add("upgrades", 12, shell.height() - 20, 100, 14);
        } else {
            int rows = 5;
            for (int i = 0; i < iconCount(); i++) {
                int row = i / ICON_COLUMNS - iconScroll;
                if (row >= 0 && row < rows) {
                    spots.add("icon" + i, 12 + (i % ICON_COLUMNS) * ICON_CELL, GRID_TOP + row * ICON_CELL, 22, 22);
                }
            }
        }
        spots.add("back", shell.width() - 70, shell.height() - 20, 60, 14);
        if (spots.focused() == null) {
            spots.focus(tab < CARD_COLORS.length ? "card0" : "icon0");
        }
    }

    @Override
    public void update(Shell shell) {
        layout(shell);
        if (tab < CARD_COLORS.length && grid.scrollForMove(shell, spots)) {
            layout(shell);
        }
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (before != null && !before.equals(spots.focused())) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (tab < CARD_COLORS.length) {
            grid.wheel(shell);
        } else {
            scrollIcons(shell);
        }
        if (shell.in.back || shell.in.mouse.rightPressed()) {
            back(shell);
            return;
        }
        if (picked == null) {
            return;
        }
        if (picked.startsWith("tab")) {
            shell.sfx(Sounds.SFX_SWITCH);
            selectTab(shell, Integer.parseInt(picked.substring(3)));
            spots.focus(picked);
        } else if (picked.equals("upgrades")) {
            shell.sfx(Sounds.SFX_STARPOST);
            upgraded = !upgraded;
            selectTab(shell, tab);
        } else if (picked.equals("back")) {
            back(shell);
        }
    }

    private void back(Shell shell) {
        shell.sfx(Sounds.SFX_SWITCH);
        shell.go(new TitleScreen());
    }

    private void scrollIcons(Shell shell) {
        int maxRow = Math.max(0, (iconCount() + ICON_COLUMNS - 1) / ICON_COLUMNS - 5);
        if (shell.in.mouse.wheel() != 0) {
            iconScroll = Math.max(0, Math.min(maxRow, iconScroll - shell.in.mouse.wheel()));
        }
        String f = spots.focused();
        if (f != null && f.startsWith("icon") && (shell.in.up || shell.in.down)) {
            int row = Integer.parseInt(f.substring(4)) / ICON_COLUMNS;
            if (shell.in.down && row - iconScroll >= 4 && iconScroll < maxRow) {
                iconScroll++;
            } else if (shell.in.up && row - iconScroll <= 0 && iconScroll > 0) {
                iconScroll--;
            }
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        Backdrops.menu(c, shell.width(), shell.height(), shell.ticks);
        SmallFont f = shell.font;
        f.drawOutlined(c, "COMPENDIUM", 12, 8, Colors.GOLD, 2);
        layout(shell);
        for (int i = 0; i < TABS.length; i++) {
            Hotspots.Spot s = spots.spot("tab" + i);
            Gfx.button(c, f, TABS[i], s.x(), s.y(), s.w(), s.h(), spots.isFocused(s.id()) || tab == i, true, shell.ticks);
        }
        if (tab < CARD_COLORS.length) {
            grid.draw(shell, renderer, c, spots, null);
            Card focus = grid.focusedCard(spots);
            if (focus != null) {
                renderer.drawBig(c, focus, null, null, shell.width() - CardRenderer.BIG_W - 14, GRID_TOP + 2, false);
                shell.cardTips(focus, shell.width() - CardRenderer.BIG_W - 14, GRID_TOP + 2, CardRenderer.BIG_W);
                String rarity = focus.rarity().toUpperCase();
                f.drawShadowed(c, rarity, shell.width() - CardRenderer.BIG_W / 2 - 14 - f.width(rarity) / 2,
                        GRID_TOP + CardRenderer.BIG_H + 6, Colors.TEXT_DIM);
            }
            Hotspots.Spot u = spots.spot("upgrades");
            Gfx.button(c, f, upgraded ? "UPGRADES: ON" : "UPGRADES: OFF", u.x(), u.y(), u.w(), u.h(),
                    spots.isFocused("upgrades"), true, shell.ticks);
            String count = cards.size() + " CARDS";
            f.drawShadowed(c, count, 120, shell.height() - 16, Colors.TEXT_DIM);
        } else {
            drawIcons(shell, c, f);
        }
        Hotspots.Spot b = spots.spot("back");
        Gfx.button(c, f, "BACK", b.x(), b.y(), b.w(), b.h(), spots.isFocused("back"), true, shell.ticks);
    }

    private void drawIcons(Shell shell, SceneCanvas c, SmallFont f) {
        int focus = -1;
        for (int i = 0; i < iconCount(); i++) {
            Hotspots.Spot s = spots.spot("icon" + i);
            if (s == null) {
                continue;
            }
            Gfx.panel(c, s.x(), s.y(), s.w(), s.h());
            if (tab == 4) {
                HudIcons.relic(shell, c, relics.get(i), s.x() + 1, s.y() + 1, 20);
            } else {
                HudIcons.potion(shell, c, potions.get(i), s.x() + 5, s.y() + 4);
            }
            if (spots.isFocused(s.id())) {
                Gfx.focusFrame(c, s.x(), s.y(), s.w(), s.h(), shell.ticks);
                focus = i;
            }
        }
        if (focus < 0) {
            return;
        }
        int px = 12 + ICON_COLUMNS * ICON_CELL + 8;
        int pw = shell.width() - px - 10;
        Gfx.panel(c, px, GRID_TOP, pw, 128);
        String name;
        String sub;
        String body;
        String flavor = null;
        if (tab == 4) {
            Relic r = relics.get(focus);
            name = r.name();
            sub = r.tier() + " relic" + (r.character() != null ? " (" + r.character() + ")" : "");
            body = r.description();
            flavor = r.flavor();
        } else {
            PotionDef p = potions.get(focus);
            name = p.name();
            sub = p.rarity() + " monitor";
            body = p.describe(p.potency());
        }
        int y = GRID_TOP + 6;
        for (String line : f.wrap(name.toUpperCase(), pw - 12)) {
            f.drawShadowed(c, line, px + 6, y, Colors.GOLD);
            y += SmallFont.LINE;
        }
        f.drawShadowed(c, sub.toUpperCase(), px + 6, y, Colors.TEXT_DIM);
        y += SmallFont.LINE + 4;
        for (String line : f.wrap(body, pw - 12)) {
            f.drawMarkup(c, line, px + 6, y, Colors.TEXT);
            y += SmallFont.LINE;
        }
        if (flavor != null && !flavor.isEmpty()) {
            y += 4;
            for (String line : f.wrap(flavor, pw - 12)) {
                f.drawShadowed(c, line, px + 6, y, Colors.TEXT_DIM);
                y += SmallFont.LINE;
            }
        }
    }
}
