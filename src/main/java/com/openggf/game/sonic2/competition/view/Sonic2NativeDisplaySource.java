package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot;

/** Explicit native display fields for owners whose ordinary draw is not one ROM mapping frame. */
public interface Sonic2NativeDisplaySource {
    /** Null means this admitted owner emits no mapping this frame. */
    ObjectSlot nativeDisplaySlot();

    /** An actual allocator-owned SST identity, never a synthetic render index. */
    record DisplaySlot(int slot, int priorityBucket, ObjectSlot occupant) {
        public DisplaySlot {
            if(slot<0) throw new IllegalArgumentException("Native display requires an allocated SST slot");
            if(priorityBucket<0 || priorityBucket>7) throw new IllegalArgumentException("Native display bucket");
            java.util.Objects.requireNonNull(occupant);
        }
    }

    /** Ordered independently admitted/cullable native SST owners. */
    default java.util.List<DisplaySlot> nativeDisplaySlots() {
        ObjectSlot display=nativeDisplaySlot();
        if(display==null) return java.util.List.of();
        if(!(this instanceof com.openggf.level.objects.AbstractObjectInstance owner))
            throw new IllegalStateException("Native display owner must carry an actual SST identity");
        return java.util.List.of(new DisplaySlot(owner.getSlotIndex(),owner.getPriorityBucket(),display));
    }
}
