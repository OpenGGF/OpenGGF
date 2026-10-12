package starpost.festivals;

import com.openggf.mods.scene.*;

/** The festival invitation/title/results stay scenes; all play between them runs in an act. */
abstract class RealFestivalScreen extends FestivalScreen {
    private boolean launched;
    RealFestivalScreen(FestivalSystem sys,Festival festival) { super(sys,festival); }
    abstract String activity();
    abstract void result(ActResult result);
    void begin() { play.clockStopped=true; play.hudHidden=true; }
    void step() {
        if(!launched) { launched=true; shell.startActivity(play,activity(),0,this::result); }
    }
    void paint(SceneCanvas canvas) { play.draw(shell,canvas); }
    static int value(ActResult result,String key) { return Integer.parseInt(result.state().getOrDefault("activity."+key,"0")); }
}
