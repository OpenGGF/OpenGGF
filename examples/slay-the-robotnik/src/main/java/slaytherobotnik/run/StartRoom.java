package slaytherobotnik.run;

import java.util.List;

/**
 * The Tornado flight that opens each act: pick one bonus from the cargo hold (Neow in Slay
 * the Spire). Act 1 offers the full set of four; later acts offer three smaller ones.
 */
public final class StartRoom implements Room {
    /** One bonus. {@code apply} may open a deck choice. */
    public record Option(String label, String detail, Runnable apply) {
    }

    private final Run run;
    private final String speech;
    private final List<Option> options;
    private int chosen = -1;

    StartRoom(Run run, String speech, List<Option> options) {
        this.run = run;
        this.speech = speech;
        this.options = options;
    }

    public String speech() { return speech; }
    public List<Option> options() { return options; }
    public boolean done() { return chosen >= 0; }
    public int chosen() { return chosen; }

    public boolean choose(int index) {
        if (chosen >= 0 || index < 0 || index >= options.size()) {
            return false;
        }
        chosen = index;
        options.get(index).apply().run();
        return true;
    }

    /** Lands the Tornado and opens the map. */
    public void proceed() {
        run.leaveRoom();
    }
}
