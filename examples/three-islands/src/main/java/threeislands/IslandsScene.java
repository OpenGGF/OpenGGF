package threeislands;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import threeislands.core.EnemyKind;
import threeislands.core.Hero;
import threeislands.core.HeroId;
import threeislands.core.Island;
import threeislands.core.Progress;
import threeislands.core.Zone;
import threeislands.field.Field;
import threeislands.field.Stage;
import threeislands.screen.BattleScreen;
import threeislands.screen.EndingScreen;
import threeislands.screen.FieldScreen;
import threeislands.screen.GameOverScreen;
import threeislands.screen.MenuScreen;
import threeislands.screen.Screen;
import threeislands.screen.TitleScreen;
import threeislands.screen.ShopScreen;

/**
 * The startup scene. All game logic lives in {@link Game} and its screens; this class adapts
 * them to the engine's scene lifecycle and offers debug jumps for tests and screenshots:
 *
 * <ul>
 *   <li>{@code title}, {@code new}, {@code ending}, {@code gameover}</li>
 *   <li>{@code map:<island>} (south, west, angel, death_egg), {@code village}, {@code shop}, {@code menu}</li>
 *   <li>{@code field:<zone>[:<fraction of the route>]} with zone keys ghz slz syz ehz cpz mcz aiz hcz lbz dez</li>
 *   <li>{@code battle:<zone>[:KIND,KIND,...]}, {@code boss:<zone>}, {@code win}</li>
 *   <li>{@code story:<scene>}, {@code level:<n>}, {@code emeralds}</li>
 *   <li>{@code audiostate}: writes {@code audio.txt} (what music is wanted, playing or being prepared)</li>
 *   <li>{@code routestats}: writes {@code routes.txt} to the mod's storage with each act's kit size,
 *       route span, airborne columns and floor range, for judging how well the route finder
 *       follows a level (it found the Marble Zone camera-boundary problem)</li>
 * </ul>
 *
 * Jumps that need a zone set the party up as if the story had reached it.
 */
public final class IslandsScene implements ModScene, DebuggableScene {
    public static final int WIDTH = 400;

    private final byte[] font;
    private final byte[] story;
    private Game game;

    public IslandsScene(byte[] font, byte[] story) {
        this.font = font;
        this.story = story;
    }

    @Override
    public void enter(SceneContext ctx) {
        game = new Game(ctx, font, story);
    }

    @Override
    public void update(SceneContext ctx) {
        game.update();
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        game.draw(canvas);
    }

    @Override
    public void exit(SceneContext ctx) {
        if (game != null) game.audio.close();
    }

    /** The current screen's name, for tests. */
    public String screen() {
        return game.screen().name();
    }

    public Game game() {
        return game;
    }

