package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import java.util.function.Predicate;

/** Level-owned conversion between runtime ROM patterns and immutable graphics presentation values. */
public final class SpritePresentationRenderer {
    private static final SpritePresentation.HeadSatReplay HEAD_SAT_REPLAY = SpritePresentationRenderer::replayHeadSat;
    private SpritePresentationRenderer() { }

    /** Immutable adapter carried by native head producers across the graphics-owned SAT boundary. */
    public static SpritePresentation.HeadSatReplay headSatReplay() { return HEAD_SAT_REPLAY; }

    public static Frame prepare(GraphicsManager graphics, int cameraX, int cameraY, Runnable producer) {
        return prepare(graphics, cameraX, cameraY, producer, null);
    }

    /** Internal caller-owned reuse hint; composition and native publication retain their existing order. */
    public static Frame prepare(GraphicsManager graphics, int cameraX, int cameraY, Runnable producer, Frame previous) {
        return PlayerHeadPresentation.compose(SpritePresentation.prepare(graphics, cameraX, cameraY, producer, descriptor -> {
            PatternDesc desc = (PatternDesc) descriptor;
            return new Attributes(desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(), desc.getPriority());
        }, previous), cameraX, cameraY);
    }

    public static void bindPatternBank(GraphicsManager graphics, int base, Pattern[] patterns) {
        if (!SpritePresentation.isPreparing(graphics)) return;
        for (int index = 0; index < patterns.length; index++) {
            SpritePresentation.bindPatternVersion(graphics, base + index, version(patterns[index]));
        }
    }

