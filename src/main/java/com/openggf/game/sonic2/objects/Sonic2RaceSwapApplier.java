package com.openggf.game.sonic2.objects;

import com.openggf.game.arena.AbstractRaceSwapApplier;
import com.openggf.game.arena.RaceSwapAdmission;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.data.RetailRomRevision;
import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.run.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** The S2 actor reconstruction and native Teleport_timer/Teleport_flag lifetime. */
public final class Sonic2RaceSwapApplier extends AbstractRaceSwapApplier
        implements RewindSnapshottable<Sonic2RaceSwapApplier.State> {
    public record State(int teleportTimer,boolean requestPending,boolean white,int backdropRgb) { }
    private int teleportTimer;
    private boolean requestPending;
    private boolean white;
    private int backdropRgb;

    @Override public int maxSwapSources(GameplayModeContext context) {
        return Math.toIntExact(context.getLevelManager().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == 0x26).count());
    }

    @Override public boolean request(ObjectServices services,AbstractPlayableSprite requester) {
        if (!services.runPolicy().swapEnabled()) return false;
        // teleport_monitor (s2.asm:26003-26011) refuses routine >= 6, not routine 4.
        if (RaceSwapAdmission.alive(requester)) requestPending=true;
        return true;
    }
    @Override public RaceSignal consumeSignal() {
        boolean requested=requestPending; requestPending=false;
        return requested ? RaceSignal.SWAP_REQUEST : RaceSignal.NONE;
    }
    @Override public void afterVBlank(GameplayModeContext context) {
        // Vint_Level (s2.asm:702-711): the old timer is serviced before the new
        // externally queued command is installed ahead of this row's gameplay owners.
        if (teleportTimer>0) teleportTimer--;
        white=teleportTimer>=0x10;
        executeQueued(context);
    }
    @Override public boolean freezesObjects() { return teleportTimer>0; }
    public int teleportTimer() { return teleportTimer; }
    public boolean white() { return white; }
    @Override public boolean whitensPalette() { return white; }
    public int backdropRgb() { return backdropRgb; }

    private static AbstractPlayableSprite local(GameplayModeContext context) {
        return (AbstractPlayableSprite)context.getCamera().getFocusedSprite();
    }
    private static boolean finished(GameplayModeContext context) {
        var state=context.getGameStateManager();
        return context.getLevelManager().getLevelGamestate().isTimerPaused()
                || state.isEndOfLevelActive() || state.isActCompletionSignalActive();
    }
    @Override public RaceEventResult apply(RaceEvent event,GameplayModeContext context) {
        java.util.Objects.requireNonNull(event);
        var player=local(context);
        boolean live=RaceSwapAdmission.alive(player) && !finished(context);
        if (event instanceof RaceEvent.SwapApply swap && live
                && RaceSwapAdmission.accepts(context,player,swap.target(),player.getStandXRadius(),
                        player.getStandYRadius(),TrackGeometry.ACT)) {
            var target=swap.target();
            var color=context.getLevelManager().getCurrentLevel().getBackdropColor();
            backdropRgb=((color.r&255)<<16)|((color.g&255)<<8)|(color.b&255);
            // Stream reconstruction (design section 8.3), replacing the full native RAM swap.
            player.setRollingFlagPreserveRadii(false);
            player.applyStandingRadii(false);
            player.setAir(true);
            player.setRollingJump(false); player.setJumping(false);
            player.setOnObject(false); player.setPushing(false);
            player.setPinballMode(false); player.setPinballSpeedLock(false);
            player.setMoveLockTimer(0); player.setDoubleJumpFlag(0); player.setDoubleJumpProperty((byte)0);
            player.setAngle((byte)0);
            player.setFlipAngle(0); player.setFlipType(0); player.setFlipsRemaining(0);
            NativePositionOps.writeXPosResetSubpixel(player,target.centreX());
            NativePositionOps.writeYPosResetSubpixel(player,target.centreY());
            player.setXSpeed((short)target.xVel()); player.setYSpeed((short)target.yVel());
            player.setGSpeed((short)target.xVel());
            player.setDirection(target.facingLeft() ? Direction.LEFT : Direction.RIGHT);
            player.setRenderFlips(target.facingLeft(),false);
            player.setTopSolidBit((byte)(target.secondaryPath()?0xE:0xC));
            player.setLrbSolidBit((byte)(target.secondaryPath()?0xF:0xD));
            player.setPriorityBucket(target.priorityBucket()); player.setHighPriority(target.highPriority());
            // process_swap_table (s2.asm:26040-26049): preserve the native prev_anim sentinel.
            player.publishRunAsPreviousAnimation();
            var revision=context.getWorldSession().getGameModule().getGameService(Sonic2RevisionProfile.class);
            if (revision!=null && revision.revision()==RetailRomRevision.S2_REV01) player.setMappingFrame(0);
            context.getLevelManager().reinitializeAfterRaceSwap();
            // swap_loop_objects (s2.asm:26150-26153), shipped fixBugs=0 timer/flag semantics.
            teleportTimer=0x40;
            context.getObjectManager().getObjectServices().playMusicMailboxNativeRequest(Sonic2Sfx.TELEPORT.id);
            return new RaceEventResult(event,RaceEventResult.Outcome.APPLIED);
        }
        // No-partner resolution is delayed until the command, unlike native icon selection.
        // A dead/finished recipient cannot receive a new effect (design section 8.2).
        if (live) MonitorObjectInstance.applyMonitorPowerup(7,player,context.getObjectManager().getObjectServices());
        return new RaceEventResult(new RaceEvent.SwapFallback(swapId(event)),RaceEventResult.Outcome.FALLBACK);
    }
    @Override public void draw(GameplayModeContext context,com.openggf.graphics.GraphicsManager graphics,Runnable draw) {
        if (!white || !graphics.isGlInitialized()) { draw.run(); return; }
        var level=context.getLevelManager().getCurrentLevel();
        var normal=new com.openggf.level.Palette[4];
        for (int line=0;line<4;line++) normal[line]=level.getPalette(line);
        var flashed=new com.openggf.level.Palette[4];
        for (int line=0;line<4;line++) {
            flashed[line]=normal[line].deepCopy();
            for (int color=0;color<16;color++) flashed[line].setColor(color,new com.openggf.level.Palette.Color((byte)255,(byte)255,(byte)255));
        }
        // Vint_Level (s2.asm:712-739), fixBugs=0: retain colour 32. Its extra final
        // colour write aliases already-white colour zero; model the resulting colours.
        flashed[2].setColor(0,new com.openggf.level.Palette.Color((byte)(backdropRgb>>>16),(byte)(backdropRgb>>>8),(byte)backdropRgb));
        com.openggf.graphics.PaletteDrawPresentation.withRows(graphics,flashed,draw);
    }
    @Override public void reset() { teleportTimer=0;requestPending=false;white=false;backdropRgb=0;clearEvents(); }
    @Override public String key() { return "sonic2.race-swap"; }
    @Override public State capture() { return new State(teleportTimer,requestPending,white,backdropRgb); }
    @Override public void restore(State state) {
        teleportTimer=state.teleportTimer(); requestPending=state.requestPending();
        white=state.white(); backdropRgb=state.backdropRgb(); clearEvents();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
}
