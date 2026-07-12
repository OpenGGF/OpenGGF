package com.openggf.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable zone/act successor plan.
 *
 * <p>Zone indices remain registry identities. Insertions therefore redirect
 * progression without splicing or renumbering the registry snapshot.</p>
 */
@ModApi
public final class ZoneProgressionPlan {
    public static final ZoneProgressionPlan LINEAR = new ZoneProgressionPlan(null, Map.of());

    private final ZoneTopology builtFor;
    private final Map<Integer, Integer> zoneSuccessors;

    private ZoneProgressionPlan(ZoneTopology builtFor, Map<Integer, Integer> zoneSuccessors) {
        this.builtFor = builtFor;
        this.zoneSuccessors = Map.copyOf(zoneSuccessors);
    }

    /** Returns the next zone/act, or the credits terminal. */
    public ProgressionResult next(ZoneTopology topology, int zone, int act) {
        requireCompatible(topology);
        ZoneMetadata metadata = topology.zone(zone);
        if (act < 0 || act >= metadata.actCount()) {
            throw new IllegalArgumentException("Act is outside the topology: " + act);
        }
        if (act + 1 < metadata.actCount()) {
            return new Successor(zone, act + 1);
        }

        Integer redirectedZone = zoneSuccessors.get(zone);
        if (redirectedZone != null) {
            return new Successor(redirectedZone, 0);
        }
        if (metadata.completion() == Completion.TERMINAL || zone + 1 >= topology.zoneCount()) {
            return Credits.INSTANCE;
        }
        return new Successor(zone + 1, 0);
    }

    /** Rejects use with a snapshot other than the one this plan was built for. */
    public void requireCompatible(ZoneTopology topology) {
        Objects.requireNonNull(topology, "topology");
        if (builtFor != null && !builtFor.equals(topology)) {
            throw new IllegalArgumentException("Progression plan was built for a different topology");
        }
    }

    public static Builder builder(ZoneTopology topology) {
        return new Builder(topology);
    }

    @ModApi
    public sealed interface ProgressionResult permits Successor, Credits {
    }

    @ModApi
    public record Successor(int zone, int act) implements ProgressionResult {
        public Successor {
            if (zone < 0 || act < 0) {
                throw new IllegalArgumentException("Successor coordinates must be non-negative");
            }
        }
    }

    @ModApi
    public enum Credits implements ProgressionResult {
        INSTANCE
    }

    @ModApi
    public enum Completion {
        RESULTS_DRIVEN,
        EVENT_CHAINED,
        TERMINAL
    }

    @ModApi
    public record ZoneMetadata(int actCount, Completion completion) {
        public ZoneMetadata {
            if (actCount <= 0) {
                throw new IllegalArgumentException("A zone must contain at least one act");
            }
            Objects.requireNonNull(completion, "completion");
        }
    }

    /** Immutable snapshot of registry progression metadata. */
    @ModApi
    public static final class ZoneTopology {
        private final List<ZoneMetadata> zones;

        private ZoneTopology(List<ZoneMetadata> zones) {
            if (zones.isEmpty()) {
                throw new IllegalArgumentException("A topology must contain at least one zone");
            }
            this.zones = List.copyOf(zones);
        }

        public static ZoneTopology of(List<ZoneMetadata> zones) {
            Objects.requireNonNull(zones, "zones");
            return new ZoneTopology(zones);
        }

        /** Creates the legacy linear topology; only its final zone is terminal. */
        public static ZoneTopology linear(int... actCounts) {
            Objects.requireNonNull(actCounts, "actCounts");
            if (actCounts.length == 0) {
                throw new IllegalArgumentException("A topology must contain at least one zone");
            }
            List<ZoneMetadata> metadata = new ArrayList<>(actCounts.length);
            for (int zone = 0; zone < actCounts.length; zone++) {
                Completion completion = zone == actCounts.length - 1
                        ? Completion.TERMINAL : Completion.RESULTS_DRIVEN;
                metadata.add(new ZoneMetadata(actCounts[zone], completion));
            }
            return new ZoneTopology(metadata);
        }

        public int zoneCount() {
            return zones.size();
        }

        public int actCount(int zone) {
            return zone(zone).actCount();
        }

