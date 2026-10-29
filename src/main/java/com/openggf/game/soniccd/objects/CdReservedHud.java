package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Source-ordered native ID $1C HUD and ID $19 subtype $0A time icon.
 *
 * <p>The selected reserved image is immutable, exactly 64 bytes. Each native
 * store is published through a fresh selected-image value CAS. The ports must
 * bind the real active invocation and current reserved owner; equal public
 * values do not authenticate origin, revisions, exact-once execution, or ABA.
 * This component cannot certify other reserved images, the dynamic pool,
 * Frame, globals, or the queue's private state. Those remain the caller's
 * publication and lifetime obligations.
 *
 * <p>N 1ecc5f3a: HUD and Points/Main.asm, ObjHUDPoints/ObjHUD_Init/ObjHUD_Main
 * (67-140); Monitor and Time Post/Main.asm, ObjTimeIcon (7-54),
 * ObjTimePostTimeIcon (64-71), ObjMonitorTimepost (269-280). No digit uploads,
 * points body, monitor/time-post body, animation, sound, or despawn is owned.
 */
public final class CdReservedHud {
    public record Assets(int hudMappingAddressBits, int monitorMappingAddressBits) {
        /** Existing authenticated views supply raw native LONG addresses only. */
        public static Assets fromAssets(CdR11bAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(
                    assets.view(CdR11bAssets.Asset.MAP_SPR_HUD).range().nativeAddress(),
                    assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME).range().nativeAddress());
        }
    }

    public record Globals(int debugCheatWord, int ringsWord, int vIntLowByte,
                          int timeAttackModeByte, int timeWarpDirectionByte,
                          int timeWarpTimerWord, int levelFramesWord) {
        public Globals {
            word(debugCheatWord, "debugCheatWord");
            word(ringsWord, "ringsWord");
            octet(vIntLowByte, "vIntLowByte");
            octet(timeAttackModeByte, "timeAttackModeByte");
            octet(timeWarpDirectionByte, "timeWarpDirectionByte");
            word(timeWarpTimerWord, "timeWarpTimerWord");
            word(levelFramesWord, "levelFramesWord");
        }
    }

    public interface Slots {
        CdAssetBytes read(int wholeIndex);

        /** Publish exactly the ready selected image, with current full64 value CAS. */
        void write(int wholeIndex, CdAssetBytes expected, CdAssetBytes next);
    }

    @FunctionalInterface
    public interface DrawObjectCall {
        /** The existing genuine queue call, including its native bit7 clear. */
        void drawObject(int wholeIndex);
    }

    public enum NativeRoutine { HUD_INIT, HUD_MAIN, TIME_ICON_INIT, TIME_ICON_MAIN }
    public enum NativeCall { DRAW_OBJECT, DELETE_OBJECT }
    public sealed interface Effect permits RoutineEntered, CallReturned, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record Deleted() implements Effect { }

    /** Completed observations only; the list never supplies replay authority. */
    public record Result(CdAssetBytes image, List<Effect> effects) {
        public Result {
            image = image64(image);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return image.unsignedByte(0) == 0; }
    }

    public enum UnsupportedBoundary {
        OBJECT_ID, POINTS_BRANCH, HUD_ROUTINE, MONITOR_BRANCH, MONITOR_DESPAWN,
        TIME_POST_BRANCH, TIME_ICON_ROUTINE
    }

    public static final class UnsupportedReservedHud extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final int value;

        private UnsupportedReservedHud(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super("Unowned reserved HUD boundary " + boundary + " at whole slot "
                    + wholeIndex + ": " + value);
            this.boundary = Objects.requireNonNull(boundary, "boundary");
            this.wholeIndex = wholeIndex;
            this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final Assets assets;
    private final Slots slots;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdReservedHud(Assets assets, Slots slots, DrawObjectCall drawObject) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** Globals are dereferenced only where the reached native path reads them. */
    public Result run(int wholeIndex, Globals globals) {
        if (wholeIndex < 1 || wholeIndex > 31) {
            throw new IllegalArgumentException("Reserved whole index must be 1..31");
        }
        if (running) {
            throw new IllegalStateException("Reserved HUD body reentry");
        }
        running = true;
        try {
            var c = new Cursor(wholeIndex, image64(slots.read(wholeIndex)));
            switch (c.u8(0)) {
                case 0x1C -> hud(c, globals);
                case 0x19 -> monitorWrapper(c, globals);
                default -> throw c.unsupported(UnsupportedBoundary.OBJECT_ID, c.u8(0));
            }
            c.requireCurrent();
            return new Result(c.image, c.effects);
        } finally {
            running = false;
        }
    }

    private void hud(Cursor c, Globals globals) {
        // ObjHUDPoints: signed subtype branch precedes the routine table.
        if ((c.u8(0x28) & 0x80) != 0) {
            throw c.unsupported(UnsupportedBoundary.POINTS_BRANCH, c.u8(0x28));
        }
        int routine = c.u8(0x24);
        if (routine == 0) {
            c.effects.add(new RoutineEntered(NativeRoutine.HUD_INIT));
            c.storeByte(0x24, 2);
            c.storeLong(4, assets.hudMappingAddressBits());
            c.storeWord(2, 0x8568);
            c.storeWord(8, 0x90);
            c.storeWord(0x0A, 0x88); // oYScr, not oY at $0C.
            // Init's subtype2 test precedes debug and the lives subtype.
            if (c.u8(0x29) != 0) {
                c.storeByte(0x1A, 3);
            } else {
                if (globals.debugCheatWord() != 0) {
                    c.storeByte(0x1A, 2);
                }
                if (c.u8(0x28) != 0) {
                    c.storeWord(0x0A, 0x148);
                    c.storeByte(0x1A, 1);
                }
            }
        } else if (routine != 2) {
            throw c.unsupported(UnsupportedBoundary.HUD_ROUTINE, routine);
        }
        c.effects.add(new RoutineEntered(NativeRoutine.HUD_MAIN));
        // Main's lives test precedes subtype2, unlike Init's ordering.
        if (c.u8(0x28) == 0) {
            if (c.u8(0x29) != 0) {
                if (globals.ringsWord() != 0) {
                    c.storeByte(2, c.u8(2) & ~0x20);
                } else if ((globals.vIntLowByte() & 0x0F) == 0) {
                    c.storeByte(2, c.u8(2) ^ 0x20);
                }
            } else {
                c.storeByte(0x1A, 0);
                if (globals.debugCheatWord() != 0) {
                    c.storeByte(0x1A, 2);
                }
            }
        }
        c.draw();
    }

    private void monitorWrapper(Cursor c, Globals globals) {
        int subtype = c.u8(0x28);
        // The 1UP/time-attack wrapper reaches CheckObjDespawn, not DeleteObject.
        if (subtype == 0 && globals.timeAttackModeByte() != 0) {
            throw c.unsupported(UnsupportedBoundary.MONITOR_DESPAWN, subtype);
        }
        if (subtype < 8) {
            throw c.unsupported(UnsupportedBoundary.MONITOR_BRANCH, subtype);
        }
        // ObjTimePostTimeIcon deletes BEFORE the subtype $0A/routine selection.
        if (globals.timeAttackModeByte() != 0) {
            c.publish(CdAssetBytes.copyOf(new byte[64]));
            c.effects.add(new CallReturned(NativeCall.DELETE_OBJECT));
            c.effects.add(new Deleted());
            return;
        }
        if (subtype != 0x0A) {
            throw c.unsupported(UnsupportedBoundary.TIME_POST_BRANCH, subtype);
        }
        timeIcon(c, globals);
    }

    private void timeIcon(Cursor c, Globals globals) {
        int routine = c.u8(0x24);
        if (routine == 0) {
            c.effects.add(new RoutineEntered(NativeRoutine.TIME_ICON_INIT));
            c.storeByte(0x24, 2);
            c.storeLong(4, assets.monitorMappingAddressBits());
            c.storeWord(2, 0x85A8);
            c.storeWord(8, 0xC4);
            c.storeWord(0x0A, 0x152);
        } else if (routine != 2) {
            throw c.unsupported(UnsupportedBoundary.TIME_ICON_ROUTINE, routine);
        }
        c.effects.add(new RoutineEntered(NativeRoutine.TIME_ICON_MAIN));
        c.storeByte(0x1A, 0x12);
        if ((globals.timeWarpDirectionByte() & 0x80) == 0) {
            c.storeByte(0x1A, 0x13);
        }
        // Direction zero still ran Init/Main above, choosing frame $13.
        if (globals.timeWarpDirectionByte() == 0) {
            return;
        }
        if (globals.timeWarpTimerWord() < 90 || (globals.levelFramesWord() & 1) == 0) {
            c.draw();
        }
    }

    private final class Cursor {
        private final int wholeIndex;
        private final List<Effect> effects = new ArrayList<>();
        private CdAssetBytes image;

        private Cursor(int wholeIndex, CdAssetBytes image) {
            this.wholeIndex = wholeIndex;
            this.image = image;
        }
        private int u8(int offset) { return image.unsignedByte(offset); }
        private UnsupportedReservedHud unsupported(UnsupportedBoundary boundary, int value) {
            return new UnsupportedReservedHud(boundary, wholeIndex, value);
        }
        private void requireCurrent() {
            requireEqual(image, image64(slots.read(wholeIndex)), "selected image changed");
        }
        private void storeByte(int offset, int value) {
            var next = image.copy();
            next[offset] = (byte) value;
            publish(CdAssetBytes.copyOf(next));
        }
        private void storeWord(int offset, int value) {
            var next = image.copy();
            next[offset] = (byte) (value >>> 8);
            next[offset + 1] = (byte) value;
            publish(CdAssetBytes.copyOf(next));
        }
        private void storeLong(int offset, int value) {
            var next = image.copy();
            next[offset] = (byte) (value >>> 24);
            next[offset + 1] = (byte) (value >>> 16);
            next[offset + 2] = (byte) (value >>> 8);
            next[offset + 3] = (byte) value;
            publish(CdAssetBytes.copyOf(next));
        }
        private void publish(CdAssetBytes next) {
            requireCurrent();
            var expected = image;
            try {
                slots.write(wholeIndex, expected, next);
            } catch (RuntimeException | Error original) {
                verifyFailure(original, expected, next);
                throw original;
            }
            var actual = image64(slots.read(wholeIndex));
            requireEqual(next, actual, "selected write was not published exactly");
            image = actual;
        }
        private void draw() {
            requireCurrent();
            var expected = image;
            var raw = expected.copy();
            raw[1] &= 0x7F;
            var cleared = CdAssetBytes.copyOf(raw);
            try {
                drawObject.drawObject(wholeIndex);
            } catch (RuntimeException | Error original) {
                verifyFailure(original, expected, cleared);
                throw original;
            }
            var actual = image64(slots.read(wholeIndex));
            requireEqual(cleared, actual, "DrawObject must clear only sprite bit7");
            image = actual;
            effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        }
        /** Failed callbacks may have retained either their pre-call or ready prefix. */
        private void verifyFailure(Throwable original, CdAssetBytes before, CdAssetBytes ready) {
            try {
                var actual = image64(slots.read(wholeIndex));
                if (!equal(before, actual) && !equal(ready, actual)) {
                    throw new IllegalStateException("Callback changed a selected nonwrite");
                }
            } catch (RuntimeException | Error verifier) {
                if (verifier != original) {
                    original.addSuppressed(verifier);
                }
            }
        }
    }

    private static CdAssetBytes image64(CdAssetBytes image) {
        Objects.requireNonNull(image, "reserved image");
        if (image.length() != 64) {
            throw new IllegalArgumentException("Reserved image must contain exactly 64 bytes");
        }
        return image;
    }
    private static boolean equal(CdAssetBytes expected, CdAssetBytes actual) {
        return Arrays.equals(expected.copy(), actual.copy());
    }
    private static void requireEqual(CdAssetBytes expected, CdAssetBytes actual, String reason) {
        if (!equal(expected, actual)) {
            throw new IllegalStateException(reason);
        }
    }
    private static void octet(int value, String name) {
        if ((value & ~0xFF) != 0) {
            throw new IllegalArgumentException(name + " must be an unsigned BYTE");
        }
    }
    private static void word(int value, String name) {
        if ((value & ~0xFFFF) != 0) {
            throw new IllegalArgumentException(name + " must be an unsigned WORD");
        }
    }
}
