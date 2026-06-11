package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
        frames.add(new Bk2FrameInput(0, 0, 0, false, 0, 0, false,
                false, false, false, "live:0"));
    }

    public void appendFrame(InputHandler input, SonicConfigurationService config) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(config, "config");
        int frameIndex = baseFrame + frames.size();
        frames.add(new Bk2FrameInput(
                frameIndex,
                heldMask(input, config,
                        SonicConfiguration.UP,
                        SonicConfiguration.DOWN,
                        SonicConfiguration.LEFT,
                        SonicConfiguration.RIGHT,
                        SonicConfiguration.JUMP),
                input.isKeyPressed(config.getInt(SonicConfiguration.JUMP)) ? 1 : 0,
                input.isKeyPressed(config.getInt(SonicConfiguration.PAUSE_KEY)),
                heldMask(input, config,
                        SonicConfiguration.P2_UP,
                        SonicConfiguration.P2_DOWN,
                        SonicConfiguration.P2_LEFT,
                        SonicConfiguration.P2_RIGHT,
                        SonicConfiguration.P2_JUMP),
                input.isKeyPressed(config.getInt(SonicConfiguration.P2_JUMP)) ? 1 : 0,
                input.isKeyPressed(config.getInt(SonicConfiguration.P2_START)),
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

    private static int heldMask(InputHandler input, SonicConfigurationService config,
                                SonicConfiguration up,
                                SonicConfiguration down,
                                SonicConfiguration left,
                                SonicConfiguration right,
                                SonicConfiguration jump) {
        int mask = 0;
        if (input.isKeyDown(config.getInt(up))) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (input.isKeyDown(config.getInt(down))) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        if (input.isKeyDown(config.getInt(left))) {
            mask |= AbstractPlayableSprite.INPUT_LEFT;
        }
        if (input.isKeyDown(config.getInt(right))) {
            mask |= AbstractPlayableSprite.INPUT_RIGHT;
        }
        if (input.isKeyDown(config.getInt(jump))) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return mask;
    }
}
