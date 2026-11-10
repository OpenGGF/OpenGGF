package com.openggf.game.soniccd;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import java.util.Objects;

/** Actual source-bound pinball publication receipt. Results and captures cannot manufacture one.
 * The legacy Bumper receipt remains a permitted original producer; the private Original constructor
 * accepts only the physical pass's private-constructor publication. No generic setter authority. */
public sealed interface CdR31PinballCall permits CdR31BumperCall,CdR31PinballCall.Original {
    CdPinballContext.PlayerStore playerStore();
    CdPinballContext.SavedStore savedStore();
    void requirePlayerPreparation(CdPlayerFrameRuntime owner);
    void requirePlayerApplication(CdPlayerFrameRuntime owner);
    void requireSavedPreparation(CdObjectPlacementRuntime owner);
    void requireSavedApplication(CdObjectPlacementRuntime owner);
    default void applyAttachmentPool(CdPlayerFrameRuntime owner){throw new IllegalStateException("Not original attachment pool adoption");}
    default CdSolidObject.Outcome attachmentOutcome(){throw new IllegalStateException("Not original attachment");}
    default CdSolidObject.Player attachmentPlayer(){throw new IllegalStateException("Not original attachment");}
    default CdSolidObject.Globals attachmentGlobals(){throw new IllegalStateException("Not original attachment");}
    static CdR31PinballCall fromOriginal(SonicCdR31aObjectPass.PinballPublication original){
        Objects.requireNonNull(original).requireCallPreparation();return new Original(original);
    }
    final class Original implements CdR31PinballCall {
        private final SonicCdR31aObjectPass.PinballPublication original;
        private final Thread thread=Thread.currentThread();
        private Original(SonicCdR31aObjectPass.PinballPublication original){this.original=original;}
        private void current(){if(thread!=Thread.currentThread())throw new IllegalStateException("Foreign pinball thread");}
        public CdPinballContext.PlayerStore playerStore(){current();if(!(original.operation() instanceof CdPinballContext.PlayerStore p))throw new IllegalStateException("Not original primary store");return p;}
        public CdPinballContext.SavedStore savedStore(){current();if(!(original.operation() instanceof CdPinballContext.SavedStore p))throw new IllegalStateException("Not original saved store");return p;}
        public void requirePlayerPreparation(CdPlayerFrameRuntime owner){current();if(owner!=original.frameOwner())throw new IllegalArgumentException("Foreign Frame");original.requireCallPreparation();}
        public void requirePlayerApplication(CdPlayerFrameRuntime owner){current();if(owner!=original.frameOwner())throw new IllegalArgumentException("Foreign Frame");original.requireCallApplication();}
        public void requireSavedPreparation(CdObjectPlacementRuntime owner){current();if(owner!=original.placementOwner())throw new IllegalArgumentException("Foreign placement");original.requireCallPreparation();}
        public void requireSavedApplication(CdObjectPlacementRuntime owner){current();if(owner!=original.placementOwner())throw new IllegalArgumentException("Foreign placement");original.requireCallApplication();}
        public void applyAttachmentPool(CdPlayerFrameRuntime owner){current();if(owner!=original.frameOwner())throw new IllegalArgumentException("Foreign attachment Frame");original.applyAttachmentPool();}
        public CdSolidObject.Outcome attachmentOutcome(){current();return original.attachmentOutcome();}
        public CdSolidObject.Player attachmentPlayer(){current();return original.attachmentPlayer();}
        public CdSolidObject.Globals attachmentGlobals(){current();return original.attachmentGlobals();}
    }
}
