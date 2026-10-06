package slaytherobotnik.run;

import java.util.List;
import java.util.function.Consumer;
import slaytherobotnik.core.Card;

/**
 * A card grid the player picks from outside combat: removing, upgrading or transforming
 * deck cards, or choosing from offered cards. {@code mode} lets the screen show the right
 * preview (an upgrade preview for {@link #UPGRADE}).
 */
public record DeckChoice(String prompt, List<Card> options, int min, int max, String mode,
        Consumer<List<Card>> onChosen) {
    public static final String PICK = "Pick";
    public static final String REMOVE = "Remove";
    public static final String UPGRADE = "Upgrade";
    public static final String TRANSFORM = "Transform";

    /** Whether the player may back out without choosing. */
    public boolean cancellable() {
        return min == 0;
    }
}