    @Override
    public boolean debugJump(String command) {
        String[] parts = command.trim().split(":");
        String verb = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (verb) {
                case "title" -> game.swap(new TitleScreen(game));
                case "new" -> game.newGame();
                case "ending" -> game.swap(new EndingScreen(game));
                case "gameover" -> game.swap(new GameOverScreen(game));
                case "shop", "menu" -> {
                    FieldScreen field = game.screen() instanceof FieldScreen f ? f : field(Zone.GREEN_HILL, 0);
                    if (field == null) return false;
                    game.swap(verb.equals("shop") ? new ShopScreen(game, field) : new MenuScreen(game, field, false));
                }
                case "field" -> {
                    Zone zone = zone(parts[1]);
                    reach(zone);
                    double fraction = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
                    FieldScreen field = field(zone, fraction);
                    if (field == null) return false;
                    game.swap(field);
                }
                case "battle", "boss" -> {
                    Zone zone = zone(parts[1]);
                    reach(zone);
                    List<EnemyKind> group = new ArrayList<>();
                    if (verb.equals("boss")) {
                        List<EnemyKind> bosses = zone.bossKinds();
                        group.add(bosses.get(bosses.size() - 1));
                    } else if (parts.length > 2) {
                        for (String name : parts[2].split(",")) group.add(EnemyKind.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                    } else {
                        group.addAll(zone.enemyKinds().subList(0, Math.min(3, zone.enemyKinds().size())));
                    }
                    double fraction = verb.equals("boss") ? 0.97 : 0.25;
                    FieldScreen field = field(zone, fraction);
                    if (field == null) return false;
                    Field.Spot spot = verb.equals("boss") ? field.field().boss() : field.field().spots.stream().filter(s -> s.kind == Field.Kind.ENCOUNTER).findFirst().orElseThrow();
                    game.battle(field, spot, group);
                }
                case "win" -> {
                    if (!(game.screen() instanceof BattleScreen battle)) return false;
                    battle.forceVictory(game);
                }
                case "story" -> {
                    Screen under = game.screen();
                    game.replayStory(parts[1], under, () -> game.swap(under));
                }
                case "level" -> {
                    int level = Integer.parseInt(parts[1]);
                    for (Hero hero : game.progress.party()) hero.setLevel(level);
                }
                case "audiostate" -> game.ctx.storage().write("audio.txt", game.audio.describe());
                case "routestats" -> {
                    StringBuilder out = new StringBuilder();
                    for (Zone zone : Zone.values()) out.append(stats(zone.game, zone.zone, zone.startX, zone.startY, zone.key));
                    game.ctx.storage().write("routes.txt", out.toString());
                }
                case "emeralds" -> {
                    for (int i = 0; i < 7; i++) game.progress.addEmerald(i);
                    game.progress.addRings(500);
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException bad) {
            return false;
        }
    }

    /** One line of {@code routestats}: an act's kit and how its route went. */
    private String stats(String g, int z, int sx, int sy, String key) {
        var kit = game.art.kit(g, z, 0);
        if (kit == null) return key + " no kit\n";
        int[] area = kit.playableArea();
        int cells = 0;
        for (int r = 0; r < kit.rows(); r++) for (int col = 0; col < kit.columns(); col++) if (kit.block(col, r) != 0) cells++;
        var path = threeislands.field.FieldPath.build(new threeislands.field.KitTerrain(kit), sx,
                area[0] + area[2] - 220, sy);
        int air = 0, minY = Integer.MAX_VALUE, maxY = 0;
        for (int x = path.startX(); x <= path.endX(); x++) {
            if (path.airborneAt(x)) air++;
            minY = Math.min(minY, path.floorAt(x));
            maxY = Math.max(maxY, path.floorAt(x));
        }
        StringBuilder rows = new StringBuilder();
        for (int r = 0; r < kit.rows(); r++) {
            int n = 0;
            for (int col = 0; col < kit.columns(); col++) if (kit.block(col, r) != 0) n++;
            rows.append(n).append(' ');
        }
        return key + " size " + kit.blockSize() + " grid " + kit.columns() + "x" + kit.rows() + " area "
                + java.util.Arrays.toString(area) + " cells " + cells + " path " + path.startX() + ".." + path.endX()
                + " air " + air + " y " + minY + ".." + maxY + " rows " + rows + "\n";
    }

    private static Zone zone(String key) {
        for (Zone zone : Zone.values()) if (zone.key.equalsIgnoreCase(key)) return zone;
        throw new IllegalArgumentException("Unknown zone " + key);
    }

    /** Sets the party up as the story would have it on arriving at {@code zone}. */
    private void reach(Zone zone) {
        Progress progress = game.progress;
        progress.setIsland(zone.island());
        for (Zone earlier : Zone.values()) {
            if (earlier.ordinal() >= zone.ordinal()) break;
            progress.clear(earlier);
            progress.addEmerald(earlier.emerald);
            for (String scene : List.of(earlier.key + "-enter", earlier.key + "-mid", earlier.key + "-boss",
                    earlier.key + "-clear")) progress.markSeen(scene);
        }
        if (zone.islandIndex >= Island.WEST.ordinal()) progress.join(HeroId.TAILS);
        if (zone.ordinal() > Zone.ANGEL_ISLAND.ordinal()) progress.join(HeroId.KNUCKLES);
        int level = zone.level;
        for (Hero hero : progress.party()) if (hero.level() < level) hero.setLevel(level);
    }

    /** Builds a zone's field synchronously (debug jumps only; play uses the loading screen). */
    private FieldScreen field(Zone zone, double fraction) {
        Stage stage = game.stage(zone);
        if (stage == null) return null;
        game.progress.markSeen(zone.key + "-enter");
        game.progress.markSeen(zone.island().key() + "-arrive");
        Field field = new Field(zone, stage.path);
        if (fraction > 0) {
            double x = 88 + 760 * Math.min(1, fraction);
            field.skipTo(x);
        }
        return new FieldScreen(game, stage, field);
    }

    /** Exposed for tests: the screen object currently shown. */
    public Screen currentScreen() {
        return game.screen();
    }
}
