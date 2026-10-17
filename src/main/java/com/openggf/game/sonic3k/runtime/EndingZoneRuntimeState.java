package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.ending.S3kEndingState;
import java.nio.ByteBuffer;
import java.util.Objects;

/** The internal $D01 ending level; it is neither AIZ1 nor a public campaign act. */
public final class EndingZoneRuntimeState implements S3kZoneRuntimeState, com.openggf.game.TitleEntryFadeSource {
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
        public int aizDeformationFrame=Integer.MIN_VALUE;
        public int initialPaletteDelay; // Pal_fade_delay2 FACE; Level clearRAMFA80..FAFF initializes zero.
        public final short[] hScrollWords=new short[140];
        public boolean foregroundSignal, backgroundSignal, catcherReady, initialized;
        public int catcherSlot=-1; // physical SST referenced by native _unkFAA4
        public int masterPaletteMode; // signed byte FAC1; survives native dynamic-sprite clear
        public final com.openggf.game.sonic3k.ending.S3kEndingAizPaletteState aizPalette =
                new com.openggf.game.sonic3k.ending.S3kEndingAizPaletteState();
        private static final int BYTES = 19 * Integer.BYTES + 5 + 140 * Short.BYTES
                + com.openggf.game.sonic3k.ending.S3kEndingAizPaletteState.BYTES;
        private void captureTo(ByteBuffer b) {
            b.putInt(foregroundRoutine).putInt(backgroundRoutine).putInt(scrollXFixed).putInt(scrollYFixed)
                    .putInt(backgroundAnchorX).putInt(backgroundYFixed).putInt(backgroundAcceleration)
                    .putInt(foregroundX).putInt(foregroundY).putInt(fadeTimer).putInt(redrawRows)
                    .putInt(aizBackgroundScrollFixed).put((byte)(foregroundSignal ? 1 : 0))
                    .put((byte)(backgroundSignal ? 1 : 0)).put((byte)(catcherReady ? 1 : 0))
                    .put((byte)(initialized ? 1 : 0));
            b.putInt(backgroundRedrawRows).putInt(backgroundScrollFrame).putInt(cloudDriftFixed).putInt(backgroundCameraY)
                    .putInt(aizDeformationFrame).putInt(initialPaletteDelay);
            for(short word:hScrollWords)b.putShort(word);
            b.putInt(catcherSlot);b.put((byte)masterPaletteMode);aizPalette.captureTo(b);
        }
        private void restoreFrom(ByteBuffer b) {
            foregroundRoutine=b.getInt();backgroundRoutine=b.getInt();scrollXFixed=b.getInt();scrollYFixed=b.getInt();
            backgroundAnchorX=b.getInt();backgroundYFixed=b.getInt();backgroundAcceleration=b.getInt();
            foregroundX=b.getInt();foregroundY=b.getInt();fadeTimer=b.getInt();redrawRows=b.getInt();
            aizBackgroundScrollFixed=b.getInt();foregroundSignal=b.get()!=0;backgroundSignal=b.get()!=0;
            catcherReady=b.get()!=0;initialized=b.get()!=0;
            backgroundRedrawRows=b.getInt();backgroundScrollFrame=b.getInt();cloudDriftFixed=b.getInt();backgroundCameraY=b.getInt();
            aizDeformationFrame=b.getInt();initialPaletteDelay=b.getInt();
            for(int i=0;i<hScrollWords.length;i++)hScrollWords[i]=b.getShort();
            catcherSlot=b.getInt();masterPaletteMode=b.get();aizPalette.restoreFrom(b);
        }
    }

    public EndingZoneRuntimeState(PlayerCharacter character) { this.character = Objects.requireNonNull(character); }
    public S3kEndingState ending() { return ending; }
    @Override public boolean titleEntryFadePending() { return ending.titleEntryFadePending(); }
    @Override public boolean stepTitleEntryFade(com.openggf.level.objects.ObjectServices services) {
        return com.openggf.game.sonic3k.ending.S3kEndingCoordinator.stepTitleEntryFade(ending, services);
    }
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
