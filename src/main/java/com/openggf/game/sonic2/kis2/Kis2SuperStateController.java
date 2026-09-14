package com.openggf.game.sonic2.kis2;

import com.openggf.audio.GameMusic;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

/**
 * Shipped KiS2 Sonic_CheckGoSuper / PalCycle_SuperSonic, gameRevision=3,
 * fixBugs=0. The lock-on program keeps Knuckles' animation table and jump,
 * seeds ring drain at 60, and changes three colours without a Sonic fade.
 * Runtime palette bytes come from the chip, never the reference tree.
 */
public final class Kis2SuperStateController extends SuperStateController {
    // Verified against c336fed's binary palette assets in the lock-on dump.
    static final int CYCLE_ADDRESS = 0x301EE0;
    static final int REVERT_ADDRESS = 0x301F1C;
    private static final int[] COLOURS = {2, 3, 5};
    private static final String PALETTE_OWNER = "kis2.super";
    private static final int PALETTE_PRIORITY = 300;
    private final byte[] cycle;
    private final byte[] revert;
    private int paletteState;
    private int paletteFrame;
    private int paletteTimer;
    private boolean paletteAdvancedThisUpdate;

    public Kis2SuperStateController(AbstractPlayableSprite player, RomByteReader lockOn) {
        super(player);
        cycle = lockOn.slice(CYCLE_ADDRESS, 0x3C);
        revert = lockOn.slice(REVERT_ADDRESS, 6);
        setRomDataPreLoaded(true);
    }

    @Override public void reset() {
        super.reset();
        paletteState = paletteFrame = paletteTimer = 0;
    }
    @Override protected int getRingDrainInterval() { return 60; }
    @Override protected int getMinRingsToTransform() { return 50; }
    @Override protected int getInitialRingDrainCounter() { return 60; }
    @Override protected PhysicsProfile getSuperProfile() { return Kis2Physics.SUPER_KNUCKLES; }
    @Override protected PhysicsProfile getNormalProfile() { return Kis2Physics.KNUCKLES; }
    @Override protected int getSuperRunSpeedThreshold() { return 0x600; }
    @Override protected boolean usesAutomaticJumpTrigger() { return false; }
    @Override protected boolean usesExplicitAirAbilityTrigger() { return true; }
    @Override protected boolean passesGameSpecificTransformGates() {
        return player.getDoubleJumpFlag() == 0;
    }

    @Override protected void onTransformationStarted() {
        paletteState = 1;
        paletteTimer = 0xF;
        // KiS2 retains the fixBugs=0 roll/rolljump flags and radii on entry.
        player.setInvincibleFrames(0);
        player.setShieldVisible(false);
        GameServices.audio().playSfx(Sonic2AudioConstants.SFX_SUPER_TRANSFORM);
        GameServices.audio().playMusic(GameMusic.SUPER);
        var level = GameServices.levelOrNull();
        var objects = level == null ? null : level.getObjectManager();
        if (objects != null) {
            var stars = new SuperSonicStarsObjectInstance(player);
            int slot = player.getGameRules().powerUp().superStarsFixedSlotIndex();
            if (slot >= 0) ObjectLifetimeOps.addDynamicAtReservedSlot(objects, stars, slot);
            else objects.addDynamicObject(stars);
        }
    }

    @Override public void update() {
        paletteAdvancedThisUpdate = false;
        super.update();
    }

    @Override protected boolean updateTransformationAnimation() {
        paletteAdvancedThisUpdate = true;
        // PalCycle_SuperSonic: the first timer underflow restores control,
        // resets the frame and reloads 3; it writes no colours on this pass.
        if (--paletteTimer < 0) {
            paletteState = -1;
            paletteFrame = 0;
            paletteTimer = 3;
        }
        return paletteState != 1;
    }

    @Override protected void onSuperActivated() {
        // Knuckles keeps his existing art and animation scripts.
    }

    @Override protected void updateSuperPalette() {
        if (paletteAdvancedThisUpdate || paletteState != -1 || --paletteTimer >= 0) return;
        int displayedFrame = paletteFrame;
        paletteTimer = 2;
        paletteFrame += 6;
        if (paletteFrame >= 0x3C) {
            paletteFrame = 0;
            paletteTimer = 14;
        }
        applyPalette(displayedFrame, false);
    }

    @Override protected void onRevertStarted() {
        paletteState = 2;
        paletteFrame = 0x28; // Sonic_RevertToNormal; palette owner clears it next pass.
        player.setInvincibleFrames(1); // Obj01_ChkInvin restores level music.
        player.setShieldVisible(true);
        // Stars are rewind-owned objects. Resolve current instances instead of
        // retaining a stale controller reference across object recreation.
        var level = GameServices.levelOrNull();
        var objects = level == null ? null : level.getObjectManager();
        if (objects != null) {
            for (var object : objects.getActiveObjects()) {
                if (object instanceof SuperSonicStarsObjectInstance stars && stars.isOwnedBy(player)) stars.destroy();
            }
        }
    }

    @Override protected void updatePostRevertEffects() {
        if (paletteState == 2) {
            paletteState = 0;
            paletteFrame = 0;
            applyPalette(0, true);
        }
    }

    /** Writes exactly the sparse colours owned by .loadPalette, preserving colour 4. */
    void writePaletteFrame(Palette palette, int frame, boolean reverting) {
        byte[] data = reverting ? revert : cycle;
        for (int i = 0; i < COLOURS.length; i++) {
            palette.getColor(COLOURS[i]).fromSegaFormat(data, frame + i * 2);
        }
    }

    private void applyPalette(int frame, boolean reverting) {
        var levelManager = GameServices.levelOrNull();
        var level = levelManager == null ? null : levelManager.getCurrentLevel();
        if (level == null) return;
        byte[] data = reverting ? revert : cycle;
        var registry = GameServices.paletteOwnershipRegistryOrNull();
        var water = GameServices.waterOrNull();
        boolean hasWater = water != null && water.hasWater(level.getZoneIndex(), levelManager.getCurrentAct());
        Palette[] underwater = hasWater
                ? water.getUnderwaterPalette(level.getZoneIndex(), levelManager.getCurrentAct()) : null;
        for (int i = 0; i < COLOURS.length; i++) {
            byte[] word = {data[frame + i * 2], data[frame + i * 2 + 1]};
            int segaWord = (word[0] & 255) << 8 | (word[1] & 255);
            // Patch presentation wins donation: use the host's Knuckles line.
            PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(),
                    PALETTE_OWNER, PALETTE_PRIORITY, 0, COLOURS[i], segaWord);
            if (hasWater && registry != null) {
                registry.submit(PaletteWrite.underwater(PALETTE_OWNER, PALETTE_PRIORITY, 0, COLOURS[i], word));
            } else if (underwater != null && underwater.length > 0 && underwater[0] != null) {
                underwater[0].getColor(COLOURS[i]).fromSegaFormat(word, 0);
            }
        }
    }

    @Override public RewindState captureRewindState() {
        return createRewindState(paletteState, paletteFrame, paletteTimer, 0);
    }

    @Override public void restoreRewindState(RewindState snapshot) {
        if (snapshot == null) return;
        restoreCoreRewindState(snapshot);
        paletteState = snapshot.paletteState();
        paletteFrame = snapshot.paletteFrame();
        paletteTimer = snapshot.paletteTimer();
        reconcileRewindPhysicsAndAnimationProfile(isSuper());
        // Palette values and sparkle objects are restored by their own owners.
    }
}
