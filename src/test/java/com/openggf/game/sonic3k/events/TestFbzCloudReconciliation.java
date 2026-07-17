package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzCloudReconciliation {
    @Test
    void resolvedCloudIdsRemainStableWithoutRecreation() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        Set<ObjectRefId> live = new HashSet<>(events.getCloudRewindIds());
        List<Integer> recreated = new ArrayList<>();

        events.reconcileCloudsAfterObjectRestore(live::contains,
                requests -> { fail("batch must not be opened when all IDs resolve"); return null; });

        assertTrue(recreated.isEmpty());
        assertEquals(10, events.getCloudRewindIds().size());
    }

    @Test
    void missingPreCleanupCloudsRecreateInOriginalAllocationOrder() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        Set<ObjectRefId> live = new HashSet<>(Set.of(events.getCloudRewindId(1), events.getCloudRewindId(8)));
        List<Integer> recreated = new ArrayList<>();

        int[] refreshes = {0};
        FbzCloudIdentityResolver resolver = new FbzCloudIdentityResolver() {
            @Override public boolean isLive(ObjectRefId id) { return live.contains(id); }
            @Override public void refresh() { refreshes[0]++; }
        };
        events.reconcileCloudsAfterObjectRestore(resolver,
                batchFactory(live, recreated, -1, false, false));

        assertEquals(List.of(0, 2, 3, 4, 5, 6, 7, 9), recreated);
        assertEquals(ObjectRefId.dynamic(20, 1, 9), events.getCloudRewindId(9));
        assertEquals(1, refreshes[0], "one batch refresh replaces per-cloud identity recapture");
    }

    @Test
    void failedOrAbsentPreCleanupRecreatorFailsVisibly() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        assertThrows(IllegalStateException.class,
                () -> events.reconcileCloudsAfterObjectRestore(id -> false, null));
        assertThrows(IllegalStateException.class,
                () -> events.reconcileCloudsAfterObjectRestore(id -> false,
                        batchFactory(new HashSet<>(), new ArrayList<>(), 0, false, true)));
        List<ObjectRefId> beforeIds = List.copyOf(events.getCloudRewindIds());
        byte[] beforeBytes = capture(events);
        Set<ObjectRefId> unresolvedLive = new HashSet<>();
        assertThrows(IllegalStateException.class,
                () -> events.reconcileCloudsAfterObjectRestore(unresolvedLive::contains,
                        batchFactory(unresolvedLive, new ArrayList<>(), -1, true, false)),
                "returning a different ID must fail even when it resolves live");
        assertEquals(beforeIds, events.getCloudRewindIds());
        assertArrayEquals(beforeBytes, capture(events));
        assertTrue(unresolvedLive.isEmpty(), "rollback must remove recreated live objects");
    }

    @Test
    void laterFailureDoesNotCommitEarlierSuccessfulReboundIds() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        List<ObjectRefId> beforeIds = List.copyOf(events.getCloudRewindIds());
        byte[] beforeBytes = capture(events);
        Set<ObjectRefId> live = new HashSet<>(beforeIds);
        live.remove(beforeIds.get(0));
        live.remove(beforeIds.get(2));

        assertThrows(IllegalStateException.class, () -> events.reconcileCloudsAfterObjectRestore(
                live::contains,
                batchFactory(live, new ArrayList<>(), 2, false, true)));

        assertEquals(beforeIds, events.getCloudRewindIds(),
                "no staged rebound ID may commit when a later slot fails");
        assertArrayEquals(beforeBytes, capture(events),
                "authoritative rewind bytes must remain identical after failed reconciliation");
        assertFalse(live.contains(beforeIds.get(0)), "rollback model starts with slot zero absent");
    }

    @Test
    void terminalCleanupLeavesCapturedCloudSlotsAbsent() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        events.setCloudCleanupTerminal(true);
        List<Integer> recreated = new ArrayList<>();

        events.reconcileCloudsAfterObjectRestore(id -> false,
                batchFactory(new HashSet<>(), recreated, -1, false, false));

        assertTrue(recreated.isEmpty());
        assertTrue(events.isCloudCleanupTerminal());
        assertTrue(events.getCloudRewindIds().stream().allMatch(java.util.Objects::isNull));
        assertThrows(IllegalStateException.class,
                () -> events.setCloudRewindId(0, ObjectRefId.dynamic(1, 1, 1)));
        assertThrows(IllegalStateException.class, () -> events.setCloudCleanupTerminal(false));
        assertTrue(events.isCloudCleanupTerminal());
    }

    @Test
    void commitAndPostCommitFailuresRollbackAndPreserveOriginalFailure() {
        Sonic3kFBZEvents events = act2WithCloudIds();
        List<ObjectRefId> before = List.copyOf(events.getCloudRewindIds());
        Set<ObjectRefId> live = new HashSet<>();
        RuntimeException commitFailure = new RuntimeException("commit");
        RuntimeException rollbackFailure = new RuntimeException("rollback");
        FbzCloudRecreationBatchFactory factory = requests -> new FbzCloudRecreationBatch() {
            @Override public List<ObjectRefId> recreateAll() { return requests.stream().map(FbzCloudRecreationRequest::stableId).toList(); }
            @Override public void commit() { throw commitFailure; }
            @Override public void rollback() { throw rollbackFailure; }
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> events.reconcileCloudsAfterObjectRestore(live::contains, factory));
        assertSame(commitFailure, thrown);
        assertArrayEquals(new Throwable[]{rollbackFailure}, thrown.getSuppressed());
        assertEquals(before, events.getCloudRewindIds());

        Set<ObjectRefId> published = new HashSet<>();
        FbzCloudRecreationBatchFactory notLiveAfterCommit = requests -> new FbzCloudRecreationBatch() {
            private final List<ObjectRefId> staged = requests.stream().map(FbzCloudRecreationRequest::stableId).toList();
            @Override public List<ObjectRefId> recreateAll() { assertTrue(published.isEmpty()); return staged; }
            @Override public void commit() { published.addAll(staged); published.clear(); }
            @Override public void rollback() { published.clear(); }
        };
        assertThrows(IllegalStateException.class,
                () -> events.reconcileCloudsAfterObjectRestore(published::contains, notLiveAfterCommit));
        assertTrue(published.isEmpty());
        assertEquals(before, events.getCloudRewindIds());
    }

    private static Sonic3kFBZEvents act2WithCloudIds() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        for (int i = 0; i < 10; i++) events.setCloudRewindId(i, ObjectRefId.dynamic(20, 1, i));
        return events;
    }

    private static byte[] capture(Sonic3kFBZEvents events) {
        return new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events).captureBytes();
    }

    private static FbzCloudRecreationBatchFactory batchFactory(Set<ObjectRefId> live,
                                                                List<Integer> recreated,
                                                                int failIndex,
                                                                boolean differentIds,
                                                                boolean nullOnFailure) {
        return requests -> new FbzCloudRecreationBatch() {
            private final List<ObjectRefId> created = new ArrayList<>();
            @Override public List<ObjectRefId> recreateAll() {
                List<ObjectRefId> result = new ArrayList<>();
                for (FbzCloudRecreationRequest request : requests) {
                    if (request.cloudIndex() == failIndex) {
                        if (nullOnFailure) result.add(null);
                        else throw new IllegalStateException("batch failure");
                        continue;
                    }
                    ObjectRefId id = differentIds
                            ? ObjectRefId.dynamic(99, 2, request.cloudIndex())
                            : request.stableId();
                    recreated.add(request.cloudIndex());
                    created.add(id);
                    result.add(id);
                }
                return result;
            }
            @Override public void commit() { live.addAll(created); }
            @Override public void rollback() { live.removeAll(created); }
        };
    }
}
