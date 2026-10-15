package com.openggf.game.mutators;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.graphics.GLCommand;

import java.util.ArrayList;
import java.util.List;

/** Bounded local-water tint, behind the HUD, using exactly the captured physics region. */
public final class MotionFluidRenderEffect implements SpecialRenderEffect {
    private final MotionMutatorRuntime runtime;
    public MotionFluidRenderEffect(MotionMutatorRuntime runtime) { this.runtime = java.util.Objects.requireNonNull(runtime); }
    @Override public SpecialRenderEffectStage stage() { return SpecialRenderEffectStage.AFTER_SPRITES; }
    @Override public void render(SpecialRenderEffectContext context) {
        var region = runtime.fluidRegion();
        if (region == null) return;
        for (var band : bands(region, context.camera().getX(), context.camera().getY(),
                context.camera().getWidth(), context.camera().getHeight())) {
            context.graphicsManager().registerCommand(GLCommand.screenSpaceRect(context.graphicsManager(),
                    context.camera().getHeight(), band.x(), band.y(), band.width(), 1, 0x303080D0));
        }
    }
    static List<Band> bands(MotionMutatorRuntime.FluidRegion region, int cameraX, int cameraY, int width, int height) {
        List<Band> bands = new ArrayList<>();
        int centreX = region.centreX() - cameraX, centreY = region.centreY() - cameraY, radius = region.radius();
        for (int y = Math.max(0, centreY - radius); y <= Math.min(height - 1, centreY + radius); y++) {
            int dy = y - centreY;
            int halfWidth = (int) Math.sqrt((long) radius * radius - (long) dy * dy);
            int left = Math.max(0, centreX - halfWidth), right = Math.min(width - 1, centreX + halfWidth);
            if (right >= left) bands.add(new Band(left, y, right - left + 1));
        }
        return List.copyOf(bands);
    }
    record Band(int x, int y, int width) { }
}
