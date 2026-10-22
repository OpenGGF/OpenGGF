package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.difficulty.AizBombingRunRecipe;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.events.S3kAizEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnYCoordinateRewindRecreatable;

import java.util.List;
import java.util.logging.Logger;

/**
 * AIZ2 Flying Battery battleship — crosses the screen dropping bombs.
 *
 * <p>ROM: Obj_AIZBattleship (sonic3k.asm:105303).
 * The ship moves left via a 16:16 fixed-point secondary camera X counter
 * (ROM: _unkEE98 -= $8800 per frame ≈ 0.53 px/frame).
 * A scripted bomb table drives 21 bomb drops. Bombs are positioned relative
 * to the ship's secondary camera, converted to world coordinates via:
 * {@code world_x = bomb_script_x - shipX + camera_x}.
 *
 * <p>The ship starts with an initial counter of $1A4, but like the ROM it waits
 * for the counter to underflow before consuming the first script entry. That
 * makes the first bomb appear on update 421 after spawn, and each entry's delay
 * governs the gap before the following bomb.
 * When the ship crosses below $3CDC, its own SST becomes {@link AizBossSmallInstance}
 * and {@link AizBgTreeSpawnerInstance} is allocated after it.
 * Every 16 frames plays {@code cfx_LargeShip} SFX.
 */
public class AizBattleshipInstance extends AbstractObjectInstance implements SpawnYCoordinateRewindRecreatable {
    private static final Logger LOG = Logger.getLogger(AizBattleshipInstance.class.getName());

    /** ROM: Battleship bomb script — {delay, bombX} pairs in secondary camera space. */
    private static final int[][] BOMB_SCRIPT = {
            {0x20, 0x3F5C}, {0x20, 0x3F2C}, {0x20, 0x3F5C}, {0x20, 0x3F2C}, {0x20, 0x3F5C}, {0x38, 0x3F2C},
            {0x20, 0x3EDC}, {0x20, 0x3EAC}, {0x20, 0x3EDC}, {0x20, 0x3EAC}, {0x20, 0x3EDC}, {0x38, 0x3EAC},
            {0x20, 0x3E5C}, {0x20, 0x3E2C}, {0x20, 0x3E5C}, {0x20, 0x3E2C}, {0x20, 0x3E5C}, {0x38, 0x3E2C},
            {0x40, 0x3DEC}, {0x40, 0x3DEC}, {0x40, 0x3DEC},
    };

    /** ROM: Ship X threshold to transition to the small boss craft. */
    private static final int SHIP_EXIT_X = 0x3CDC;
    /** ROM: once ship X passes below $3D5C, the secondary BG camera rises upward. */
    private static final int SHIP_RISE_TRIGGER_X = 0x3D5C;

    /** ROM: Initial secondary camera X (ship starts here). */
    private static final int INITIAL_SHIP_X = 0x4020;

    /** ROM: Ship movement speed in 16:16 fixed-point ($8800 per frame ≈ 0.53 px/frame). */
    private static final int SHIP_SPEED = 0x8800;

    /** ROM: Initial delay counter before the first underflow ($1A4 -> first bomb on update 421). */
    private static final int INITIAL_DELAY = 0x1A4;

    /** ROM: bomb source Y in secondary BG camera space. */
    private static final int BOMB_SOURCE_Y = 0x0A60;
    /** ROM: 16-step secondary-camera bobbing motion while the ship is on-screen. */
    private static final int[] BOBBING_MOTION = {
            4, 4, 3, 3, 2, 1, 1, 0, 0, 0, 1, 1, 2, 3, 3, 4
    };

    // 16:16 fixed-point ship position (integer part = upper 16 bits)
    private int shipXFixed;
    /** False until the object pass that created the ship has gone by unused. */
    private boolean creationPassConsumed;
    /** True once the Obj_AIZBattleship init has allocated its propellers. */
    private boolean propellersAllocated;
    // Non-final so the generic rewind field capturer reapplies it after
    // Phase-2 generic recreate.
    private int baseSecondaryY;
    private int effectiveSecondaryY;
    private int scriptIndex;
    private int delayTimer;
    private int frameCounter;
    private boolean finished;
    private boolean bombingStarted;

