package starpost.realtown;

import starpost.scene.PlayScreen;
import starpost.scene.Shell;
import starpost.people.PeopleSystem;
import starpost.festivals.FestivalSystem;

/** Phase-2 glue without guessed engine APIs. The E1 owner calls prepare before startAct and
 * resume once after consuming requestActExit. Return to this same PlayScreen before relaunch. */
public final class TownBridge {
    private TownBridge() {}
    public static void prepare(TownSession town,Shell shell,PlayScreen play) {
        town.bind(shell.game,TownLayout.from(play.valley().valley),
            new TownPresentation(shell.art,FestivalSystem.forPlay(play),play));
    }
    public static void resume(Shell shell,PlayScreen play,TownSession.HandBack result) {
        if (result==null) return;
        play.placeInValley(result.returnX());
        shell.goNow(play);
        switch (result.place()) {
            case "farm_gate" -> play.returnToFarm();
            case "heart_event" -> PeopleSystem.of(play).startHeartEvent(result.event());
            case "inventory" -> shell.push(new starpost.scene.InventoryMenu());
            case "time_up", "fainted" -> shell.endActDay(result.place().equals("fainted"));
            default -> {
                var handler=play.places.get(result.place());
                if (handler==null) throw new IllegalArgumentException("Unregistered town door: "+result.place());
                handler.accept(shell);
            }
        }
    }
}
