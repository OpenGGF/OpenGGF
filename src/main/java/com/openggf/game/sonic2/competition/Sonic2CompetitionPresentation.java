package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.menu.MenuBackgroundRenderer;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;

import static com.openggf.game.sonic2.competition.Sonic2CompetitionAssets.ResultScreen;
import static com.openggf.game.sonic2.competition.Sonic2CompetitionAssets.Text;

/** Original ROM planes, field positions, font and winner sprite for every native results screen. */
public final class Sonic2CompetitionPresentation {
    private static final int PATTERN_BASE = PatternAtlasRange.MENU_AND_DATA_SELECT.base();
    private static final int WINS_BASE = PATTERN_BASE + 0x400;
    private final Sonic2CompetitionAssets assets;
    private final GraphicsManager graphics;
    private final MenuBackgroundRenderer planeRenderer = new MenuBackgroundRenderer();
    private final PatternSpriteRenderer winnerRenderer;
    private Boolean cachedSelect;
    private int cachedBackground = -1;

    public Sonic2CompetitionPresentation(Sonic2CompetitionAssets assets, Rom rom, GraphicsManager graphics) throws IOException {
        this.assets = java.util.Objects.requireNonNull(assets);
        this.graphics = java.util.Objects.requireNonNull(graphics);
        var mappings = S2SpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom), 0x8146, 3);
        winnerRenderer = new PatternSpriteRenderer(new ObjectSpriteSheet(assets.winsPatterns(), mappings, 0, 1), graphics);
    }

    public Sonic2CompetitionAssets.TileMap compose(Sonic2CompetitionSession session) {
        return compose(assets, session);
    }
    public static Sonic2CompetitionAssets.TileMap compose(Sonic2CompetitionAssets assets, Sonic2CompetitionSession s) {
        if (s.phase() == Sonic2CompetitionSession.Phase.SELECT) {
            return assets.selectMap(s.selection(), new boolean[]{s.completed(0), s.completed(1), s.completed(2), s.completed(3)});
        }
        var screen = resultScreen(s.phase());
        var map = assets.resultMap(screen);
        if (screen == ResultScreen.ACT) {
            map = assets.withText(map, 0x1F2, zoneText(s.currentZone()), 0);
            map = assets.withCount(map, 0x216, s.act() + 1, 0);
            var p1 = s.resultMetrics(0); var p2 = s.resultMetrics(1);
            map = assets.withScore(map, 0x33E, p1.score() / 10, 0);
            map = assets.withScore(map, 0x352, p2.score() / 10, 0);
            map = withTime(assets, map, 0x3DA, p1.timerFrames());
            map = withTime(assets, map, 0x3EE, p2.timerFrames());
            int[] offsets = {0x486, 0x526, 0x5C6};
            int[] one = {p1.rings(), p1.totalRings(), p1.monitors()};
            int[] two = {p2.rings(), p2.totalRings(), p2.monitors()};
            for (int i = 0; i < offsets.length; i++) {
                map = assets.withCount(map, offsets[i], one[i], 0);
                map = assets.withCount(map, offsets[i] + 0x14, two[i], 0);
            }
            int[] labels = {0x364, 0x404, 0x4A4, 0x544, 0x5E4};
            int[] comparisons = {Integer.compare(p1.score(), p2.score()), Long.compare(p2.timerFrames(), p1.timerFrames()),
                    Integer.compare(p1.rings(), p2.rings()), Integer.compare(p1.totalRings(), p2.totalRings()),
                    Integer.compare(p1.monitors(), p2.monitors())};
            for (int i = 0; i < labels.length; i++) map = withWinner(assets, map, labels[i], comparisons[i]);
            var wins = s.actResult(s.currentZone(), s.act());
            map = assets.withCount(map, 0x706, wins.player1(), 0);
            return assets.withCount(map, 0x70E, wins.player2(), 0);
        }
        if (screen == ResultScreen.ZONE) {
            map = assets.withText(map, 0x242, zoneText(s.currentZone()), 0);
            int[] offsets = {0x398, 0x488, 0x618};
            for (int row = 0; row < 3; row++) map = withRow(assets, map, offsets[row], 8, 0x14, s.actResult(s.currentZone(), row));
        } else if (screen == ResultScreen.GAME) {
            int[] offsets = {0x208, 0x258, 0x2A8, 0x348, 0x398, 0x3E8, 0x488, 0x4D8, 0x528, 0x5C8, 0x618, 0x668};
            for (int row = 0; row < 12; row++) map = withRow(assets, map, offsets[row], 8, 0x1A, s.actResult(row / 3, row % 3));
            map = assets.withCount(map, 0x70A, s.gameWins().player1(), 0);
            map = assets.withCount(map, 0x710, s.gameWins().player2(), 0);
        } else if (screen == ResultScreen.SPECIAL_ACT) {
            map = assets.withCount(map, 0x266, s.act() + 1, 0);
            int[] offsets = {0x4D6, 0x576, 0x616};
            for (int row = 0; row < 3; row++) {
                int one = s.specialRings().get(row * 2), two = s.specialRings().get(row * 2 + 1);
                map = assets.withCount(map, offsets[row], one, 0);
                map = assets.withCount(map, offsets[row] + 0x10, two, 0);
                map = withWinner(assets, map, offsets[row] + 0x1A, Integer.compare(one, two));
            }
        } else {
            int[] offsets = {0x4D4, 0x574, 0x614};
            for (int row = 0; row < 3; row++) map = withRow(assets, map, offsets[row], 0xC, 0x1C, s.actResult(3, row));
        }
        return map;
    }
    private static Sonic2CompetitionAssets.TileMap withTime(Sonic2CompetitionAssets assets,
            Sonic2CompetitionAssets.TileMap map, int offset, long frames) {
        map = assets.withCount(map, offset, (int) (frames / 3600), 0);
        map = assets.withCount(map, offset + 6, (int) (frames / 60 % 60), 0);
        return assets.withCount(map, offset + 12, (int) ((frames % 60 * 0x1B0) >>> 8), 0);
    }
    private static Sonic2CompetitionAssets.TileMap withRow(Sonic2CompetitionAssets assets,
            Sonic2CompetitionAssets.TileMap map, int offset, int p2Offset, int winnerOffset, Sonic2CompetitionResults.Wins row) {
        if (row == null) {
            int textInset = winnerOffset == 0x14 ? 0 : 4;
            int blankInset = winnerOffset == 0x1C ? 0x14 : winnerOffset;
            map = assets.withText(map, offset + textInset, Text.NO_GAME, 0);
            return assets.withText(map, offset + blankInset, Text.BLANK, 0);
        }
        if (row.player1() >= 254 || row.player2() >= 254) {
            map = assets.withText(map, offset + (winnerOffset == 0x1A ? 4 : 0), Text.GAME_OVER, 0);
            if (winnerOffset == 0x1C) winnerOffset = 0x14;
        }
        else {
            map = assets.withCount(map, offset, row.player1(), 0);
            map = assets.withCount(map, offset + p2Offset, row.player2(), 0);
        }
        return withWinner(assets, map, offset + winnerOffset, row.winner());
    }
    private static Sonic2CompetitionAssets.TileMap withWinner(Sonic2CompetitionAssets assets,
            Sonic2CompetitionAssets.TileMap map, int offset, int comparison) {
        return assets.withText(map, offset, comparison == 0 ? Text.TIED : comparison > 0 ? Text.ONE_PLAYER : Text.TWO_PLAYER, 0x6000);
    }
    private static Text zoneText(int zone) {
        return switch (zone) { case 0 -> Text.EMERALD_HILL; case 1 -> Text.MYSTIC_CAVE;
            case 2 -> Text.CASINO_NIGHT; default -> Text.SPECIAL_STAGE; };
    }
    private static ResultScreen resultScreen(Sonic2CompetitionSession.Phase phase) {
        return switch (phase) {
            case ACT_RESULTS -> ResultScreen.ACT; case ZONE_RESULTS -> ResultScreen.ZONE;
            case GAME_RESULTS -> ResultScreen.GAME; case SPECIAL_ACT_RESULTS -> ResultScreen.SPECIAL_ACT;
            case SPECIAL_ZONE_RESULTS -> ResultScreen.SPECIAL_ZONE;
            default -> throw new IllegalStateException("No results plane for " + phase);
        };
    }

    public void draw(Sonic2CompetitionSession session, int vintCount) {
        boolean select = session.phase() == Sonic2CompetitionSession.Phase.SELECT;
        if (cachedSelect == null || cachedSelect != select) {
            var patterns = select ? assets.selectPatterns() : assets.resultsPatterns();
            for (int i = 0; i < patterns.length; i++) graphics.cachePatternTexture(patterns[i], PATTERN_BASE + i);
            cachedSelect = select; cachedBackground = -1;
        }
        var palettes = select ? assets.selectionPalettes(session.selection()) : assets.menuPalettes();
        for (int i = 0; i < palettes.length; i++) graphics.cachePaletteTexture(palettes[i], i);
        int background = backgroundSource(vintCount);
        if (background != cachedBackground) {
            var patterns = assets.backgroundPatterns();
            for (int i = 0; i < 10; i++) graphics.updatePatternTexture(patterns[background + i], PATTERN_BASE + 1 + i);
            cachedBackground = background;
        }
        int width = Math.max(320, graphics.getProjectionWidth());
        var backdrop = assets.backgroundMap();
        planeRenderer.renderTiled(graphics, ints(backdrop), 40, 28, PATTERN_BASE, 0, width);
        var map = compose(session);
        planeRenderer.render(graphics, ints(map), 40, 28, PATTERN_BASE, 0, (width - 320) / 2);
        if (!select) {
            winnerRenderer.ensurePatternsCached(graphics, WINS_BASE);
            var wins = switch (session.phase()) {
                case ACT_RESULTS, SPECIAL_ACT_RESULTS -> session.actResult(session.currentZone(), session.act());
                case GAME_RESULTS -> session.gameWins();
                default -> session.zoneWins(session.currentZone());
            };
            int frame = wins.winner() == 0 ? 2 : wins.winner() > 0 ? 0 : 1;
            int x = session.phase() == Sonic2CompetitionSession.Phase.GAME_RESULTS ? 96 : 112;
            int y = session.phase() == Sonic2CompetitionSession.Phase.ZONE_RESULTS ? 176 : 200;
            winnerRenderer.drawFrameIndex(frame, (width - 320) / 2 + x, y, false, false, (vintCount & 8) == 0 ? 0 : 1);
        }
    }
    static int backgroundSource(int count) {
        int t = Math.floorMod(count, 424);
        int[] durations = {200, 6, 6, 200, 6, 6};
        int[] sources = {0, 10, 20, 30, 20, 10};
        for (int i = 0; i < durations.length; i++) { if (t < durations[i]) return sources[i]; t -= durations[i]; }
        throw new AssertionError();
    }
    private static int[] ints(Sonic2CompetitionAssets.TileMap map) { return map.words().stream().mapToInt(Integer::intValue).toArray(); }
    public void invalidate() { cachedSelect = null; cachedBackground = -1; }
}
