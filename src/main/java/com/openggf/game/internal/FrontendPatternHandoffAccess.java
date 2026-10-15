package com.openggf.game.internal;

import com.openggf.game.GameDataSource;
import com.openggf.game.session.GameplayModeContext;
import java.util.Objects;

/** Carries only immutable admitted art over the menu's exact reused source/world replacement. */
public final class FrontendPatternHandoffAccess {
    private FrontendPatternHandoffAccess() { }
    /** Capture-only token: source and supported owner identity cannot be supplied by a caller. */
    public static final class Ticket {
        private final GameDataSource source;
        private final Class<?> ownerType;
        private final String format;
        private final byte[] bytes;
        private Ticket(GameDataSource source, FrontendPatternHandoffOwner owner, byte[] bytes) {
            this.source = Objects.requireNonNull(source);
            this.ownerType = owner.getClass();
            this.format = Objects.requireNonNull(owner.frontendPatternFormat());
            this.bytes = bytes.clone();
        }
        public GameDataSource source() { return source; }
        public String format() { return format; }
        public byte[] bytes() { return bytes.clone(); }
    }
    public static Ticket capture(GameplayModeContext context) {
        if(context==null||!(context.runtimeArtCoordinator() instanceof FrontendPatternHandoffOwner owner))return null;
        byte[] bytes=owner.captureFrontendPatterns();
        return bytes==null?null:new Ticket(context.getWorldSession().getDataSource(),owner,bytes);
    }
    public static void transfer(Ticket ticket,GameplayModeContext destination) {
        if(ticket==null)return;
        if(destination.getWorldSession().getDataSource()!=ticket.source())
            throw new IllegalStateException("Frontend pattern handoff source changed");
        if(!(destination.runtimeArtCoordinator() instanceof FrontendPatternHandoffOwner owner)
                ||owner.getClass()!=ticket.ownerType
                ||!owner.frontendPatternFormat().equals(ticket.format()))
            throw new IllegalStateException("Frontend pattern handoff owner is unsupported");
        owner.acceptFrontendPatterns(ticket.bytes());
    }
    public static void installed(GameplayModeContext destination) {
        if(destination.runtimeArtCoordinator() instanceof FrontendPatternHandoffOwner owner)owner.retireFrontendPatterns();
    }
}
