package com.openggf.level.objects;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import com.openggf.game.mutators.HudPresentationRuntime;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;

/** Engine-internal bridge for destination-scoped HUD profile installation. */
public final class HudProfileAccess {
    private static final String READOUT_PREFIX = "native-hud-readout:";
    private HudProfileAccess() {
    }

    public static void install(HudRenderManager hud, HudProfile profile) {
        Objects.requireNonNull(hud, "hud").installProfile(profile);
    }

    /** Internal ROM topology installation; the creator-facing HudStaticArt shape stays unchanged. */
    public static void installSatLayout(HudRenderManager hud, com.openggf.game.internal.HudSatLayout layout) {
        Objects.requireNonNull(hud, "hud").setSatLayout(layout);
    }

    public static void setScreenLeftAnchorEnabled(HudRenderManager hud, boolean enabled) {
        Objects.requireNonNull(hud, "hud").setScreenLeftAnchorEnabled(enabled);
    }

    public static void installWarningPolicy(HudRenderManager hud,
            com.openggf.game.internal.HudWarningPolicyProvider policy, java.util.function.IntSupplier clock) {
        Objects.requireNonNull(hud, "hud").setWarningPolicy(
                Objects.requireNonNull(policy, "policy"), Objects.requireNonNull(clock, "clock"));
    }

    public static void drawVBlankCounters(HudRenderManager hud, com.openggf.game.LevelState state,
            com.openggf.game.PlayableEntity player, boolean advanceTimer) {
        Objects.requireNonNull(hud, "hud").drawForCounterPublication(state, player, advanceTimer);
    }

    public static HudProfile current(HudRenderManager hud) {
        return Objects.requireNonNull(hud, "hud").currentProfile();
    }

    static void withReadout(GraphicsManager graphics, int id, Runnable draw) {
        var runtime=runtime(graphics);
        if(runtime==null||!runtime.tags()) { draw.run();return; }
        SpritePresentation.withSubject(graphics,
                new SpritePresentation.Subject(READOUT_PREFIX + id, SpritePresentation.Part.WORLD, false), draw);
    }

    /** Layout is sampled by the world runtime only on an active presentation. */
    public static List<HudPresentationRuntime.Readout> presentationReadouts(GraphicsManager graphics) {
        var pipeline = graphics.getUiRenderPipeline();
        var hud = pipeline == null ? null : pipeline.getHudRenderManager();
        return hud == null ? List.of() : hud.presentationReadouts();
    }

    public static void bindWorld(HudRenderManager hud,WorldSession world) { hud.presentationWorld=world; }
    static HudPresentationRuntime runtime(GraphicsManager graphics) {
        var pipeline=graphics.getUiRenderPipeline();
        var hud=pipeline==null?null:pipeline.getHudRenderManager();
        return hud!=null&&hud.presentationWorld!=null?WorldSessionPolicyAccess.getService(hud.presentationWorld,HudPresentationRuntime.class):null;
    }

    /** Called once at replay entry, after native SAT/counter publication and before drawing. */
    public static SpritePresentation.Frame projectPresentation(GraphicsManager graphics, SpritePresentation.Frame frame) {
        return projectPresentation(runtime(graphics), frame);
    }

    /** Explicit world bridge for owned offscreen consumers that do not install a global session. */
    public static SpritePresentation.Frame projectPresentation(WorldSession world, GraphicsManager graphics,
                                                                SpritePresentation.Frame frame) {
        return projectPresentation(WorldSessionPolicyAccess.getService(world, HudPresentationRuntime.class), frame);
    }

    private static SpritePresentation.Frame projectPresentation(HudPresentationRuntime runtime, SpritePresentation.Frame frame) {
        if (runtime == null || !runtime.active()) return frame;
        var counterLeft = new HashMap<Integer, Float>();
        var labelLeft = new HashMap<Integer, Float>();
        for (var tile : frame.tiles()) {
            int id = readoutId(tile);
            if (id >= 0 && tile.layer() == SpritePresentation.Layer.HUD_COUNTERS)
                counterLeft.merge(id, tile.x(), Math::min);
            else if (id >= 0 && tile.layer() == SpritePresentation.Layer.HUD)
                labelLeft.merge(id, tile.x(), Math::min);
        }
        var tiles = new ArrayList<SpritePresentation.Tile>(frame.tiles().size());
        int[] retainedBefore = new int[frame.tiles().size() + 1];
        boolean changed = false;
        for (int index = 0; index < frame.tiles().size(); index++) {
            retainedBefore[index] = tiles.size();
            var tile = frame.tiles().get(index); int id = readoutId(tile);
            var pair = id < 0 ? null : runtime.pair(id);
            if (pair == null) { tiles.add(tile); continue; }
            changed = true;
            if (runtime.hidden()) continue;
            var row = pair.readout(); float scale = (float)pair.scale();
            boolean counter = tile.layer() == SpritePresentation.Layer.HUD_COUNTERS;
            float localX = counter ? row.labelWidth() + (row.labelWidth() == 0 ? 0 : 4)
                    + tile.x() - counterLeft.get(id) : tile.x() - labelLeft.get(id);
            float localY = tile.y() - row.top();
            tiles.add(new SpritePresentation.Tile(tile.layer(), tile.patternId(), tile.palette(), tile.hFlip(), tile.vFlip(),
                    tile.priority(), (float)pair.x() + localX * scale, (float)pair.y() + localY * scale,
                    tile.width() * scale, tile.height() * scale, tile.priorityShader(), tile.occlusionMask(),
                    tile.ghost(), tile.ghostAlpha(), tile.rowStart(), tile.rowEnd(),
                    new SpritePresentation.Subject("projected-" + tile.subject().id(), tile.subject().part(),
                            tile.subject().suppressed(), tile.subject().headScalePercent(), tile.subject().head()), tile.pixelOperation()));
        }
        if (!changed) return frame;
        retainedBefore[frame.tiles().size()] = tiles.size();
        var primitives = new ArrayList<SpritePresentation.Primitive>();
        for (var primitive : frame.primitives()) primitives.add(new SpritePresentation.Primitive(
                retainedBefore[primitive.beforeTile()], primitive.layer(), primitive.primitive(), primitive.subject()));
        return new SpritePresentation.Frame(tiles, primitives, frame.patternVersions());
    }

    private static int readoutId(SpritePresentation.Tile tile) {
        if (!tile.layer().isHud() || !tile.subject().id().startsWith(READOUT_PREFIX)) return -1;
        try { return Integer.parseInt(tile.subject().id().substring(READOUT_PREFIX.length())); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
