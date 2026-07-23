package com.openggf.audio.presentation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A fixed-capacity command ledger with structural admission reserve.
 */
public final class AudioPresentationCommandQueue {
    public static final int CAPACITY = 256;
    public static final int STRUCTURAL_RESERVE = 32;
    private static final int NORMAL_CAPACITY = CAPACITY - STRUCTURAL_RESERVE;

    private final AudioPresentationCommand[] entries =
            new AudioPresentationCommand[CAPACITY];
    private final BooleanSupplier renderingActive;
    private int size;

    public AudioPresentationCommandQueue() {
        this(() -> false);
    }

    public AudioPresentationCommandQueue(BooleanSupplier renderingActive) {
        this.renderingActive = Objects.requireNonNull(renderingActive, "renderingActive");
    }

    public void submit(AudioPresentationCommand command,
                       BooleanSupplier ownerThreadBoundary,
                       Consumer<AudioPresentationCommand> synchronousApply) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(ownerThreadBoundary, "ownerThreadBoundary");
        Objects.requireNonNull(synchronousApply, "synchronousApply");
        assertNotRendering();

        if (coalesce(command)) {
            return;
        }

        if (command.structural()) {
            admitStructural(command, ownerThreadBoundary, synchronousApply);
            return;
        }

        if (size >= NORMAL_CAPACITY) {
            if (command.droppableSampleStart()) {
                return;
            }
            drainAtOwnerBoundary(ownerThreadBoundary, synchronousApply);
        }
        entries[size++] = command;
    }

    public void applyPending(Consumer<AudioPresentationCommand> applier) {
        Objects.requireNonNull(applier, "applier");
        assertNotRendering();
        drain(applier);
    }

    public int size() {
        return size;
    }

    private void admitStructural(AudioPresentationCommand command,
                                 BooleanSupplier ownerThreadBoundary,
                                 Consumer<AudioPresentationCommand> synchronousApply) {
        if (size == CAPACITY && !removeOldestDroppableSampleStart()) {
            drainAtOwnerBoundary(ownerThreadBoundary, synchronousApply);
        }
        entries[size++] = command;
    }

    private boolean coalesce(AudioPresentationCommand command) {
        Object key = command.coalescingKey();
        if (key == null) {
            return false;
        }
        for (int index = size - 1; index >= 0; index--) {
            AudioPresentationCommand pending = entries[index];
            Object pendingKey = pending.coalescingKey();
            if (pendingKey == null) {
                break;
            }
            if (pending.getClass() == command.getClass()
                    && Objects.equals(pendingKey, key)) {
                entries[index] = command;
                return true;
            }
        }
        return false;
    }

    private boolean removeOldestDroppableSampleStart() {
        for (int index = 0; index < size; index++) {
            if (entries[index].droppableSampleStart()) {
                int remaining = size - index - 1;
                if (remaining > 0) {
                    System.arraycopy(entries, index + 1, entries, index, remaining);
                }
                entries[--size] = null;
                return true;
            }
        }
        return false;
    }

    private void drainAtOwnerBoundary(BooleanSupplier ownerThreadBoundary,
                                      Consumer<AudioPresentationCommand> synchronousApply) {
        if (!ownerThreadBoundary.getAsBoolean()) {
            throw new IllegalStateException(
                    "full audio command queue may drain only at its owner boundary");
        }
        assertNotRendering();
        drain(synchronousApply);
    }

    private void drain(Consumer<AudioPresentationCommand> applier) {
        while (size > 0) {
            AudioPresentationCommand command = entries[0];
            int remaining = size - 1;
            if (remaining > 0) {
                System.arraycopy(entries, 1, entries, 0, remaining);
            }
            entries[--size] = null;
            applier.accept(command);
        }
    }

    private void assertNotRendering() {
        if (renderingActive.getAsBoolean()) {
            throw new IllegalStateException(
                    "audio commands cannot be submitted or drained during rendering");
        }
    }
}
