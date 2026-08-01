package com.openggf.trace.replay;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.trace.EngineSnapshot;

import java.util.Map;

/** Read-only frame-zero engine projection shared by headless and visual replay. */
public final class TraceReplayEngineSnapshot {
    private TraceReplayEngineSnapshot() {
    }

    public static EngineSnapshot capture(AbstractPlayableSprite sprite) {
        return EngineSnapshot.capture(
                sprite != null ? sprite.copyXHistory() : null,
                sprite != null ? sprite.copyYHistory() : null,
                sprite != null ? sprite.copyInputHistory() : null,
                sprite != null ? sprite.copyStatusHistory() : null,
                sprite != null ? sprite.historyPos() : 0,
                captureFirstSidekickCpu(),
                Map.of());
    }

    private static EngineSnapshot.SidekickCpuView captureFirstSidekickCpu() {
        var sprites = GameServices.spritesOrNull();
        if (sprites == null || sprites.getSidekicks().isEmpty()) {
            return null;
        }
        SidekickCpuController controller =
                sprites.getSidekicks().getFirst().getCpuController();
        if (controller == null) {
            return null;
        }
        return new EngineSnapshot.SidekickCpuView(
                controller.getDiagnosticControlCounter(),
                controller.getDiagnosticRespawnCounter(),
                controller.getDiagnosticRomCpuRoutine(),
                (short) controller.targetX(),
                (short) controller.targetY(),
                controller.getDiagnosticInteractId(),
                controller.getDiagnosticJumpingFlag() != 0);
    }
}
