package starpost.festivals;

import com.openggf.mods.scene.ActResult;
import java.util.ArrayList;

/** Two native Green Hill circuit laps; rewards are applied once on scene resume. */
final class RaceScreen extends RealFestivalScreen {
    RaceScreen(FestivalSystem sys,Festival festival) { super(sys,festival); }
    String activity() { return "race"; }
    void result(ActResult result) {
        int place=value(result,"place"),ticks=value(result,"ticks");
        var lines=new ArrayList<String>(); lines.add(ordinal(place)+" PLACE - "+clock(ticks));
        lines.addAll(Race.reward(shell.game,festivals,place,ticks));
        finish(placeWord(place),lines);
    }
}
