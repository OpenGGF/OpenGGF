package com.openggf.level.objects;

import com.openggf.game.*;
import com.openggf.game.internal.PoweredAnimationSource;
import com.openggf.level.render.*;
import com.openggf.sprites.animation.*;
import com.openggf.sprites.art.SpriteArtSet;
import java.util.*;

/** Initializes immutable shared tile/mapping storage; switching forms changes only script/profile views. */
public final class PoweredPlayableArtBinding {
    private PoweredPlayableArtBinding() { }
    public record Result(SpriteArtSet normal, Map<SuperFormProfile.Tier, SpriteArtSet> powered) { }

    private record NormalPhase(int frame, int offsetX, int offsetY) { }

    private static NormalPhase normalPhase(SpriteArtSet normal, PoweredAnimationSource source, String key) {
        if (key == null || source == null) return null;
        for (var entry : source.families().entrySet()) {
            var binding = entry.getValue(); var script = normal.animationSet().getScript(entry.getKey());
            if (binding.family() == null || script == null) continue;
            for (int step = 0; step < binding.phases().size() && step < script.frames().size(); step++)
                if (binding.phaseKey(step).equals(key)) return new NormalPhase(script.frames().get(step), binding.offsetX(), binding.offsetY());
        }
        return null;
    }

    private static SpriteMappingFrame translate(SpriteMappingFrame frame, int x, int y) {
        if (x == 0 && y == 0) return frame;
        return new SpriteMappingFrame(frame.pieces().stream().map(piece -> new SpriteMappingPiece(
                piece.xOffset() + x, piece.yOffset() + y, piece.widthTiles(), piece.heightTiles(),
                piece.tileIndex(), piece.hFlip(), piece.vFlip(), piece.paletteIndex(), piece.priority())).toList());
    }

    public static Result combine(SpriteArtSet normal, Map<SuperFormProfile.Tier, SuperFormProfile.Form> forms,
                                 PoweredAnimationSource source) {
        return combine(normal, forms, source, null);
    }

