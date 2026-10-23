package com.openggf.game.sonic2;

import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.DestinationPlan;
import com.openggf.game.animation.strategies.ScriptFramesApplyStrategy;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.animation.AniPlcScriptState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class Sonic2AnimatedTileChannels {

    private Sonic2AnimatedTileChannels() {
    }

    static List<AnimatedTileChannel> fromScripts(List<AniPlcScriptState> scripts,
                                               Supplier<? extends GraphicsManager> graphics) {
        List<AnimatedTileChannel> channels = new ArrayList<>(scripts.size());
        for (int i = 0; i < scripts.size(); i++) {
            AniPlcScriptState script = scripts.get(i);
            channels.add(new AnimatedTileChannel(
                    "s2.script." + i,
                    () -> true,
                    ctx -> ctx.frameCounter(),
                    destinationPlan(script),
                    AnimatedTileCachePolicy.ALWAYS,
                    new ScriptFramesApplyStrategy(script, graphics)));
        }
        return channels;
    }

    private static DestinationPlan destinationPlan(AniPlcScriptState script) {
        int primaryTile = script.destinationTileIndex();
        int lastTile = primaryTile + Math.max(script.tilesPerFrame(), 1) - 1;
        return lastTile > primaryTile
                ? new DestinationPlan(primaryTile, lastTile)
                : DestinationPlan.single(primaryTile);
    }
}
