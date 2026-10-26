package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.CharacterKey;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** World-owned service/rewind publication; never grants an ordinary CPU follower a human slot. */
public final class Sonic2CompetitionPolicy implements WorldSessionPolicyState {
    private final Sonic2CompetitionSession session;
    public Sonic2CompetitionPolicy(Sonic2CompetitionSession session) { this.session = java.util.Objects.requireNonNull(session); }
    @Override public <T> T getService(Class<T> type) {
        return session.phase() != Sonic2CompetitionSession.Phase.SELECT
                && session.phase() != Sonic2CompetitionSession.Phase.FINISHED && type.isInstance(session)
                ? type.cast(session) : null;
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
    @Override public void beforeAssembly(LevelLoadCause cause) { }
    @Override public void bindRoster(SpriteManager sprites) {
        if (session.phase() != Sonic2CompetitionSession.Phase.ACT) return;
        AbstractPlayableSprite sonic = null, tails = null;
        for (var sprite : sprites.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                if (CharacterKey.SONIC.equals(playable.characterKey())) sonic = playable;
                if (CharacterKey.TAILS.equals(playable.characterKey())) tails = playable;
            }
        }
        session.bindPlayers(sonic, tails);
    }
    @Override public void afterNativeTick(LevelFrameContext context, LogicalInputSnapshot input) { session.advanceActFlow(); }
    @Override public void failedAssembly(LevelLoadCause cause) { session.reset(); }
    @Override public void closeScreens() { }
    @Override public void retire() { session.reset(); }
}