        public ZoneMetadata zone(int zone) {
            if (zone < 0 || zone >= zones.size()) {
                throw new IllegalArgumentException("Zone is outside the topology: " + zone);
            }
            return zones.get(zone);
        }

        public List<ZoneMetadata> zones() {
            return zones;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof ZoneTopology that && zones.equals(that.zones);
        }

        @Override
        public int hashCode() {
            return zones.hashCode();
        }
    }

    @ModApi
    public static final class Builder {
        private final ZoneTopology topology;
        private final int stockTerminalZone;
        private final Map<Integer, List<Integer>> insertionsByAnchor = new LinkedHashMap<>();
        private final Set<Integer> insertedZones = new HashSet<>();

        private Builder(ZoneTopology topology) {
            this.topology = Objects.requireNonNull(topology, "topology");
            this.stockTerminalZone = firstTerminalZone(topology);
        }

        /**
         * Adds an enabled zone after a results-driven anchor. Repeated calls for
         * one anchor retain call order, which callers supply in frozen effective
         * dependency order.
         */
        public Builder insertAfter(int anchorZone, int insertedZone) {
            if (topology.zone(anchorZone).completion() != Completion.RESULTS_DRIVEN) {
                throw new IllegalArgumentException("Insertions require a results-driven anchor");
            }
            if (topology.zone(insertedZone).completion() != Completion.RESULTS_DRIVEN) {
                throw new IllegalArgumentException("Inserted zones must be results-driven");
            }
            if (anchorZone >= stockTerminalZone) {
                throw new IllegalArgumentException("Insertion anchors must be stock zones before the terminal boundary");
            }
            if (insertedZone <= stockTerminalZone) {
                throw new IllegalArgumentException("Inserted zones must be appended after the stock terminal boundary");
            }
            if (insertedZones.contains(anchorZone)) {
                throw new IllegalArgumentException("An inserted zone cannot also be an anchor: " + anchorZone);
            }
            if (insertionsByAnchor.containsKey(insertedZone)) {
                throw new IllegalArgumentException("An anchor cannot also be inserted: " + insertedZone);
            }
            if (!insertedZones.add(insertedZone)) {
                throw new IllegalArgumentException("Duplicate inserted zone identity: " + insertedZone);
            }
            insertionsByAnchor.computeIfAbsent(anchorZone, ignored -> new ArrayList<>()).add(insertedZone);
            return this;
        }

        public ZoneProgressionPlan build() {
            Map<Integer, Integer> successors = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> entry : insertionsByAnchor.entrySet()) {
                int anchor = entry.getKey();
                List<Integer> inserted = entry.getValue();
                successors.put(anchor, inserted.getFirst());
                for (int i = 0; i + 1 < inserted.size(); i++) {
                    successors.put(inserted.get(i), inserted.get(i + 1));
                }
                int stockSuccessor = anchor + 1;
                if (stockSuccessor < topology.zoneCount()) {
                    successors.put(inserted.getLast(), stockSuccessor);
                }
            }
            requireAcyclic(successors);
            return new ZoneProgressionPlan(topology, successors);
        }

        private static int firstTerminalZone(ZoneTopology topology) {
            for (int zone = 0; zone < topology.zoneCount(); zone++) {
                if (topology.zone(zone).completion() == Completion.TERMINAL) {
                    return zone;
                }
            }
            throw new IllegalArgumentException("Insertion topology requires a stock terminal boundary");
        }

        private void requireAcyclic(Map<Integer, Integer> explicitSuccessors) {
            byte[] states = new byte[topology.zoneCount()];
            for (int start = 0; start < topology.zoneCount(); start++) {
                int zone = start;
                List<Integer> path = new ArrayList<>();
                while (zone >= 0 && states[zone] == 0) {
                    states[zone] = 1;
                    path.add(zone);
                    Integer redirected = explicitSuccessors.get(zone);
                    if (redirected != null) {
                        zone = redirected;
                    } else if (topology.zone(zone).completion() == Completion.TERMINAL
                            || zone + 1 >= topology.zoneCount()) {
                        zone = -1;
                    } else {
                        zone++;
                    }
                }
                if (zone >= 0 && states[zone] == 1) {
                    throw new IllegalArgumentException("Insertion graph contains a progression cycle");
                }
                for (int visited : path) {
                    states[visited] = 2;
                }
            }
        }
    }
}
