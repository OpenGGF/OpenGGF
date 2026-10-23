package com.openggf.editor.project;

import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import java.util.List;
import java.util.Objects;

/** Stock authoring presets; persisted settings also accept installed character keys. */
public final class EditorLevelTeams {
    public record Choice(String label, GameplayLaunchTeam team) { }
    private static final List<Choice> CHOICES = List.of(new Choice("Any team", null),
            new Choice("Sonic", new GameplayLaunchTeam(CharacterKey.SONIC, List.of())),
            new Choice("Sonic and Tails", new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS))),
            new Choice("Tails", new GameplayLaunchTeam(CharacterKey.TAILS, List.of())),
            new Choice("Knuckles", new GameplayLaunchTeam(CharacterKey.KNUCKLES, List.of())));
    private EditorLevelTeams() { }
    public static List<Choice> choices() { return CHOICES; }
    public static String label(GameplayLaunchTeam team) {
        return CHOICES.stream().filter(choice -> Objects.equals(choice.team(), team)).map(Choice::label)
                .findFirst().orElseGet(() -> team.main().persisted() + " + " + team.sidekicks().size());
    }
}
