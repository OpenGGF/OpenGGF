package starpost.fishing;

import com.openggf.mods.scene.*;
import starpost.art.Art;
import starpost.realfest.*;
import starpost.scene.Shell;
import starpost.ui.Text;

/** Casting and Bubble Bar presentation on the lake level, reusing the pond's ROM art. */
public final class LakeOverlay {
    private final FishArt art;
    public LakeOverlay(Art art) { this.art=new FishArt(art); }
    public static int poolY(Art art) {
        var image=art.kit.blockImage(51);
        for(int y=Art.FLOOR-50;y<Art.BLOCK-8;y++) { int drawn=0;
            for(int x=0;x<Art.BLOCK;x++) if(image.pixel(x,y)>>>24!=0) drawn++;
            if(drawn>=Art.BLOCK/4) return 128+y;
        } return 128+Art.FLOOR-16;
    }
    public void draw(Shell shell,SceneCanvas canvas,LakeState state,float x,float feet,int cx,int cy,int ticks) {
        if(state.contest()) Text.centred(canvas,"TIME "+(state.left()+59)/60+"  POINTS "+state.score(),42,Text.YELLOW);
        if(!state.notice().isEmpty()) Text.centred(canvas,state.notice(),58,Text.WHITE);
        if(state.charging() || state.line.out()) {
            float angle=state.charging()?Rod.REST+(Rod.BACK-Rod.REST)*state.power():Rod.WAIT;
            float[] hand=Rod.hand(shell.game.farmer);
            float[] tip=Rod.draw(canvas,x+hand[0],feet-hand[1],1,angle,Rod.bend(ticks,state.line.state==Line.BITE));
            if(state.line.out()) {
                float bx=state.line.bobberX()-cx,by=state.line.bobberY()-cy;
                line(canvas,tip[0],tip[1],bx,by);
                canvas.fill(Math.round(bx)-2,Math.round(by)-3,4,3,0xFFFF4924);
                canvas.fill(Math.round(bx)-2,Math.round(by),4,3,0xFFFFFFFF);
            }
            if(state.charging()) { canvas.fill(Math.round(x)-22,Math.round(feet)-60,44,5,0xFF101848);
                canvas.fill(Math.round(x)-21,Math.round(feet)-59,Math.round(42*state.power()),3,Text.YELLOW); }
        }
        if(state.bar()!=null) new BubbleBarScreen(art,state.hooked(),state.bar(),1,(won,perfect)->{}).drawLevel(shell,canvas,state.barResult(),ticks);
        else Text.centred(canvas,"B / X: CAST OR STRIKE   START: LEAVE",canvas.height()-34,Text.YELLOW);
    }
    private void line(SceneCanvas canvas,float x,float y,float bx,float by) {
        int n=Math.max(1,Math.round(Math.max(Math.abs(bx-x),Math.abs(by-y))));
        for(int i=0;i<=n;i++) canvas.fill(Math.round(x+(bx-x)*i/n),Math.round(y+(by-y)*i/n),1,1,0xFFDBDBDB);
    }
}
