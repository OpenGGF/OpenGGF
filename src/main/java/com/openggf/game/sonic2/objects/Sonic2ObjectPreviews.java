package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.objects.ObjectSpawn;

/** Static native placement poses selected from each production object's art owner. */
final class Sonic2ObjectPreviews {
    private Sonic2ObjectPreviews() { }
    static ObjectPreview preview(ObjectSpawn spawn) {
        int id = spawn.objectId();

        if (id == Sonic2ObjectIds.MONITOR) {
            // Production monitor animations settle on subtype+1; frame 11 is the broken shell.
            int type = spawn.subtype() & 15;
            return ObjectPreview.frame("monitor", type < 10 ? type + 1 : 11);
        }
        if (id == Sonic2ObjectIds.SPRING) {
            int type = (spawn.subtype() >> 4) & 7;
            boolean yellow = (spawn.subtype() & 2) != 0;
            String direction = type == 1 ? "horizontal" : type >= 3 ? "diagonal" : "vertical";
            String key = "spring_" + direction + (yellow ? "_red" : "");
            return new ObjectPreview(java.util.List.of(new ObjectPreview.Part(key, 0, 0, 0, false, type == 2 || type == 4)));
        }

        if (id == Sonic2ObjectIds.SPIKES) {
            int frame = Math.min(7, (spawn.subtype() >> 4) & 15);
            return ObjectPreview.frame(frame >= 4 ? "spike_side" : "spike", frame);
        }
        if (id == Sonic2ObjectIds.BRIDGE_STAKE) return ObjectPreview.frame("bridge", 1);
        if (id == Sonic2ObjectIds.GENERIC_PLATFORM_A) return ObjectPreview.frame("platform", Math.min(4, (spawn.subtype() >> 4) & 7));
        if (id == Sonic2ObjectIds.BRIDGE) {
            int count = Math.clamp(spawn.subtype() & 31, 1, 32);
            var parts = new java.util.ArrayList<ObjectPreview.Part>();
            for (int i = 0; i < count; i++) parts.add(new ObjectPreview.Part("bridge", 0, (i - count / 2) * 16, 0, false, false));
            return new ObjectPreview(parts);
        }
        String key = switch (id) {
            case Sonic2ObjectIds.MONITOR -> "monitor";
            case Sonic2ObjectIds.SPRING -> "spring_vertical";
            case Sonic2ObjectIds.SPIKES -> "spike";
            case Sonic2ObjectIds.CHECKPOINT -> "checkpoint";
            case Sonic2ObjectIds.SIGNPOST -> "signpost";
            case Sonic2ObjectIds.BRIDGE -> "bridge";
            case Sonic2ObjectIds.BRIDGE_STAKE -> "bridge";
            case Sonic2ObjectIds.BUZZER -> "buzzer";
            case Sonic2ObjectIds.MASHER -> "masher";
            case Sonic2ObjectIds.COCONUTS -> "coconuts";
            case Sonic2ObjectIds.SEESAW -> "seesaw";
            case Sonic2ObjectIds.SWINGING_PLATFORM -> "swinging_platform";
            case Sonic2ObjectIds.GENERIC_PLATFORM_A -> "platform";
            case Sonic2ObjectIds.GENERIC_PLATFORM_B -> "cpz_platform";
            case Sonic2ObjectIds.EHZ_WATERFALL -> "waterfall";
            case Sonic2ObjectIds.BUMPER -> "bumper";
            case Sonic2ObjectIds.HEX_BUMPER -> "hex_bumper";
            case Sonic2ObjectIds.BONUS_BLOCK -> "bonus_block";
            case Sonic2ObjectIds.FLIPPER -> "flipper";
            case Sonic2ObjectIds.OCTUS -> "octus";
            case Sonic2ObjectIds.AQUIS -> "aquis";
            case Sonic2ObjectIds.CRAWLTON -> "crawlton";
            case Sonic2ObjectIds.FLASHER -> "flasher";
            case Sonic2ObjectIds.SHELLCRACKER -> "shellcracker";
            case Sonic2ObjectIds.SLICER -> "slicer";
            case Sonic2ObjectIds.ASTERON -> "asteron";
            case Sonic2ObjectIds.SPINY -> "spiny";
            case Sonic2ObjectIds.SPINY_ON_WALL -> "spiny";
            case Sonic2ObjectIds.GRABBER -> "grabber";
            case Sonic2ObjectIds.CHOP_CHOP -> "chopchop";
            case Sonic2ObjectIds.SPIKER -> "spiker";
            case Sonic2ObjectIds.REXON -> "rexon";
            case Sonic2ObjectIds.SOL -> "sol";
            case Sonic2ObjectIds.WHISP -> "whisp";
            case Sonic2ObjectIds.GROUNDER_IN_WALL -> "grounder";
            case Sonic2ObjectIds.NEBULA -> "nebula";
            case Sonic2ObjectIds.TURTLOID -> "turtloid";
            case Sonic2ObjectIds.BALKIRY -> "balkiry";
            case Sonic2ObjectIds.CLUCKER -> "clucker";
            case Sonic2ObjectIds.CRAWL -> "crawl";
            case Sonic2ObjectIds.BUTTON -> "button";
            case Sonic2ObjectIds.BREAKABLE_BLOCK -> "breakable_block";
            case Sonic2ObjectIds.TIPPING_FLOOR -> "tipping_floor";
            case Sonic2ObjectIds.SPEED_BOOSTER -> "speed_booster";
            case Sonic2ObjectIds.BARRIER -> "barrier";
            case Sonic2ObjectIds.SPRINGBOARD -> "springboard";
            case Sonic2ObjectIds.EGG_PRISON -> "egg_prison";
            case Sonic2ObjectIds.ARROW_SHOOTER -> "arrow_shooter";
            case Sonic2ObjectIds.BUBBLES -> "bubbles";
            case Sonic2ObjectIds.CPZ_PYLON -> "cpz_pylon";
            case Sonic2ObjectIds.PIPE_EXIT_SPRING -> "pipe_exit_spring";
            case Sonic2ObjectIds.FALLING_PILLAR -> "falling_pillar";
            default -> null;
        };
        return key == null ? ObjectPreview.empty() : ObjectPreview.frame(key, 0);
    }
}
