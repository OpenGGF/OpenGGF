package com.openggf.level;

import com.openggf.game.ActExit;
import java.util.OptionalInt;
import java.util.Map;
import java.util.Objects;
import com.openggf.game.DynamicStartPositionProvider;
import java.io.IOException;

/** Engine-only admission/consumption. Pending exits participate in the transition rewind adapter. */
public final class LevelSceneActAccess {
    private LevelSceneActAccess() {}
    record Exit(ActExit reason, Map<String, String> state) {
        Exit { Objects.requireNonNull(reason); state = Map.copyOf(state); }
    }
    /** Native provider selection precedes every optional scene override. */
    public static int[] nativeStart(com.openggf.data.Game game, int zone, int act,
            LevelDescriptor levelData, boolean additiveAct) {
        int spawnX = levelData.startX();
        int spawnY = levelData.startY();
        var log = java.util.logging.Logger.getLogger(LevelManager.class.getName());
        if (!additiveAct && game instanceof DynamicStartPositionProvider dynamicStartProvider) {
            try {
                int[] dynamicStart = dynamicStartProvider.getStartPosition(zone, act);
                if (dynamicStart != null && dynamicStart.length >= 2) {
                    spawnX = dynamicStart[0];
                    spawnY = dynamicStart[1];
                    log.info("Set player position from dynamic start provider: X=" + spawnX +
                        ", Y=" + spawnY + " (zone=" + zone + ", act=" + act + ")");
                } else {
                    log.info("Dynamic start provider unavailable, using levelData fallback for " + levelData);
                }
            } catch (IOException error) {
                log.warning("DynamicStartPositionProvider failed, using levelData fallback: " + error.getMessage());
            }
        }
        return new int[] {spawnX, spawnY};
    }
    public static void prepareSpawn(LevelManager level, OptionalInt spawnX, OptionalInt spawnY) {
        level.sceneSpawnX = spawnX; level.sceneSpawnY = spawnY;
    }
    public static void arm(LevelManager level) { level.getTransitions().sceneActActive = true; }
    public record Result(ActExit reason, int rings, long frames, Map<String, String> state) {}
    public static Result consume(LevelManager level) {
        var transitions = level.getTransitions();
        Exit exit = transitions.sceneActExit;
        if (exit == null) return null;
        transitions.sceneActExit = null;
        transitions.sceneActActive = false;
        return new Result(exit.reason(), level.getLevelGamestate().getRings(),
                Integer.toUnsignedLong(level.getFrameCounter()), exit.state());
    }
}
