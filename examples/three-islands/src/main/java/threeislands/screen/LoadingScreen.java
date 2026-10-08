package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import threeislands.Game;
import threeislands.core.EnemyKind;
import threeislands.core.Hero;
import threeislands.core.Zone;
import threeislands.field.Stage;
import threeislands.view.Ui;

/**
 * A title card while a zone loads. The work is split into steps run a few per tick: build the
 * level kit, trace the route, decode the block pictures along it, and decode the foes, so the
 * field never stalls on art once play begins.
 */
public final class LoadingScreen implements Screen {
    private final Zone zone;
    private final Consumer<Stage> then;
    private int step;
    private Stage stage;
    private List<Integer> blocks = List.of();
    private int warmed;
    private final List<EnemyKind> foes = new ArrayList<>();
    private int foeIndex;
    private long shown;
    private boolean failed;

    public LoadingScreen(Game game, Zone zone, Consumer<Stage> then) {
        this.zone = zone;
        this.then = then;
        foes.addAll(zone.enemyKinds());
        foes.addAll(zone.bossKinds());
    }

    @Override
    public String name() {
        return "LOADING";
    }

    @Override
    public void update(Game game) {
        shown++;
        if (failed) {
            if (game.controls.accept() || game.controls.back()) game.go(new MapScreen(game));
            return;
        }
        long budget = System.nanoTime() + 6_000_000L;
        while (System.nanoTime() < budget && step < 5) {
            switch (step) {
                case 0 -> {
                    stage = game.stage(zone);
                    if (stage == null) {
                        failed = true;
                        return;
                    }
                    blocks = stage.blocksNearRoute(game.width() + 64, 384);
                    step++;
                }
                case 1 -> {
                    if (warmed < blocks.size()) stage.kit.blockImage(blocks.get(warmed++));
                    else step++;
                }
                case 2 -> {
                    if (foeIndex < foes.size()) game.enemies.preload(foes.get(foeIndex++));
                    else step++;
                }
                case 3 -> {
                    for (Hero hero : game.progress.party()) game.art.hero(game.heroes.gameFor(zone.game, hero.id), hero.id);
                    game.art.sprites("s3k:monitor");
                    game.art.sprites("s3k:starpost");
                    game.art.sprites("s3k:ring");
                    game.art.sprites("s3k:explosion");
                    step++;
                }
                default -> step++;
            }
        }
        // Start the zone's own song now; the card waits a little for it, then the island's
        // S3K stand-in covers whatever synthesis remains.
        game.audio.music(zone.game, zone.music, zone.island().mapMusic);
        // Keep the card up long enough to read, even when loading is quick.
        if (step >= 5 && shown >= 45 && (game.audio.ready() || shown >= 240)) then.accept(stage);
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        c.clear(0x000010);
        int slide = (int) Math.max(0, 40 - shown * 4);
        int island = zone.island().color;
        c.fill(0, 70 - slide, w, 26, island);
        c.fill(0, 96 - slide, w, 3, 0xFF000000);
        c.fill(w / 2 - 70 + slide, 108, 140, 30, 0xFFFFD020);
        game.font.shadowed(c, zone.island().label.toUpperCase(), 24, 79 - slide, Ui.TEXT);
        String title = zone.label.toUpperCase() + " ZONE";
        game.font.shadowed(c, title, w / 2 - game.font.width(title), 114, 0xFF101010, 2);
        if (failed) {
            game.font.centered(c, "This zone's level data could not be read.", w / 2, 160, Ui.BAD);
            game.font.centered(c, "Press any button to return to the map.", w / 2, 174, Ui.DIM);
            return;
        }
        int total = Math.max(1, blocks.size() + foes.size() + 3);
        int done = Math.min(total, (step >= 1 ? 1 : 0) + warmed + foeIndex + (step >= 4 ? 2 : 0));
        game.ui.bar(c, w / 2 - 80, 168, 160, 4, done, total, Ui.GOLD);
        game.font.centered(c, step >= 5 ? "Ready!" : "Loading...", w / 2, 178, Ui.DIM);
    }
}