    public AizBattleshipInstance(ObjectSpawn spawn, int baseSecondaryY) {
        super(spawn, "AIZBattleship");
        this.shipXFixed = INITIAL_SHIP_X << 16;
        this.baseSecondaryY = baseSecondaryY;
        this.effectiveSecondaryY = baseSecondaryY;
        this.scriptIndex = 0;
        this.delayTimer = INITIAL_DELAY;
        this.frameCounter = 0;
        this.finished = false;
        this.bombingStarted = false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed() || finished) return;

        if (!creationPassConsumed) {
            // ROM: AIZ2SE_ShipRefresh sets the secondary camera X and then takes
            // a slot with plain AllocateObject (sonic3k.asm:104963-104974), NOT
            // AllocateObjectAfterCurrent -- so the slot is not guaranteed to sit
            // ahead of the object pass that created it, and for this run it does
            // not: the recording shows slot 4 still holding Obj_AIZBattleship
            // (the init) at the end of its creation frame and Obj_AIZBattleshipMain
            // only on the frame after. The init writes the Main pointer before
            // falling through, so a pointer still reading init means the slot was
            // allocated without being executed.
            //
            // The ship's first real pass -- init falling through into
            // Obj_AIZBattleshipMain and its opening `subi.l #$8800,(_unkEE98).w`
            // (:105307-105332) -- therefore happens one frame after creation.
            // Advancing on the creation frame left the secondary camera one step
            // ahead in its fraction for the rest of the act.
            creationPassConsumed = true;
            return;
        }
        if (!propellersAllocated) {
            propellersAllocated = true;
            allocatePropellers();
        }

        this.frameCounter++;

        // Move ship left (16:16 fixed-point)
        shipXFixed -= SHIP_SPEED;
        int shipX = shipXFixed >> 16;

        // ROM parity: exit check happens BEFORE SFX replay and bobbing.
        // When the ship crosses $3CDC, it transitions to Obj_AIZ2BossSmall
        // immediately — the continuous SFX stops being re-triggered and
        // naturally fades out via the cfLoopContinuousSFX fallthrough.
        if (shipX < SHIP_EXIT_X) {
            onShipExited();
            return;
        }

        updateSecondaryCameraY(shipX);

        // ROM: replay the large ship SFX every 16th frame.
        if (((this.frameCounter - 1) & 0xF) == 0) {
            services().playSfx(Sonic3kSfx.LARGE_SHIP.id);
        }

        if (!bombDelayUnderflowed() || scriptIndex >= BOMB_SCRIPT.length) {
            return;
        }

        if (!bombingStarted) {
            bombingStarted = true;
            LOG.info("AIZ2 battleship: bombing started at frame " + this.frameCounter
                    + ", shipX=0x" + Integer.toHexString(shipX));
        }

        int currentScriptIndex = scriptIndex;
        int nextDelay = BOMB_SCRIPT[currentScriptIndex][0];

        // ROM: Translate_Camera2ObjPosition/Translate_Camera2ObjX.
        int bombScriptX = BOMB_SCRIPT[currentScriptIndex][1];
        boolean staggered = admitStaggeredPorts();
        if (staggered && nextDelay == 0x20) {
            // Finite authored preset at the native script-entry commitment.
            // Keep all 21 ROM entries and the $38/$40 recovery rows; shorter
            // waits cannot push the final release beyond the native ship exit.
            nextDelay -= 8;
            bombScriptX += (currentScriptIndex & 1) == 0 ? 8 : -8;
        }
        int screenX = bombScriptX - shipX;
        int worldY = services().camera().getY() + (BOMB_SOURCE_Y - effectiveSecondaryY);

