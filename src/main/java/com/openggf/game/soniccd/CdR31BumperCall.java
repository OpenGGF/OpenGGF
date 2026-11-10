package com.openggf.game.soniccd;
import com.openggf.game.soniccd.objects.CdPinballContext;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import java.util.Objects;

/** One opaque original Bumper operation, issued only by the actual package-private physical pass.
 * No public constructor/factory, capture, generic setter or completed-result entry exists.
 * The private pass callbacks revalidate source/receiver/thread/slot/site/order/full Core/Host cut.
 */
public final class CdR31BumperCall implements CdR31PinballCall {
    private final CdPlayerFrameRuntime frame;private final CdObjectPlacementRuntime placement;
    private final CdPinballContext.Operation original;private final Runnable prepare,apply;
    private final Thread thread=Thread.currentThread();
    private CdR31BumperCall(SonicCdR31aObjectPass.BumperPublication original){
        frame=original.frameOwner();placement=original.placementOwner();this.original=original.operation();
        prepare=original::requireCallPreparation;apply=original::requireCallApplication;
    }
    /** Package-only access still requires an unforgeable original private-constructor publication. */
    static CdR31BumperCall fromOriginal(SonicCdR31aObjectPass.BumperPublication original){
        Objects.requireNonNull(original).requireCallPreparation();return new CdR31BumperCall(original);
    }
    private void current(){if(thread!=Thread.currentThread())throw new IllegalStateException("Foreign Bumper call thread");}
    public CdPinballContext.PlayerStore playerStore(){current();if(!(original instanceof CdPinballContext.PlayerStore p))throw new IllegalStateException("Not original Bumper player store");return p;}
    public CdPinballContext.SavedStore savedStore(){current();if(!(original instanceof CdPinballContext.SavedStore p))throw new IllegalStateException("Not original Bumper saved store");return p;}
    public void requirePlayerPreparation(CdPlayerFrameRuntime owner){current();if(owner!=frame)throw new IllegalArgumentException("Foreign Frame");prepare.run();}
    public void requirePlayerApplication(CdPlayerFrameRuntime owner){current();if(owner!=frame)throw new IllegalArgumentException("Foreign Frame");apply.run();}
    public void requireSavedPreparation(CdObjectPlacementRuntime owner){current();if(owner!=placement)throw new IllegalArgumentException("Foreign placement");prepare.run();}
    public void requireSavedApplication(CdObjectPlacementRuntime owner){current();if(owner!=placement)throw new IllegalArgumentException("Foreign placement");apply.run();}
}
