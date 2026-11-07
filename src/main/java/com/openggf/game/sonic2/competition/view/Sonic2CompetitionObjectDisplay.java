package com.openggf.game.sonic2.competition.view;

import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.Mapping;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.render.SpriteFrameCaptureInternalAccess;
import java.util.Objects;

/** Read-only ordinary-draw projection. Unsupported owners must adopt the explicit source port. */
public final class Sonic2CompetitionObjectDisplay {
    private Sonic2CompetitionObjectDisplay() { }
    public record Deferred(int slot,int id,String name,String citation) { }
    public static boolean adopted(com.openggf.level.objects.ObjectInstance owner) {
        if (!(owner instanceof AbstractObjectInstance object)) return false;
        return owner instanceof Sonic2NativeDisplaySource
                || !Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.containsKey(object.getLiveObjectId()&255);
    }
    /** Explicit C2 exclusions remain observable; qualification still requires their adoption. */
    public static java.util.List<Deferred> deferred(com.openggf.level.objects.ObjectManager manager) {
        var result=new java.util.ArrayList<Deferred>();
        for (var owner:manager.getActiveObjects()) if(owner instanceof AbstractObjectInstance object && !adopted(owner)) {
            int id=object.getLiveObjectId()&255;var row=Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.get(id);
            result.add(new Deferred(object.getSlotIndex(),id,row.name(),row.initCitation()));
        }
        return java.util.List.copyOf(result);
    }

    /** Native FIFO prefixes and the inferred legacy suffix share one display-list budget. */
    public static java.util.List<Sonic2CompetitionDisplayInputs.Admission> admissions(
            com.openggf.level.objects.ObjectManager manager, ObjectArtProvider art) {
        record Source(int bucket,int slot,AbstractObjectInstance owner,boolean nativeCall,int order) { }
        record Projected(int slot,int bucket,ObjectSlot display,boolean nativeCall,int order) { }
        var sources=new java.util.ArrayList<Source>();
        com.openggf.level.objects.ObjectDrawInternalAccess.visitNativeDisplayOrder(manager,(bucket,slot,owner,nativeCall)->{
            if (!(owner instanceof AbstractObjectInstance object))
                throw new IllegalStateException("Native object display requires its actual owner at slot "+slot);
            sources.add(new Source(bucket,slot,object,nativeCall,sources.size()));
        });
        var nativeSlots=new java.util.HashSet<Integer>();
        for (var source:sources) if(source.nativeCall()) nativeSlots.add(source.slot());
        var projected=new java.util.ArrayList<Projected>();
        for (var source:sources) {
            // The lead retains these named lane-C obligations. They have no
            // ordinary-draw fallback and do not acquire native visibility authority.
            if (!adopted(source.owner())) continue;
            for (var group:projectNativeRole(manager,source.owner(),art,
                    ()->source.owner().appendRenderCommands(new java.util.ArrayList<>()))) {
                boolean ownCall=source.nativeCall() && group.slot()==source.slot();
                if (!ownCall && nativeSlots.contains(group.slot())) continue;
                projected.add(new Projected(group.slot(),ownCall?source.bucket():group.priorityBucket(),
                        group.occupant(),ownCall,source.order()));
            }
        }
        // Preserve explicit repeats/FIFO first. A legacy bucket correction (e.g.
        // MCZ bridge0 or Crawl3) must retain SST order within its corrected bucket.
        projected.sort(java.util.Comparator.comparingInt(Projected::bucket)
                .thenComparingInt(p->p.nativeCall()?0:1)
                .thenComparingInt(p->p.nativeCall()?p.order():p.slot()));
        var result=new java.util.ArrayList<Sonic2CompetitionDisplayInputs.Admission>();
        for(var p:projected) result.add(new Sonic2CompetitionDisplayInputs.Admission(
                p.slot(),p.bucket(),result.size(),p.display()));
        return java.util.List.copyOf(result);
    }

    private static java.util.List<Sonic2NativeDisplaySource.DisplaySlot> projectNativeRole(
            com.openggf.level.objects.ObjectManager manager,AbstractObjectInstance owner,ObjectArtProvider art,Runnable draw) {
        if (owner instanceof Sonic2NativeDisplaySource) return projectAll(owner,art,draw);
        if (!(owner instanceof com.openggf.level.objects.ShieldObjectInstance shield)) return projectAll(owner,art,draw);
        // LevelOnly_Object_RAM / Sonic_Shield / Tails_Shield (s2.constants.asm:1149-1165):
        // six fixed owners follow Tails_Tails at slot128. Consume the allocator's real owner, never invent a slot.
        final int sonicShieldSlot=128+6;
        int human=owner.getSlotIndex()-sonicShieldSlot;
        var roster=com.openggf.game.competition.NativeCompetitionRoster.from(manager.getObjectServices().worldSession());
        if(human<0 || human>1 || roster==null
                || com.openggf.level.objects.NativeSstSlotAccess.currentOwner(manager,owner.getSlotIndex())!=owner
                || !shield.isShieldFor(roster.player(human),com.openggf.game.ShieldType.BASIC)) throw unsupported(owner,0x38);
        var projected=projectCaptured(owner,art,draw,0x38);
        if(projected==null) return java.util.List.of();
        var player=roster.player(human);
        // Obj38_Shield copies parent status (s2.asm:42411-42415). Anim_Next
        // (s2.asm:30441-30449) copies its low two bits, including player's air bit as Y flip.
        int flags=4 | (player.getDirection()==com.openggf.physics.Direction.LEFT?1:0) | (player.getAir()?2:0);
        projected=new ObjectSlot(projected.id(),flags,projected.x(),projected.y(),projected.width(),projected.yRadius(),
                projected.mapping(),projected.artTile(),null);
        return java.util.List.of(new Sonic2NativeDisplaySource.DisplaySlot(owner.getSlotIndex(),
                Sonic2CompetitionSpriteArtTable.fixedRoleRows().getFirst().priorityBucket(),projected));
    }

