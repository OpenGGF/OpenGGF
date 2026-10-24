package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Native per-placement, per-player plane state, including rewind; owned by ObjectManager. */
final class ObjectPlaneSwitcherController {
    private static final Logger LOGGER = Logger.getLogger(ObjectPlaneSwitcherController.class.getName());
    private static final int[] HALF_SPANS = new int[]{0x20, 0x40, 0x80, 0x100};
    private static final int MASK_SIZE = 0x03;
    private static final int MASK_HORIZONTAL = 0x04;
    private static final int MASK_PATH_SIDE1 = 0x08;
    private static final int MASK_PATH_SIDE0 = 0x10;
    private static final int MASK_PRIORITY_SIDE1 = 0x20;
    private static final int MASK_PRIORITY_SIDE0 = 0x40;
    private static final int MASK_GROUNDED_ONLY = 0x80;

    private final ObjectPlacementController placement;
    private final int objectId;
    private final PlaneSwitcherConfig config;
    private final Map<ObjectSpawn, PlaneSwitcherState> states = new HashMap<>();

    ObjectPlaneSwitcherController(ObjectPlacementController placement, int objectId, PlaneSwitcherConfig config) {
        this.placement = placement;
        this.objectId = objectId & 0xFF;
        this.config = config;
    }

    void reset() {
        states.clear();
    }

    com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot captureRewindState() {
        List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry> entries =
                new ArrayList<>();
        for (Map.Entry<ObjectSpawn, PlaneSwitcherState> entry : states.entrySet()) {
            PlaneSwitcherState state = entry.getValue();
            List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry>
                    playerSides = new ArrayList<>();
            for (Map.Entry<PlayableEntity, Byte> sideEntry : state.sideStates.entrySet()) {
                playerSides.add(
                        new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry(
                                sideEntry.getKey(),
                                sideEntry.getValue() & 0xFF));
            }
            playerSides.sort(Comparator
                    .comparing((com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry e)
                            -> stablePlayerKey(e.player()))
                    .thenComparingInt(e -> e.sideState()));
            entries.add(new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry(
                    entry.getKey(),
                    state.getLastSideState(),
                    state.hasLastSideState(),
                    List.copyOf(playerSides)));
        }
        entries.sort(Comparator.comparing(entry -> ObjectRewindOrdering.spawnKey(entry.spawn())));
        return new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot(
                List.copyOf(entries));
    }

