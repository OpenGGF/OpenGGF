package mutators;

import com.openggf.game.mutators.MonitorContent;
import com.openggf.mods.mutators.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Common semantic checkboxes; native registries classify each game's actual subtypes. */
public final class NoPowerups {
    private NoPowerups() { }
    public static MutatorDefinition definition() {
        List<MutatorOption> options = Arrays.stream(MonitorContent.values()).map(content ->
                (MutatorOption) new MutatorOption.Checkbox(id(content), label(content),
                        "Remove this monitor before it is created on the next full load. Unsupported types are unavailable.",
                        MutatorScope.LOAD, content != MonitorContent.BROKEN_SHELL)).toList();
        return new MutatorDefinition("no-powerups", "No Powerups",
                "Remove checked item monitors on a full restart. Editing while standing on one leaves it in place until then.",
                MutatorScope.LOAD, MutatorScope.LOAD, options, Set.of(MutatorCapability.MONITOR_FILTER),
                values -> List.of(new MutatorPolicy.MonitorFilter(Arrays.stream(MonitorContent.values())
                        .filter(content -> values.checkbox(id(content))).collect(Collectors.toSet()))));
    }
    public static String id(MonitorContent content) { return content.name().toLowerCase(java.util.Locale.ROOT); }
    private static String label(MonitorContent content) {
        return switch (content) {
            case LIFE -> "Extra life";
            case RINGS -> "Ten rings";
            case SPEED_SHOES -> "Speed shoes";
            case INVINCIBILITY -> "Invincibility";
            case BASIC_SHIELD -> "Basic shield";
            case FIRE_SHIELD -> "Fire shield";
            case LIGHTNING_SHIELD -> "Lightning shield";
            case BUBBLE_SHIELD -> "Bubble shield";
            case EGGMAN -> "Eggman";
            case SUPER -> "Super monitor";
            case STATIC -> "Static monitor";
            case S_MONITOR -> "S monitor";
            case GOGGLES -> "Goggles";
            case TELEPORT -> "Teleport";
            case RANDOM -> "Random monitor";
            case BROKEN_SHELL -> "Broken shells";
        };
    }
}
