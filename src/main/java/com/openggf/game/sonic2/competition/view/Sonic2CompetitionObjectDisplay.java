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

    public static ObjectSlot project(AbstractObjectInstance owner, ObjectArtProvider art, Runnable draw) {
        Objects.requireNonNull(owner);
        if (owner instanceof Sonic2NativeDisplaySource source) return source.nativeDisplaySlot();
        int id = owner.getLiveObjectId() & 0xFF;
        if (id == 0) return null;
        var capture = SpriteFrameCaptureInternalAccess.capture(draw);
        if (capture.calls().isEmpty()) return null;
        var entry = Sonic2CompetitionSpriteArtTable.entry(id);
        if (entry == null || capture.calls().size() != 1
                || !(capture.calls().getFirst() instanceof SpriteFrameCaptureInternalAccess.FrameCall call))
            throw unsupported(owner, id);
        var expected = art.getRenderer(entry.expectedArtKey());
        if (expected == null || call.renderer() != expected || call.frameIndex() < 0
                || call.frameIndex() > 255 || call.frameIndex() >= call.sheet().getFrameCount())
            throw unsupported(owner, id);
        int word = Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(entry.artTile1P());
        word = (word & 0x7FFF) | (owner.isHighPriority() ? 0x8000 : 0);
        if (call.paletteOverride() >= 0) word = (word & ~0x6000) | ((call.paletteOverride() & 3) << 13);
        return new ObjectSlot(id, entry.renderFlagsInit() | (call.hFlip() ? 1 : 0) | (call.vFlip() ? 2 : 0),
                call.originX() & 0xFFFF, call.originY() & 0xFFFF, entry.widthPixels(), entry.yRadius(),
                new Mapping(entry.mappingsAddress(), call.frameIndex()), word, null);
    }

    private static IllegalStateException unsupported(AbstractObjectInstance owner, int id) {
        return new IllegalStateException(String.format("Native display requires adoption: %s id $%02X",
                owner.getClass().getName(), id));
    }
}
