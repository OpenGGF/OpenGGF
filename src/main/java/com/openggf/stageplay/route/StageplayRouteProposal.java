package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument;

import java.util.List;

/** Private static-terrain hypothesis. It is never an editor-applicable route result. */
record StageplayRouteProposal(
        StageplayRouteResult.Status status,
        String message,
        List<StageplayDocument.PuppetCommand> commands,
        List<StageplayRoutePoint> path,
        int nodes,
        int ticks) {
    boolean found() {
        return status == null;
    }
}
