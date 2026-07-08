package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2FrameInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Growing input source for live gameplay rewind.
 *
 * <p>Frame 0 is a synthetic neutral input row that matches the initial
 * keyframe captured by {@link RewindController}. Each normal level tick appends
 * one row after gameplay has advanced, then {@code recordExternalStep()} moves
 * the rewind cursor onto that row.
 */
public final class LiveRewindInputSource implements InputSource {

    private final List<Bk2FrameInput> frames = new ArrayList<>();
    private int baseFrame;

    public LiveRewindInputSource() {
        frames.add(neutralFrameInput(0));
    }

    public void appendFrame(InputHandler input, SonicConfigurationService config) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(config, "config");
        int frameIndex = baseFrame + frames.size();
        PlayerInputState p1 = input.logical().player1();
        PlayerInputState p2 = input.logical().player2();
        frames.add(new Bk2FrameInput(
                frameIndex,
                p1.heldMask(),
                p1.actionHeldMask(),
                p1.startHeld(),
                p2.heldMask(),
                p2.actionHeldMask(),
                p2.startHeld(),
                input.isKeyPressed(config.getInt(SonicConfiguration.DEBUG_MODE_KEY)),
                input.isShiftDown(),
                input.isControlDown(),
                "live:" + frameIndex));
    }

    public void discardAfter(int frame) {
        int keepCount = Math.max(1, Math.min(frames.size(), frame - baseFrame + 1));
        while (frames.size() > keepCount) {
            frames.remove(frames.size() - 1);
        }
    }

    public void discardBefore(int frame) {
        int removeCount = Math.min(Math.max(0, frame - baseFrame), frames.size() - 1);
        if (removeCount <= 0) {
            return;
        }
        frames.subList(0, removeCount).clear();
        baseFrame += removeCount;
    }

    public void resetToFrameZero() {
        frames.clear();
        baseFrame = 0;
        frames.add(neutralFrameInput(0));
    }

    public void retainOnlyFrame(int frame) {
        if (frame < earliestFrame() || frame >= frameCount()) {
            resetToSingleNeutralFrame(frame);
            return;
        }
        Bk2FrameInput retained = read(frame);
        frames.clear();
        baseFrame = frame;
        frames.add(retained);
    }

    public int earliestFrame() {
        return baseFrame;
    }

    @Override
    public int frameCount() {
        return baseFrame + frames.size();
    }

    @Override
    public Bk2FrameInput read(int frame) {
        int index = frame - baseFrame;
        if (index < 0 || index >= frames.size()) {
            throw new IndexOutOfBoundsException("Live rewind frame " + frame
                    + " outside " + baseFrame + ".." + (frameCount() - 1));
        }
        return frames.get(index);
    }

    private void resetToSingleNeutralFrame(int frame) {
        frames.clear();
        baseFrame = frame;
        frames.add(neutralFrameInput(frame));
    }

    private static Bk2FrameInput neutralFrameInput(int frame) {
        return new Bk2FrameInput(frame, 0, 0, false, 0, 0, false,
                false, false, false, "live:" + frame);
    }
}
