package com.openggf.level.objects;

import com.openggf.graphics.*;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.*;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.*;

import static com.openggf.level.objects.StageplayObjectDirection.*;

/** CPU-only capture of initialized native art and pure picture animation. */
final class StageplayObjectCapture {
    private final StageplayObjectDirection owner;
    private final ObjectManager objects;
    final ObjectInstance root;
    final GraphicsManager graphics;
    final int anchorX, anchorY;
    final List<VisualPart> parts = new ArrayList<>();
    final List<PatternPixels> patterns = new ArrayList<>();
    final Map<PatternPixels, Integer> patternIds = new LinkedHashMap<>();
    final List<PaletteColors> palettes = new ArrayList<>();
    final Map<PaletteColors, Integer> paletteIds = new LinkedHashMap<>();
    final List<String> limitations = new ArrayList<>();

    StageplayObjectCapture(StageplayObjectDirection owner, ObjectInstance root, GraphicsManager graphics) {
        this.owner = owner;
        this.objects = owner.objects;
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.root = root;
        anchorX = root.getX();
        anchorY = root.getY();
    }

    VisualTemplate capture() {
        Map<ObjectInstance, String> paths = owner.structuralPaths(root);
        List<ObjectInstance> members = owner.graph(root);
        Set<ObjectInstance> inline = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ObjectInstance member : members) {
            if (member instanceof AbstractBossInstance boss) {
                inline.addAll(boss.getChildComponents());
            }
        }
        List<ObjectRenderParts.RenderEntry>[] unified =
                new List[RenderPriority.MAX - RenderPriority.MIN + 1];
        List<ObjectRenderParts.RenderEntry>[] low = new List[unified.length],
                high = new List[unified.length];
        for (int i = 0; i < unified.length; i++) {
            unified[i] = new ArrayList<>();
            low[i] = new ArrayList<>();
            high[i] = new ArrayList<>();
        }
        for (ObjectInstance member : members) {
            if (inline.contains(member) && objects.objectQuery().identityOf(member).isEmpty()) {
                continue;
            }
            ObjectRenderParts.addToRenderBuckets(member, unified, low, high);
        }
        for (int b = 0; b < unified.length; b++) {
            unified[b].sort(ObjectRenderParts.SLOT_DESCENDING);
            for (var entry : unified[b]) {
                captureEntry(paths.get(entry.instance()), entry, b + RenderPriority.MIN);
            }
        }
        limitations.add(
                "Native PLC, palette and child-graph state is held at capture; mapping poses"
                        + " use that frozen ROM art bank");
        return new VisualTemplate(
                anchorX,
                anchorY,
                parts,
                patterns,
                palettes,
                limitations.stream().distinct().toList());
    }

    void captureEntry(String path, ObjectRenderParts.RenderEntry entry, int bucket) {
        List<ObjectSpriteCapture.Draw> draws = new ArrayList<>();
        List<GLCommand> commands = new ArrayList<>();
        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        SpritePresentation.Frame residual;
        try {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(
                    ObjectRenderParts.tileOcclusionPaletteMask(entry));
            residual =
                    SpritePresentationRenderer.prepare(
                            graphics,
                            anchorX,
                            anchorY,
                            () ->
                                    ObjectSpriteCapture.observe(
                                            draws::add,
                                            detail -> {
                                                throw new UnsupportedRenderOrderException(
                                                        root.getName(), detail);
                                            },
                                            () -> {
                                                ObjectRenderParts.appendRenderCommands(
                                                        entry, commands, bucket);
                                                for (GLCommand command : commands) {
                                                    graphics.registerCommand(command);
                                                }
                                            }));
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
        }
        if (!residual.primitives().isEmpty()) {
            throw new UnsupportedRenderOrderException(root.getName());
        }
        String prefix = path + "/bucket" + bucket + (entry.high() ? "h" : "l");
        if (!residual.tiles().isEmpty() || !residual.primitives().isEmpty()) {
            parts.add(
                    new VisualPart(
                            prefix + "/native",
                            bucket,
                            entry.high(),
                            entry.slot(),
                            -1,
                            List.of(convert(-1, residual))));
            limitations.add(
                    prefix
                            + ": custom draw/primitive state is held; native routines remain"
                            + " suspended");
        }
        int ordinal = 0;
        for (var draw : draws) {
            if (draw.sheet().getFrameCount() > MAX_POSES) {
                throw new IllegalStateException(
                        "Native mapping frame count exceeds visual template budget");
            }
            int defaultPose = draw.defaultPose();
            List<VisualPose> poses = new ArrayList<>();
            if (defaultPose < 0) {
                poses.add(convert(-1, prepare(draw, -1)));
            }
            for (int pose = 0; pose < draw.sheet().getFrameCount(); pose++) {
                poses.add(convert(pose, prepare(draw, pose)));
            }
            List<VisualAnimation> animations = animations(draw, entry.instance());
            if (animations.isEmpty()) {
                limitations.add(
                        prefix
                                + ": routine-owned animation is held; author a mapping pose per"
                                + " part");
            }
            if (poses.stream()
                    .anyMatch(p -> !p.tiles().isEmpty() || !p.primitives().isEmpty())) {
                parts.add(
                        new VisualPart(
                                prefix + "/draw" + ordinal,
                                bucket,
                                entry.high(),
                                entry.slot(),
                                defaultPose,
                                poses,
                                animations));
            }
            ordinal++;
        }
    }

    SpritePresentation.Frame prepare(ObjectSpriteCapture.Draw draw, int pose) {
        return SpritePresentationRenderer.prepare(
                graphics,
                anchorX,
                anchorY,
                () -> {
                    bindMappedPatterns(draw, pose);
                    int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
                    try {
                        graphics.setCurrentSpriteTileOcclusionPaletteMask(draw.occlusionMask());
                        SpritePresentation.withSubject(
                                graphics, draw.subject(), () -> draw.drawPose(pose));
                    } finally {
                        graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
                    }
                });
    }

    private void bindMappedPatterns(ObjectSpriteCapture.Draw draw, int pose) {
        var pieces = pose < 0 ? draw.pieces() : draw.sheet().getFrame(pose).pieces();
        var bank = draw.sheet().getPatterns();
        for (var piece : pieces) {
            int first = piece.tileIndex();
            long end = first + (long) piece.widthTiles() * piece.heightTiles();
            if (bank == null || first < 0 || end > bank.length) {
                throw new IllegalStateException(
                        "Native mapping pose "
                                + pose
                                + " references ROM patterns outside its initialized art bank");
            }
            for (int index = first; index < end; index++) {
                if (bank[index] == null) {
                    throw new IllegalStateException(
                            "Native mapping pose "
                                    + pose
                                    + " references unloaded ROM pattern "
                                    + index);
                }
                SpritePresentation.bindPatternVersion(
                        graphics,
                        draw.renderer().getPatternBase() + index,
                        SpritePresentationRenderer.version(bank[index]));
            }
        }
    }

    List<VisualAnimation> animations(ObjectSpriteCapture.Draw draw, ObjectInstance member) {
        if (member instanceof ObjectVisualAnimationSource source) {
            var cycle = source.visualAnimationCycle(draw.defaultPose());
            if (cycle.isPresent()) {
                var value = cycle.get();
                if (value.mappingFrames().stream().anyMatch(frame -> frame < 0 || frame >= draw.sheet().getFrameCount()))
                    throw new IllegalArgumentException("Native visual cycle references an absent mapping frame");
                return List.of(new VisualAnimation(0, value.delay(), value.mappingFrames(), "LOOP", 0));
            }
        }
        ObjectRenderManager render = objects.services().renderManager();
        if (render == null) {
            return List.of();
        }
        List<VisualAnimation> result = new ArrayList<>();
        for (String key : render.getRendererKeys()) {
            if (render.getRenderer(key) == draw.renderer()) {
                SpriteAnimationSet set = render.getAnimations(key);
                if (set == null) {
                    set = render.getAnimations("anim_" + key);
                }
                if (set == null) {
                    continue;
                }
                result.addAll(pureAnimations(set, draw.sheet().getFrameCount()));
            }
        }
        return result;
    }

    VisualPose convert(int pose, SpritePresentation.Frame frame) {
        List<VisualTile> tiles = new ArrayList<>();
        for (var tile : frame.tiles()) {
            var pixels = frame.patternVersions().get(tile.patternId());
            if (pixels == null) {
                pixels = SpritePresentation.patternSample(graphics, tile.patternId());
            }
            if (pixels == null) {
                throw new IllegalStateException(
                        "Native object pattern " + tile.patternId() + " is not initialized");
            }
            PatternPixels pattern =
                    new PatternPixels(pixels.a(), pixels.b(), pixels.c(), pixels.d());
            int patternIndex =
                    patternIds.computeIfAbsent(
                            pattern,
                            p -> {
                                patterns.add(p);
                                return patterns.size() - 1;
                            });
            // Trusted native producers may project a logical CRAM line into their own
            // scoped bank. Resolve it while that exact source world/owner is alive; only
            // the resulting raw ROM colors, never the resolver or lease, enter the template.
            var projection = tile.subject().paletteReplay();
            int nativePalette =
                    projection == null
                            ? tile.palette()
                            : projection.palette(graphics, tile.palette());
            if (nativePalette < 0) {
                throw new IllegalStateException(
                        "Native scoped object palette is not ready for visual capture");
            }
            PaletteColors palette =
                    new PaletteColors(
                            ObjectVisualGraphicsAccess.palette(graphics, nativePalette));
            int paletteIndex =
                    paletteIds.computeIfAbsent(
                            palette,
                            p -> {
                                palettes.add(p);
                                return palettes.size() - 1;
                            });
            tiles.add(
                    new VisualTile(
                            patternIndex,
                            paletteIndex,
                            tile.hFlip(),
                            tile.vFlip(),
                            tile.priority(),
                            tile.x(),
                            tile.y(),
                            tile.width(),
                            tile.height(),
                            tile.priorityShader(),
                            tile.occlusionMask(),
                            tile.rowStart(),
                            tile.rowEnd()));
        }
        List<VisualPrimitive> primitives = new ArrayList<>();
        for (var primitive : frame.primitives()) {
            var geometry = SpritePresentation.geometry(primitive);
            primitives.add(
                    new VisualPrimitive(
                            primitive.beforeTile(),
                            geometry.kind(),
                            geometry.method(),
                            geometry.vertices()));
        }
        return new VisualPose(pose, tiles, primitives);
    }


    /**
     * Parsed ROM terminators, rather than the native decoder's compatibility enum, admit pure
     * controls.
     */
    static List<VisualAnimation> pureAnimations(SpriteAnimationSet source, int frameCount) {
        Map<Integer, SpriteAnimationScript> admitted = new TreeMap<>();
        for (var entry : source.getAllScripts().entrySet()) {
            SpriteAnimationScript script = entry.getValue();
            // Signed duration admission is conservative across native object interpreters:
            // Anim_Run (s2.asm:30431-30444) branches on the decremented byte's sign.
            if (script.trailingBytes().isEmpty()
                    || script.frames().isEmpty()
                    || script.delay() < 0
                    || script.delay() > 127
                    || script.frames().stream()
                            .anyMatch(frame -> frame < 0 || frame >= Math.min(128, frameCount))) {
                continue;
            }
            int terminator = script.trailingBytes().getFirst();
            // Only FF/FE/FD are picture controls. FC advances the object routine:
            // Anim_End_FF (s2.asm:30457-30491), loc_1AC38 (sonic3k.asm:36229-36263).
            boolean pure =
                    switch (terminator) {
                        case 0xff -> script.endAction() == SpriteAnimationEndAction.LOOP;
                        case 0xfe ->
                                script.endAction() == SpriteAnimationEndAction.LOOP_BACK
                                        && script.endParam() > 0
                                        && script.endParam() <= script.frames().size()
                                        && script.trailingBytes().size() > 1
                                        && script.trailingBytes().get(1) == script.endParam();
                        case 0xfd ->
                                script.endAction() == SpriteAnimationEndAction.SWITCH
                                        && script.trailingBytes().size() > 1
                                        && script.trailingBytes().get(1) == script.endParam();
                        default -> false;
                    };
            if (pure) {
                admitted.put(entry.getKey(), script);
            }
        }
        boolean changed;
        do {
            changed =
                    admitted.values()
                            .removeIf(
                                    script ->
                                            script.endAction() == SpriteAnimationEndAction.SWITCH
                                                    && !admitted.containsKey(script.endParam()));
        } while (changed);
        List<VisualAnimation> result = new ArrayList<>();
        for (var entry : admitted.entrySet()) {
            SpriteAnimationScript script = entry.getValue();
            result.add(
                    new VisualAnimation(
                            entry.getKey(),
                            script.delay(),
                            script.frames(),
                            script.endAction().name(),
                            script.endParam()));
        }
        return List.copyOf(result);
    }

}
