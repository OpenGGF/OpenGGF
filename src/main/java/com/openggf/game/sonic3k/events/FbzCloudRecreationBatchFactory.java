package com.openggf.game.sonic3k.events;

import java.util.List;

@FunctionalInterface
public interface FbzCloudRecreationBatchFactory {
    FbzCloudRecreationBatch begin(List<FbzCloudRecreationRequest> orderedMissingClouds);
}
