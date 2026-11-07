package com.openggf.game.sonic3k.competition;

import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.NativeCompetitionRenderer;
import java.util.List;

/** Transient display projection. Native split cameras and sprite visibility retain their owners. */
public final class S3kSoloArenaViews implements S3kCompetitionNativeHost.Views, AutoCloseable {
    private final S3kCompetitionViews nativeViews;
    private final NativeCompetitionRenderer renderer = new NativeCompetitionRenderer();
    private final GraphicsManager graphics;

    public S3kSoloArenaViews(GraphicsManager graphics) {
        this.graphics = graphics;
        nativeViews = new S3kCompetitionViews(graphics);
    }
    @Override public void prepareNativeSpritePages(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) {
        nativeViews.prepareNativeSpritePages(context,roster,scroll);
    }
    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context) {
        nativeViews.publishNativeSpritePages(phase,context);
    }
    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context,S3kCompetitionSpritePages.Pair pair) {
        nativeViews.publishNativeSpritePages(phase,context,pair);
    }
    static int displayY(S3kCompetitionScroll scroll) {
        int y = scroll.view(0).camera().y()-56;
        if (scroll.arena() == S3kCompetitionSession.Arena.CHROME_GADGET) return y & scroll.profile().yWrapMask();
        var bounds = scroll.profile().bounds();
        return Math.clamp(y,bounds.minimumY(),Math.max(bounds.minimumY(),bounds.maximumY()-112));
    }
    @Override public void drawArena(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) {
        var host = (S3kCompetitionNativeHost) roster;
        int y = displayY(scroll);
        var page = host.spritePages().projectSolo(context.getObjectManager(),host.players(),scroll,y);
        var sprites = S3kCompetitionViews.prepareFrame(graphics,host.artProvider(),page,0,224,bucket -> {
            context.getGhostRenderRegistry().renderForLayer(bucket,false);
            context.getGhostRenderRegistry().renderForLayer(bucket,true);
        });
        var view = scroll.view(0);
        int backgroundY = view.backgroundVScroll()+y-view.camera().y();
        int word = nativeViews.capture().backdropWord();
        int rgb = DisplayColorConverter.toPackedRgb(((word>>>1)&7)*255/7,((word>>>5)&7)*255/7,
                ((word>>>9)&7)*255/7,graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());
        renderer.draw(context,List.of(new NativeCompetitionRenderer.View(roster.camera(0).capture(),0,224,
                y,backgroundY,0,host.planes().widthTiles()*8,scroll.projectSoloHorizontalScroll(y),sprites)),rgb);
    }
    @Override public void drawMenu(S3kCompetitionMenuScene.Scene scene) { nativeViews.drawMenu(scene); }
    @Override public void closeArena() { nativeViews.closeArena(); renderer.close(); }
    @Override public void setClearColor() { nativeViews.setClearColor(); }
    @Override public void close() { nativeViews.close(); renderer.close(); }
}
