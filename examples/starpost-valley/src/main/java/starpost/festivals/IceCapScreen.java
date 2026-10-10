package starpost.festivals;

import com.openggf.mods.scene.*;
import java.util.ArrayList;
import java.util.List;

/** Frost's choice/verdict are scenes; the snowboard and fishing contests use real levels. */
final class IceCapScreen extends FestivalScreen {
    static final int CONTEST_SECONDS=120;
    static final int BOARD_FLAT=1;
    private boolean launched;
    IceCapScreen(FestivalSystem sys,Festival festival) { super(sys,festival); }
    void begin() { shell.music.want("s3k",0x0B); }
    void step() {
        if(launched) return; launched=true;
        shell.push(new Choose("FROST'S ICE CAP CONTEST",List.of("FISHING CONTEST","SNOWBOARD RUN"),null,
            choice->{ if(choice<0) { launched=false; return; }
                shell.startActivity(play,choice==0?"lake":"snowboard",choice==0?CONTEST_SECONDS:0,this::result); }));
    }
    private void result(ActResult result) {
        boolean fishing=result.state().get("activity.kind").equals("lake");
        int score=RealFestivalScreen.value(result,"points"),rings=RealFestivalScreen.value(result,"rings");
        boolean won=Snowboard.beats(score,fishing?FishingContest.FROST_CATCH:Snowboard.FROST_RECORD);
        var lines=new ArrayList<String>(); lines.add("YOURS "+score+" - FROST "+(fishing?FishingContest.FROST_CATCH:Snowboard.FROST_RECORD));
        lines.addAll(fishing?Snowboard.fishingReward(shell.game,festivals,score,FishingContest.FROST_CATCH)
            :Snowboard.reward(shell.game,festivals,score,rings,won));
        finish(won?"FROST'S RECORD!":"WELL PLAYED",lines);
    }
    void paint(SceneCanvas canvas) { play.draw(shell,canvas); }
}
