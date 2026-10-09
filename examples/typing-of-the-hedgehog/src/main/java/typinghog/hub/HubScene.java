package typinghog.hub;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import typinghog.play.CourseAssets;
import typinghog.rails.Rail;
import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

/**
 * The hub: chooses an act and hands off to gameplay with a {@link LaunchIntent}; shows the last
 * {@link ActResult} when gameplay returns. (Scaffold; the full hub replaces this.)
 */
public final class HubScene implements ModScene {
    private final CourseAssets assets;
    private final List<Rail> rails = new ArrayList<>();
    private int selection;
    private ActResult last;
    private String game = "s1";

    public HubScene(CourseAssets assets) {
        this.assets = assets;
    }

    @Override public void enter(SceneContext ctx) {
        var launch = ctx.currentLaunch();
        if (launch != null) game = launch.gameId();
        rails.clear();
        assets.rails().values().stream().filter(rail -> rail.game().equals(game))
                .sorted(Comparator.comparingInt(Rail::zone).thenComparingInt(Rail::act)).forEach(rails::add);
        last = ctx.storage().read(ActResult.FILE).map(ActResult::decode).orElse(null);
        ctx.storage().delete(ActResult.FILE);
    }

    @Override public void update(SceneContext ctx) {
        if (rails.isEmpty()) return;
        if (ctx.buttonRepeated(SceneButtons.DOWN)) selection = Math.floorMod(selection + 1, rails.size());
        if (ctx.buttonRepeated(SceneButtons.UP)) selection = Math.floorMod(selection - 1, rails.size());
        if (ctx.input().menuAccept()) {
            Rail rail = rails.get(selection);
            var intent = new LaunchIntent(LaunchIntent.Mode.ARCADE, rail.game(), rail.zone(), rail.act(),
                    LaunchIntent.Difficulty.NORMAL, false, false, "standard", ctx.ticks() * 0x9E3779B97F4A7C15L,
                    1, false, false);
            ctx.storage().write(LaunchIntent.FILE, intent.encode());
            ctx.startStockAct(rail.zone(), rail.act());
        }
    }

    @Override public void draw(SceneContext ctx, SceneCanvas canvas) {
        canvas.clear(0x101830);
        canvas.text("THE TYPING OF THE HEDGEHOG", 16, 16, 0xFFFFD000);
        if (last != null) {
            canvas.text((last.cleared() ? "CLEARED " : "FAILED ") + "SCORE " + last.score() + "  RANK " + last.grade()
                    + "  WPM " + Math.round(last.wpm()), 16, 36, 0xFFFFFFFF);
        }
        for (int i = 0; i < rails.size(); i++) {
            Rail rail = rails.get(i);
            canvas.text((i == selection ? "> " : "  ") + rail.game().toUpperCase() + " ZONE " + rail.zone() + " ACT " + (rail.act() + 1),
                    16, 60 + i * 12, i == selection ? 0xFFFFD000 : 0xFFFFFFFF);
        }
        if (rails.isEmpty()) canvas.text("NO RAILS FOR " + game.toUpperCase(), 16, 60, 0xFFFF6060);
    }
}
