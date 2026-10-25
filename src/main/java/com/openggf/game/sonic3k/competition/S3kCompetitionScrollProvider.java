package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.internal.ExplicitForegroundVerticalScroll;
import com.openggf.level.scroll.CameraDrivenScrollHandler;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Connects the two-camera native owner to the canonical world camera boundary.
 * The host initializes the owner and samples Sonic_RecordPosCompetition after
 * each actor, before objects; this provider follows the post-object bodies once
 * before pattern animation. Rendering only reads that completed native pass.
 *
 * <p>Rewind ownership is the active handler in ParallaxManager. Do not also
 * register the supplied scroll owner as a separate rewind adapter.
 */
public final class S3kCompetitionScrollProvider implements ScrollHandlerProvider {
    private final ZoneConstants zoneConstants;
    private final Supplier<S3kCompetitionScroll> scroll;
    private final Supplier<List<S3kCompetitionPlayer>> players;
    private final Consumer<S3kCompetitionScroll.View> mirrorCamera;
    private final Handler handler=new Handler();

    public S3kCompetitionScrollProvider(ZoneConstants zoneConstants,
            Supplier<S3kCompetitionScroll> scroll,
            Supplier<List<S3kCompetitionPlayer>> players,
            Consumer<S3kCompetitionScroll.View> mirrorCamera) {
        this.zoneConstants=Objects.requireNonNull(zoneConstants);
        this.scroll=Objects.requireNonNull(scroll);
        this.players=Objects.requireNonNull(players);
        this.mirrorCamera=Objects.requireNonNull(mirrorCamera);
    }

    /** The host constructs the native owner from ROM before publishing it. */
    @Override public void load(Rom rom) { Objects.requireNonNull(rom); }
    @Override public ZoneConstants getZoneConstants() { return zoneConstants; }
    @Override public ZoneScrollHandler getHandler(int zoneIndex) {
        if(zoneIndex<0xE || zoneIndex>0x12) return null;
        S3kCompetitionScroll current=scroll.get();
        return current!=null && current.arena().zoneId()==zoneIndex?handler:null;
    }
    private S3kCompetitionScroll current() {
        return Objects.requireNonNull(scroll.get(),"Native competition scroll owner is not installed");
    }
    private void mirror(S3kCompetitionScroll owner) {
        mirrorCamera.accept(owner.view(0));mirrorCamera.accept(owner.view(1));
    }

    private final class Handler implements ZoneScrollHandler,CameraDrivenScrollHandler,
            ExplicitForegroundVerticalScroll {
        /** Host.initializeCamerasAndScroll owns init; generic init must preserve it. */
        @Override public void init(int actId,int cameraX,int cameraY) { }

        @Override public boolean advanceCameraForFrame(Camera camera,int actId) {
            S3kCompetitionScroll owner=current();
            List<S3kCompetitionPlayer> currentPlayers=List.copyOf(players.get());
            if(currentPlayers.isEmpty() || currentPlayers.size()>2 || currentPlayers.getFirst().slot()!=0
                    || currentPlayers.size()==2 && currentPlayers.get(1).slot()!=1)
                throw new IllegalStateException("Native competition requires current P1/P2 actor order");
            var p1=currentPlayers.getFirst();var p2=currentPlayers.size()==2?currentPlayers.get(1):null;
            // sub_123C2 locks only the dead slot; loc_124C8 releases its lock on
            // respawn. Arena objects do not set a separate camera-look state.
            var first=new S3kCompetitionScroll.Tracking(p1,0x60,p1.routine()==6);
            var second=p2==null?null:new S3kCompetitionScroll.Tracking(p2,0x60,p2.routine()==6);
            owner.trackPlayers(first,second,S3kCompetitionScroll.TrackingOptions.NORMAL);
            mirror(owner);
            return true;
        }

        @Override public void update(int[] horizScrollBuf,int cameraX,int cameraY,int frameCounter,int actId) {
            if(horizScrollBuf.length<224) throw new IllegalArgumentException("Competition raster requires 224 lines");
            int[] raster=current().horizontalScrollBuffer();
            System.arraycopy(raster,0,horizScrollBuf,0,raster.length);
        }
        @Override public short getVscrollFactorBG() { return (short)current().view(0).backgroundVScroll(); }
        @Override public short getVscrollFactorFG() { return (short)current().view(0).foregroundVScroll(); }
        @Override public boolean foregroundVerticalScrollExplicit() { return true; }
        @Override public int getBgCameraX() { return current().view(0).backgroundX(); }
        @Override public int getMinScrollOffset() { return offsetBound(true); }
        @Override public int getMaxScrollOffset() { return offsetBound(false); }
        private int offsetBound(boolean minimum) {
            int result=minimum?Integer.MAX_VALUE:Integer.MIN_VALUE;
            for(int packed:current().horizontalScrollBuffer()) {
                int offset=(short)packed-(short)(packed>>>16);
                result=minimum?Math.min(result,offset):Math.max(result,offset);
            }
            return result;
        }
        @Override public Object captureRewindState() { return current().capture(); }
        @Override public void restoreRewindState(Object state) {
            if(state==null) return;
            if(!(state instanceof S3kCompetitionScroll.State nativeState))
                throw new IllegalArgumentException("Native competition scroll snapshot required");
            S3kCompetitionScroll owner=current();owner.restore(nativeState);
            if(nativeState.initialized()) mirror(owner);
        }
    }
}
