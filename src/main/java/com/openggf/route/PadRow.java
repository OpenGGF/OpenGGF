package com.openggf.route;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * One controller poll: the held level of every physical button on both pads. Neutral rows,
 * opposite directions, Start and each of A/B/C are preserved exactly; nothing is normalised.
 * A row carries no index; its position belongs to the tape.
 *
 * <p>This is the only conversion between route rows and the engine's recorded input. The engine
 * form sets {@code INPUT_JUMP} whenever any action button is held, exactly as
 * {@code Bk2MovieLoader} does, so a row driven directly and the same row loaded from an exported
 * movie produce identical gameplay input.
 */
public record PadRow(int p1, int p2) {
    public static final PadRow NEUTRAL = new PadRow(0, 0);

    public PadRow {
        if ((p1 & ~0xFF) != 0 || (p2 & ~0xFF) != 0) {
            throw new IllegalArgumentException("pad masks are 8-bit physical levels: " + p1 + "/" + p2);
        }
    }

    public static PadRow of(PlayerSlot slot, PhysicalButton... held) {
        int mask = 0;
        for (PhysicalButton button : held) mask |= button.bit();
        return slot == PlayerSlot.P1 ? new PadRow(mask, 0) : new PadRow(0, mask);
    }

    public int lane(PlayerSlot slot) {
        return slot == PlayerSlot.P1 ? p1 : p2;
    }

    public boolean held(PlayerSlot slot, PhysicalButton button) {
        return (lane(slot) & button.bit()) != 0;
    }

    public PadRow with(PlayerSlot slot, int mask) {
        return slot == PlayerSlot.P1 ? new PadRow(mask, p2) : new PadRow(p1, mask);
    }

    public boolean isNeutral() {
        return p1 == 0 && p2 == 0;
    }

    /** Packed 16-bit form: P1 in the low byte, P2 in the high byte. */
    public int packed() {
        return p1 | (p2 << 8);
    }

    public static PadRow unpack(int packed) {
        return new PadRow(packed & 0xFF, (packed >>> 8) & 0xFF);
    }

    /** The engine's recorded-input form of this row at movie index {@code index}. */
    public Bk2FrameInput toEngine(int index) {
        return new Bk2FrameInput(index, inputMask(p1), actionMask(p1), (p1 & PhysicalButton.START.bit()) != 0,
                inputMask(p2), actionMask(p2), (p2 & PhysicalButton.START.bit()) != 0, "|" + lane(p1) + "|" + lane(p2) + "|");
    }

    /**
     * The physical row a recorded engine input represents. Debug modifiers are not controller
     * signals and are rejected rather than dropped. A jump bit with no action button reads as A,
     * as {@code UserRecordingWriter} writes it.
     */
    public static PadRow fromEngine(Bk2FrameInput input) {
        if (input.debugModeTogglePressed() || input.debugShiftDown() || input.debugControlDown()
                || input.debugAltDown() || input.debugSuperDown()) {
            throw new IllegalArgumentException("debug modifiers are not route controller rows: " + input.rawLine());
        }
        return new PadRow(physical(input.p1InputMask(), input.p1ActionMask(), input.p1StartPressed()),
                physical(input.p2InputMask(), input.p2ActionMask(), input.p2StartPressed()));
    }

    /** Explicit physical letters for one lane in script order ({@code U+D+L+R+A+B+C+S}), or {@code -}. */
    public static String letters(int lane) {
        var out = new StringBuilder();
        for (PhysicalButton button : new PhysicalButton[]{PhysicalButton.UP, PhysicalButton.DOWN,
                PhysicalButton.LEFT, PhysicalButton.RIGHT, PhysicalButton.A, PhysicalButton.B,
                PhysicalButton.C, PhysicalButton.START}) {
            if ((lane & button.bit()) != 0) {
                if (!out.isEmpty()) out.append('+');
                out.append(button.letter());
            }
        }
        return out.isEmpty() ? "-" : out.toString();
    }

    @Override
    public String toString() {
        return p2 == 0 ? letters(p1) : letters(p1) + " / " + letters(p2);
    }

    private static String lane(int mask) {
        char[] field = "........".toCharArray();
        if ((mask & PhysicalButton.UP.bit()) != 0) field[0] = 'U';
        if ((mask & PhysicalButton.DOWN.bit()) != 0) field[1] = 'D';
        if ((mask & PhysicalButton.LEFT.bit()) != 0) field[2] = 'L';
        if ((mask & PhysicalButton.RIGHT.bit()) != 0) field[3] = 'R';
        if ((mask & PhysicalButton.START.bit()) != 0) field[4] = 'S';
        if ((mask & PhysicalButton.A.bit()) != 0) field[5] = 'A';
        if ((mask & PhysicalButton.B.bit()) != 0) field[6] = 'B';
        if ((mask & PhysicalButton.C.bit()) != 0) field[7] = 'C';
        return new String(field);
    }

    private static int inputMask(int lane) {
        int mask = 0;
        if ((lane & PhysicalButton.UP.bit()) != 0) mask |= AbstractPlayableSprite.INPUT_UP;
        if ((lane & PhysicalButton.DOWN.bit()) != 0) mask |= AbstractPlayableSprite.INPUT_DOWN;
        if ((lane & PhysicalButton.LEFT.bit()) != 0) mask |= AbstractPlayableSprite.INPUT_LEFT;
        if ((lane & PhysicalButton.RIGHT.bit()) != 0) mask |= AbstractPlayableSprite.INPUT_RIGHT;
        if (actionMask(lane) != 0) mask |= AbstractPlayableSprite.INPUT_JUMP;
        return mask;
    }

    private static int actionMask(int lane) {
        int mask = 0;
        if ((lane & PhysicalButton.A.bit()) != 0) mask |= InputActionMasks.ACTION_A;
        if ((lane & PhysicalButton.B.bit()) != 0) mask |= InputActionMasks.ACTION_B;
        if ((lane & PhysicalButton.C.bit()) != 0) mask |= InputActionMasks.ACTION_C;
        return mask;
    }

    private static int physical(int input, int action, boolean start) {
        int lane = 0;
        if ((input & AbstractPlayableSprite.INPUT_UP) != 0) lane |= PhysicalButton.UP.bit();
        if ((input & AbstractPlayableSprite.INPUT_DOWN) != 0) lane |= PhysicalButton.DOWN.bit();
        if ((input & AbstractPlayableSprite.INPUT_LEFT) != 0) lane |= PhysicalButton.LEFT.bit();
        if ((input & AbstractPlayableSprite.INPUT_RIGHT) != 0) lane |= PhysicalButton.RIGHT.bit();
        if (start) lane |= PhysicalButton.START.bit();
        boolean explicit = (action & InputActionMasks.ACTION_ALL) != 0;
        if ((action & InputActionMasks.ACTION_A) != 0
                || (!explicit && (input & AbstractPlayableSprite.INPUT_JUMP) != 0)) lane |= PhysicalButton.A.bit();
        if ((action & InputActionMasks.ACTION_B) != 0) lane |= PhysicalButton.B.bit();
        if ((action & InputActionMasks.ACTION_C) != 0) lane |= PhysicalButton.C.bit();
        return lane;
    }
}
