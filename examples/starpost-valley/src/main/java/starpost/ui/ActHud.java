package starpost.ui;

import com.openggf.mods.scene.SceneCanvas;
import starpost.core.Game;

/** Shared scene-style Momentum meter for native valley and Ruins acts. */
public final class ActHud {
    private ActHud() {}
    public static void momentum(SceneCanvas canvas,Game game) {
        int width=72,x=canvas.width()-8-width,y=19;
        int fill=Math.round(width*Math.max(0,Math.min(game.maxMomentum,game.momentum))/(float)Math.max(1,game.maxMomentum));
        canvas.fill(x-1,y-1,width+2,8,0xFF000000);
        canvas.fill(x,y,fill,6,game.momentum<game.maxMomentum/5?0xFFFF4924:0xFF24B6FF);
        canvas.fill(x,y,fill,2,0x60FFFFFF);
        Text.right(canvas,"MOMENTUM",x-4,18,Text.YELLOW);
    }
}
