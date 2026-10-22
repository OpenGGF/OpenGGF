package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Registration-only code replacement: no delete, respawn mutation or onUnload. */
final class ObjectNativeSstOverwrite {
    private ObjectNativeSstOverwrite() { }
    record Surface(ObjectServices services,ObjectSlotLayout layout,SlotAllocator allocator,
            Map<ObjectSpawn,ObjectInstance> active,Map<ObjectInstance,ObjectSpawn> inverse,
            List<ObjectInstance> dynamic,ObjectCallbackRouter callbacks,DynamicObjectOwnership ownership,
            Map<ObjectInstance,ObjectRefId> identities,Set<ObjectInstance> deferred,ObjectInstance[] exec,
            IntUnaryOperator execIndex,boolean updating,int currentExec,Consumer<ObjectInstance> add,Runnable dirty,
            ObjectNativeSstWord2C word2C) { }
    static <T extends AbstractObjectInstance> T create(Surface s,Function<NativeSstState,T> writer) {
        int free=s.allocator().firstFreeSlot();
        int slot=free>=0?free:s.layout().lastDynamicSlotExclusive()-1;
        ObjectInstance old=free>=0?null:occupant(s,slot);
        NativeSstState residue=old==null?NativeSstState.CLEARED:s.callbacks().call(old,()->residue(old));
        T next=ObjectConstructionContext.with(s.services(),slot,()->writer.apply(residue));
        if(next==null)throw new IllegalStateException("Native partial writer returned no dispatch owner");
        s.word2C().preserve(slot);
        ObjectSpawn placed=old==null?null:s.inverse().remove(old);
        ObjectRefId identity=old==null?null:s.identities().remove(old);
        if(old!=null){
            s.dynamic().remove(old);s.ownership().remove(old);s.deferred().remove(old);s.callbacks().unregister(old);
        }
        if(identity!=null)s.identities().put(next,identity);
        s.add().accept(next);
        if(placed!=null){
            s.dynamic().remove(next);s.active().put(placed,next);s.inverse().put(next,placed);
        }
        int exec=s.execIndex().applyAsInt(slot);
        if(s.updating()&&exec<=s.currentExec())s.exec()[exec]=next;
        // Earlier/current slots wait; add() publishes later slots in the current walk.
        s.dirty().run();return next;
    }
    static AbstractObjectInstance currentOwner(java.util.Collection<ObjectInstance> placed,
            List<ObjectInstance> dynamic,ObjectCallbackRouter callbacks,int slot) {
        AbstractObjectInstance found=null;
        for(var group:List.of(placed,dynamic))for(var o:group)
            if(o instanceof AbstractObjectInstance a&&callbacks.call(o,a::getSlotIndex)==slot
                    &&!callbacks.call(o,a::isDestroyed)){
                if(found!=null&&found!=a)throw new IllegalStateException("Multiple native SST dispatch owners: "+slot);
                found=a;
            }
        return found;
    }
    private static ObjectInstance occupant(Surface s,int slot) {
        var found=currentOwner(s.active().values(),s.dynamic(),s.callbacks(),slot);
        if(found==null)throw new IllegalStateException("Occupied native SST has no live field owner: "+slot);
        return found;
    }
    private static NativeSstState residue(ObjectInstance old) {
        if(!(old instanceof NativeSstStateSource source))
            throw new IllegalStateException("Unchecked native overwrite needs a live projection: "+old.getClass().getName());
        return java.util.Objects.requireNonNull(source.nativeSstState(),"native SST projection");
    }
}