    void restoreRewindState(
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot snapshot) {
        states.clear();
        if (snapshot == null) {
            return;
        }
        for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry entry
                : snapshot.entries()) {
            ObjectSpawn spawn = entry.spawn();
            if (spawn == null) {
                continue;
            }
            PlaneSwitcherState state = new PlaneSwitcherState(decodeHalfSpan(spawn.subtype()));
            if (entry.hasLastSideState()) {
                state.setLastSideState(entry.lastSideState());
            }
            for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry side
                    : entry.playerSides()) {
                if (side.player() != null) {
                    state.setSideState(side.player(), side.sideState());
                }
            }
            states.put(spawn, state);
        }
    }

    private static String stablePlayerKey(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            return sprite.getCode();
        }
        return player == null ? "" : player.getClass().getName();
    }

    void update(PlayableEntity player, java.util.function.Predicate<ObjectSpawn> skipSpawn) {
        if (placement == null || player == null || config == null) {
            return;
        }
        Collection<ObjectSpawn> active = placement.getActiveSpawns();
        if (active.isEmpty()) {
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        for (ObjectSpawn spawn : active) {
            if (spawn.objectId() != objectId || skipSpawn.test(spawn)) {
                continue;
            }

            updateSpawn(spawn, player);
        }

        states.keySet().removeIf(spawn -> spawn.objectId() == objectId && !active.contains(spawn));
    }

    void updateSpawn(ObjectSpawn spawn, PlayableEntity player) {
        if (spawn == null || player == null || spawn.objectId() != objectId) {
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        int subtype = spawn.subtype();
        PlaneSwitcherState state = states.computeIfAbsent(spawn,
                key -> new PlaneSwitcherState(decodeHalfSpan(subtype)));

        boolean horizontal = isHorizontal(subtype);
        int sideNow = horizontal
                ? (playerY >= spawn.y() ? 1 : 0)
                : (playerX >= spawn.x() ? 1 : 0);
        int previousSide = state.getSideState(player);
        if (previousSide < 0) {
            state.setSideState(player, sideNow);
            state.setLastSideState(sideNow);
            return;
        }

        int half = state.halfSpanPixels;
        boolean inSpan = horizontal
                ? (playerX >= spawn.x() - half && playerX < spawn.x() + half)
                : (playerY >= spawn.y() - half && playerY < spawn.y() + half);
        boolean groundedGate = onlySwitchWhenGrounded(subtype) && player.getAir();

        if (inSpan && !groundedGate && sideNow != previousSide) {
            boolean skipCollisionChange = (spawn.renderFlags() & 0x1) != 0;
            if (!skipCollisionChange) {
                int path = decodePath(subtype, sideNow);
                player.setLayer((byte) path);
                if (path == 0) {
                    player.setTopSolidBit(config.getPath0TopSolidBit());
                    player.setLrbSolidBit(config.getPath0LrbSolidBit());
                } else {
                    player.setTopSolidBit(config.getPath1TopSolidBit());
                    player.setLrbSolidBit(config.getPath1LrbSolidBit());
                }
                LOGGER.fine(
                        () ->
                                String.format(
                                        "PlaneSwitcher path=%d: player(%d,%d) obj(%d,%d)"
                                                + " sub=0x%02X side=%d→%d air=%b mode=%s",
                                        path,
                                        player.getCentreX(),
                                        player.getCentreY(),
                                        spawn.x(),
                                        spawn.y(),
                                        subtype,
                                        previousSide,
                                        sideNow,
                                        player.getAir(),
                                        player.getGroundMode()));
            }
            boolean highPriority = decodePriority(subtype, sideNow);
            player.setHighPriority(highPriority);
        }

        state.setSideState(player, sideNow);
        state.setLastSideState(sideNow);
    }

    int getSideState(ObjectSpawn spawn) {
        PlaneSwitcherState state = states.get(spawn);
        if (state == null || !state.hasLastSideState()) {
            return -1;
        }
        return state.getLastSideState();
    }

    static int decodeHalfSpan(int subtype) {
        int index = subtype & MASK_SIZE;
        if (index < 0 || index >= HALF_SPANS.length) {
            index = 0;
        }
        return HALF_SPANS[index];
    }

    static boolean isHorizontal(int subtype) {
        return (subtype & MASK_HORIZONTAL) != 0;
    }

    static int decodePath(int subtype, int side) {
        int mask = side == 1 ? MASK_PATH_SIDE1 : MASK_PATH_SIDE0;
        return (subtype & mask) != 0 ? 1 : 0;
    }

    static boolean decodePriority(int subtype, int side) {
        int mask = side == 1 ? MASK_PRIORITY_SIDE1 : MASK_PRIORITY_SIDE0;
        return (subtype & mask) != 0;
    }

    static boolean onlySwitchWhenGrounded(int subtype) {
        return (subtype & MASK_GROUNDED_ONLY) != 0;
    }

    static char formatLayer(byte layer) {
        return layer == 0 ? 'A' : 'B';
    }

    static char formatPriority(boolean highPriority) {
        return highPriority ? 'H' : 'L';
    }

    private static final class PlaneSwitcherState {
        private final int halfSpanPixels;
        private final IdentityHashMap<PlayableEntity, Byte> sideStates = new IdentityHashMap<>();
        private byte lastSideState = 0;
        private boolean hasLastSideState = false;

        private PlaneSwitcherState(int halfSpanPixels) {
            this.halfSpanPixels = halfSpanPixels;
        }

        private int getSideState(PlayableEntity player) {
            Byte value = sideStates.get(player);
            return value != null ? value & 0xFF : -1;
        }

        private void setSideState(PlayableEntity player, int sideState) {
            sideStates.put(player, (byte) sideState);
        }

        private void setLastSideState(int sideState) {
            this.lastSideState = (byte) sideState;
            this.hasLastSideState = true;
        }

        private boolean hasLastSideState() {
            return hasLastSideState;
        }

        private int getLastSideState() {
            return lastSideState & 0xFF;
        }
    }
}
