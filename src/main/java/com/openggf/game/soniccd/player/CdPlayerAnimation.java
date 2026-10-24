package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Finite R11A ObjSonic_Animate owner, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Consumes already decoded disc-backed assets; no source-data fallback, art loading or clock ownership.
 * Call once only when the production player actually calls Animate, never to prime initialization.
 * This is source-level behavior, with no instruction-phase, cycle or parity claim.
 */
public final class CdPlayerAnimation {
    // Ani_Sonic index, Data/Animations.asm:2-6,35-41,51,55-56. These select
    // the existing decoded script objects; frame bytes are never duplicated here.
    private static final int WALK = 0, RUN = 1, ROLL = 2, ROLL_FAST = 3, PUSH = 4;
    private static final int WALK_MINI = 0x21, RUN_MINI = 0x22, ROLL_MINI = 0x23, PUSH_MINI = 0x27;
    private static final int PEELOUT = 0x31, RUN_3D = 0x35, ROLL_3D = 0x36;

    /**
     * The four owned RAM bytes: oPrevAnim, oAnimFrame, oAnimTime, oMapFrame.
     * Values are unsigned byte contents, including timer/index values with bit 7 set.
     * This immutable value is the capture/restore boundary. There is no default/primed state;
     * the caller supplies real RAM initialization and any external oAnimFrame selection.
     * mapFrame is independent of lastResident/dirty and causes no DPLC request.
     */
    public record State(int previousAnimation, int scriptIndex, int timer, int mapFrame) {
        public State {
            requireByte(previousAnimation, "oPrevAnim");
            requireByte(scriptIndex, "oAnimFrame");
            requireByte(timer, "oAnimTime");
            requireByte(mapFrame, "oMapFrame");
        }
    }

    /** Current production oAnim, signed oPlayerGVel word, oAngle, oFlags, oSprFlags, oPlayerCtrl, miniSonic. */
    public record Input(int animation, int groundVelocity, int angle, int flags,
                        int spriteFlags, int playerControl, int miniSonic) {
        public Input {
            requireByte(animation, "oAnim");
            if (animation >= CdR11aPlayerAssets.ANIMATION_COUNT) {
                throw new IllegalArgumentException("oAnim outside the 60 admitted R11A scripts");
            }
            if (groundVelocity != (short) groundVelocity) {
                throw new IllegalArgumentException("oPlayerGVel must be a signed word");
            }
            requireByte(angle, "oAngle");
            requireByte(flags, "oFlags");
            requireByte(spriteFlags, "oSprFlags");
            requireByte(playerControl, "oPlayerCtrl");
            requireByte(miniSonic, "miniSonic");
        }
    }

    /**
     * Only source-owned actor writes, to apply at this call boundary.
     * Empty optionals mean no write. miniRollHitboxWrite means precisely
     * BSET #2,oFlags; oYRadius=$A; oXRadius=5 (Main.asm:2918-2922).
     * It neither shifts position nor starts a movement routine. Other actor/global fields are untouched.
     */
    public record Effects(OptionalInt animationWrite, OptionalInt spriteFlagsWrite, boolean miniRollHitboxWrite) {
        public Effects {
            Objects.requireNonNull(animationWrite, "animationWrite");
            Objects.requireNonNull(spriteFlagsWrite, "spriteFlagsWrite");
        }
    }

