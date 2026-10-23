package com.openggf.editor.properties;

import com.openggf.game.MusicReference;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.authored.AuthoredObjectProperties;
import java.util.HashMap;

/** Safe initial properties; selecting Warp places its source and then its matching exit. */
public final class EditorGizmoPlacements {
    private EditorGizmoPlacements() { }
    public static ObjectSpawn initialize(ObjectSpawn spawn, Level level) {
        int width = Math.min(64, level.getMap().getWidth() * level.getBlockPixelSize() - spawn.x());
        int height = Math.min(64, level.getMap().getHeight() * level.getBlockPixelSize() - spawn.y());
        if (width < 1 || height < 1) throw new IllegalArgumentException("Place the gizmo inside the level canvas");
        AuthoredObjectProperties properties = switch (spawn.objectKey()) {
            case "openggf:camera-zone" -> new AuthoredObjectProperties.CameraZone(width, height,
                    level.getMinX(), level.getMaxX(), level.getMinY(), level.getMaxY(), AuthoredObjectProperties.ReleaseCondition.NONE, 0);
            case "openggf:music-zone" -> new AuthoredObjectProperties.MusicZone(width, height, MusicReference.stock(0x81), true);
            case "openggf:warp" -> {
                var counts = new HashMap<String, Integer>();
                for (var object : level.getObjects()) if (object.authoredProperties() instanceof AuthoredObjectProperties.Warp warp)
                    counts.merge(warp.link(), 1, Integer::sum);
                String link = counts.entrySet().stream().filter(entry -> entry.getValue() == 1)
                        .map(java.util.Map.Entry::getKey).sorted().findFirst().orElse(null);
                boolean twoWay = true;
                if (link != null) {
                    String selected = link;
                    twoWay = level.getObjects().stream().map(ObjectSpawn::authoredProperties)
                            .filter(p -> p instanceof AuthoredObjectProperties.Warp w && w.link().equals(selected))
                            .map(p -> ((AuthoredObjectProperties.Warp) p).twoWay()).findFirst().orElse(true);
                } else { int id = 1; while (counts.containsKey("warp-" + id)) id++; link = "warp-" + id; }
                yield new AuthoredObjectProperties.Warp(width, height, link, twoWay);
            }
            case "openggf:stageplay-trigger" -> new AuthoredObjectProperties.StageplayTrigger(width, height,
                    "scenes/scene.stageplay", AuthoredObjectProperties.Trigger.PROXIMITY, true, true, false);
            default -> null;
        };
        return properties == null ? spawn : withProperties(spawn, properties);
    }
    public static ObjectSpawn withProperties(ObjectSpawn spawn, AuthoredObjectProperties properties) {
        return new ObjectSpawn(spawn.x(), spawn.y(), spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                spawn.respawnTracked(), spawn.rawYWord(), spawn.layoutIndex(), spawn.ownerModId(), spawn.objectKey(), properties);
    }
}