    /** Publish only this mapping's slots: unused bank capacity may overlap another sprite's bank. */
    public static void bindPatternBank(GraphicsManager graphics, int base, Pattern[] patterns,
                                       SpriteMappingFrame frame) {
        if (!SpritePresentation.isPreparing(graphics)) return;
        for (SpriteMappingPiece piece : frame.pieces()) {
            int end = Math.min(patterns.length, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
            for (int index = Math.max(0, piece.tileIndex()); index < end; index++) {
                SpritePresentation.bindPatternVersion(graphics, base + index, version(patterns[index]));
            }
        }
    }

    public static PatternVersion version(Pattern pattern) {
        return new PatternVersion(packWord(pattern, 0), packWord(pattern, 16),
                packWord(pattern, 32), packWord(pattern, 48));
    }

    /** Exact mapping-owned slots, frozen before later draws can reuse their virtual addresses. */
    public static java.util.Map<Integer, PatternVersion> patternVersions(int base, Pattern[] patterns, SpriteMappingFrame frame) {
        var versions = new java.util.HashMap<Integer, PatternVersion>();
        for (var piece : frame.pieces()) {
            int end = Math.min(patterns.length, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
            for (int index = Math.max(0, piece.tileIndex()); index < end; index++) versions.put(base + index, version(patterns[index]));
        }
        return java.util.Map.copyOf(versions);
    }

    /** Live SAT path: native admission is already finished; freeze and compose its replay only. */
    public static void replayHeadSat(GraphicsManager graphics, java.util.List<com.openggf.graphics.SpriteSatEntry> entries,
                                     java.util.function.Consumer<com.openggf.graphics.SpriteSatEntry> emitter) {
        boolean priorityShader = graphics.isUseSpritePriorityShader();
        graphics.flushPatternBatch();
        // Stock live SAT replay uses the plain shader; priority remains in the admitted descriptors.
        graphics.setUseSpritePriorityShader(false);
        try {
        Frame frame = prepare(graphics, 0, 0, () -> {
            for (int bucket = com.openggf.graphics.RenderPriority.MAX; bucket >= com.openggf.graphics.RenderPriority.MIN; bucket--) {
                for (int i = entries.size() - 1; i >= 0; i--) {
                    var entry = entries.get(i); if (entry.priorityBucket() != bucket) continue;
                    var part=entry.presentationSubject().part();
                    SpritePresentation.layer(graphics, part == SpritePresentation.Part.WORLD
                            || part == SpritePresentation.Part.OBJECT_BODY ? Layer.OBJECT : Layer.PLAYER);
                    if (entry.presentationSubject().head() != null)
                        SpritePresentation.bindPatternVersions(graphics, entry.presentationSubject().head().patterns());
                    graphics.setCurrentSpriteHighPriority(entry.globalHighPriority());
                    SpritePresentation.withSubject(graphics, entry.presentationSubject(), () -> emitter.accept(entry));
                }
            }
        });
        graphics.setCurrentSpriteHighPriority(false);
        draw(graphics, frame, 0, 0, layer -> true);
        } finally {
            graphics.setCurrentSpriteHighPriority(false);
            graphics.setUseSpritePriorityShader(priorityShader);
        }
    }

    private static long packWord(Pattern pattern, int start) {
        long word = 0;
        for (int i = 0; i < 16; i++) {
            word |= (long) (pattern.getPixel((start + i) % 8, (start + i) / 8) & 15) << (i * 4);
        }
        return word;
    }

    public static Pattern pattern(PatternVersion version) {
        Pattern pattern = new Pattern();
        unpackPattern(version, pattern);
        return pattern;
    }

    private static void unpackPattern(PatternVersion version, Pattern pattern) {
        for (int group = 0; group < 4; group++) {
            long word = switch (group) {
                case 0 -> version.a();
                case 1 -> version.b();
                case 2 -> version.c();
                default -> version.d();
            };
            for (int i = 0; i < 16; i++) {
                pattern.setPixel(i % 8, group * 2 + i / 8, (byte) ((word >>> (i * 4)) & 15));
            }
        }
    }

    /** Resolve stable art addresses in the cache and restore the published generation of mutable virtual banks. */
    public static void draw(GraphicsManager graphics, Frame frame, int cameraX, int cameraY,
                            Predicate<Layer> visible) {
        draw(graphics,frame,cameraX,cameraY,visible,null);
    }

    /** Presentation state is sampled by the logical loop; draw consumes immutable values only. */
    public static void draw(GraphicsManager graphics, Frame frame, int cameraX, int cameraY,
                            Predicate<Layer> visible,com.openggf.game.mutators.LivePresentationRuntime runtime) {
        try {
            frame=runtime==null?com.openggf.level.objects.HudProfileAccess.projectPresentation(graphics,frame):
                    com.openggf.level.objects.HudProfileAccess.projectPresentation(runtime.world(),graphics,frame);
            if(runtime!=null&&!runtime.trails().isEmpty())drawTrails(graphics,frame,cameraX,cameraY,visible,runtime);
            drawPicture(graphics,frame,cameraX,cameraY,visible,runtime,1);
        } catch(RuntimeException|Error failure) {
            if(runtime!=null)runtime.releaseTrailArt();
            throw failure;
        }
    }

    private static void drawTrails(GraphicsManager graphics,Frame current,int cameraX,int cameraY,Predicate<Layer> visible,
                                    com.openggf.game.mutators.LivePresentationRuntime runtime) {
        var displayed=current.tiles().stream().filter(t->!t.subject().suppressed()&&t.subject().body()!=null
                &&(t.subject().part()==Part.BODY||t.subject().part()==Part.APPENDAGE))
                .map(t->new DisplayPart(t.subject().id(),t.subject().part())).collect(java.util.stream.Collectors.toSet());
        if(displayed.isEmpty())return;
        int bank=runtime.trailBank(graphics),poseIndex=0;
        var poses=runtime.trails();
        for(var pose:poses) {
            int poseSlot=poseIndex;
            var versions=new java.util.HashMap<Integer,PatternVersion>();
            var tiles=new java.util.ArrayList<Tile>();
            var ids=new java.util.HashMap<Integer,Integer>();
            for(var tile:pose.frame().tiles()) {
                if(!displayed.contains(new DisplayPart(tile.subject().id(),tile.subject().part())))continue;
                int id=ids.computeIfAbsent(tile.patternId(),source->bank+poseSlot*com.openggf.game.mutators.LivePresentationRuntime.MAX_TRAIL_TILES+ids.size());
                versions.put(id,pose.frame().patternVersions().get(tile.patternId()));
                tiles.add(new Tile(tile.layer(),id,tile.palette(),tile.hFlip(),tile.vFlip(),tile.priority(),
                        tile.x()+pose.cameraX()-cameraX,tile.y()+pose.cameraY()-cameraY,tile.width(),tile.height(),
                        tile.priorityShader(),tile.occlusionMask(),tile.ghost(),tile.ghostAlpha(),tile.rowStart(),tile.rowEnd(),tile.subject()));
            }
            // Every retained actor stays clearly readable; the newest echoes are nearly opaque.
            float opacity=.65f+.30f*poseIndex++/Math.max(1,poses.size()-1);
            drawPicture(graphics,new Frame(tiles,java.util.List.of(),versions),cameraX,cameraY,visible,runtime,opacity);
        }
    }
    private record DisplayPart(String id,Part part) { }
    private static void drawPicture(GraphicsManager graphics,Frame frame,int cameraX,int cameraY,Predicate<Layer> visible,
                                     com.openggf.game.mutators.LivePresentationRuntime runtime,float opacity) {
        boolean priority = graphics.isUseSpritePriorityShader();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean ghost = graphics.isGhostRenderEffectActive();
        float alpha = graphics.getGhostRenderAlpha();
        PatternDesc desc = new PatternDesc();
        graphics.beginPatternAtlasBatch();
        try {
            for (var entry : frame.patternVersions().entrySet())
                com.openggf.graphics.PresentationGraphicsAccess.restoreFrozenPattern(graphics, entry.getValue(), entry.getKey());
        } finally { graphics.endPatternAtlasBatch(); }
        var viewport = com.openggf.graphics.PresentationGraphicsAccess.viewport(graphics);
        Tile previous = null;
        boolean previousPictureGeometry = false;
        var policies = runtime == null ? java.util.List.<com.openggf.game.mutators.TransientMutatorPolicySource.Entry>of()
                : runtime.spritePolicies();
        java.util.Map<Body, com.openggf.game.mutators.SpritePictureTransform> transforms = policies.isEmpty()
                ? java.util.Map.of() : new java.util.HashMap<>();
        graphics.flushPatternBatch();
        try {
            int primitiveIndex = 0;
            for (int index = 0; index <= frame.tiles().size(); index++) {
                while (primitiveIndex < frame.primitives().size()
                        && frame.primitives().get(primitiveIndex).beforeTile() == index) {
                    var primitive = frame.primitives().get(primitiveIndex++);
                    if (visible.test(primitive.layer()) && !primitive.subject().suppressed()) {
                        graphics.flushPatternBatch();
                        graphics.enqueueDebugLineState();
                        graphics.registerCommand(primitive.primitive().command(graphics, cameraX, cameraY));
                        graphics.enqueueDefaultShaderState();
                        previous = null;
                    }
                }
                if (index == frame.tiles().size()) break;
                Tile tile = frame.tiles().get(index);
                if (!visible.test(tile.layer()) || tile.subject().suppressed()) continue;
                var body = tile.subject().body();
                var transform = policies.isEmpty() || body == null
                        ? com.openggf.game.mutators.SpritePictureTransform.IDENTITY
                        : transforms.computeIfAbsent(body, ignored ->
                            com.openggf.game.mutators.SpritePictureTransform.forTile(tile, cameraX, cameraY, policies));
                boolean pictureGeometry = !transform.identity() || opacity != 1;
                float[] quad = pictureGeometry ? transform.quad(tile, cameraX, cameraY) : null;
                if (viewport != null) {
                    boolean outside;
                    if (pictureGeometry) outside = viewport.outside(quad, cameraX, cameraY);
                    else if (tile.rowClipped()) {
                        // The native row path emits integer-positioned 8-pixel strips.
                        float left = (int) (tile.x() + cameraX) - cameraX;
                        float top = (int) (tile.y() + cameraY) - cameraY;
                        outside = viewport.outside(left, top + tile.rowStart(), left + 8, top + tile.rowEnd());
                    } else outside = viewport.outside(tile.x(), tile.y(), tile.x() + tile.width(), tile.y() + tile.height());
                    if (outside) continue;
                }
                if (previous == null || previousPictureGeometry != pictureGeometry || previous.priorityShader() != tile.priorityShader()
                        || (previous.occlusionMask() != tile.occlusionMask()
                            && !com.openggf.graphics.PresentationGraphicsAccess.maskIsPerInstance(graphics))
                        || previous.ghost() != tile.ghost() || previous.ghostAlpha() != tile.ghostAlpha()) {
                    graphics.flushPatternBatch();
                    graphics.setUseSpritePriorityShader(tile.priorityShader());
                    graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                    if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                    else graphics.endGhostRenderEffect();
                    graphics.beginPatternBatch();
                }
                previous = tile;
                previousPictureGeometry = pictureGeometry;
                graphics.setUseSpritePriorityShader(tile.priorityShader());
                graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                else graphics.endGhostRenderEffect();
                desc.setPaletteIndex(tile.palette());
                desc.setHFlip(tile.hFlip());
                desc.setVFlip(tile.vFlip());
                desc.setPriority(tile.priority());
                float x = tile.x() + cameraX, y = tile.y() + cameraY;
                SpritePresentation.withSubject(graphics,tile.subject(),()-> {
                if(pictureGeometry) {
                    com.openggf.graphics.PresentationGraphicsAccess.render(graphics, tile.patternId(), tile.palette(),
                            tile.hFlip(), tile.vFlip(), tile.priority(), tile.rowStart(), tile.rowEnd(),
                            quad,transform.hue(),opacity);
                } else if (tile.rowClipped()) {
                    SpritePresentation.renderTileRows(graphics, tile, desc, (int) x, (int) y);
                } else if (tile.width() == 8 && tile.height() == 8 && x == (int) x && y == (int) y) {
                    graphics.renderPatternWithId(tile.patternId(), desc, (int) x, (int) y);
                } else {
                    graphics.renderPatternWithIdScaled(tile.patternId(), desc, x, y, tile.width(), tile.height());
                }
                });
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.setUseSpritePriorityShader(priority);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            if (ghost) graphics.beginGhostRenderEffect(alpha); else graphics.endGhostRenderEffect();
        }
    }
}
