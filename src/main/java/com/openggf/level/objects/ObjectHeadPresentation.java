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
        if (services == null) { nativeDraw.run(); return; }
        var source=WorldSessionPolicyAccess.getService(services.worldSession(),ObjectHeadScaleSource.class);
        var player=services.spriteManager()==null?null:services.spriteManager().getMainPlayable();
        int percent=source==null||owner.headPresentationRole()==ObjectHeadRole.NONE?100:source.percent(owner.headPresentationRole(),player==null?0:player.getRingCount());
        var metadata=owner.presentationMetadata();
        var role=switch(metadata.role()) {
            case NONE -> SpritePresentation.Role.NONE;
            case BADNIK -> SpritePresentation.Role.BADNIK;
            case PICKUP -> SpritePresentation.Role.PICKUP;
        };
        var runtime=com.openggf.game.mutators.LivePresentationRuntime.from(services.worldSession());
        boolean body=runtime!=null&&runtime.tags(role);
        if(percent==100&&!body) { nativeDraw.run(); return; }
        GraphicsManager graphics=services.graphicsManager();
        var subject=new SpritePresentation.Subject(owner.getName()+":"+ObjectRenderParts.slotIndex(owner),
                SpritePresentation.Part.OBJECT_BODY,false,percent,null,
                body?new SpritePresentation.Body(role,metadata.anchorX(),metadata.anchorY()):null);
        Runnable draw=()->SpritePresentation.withSubject(graphics,subject,nativeDraw);
        if(SpritePresentation.isPreparing(graphics)||graphics.isSpriteSatCollectionActive()) draw.run();
        else SpritePresentationRenderer.draw(graphics,SpritePresentationRenderer.prepare(graphics,0,0,draw),0,0,layer->true);
    }
}
