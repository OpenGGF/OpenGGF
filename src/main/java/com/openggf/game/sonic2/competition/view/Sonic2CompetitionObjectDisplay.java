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
        int id = owner.getLiveObjectId() & 0xFF;
        if (id == 0) return null;
        var capture = SpriteFrameCaptureInternalAccess.capture(draw);
        if (capture.calls().isEmpty()) return null;
        if (capture.calls().size() != 1
                || !(capture.calls().getFirst() instanceof SpriteFrameCaptureInternalAccess.FrameCall call))
            throw unsupported(owner, id);
        int nativeFrame=call.frameIndex();
        var entry = Sonic2CompetitionSpriteArtTable.entry(id);
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
