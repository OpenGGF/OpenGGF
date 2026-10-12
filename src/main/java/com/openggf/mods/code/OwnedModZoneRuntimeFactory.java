package com.openggf.mods.code;

import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.ApplyStrategy;
import com.openggf.game.animation.ChannelGuard;
import com.openggf.game.animation.PhaseSource;
import com.openggf.game.modzone.ModZoneRuntimeContext;
import com.openggf.game.modzone.ModZoneRuntimeFactory;
import com.openggf.game.modzone.ModZoneRuntimeServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Publication bridge: both creation and every returned callback retain the verified zone owner. */
final class OwnedModZoneRuntimeFactory implements ModZoneRuntimeFactory {
    private final String owner;
    private final String localKey;
    private final ModZoneRuntimeFactory delegate;
    private final ModFaultBoundary boundary;
    private final int limit;
    OwnedModZoneRuntimeFactory(String owner, String localKey, ModZoneRuntimeFactory delegate, ModFaultBoundary boundary) {
        this(owner, localKey, delegate, boundary, com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES);
    }
    OwnedModZoneRuntimeFactory(String owner, String localKey, ModZoneRuntimeFactory delegate, ModFaultBoundary boundary, int limit) {
        this.limit = limit;
        this.owner = owner; this.localKey = localKey;
        this.delegate = Objects.requireNonNull(delegate); this.boundary = Objects.requireNonNull(boundary);
    }
    @Override public ModZoneRuntimeServices create(ModZoneRuntimeContext context) {
        return boundary.call(owner, () -> {
            if (!context.destination().ownerModId().equals(owner)
                    || !context.destination().localName().equals(localKey))
                throw new IllegalArgumentException("Runtime context belongs to another contributed zone");
            ModZoneRuntimeServices raw = Objects.requireNonNull(delegate.create(context), "Zone runtime factory result");
            int entries = raw.animatedTiles().size() + raw.renderEffects().size() + raw.renderModes().size() + raw.rewindAdapters().size()
                    + (raw.features() == null ? 0 : 1) + (raw.water() == null ? 0 : 1)
                    + (raw.scroll() == null ? 0 : 1) + (raw.state() == null ? 0 : 1)
                    + (raw.paletteAnimation() == null ? 0 : 1);
            if (entries > limit) throw new IllegalArgumentException("Owner zone runtime limit exceeded");
            if (raw.animatedTiles().stream().map(AnimatedTileChannel::channelId).distinct().count() != raw.animatedTiles().size())
                throw new IllegalArgumentException("Duplicate zone animated tile channel");
            if (raw.state() != null && (!context.hostGameId().equals(raw.state().gameId())
                    || context.zoneIndex() != raw.state().zoneIndex() || context.actIndex() != raw.state().actIndex()))
                throw new IllegalArgumentException("Zone state must describe the engine-selected runtime context");
            LinkedHashMap<String,RewindSnapshottable<?>> identities = new LinkedHashMap<>();
            raw.rewindAdapters().forEach((key, value) -> identities.put(
                    "zone-runtime/" + localKey + "/" + context.actIndex() + "/" + key, value));
            var callbacks = new OwnerBoundCallbacks(owner, boundary,
                    limit, identities);
            var animations = raw.animatedTiles().stream().map(channel -> new AnimatedTileChannel(
                    owner + ":" + localKey + ":" + com.openggf.game.ModKeySyntax.requireLocalName(channel.channelId()),
                    callbacks.bind(ChannelGuard.class, channel.guard()),
                    callbacks.bind(PhaseSource.class, channel.phaseSource()), channel.destinationPlan(), channel.cachePolicy(),
                    callbacks.bind(ApplyStrategy.class, channel.applyStrategy()))).toList();
            LinkedHashMap<String,RewindSnapshottable<?>> adapters = new LinkedHashMap<>();
            raw.rewindAdapters().forEach((key,value) -> adapters.put(key,callbacks.adapter(value)));
            return new ModZoneRuntimeServices(
                    callbacks.bind(com.openggf.game.ZoneFeatureProvider.class, raw.features()),
                    callbacks.bind(com.openggf.game.WaterDataProvider.class, raw.water()),
                    callbacks.bind(com.openggf.level.scroll.ZoneScrollHandler.class, raw.scroll()),
                    callbacks.bind(com.openggf.game.zone.ZoneRuntimeState.class, raw.state()), animations,
                    callbacks.bind(com.openggf.level.animation.AnimatedPaletteManager.class, raw.paletteAnimation()),
                    raw.renderEffects().stream().map(value -> callbacks.bind(com.openggf.game.render.SpecialRenderEffect.class,value)).toList(),
                    raw.renderModes().stream().map(value -> callbacks.bind(com.openggf.game.render.AdvancedRenderMode.class,value)).toList(), adapters);
        });
    }
}
