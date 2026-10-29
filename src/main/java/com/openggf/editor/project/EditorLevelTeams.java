package com.openggf.editor.project;

import com.openggf.game.CharacterKey;
import com.openggf.i18n.UiMessages;
import com.openggf.game.GameplayLaunchTeam;
import java.util.List;
import java.util.Objects;

/** Stock authoring presets; persisted settings also accept installed character keys. */
public final class EditorLevelTeams {
    public record Choice(String label, GameplayLaunchTeam team) { }
    private static final List<Choice> CHOICES = List.of(new Choice("editor.team.0", null),
            new Choice("editor.team.1", new GameplayLaunchTeam(CharacterKey.SONIC, List.of())),
            new Choice("editor.team.2", new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS))),
            new Choice("editor.team.3", new GameplayLaunchTeam(CharacterKey.TAILS, List.of())),
            new Choice("editor.team.4", new GameplayLaunchTeam(CharacterKey.KNUCKLES, List.of())));
    private EditorLevelTeams() { }
    public static List<Choice> choices() { return CHOICES.stream()
            .map(choice -> new Choice(UiMessages.text(choice.label()), choice.team())).toList(); }
    public static String label(GameplayLaunchTeam team) {
        return choices().stream().filter(choice -> Objects.equals(choice.team(), team)).map(Choice::label)
                .findFirst().orElseGet(() -> team.main().persisted() + " + " + team.sidekicks().size());
    }
}
