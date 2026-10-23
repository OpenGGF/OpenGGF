package com.openggf.level.objects.authored;

import com.openggf.game.ModApi;
import com.openggf.game.MusicReference;
import com.openggf.io.ModAssetRoot;
import java.util.Objects;

/** Typed persisted properties for host-owned authoring gizmos. Geometry is in world pixels. */
@ModApi
public sealed interface AuthoredObjectProperties {
    int width();
    int height();

    default String objectKey() {
        return switch (this) {
            case CameraZone ignored -> "openggf:camera-zone";
            case MusicZone ignored -> "openggf:music-zone";
            case Warp ignored -> "openggf:warp";
            case StageplayTrigger ignored -> "openggf:stageplay-trigger";
        };
    }

    @ModApi enum ReleaseCondition { NONE, CROSS_X, CROSS_Y }
    @ModApi enum Trigger { PROXIMITY, BUTTON }

    /** NONE releases on leaving the region; CROSS conditions latch until crossing the positive axis threshold. */
    @ModApi record CameraZone(int width, int height, int minX, int maxX, int minY, int maxY,
                             ReleaseCondition releaseCondition, int releaseAt) implements AuthoredObjectProperties {
        public CameraZone {
            geometry(width, height);
            if (minX < 0 || maxX > 0xffff || minX > maxX || minY < 0 || maxY > 0xfff || minY > maxY)
                throw new IllegalArgumentException("Camera bounds must be ordered native world coordinates");
            Objects.requireNonNull(releaseCondition, "releaseCondition");
            if (releaseAt < 0 || releaseAt > (releaseCondition == ReleaseCondition.CROSS_Y ? 0xfff : 0xffff))
                throw new IllegalArgumentException("Camera release threshold is outside native world coordinates");
        }
    }

    @ModApi record MusicZone(int width, int height, MusicReference music, boolean once) implements AuthoredObjectProperties {
        public MusicZone {
            geometry(width, height);
            Objects.requireNonNull(music, "music");
            if (music instanceof MusicReference.Stock stock && (stock.musicId() < 0 || stock.musicId() > 0xffff))
                throw new IllegalArgumentException("Stock music ID must be 0..65535");
        }
    }

    /** The lower placement ID is the source for one-way links. A link has exactly two endpoints. */
    @ModApi record Warp(int width, int height, String link, boolean twoWay) implements AuthoredObjectProperties {
        public Warp {
            geometry(width, height);
            if (link == null || !link.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("Warp link must be 1..64 letters, digits, dots, underscores or hyphens");
        }
    }

    /** BUTTON means a fresh Up press while inside the region. The resource is relative to the owning mod root. */
    @ModApi record StageplayTrigger(int width, int height, String resource, Trigger trigger,
                                    boolean blocking, boolean once, boolean consume) implements AuthoredObjectProperties {
        public StageplayTrigger {
            geometry(width, height);
            resource = ModAssetRoot.requireNormalizedEntry(resource);
            if (resource.length() > 512) throw new IllegalArgumentException("Stageplay resource path must be at most 512 characters");
            if (!resource.endsWith(".stageplay")) throw new IllegalArgumentException("Stageplay resource must end in .stageplay");
            Objects.requireNonNull(trigger, "trigger");
        }
    }

    private static void geometry(int width, int height) {
        if (width < 1 || width > 0xffff || height < 1 || height > 0x1000)
            throw new IllegalArgumentException("Gizmo width must be 1..65535 and height must be 1..4096");
    }
}
