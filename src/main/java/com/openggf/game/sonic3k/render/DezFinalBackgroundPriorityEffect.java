package com.openggf.game.sonic3k.render;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;

/**
 * Final DEZ arena: DEZ3_ScreenInit (sonic3k.asm:120291-120348) and DEZ3_ScreenEvent
 * (sonic3k.asm:120351-120355) draw the planet and girder floor into Plane B from the
 * background layout; DEZ3_BackgroundInit (sonic3k.asm:120425-120432) and
 * DEZ3_BackgroundEvent (sonic3k.asm:120434-120439) draw the robot into Plane A from the
 * foreground layout. The floor rows ($E0-$FF) are high-priority tiles and the robot's are
 * low, so the VDP shows the floor over the robot's lower body and the players above both.
 * The engine composes the whole background before Plane A, so replay the high Plane B
 * tiles above Plane A's low pass, as LRZ3 and SSZ2 do. The rear composite already supplies
 * the matching low-sprite mask.
 */
public final class DezFinalBackgroundPriorityEffect implements SpecialRenderEffect {
    @Override public SpecialRenderEffectStage stage() {
        return SpecialRenderEffectStage.AFTER_FOREGROUND;
    }

    @Override public void render(SpecialRenderEffectContext context) {
        if (!(com.openggf.game.GameServices.zoneRuntimeState() instanceof
                com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState)) return;
        // Replay the scroll published with this frame's tile passes, as the ending does.
        var displayed = com.openggf.level.LevelSpritePresentation.backgroundScrollForDraw(context.levelManager());
        if (displayed != null) HczBgHighPriorityTileRenderer.render(context, false, false, displayed);
        else HczBgHighPriorityTileRenderer.render(context, false, false);
    }
}
