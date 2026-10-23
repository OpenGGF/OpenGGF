package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.objects.ObjectSpawn;

/** Static native placement poses selected from each production object's art owner. */
final class Sonic1ObjectPreviews {
    private Sonic1ObjectPreviews() { }
    static ObjectPreview preview(ObjectSpawn spawn) {
        int id = spawn.objectId();

        if (id == Sonic1ObjectIds.MONITOR) {
            // Production monitor animations settle on subtype+2; frame 11 is the broken shell.
            int type = spawn.subtype() & 15;
            return ObjectPreview.frame("monitor", type > 0 && type < 9 ? type + 2 : type == 9 ? 11 : 0);
        }
        if (id == Sonic1ObjectIds.SPRING) {
            int type = (spawn.subtype() >> 4) & 7;
            boolean yellow = (spawn.subtype() & 2) != 0;
            String direction = type == 1 ? "horizontal" : "vertical";
            String key = "spring_" + direction + (yellow ? "_red" : "");
            return new ObjectPreview(java.util.List.of(new ObjectPreview.Part(key, 0, 0, 0, false, type == 2 || type == 4)));
        }

        // Map_Newt's frame 0 is the materializing stipple; frame 1 is its readable standing pose.
        if (id == Sonic1ObjectIds.NEWTRON) {
            // Newt_Type1's make_art_tile(ArtTile_Newtron,1,0), also used by the production draw call.
            return new ObjectPreview(java.util.List.of(new ObjectPreview.Part("newtron", 1, 0, 0, false, false,
                    spawn.subtype() != 0 ? 1 : -1)));
        }
        if (id == Sonic1ObjectIds.SPIKES) return ObjectPreview.frame("spike", Math.min(5, (spawn.subtype() >> 4) & 15));
        if (id == Sonic1ObjectIds.PLATFORM) return ObjectPreview.frame("platform", (spawn.subtype() & 15) == 10 ? 1 : 0);
        if (id == Sonic1ObjectIds.SCENERY) return ObjectPreview.frame("bridge", 1);
        if (id == Sonic1ObjectIds.BRIDGE) {
            int count = Math.clamp(spawn.subtype() & 31, 1, 32);
            var parts = new java.util.ArrayList<ObjectPreview.Part>();
            for (int i = 0; i < count; i++) parts.add(new ObjectPreview.Part("bridge", 0, (i - count / 2) * 16, 0, false, false));
            return new ObjectPreview(parts);
        }
        String key = switch (id) {
            case Sonic1ObjectIds.RING -> "ring";
            case Sonic1ObjectIds.MONITOR -> "monitor";
            case Sonic1ObjectIds.SPRING -> "spring_vertical";
            case Sonic1ObjectIds.SPIKES -> "spike";
            case Sonic1ObjectIds.LAMPPOST -> "checkpoint";
            case Sonic1ObjectIds.SIGNPOST -> "signpost";
            case Sonic1ObjectIds.ROCK -> "rock";
            case Sonic1ObjectIds.BRIDGE -> "bridge";
            case Sonic1ObjectIds.SCENERY -> "scenery";
            case Sonic1ObjectIds.PLATFORM -> "platform";
            case Sonic1ObjectIds.COLLAPSING_LEDGE -> "collapsing_ledge";
            case Sonic1ObjectIds.BREAKABLE_WALL -> "breakable_wall";
            case Sonic1ObjectIds.EDGE_WALLS -> "ghz_edge_wall";
            case Sonic1ObjectIds.CHOPPER -> "chopper";
            case Sonic1ObjectIds.BUZZ_BOMBER -> "buzz_bomber";
            case Sonic1ObjectIds.CRABMEAT -> "crabmeat";
            case Sonic1ObjectIds.MOTOBUG -> "motobug";
            case Sonic1ObjectIds.NEWTRON -> "newtron";
            case Sonic1ObjectIds.CATERKILLER -> "caterkiller";
            case Sonic1ObjectIds.BATBRAIN -> "batbrain";
            case Sonic1ObjectIds.ROLLER -> "roller";
            case Sonic1ObjectIds.YADRIN -> "yadrin";
            case Sonic1ObjectIds.JAWS -> "jaws";
            case Sonic1ObjectIds.BURROBOT -> "burrobot";
            case Sonic1ObjectIds.ORBINAUT -> "orbinaut";
            case Sonic1ObjectIds.BOMB -> "bomb";
            case Sonic1ObjectIds.BALL_HOG -> "ball_hog";
            case Sonic1ObjectIds.GIANT_RING -> "giant_ring";
            case Sonic1ObjectIds.BUTTON -> "button";
            default -> null;
        };
        return key == null ? ObjectPreview.empty() : ObjectPreview.frame(key, 0);
    }
}
