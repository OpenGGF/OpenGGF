package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.sonic3k.events.FbzCloudRecreationBatch;
import com.openggf.game.sonic3k.events.FbzCloudRecreationBatchFactory;
import com.openggf.game.sonic3k.events.FbzCloudRecreationRequest;

import java.util.ArrayList;
import java.util.List;

/** Exact selector-role cloud relinker; no raw object references are retained. */
public final class FbzBossEventRewindLinks {
    private FbzBossEventRewindLinks() { }

    public static FbzCloudRecreationBatchFactory recreationFactory(ObjectManager manager) {
        return requests -> new FbzCloudRecreationBatch() {
            private final List<FbzCloudInstance> committed = new ArrayList<>();

            @Override public List<ObjectRefId> recreateAll() {
                // Staging is side-effect free. Null snapshot slots never appear in
                // requests, and request order is native address/allocation order.
                return requests.stream().map(FbzCloudRecreationRequest::stableId).toList();
            }

            @Override public void commit() {
                var adoption = manager.exactRewindAdoptionSurface();
                try {
                    for (FbzCloudRecreationRequest request : requests) {
                        int addressSlot = request.cloudIndex();
                        int selector = 9 - addressSlot;
                        FbzCloudInstance cloud = adoption.adopt(
                                request.stableId(), () -> new FbzCloudInstance(selector));
                        if (cloud == null) throw new IllegalStateException(
                                "Could not restore FBZ cloud address slot " + addressSlot);
                        committed.add(cloud);
                    }
                } catch (RuntimeException | Error failure) {
                    rollback();
                    throw failure;
                }
            }

            @Override public void rollback() {
                var adoption = manager.exactRewindAdoptionSurface();
                for (int i = committed.size() - 1; i >= 0; i--) {
                    adoption.rollback(committed.get(i));
                }
                committed.clear();
            }
        };
    }
}
