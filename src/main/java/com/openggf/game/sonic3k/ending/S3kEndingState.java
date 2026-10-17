package com.openggf.game.sonic3k.ending;

import com.openggf.game.sonic3k.runtime.DezFinalArtState;
import java.nio.ByteBuffer;

/**
 * Shared ending RAM. The $D01 rescue screen and the $A01 Knuckles screen
 * dispatch the same credit/terminal routines without replacing their level.
 */
public final class S3kEndingState {
    private final S3kCreditsState credits = new S3kCreditsState();
    private final S3kEndingCloudState clouds = new S3kEndingCloudState();
    // Queue_Kos_Module is the same native queue used by the DEZ final scene.
    private final DezFinalArtState art = new DezFinalArtState();
    private final S3kEndingSceneState scene = new S3kEndingSceneState();
    private final EndingNativePlayerBinding nativePlayer = new EndingNativePlayerBinding();
    private int progress;
    private int flags; // _unkFAB8 ending bits, owned by rescue and terminal objects
    private boolean initialized;
    private boolean postCreditsStarted;
    private boolean startHeld;

    public S3kCreditsState credits() { return credits; }
    public S3kEndingCloudState clouds() { return clouds; }
    public DezFinalArtState art() { return art; }
    public S3kEndingSceneState scene() { return scene; }
    public EndingNativePlayerBinding nativePlayer() { return nativePlayer; }
    public int progress() { return progress; }
    /** sub_5FE82: equality with seven, not a fitted "good ending" gate. */
    public void initializeProgress(int chaos, int superEmeralds) {
        progress = superEmeralds == 7 ? 2 : chaos == 7 ? 0 : -2;
        initialized = true;
    }
    public boolean initialized() { return initialized; }
    public int flags() { return flags; }
    public boolean flag(int bit) { return (flags & (1 << bit)) != 0; }
    public void setFlag(int bit) { flags |= 1 << bit; }
    public void clearFlag(int bit) { flags &= ~(1 << bit); }
    public boolean postCreditsStarted() { return postCreditsStarted; }
    public void markPostCreditsStarted() { postCreditsStarted = true; }
    public boolean startHeld() { return startHeld; }
    // Title_Screen's Pal_FadeToBlack passes still to run over the retained terminal picture.
    private int titleEntryPasses;
    /** loc_5F314: Game_mode 0, then Sega_Screen's 4; Title_Screen opens with 22 fade passes. */
    public void beginTitleEntryFade() { titleEntryPasses = 0x15 + 1; }
    public boolean titleEntryFadePending() { return titleEntryPasses > 0; }
    /** @return the passes left after this one */
    public int consumeTitleEntryPass() { return --titleEntryPasses; }
    public void setStartHeld(boolean held) { startHeld = held; }
    public int snapshotBytes() { return 3 * Integer.BYTES + 3 + credits.snapshotBytes() + art.snapshotBytes() + scene.snapshotBytes() + S3kEndingCloudState.BYTES; }
    public void captureTo(ByteBuffer buffer) {
        buffer.putInt(progress).putInt(flags).put((byte) (initialized ? 1 : 0))
                .put((byte) (postCreditsStarted ? 1 : 0)).put((byte) (startHeld ? 1 : 0)).putInt(titleEntryPasses);
        credits.captureTo(buffer); art.captureTo(buffer); scene.captureTo(buffer); clouds.captureTo(buffer);
    }
    public void restoreFrom(ByteBuffer buffer) {
        progress = buffer.getInt(); flags = buffer.getInt(); initialized = buffer.get() != 0;
        postCreditsStarted = buffer.get() != 0; startHeld = buffer.get() != 0; titleEntryPasses = buffer.getInt();
        credits.restoreFrom(buffer); art.restoreFrom(buffer); scene.restoreFrom(buffer); clouds.restoreFrom(buffer);
    }
}
