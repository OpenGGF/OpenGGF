package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.BoxObjectInstance;

import com.openggf.audio.GameSound;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindStateful;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * ForcedSpin / Pinball Mode object (Object 0x84).
 * Used in Casino Night Zone and Hill Top Zone.
 * <p>
 * This is an invisible trigger object that forces Sonic into/out of rolling state
 * when he crosses the trigger zone. It enables "pinball mode" where rolling cannot
 * be cleared on landing.
 * <p>
 * Based on Obj84 from the Sonic 2 disassembly.
 *
 * <h3>Subtype Encoding:</h3>
 * <ul>
 *   <li>Bit 2 (0x04): Direction flag - 0=horizontal trigger, 1=vertical trigger</li>
 *   <li>Bits 0-1 (0x03): Width index into table: 0=32px, 1=64px, 2=128px, 3=256px</li>
 * </ul>
 *
 * <h3>X_flip Behavior (render_flags bit):</h3>
 * <ul>
 *   <li>X_flip = 0 (unflipped): Crossing trigger line enables pinball mode</li>
 *   <li>X_flip = 1 (flipped): Crossing trigger line disables pinball mode</li>
 * </ul>
 *
 * <h3>Trigger Logic:</h3>
 * <p>
 * The object tracks per-character crossing state. When player crosses the trigger line
 * in one direction, the action is applied and state is set. When player crosses back
 * in the opposite direction, state resets and the OPPOSITE action is applied.
 * </p>
 */
public class ForcedSpinObjectInstance extends BoxObjectInstance implements RewindRecreatable {

    // Width lookup table from disassembly word_211E8
    private static final int[] WIDTH_TABLE = {0x20, 0x40, 0x80, 0x100};

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = staticDebugViewEnabled();
    private static final DebugOverlayManager OVERLAY_MANAGER = staticDebugOverlay();

    // Debug colors
    private static final float ENABLE_R = 0.0f;
    private static final float ENABLE_G = 1.0f;
    private static final float ENABLE_B = 0.0f;
    private static final float DISABLE_R = 1.0f;
    private static final float DISABLE_G = 0.0f;
    private static final float DISABLE_B = 0.0f;

    private boolean verticalMode;    // bit 2 of subtype: 0=horizontal, 1=vertical
    private int triggerWidth;        // half-width from WIDTH_TABLE
    private boolean xFlipped;        // x_flip bit from spawn (determines action direction)

    // Per-character crossing state (true = player is past the trigger line)
    // Matches objoff_34 (Sonic) and objoff_35 (Tails) from disassembly
    private boolean sonicPastTrigger;
    private boolean tailsPastTrigger;
    private PlayableEntity sonicStateOwner;
    private PlayableEntity tailsStateOwner;
    private final Map<PlayableEntity, CrossingState> extensionStates = new IdentityHashMap<>();

    // Track initialization state
    private boolean initialized;