        spawnBomb(screenX, worldY, bombScriptX, staggered);
        scriptIndex = currentScriptIndex + 1;
        delayTimer = nextDelay;
    }

    /**
     * ROM Obj_AIZBattleship init (sonic3k.asm:105349-105364): two
     * AllocateObjectAfterCurrent calls create the front ($3FBC) and back ($3DCC)
     * Obj_BattleshipPropeller before the init falls through to the Main pass, so
     * both take SST slots after the ship and run later in this same pass. A
     * failed allocation skips the remaining propeller (bne.s loc_5037E).
     */
    private void allocatePropellers() {
        int[] secondaryXs = {
                AizBattleshipPropellerInstance.FRONT_SECONDARY_X,
                AizBattleshipPropellerInstance.BACK_SECONDARY_X};
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();
        int shipX = shipXFixed >> 16;
        for (int secondaryX : secondaryXs) {
            ObjectSpawn spawn = new ObjectSpawn(
                    cameraX + secondaryX - shipX,
                    cameraY + AizBattleshipPropellerInstance.SECONDARY_Y - effectiveSecondaryY,
                    0, 0, 0, false, 0);
            AizBattleshipPropellerInstance propeller = spawnChild(
                    () -> new AizBattleshipPropellerInstance(spawn, this, secondaryX));
            if (propeller == null || propeller.isDestroyed()) {
                return;
            }
        }
    }

    private boolean bombDelayUnderflowed() {
        return --delayTimer < 0;
    }

    private boolean admitStaggeredPorts() {
        if (!difficultyBombsAreLive()) return false;
        if (S3kRuntimeStates.resolvePlayerCharacter(services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES) return false;
        var manager = services().renderManager();
        var renderer = manager == null ? null : manager.getRenderer(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE);
        return renderer != null && renderer.isReady();
    }

    boolean difficultyBombsAreLive() {
        var module = services().gameModule();
        return getSlotIndex() >= 0 && !isDestroyed() && module != null
                && module.getGameService(AizBombingRunRecipe.class) == AizBombingRunRecipe.STAGGERED_PORTS;
    }

    private void spawnBomb(int screenX, int worldY, int bombScriptX, boolean difficultyAttack) {
        // Spawn with the translated world position for bookkeeping; the bomb itself
        // continues translating from the ship's live secondary-camera coordinates.
        int cameraX = services().camera().getX();
        // ROM Obj_AIZBattleshipMain creates bombs with AllocateObjectAfterCurrent
        // (sonic3k.asm:105361), so bomb slots must follow the ship's slot.
        spawnChild(() -> new AizShipBombInstance(
                new ObjectSpawn(cameraX + screenX, worldY, 0, 0, 0, false, 0),
                this, bombScriptX, worldY, difficultyAttack));
    }

    private void updateSecondaryCameraY(int shipX) {
        if (shipX < SHIP_RISE_TRIGGER_X) {
            int delta = SHIP_RISE_TRIGGER_X - shipX;
            effectiveSecondaryY = baseSecondaryY + (delta >> 1);
            return;
        }
        int bobIndex = (shipX >> 2) & 0xF;
        effectiveSecondaryY = baseSecondaryY + BOBBING_MOTION[bobIndex];
    }

    private void onShipExited() {
        finished = true;
        int shipX = shipXFixed >> 16;

        // ROM Obj_AIZBattleshipMain (sonic3k.asm:105373-105384): the ship rewrites
        // its OWN SST to Obj_AIZ2BossSmall, sets Events_fg_4, and allocates
        // Obj_AIZ2MakeTree with AllocateObjectAfterCurrent. The rewritten slot has
        // already been passed this frame, so the small craft's init first runs on
        // the next pass; MakeTree takes the next free SST after the ship and runs
        // later in this one.
        var objectManager = services().objectManager();
        // AllocateObjectAfterCurrent searches above the ship's SST. Allocate the
        // independent sibling while that identity is still held, before replacing
        // the ship at the same SST; this preserves the ROM's allocation result.
        spawnAfterCurrentSibling(AizBgTreeSpawnerInstance::new);
        int shipSlot = ObjectLifetimeOps.detachSlotForTransfer(this);
        ObjectLifetimeOps.expireDynamic(this);
        if (objectManager != null) {
            ObjectLifetimeOps.addReplacementAtTransferredSlot(
                    objectManager, new AizBossSmallInstance(), shipSlot);
        }
        S3kAizEventWriteSupport.onBattleshipComplete(services());

        LOG.info("AIZ2 battleship: ship exited at shipX=0x" + Integer.toHexString(shipX)
                + " after " + frameCounter + " frames, " + scriptIndex + " bombs dropped");
    }

    @Override
    public int getX() { return shipXFixed >> 16; }

    @Override
    public int getY() { return 0; }

    public int getSecondaryCameraX() {
        return shipXFixed >> 16;
    }

    public int getSecondaryCameraY() {
        return effectiveSecondaryY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The battleship manipulates the background plane in the ROM and does not
        // render as an individual sprite. No rendering needed here.
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }

    @Override
    public int getPriorityBucket() { return 4; }
}
