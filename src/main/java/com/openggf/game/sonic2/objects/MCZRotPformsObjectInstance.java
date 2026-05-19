package com.openggf.game.sonic2.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x6A - MCZ Rotating Platforms / MTZ Moving Platforms.
 * <p>
 * In MCZ (mystic_cave_zone): Large wooden crates (64x64) that move continuously
 * once initialised (routine 4 = loc_27C66 — no standing gate). Subtype 0x18
 * spawns 2 child platforms at (+/-64, +64) with subtypes 6 and C.
 * <p>
 * In MTZ (metropolis_zone): Uses level art, smaller y_radius, faster four-phase
 * cycle. Movement is gated on the player walking off (routine 2 = loc_27BDE).
 * Subtype 0x18 child-spawning is skipped (Obj6A_Init branches to loc_27BC4 at
 * s2.asm:53697 before the child-spawn block).
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 53670-53871 (Obj6A code + tables).
 * <p>
 * <b>Movement Tables (each entry is 3 words = 6 bytes: x_vel, y_vel, duration):</b>
 * <ul>
 *   <li>byte_27CDC: MTZ - 4 phases (s2.asm:53853)</li>
 *   <li>byte_27CF4: MCZ counter-clockwise (no x_flip) - 5 declared, 4 active (s2.asm:53859)</li>
 *   <li>byte_27D12: MCZ clockwise (x_flip set) - 5 declared, 4 active (s2.asm:53866)</li>
 * </ul>
 * Phase advance wraps at {@code cmpi.b #6*4,objoff_38} (s2.asm:53846), so only
 * the first 4 entries of each table are reachable through normal cycling.
 * <p>
 * <b>Property table indexing pitfall:</b> ROM stores the full {@code subtype}
 * byte into {@code objoff_38} (s2.asm:53750), then uses it as a byte offset
 * into the velocity table. The engine must NOT collapse this to {@code subtype
 * & 0x0F} or treat the index as an array index. Initial subtypes from layout
 * (0, 6, 0xC, 0x18) directly land on table entries 0, 1, 2 (and 3-via-wrap for
 * the parent's first phase load).
 */
public class MCZRotPformsObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(MCZRotPformsObjectInstance.class.getName());

    // Movement tables -- format: {x_vel, y_vel, duration} per phase (16-bit
    // signed values, x_vel/y_vel in 16.8 subpixel-per-frame fixed point).

    // byte_27CDC: MTZ - 4 active phases (faster down/turn cycle).
    private static final int[][] MOVE_TABLE_MTZ = {
            {0x0000, 0x0400, 0x10},
            {0x0400, -0x0200, 0x20},
            {0x0000, 0x0400, 0x10},
            {-0x0400, -0x0200, 0x20},
    };

    // byte_27CF4: MCZ counter-clockwise (no x_flip) - 4 active phases.
    // ROM wraps at cmpi.b #6*4,objoff_38 so phase 4 (5th entry) is unreachable
    // by normal cycling; include it so a starting objoff_38 of 0x18 reads it
    // before the wrap.
    private static final int[][] MOVE_TABLE_MCZ_CCW = {
            {0x0000, 0x0100, 0x40},
            {-0x0100, 0x0000, 0x80},
            {0x0000, -0x0100, 0x40},
            {0x0100, 0x0000, 0x80},
            {0x0100, 0x0000, 0x40},
    };

    // byte_27D12: MCZ clockwise (x_flip set) - 4 active phases (+ unreachable 5th).
    private static final int[][] MOVE_TABLE_MCZ_CW = {
            {0x0000, 0x0100, 0x40},
            {0x0100, 0x0000, 0x80},
            {0x0000, -0x0100, 0x40},
            {-0x0100, 0x0000, 0x80},
            {-0x0100, 0x0000, 0x40},
    };

    // Collision parameters from disassembly.
    //   MTZ (s2.asm:53692-53693): width_pixels = 0x20, y_radius = 0x0C
    //   MCZ (s2.asm:53701-53702): width_pixels = 0x20, y_radius = 0x20
    // Collision half-width = width_pixels + 0x0B (s2.asm:53797-53798 / 53822-53823).
    private static final int WIDTH_PIXELS = 0x20;
    private static final int MTZ_Y_RADIUS = 0x0C;
    private static final int MCZ_Y_RADIUS = 0x20;
    private static final int HALF_WIDTH = WIDTH_PIXELS + 0x0B;

    // Phase wrap threshold: s2.asm:53846 -- cmpi.b #6*4,objoff_38.
    private static final int PHASE_WRAP_THRESHOLD = 24;

    // Position state (16.8 fixed point for subpixel accuracy).
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;

    // Original spawn position (objoff_32, objoff_30 in disassembly).
    private final int baseX;
    private final int baseY;

    // Movement state.
    private int phaseIndex;         // objoff_38: byte-offset cursor into objoff_2C table
    private int phaseDuration;      // objoff_34: frame counter for current phase
    private boolean activated;      // objoff_36: MTZ gate -- 0 = wait, 1 = move
    private int prevStandingFlags;  // objoff_3C: previous frame's player standing status

    // Configuration captured at construct time.
    private final boolean isMtz;
    private final int[][] moveTable;
    private final int yRadius;
    private final boolean xFlip;
    private final boolean yFlip;
    private final boolean isParent;  // Only MCZ subtype 0x18 acts as parent.

    // Child tracking for cleanup on unload.
    private final List<MCZRotPformsObjectInstance> children = new ArrayList<>();
    private boolean childrenSpawned;

    // ROM parity: Obj6A_Init (s2.asm:53686-53751) sets up phase params via
    // loc_27CA2 but does NOT call ObjectMove. The first move happens on the
    // NEXT frame when the appropriate routine (2 for MTZ, 4 for MCZ) runs.
    // Without this skip, the engine's spawn frame applies an extra move because
    // syncActiveSpawnsLoad runs before runExecLoop, so the constructor's phase
    // load + the same-frame update() call would move twice relative to ROM's
    // init-then-next-frame ordering.
    private boolean spawnFrameSkipPending;

    public MCZRotPformsObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // Zone determines which table, y_radius, and gating behavior to use.
        // s2.asm:53696 -- cmpi.b #mystic_cave_zone,(Current_Zone).w
        // services().currentZone() returns the ROM zone id.
        int zoneId = services().currentZone();
        this.isMtz = (zoneId != Sonic2ZoneConstants.ROM_ZONE_MCZ);

        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.yFlip = (spawn.renderFlags() & 0x02) != 0;

        if (isMtz) {
            this.moveTable = MOVE_TABLE_MTZ;
            this.yRadius = MTZ_Y_RADIUS;
            // MTZ skips the subtype-0x18 child-spawn block (bne.w loc_27BC4
            // at s2.asm:53697 jumps past lines 53698-53731).
            this.isParent = false;
        } else {
            // MCZ selects table from x_flip (s2.asm:53703-53706).
            this.moveTable = xFlip ? MOVE_TABLE_MCZ_CW : MOVE_TABLE_MCZ_CCW;
            this.yRadius = MCZ_Y_RADIUS;
            this.isParent = (spawn.subtype() == 0x18);
        }

        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.x = baseX;
        this.y = baseY;
        this.xFixed = x << 8;
        this.yFixed = y << 8;

        // ROM s2.asm:53750 -- move.b subtype(a0),objoff_38(a0).
        // The FULL subtype byte is the byte-offset cursor, not subtype & 0x0F.
        // Subtypes 0, 6, 0xC, 0x18 land on table entries 0, 1, 2, (3-via-wrap).
        this.phaseIndex = spawn.subtype() & 0xFF;
        this.phaseDuration = 0;
        // Activation routing matches ROM routine selection:
        //   MTZ (routine 2, loc_27BDE): wait for player to walk off.
        //   MCZ (routine 4, loc_27C66): move unconditionally from the start.
        this.activated = !isMtz;
        this.prevStandingFlags = 0;
        this.xVel = 0;
        this.yVel = 0;

        // Init's tail call to loc_27CA2 (s2.asm:53751) preloads the first
        // phase's velocity/duration and advances objoff_38 by 6.
        loadPhaseParameters();

        this.childrenSpawned = false;
        // Suppress the spawn-frame move; see field docstring above.
        this.spawnFrameSkipPending = true;

        updateDynamicSpawn(x, y);

        LOGGER.fine(() -> String.format(
                "Obj6A init: pos=(%d,%d), subtype=0x%02X, xFlip=%b, isParent=%b, isMtz=%b",
                baseX, baseY, spawn.subtype(), xFlip, isParent, isMtz));
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // s2.asm:53797-53802 (MTZ) and 53822-53827 (MCZ):
        //   d1 = width_pixels + 0x0B  -> collision half-width
        //   d2 = y_radius              -> collision half-height (top)
        //   d3 = y_radius + 1          -> collision half-height (bottom)
        return new SolidObjectParams(HALF_WIDTH, yRadius, yRadius + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        // ROM uses JmpTo13_SolidObject (fully solid from all sides).
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        // MCZ subtype 0x18 parents don't render and don't collide (children do).
        return !isDestroyed() && !isParent;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // Standing detection is polled in update() via ObjectManager.isAnyPlayerRiding.
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        // MCZ subtype 0x18 parents do not render/collide; they exist to spawn
        // child platforms once on the spawn frame.
        if (isParent) {
            ensureChildrenSpawned();
            return;
        }

        // ROM parity: Obj6A_Init returns without calling ObjectMove. Skip the
        // first update() invocation so the constructor's loadPhaseParameters()
        // does not combine with a same-frame move.
        if (spawnFrameSkipPending) {
            spawnFrameSkipPending = false;
            updateDynamicSpawn(x, y);
            return;
        }

        boolean playerStanding = isPlayerStandingOnUs();

        if (isMtz) {
            updateMtz(playerStanding);
        } else {
            updateMcz();
        }

        updateDynamicSpawn(x, y);
    }

    /**
     * MTZ routine 2 (loc_27BDE @ s2.asm:53754): wait for the player to walk off,
     * then cycle through the movement table from the next frame onward.
     */
    private void updateMtz(boolean playerStanding) {
        if (activated) {
            // loc_27C2E (s2.asm:53786): move, decrement, advance phase on hit.
            applyMovement();
        } else {
            // Standing-state edge detection (lines 53756-53783): activation
            // triggers when prev_standing was set but current is clear.
            if (!playerStanding && prevStandingFlags != 0) {
                activated = true;
            }
            // ROM sets objoff_36=1 same frame but branches via loc_27C3E,
            // skipping the move; equivalent here to "activate but don't move
            // this frame". The first move happens next update.
        }
        prevStandingFlags = playerStanding ? 1 : 0;
    }

    /**
     * MCZ routine 4 (loc_27C66 @ s2.asm:53810): move unconditionally every frame.
     */
    private void updateMcz() {
        applyMovement();
    }

    /**
     * Apply velocity (16.8) and tick duration. When the duration expires,
     * advance to the next phase via {@link #loadPhaseParameters}.
     */
    private void applyMovement() {
        // ROM: jsr (ObjectMove).l adds x_vel/y_vel to x_pos/y_pos.
        xFixed += xVel;
        yFixed += yVel;
        x = xFixed >> 8;
        y = yFixed >> 8;

        // s2.asm:53788 -- subq.w #1,objoff_34(a0); bne.s loc_27C3E.
        phaseDuration--;
        if (phaseDuration <= 0) {
            loadPhaseParameters();
        }
    }

    /**
     * Implements loc_27CA2 (s2.asm:53835): read the current 6-byte entry from
     * the velocity table at byte offset {@code phaseIndex}, advance the cursor
     * by 6, and wrap at {@code 6*4 = 24}.
     */
    private void loadPhaseParameters() {
        // ROM uses the raw byte offset to index a packed word table. Each
        // entry is 6 bytes (3 words), so the array index is phaseIndex / 6.
        int entryIndex = phaseIndex / 6;

        if (entryIndex >= 0 && entryIndex < moveTable.length) {
            int[] phase = moveTable[entryIndex];
            xVel = phase[0];
            yVel = phase[1];
            phaseDuration = phase[2];
        } else {
            // Defensive: indices > table length only occur if the subtype
            // byte was set beyond the cycle range; mirror ROM by wrapping.
            xVel = 0;
            yVel = 0;
            phaseDuration = 0;
        }

        // s2.asm:53845-53848: addq.b #6,objoff_38; cmpi.b #6*4,objoff_38;
        // blo continue; else move.b #0,objoff_38.
        phaseIndex += 6;
        if (phaseIndex >= PHASE_WRAP_THRESHOLD) {
            phaseIndex = 0;
        }
    }

    private boolean isPlayerStandingOnUs() {
        ObjectManager manager = services().objectManager();
        return manager != null && manager.isAnyPlayerRiding(this);
    }

    /**
     * MCZ subtype 0x18 (s2.asm:53711-53729): spawn two child platforms at
     * (+/-64, +64) with subtypes 6 and C. The pairing depends on x_flip.
     */
    private void ensureChildrenSpawned() {
        if (!childrenSpawned && spawnChildren()) {
            childrenSpawned = true;
        }
    }

    private boolean spawnChildren() {
        ObjectManager manager = services().objectManager();
        if (manager == null) {
            return false;
        }

        int child1Subtype = xFlip ? 0x0C : 0x06;
        ObjectSpawn child1Spawn = new ObjectSpawn(
                baseX + 0x40,
                baseY + 0x40,
                spawn.objectId(),
                child1Subtype,
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
        MCZRotPformsObjectInstance child1 = spawnChild(
                () -> new MCZRotPformsObjectInstance(child1Spawn, "MCZRotPforms"));
        children.add(child1);

        int child2Subtype = xFlip ? 0x06 : 0x0C;
        ObjectSpawn child2Spawn = new ObjectSpawn(
                baseX - 0x40,
                baseY + 0x40,
                spawn.objectId(),
                child2Subtype,
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
        MCZRotPformsObjectInstance child2 = spawnChild(
                () -> new MCZRotPformsObjectInstance(child2Spawn, "MCZRotPforms"));
        children.add(child2);
        return true;
    }

    @Override
    public void onUnload() {
        for (MCZRotPformsObjectInstance child : children) {
            child.setDestroyed(true);
        }
        children.clear();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isParent) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        // Only MCZ uses the wooden-crate Nemesis art (ArtNem_Crate). MTZ uses
        // level art (ArtTile_ArtKos_LevelArt at mapping_frame=1) which we do
        // not yet render -- the platform is treated as invisible for now.
        if (isMtz) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_CRATE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, xFlip, yFlip);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int left = x - HALF_WIDTH;
        int right = x + HALF_WIDTH;
        int top = y - yRadius;
        int bottom = y + yRadius + 1;

        float r = 0.2f, g = 0.8f, b = 0.2f;
        if (activated && isMtz) {
            r = 0.8f;
            g = 0.8f;
            b = 0.2f;
        }

        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);
        ctx.drawLine(right, bottom, left, bottom, r, g, b);
        ctx.drawLine(left, bottom, left, top, r, g, b);

        ctx.drawLine(x - 4, y, x + 4, y, 0.0f, 1.0f, 1.0f);
        ctx.drawLine(x, y - 4, x, y + 4, 0.0f, 1.0f, 1.0f);
    }
}
