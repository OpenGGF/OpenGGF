package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.game.LevelState;
import com.openggf.game.RespawnState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Read-only absence of native rings, timer and checkpoint state. Never a campaign/save model. */
final class SonicCdStaticState {
    private SonicCdStaticState() { }

    static final class Level implements LevelState {
        @Override public void update() { throw SonicCd.unsupported("native level-state ticks"); }
        @Override public int getRings() { return 0; }
        @Override public void addRings(int amount) { requireZero(amount, "rings"); }
        @Override public void setRings(int rings) { requireZero(rings, "rings"); }
        @Override public void setRingExtraLifeFlags(int flags) { requireZero(flags, "ring rewards"); }
        @Override public boolean isTimeOver() { return false; }
        @Override public String getDisplayTime() { return "0:00"; }
        @Override public boolean shouldFlashTimer() { return false; }
        @Override public boolean getFlashCycle() { return false; }
        @Override public void pauseTimer() { }
        @Override public void resumeTimer() { throw SonicCd.unsupported("native level timer"); }
        @Override public boolean isTimerPaused() { return true; }
        @Override public int getElapsedSeconds() { return 0; }
        @Override public long getTimerFrames() { return 0; }
        @Override public void setTimerFrames(long frames) { requireZero(frames, "native timer restore"); }
        private static void requireZero(long value, String operation) {
            if (value != 0) throw SonicCd.unsupported(operation);
        }
    }

    static final class Respawn implements RespawnState {
        @Override public void clear() { }
        @Override public boolean isActive() { return false; }
        @Override public int getLastCheckpointIndex() { return -1; }
        @Override public int getSavedX() { return 0; }
        @Override public int getSavedY() { return 0; }
        @Override public int getSavedCameraX() { return 0; }
        @Override public int getSavedCameraY() { return 0; }
        @Override public void restoreToPlayer(AbstractPlayableSprite player, Camera camera) {
            throw SonicCd.unsupported("checkpoint respawn");
        }
        @Override public void restoreFromSaved(int x, int y, int cameraX, int cameraY, int checkpointIndex) {
            throw SonicCd.unsupported("checkpoint restore");
        }
        @Override public void restoreStarPostActivationMark(int mark) {
            if (mark != -1) throw SonicCd.unsupported("checkpoint activation");
        }
    }
}
