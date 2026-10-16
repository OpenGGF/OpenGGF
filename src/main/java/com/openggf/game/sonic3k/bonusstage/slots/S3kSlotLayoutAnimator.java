package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * 32-entry animation slot table matching ROM sub_4B592 (lines 98392-98491).
 * Drives tile animations in the slot stage layout grid.
 */
public final class S3kSlotLayoutAnimator {

    private static final int MAX_SLOTS = 32;

    // Type 1: ring sparkle (byte_4B5EC)
    private static final int RING_SPARKLE_DELAY = 5;
    private static final byte RING_SPARKLE_RESTORE = 0; // ring consumed

    // Type 2: bumper bounce (byte_4B622)
    private static final int BUMPER_BOUNCE_DELAY = 1;
    private static final byte BUMPER_BOUNCE_RESTORE = 5; // restore bumper tile

    // Type 4: R-label/spike animation (byte_4B656)
    private static final int SPIKE_ANIM_DELAY = 7;
    private static final byte SPIKE_ANIM_RESTORE = 6; // restore spike tile

    private final S3kSlotResources suppliedResources;

    private final AnimSlot[] slots = new AnimSlot[MAX_SLOTS];

    public S3kSlotLayoutAnimator() { this(null); }

    public S3kSlotLayoutAnimator(S3kSlotResources resources) {
        suppliedResources = resources;
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new AnimSlot();
        }
    }

    private S3kSlotResources resources() {
        return suppliedResources != null ? suppliedResources : S3kSlotResources.fromActiveRom();
    }

    private static byte[] terminated(byte[] frames) {
        return java.util.Arrays.copyOf(frames, frames.length + 1);
    }

    /** Queue ring sparkle animation at the given layout index */
    public boolean queueRingSparkle(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, terminated(resources().ringFrames()), RING_SPARKLE_DELAY, RING_SPARKLE_RESTORE);
    }

    /** Queue bumper bounce animation at the given layout index */
    public boolean queueBumperBounce(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, terminated(resources().bumperFrames()), BUMPER_BOUNCE_DELAY, BUMPER_BOUNCE_RESTORE);
    }

    /** Queue spike/R-label animation at the given layout index */
    public boolean queueSpikeAnimation(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, terminated(resources().spikeFrames()), SPIKE_ANIM_DELAY, SPIKE_ANIM_RESTORE);
    }

    /** Tick all active animation slots, updating layout tiles */
    public void tick(byte[] layout) {
        for (AnimSlot slot : slots) {
            if (!slot.active) continue;
            slot.tick(layout);
        }
    }

    /** Returns number of active animation slots */
    public int activeCount() {
        int count = 0;
        for (AnimSlot slot : slots) {
            if (slot.active) count++;
        }
        return count;
    }

    private boolean allocate(byte[] layout, int layoutIndex, byte[] frames, int delay, byte restoreTile) {
        for (AnimSlot slot : slots) {
            if (!slot.active) {
                slot.start(layoutIndex, frames, delay, restoreTile);
                // Apply first frame immediately
                if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length && frames.length > 0) {
                    layout[layoutIndex] = frames[0];
                }
                return true;
            }
        }
        return false; // all slots full
    }

    java.util.List<com.openggf.game.rewind.schema.RewindObjectStateBlob> captureSlotRewindState() {
        var result=new java.util.ArrayList<com.openggf.game.rewind.schema.RewindObjectStateBlob>(slots.length);
        for (var slot:slots) result.add(com.openggf.game.rewind.schema.CompactFieldCapturer.capture(slot));
        return java.util.List.copyOf(result);
    }
    void restoreSlotRewindState(java.util.List<com.openggf.game.rewind.schema.RewindObjectStateBlob> states) {
        if (states.size()!=slots.length) throw new IllegalArgumentException("Slot animation pool size changed");
        for (int i=0;i<slots.length;i++) com.openggf.game.rewind.schema.CompactFieldCapturer.restore(slots[i],states.get(i));
    }

    private static final class AnimSlot {
        boolean active;
        int layoutIndex;
        byte[] frames;
        int delay;
        int timer;
        int frameIndex;
        byte restoreTile;

        void start(int layoutIndex, byte[] frames, int delay, byte restoreTile) {
            this.active = true;
            this.layoutIndex = layoutIndex;
            this.frames = frames;
            this.delay = delay;
            this.timer = delay;
            this.frameIndex = 0;
            this.restoreTile = restoreTile;
        }

        void tick(byte[] layout) {
            if (--timer > 0) return;
            timer = delay;
            frameIndex++;

            if (frameIndex >= frames.length || frames[frameIndex] == 0) {
                // Animation complete: restore tile and free slot
                if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                    layout[layoutIndex] = restoreTile;
                }
                active = false;
                return;
            }

            // Apply current frame
            if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = frames[frameIndex];
            }
        }
    }
}
