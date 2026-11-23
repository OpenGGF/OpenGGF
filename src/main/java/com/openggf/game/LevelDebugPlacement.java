package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.PlayerInputState;
import com.openggf.game.internal.LevelDebugPlacementProvider;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

import static com.openggf.control.InputActionMasks.ACTION_A;
import static com.openggf.control.InputActionMasks.ACTION_B;
import static com.openggf.control.InputActionMasks.ACTION_C;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_UP;

/** State and input for the current gameplay session's placement cursor. */
public final class LevelDebugPlacement implements RewindSnapshottable<LevelDebugPlacement.Snapshot> {
    private static final String KEY = "level-debug-placement";
    private static final int CURSOR_STEP = 4;

    public record Bounds(short minX, short maxX, short minY, short maxY, short minXTarget,
                         short maxXTarget, short minYTarget, short maxYTarget) {
        static Bounds save(Camera camera) {
            return new Bounds(camera.getMinX(), camera.getMaxX(), camera.getMinY(), camera.getMaxY(),
                    camera.getMinXTarget(), camera.getMaxXTarget(), camera.getMinYTarget(), camera.getMaxYTarget());
        }

        void apply(Camera camera) {
            camera.setMinXCurrent(minX);
            camera.setMaxXCurrent(maxX);
            camera.setMinYCurrent(minY);
            camera.setMaxYCurrent(maxY);
            camera.setMinXTarget(minXTarget);
            camera.setMaxXTarget(maxXTarget);
            camera.setMinYTarget(minYTarget);
            camera.setMaxYTarget(maxYTarget);
        }
    }

    public record Snapshot(boolean cheatEnabled, int selected, Bounds savedBounds) { }

    private boolean cheatEnabled;
    private int selected;
    private Bounds savedBounds;

    public static LevelDebugPlacement of(GameStateManager gameState) {
        return gameState.levelDebugPlacement;
    }

    public static LevelDebugPlacementProvider provider(LevelManager level) {
        var module = level == null ? null : level.getGameModule();
        return module == null ? null : module.getGameService(LevelDebugPlacementProvider.class);
    }

    public static boolean engineMode(SonicConfigurationService config, LevelDebugPlacementProvider provider) {
        return provider == null || "engine".equals(config.getString(SonicConfiguration.LEVEL_DEBUG_MODE));
    }

    public void enableCheat() {
        cheatEnabled = true;
    }

    public boolean isActive() {
        return savedBounds != null;
    }

    public static boolean isActiveFor(AbstractPlayableSprite player) {
        if (player == null || player.isCpuControlled()) return false;
        GameStateManager state = player.currentGameStateOrNull();
        return state != null && of(state).isActive();
    }

    public static boolean bypassesInteractions(PlayableEntity player) {
        return player.isDebugMode() || player instanceof AbstractPlayableSprite actor && isActiveFor(actor);
    }

    public static boolean isActive(GameStateManager state) {
        return state != null && of(state).isActive();
    }

    public static boolean preventsTimeOver(AbstractPlayableSprite player) {
        if (player == null) return false;
        var state = player.currentGameStateOrNull();
        if (state == null || !of(state).cheatEnabled) return false;
        var provider = provider(player.currentLevelManagerIfAvailable());
        return provider != null && provider.preventsTimeOver();
    }

    /** True when placement owns the primary actor's dispatch for this frame. */
    public boolean dispatch(LevelDebugPlacementProvider provider, LevelManager level, AbstractPlayableSprite player,
                            PlayerInputState input, boolean engineMode) {
        Camera camera = player.currentCamera();
        if (!isActive()) {
            if (engineMode || !provider.canEnter(level, player, input, cheatEnabled)) return false;
            List<LevelDebugPlacementProvider.Entry> entries = provider.entries(level);
            if (entries.isEmpty()) return false;
            if (selected >= entries.size()) selected = 0;
            player.setDebugMode(false);
            player.setControlLocked(false);
            savedBounds = Bounds.save(camera);
            camera.setFrozen(false);
            provider.initializeCursor(level, player);
            return true;
        }

        if (engineMode) {
            exit(provider, player, camera);
            return true;
        }
        moveCursor(player, camera, input.heldMask());
        List<LevelDebugPlacementProvider.Entry> entries = provider.entries(level);
        int pressed = input.actionPressedMask();
        if ((input.actionHeldMask() & ACTION_A) != 0 && (pressed & ACTION_C) != 0) {
            selected = Math.floorMod(selected - 1, entries.size());
        } else if ((pressed & ACTION_A) != 0) {
            selected = (selected + 1) % entries.size();
        } else if ((pressed & ACTION_C) != 0 && ObjectManagerInternalAccess.placeDebugObject(
                level.getObjectManager(), entries.get(selected).at(
                        player.getCentreX(), player.getCentreY(),
                        (player.getRenderHFlip() ? 1 : 0) | (player.getRenderVFlip() ? 2 : 0)))) {
            return true;
        } else if ((pressed & ACTION_B) != 0) {
            exit(provider, player, camera);
        }
        return true;
    }

    private static void moveCursor(AbstractPlayableSprite player, Camera camera, int held) {
        int dx = ((held & INPUT_RIGHT) != 0 ? CURSOR_STEP : 0) - ((held & INPUT_LEFT) != 0 ? CURSOR_STEP : 0);
        int dy = ((held & INPUT_DOWN) != 0 ? CURSOR_STEP : 0) - ((held & INPUT_UP) != 0 ? CURSOR_STEP : 0);
        if (dx != 0) {
            NativePositionOps.addXPosPreserveSubpixel(player, Math.max(-player.getCentreX(), dx));
        }
        if (dy != 0) {
            int minY = camera.getMinY();
            int maxY = Math.max(minY, camera.getMaxYTarget() + camera.getHeight() - 1);
            int nextY = Math.clamp(player.getCentreY() + dy, minY, maxY);
            NativePositionOps.writeYPosPreserveSubpixel(player, nextY);
        }
    }

    private void exit(LevelDebugPlacementProvider provider, AbstractPlayableSprite player, Camera camera) {
        provider.resetPlayer(player);
        savedBounds.apply(camera);
        savedBounds = null;
    }

    /** Draw the cursor in the primary actor's existing sprite layer. */
    public static boolean drawPreview(AbstractPlayableSprite player) {
        if (!isActiveFor(player)) return false;
        LevelManager level = player.currentLevelManagerIfAvailable();
        LevelDebugPlacementProvider provider = provider(level);
        if (provider == null) return false;
        LevelDebugPlacement placement = of(player.currentGameState());
        var entries = provider.entries(level);
        if (placement.selected < entries.size()) {
            entries.get(placement.selected).preview().draw(level, player.getRenderCentreX(),
                    player.getRenderCentreY(), player.getRenderHFlip(), player.getRenderVFlip());
        }
        return true;
    }

    void resetLevel() {
        savedBounds = null;
    }

    @Override public String key() { return KEY; }

    @Override public Snapshot capture() { return new Snapshot(cheatEnabled, selected, savedBounds); }

    @Override
    public void restore(Snapshot snapshot) {
        cheatEnabled = snapshot.cheatEnabled();
        selected = snapshot.selected();
        savedBounds = snapshot.savedBounds();
    }

    @Override public void resetForMissingSnapshot() { restore(new Snapshot(false, 0, null)); }
}