    public static Result combine(SpriteArtSet normal, Map<SuperFormProfile.Tier, SuperFormProfile.Form> forms,
                                 PoweredAnimationSource source, DonorCapabilities host) {
        var tiles = new ArrayList<>(Arrays.asList(normal.artTiles()));
        var mappings = new ArrayList<>(normal.mappingFrames());
        var dplcs = new ArrayList<>(normal.dplcFrames());
        int capacity = normal.bankSize();
        var views = new EnumMap<SuperFormProfile.Tier, SpriteArtSet>(SuperFormProfile.Tier.class);
        var shared = new IdentityHashMap<PoweredPlayableSheet, SpriteArtSet>();
        for (var entry : forms.entrySet()) {
            var bank = entry.getValue().poweredArt();
            if (bank == null) continue;
            if (shared.containsKey(bank)) { views.put(entry.getKey(), shared.get(bank)); continue; }
            var art = bank.art();
            if (!(art.animationProfile() instanceof PlayableSheetAnimationBinding.CanonicalProfile canonical))
                throw new IllegalArgumentException("Powered art requires a canonical GGFP sheet");
            int tileOffset = tiles.size(), frameOffset = mappings.size();
            tiles.addAll(Arrays.asList(art.artTiles()));
            mappings.addAll(art.mappingFrames());
            art.dplcFrames().forEach(frame -> dplcs.add(new SpriteDplcFrame(frame.requests().stream()
                    .map(run -> new TileLoadRequest(Math.addExact(run.startTile(), tileOffset), run.count(), run.destinationOffset())).toList())));
            capacity = Math.max(capacity, art.bankSize());
            var scripts = new SpriteAnimationSet();
            normal.animationSet().getAllScripts().forEach(scripts::addScript);
            var profile = ((ScriptedVelocityAnimationProfile) normal.animationProfile()).withRunSpeedThreshold(0x800);
            boolean walk = false, run = false;
            // A host without native powered scripts consumes authored canonical timing.
            // It requires only its own animation vocabulary, never another host's ROM.
            if (source == null && host != null) for (var authored : art.animationSet().getAllScripts().entrySet()) {
                var canonicalAnimation = CanonicalAnimation.values()[authored.getKey()];
                int id = host.resolveNativeId(canonicalAnimation);
                if (id < 0 || bank.normalAliases().contains(canonicalAnimation)) continue;
                var script = authored.getValue();
                int next = script.endAction() == SpriteAnimationEndAction.SWITCH
                        ? host.resolveNativeId(CanonicalAnimation.values()[script.endParam()]) : script.endParam();
                scripts.addScript(id, new SpriteAnimationScript(script.delay(), script.frames().stream()
                        .map(f -> f + frameOffset).toList(), script.endAction(), next));
                if (canonicalAnimation == CanonicalAnimation.WALK) profile.setWalkSlopeFrameStride(canonical.strides.getOrDefault(canonicalAnimation,-1));
                if (canonicalAnimation == CanonicalAnimation.RUN) profile.setRunSlopeFrameStride(canonical.strides.getOrDefault(canonicalAnimation,-1));
            }

            if (source != null) for (var family : new TreeMap<>(source.families()).entrySet()) {
                int id = family.getKey();
                if (family.getValue().animation() != null && bank.normalAliases().contains(family.getValue().animation())
                        && normal.animationSet().getScript(id) != null) continue;
                var poses = source.poses(id);
                if (poses.isEmpty() || !poses.values().stream().allMatch(key -> canonical.nativeFrames.containsKey(key) || normalPhase(normal, source, bank.normalPhaseAliases().get(key)) != null)) continue;
                int base = mappings.size();
                int last = Collections.max(poses.keySet());
                for (int index = 0; index <= last; index++) {
                    String key = poses.get(index);
                    Integer selected = key == null ? null : canonical.nativeFrames.get(key);
                    NormalPhase alias = key == null ? null : normalPhase(normal, source, bank.normalPhaseAliases().get(key));
                    mappings.add(selected != null ? art.mappingFrames().get(selected) : alias != null ? translate(normal.mappingFrames().get(alias.frame()), family.getValue().offsetX() - alias.offsetX(), family.getValue().offsetY() - alias.offsetY()) : new SpriteMappingFrame(List.of()));
                    dplcs.add(selected != null ? dplcs.get(selected + frameOffset) : alias != null ? normal.dplcFrames().get(alias.frame()) : new SpriteDplcFrame(List.of()));
                }
                var nativeScript = source.scripts().getScript(id);
                scripts.addScript(id, new SpriteAnimationScript(nativeScript.delay(),
                        nativeScript.frames().stream().map(f -> f + base).toList(), nativeScript.endAction(), nativeScript.endParam()));
                if (family.getValue().family() == CanonicalAnimation.WALK) {
                    walk = true;
                    profile.setWalkSlopeFrameStride(source.walkStride() / 2);
                    profile.setSuperAlternateFrameStep(source.alternateMask(), source.alternateStep(), base + source.alternateCeiling());
                }
                if (family.getValue().family() == CanonicalAnimation.RUN) {
                    run = true;
                    // S2's compact run selector uses offsets 0,1,2,3 rather than 0,2,4,6.
                    profile.setCompactSuperRunSlope(source.runStride() == 1);
                    profile.setRunSlopeFrameStride(source.runStride() == 1 ? 0 : source.runStride() / 2);
                }
            }
            // A missing movement family preserves all of that normal family's selectors.
            if (!walk) profile.setSuperAlternateFrameStep(0, 0, 0);
            if (!run) profile.setCompactSuperRunSlope(false);
            var view = new SpriteArtSet(art.artTiles(), List.of(), List.of(), normal.paletteIndex(), normal.basePatternIndex(),
                    normal.frameDelay(), capacity, profile, scripts);
            views.put(entry.getKey(), view); shared.put(bank, view);
        }
        var patterns = tiles.toArray(com.openggf.level.Pattern[]::new);
        var finalMappings = List.copyOf(mappings); var finalDplcs = List.copyOf(dplcs);
        int bankSize = capacity;
        var combined = new SpriteArtSet(patterns, finalMappings, finalDplcs, normal.paletteIndex(), normal.basePatternIndex(),
                normal.frameDelay(), bankSize, normal.animationProfile(), normal.animationSet());
        views.replaceAll((tier, view) -> new SpriteArtSet(patterns, finalMappings, finalDplcs, normal.paletteIndex(),
                normal.basePatternIndex(), normal.frameDelay(), bankSize, view.animationProfile(), view.animationSet()));
        forms.values().forEach(form -> form.animations().values().forEach(script -> {
            if (script.frames().stream().anyMatch(f -> f >= finalMappings.size()))
                throw new IllegalArgumentException("Form animation exceeds combined mapping bank");
        }));
        return new Result(combined, Map.copyOf(views));
    }
}