    public record Result(State state, Effects effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private final CdR11aPlayerAssets assets;

    public CdPlayerAnimation(CdR11aPlayerAssets assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    /** Pure transition: neither assets, input nor a captured state is mutated, including on failure. */
    public Result step(State state, Input input) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        requireMapFrame(state.mapFrame());

        // ObjSonic_Animate.Do (Main.asm:2647-2662): compare the original ID,
        // then remap for mini. Toggling mini alone does not reset either byte.
        int index = state.scriptIndex();
        int timer = state.timer();
        if (input.animation() != state.previousAnimation()) {
            index = 0;
            timer = 0;
        }
        int previous = input.animation();
        int scriptId = input.miniSonic() == 0 ? input.animation() : assets.miniAnimationId(input.animation());
        int header = assets.animation(scriptId).signedDelayOrHandler();
        timer = u8(timer - 1);

        if (header >= 0) {
            // Normal animations apply facing even on AniDelay (2664-2673).
            int spriteFlags = horizontalFlags(input);
            if ((byte) timer >= 0) {
                return result(previous, index, timer, state.mapFrame(), OptionalInt.empty(),
                        OptionalInt.of(spriteFlags), false);
            }
            return runScript(previous, index, header, state.mapFrame(), scriptId, 0, spriteFlags, false);
        }

        // SpecialAnim (2719-2727): delay precedes ALL special flag/frame effects.
        if ((byte) timer >= 0) {
            return result(previous, index, timer, state.mapFrame(), OptionalInt.empty(), OptionalInt.empty(), false);
        }

        // Shipped FixBugs=0 behavior. In particular retain the mini angle path's
        // no-op NoFlip2 and byte/word wrap; do not repair it into the normal angle path.
        if (header == -1) {
            if (input.miniSonic() != 0) {
                // MiniSonicRun (2897-2945): no 3D offset and no pushing test.
                int angle = u8(facingAngle(input) + 0x10);
                int spriteFlags = horizontalFlags(input);
                if (u8(angle + 0x30) >= 0x60) {
                    return roll(previous, index, state.mapFrame(), input, true);
                }
                int speed = wordMagnitude(input.groundVelocity());
                int selected = speed >= 0x600 ? RUN_MINI : WALK_MINI;
                return runScript(previous, index, duration(speed, 0x800), state.mapFrame(),
                        selected, 0, spriteFlags, false);
            }

            // Flipped/CheckInvert/NoInvert (2729-2755): ADD.B and its N flag.
            boolean ramp = (input.playerControl() & 2) != 0;
            int angle = u8(facingAngle(input) + (ramp ? 8 : 0x10));
            int flip = (input.flags() & 1) ^ ((byte) angle < 0 ? 3 : 0);
            int spriteFlags = (input.spriteFlags() & 0xFC) | flip;
            if ((input.flags() & 0x20) != 0) {
                return push(previous, index, state.mapFrame(), input);
            }

            // CheckSpeed/GotRunAnim (2757-2804): CMP.W/BCC uses the unsigned
            // magnitude, including NEG.W($8000)==$8000, rather than Java abs.
            int speed = wordMagnitude(input.groundVelocity());
            int selected;
            int offset;
            if (ramp) {
                selected = RUN_3D;
                offset = (((angle >>> 4) << 1) & 0xE) * 2;
            } else {
                int sector = (angle >>> 4) & 6;
                selected = speed >= 0xA00 ? PEELOUT : speed >= 0x600 ? RUN : WALK;
                offset = selected == WALK ? sector * 3 : sector * 2;
            }
            return runScript(previous, index, duration(speed, 0x800), state.mapFrame(),
                    selected, offset, spriteFlags, false);
        }
        if (header == -2) {
            return roll(previous, index, state.mapFrame(), input, false);
        }
        if (header == -3) {
            return push(previous, index, state.mapFrame(), input);
        }
        if (header == -4) {
            // FrozenAnim (2888-2893): a caller-owned index selects a byte;
            // no command dispatch, index advance or sprite-flags write.
            int frame = scriptByte(scriptId, 1 + index);
            return result(previous, index, 0, frame, OptionalInt.empty(), OptionalInt.empty(), false);
        }
        throw new UnsupportedOperationException("Unadmitted R11A animation header " + header);
    }

    private Result roll(int previous, int index, int mapFrame, Input input, boolean miniRollHitbox) {
        // RollAnim/GotRollAnim (2808-2851). Mini selection precedes ramp/speed.
        int speed = wordMagnitude(input.groundVelocity());
        int selected;
        if (input.miniSonic() != 0) {
            selected = ROLL_MINI;
        } else if ((input.playerControl() & 2) != 0) {
            selected = (u8(input.angle() + 0x10) & 0xC0) == 0 ? ROLL_FAST : ROLL_3D;
        } else {
            selected = speed >= 0x600 ? ROLL_FAST : ROLL;
        }
        return runScript(previous, index, duration(speed, 0x400), mapFrame, selected,
                0, horizontalFlags(input), miniRollHitbox);
    }

    private Result push(int previous, int index, int mapFrame, Input input) {
        // PushAnim (2859-2884): BMI retains a negative word; otherwise NEG.W.
        int negative = input.groundVelocity() < 0 ? input.groundVelocity() : (short) -input.groundVelocity();
        int remaining = (short) (negative + 0x800);
        int timer = (remaining < 0 ? 0 : remaining) >>> 6;
        return runScript(previous, index, timer, mapFrame, input.miniSonic() == 0 ? PUSH : PUSH_MINI,
                0, horizontalFlags(input), false);
    }

    private Result runScript(int previous, int index, int timer, int mapFrame, int scriptId,
                             int angleOffset, int spriteFlags, boolean miniRollHitbox) {
        // RunAnimScript/AniFF/AniFE/AniFD (2675-2715). Only FF/FE/FD
        // are commands: high-bit mapping frames, including $80..$C5, are data.
        int value = scriptByte(scriptId, 1 + index);
        OptionalInt animationWrite = OptionalInt.empty();
        if (value == 0xFD) {
            animationWrite = OptionalInt.of(scriptByte(scriptId, 2 + index));
            // No immediate reset, index increment or base map-frame write.
        } else {
            if (value == 0xFF) {
                index = 0;
                value = scriptByte(scriptId, 1);
            } else if (value == 0xFE) {
                int count = scriptByte(scriptId, 2 + index);
                index = u8(index - count); // SUB.B in both oAnimFrame and zero-extended d1.
                value = scriptByte(scriptId, 1 + index);
            }
            mapFrame = value;
            index = u8(index + 1);
        }
        // GotRunAnim calls RunAnimScript then ADD.B even on the FD path.
        mapFrame = u8(mapFrame + angleOffset);
        return result(previous, index, timer, mapFrame, animationWrite,
                OptionalInt.of(spriteFlags), miniRollHitbox);
    }

    private int scriptByte(int scriptId, int offset) {
        CdAssetBytes bytes = assets.animation(scriptId).bytes();
        if (offset < 1 || offset >= bytes.length()) {
            throw new UnsupportedOperationException("Missing admitted animation byte: script " + scriptId
                    + ", body offset " + offset + "; adjacent resident bytes are not an admitted input");
        }
        return bytes.unsignedByte(offset);
    }

    private Result result(int previous, int index, int timer, int mapFrame, OptionalInt animationWrite,
                           OptionalInt spriteFlagsWrite, boolean miniRollHitboxWrite) {
        requireMapFrame(mapFrame);
        return new Result(new State(previous, index, timer, mapFrame),
                new Effects(animationWrite, spriteFlagsWrite, miniRollHitboxWrite));
    }

    private void requireMapFrame(int frame) {
        if (frame >= assets.sonic().mappingFrames().size()) {
            throw new UnsupportedOperationException("Map frame " + frame + " outside admitted Sonic mappings ("
                    + assets.sonic().mappingFrames().size() + "); no neighbouring mapping family is admitted");
        }
    }

    private static int facingAngle(Input input) {
        return (input.flags() & 1) == 0 ? u8(~input.angle()) : input.angle();
    }

    private static int horizontalFlags(Input input) {
        return (input.spriteFlags() & 0xFC) | (input.flags() & 1);
    }

    private static int wordMagnitude(int signedWord) {
        return (signedWord < 0 ? -signedWord : signedWord) & 0xFFFF;
    }

    private static int duration(int magnitudeWord, int bias) {
        int remaining = (short) (-magnitudeWord + bias); // NEG.W then ADDI.W, test N before LSR.W.
        return (remaining < 0 ? 0 : remaining) >>> 8;
    }

    private static int u8(int value) { return value & 0xFF; }

    private static void requireByte(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned byte"); }
    }
}