    public static java.util.List<Sonic2NativeDisplaySource.DisplaySlot> projectAll(
            AbstractObjectInstance owner, ObjectArtProvider art, Runnable draw) {
        if(owner instanceof Sonic2NativeDisplaySource source) return java.util.List.copyOf(source.nativeDisplaySlots());
        ObjectSlot display=project(owner,art,draw);
        return display==null ? java.util.List.of() : java.util.List.of(
                new Sonic2NativeDisplaySource.DisplaySlot(owner.getSlotIndex(),nativeBucket(owner,display),display));
    }

    public static ObjectSlot project(AbstractObjectInstance owner, ObjectArtProvider art, Runnable draw) {
        Objects.requireNonNull(owner);
        if (owner instanceof Sonic2NativeDisplaySource source) return source.nativeDisplaySlot();
        return projectCaptured(owner,art,draw,owner.getLiveObjectId()&255);
    }

    private static ObjectSlot projectCaptured(AbstractObjectInstance owner,ObjectArtProvider art,Runnable draw,int id) {
        if (id == 0) return null;
        var capture = SpriteFrameCaptureInternalAccess.capture(draw);
        if (capture.calls().isEmpty()) return null;
        if (capture.calls().size() != 1
                || !(capture.calls().getFirst() instanceof SpriteFrameCaptureInternalAccess.FrameCall call))
            throw unsupported(owner, id);
        int nativeFrame=call.frameIndex();
        var entry = id==0x38 ? Sonic2CompetitionSpriteArtTable.fixedRoleRows().getFirst().display()
                : Sonic2CompetitionSpriteArtTable.entry(id);
        // AnimalObjectInstance's combined sheet indexes (mappingSet*2+artVariant)*3+frame;
        // Obj28_Properties (s2.asm:24504-24524).
        // Obj28_InitRandom (s2.asm:24596-24630).
        if(id==0x28) {
            int set=call.frameIndex()/6, variant=(call.frameIndex()/3)%2;
            entry=Sonic2CompetitionSpriteArtTable.row("animal-"+set+"-"+variant).display();
            nativeFrame=call.frameIndex()%3;
        }
        if(entry==null) {
            int subtype=owner.getSpawn()==null ? 0 : owner.getSpawn().subtype();
            var candidates=Sonic2CompetitionSpriteArtTable.captureRows().stream()
                    .filter(row->row.id()==id && row.accepts(subtype)
                            && art.getRenderer(row.display().expectedArtKey())==call.renderer()).toList();
            if(candidates.isEmpty()) throw unsupported(owner,id);
            // Same-sheet child variants retain their native bucket. Their unused Y radius does not cull.
            entry=candidates.stream().filter(row->row.priorityBucket()==owner.getPriorityBucket())
                    .findFirst().orElse(candidates.getFirst()).display();
        }
        var expected = art.getRenderer(entry.expectedArtKey());
        if (expected == null || call.renderer() != expected || call.frameIndex() < 0
                || call.frameIndex() > 255 || call.frameIndex() >= call.sheet().getFrameCount()
                || call.frameIndex() >= Sonic2CompetitionSpriteArtTable.nativeFrameLimit(entry.mappingsAddress()))
            throw unsupported(owner, id);
        int word = Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(entry.artTile1P());
        word = (word & 0x7FFF) | (owner.isHighPriority() ? 0x8000 : 0);
        if (call.paletteOverride() >= 0) word = (word & ~0x6000) | ((call.paletteOverride() & 3) << 13);
        return new ObjectSlot(id, entry.renderFlagsInit() | (call.hFlip()?1:0) | (call.vFlip()?2:0),
                call.originX() & 0xFFFF, call.originY() & 0xFFFF, entry.widthPixels(), entry.yRadius(),
                new Mapping(entry.mappingsAddress(), nativeFrame), word, null);
    }

    private static int nativeBucket(AbstractObjectInstance owner,ObjectSlot display) {
        int subtype=owner.getSpawn()==null ? 0 : owner.getSpawn().subtype();
        var rows=Sonic2CompetitionSpriteArtTable.captureRows().stream()
                .filter(r->r.id()==display.id() && r.accepts(subtype)
                        && r.display().mappingsAddress()==display.mapping().address()
                        && r.display().widthPixels()==display.width()).toList();
        return rows.stream().filter(r->r.priorityBucket()==owner.getPriorityBucket()).findFirst()
                .or(()->rows.stream().findFirst()).map(Sonic2CompetitionSpriteArtTable.Row::priorityBucket)
                .orElse(owner.getPriorityBucket());
    }

    private static IllegalStateException unsupported(AbstractObjectInstance owner, int id) {
        return new IllegalStateException(String.format("Native display requires adoption: %s id $%02X",
                owner.getClass().getName(), id));
    }
}
