package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.ending.S3kEndingState;
import java.nio.ByteBuffer;
import java.util.Objects;

/** The internal $D01 ending level; it is neither AIZ1 nor a public campaign act. */
public final class EndingZoneRuntimeState implements S3kZoneRuntimeState {
    private final PlayerCharacter character;
    private final S3kEndingState ending = new S3kEndingState();
    private final ScreenRam screen = new ScreenRam();

    /** Native ending RAM survives the two clearRAM dynamic-sprite boundaries. */
    public static final class ScreenRam {
        public int foregroundRoutine, backgroundRoutine, scrollXFixed, scrollYFixed;
        public int backgroundAnchorX, backgroundYFixed = 0x2B40000, backgroundAcceleration;
        public int foregroundX = 0x200, foregroundY = 0x200;
        public int fadeTimer, redrawRows, aizBackgroundScrollFixed;
        public int backgroundRedrawRows, backgroundScrollFrame=Integer.MIN_VALUE, cloudDriftFixed, backgroundCameraY;
        public int aizWaveOriginFrame=Integer.MIN_VALUE;
        public final short[] hScrollWords=new short[140];
        public boolean foregroundSignal, backgroundSignal, catcherReady, initialized;
        private static final int BYTES = 17 * Integer.BYTES + 4 + 140 * Short.BYTES;
        private void captureTo(ByteBuffer b) {
            b.putInt(foregroundRoutine).putInt(backgroundRoutine).putInt(scrollXFixed).putInt(scrollYFixed)
                    .putInt(backgroundAnchorX).putInt(backgroundYFixed).putInt(backgroundAcceleration)
                    .putInt(foregroundX).putInt(foregroundY).putInt(fadeTimer).putInt(redrawRows)
                    .putInt(aizBackgroundScrollFixed).put((byte)(foregroundSignal ? 1 : 0))
                    .put((byte)(backgroundSignal ? 1 : 0)).put((byte)(catcherReady ? 1 : 0))
                    .put((byte)(initialized ? 1 : 0));
            b.putInt(backgroundRedrawRows).putInt(backgroundScrollFrame).putInt(cloudDriftFixed).putInt(backgroundCameraY)
                    .putInt(aizWaveOriginFrame);
            for(short word:hScrollWords)b.putShort(word);
        }
        private void restoreFrom(ByteBuffer b) {
            foregroundRoutine=b.getInt();backgroundRoutine=b.getInt();scrollXFixed=b.getInt();scrollYFixed=b.getInt();
            backgroundAnchorX=b.getInt();backgroundYFixed=b.getInt();backgroundAcceleration=b.getInt();
            foregroundX=b.getInt();foregroundY=b.getInt();fadeTimer=b.getInt();redrawRows=b.getInt();
            aizBackgroundScrollFixed=b.getInt();foregroundSignal=b.get()!=0;backgroundSignal=b.get()!=0;
            catcherReady=b.get()!=0;initialized=b.get()!=0;
            backgroundRedrawRows=b.getInt();backgroundScrollFrame=b.getInt();cloudDriftFixed=b.getInt();backgroundCameraY=b.getInt();
            aizWaveOriginFrame=b.getInt();
            for(int i=0;i<hScrollWords.length;i++)hScrollWords[i]=b.getShort();
        }
    }

    public EndingZoneRuntimeState(PlayerCharacter character) { this.character = Objects.requireNonNull(character); }
    public S3kEndingState ending() { return ending; }
    public ScreenRam screen() { return screen; }
    @Override public int zoneIndex() { return 0x0D; }
    @Override public int actIndex() { return 1; }
    @Override public PlayerCharacter playerCharacter() { return character; }
    @Override public int getDynamicResizeRoutine() { return screen.foregroundRoutine; }
    @Override public boolean isActTransitionFlagActive() { return false; }
    @Override public byte[] captureBytes() {
        var buffer = ByteBuffer.allocate(ScreenRam.BYTES + ending.snapshotBytes());
        screen.captureTo(buffer); ending.captureTo(buffer); return buffer.array();
    }
    @Override public void restoreBytes(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes); screen.restoreFrom(buffer); ending.restoreFrom(buffer);
    }
}
