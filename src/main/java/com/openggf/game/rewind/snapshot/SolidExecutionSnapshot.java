package com.openggf.game.rewind.snapshot;

import com.openggf.game.solid.ContactKind;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;

import java.util.List;

/**
 * Snapshot record for the solid execution registry.
 *
 * <p>{@code DefaultSolidExecutionRegistry} keeps previous-frame standing state
 * keyed by live object/player references. Rewind serializes only the stable
 * identities needed to rebuild that map after {@code object-manager} has
 * re-instantiated placed and dynamic objects: stable object and player IDs.
 * Spawn indices alone lose promoted collapsing platforms and cannot distinguish
 * multiple children from one placement.
 */
public record SolidExecutionSnapshot(List<PreviousStandingEntry> previousStanding) {
    public SolidExecutionSnapshot {
        previousStanding = List.copyOf(previousStanding);
    }

    public record PreviousStandingEntry(
            ObjectRefId objectId,
            PlayerRefId playerId,
            ContactKind kind,
            boolean standing,
            boolean pushing) {}
}
