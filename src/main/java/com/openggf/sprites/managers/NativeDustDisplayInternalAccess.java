package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-only completed fixed-Dust observation; no game/zone policy or second clock. */
public final class NativeDustDisplayInternalAccess {
    private NativeDustDisplayInternalAccess() { }
    public interface Source {
        boolean owns(AbstractPlayableSprite player);
        boolean active(AbstractPlayableSprite player);
        boolean holdsAnimation(AbstractPlayableSprite player);
        void chargeStarted(AbstractPlayableSprite player);
        void chargeReleased(AbstractPlayableSprite player);
        void completed(AbstractPlayableSprite player,int nextFrame,int timer,boolean active);
    }
    private static Source source(AbstractPlayableSprite player) {
        var level=player==null ? null : player.currentLevelManagerIfAvailable();
        var world=level==null ? null : level.getWorldSession();
        return world==null ? null : world.getGameModule().getGameService(Source.class);
    }
    static boolean owns(AbstractPlayableSprite player) {
        var source=source(player);return source!=null && source.owns(player);
    }
    static boolean active(AbstractPlayableSprite player,boolean ordinary) {
        var source=source(player);return source!=null && source.owns(player) ? source.active(player) : ordinary;
    }
    static boolean holdsAnimation(AbstractPlayableSprite player) {
        var source=source(player);return source!=null && source.owns(player) && source.holdsAnimation(player);
    }
    static void chargeStarted(AbstractPlayableSprite player) {
        var source=source(player);if(source!=null && source.owns(player))source.chargeStarted(player);
    }
    static void chargeReleased(AbstractPlayableSprite player) {
        var source=source(player);if(source!=null && source.owns(player))source.chargeReleased(player);
    }
    static void completed(AbstractPlayableSprite player,int nextFrame,int timer,boolean active) {
        var source=source(player);
        if(source!=null && source.owns(player))source.completed(player,nextFrame,timer,active);
    }
}
