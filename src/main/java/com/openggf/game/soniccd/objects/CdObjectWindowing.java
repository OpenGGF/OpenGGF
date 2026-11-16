package com.openggf.game.soniccd.objects;

import com.openggf.level.objects.ViewportAwareObjectWindowingStrategy;
import com.openggf.level.spawn.PlacementViewportWidth;

/**
 * SpawnObjects_Forward (Level/Object Spawner.asm:176-185) and
 * SpawnObjects_Backward (Level/Object Spawner.asm:124-128):
 * load to (cameraX & $FF80) + $280, trim behind that chunk minus $80.
 * CheckObjDespawn2 (Level/Object Spawner.asm:392-399) compares unsigned chunk distance
 * with $80+(320+$40)+$80. Wider simulation is an engine extension: keep the native
 * load lead until the visible right edge needs another $80, and grow unload by the width delta.
 */
public final class CdObjectWindowing implements ViewportAwareObjectWindowingStrategy {
    public static final CdObjectWindowing INSTANCE = new CdObjectWindowing();
    private CdObjectWindowing() { }

    public static int simulationWidth() { return Math.max(320, PlacementViewportWidth.simulation()); }
    public static int unloadRange(int width) { return 0x280 + Math.max(0, width - 320); }
    public static boolean outside(int x, int cameraX, int width) {
        return (((x & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF) > unloadRange(width);
    }
    @Override public boolean overridesLoadWindow() { return true; }
    // CD objects own their deletion checks; the manager must not unload every object by range.
    @Override public boolean overridesUnloadWindow() { return false; }
    @Override public int loadWindowForwardEdge(int cameraX) { return loadWindowForwardEdge(cameraX, 320); }
    @Override public int loadWindowForwardEdge(int cameraX, int width) {
        return (cameraX & 0xFF80) + Math.max(0x280, Math.max(320, width) + 0x80);
    }
    @Override public int loadWindowLeftTrimEdge(int cameraX) { return (cameraX & 0xFF80) - 0x80; }
    @Override public int loadWindowLeftTrimEdge(int cameraX, int width) { return loadWindowLeftTrimEdge(cameraX); }
    @Override public boolean isOutsideUnloadWindow(int x, int cameraX) { return outside(x, cameraX, 320); }
    @Override public boolean isOutsideUnloadWindow(int x, int cameraX, int width) { return outside(x, cameraX, width); }
}
