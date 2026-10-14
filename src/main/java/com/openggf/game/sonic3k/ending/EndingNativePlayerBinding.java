package com.openggf.game.sonic3k.ending;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.sprites.managers.TailsTailsController;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Native ending scripts own P1's S3K mapping/palette/controller presentation.
 * Ordinary gameplay delegation is retained behind this reversible scene binding.
 */
public final class EndingNativePlayerBinding {
    private boolean active;
    private AbstractPlayableSprite bound;
    private SuperStateController ordinaryController;
    private PlayerSpriteRenderer ordinaryRenderer;
    private SpriteAnimationProfile ordinaryProfile;
    private SpriteAnimationSet ordinaryAnimations;
    private PlayerSpriteRenderer nativeRenderer;
    private SpriteAnimationProfile nativeProfile;
    private SpriteAnimationSet nativeAnimations;
    private SceneController nativeController;
    private TailsTailsController ordinaryTail;
    private TailsTailsController nativeTail;

    public void ensure(ObjectServices services){
        if(!(services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite player))return;
        if(active&&bound==player)return;
        if(player.getSuperStateController() instanceof Sonic3kSuperStateController)return;
        prepare(services,player);active=true;bindNormal();
    }
    private void prepare(ObjectServices services,AbstractPlayableSprite player){
        if(bound==player&&nativeController!=null)return;
        bound=player;ordinaryController=player.getSuperStateController();ordinaryRenderer=player.getSpriteRenderer();
        ordinaryProfile=player.getAnimationProfile();ordinaryAnimations=player.getAnimationSet();
        ordinaryTail=player.getTailsTailsController();
        try{
            var nativeArt=new Sonic3kPlayerArt(services.romReader());
            var art=nativeArt.loadForCharacter(player.getCode());
            if(art==null)throw new IllegalStateException("Native ending player has no S3K art: "+player.getCode());
            nativeRenderer=new PlayerSpriteRenderer(art,services.graphicsManager());
            nativeProfile=art.animationProfile();nativeAnimations=art.animationSet();
            if(player instanceof com.openggf.sprites.playable.Tails)
                nativeTail=new TailsTailsController(player,new PlayerSpriteRenderer(nativeArt.loadTailsTail(),services.graphicsManager()),true);
            nativeController=new SceneController(player,services);
            nativeController.loadRomData(services.romReader());nativeController.setRomDataPreLoaded(true);
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void bindNormal(){
        bound.setSpriteRenderer(nativeRenderer);bound.setAnimationProfile(nativeProfile);bound.setAnimationSet(nativeAnimations);
        bound.setSuperStateController(nativeController);
        if(nativeTail!=null)bound.setTailsTailsController(nativeTail);
        nativeRenderer.invalidateDplcCache();
        if(nativeTail!=null)nativeTail.getRenderer().invalidateDplcCache();
    }
    private void detach(){
        if(bound==null)return;
        bound.setSuperStateController(ordinaryController);bound.setSpriteRenderer(ordinaryRenderer);
        bound.setAnimationProfile(ordinaryProfile);bound.setAnimationSet(ordinaryAnimations);
        bound.setTailsTailsController(ordinaryTail);
        ordinaryRenderer.invalidateDplcCache();
        if(ordinaryTail!=null)ordinaryTail.getRenderer().invalidateDplcCache();
        active=false;
    }
    private Value capture(){
        var player=bound!=null?bound:GameServices.sprites().getMainPlayable();
        var tail=player==null?null:player.getTailsTailsController();
        var tailState=tail==null?null:tail.captureRewindState();
        return active?new Value(true,nativeController.captureRewindState(),tailState)
                :tailState==null?Value.EMPTY:new Value(false,null,tailState);
    }
    private void restore(Value value,ObjectServices services){
        if(!value.active()){
            if(active)detach();
            restoreTail(value.tails(),services);
            return;
        }
        var player=services.playerQuery().mainPlayerOrNull();
        if(!(player instanceof AbstractPlayableSprite playable))throw new IllegalStateException("Missing ending P1 during binding restore");
        prepare(services,playable);active=true;bindNormal();nativeController.restoreRewindState(value.controller());
        restoreTail(value.tails(),services);
    }
    private void restoreTail(TailsTailsController.RewindState state,ObjectServices services){
        var player=services.playerQuery().mainPlayerOrNull();
        if(state!=null&&player instanceof AbstractPlayableSprite playable&&playable.getTailsTailsController()!=null)
            playable.getTailsTailsController().restoreRewindState(state);
    }
    public record Value(boolean active,SuperStateController.RewindState controller,TailsTailsController.RewindState tails){
        private static final Value EMPTY=new Value(false,null,null);
    }
    /** Resource references are derived; captured controller/tail RAM is reapplied after rebinding. */
    public static final class Adapter implements RewindSnapshottable<Value>{
        @Override public String key(){return "s3k-ending-native-player-binding";}
        private EndingNativePlayerBinding current(){return S3kEndingCoordinator.current(GameServices.zoneRuntimeRegistry())
                .map(S3kEndingState::nativePlayer).orElse(null);}
        @Override public Value capture(){var binding=current();return binding==null?Value.EMPTY:binding.capture();}
        @Override public void restore(Value value){var binding=current();if(binding!=null)
            binding.restore(value,GameServices.level().getObjectManager().getObjectServices());}
        @Override public void resetForMissingSnapshot(){restore(Value.EMPTY);}
    }
    private static final class SceneController extends Sonic3kSuperStateController{
        private final ObjectServices services;
        SceneController(AbstractPlayableSprite player,ObjectServices services){super(player);this.services=services;}
        @Override protected PaletteTarget resolvePaletteTarget(int line){
            var level=services.currentLevel();var palette=level==null?null:level.getPalette(line);
            return palette==null?null:com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativePaletteTarget(palette,line);
        }
        private void bindNativeContext(){var renderer=player.getSpriteRenderer();if(renderer!=null)renderer.setRenderContext(null);}
        @Override public void update(){super.update();bindNativeContext();}
        @Override public void restoreRewindState(RewindState state){super.restoreRewindState(state);bindNativeContext();}
        @Override public void initializeEndingSuperSonic(){super.initializeEndingSuperSonic();bindNativeContext();}
        @Override public void initializeEndingHyperSonic(){super.initializeEndingHyperSonic();bindNativeContext();}
        @Override public void initializeEndingSuperTails(){super.initializeEndingSuperTails();bindNativeContext();}
        @Override public void initializeEndingKnucklesFlight(){super.initializeEndingKnucklesFlight();bindNativeContext();}
        @Override public void completeEndingRescueRevertMappings(){super.completeEndingRescueRevertMappings();bindNativeContext();}
        @Override public void startEndingReunionTransformation(){super.startEndingReunionTransformation();bindNativeContext();}
    }
}
