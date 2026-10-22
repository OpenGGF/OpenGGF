package com.openggf.game.sonic3k.competition.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;

/** Native goal-owned HUD records, including the neon animation that starts the timers. */
public final class CompetitionHudObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    public enum Kind { ITEM_ICON, LAP, NEON, LOSER_NEON, FALSE_START, TIMER, RESERVED }
    private Kind kind;
    private int playerSlot;
    private int mappingFrame;
    private int packedTime;
    private int animation;
    private int previousAnimation = -1;
    private int animationFrame;
    private int animationTimer;
    private boolean startPublished;
    private int finishedMask;
    private boolean finishPhaseStarted;
    private boolean drawPublished;

    /** Supported generic rewind probe; compact state restores the actual routine and slot. */
    public CompetitionHudObjectInstance(ObjectSpawn spawn) { this(Kind.RESERVED, spawn.subtype()); }

    public CompetitionHudObjectInstance(Kind kind, int slot) {
        super(new ObjectSpawn(0x120, 0xB8, 0x65, slot, 0, false, 0), "CompetitionHud" + kind);
        this.kind = java.util.Objects.requireNonNull(kind);
        this.playerSlot = slot;
        if (kind == Kind.FALSE_START) animation = 7;
        if (kind == Kind.LOSER_NEON) animation = 2;
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        var session = WorldSessionPolicyAccess.getService(services().worldSession(), S3kCompetitionSession.class);
        if (session != null) updateNative(session);
    }

    public void updateNative(S3kCompetitionSession session) {
        drawPublished = false;
        if (kind == Kind.RESERVED) return; // locret_3827A is a live, intentionally empty SST routine.
        if (playerSlot >= session.players().size()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        switch (kind) {
            case ITEM_ICON -> {
                var player = session.player(playerSlot);
                mappingFrame = session.rings(playerSlot) == 0 ? 0 : 3;
                if ((player.statusSecondary() & 4) != 0)
                    mappingFrame = player.speeds().maxSpeed() == 0xC00 ? 1 : 2;
            }
            case LAP -> mappingFrame = Math.min(5, session.lap(playerSlot)) % 10;
            case TIMER -> { session.tickGoalTimer(playerSlot); packedTime = session.packedTime(playerSlot); }
            case NEON -> tickNeon(session);
            case FALSE_START -> {
                if (!session.controlsLocked()) { ObjectLifetimeOps.expireDynamic(this); return; }
                animate(session.nativeObjectContext().animations("competition_neon"));
            }
            case LOSER_NEON -> {
                animate(session.nativeObjectContext().animations("competition_neon"));
                if (session.finishFromLoser(playerSlot)) animation = 5;
            }
            default -> { }
        }
        drawPublished = kind != Kind.NEON || !session.controlsLocked();
    }

    private void tickNeon(S3kCompetitionSession session) {
        SpriteAnimationSet scripts = session.nativeObjectContext().animations("competition_neon");
        if (scripts == null) throw new IllegalStateException("Native competition neon ROM animation is not installed");
        animate(scripts);
        // loc_3716C tests the post-Animate_Sprite byte frame/timer, not elapsed time.
        if (animation == 1 && animationTimer == 5) {
            if (animationFrame == 1 || animationFrame == 11 || animationFrame == 21)
                session.playSfx(Sonic3kSfx.LAUNCH_READY);
            else if (animationFrame == 31) session.playSfx(Sonic3kSfx.LAUNCH_GO);
        }
        if (session.controlsLocked()) {
            // move.w #1,anim(a0): anim=0,prev_anim=1. FixBugs=0 restarts on every locked pass.
            animation = 0; previousAnimation = 1; mappingFrame = 0xA;
            startPublished = false;
            return;
        }
        if (!startPublished && mappingFrame == 3) {
            startPublished = true;
            session.signalGo();
        }
        if (!finishPhaseStarted) session.finishFromNeon();
        else session.advanceResultWait();
        int nowFinished = 0;
        boolean anyStopped = false;
        for (var player : session.players()) {
            anyStopped |= session.finished(player.slot());
            if (session.finished(player.slot()) && session.lap(player.slot()) > S3kCompetitionSession.TOTAL_LAPS)
                nowFinished |= 1 << player.slot();
        }
        if (!finishPhaseStarted && anyStopped) {
            finishPhaseStarted = true;
            finishedMask = nowFinished;
            if (nowFinished != 0) animation = nowFinished == 3 ? 6 : session.players().size() == 1 ? 3 : 4;
            if (session.players().size() == 2 && nowFinished != 0 && nowFinished != 3) {
                int loser = nowFinished == 1 ? 1 : 0;
                session.allocateAfter(this, () -> new CompetitionHudObjectInstance(Kind.LOSER_NEON, loser));
            }
        }
    }

    private void animate(SpriteAnimationSet scripts) {
        if (animation != previousAnimation) {
            previousAnimation = animation; animationFrame = 0; animationTimer = 0;
        }
        animationTimer = (animationTimer - 1) & 0xFF;
        if ((byte) animationTimer >= 0) return;
        var script = scripts.getScript(animation);
        if (script == null || script.frames().isEmpty()) throw new IllegalStateException("Incomplete native neon animation " + animation);
        animationTimer = script.delay();
        if (animationFrame >= script.frames().size()) {
            switch (script.endAction()) {
                case SWITCH -> { animation = script.endParam(); return; }
                case LOOP_BACK -> animationFrame -= script.endParam();
                case HOLD -> animationFrame = script.frames().size() - 1;
                default -> animationFrame = 0;
            }
        }
        mappingFrame = script.frames().get(animationFrame++);
    }

    public Kind kind() { return kind; }
    public int playerSlot() { return playerSlot; }
    public int mappingFrame() { return mappingFrame; }
    public int packedTime() { return packedTime; }
    /** -1 is native shared display; 0/1 address the corresponding view. */
    public int viewSlot() { return kind == Kind.NEON ? finishedMask == 3 ? -1 : finishedMask == 2 ? 1 : finishedMask == 1 ? 0 : -1 : playerSlot; }
    public boolean nativeDrawPublished() { return drawPublished; }
    public String artKey() {
        return switch (kind) {
            case ITEM_ICON -> "competition_item_icon";
            case LAP -> "competition_lap";
            case NEON, LOSER_NEON, FALSE_START -> "competition_neon";
            case TIMER -> "competition_timer";
            case RESERVED -> null;
        };
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; } // Goal children explicitly write priority word zero.
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (artKey() == null || !drawPublished) return;
        var renderer = getRenderer(artKey());
        if (renderer == null || !renderer.isReady()) return;
        if (kind == Kind.TIMER) {
            for (int piece : new int[]{0, 2, 5}) renderer.drawFramePieceByIndex(0, piece, 0xC8 - 0x80, 0x90 - 0x80, false, false);
            var digits = getRenderer("competition_timer_digit");
            if (digits == null || !digits.isReady()) return;
            int min = packedTime >>> 16 & 0xFF, sec = packedTime >>> 8 & 0xFF, cs = packedTime & 0xFF;
            int[] values = {min, sec / 10, sec % 10, cs / 10, cs % 10};
            int[] x = {-0x18, -4, 8, 0x1C, 0x28};
            for (int i = 0; i < values.length; i++) digits.drawFrameIndex(values[i], 0xC8 - 0x80 + x[i], 0x90 - 0x80, false, false);
        } else {
            int x = kind == Kind.LAP ? 0x190 : 0x120;
            int y = kind == Kind.ITEM_ICON ? 0xE0 : kind == Kind.LAP ? 0x98 : 0xB8;
            renderer.drawFrameIndex(mappingFrame, x - 0x80, y - 0x80, false, false);
        }
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CompetitionHudObjectInstance(kind, playerSlot);
    }
}
