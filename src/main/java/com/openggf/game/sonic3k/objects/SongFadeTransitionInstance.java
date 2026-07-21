package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * Lightweight persistent object that fades out the current music, executes a
 * native signed wait word, then plays a new track and destroys itself.
 *
 * ROM equivalent: Obj_Song_Fade_Transition / Obj_Song_Fade_ToLevelMusic
 * (sonic3k.asm line 180305). The ROM spawns this as an independent object so
 * that the music transition survives the destruction of the cutscene object
 * that initiated it.
 */
public class SongFadeTransitionInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    /** Locked-on {@code Obj_Song_Fade_Transition} initial wait word. */
    public static final int TRANSITION_WAIT_WORD = 90;
    /** Locked-on {@code Obj_Song_Fade_ToLevelMusic} initial wait word. */
    public static final int TO_LEVEL_MUSIC_WAIT_WORD = 120;

    // Non-final: nativeWaitWord/musicId are not derivable from the dummy
    // ObjectSpawn (the ctor passes ObjectSpawn(0,0,0,0,0,false,0) to super).
    // Generic rewind recreate constructs with placeholder (0, 0), then the
    // GenericFieldCapturer reapplies these captured values after recreate.

    /**
     * Initial signed 16-bit wait word. Native code completes only when the
     * decremented word becomes negative, so a value of N completes on update N+1.
     * This is deliberately not named or treated as an ordinary frame duration.
     *
     * <p>Caller audit: the zone-event constants and literal values used by AIZ,
     * CNZ, MGZ, MHZ, LBZ, FBZ, and their cutscene/boss helpers are native wait
     * words. Common values 2, 30, 90 and 120 therefore execute for 3, 31, 91 and
     * 121 updates respectively; callers must not pre-adjust them.</p>
     */
    private int nativeWaitWord;

    /** Music ID to play when the delay expires. */
    private int musicId;

    /** Number of update executions since creation. */
    private int elapsedUpdates;

    /** Whether the initial fade-out has been issued. */
    private boolean fadeStarted;

    /** Whether the ROM delay countdown starts on the update after fade initialization. */
    private boolean deferCountdownOnFadeStart;

    /** Whether initialization must wait until the object pass after allocation. */
    private boolean deferSameFrameUpdateAfterSpawn;

    /**
     * @param nativeWaitWord native signed 16-bit wait word; N completes on update N+1
     * @param musicId        music ID to play when the wait word underflows
     */
    public SongFadeTransitionInstance(int nativeWaitWord, int musicId) {
        this(nativeWaitWord, musicId, false);
    }

    SongFadeTransitionInstance(int nativeWaitWord, int musicId, boolean deferCountdownOnFadeStart) {
        this(nativeWaitWord, musicId, deferCountdownOnFadeStart, false);
    }

    SongFadeTransitionInstance(int nativeWaitWord, int musicId,
                               boolean deferCountdownOnFadeStart,
                               boolean deferSameFrameUpdateAfterSpawn) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "SongFadeTransition");
        // The deferred constructors came from the frame-duration implementation:
        // callers pass the number of post-init countdown ticks. Convert that to
        // the signed native wait word used by the merged implementation.
        this.nativeWaitWord = (short) (deferCountdownOnFadeStart
                ? nativeWaitWord - 1 : nativeWaitWord);
        this.musicId = musicId;
        this.elapsedUpdates = 0;
        this.fadeStarted = false;
        this.deferCountdownOnFadeStart = deferCountdownOnFadeStart;
        this.deferSameFrameUpdateAfterSpawn = deferSameFrameUpdateAfterSpawn;
    }

    /** Creates the locked-on generic song transition helper (90 -> update 91). */
    public static SongFadeTransitionInstance transitionTo(int musicId) {
        return new SongFadeTransitionInstance(TRANSITION_WAIT_WORD, musicId);
    }

    /** Creates the locked-on level-music restore helper (120 -> update 121). */
    public static SongFadeTransitionInstance toLevelMusic(int musicId) {
        return new SongFadeTransitionInstance(TO_LEVEL_MUSIC_WAIT_WORD, musicId);
    }

    /** Re-evaluates Restore_LevelMusic state (including invincibility) at expiry. */
    public static SongFadeTransitionInstance toCurrentLevelMusic() {
        return new SongFadeTransitionInstance(TO_LEVEL_MUSIC_WAIT_WORD, -1);
    }

    SongFadeTransitionInstance(ObjectSpawn spawn) {
        this(0, 0);
    }

    int getMusicIdForTest() {
        return musicId;
    }

    int nativeWaitWordForTest() {
        return nativeWaitWord;
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    protected boolean skipsSameFrameUpdateAfterSpawn() {
        return deferSameFrameUpdateAfterSpawn;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!fadeStarted) {
            services().audioManager().fadeOutMusic(0x28, 6);
            fadeStarted = true;
            if (deferCountdownOnFadeStart) {
                return;
            }
        }
        // Native helpers decrement a signed wait word and complete only after it underflows:
        // a word of 90 completes on update 91; a word of 120 completes on update 121.
        if (elapsedUpdates++ >= nativeWaitWord) {
            int resolvedMusicId = musicId >= 0 ? musicId : services().getCurrentLevelMusicId();
            if (resolvedMusicId >= 0) services().playMusic(resolvedMusicId);
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible object — no rendering
    }
}
