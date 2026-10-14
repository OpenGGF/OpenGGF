package com.openggf.game.sonic3k.render;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;

/** Native D01 VDP order: B-high sea covers A-low island, before A-high terrain. */
public final class EndingBackgroundPriorityEffect implements SpecialRenderEffect {
    @Override public SpecialRenderEffectStage stage(){return SpecialRenderEffectStage.AFTER_FOREGROUND;}
    @Override public void render(SpecialRenderEffectContext context){
        // Use the existing per-scanline/window replay. The ordinary rear-composite
        // priority metadata already supplies the matching low-sprite mask.
        var displayed=com.openggf.level.LevelSpritePresentation.backgroundScrollForDraw(context.levelManager());
        if(displayed!=null)HczBgHighPriorityTileRenderer.render(context,false,false,displayed);
    }
}
