package com.openggf.level.objects;

import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.game.mutators.ObjectHeadScaleSource;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;

/** Render callback ownership is explicit; reusable ROM renderers never infer enemy types. */
final class ObjectHeadPresentation {
    private ObjectHeadPresentation() { }
    static void draw(ObjectInstance owner, ObjectServices services, Runnable nativeDraw) {
        if (services == null || owner.headPresentationRole() == ObjectHeadRole.NONE) { nativeDraw.run(); return; }
        var source=WorldSessionPolicyAccess.getService(services.worldSession(),ObjectHeadScaleSource.class);
        var player=services.spriteManager()==null?null:services.spriteManager().getMainPlayable();
        int percent=source==null?100:source.percent(owner.headPresentationRole(),player==null?0:player.getRingCount());
        if(percent==100) { nativeDraw.run(); return; }
        GraphicsManager graphics=services.graphicsManager();
        var subject=new SpritePresentation.Subject(owner.getName()+":"+ObjectRenderParts.slotIndex(owner),
                SpritePresentation.Part.OBJECT_BODY,false,percent);
        Runnable draw=()->SpritePresentation.withSubject(graphics,subject,nativeDraw);
        if(SpritePresentation.isPreparing(graphics)||graphics.isSpriteSatCollectionActive()) draw.run();
        else SpritePresentationRenderer.draw(graphics,SpritePresentationRenderer.prepare(graphics,0,0,draw),0,0,layer->true);
    }
}