    public ForcedSpinObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name,
                WIDTH_TABLE[spawn.subtype() & 0x03],  // halfWidth from width table
                WIDTH_TABLE[spawn.subtype() & 0x03],  // halfHeight same as width (square trigger zone)
                // Color based on x_flip: green for enable, red for disable
                (spawn.renderFlags() & 0x01) == 0 ? ENABLE_R : DISABLE_R,
                (spawn.renderFlags() & 0x01) == 0 ? ENABLE_G : DISABLE_G,
                (spawn.renderFlags() & 0x01) == 0 ? ENABLE_B : DISABLE_B,
                false  // not high priority (invisible in normal gameplay)
        );

        this.verticalMode = (spawn.subtype() & 0x04) != 0;
        this.triggerWidth = WIDTH_TABLE[spawn.subtype() & 0x03];
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public ForcedSpinObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new ForcedSpinObjectInstance(ctx.spawn(), getName());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = resolveMain(playerEntity);
        if (player == null) {
            return;
        }

        List<PlayableEntity> participants = interactionParticipants(player);
        bindNativeOwners(participants);

        // Initialize crossing state based on player's current position
        if (!initialized) {
            initializeCrossingStates(participants);
            initialized = true;
        }

        // Check for Sonic (main player)
        checkPlayerCrossing(player, true);

        AbstractPlayableSprite nativeP2 = participants.size() > 1
                && participants.get(1) instanceof AbstractPlayableSprite sprite ? sprite : null;
        if (nativeP2 != null && !isHorizontalSidekickFlightAutoRecovery(nativeP2)) {
            checkPlayerCrossing(nativeP2, false);
        }
        for (int index = 2; index < participants.size(); index++) {
            if (participants.get(index) instanceof AbstractPlayableSprite extension) {
                if (extension.getDead() || extension.isHurt() || extension.isDebugMode()) {
                    extensionStates.remove(extension);
                    continue;
                }
                if (isHorizontalSidekickFlightAutoRecovery(extension)) {
                    continue;
                }
                CrossingState state = extensionStates.computeIfAbsent(extension,
                        ignored -> new CrossingState(isPastTrigger(extension)));
                boolean nativeP2State = tailsPastTrigger;
                tailsPastTrigger = state.pastTrigger;
                checkPlayerCrossing(extension, false);
                state.pastTrigger = tailsPastTrigger;
                tailsPastTrigger = nativeP2State;
            }
        }
        pruneOmittedExtensions(participants);
    }

    private AbstractPlayableSprite resolveMain(PlayableEntity fallback) {
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        if (main instanceof AbstractPlayableSprite sprite) {
            return sprite;
        }
        return fallback instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private List<PlayableEntity> interactionParticipants(AbstractPlayableSprite main) {
        List<PlayableEntity> participants = services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        if (participants.contains(main)) {
            return participants;
        }
        ArrayList<PlayableEntity> withMain = new ArrayList<>(participants.size() + 1);
        withMain.add(main);
        withMain.addAll(participants);
        return withMain;
    }

    private void bindNativeOwners(List<PlayableEntity> participants) {
        PlayableEntity main = participants.isEmpty() ? null : participants.get(0);
        PlayableEntity nativeP2 = participants.size() > 1 ? participants.get(1) : null;
        sonicStateOwner = bindNativeOwner(sonicStateOwner, main, true);
        tailsStateOwner = bindNativeOwner(tailsStateOwner, nativeP2, false);
    }

    private PlayableEntity bindNativeOwner(
            PlayableEntity previous, PlayableEntity current, boolean sonicSlot) {
        if (previous == current) {
            return current;
        }
        if (previous != null) {
            extensionStates.put(previous,
                    new CrossingState(sonicSlot ? sonicPastTrigger : tailsPastTrigger));
        }
        CrossingState restored = current != null ? extensionStates.remove(current) : null;
        boolean state = restored != null ? restored.pastTrigger
                : current instanceof AbstractPlayableSprite playable && initialized && isPastTrigger(playable);
        if (sonicSlot) {
            sonicPastTrigger = state;
        } else {
            tailsPastTrigger = state;
        }
        return current;
    }

    private void pruneOmittedExtensions(List<PlayableEntity> participants) {
        extensionStates.keySet().removeIf(player -> participants.stream().noneMatch(live -> live == player));
    }

    /**
     * Initializes the crossing state based on the player's current position relative
     * to the trigger line. This ensures correct behavior if player is already past
     * the trigger when the object loads.
     */
    private void initializeCrossingStates(List<PlayableEntity> participants) {
        int objX = spawn.x();
        int objY = spawn.y();
        AbstractPlayableSprite player = (AbstractPlayableSprite) participants.get(0);
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        if (verticalMode) {
            // Vertical mode: check if player is below trigger line
            // Disassembly uses strictly greater (bhs branch skips if objY >= playerY)
            sonicPastTrigger = playerY > objY;
        } else {
            // Horizontal mode: check if player is to the right of trigger line
            // Disassembly uses strictly greater (bhs branch skips if objX >= playerX)
            sonicPastTrigger = playerX > objX;
        }

        AbstractPlayableSprite nativeP2 = participants.size() > 1
                && participants.get(1) instanceof AbstractPlayableSprite sprite ? sprite : null;
        if (nativeP2 != null) {
            int sidekickX = nativeP2.getCentreX();
            int sidekickY = nativeP2.getCentreY();
            if (verticalMode) {
                tailsPastTrigger = sidekickY > objY;
            } else {
                tailsPastTrigger = sidekickX > objX;
            }
        }
        for (int index = 2; index < participants.size(); index++) {
            if (participants.get(index) instanceof AbstractPlayableSprite extension) {
                extensionStates.put(extension, new CrossingState(isPastTrigger(extension)));
            }
        }
    }

    private boolean isPastTrigger(AbstractPlayableSprite player) {
        return verticalMode ? player.getCentreY() > spawn.y() : player.getCentreX() > spawn.x();
    }

    private boolean isHorizontalSidekickFlightAutoRecovery(AbstractPlayableSprite player) {
        SidekickCpuController controller = player.getCpuController();
        return !verticalMode
                && player.isCpuControlled()
                && controller != null
                && controller.getDiagnosticRomCpuRoutine() == 0x04;
    }

    private static final class CrossingState implements RewindStateful<Boolean> {
        private boolean pastTrigger;

        private CrossingState() {
        }

        private CrossingState(boolean pastTrigger) {
            this.pastTrigger = pastTrigger;
        }

        @Override
        public Boolean captureRewindStateValue() {
            return pastTrigger;
        }

        @Override
        public void restoreRewindStateValue(Boolean state) {
            pastTrigger = Boolean.TRUE.equals(state);
        }
    }

    /**
     * Checks if a player has crossed the trigger line and applies the appropriate action.
     *
     * @param player the player to check
     * @param isSonic true for Sonic (main character), false for Tails
     */
    private void checkPlayerCrossing(AbstractPlayableSprite player, boolean isSonic) {
        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        boolean pastTrigger = isSonic ? sonicPastTrigger : tailsPastTrigger;

        if (verticalMode) {
            // Vertical mode: trigger line is horizontal at objY
            if (!pastTrigger) {
                // Player was above trigger line
                if (playerY >= objY) {
                    // Crossed to below - set flag and check range
                    if (isSonic) {
                        sonicPastTrigger = true;
                    } else {
                        tailsPastTrigger = true;
                    }
                    // Check if player X is within range
                    if (isWithinRange(playerX, objX, triggerWidth)) {
                        applyAction(player, !xFlipped);  // Primary direction: enable if not flipped
                    }
                }
            } else {
                // Player was below trigger line
                if (playerY < objY) {
                    // Crossed back to above - reset flag and apply opposite action
                    if (isSonic) {
                        sonicPastTrigger = false;
                    } else {
                        tailsPastTrigger = false;
                    }
                    // Check if player X is within range
                    if (isWithinRange(playerX, objX, triggerWidth)) {
                        applyAction(player, xFlipped);  // Opposite direction: disable if not flipped
                    }
                }
            }
        } else {
            // Horizontal mode: trigger line is vertical at objX
            if (!pastTrigger) {
                // Player was to the left of trigger line
                if (playerX >= objX) {
                    // Crossed to right - set flag and check range
                    if (isSonic) {
                        sonicPastTrigger = true;
                    } else {
                        tailsPastTrigger = true;
                    }
                    // Check if player Y is within range
                    if (isWithinRange(playerY, objY, triggerWidth)) {
                        applyAction(player, !xFlipped);  // Primary direction: enable if not flipped
                    }
                }
            } else {
                // Player was to the right of trigger line
                if (playerX < objX) {
                    // Crossed back to left - reset flag and apply opposite action
                    if (isSonic) {
                        sonicPastTrigger = false;
                    } else {
                        tailsPastTrigger = false;
                    }
                    // Check if player Y is within range
                    if (isWithinRange(playerY, objY, triggerWidth)) {
                        applyAction(player, xFlipped);  // Opposite direction: disable if not flipped
                    }
                }
            }
        }
    }

    /**
     * Checks if a position is within range of the center point.
     */
    private boolean isWithinRange(int pos, int center, int halfWidth) {
        int delta = pos - center;
        return delta >= -halfWidth && delta < halfWidth;
    }

    /**
     * Applies the pinball mode action to the player.
     *
     * @param player the player to modify
     * @param enablePinball true to enable pinball mode, false to disable
     */
    private void applyAction(AbstractPlayableSprite player, boolean enablePinball) {
        if (enablePinball) {
            enablePinballMode(player);
        } else {
            disablePinballMode(player);
        }
    }

    /**
     * Enables pinball mode on the player.
     * Based on loc_212C4 from the disassembly:
     * 1. If already rolling, return early (no sound/adjustment needed)
     * 2. Set rolling = true (which handles hitbox and Y adjustment)
     * 3. Set animation to Roll
     * 4. Play roll sound
     */
    private void enablePinballMode(AbstractPlayableSprite player) {
        // S2 aliases Obj84 pinball_mode to spindash_flag. Keep the engine's
        // separate fields in sync so later Tails_UpdateSpindash paths can
        // consume the same ROM byte after forced rolling ends.
        player.setPinballMode(true);
        player.setSpindash(true);

        // If already rolling, no need to do anything else
        if (player.getRolling()) {
            return;
        }

        short centreX = player.getCentreX();
        short centreY = player.getCentreY();
        // Force into rolling state
        // setRolling(true) handles radii and visual dimensions.
        player.setRolling(true);
        // ROM Obj84 writes radii/status/anim and adds 5 to y_pos only; it
        // never adjusts x_pos. The engine's generic wall-mode roll transition
        // changes width, so preserve the ROM centre X for this object path.
        player.setCentreXPreserveSubpixel(centreX);

        // ROM Obj84 loc_212C4 applies a fixed addq.w #5,y_pos(a1) after
        // setting rolling radii, for Sonic and Tails alike (docs/s2disasm/s2.asm:46377-46495).
        player.setCentreYPreserveSubpixel((short) (centreY + 5));

        // Set roll animation
        forceRollAnimation(player);

        // Play roll sound
        playRollSound();
    }

    /**
     * Disables pinball mode on the player.
     * The player will continue rolling until speed reaches 0, at which point
     * rolling will clear naturally (since pinballMode is now false).
     */
    private void disablePinballMode(AbstractPlayableSprite player) {
        player.setPinballMode(false);
        player.setSpindash(false);
    }

    /**
     * Forces the roll animation on the player.
     */
    private void forceRollAnimation(AbstractPlayableSprite player) {
        SpriteAnimationProfile profile = player.getAnimationProfile();
        if (profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
            int rollId = velocityProfile.getRollAnimId();
            player.setAnimationId(rollId);
            player.setAnimationFrameIndex(0);
            player.setAnimationTick(0);
        }
    }

    /**
     * Plays the roll sound effect.
     */
    private void playRollSound() {
        try {
            services().playSfx(GameSound.ROLLING);
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Only render in debug mode
        if (!isDebugViewEnabled()) {
            return;
        }

        // Render the trigger zone box using parent class
        super.appendRenderCommands(commands);

        // Draw a line indicating the trigger direction
        int centerX = spawn.x();
        int centerY = spawn.y();

        if (verticalMode) {
            // Vertical mode: horizontal trigger line
            appendLine(commands, centerX - triggerWidth, centerY, centerX + triggerWidth, centerY);
        } else {
            // Horizontal mode: vertical trigger line
            appendLine(commands, centerX, centerY - triggerWidth, centerX, centerY + triggerWidth);
        }
    }

    private boolean isDebugViewEnabled() {
        return DEBUG_VIEW_ENABLED && OVERLAY_MANAGER.isEnabled(DebugOverlayToggle.OVERLAY);
    }
}
