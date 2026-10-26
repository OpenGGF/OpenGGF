package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.ROUTINE;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.SPRITE_FLAGS;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * Pure staged ObjSpinTunnel tag ($34), not TunnelPath or either door body.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/_Objects/Spin Tunnel/Main (Tag).asm:7-188; Object Spawner.asm:385-446.
 * Shipped FixBugs=0 behavior; these owning routines have no conditional fixed alternative.
 *
 * <p>Root preflights and publishes the complete expected object/player cut before calling the existing
 * shared DrawObject when requested. Root MUST then reload the LIVE slot and pass that post-draw image
 * to {@link #despawn}: DrawObject can clear sprite bit7. Neither result authenticates a call or grants
 * publication authority. Root owns phase/nonreentry, pool and expected-motion checks, the real 766-byte
 * placement-bank preflight, loaded-bit clearing and full 64-byte DeleteObject.</p>
 *
 * <p>Only immutable Assets is retained. No pool, snapshot, Frame, Runtime, callback, sound, probe,
 * animation service or Body rerun is performed or retained. Results preserve unowned bytes/fields;
 * their exact store sets include executed stores whose values were unchanged.</p>
 */
public final class CdSpinTunnelTag {
    public static final int OBJECT_ID = 0x34;
    private static final int MAPPING_BYTES = 274;

    /** An explicit pointer declaration alone authenticates no media or native operand. */
    public record Assets(int mappingsAddress) {
        public Assets {
            if (mappingsAddress <= 0 || (mappingsAddress & 1) != 0
                    || mappingsAddress > 0xFFFFFF - (MAPPING_BYTES - 1)) {
                throw new IllegalArgumentException("Powerup mappings need a nonzero aligned bounded native pointer");
            }
        }

        /**
         * Authenticated-owner ingress; retains only the raw view's native address, with no uniform
         * mapping decode, embedded bytes, guessed address or fallback. Compile prerequisite: Root's
         * separately owned Table.POWERUP_MAPPINGS binding for the exact raw 274-byte include.
         */
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "object assets");
            var view = owner.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS);
            if (view.range().length() != MAPPING_BYTES || view.bytes().length() != MAPPING_BYTES) {
                throw new IllegalArgumentException("Powerup mappings must retain the complete raw 274-byte view");
            }
            return new Assets(view.range().nativeAddress());
        }
    }

    /** Full native words; TST.B at either big-endian debug symbol reads its HIGH byte. */
    public record Gates(int zoneByte, int debugModeWord, int debugCheatWord, int rawHeldByte) {
        public Gates {
            byteValue(zoneByte); wordValue(debugModeWord); wordValue(debugCheatWord); byteValue(rawHeldByte);
        }
    }

    /** The complete native store union. CENTRE_Y_WORD never authorizes a fraction write. */
    public enum PlayerField {
        CENTRE_Y_WORD, X_VELOCITY, Y_VELOCITY, GROUND_VELOCITY, FLAGS, Y_RADIUS, X_RADIUS, ANIMATION
    }

    /** Detached values, not a generic patch/capture owner. Root validates preservation and store authority. */
    public record PlayerResult(CdPlayerMotionState expectedMotion, CdPlayerMotionState finalMotion,
                               Set<PlayerField> executedWrites) {
        public PlayerResult {
            Objects.requireNonNull(expectedMotion, "expected motion");
            Objects.requireNonNull(finalMotion, "final motion");
            executedWrites = Set.copyOf(Objects.requireNonNull(executedWrites, "executed stores"));
        }
    }

    /** Source prefix only. Draw/despawn have not run; the object is never written back after shared draw. */
    public record Result(CdObjectState expectedObject, CdObjectState object, PlayerResult player,
                         boolean drawObject) {
        public Result {
            Objects.requireNonNull(expectedObject, "expected object");
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(player, "player");
        }
    }

    /** Raw bytes preserve signed direction and the independent time-warp bit. Camera is pre-scroll. */
    public record DespawnGlobals(int rawTimeZoneByte, int rawTimeWarpDirByte, int cameraXWord) {
        public DespawnGlobals {
            byteValue(rawTimeZoneByte); byteValue(rawTimeWarpDirByte); wordValue(cameraXWord);
        }
    }

    /**
     * Actual reached BCLR index, including out-of-bank values. Without a warp the native low seven-bit
     * period is not clamped. No bank is read/filled here; Root checks this index against its real bank
     * BEFORE either clearing loaded bit7 (preserving bits0..6) or deleting the slot.
     */
    public record LoadedClear(int savedId, int period, int index) {
        public LoadedClear {
            if (savedId < 1 || savedId > 0xFF || period < 0 || period > 0x7F
                    || index != 2 + 3 * savedId + period) {
                throw new IllegalArgumentException("Not the reached native saved-ID/period/index tuple");
            }
        }
    }

    /** A decision, never a performed deletion/clear. Saved ID zero has no LoadedClear. */
    public record DespawnResult(boolean deleteObject, Optional<LoadedClear> loadedClear) {
        public DespawnResult {
            Objects.requireNonNull(loadedClear, "loaded clear");
            if (!deleteObject && loadedClear.isPresent()) {
                throw new IllegalArgumentException("A kept tag cannot request a loaded-bit clear");
            }
        }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH }
    public static final class UnsupportedSpinTunnelTag extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final int value;

        private UnsupportedSpinTunnelTag(CdObjectState object) {
            super("ROUTINE_DISPATCH at Spin tunnel tag whole slot " + object.identity().wholeIndex()
                    + ", reached routine $" + Integer.toHexString(object.routine()));
            boundary = UnsupportedBoundary.ROUTINE_DISPATCH;
            wholeIndex = object.identity().wholeIndex();
            value = object.routine();
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final Assets assets;

    public CdSpinTunnelTag(Assets assets) { this.assets = Objects.requireNonNull(assets, "assets"); }

    /** One actual routine0/2 source prefix, with Init0 falling straight through into Main2. */
    public Result step(CdObjectState beforeObject, CdPlayerMotionState motion, Gates gates) {
        Objects.requireNonNull(beforeObject, "before object");
        Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(gates, "gates");
        if (beforeObject.id() != OBJECT_ID) {
            throw new IllegalArgumentException("Expected Spin tunnel tag object ID $34");
        }
        // Native routine is an unsigned BYTE OFFSET into two words, not a normalized routine number.
        // Stop before Init or any player store for odd offsets and every offset above2 (Tag:7-11,21-23).
        if (beforeObject.routine() != 0 && beforeObject.routine() != 2) {
            throw new UnsupportedSpinTunnelTag(beforeObject);
        }

        var object = beforeObject;
        if (object.routine() == 0) {
            // ObjSpinTunnel_Init (Tag:26-32). No RTS; no other a0 byte is initialized.
            object = object.addByte(ROUTINE, 2)
                    .moveByte(SPRITE_FLAGS, object.spriteFlags() | 4)
                    .moveWord(TILE, 0x0544)
                    .moveLong(MAPPINGS, assets.mappingsAddress())
                    .moveByte(MAP_FRAME, object.subtype()).addByte(MAP_FRAME, 1);
        }

        CdPlayerMotionState next = motion;
        var stores = EnumSet.noneOf(PlayerField.class);
        // Tag:37-44,166-188. Other actor flags/controls/charge/radii are NOT admission gates.
        if (motion.animation() != 0x2B && motion.routine() < 6
                && (gates.debugModeWord() >>> 8) == 0
                && inRange(motion.centreX16_16() >>> 16, object.centreX())
                && inRange(motion.centreY16_16() >>> 16, object.centreY())) {
            int xv = motion.xVelocity();
            int yv = motion.yVelocity();
            int gv = motion.groundVelocity();
            int flags = motion.flags();
            int xr = motion.xRadius();
            int yr = motion.yRadius();
            int animation = motion.animation();
            int y = motion.centreY16_16();
            int subtype = object.subtype();

            if (subtype == 0) {
                // .AbsVX/.GotXCap/.CheckXSign (Tag:47-76): unsigned minimum, not maximum.
                xv = minimumMagnitude(xv, gates.zoneByte() == 5 ? 0xD00 : 0xA00);
                gv = xv;
                int sector = ((motion.angle() + 0x20) & 0xFF) & 0xC0;
                if (sector == 0x80) gv = (short) -gv;
                stores.add(PlayerField.X_VELOCITY);
                stores.add(PlayerField.GROUND_VELOCITY);
            } else if (subtype == 1) {
                // .Subtype1/.CheckYSign (Tag:80-102).
                yv = minimumMagnitude(yv, 0xD00);
                gv = yv;
                flags |= 2;
                stores.add(PlayerField.Y_VELOCITY);
                stores.add(PlayerField.GROUND_VELOCITY);
            } else if (subtype == 4) {
                // .Subtype4 jumps DIRECTLY to .CheckYSign (Tag:155-161,99-102).
                // The old Y sign is never reapplied to this negative immediate.
                if (xv >= 0 && (gates.rawHeldByte() & 1) != 0) {
                    yv = -0xA00;
                    gv = -0xA00;
                    flags |= 2;
                    stores.add(PlayerField.Y_VELOCITY);
                    stores.add(PlayerField.GROUND_VELOCITY);
                }
            } else if (subtype == 2 ? yv < 0 : yv >= 0) {
                // .Subtype2/.Subtype3/.ChkLaunch (Tag:117-151). Every other unsigned subtype
                // >=2, including5..255, follows Subtype3. Raw Right wins over raw Left.
                int launch = (gates.rawHeldByte() & 8) != 0 ? 0xD00
                        : (gates.rawHeldByte() & 4) != 0 ? -0xD00 : 0;
                if (launch != 0) {
                    if (subtype != 2) flags |= 2;
                    xv = launch;
                    gv = launch;
                    stores.add(PlayerField.X_VELOCITY);
                    stores.add(PlayerField.GROUND_VELOCITY);
                }
            }

            // .SetRoll (Tag:104-113). BSET is an executed store even if already set.
            boolean wasRolling = (flags & 4) != 0;
            flags |= 4;
            stores.add(PlayerField.FLAGS);
            if (!wasRolling) {
                yr = 0x0E;
                xr = 7;
                int integerY = ((y >>> 16) + 5) & 0xFFFF;
                y = (integerY << 16) | (y & 0xFFFF);
                animation = 2;
                stores.add(PlayerField.Y_RADIUS);
                stores.add(PlayerField.X_RADIUS);
                stores.add(PlayerField.CENTRE_Y_WORD);
                stores.add(PlayerField.ANIMATION);
            }
            next = new CdPlayerMotionState(motion.centreX16_16(), y, xv, yv, gv,
                    motion.routine(), motion.angle(), flags, motion.spriteFlags(), xr, yr,
                    animation, motion.previousAnimation(), motion.playerJump(), motion.charge(), motion.moveLock(),
                    motion.stick(), motion.playerControl(), motion.previousPrimaryAngle(), motion.previousSecondaryAngle());
        }
        // The wrapper's TST.B debugCheat follows EVERY returning Main path (Tag:12-17).
        return new Result(beforeObject, object, new PlayerResult(motion, next, stores),
                (gates.debugCheatWord() >>> 8) != 0);
    }

    /**
     * Pure second cut of CheckObjDespawn, Object Spawner.asm:385-446. Supply the reloaded live image
     * AFTER optional shared DrawObject; never reuse step's stale sprite bit7. There is no viewport fence
     * or original-anchor substitution. No bank/pool access occurs, including at out-of-range indices.
     */
    public DespawnResult despawn(CdObjectState afterDrawLiveImage, DespawnGlobals globals) {
        Objects.requireNonNull(afterDrawLiveImage, "post-draw live image");
        Objects.requireNonNull(globals, "despawn globals");
        boolean warp = (globals.rawTimeZoneByte() & 0x80) != 0;
        boolean distanceDelete = false;
        if ((afterDrawLiveImage.spriteFlags() & 0x80) == 0) {
            int cameraChunk = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
            int distance = ((afterDrawLiveImage.centreX() & 0xFF80) - cameraChunk) & 0xFFFF;
            distanceDelete = distance > 0x280; // BLS keeps equality; unsigned WORD, fixed native320 constant.
        }
        if (!distanceDelete && !warp) return new DespawnResult(false, Optional.empty());
        int savedId = afterDrawLiveImage.savedFlagsId();
        if (savedId == 0) return new DespawnResult(true, Optional.empty());

        int period = globals.rawTimeZoneByte() & 0x7F;
        if (warp) {
            // EXT.W timeWarpDir, NEG.W, ADD.W; clamp ONLY this time-warp branch (Spawner:417-432).
            period = (short) (period - (byte) globals.rawTimeWarpDirByte());
            if (period < 0) period = 0;
            else if (period >= 3) period = 2;
        }
        return new DespawnResult(true, Optional.of(new LoadedClear(savedId, period, 2 + 3 * savedId + period)));
    }

    /** Tag:166-188; signed BMI followed by unsigned BCC on the wrapped distance word. */
    private static boolean inRange(int playerWord, int objectWord) {
        int distance = (playerWord - objectWord + 0x28) & 0xFFFF;
        return (short) distance >= 0 && distance < 0x50;
    }

    /** Native NEG.W $8000 remains $8000; unsigned magnitude comparison must preserve that case. */
    private static int minimumMagnitude(int velocity, int minimum) {
        int magnitude = velocity < 0 ? (short) -velocity : velocity;
        if ((magnitude & 0xFFFF) < minimum) magnitude = minimum;
        return velocity < 0 ? (short) -magnitude : (short) magnitude;
    }

    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
