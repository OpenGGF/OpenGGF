package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C886, allocated by the bad Knuckles eyecatch at loc_5F7D8. */
public final class EndingMechaChargeObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private int parentSlot=-1,x,y,animationFrame,animationTimer,mappingFrame=0x11;
    private boolean initialized,following;
    private transient S3kRawAnimation script;
    private EndingMechaChargeObjectInstance(ObjectSpawn spawn){super(spawn,"EndingMechaCharge");}
    static void spawn(ObjectServices s,S3kPostCreditsObjectInstance parent){
        var manager=s.objectManager();int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,parent.getSlotIndex());
        if(slot<0)return;
        try{var child=ObjectConstructionContext.with(s,slot,()->new EndingMechaChargeObjectInstance(new ObjectSpawn(0,0,0,0,0,false,0)));
            child.parentSlot=parent.getSlotIndex();ObjectLifetimeOps.addDynamicAtReservedSlot(manager,child,slot);
        }catch(RuntimeException|Error failure){manager.releaseDynamicSlot(slot);throw failure;}
    }
    @Override public EndingMechaChargeObjectInstance recreateForRewind(RewindRecreateContext c){return new EndingMechaChargeObjectInstance(c.spawn());}
    private S3kPostCreditsObjectInstance parent(){return services().objectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class)
            .stream().filter(p->p.getSlotIndex()==parentSlot&&!p.isDestroyed()).findFirst().orElse(null);}
    @Override public void update(int vIntRunCount,PlayableEntity player){
        var parent=parent();
        if(!initialized){initialized=true;x=parent==null?0:parent.getX()-4;y=parent==null?0:parent.getY()+0xC;}
        if(following){
            if(parent==null||parent.mappingFrame()!=0xE){ObjectLifetimeOps.deleteNoRespawn(this);return;}
            x=parent.getX()-7;y=parent.getY()+3;return;
        }
        if(script==null)try{script=S3kRawAnimation.load(services().romReader(),0x7D668,19);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        var animation=new S3kRawAnimation.State();animation.script=0x7D668;animation.animFrame=animationFrame;
        animation.animFrameTimer=animationTimer;animation.mappingFrame=mappingFrame;
        script.animateMultiDelay(animation,()->{following=true;if(parent!=null)parent.finishMechaCharge();});
        animationFrame=animation.animFrame;animationTimer=animation.animFrameTimer;mappingFrame=animation.mappingFrame;
    }
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return true;}
    @Override public int getPriorityBucket(){return 4;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(!initialized||isDestroyed())return;var renderer=getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_CHARGE);
        if(renderer==null||!renderer.isReady())return;var camera=services().camera();
        renderer.drawFrameIndex(mappingFrame,EndingScreenProjection.worldX(camera,x),EndingScreenProjection.worldY(camera,y),false,false,1);
    }
}
