package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.objects.ObjectSpawn;

/** Static native placement poses selected from each production object's art owner. */
final class Sonic3kObjectPreviews {
    private Sonic3kObjectPreviews() { }
    static ObjectPreview preview(ObjectSpawn spawn) {
        int id = spawn.objectId();

        if (id == Sonic3kObjectIds.MONITOR) {
            // Production monitor animations settle on subtype+1; frame 11 is the broken shell.
            int type = spawn.subtype() & 15;
            return ObjectPreview.frame("monitor", type < 10 ? type + 1 : 1);
        }
        if (id == Sonic3kObjectIds.SPRING) {
            int type = (spawn.subtype() >> 4) & 7;
            boolean yellow = (spawn.subtype() & 2) != 0;
            String direction = type == 1 ? "horizontal" : type >= 3 ? "diagonal" : "vertical";
            String key = "s3k_spring_" + direction + (yellow ? "_yellow" : "");
            return new ObjectPreview(java.util.List.of(new ObjectPreview.Part(key, 0, 0, 0, false, type == 2 || type == 4)));
        }

        if (id == Sonic3kObjectIds.AIZLRZ_ROCK) return ObjectPreview.frame("aiz1_rock", (spawn.subtype() >> 4) & 7);
        String key = switch (id) {
            case Sonic3kObjectIds.MONITOR -> "monitor";
            case Sonic3kObjectIds.SPRING -> "s3k_spring_vertical";
            case Sonic3kObjectIds.SPIKES -> "s3k_spikes";
            case Sonic3kObjectIds.STAR_POST -> "checkpoint";
            case Sonic3kObjectIds.AIZ1_TREE -> "aiz1_tree";
            case Sonic3kObjectIds.AIZ1_ZIPLINE_PEG -> "aiz1_zipline_peg";
            case Sonic3kObjectIds.AIZ_RIDE_VINE -> "aiz_ride_vine";
            case Sonic3kObjectIds.AIZ_FOREGROUND_PLANT -> "aiz_foreground_plant";
            case Sonic3kObjectIds.AIZLRZ_ROCK -> "aiz1_rock";
            case Sonic3kObjectIds.COLLAPSING_PLATFORM -> "collapsing_platform_aiz1";
            case Sonic3kObjectIds.AIZ_FLIPPING_BRIDGE -> "aiz_flipping_bridge";
            case Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE -> "aiz_collapsing_log_bridge";
            case Sonic3kObjectIds.AIZ_FALLING_LOG -> "aiz1_falling_log";
            case Sonic3kObjectIds.AIZ_SPIKED_LOG -> "aiz_spiked_log";
            case Sonic3kObjectIds.BLOOMINATOR -> "aiz_bloominator";
            case Sonic3kObjectIds.RHINOBOT -> "aiz_rhinobot";
            case Sonic3kObjectIds.MONKEY_DUDE -> "aiz_monkey_dude";
            case Sonic3kObjectIds.CATERKILLER_JR -> "aiz_caterkiller_jr";
            case Sonic3kObjectIds.BUTTON -> "button";
            case Sonic3kObjectIds.CORK_FLOOR -> "cork_floor_aiz1";
            case Sonic3kObjectIds.FLOATING_PLATFORM -> "floating_platform_aiz1";
            case Sonic3kObjectIds.AIZ_GIANT_RIDE_VINE -> "aiz_ride_vine";
            case Sonic3kObjectIds.SS_ENTRY_RING -> "ss_entry_ring";
            default -> null;
        };
        return key == null ? ObjectPreview.empty() : ObjectPreview.frame(key, 0);
    }
}
