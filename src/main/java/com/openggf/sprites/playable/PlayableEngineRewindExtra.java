package com.openggf.sprites.playable;

import com.openggf.level.objects.PerObjectRewindSnapshot.ObjectSubclassRewindExtra;

/** Engine payload only; the exported player extra and creator subclass payload remain independent. */
record PlayableEngineRewindExtra(PendingInstaShieldRewindExtra pendingInstaShield,
                                NativePlayerSpeedWords.Snapshot nativeSpeedWords)
        implements ObjectSubclassRewindExtra {
    static PlayableEngineRewindExtra capture(AbstractPlayableSprite player,
                                             PendingInstaShieldRewindExtra pending) {
        var words = player.nativeSpeedPolicy() != null ? player.nativeSpeedWords.capture() : null;
        return pending == null && words == null ? null : new PlayableEngineRewindExtra(pending, words);
    }

    static ObjectSubclassRewindExtra pending(ObjectSubclassRewindExtra extra) {
        return extra instanceof PlayableEngineRewindExtra engine ? engine.pendingInstaShield() : extra;
    }

    static void restoreSpeedWords(AbstractPlayableSprite player, ObjectSubclassRewindExtra extra) {
        player.nativeSpeedWords.restore(player.nativeSpeedPolicy() != null
                && extra instanceof PlayableEngineRewindExtra engine ? engine.nativeSpeedWords() : null);
    }
}
