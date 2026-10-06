package slaytherobotnik.run;

import java.util.List;
import slaytherobotnik.core.RestOption;

/** A Starpost: rest (heal 30% of max HP), smith (upgrade a card), or a relic's option. */
public final class RestRoom implements Room {
    private final Run run;
    private final List<RestOption> options;
    private String chosen;

    RestRoom(Run run, List<RestOption> options) {
        this.run = run;
        this.options = options;
    }

    public List<RestOption> options() { return options; }
    /** Id of the option used, or null while choosing. */
    public String chosen() { return chosen; }

    public boolean choose(String id) {
        if (chosen != null) {
            return false;
        }
        for (RestOption option : options) {
            if (option.id().equals(id) && option.enabled()) {
                chosen = id;
                option.action().run();
                return true;
            }
        }
        return false;
    }

    /** Undo the choice when the follow-up (picking a card to smith) was cancelled. */
    void cancelChoice() {
        chosen = null;
    }

    public void proceed() {
        run.leaveRoom();
    }
}
