package com.openggf.game.internal;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;

/** Optional ROM program for a title's idle-to-demo transition. */
public interface TitleAttractDemo {
    interface TitleClock {
        /** Number of native title-loop waits serviced since this title was initialized. */
        int attractTitleTicks();

        /** Enters the native title program directly, skipping the preceding boot screen. */
        void initializeDirectTitle();
    }

    sealed interface Destination {
        record Level(int zone, int act, GameplayLaunchTeam team) implements Destination { }

        /** The entry's provider supplies ROM controls and the native wait program in Launch. */
        record SpecialStage(int stageIndex, EmeraldRewardKind rewardKind) implements Destination { }

        @FunctionalInterface
        non-sealed interface NativeScreen extends Destination {
            NativeMenuScreen open(NativeMenuHostContext host, AttractDemoPlayback playback);
        }
    }

    record Launch(Destination destination, AttractDemoPlayback playback, boolean retainProgress) { }

    void enterTitle();

    /** Called after each represented title-loop wait. Returns null until a launch is due. */
    Launch onTitleTick();
}
