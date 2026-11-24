package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.PlayerInputState;
import com.openggf.game.internal.LevelDebugPlacementProvider;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
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
    private static final int DIRECTIONS = INPUT_UP | INPUT_DOWN | INPUT_LEFT | INPUT_RIGHT;

    public record Bounds(short minY, short maxYTarget) {
        static Bounds save(Camera camera) {
            return new Bounds(camera.getMinY(), camera.getMaxYTarget());
        }

        void apply(Camera camera) {
            camera.setMinYCurrent(minY);
            camera.setMaxYTarget(maxYTarget);
        }
    }

    public record Snapshot(boolean cheatUnlocked, boolean cheatEnabled, int selected,
                           int moveDelay, int moveSpeed, Bounds savedBounds) { }

    private boolean cheatUnlocked;
    private boolean cheatEnabled;
    private int selected;
    private int moveDelay;
    private int moveSpeed;
    private Bounds savedBounds;

    public static LevelDebugPlacement of(GameStateManager gameState) {
        return gameState.levelDebugPlacement;
    }

    public static LevelDebugPlacementProvider provider(LevelManager level) {
        var module = level == null ? null : level.getGameModule();
        return module == null ? null : module.getGameService(LevelDebugPlacementProvider.class);
    }

    public static void activateOnLevelLoad(LevelManager level, GameStateManager state, SpriteManager sprites) {
        var provider = provider(level);
        if (provider != null) {
            provider.onLevelLoad(of(state), SpriteManagerInternalAccess.levelEntryPlayerOne(sprites));
        }
    }

    public static boolean engineMode(SonicConfigurationService config, LevelDebugPlacementProvider provider) {
        return provider == null || "engine".equals(config.getString(SonicConfiguration.LEVEL_DEBUG_MODE));
    }

    public void unlockCheat() {
        cheatUnlocked = true;
    }

    public boolean isCheatUnlocked() {
        return cheatUnlocked;
    }

    public void clearCheatForTitle() {
        cheatEnabled = false;
        selected = 0;
        resetLevel();
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

    public static boolean blocksObjectInteractions(ObjectServices services) {
        return services != null && isActive(services.gameState());
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
            var motion = provider.cursorMotion();
            moveDelay = motion.holdDelay();
            moveSpeed = motion.entrySpeed();
            camera.setFrozen(false);
            provider.initializeCursor(level, player);
            return true;
        }

        if (engineMode) {
            exit(provider, player, camera);
            return true;
        }
        moveCursor(provider, player, camera, input);
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

    private void moveCursor(LevelDebugPlacementProvider provider, AbstractPlayableSprite player,
                            Camera camera, PlayerInputState input) {
        var motion = provider.cursorMotion();
        int directions = input.heldMask() & DIRECTIONS;
        if ((input.pressedMask() & DIRECTIONS) == 0) {
            if (directions == 0) {
                moveDelay = motion.holdDelay();
                moveSpeed = motion.idleSpeed();
                return;
            }
            if (--moveDelay != 0) return;
            moveDelay = 1;
            moveSpeed = Math.min(moveSpeed + motion.acceleration(), motion.maxSpeed());
        }
        int delta = ((moveSpeed + 1) << 16) >> motion.speedShift();
        if ((directions & INPUT_UP) != 0) {
            int top = provider.cursorTop(camera);
            NativePositionOps.addYPos16_16(player, -delta);
            if (player.getCentreY() < top) {
                NativePositionOps.writeYPosResetSubpixel(player, top);
            }
        }
        if ((directions & INPUT_DOWN) != 0) {
            int bottom = provider.cursorBottom(camera);
            NativePositionOps.addYPos16_16(player, delta);
            if (player.getCentreY() >= bottom) {
                NativePositionOps.writeYPosResetSubpixel(player, bottom);
            }
        }
        if ((directions & INPUT_LEFT) != 0) {
            int x = (player.getCentreX() << 16) | player.getXSubpixelRaw();
            NativePositionOps.addXPos16_16(player, Integer.compareUnsigned(x, delta) < 0 ? -x : -delta);
        }
        if ((directions & INPUT_RIGHT) != 0) {
            NativePositionOps.addXPos16_16(player, delta);
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
        moveDelay = 0;
        moveSpeed = 0;
    }

    @Override public String key() { return KEY; }

    @Override
    public Snapshot capture() {
        return new Snapshot(cheatUnlocked, cheatEnabled, selected, moveDelay, moveSpeed, savedBounds);
    }

    @Override
    public void restore(Snapshot snapshot) {
        cheatUnlocked = snapshot.cheatUnlocked();
        cheatEnabled = snapshot.cheatEnabled();
        selected = snapshot.selected();
        moveDelay = snapshot.moveDelay();
        moveSpeed = snapshot.moveSpeed();
        savedBounds = snapshot.savedBounds();
    }

    @Override public void resetForMissingSnapshot() { restore(new Snapshot(false, false, 0, 0, 0, null)); }
}
