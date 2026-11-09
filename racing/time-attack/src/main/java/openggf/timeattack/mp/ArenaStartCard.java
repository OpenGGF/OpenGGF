package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;
import openggf.timeattack.ui.LapTimeFormat;
import openggf.timeattack.ui.MenuPage;

import java.util.List;
import java.util.Objects;

/**
 * The page a Competition arena shows while its mass start holds the native session: the track,
 * its laps, the field, the live time to the start and the racers. The held arena has not run a
 * frame, so the page covers the whole screen; in the last {@link #FADE_MILLIS} before release it
 * fades to black, and the arena's own fade from black then brings in the native 3-2-1-GO.
 */
public final class ArenaStartCard {
    /**
     * The fade to black before release mirrors the native fade in after it: Competition loc_665A
     * (sonic3k.asm:8012-8016) sets Palette_fade_timer to $16, one Pal_AddColor step per frame.
     */
    static final long FADE_MILLIS = 0x16 * 1000L / 60;
    private static final int BACKGROUND = 0xFF030A26;
    private static final int CHECKER = 0x143A5BA8;
    private static final int BODY = 0xB805123B;
    private static final int COLUMN_W = 100;
    private static final int GRID_TOP = 122;

    /** One racer: their name, its colour, and whether they are this player. */
    public record Racer(String name, int argb, boolean local) {
        public Racer {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * The page's facts. {@code toStartMillis} counts to the host's GO; {@code toReleaseMillis} to
     * the arena's release, four native seconds earlier. {@code quickChatKey} is empty when the
     * room has no quick chat.
     */
    public record Card(String track, int laps, int fieldSize, long toStartMillis, long toReleaseMillis,
                       List<Racer> racers, String quickChatKey) {
        public Card {
            Objects.requireNonNull(track, "track");
            racers = List.copyOf(racers);
            Objects.requireNonNull(quickChatKey, "quickChatKey");
        }
    }

    private ArenaStartCard() { }

    /** The black over the page, 0 (none) to 255 (opaque), as release approaches. */
    static int fadeAlpha(long toReleaseMillis) {
        if (toReleaseMillis >= FADE_MILLIS) return 0;
        if (toReleaseMillis <= 0) return 255;
        return (int) (255 - toReleaseMillis * 255 / FADE_MILLIS);
    }

    /** The meter's filled width: what remains of the second the countdown is showing. */
    static int secondMeter(long toStartMillis, int width) {
        if (toStartMillis <= 0) return 0;
        long remainder = (toStartMillis - 1) % 1000 + 1;
        return (int) (remainder * width / 1000);
    }

    /** How many racers the grid shows by name at {@code width}; the rest are counted. */
    static int visibleNames(int width, int height, int racers) {
        int columns = Math.max(1, (width - 2 * MenuPage.MARGIN) / COLUMN_W);
        int rows = Math.max(1, (Math.min(height, 224) - 21 - GRID_TOP) / MenuPage.ROW_H);
        int cells = columns * rows;
        return racers <= cells ? racers : cells - 1;
    }

    public static void draw(PixelCanvas canvas, Card card) {
        int width = canvas.width();
        canvas.fill(0, 0, width, canvas.height(), BACKGROUND);
        for (int y = 0; y < 224; y += 32) {
            for (int x = ((y / 32) % 2) * 32; x < width; x += 64) {
                canvas.fill(x, y, Math.min(32, width - x), Math.min(32, 224 - y), CHECKER);
            }
        }
        canvas.fill(0, MenuPage.HEADER_H, width, MenuPage.FOOTER_Y - MenuPage.HEADER_H, BODY);
        header(canvas, card);

        String caption = "RACE STARTS IN";
        CompactFont.draw(canvas, caption, (width - CompactFont.width(caption, 1)) / 2, 40, 1, MenuPage.MUTED);
        String clock = LapTimeFormat.countdown(card.toStartMillis());
        int clockWidth = CompactFont.width(clock, 4);
        CompactFont.shadowed(canvas, clock, (width - clockWidth) / 2, 54, 4, MenuPage.GOLD, 0xC0000000);
        // The second in progress drains under the digits, so the page moves on every frame.
        canvas.fill((width - clockWidth) / 2, 88, clockWidth, 2, MenuPage.FAINT);
        canvas.fill((width - clockWidth) / 2, 88, secondMeter(card.toStartMillis(), clockWidth), 2, MenuPage.GOLD);

        List<String> chips = List.of(card.laps() + " LAPS", card.fieldSize() + " RACERS");
        int chipsWidth = chips.stream().mapToInt(MenuPage::tagWidth).sum() + 6 * (chips.size() - 1);
        int x = (width - chipsWidth) / 2;
        for (String chip : chips) x += MenuPage.tag(canvas, x, 96, chip, MenuPage.CYAN) + 6;

        MenuPage.section(canvas, "Racers", MenuPage.MARGIN, GRID_TOP - 10, width - 2 * MenuPage.MARGIN);
        racers(canvas, card.racers(), width);

        List<MenuPage.Hint> hints = card.quickChatKey().isEmpty()
                ? List.of(MenuPage.hint("ESC", "Menu"))
                : List.of(MenuPage.hint(card.quickChatKey(), "Quick chat"), MenuPage.hint("ESC", "Menu"));
        MenuPage.footer(canvas, "Get ready", MenuPage.GREEN, hints);

        int alpha = fadeAlpha(card.toReleaseMillis());
        if (alpha > 0) canvas.fill(0, 0, width, canvas.height(), alpha << 24);
    }

    private static void header(PixelCanvas canvas, Card card) {
        int width = canvas.width();
        canvas.fill(0, 0, width, MenuPage.HEADER_H, MenuPage.HEAD);
        canvas.fill(0, MenuPage.HEADER_H - 2, width, 1, MenuPage.GOLD);
        canvas.fill(0, MenuPage.HEADER_H - 1, width, 1, 0xFF664405);
        String scope = "COMPETITION";
        int scopeX = width - MenuPage.MARGIN - MenuPage.tagWidth(scope);
        MenuPage.tag(canvas, scopeX, 9, scope, MenuPage.CYAN);
        String title = CompactFont.fit(card.track(), scopeX - 2 * MenuPage.MARGIN, 2);
        CompactFont.shadowed(canvas, title, MenuPage.MARGIN + 1, 7, 2, MenuPage.GOLD, 0xC0000000);
    }

    private static void racers(PixelCanvas canvas, List<Racer> racers, int width) {
        int columns = Math.max(1, (width - 2 * MenuPage.MARGIN) / COLUMN_W);
        int columnWidth = (width - 2 * MenuPage.MARGIN) / columns;
        int shown = visibleNames(width, canvas.height(), racers.size());
        for (int index = 0; index < shown; index++) {
            Racer racer = racers.get(index);
            int cx = MenuPage.MARGIN + (index % columns) * columnWidth;
            int cy = GRID_TOP + (index / columns) * MenuPage.ROW_H;
            NameFont.draw(canvas, NameFont.fit(racer.name(), columnWidth - 6), cx, cy,
                    racer.local() ? MenuPage.GOLD : racer.argb());
        }
        if (shown < racers.size()) {
            int cx = MenuPage.MARGIN + (shown % columns) * columnWidth;
            int cy = GRID_TOP + (shown / columns) * MenuPage.ROW_H;
            NameFont.draw(canvas, "and " + (racers.size() - shown) + " more", cx, cy, MenuPage.DIM);
        }
    }
}
