package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ZeroArgRewindRecreatable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Spawner for AIZ2 parallax background trees during the post-bombing transition.
 *
 * <p>ROM: Obj_AIZ2MakeTree (docs/skdisasm/sonic3k.asm:105553-105583).
 * It waits until Camera_X_pos >= $44D0, records Events_fg_1 ($2E) and the
 * script start ($30), and falls into loc_50632 in the same pass. Every pass
 * first tests the next AIZMakeTreeScript word: the $FFFF terminator deletes
 * the maker, so it outlives its last tree by one pass (:105564-105568).
 * Otherwise loc_50640 (:105571-105580) makes at most one allocation attempt.
 * Once the unsigned 16-bit Events_fg_1 delta reaches the entry's threshold
 * ({@code cmp.w (a2)+,d0 / blo}), AllocateObjectAfterCurrent places one
 * {@link AizBgTreeInstance} after the maker. {@code move.w (a2)+,8(a1)}
 * writes the entry's second word as the tree's sprite priority. A failed
 * allocation ({@code bne.s locret_50662}) skips the $30 write, so the same
 * entry is retried on the next pass.
 *
 * <p>Allocated after the leaving {@link AizBattleshipInstance} with
 * AllocateObjectAfterCurrent (Obj_AIZBattleshipMain, sonic3k.asm:105334-105337).
 */
public class AizBgTreeSpawnerInstance extends AbstractObjectInstance
        implements ZeroArgRewindRecreatable {
    private static final Logger LOG = Logger.getLogger(AizBgTreeSpawnerInstance.class.getName());

    /** Camera X threshold to activate the spawner. ROM: cmpi.w #$44D0,(Camera_X_pos).w. */
    private static final int ACTIVATION_CAMERA_X = 0x44D0;

    /**
     * AIZMakeTreeScript in the locked-on ROM (S3 half, Lockon S3/LockOn Pointers.asm:151
     * {@code ds.b $46}): (Events_fg_1 delta threshold, sprite priority word) pairs closed by a
     * negative word ($FFFF). Read through the ROM pipeline one entry per pass, as the routine
     * walks its $30 pointer, rather than from a copied table.
     */
    private static final int MAKE_TREE_SCRIPT_ADDR = 0x23C13C;
    private static final int SCRIPT_ENTRY_BYTES = 4;

    /** True once camera has reached the activation threshold. */
    private boolean activated;

    /** ROM $2E: Events_fg_1 (engine smooth scroll X) recorded at activation. */
    private int activationSmoothScrollX;

    /**
     * ROM $30 as an entry index. It advances only after a successful
     * allocation; the entry it reaches after the last tree is the terminator.
     */
    private int scriptIndex;

    public AizBgTreeSpawnerInstance() {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "AIZ2MakeTree");
        this.activated = false;
        this.scriptIndex = 0;
    }

    /** Probe constructor for generic restore paths. Delegates to the zero-arg constructor. */
    AizBgTreeSpawnerInstance(ObjectSpawn spawn) {
        this();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) return;

        AizZoneRuntimeState state = currentAizState();
        if (state == null) {
            return;
        }

        // Wait for camera to reach the activation point
        if (!activated) {
            int cameraX = services().camera().getX();
            if (cameraX < ACTIVATION_CAMERA_X) {
                return;
            }
            activated = true;
            activationSmoothScrollX = state.getBattleshipSmoothScrollX();
            LOG.info("AIZ2 tree spawner: activated at cameraX=0x"
                    + Integer.toHexString(cameraX)
                    + ", smoothX=0x" + Integer.toHexString(activationSmoothScrollX));
        }

        // loc_50632: tst.w (a2) / bpl -- a negative word ends the script and
        // deletes the maker before any allocation attempt (:105564-105568).
        int entry = scriptIndex * SCRIPT_ENTRY_BYTES;
        int threshold = scriptWord(entry);
        if ((threshold & 0x8000) != 0) {
            LOG.info("AIZ2 tree spawner: script terminator reached, removing");
            setDestroyed(true);
            return;
        }

        // loc_50640: sub.w $2E(a0),d0 / cmp.w (a2)+,d0 / blo -- an unsigned
        // word compare of the Events_fg_1 delta, one entry per pass.
        int currentSmooth = state.getBattleshipSmoothScrollX();
        int scrollDistance = (currentSmooth - activationSmoothScrollX) & 0xFFFF;
        if (scrollDistance < threshold) {
            return;
        }

        int priorityWord = scriptWord(entry + 2);
        AizBgTreeInstance tree = spawnChild(() -> new AizBgTreeInstance(currentSmooth, priorityWord));
        // AllocateObjectAfterCurrent failure (bne.s locret_50662) leaves $30 unchanged.
        if (tree == null || tree.isDestroyed()) {
            return;
        }
        scriptIndex++;
    }

    /**
     * The spawner is invisible; it only spawns trees.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No-op: spawner has no visual representation
    }

    /**
     * ROM Obj_AIZ2MakeTree does not tail-call MarkObjGone. It remains alive until
     * AIZMakeTreeScript reaches its terminator, even though its engine spawn anchor
     * is not near the camera during the forest sequence.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    private int scriptWord(int byteOffset) {
        try {
            byte[] word = services().rom().readBytes(MAKE_TREE_SCRIPT_ADDR + byteOffset, 2);
            return ((word[0] & 0xFF) << 8) | (word[1] & 0xFF);
        } catch (IOException e) {
            throw new UncheckedIOException("AIZMakeTreeScript read at entry offset " + byteOffset, e);
        }
    }

    private AizZoneRuntimeState currentAizState() {
        return S3kRuntimeStates.currentAiz(services().zoneRuntimeRegistry()).orElse(null);
    }
}
