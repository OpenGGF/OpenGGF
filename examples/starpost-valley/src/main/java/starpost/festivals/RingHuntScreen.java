package starpost.festivals;

import com.openggf.mods.scene.ActResult;
import java.util.ArrayList;

/** The year's Ring Hunt on the same real town terrain, with shared champion/pickup ownership. */
final class RingHuntScreen extends RealFestivalScreen {
    RingHuntScreen(FestivalSystem sys,Festival festival) { super(sys,festival); }
    String activity() { return "hunt"; }
    void result(ActResult result) {
        int farmer=value(result,"rings"),champion=value(result,"champion");
        boolean won=RingHunt.farmerWins(farmer,champion);
        var lines=new ArrayList<String>(); lines.add("YOURS "+farmer+" - CHAMPION "+champion);
        lines.addAll(RingHunt.reward(shell.game,festivals,farmer,champion));
        Prizes.friendship(shell.game,RingHunt.champion(shell.game),won?60:30);
        finish(won?"NEW CHAMPION!":"CHAMPION WINS",lines);
    }
}
