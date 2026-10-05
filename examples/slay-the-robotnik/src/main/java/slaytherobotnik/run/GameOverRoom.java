package slaytherobotnik.run;

/** The player died. {@code killedBy} names the encounter or event. */
public record GameOverRoom(String killedBy, int score) implements Room {
}
