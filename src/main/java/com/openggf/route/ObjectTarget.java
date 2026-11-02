package com.openggf.route;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Which object a goal means. Resolution reads only the sample's object list; there is no Java
 * pointer and no slot order.
 */
public sealed interface ObjectTarget {
    /** The live instance of a ROM placement (rebinding after a respawn). */
    record Placement(PlacementKey key) implements ObjectTarget {
        public Placement {
            Objects.requireNonNull(key, "key");
        }
    }

    /** One timeline instance; it expires on removal or a timeline reset. */
    record Instance(InstanceKey key) implements ObjectTarget {
        public Instance {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * The live object of {@code typeKey} with {@code capability} nearest the player (ties by
     * instance identity), inside {@code region} when given. Dynamic objects such as spawned
     * bosses are selected this way.
     */
    record Typed(String typeKey, Capability capability, Optional<Region> region) implements ObjectTarget {
        public Typed {
            Objects.requireNonNull(typeKey, "typeKey");
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(region, "region");
        }
    }

    /** A placement target from fields an editor knows. */
    static ObjectTarget placement(int zone, int act, int layoutIndex, int objectId, int subtype) {
        return new Placement(new PlacementKey(zone, act, layoutIndex, objectId, subtype));
    }

    static ObjectTarget typed(String typeKey, Capability capability) {
        return new Typed(typeKey, capability, Optional.empty());
    }

    /** The object this target names in {@code sample}, if live. */
    default Optional<ObjectView> resolve(RouteSample sample, PlayerSlot near) {
        return switch (this) {
            case Placement p -> sample.objects().stream().filter(o -> o.placement().equals(Optional.of(p.key()))).findFirst();
            case Instance i -> sample.objects().stream().filter(o -> o.instance().equals(i.key())).findFirst();
            case Typed t -> {
                if (near == PlayerSlot.P2 && sample.p2().isEmpty()) yield Optional.empty();
                PlayerView player = sample.player(near);
                yield sample.objects().stream()
                        .filter(o -> o.typeKey().equals(t.typeKey()) && o.has(t.capability()))
                        .filter(o -> t.region().map(r -> r.contains(o.centreX(), o.centreY())).orElse(true))
                        .min(Comparator.comparingLong((ObjectView o) -> Math.abs((long) o.centreX() - player.centreX())
                                + Math.abs((long) o.centreY() - player.centreY()))
                                .thenComparing(o -> o.instance().toString()));
            }
        };
    }
}
