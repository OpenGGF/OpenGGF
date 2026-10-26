package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.rewind.NativeRewindAdapterPublication;
import com.openggf.game.session.WorldRewindAdapterAccess;
import com.openggf.game.session.WorldSession;
import com.openggf.util.EngineCallerAccess;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;

/**
 * Actual fresh-Level clear dispatch only. No SAT prefix, fixed-player boot,
 * position, controller, successful-load or current-physical-RAM authority.
 * Scopes and invocations are ephemeral and are never snapshot state.
 */
public final class NativePhysicalLevelLoad {
    private NativePhysicalLevelLoad() { }
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final Class<?> ATTEMPT = attemptClass();

    private static Class<?> attemptClass() {
        try {
            return Class.forName("com.openggf.level.LevelLoadAttempt", false,
                    NativePhysicalLevelLoad.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new ExceptionInInitializerError(missing);
        }
    }

    public interface Writer { void clearFresh(Clear invocation); }

    private static void requireAttempt(MethodHandles.Lookup lookup) {
        Class<?> caller = EngineCallerAccess.callerOutside(NativePhysicalLevelLoad.class);
        if (lookup == null || !lookup.hasFullPrivilegeAccess()
                || lookup.lookupClass() != caller || caller != ATTEMPT)
            throw new SecurityException("Only the actual load attempt owns its source interval");
    }

    public static Scope begin(MethodHandles.Lookup lookup, WorldSession world,
            LevelLoadContext context, LevelLoadMode mode) {
        requireAttempt(lookup);
        Scope previous = ACTIVE.get();
        // A nested attempt in ANY world permanently retires suspended authority.
        for (Scope outer = previous; outer != null; outer = outer.previous) outer.revoked = true;
        Scope scope = new Scope(previous, world, context,
                mode == LevelLoadMode.FULL && context.isIncludePostLoadAssembly()
                        && context.getAssemblyKind() == com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        ACTIVE.set(scope);
        return scope;
    }

    public static Step step(MethodHandles.Lookup lookup, InitStep step) {
        requireAttempt(lookup);
        Scope scope = ACTIVE.get();
        if (scope == null || scope.closed || scope.thread != Thread.currentThread()
                || scope.step != null || step == null)
            throw new IllegalStateException("No actual selected load step");
        scope.step = step;
        scope.used = false;
        return new Step(scope, step);
    }

    public static void clearFresh(MethodHandles.Lookup lookup, Runnable action,
            LevelInitProfile profile, LevelLoadContext context) {
        Scope scope = ACTIVE.get();
        InitStep selectedStep = scope == null ? null : scope.step;
        Class<?> caller = EngineCallerAccess.callerOutside(NativePhysicalLevelLoad.class);
        if (scope == null || scope.closed || scope.revoked || !scope.eligible
                || scope.world == null || scope.thread != Thread.currentThread()
                || scope.context != context || !context.permitsInitialProcessSpritesRequest()
                || lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || caller.getNestHost() != profile.getClass() || !Modifier.isPrivate(caller.getModifiers())
                || action == null || action.getClass() != caller
                || scope.step == null || scope.step.action() != action || scope.used || scope.cleared)
            throw new SecurityException("No actual selected private fresh-Level clear action");
        GameModule root = NativeRewindAdapterPublication.nativeRoot(scope.world.rootGameModule());
        if (root == null || root.getLevelInitProfile() != profile)
            throw new SecurityException("Fresh clear profile is not the actual native root");
        Writer writer = root.getGameService(Writer.class);
        var target = WorldRewindAdapterAccess.data(MethodHandles.lookup(), scope.world);
        if (writer == null || target == null)
            throw new IllegalStateException("Fresh clear has no existing world DATA owner/writer");
        // Profile/eligibility/service predicates above may reenter the actual load loop.
        // Revalidate only stored authority immediately before consuming this dispatch.
        if (ACTIVE.get() != scope || scope.closed || scope.revoked
                || scope.thread != Thread.currentThread() || scope.step != selectedStep
                || scope.used || scope.cleared)
            throw new SecurityException("No actual selected private fresh-Level clear action");
        scope.used = true; scope.cleared = true; // Consume before dispatch, including a throwing handler.
        Clear clear = new Clear(scope, root, writer, target);
        try {
            writer.clearFresh(clear);
            if (!clear.consumed || !clear.bodyReturned || !clear.handlerReturned)
                throw new IllegalStateException("Original clear body/handler did not complete");
        } finally {
            clear.closed = true;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final WorldSession world;
        private final LevelLoadContext context;
        private final boolean eligible;
        private final Thread thread = Thread.currentThread();
        private InitStep step;
        private boolean used, cleared, revoked, closed;
        private Scope(Scope previous, WorldSession world, LevelLoadContext context, boolean eligible) {
            this.previous = previous; this.world = world; this.context = context; this.eligible = eligible;
        }
        @Override public void close() {
            if (closed) return;
            if (ACTIVE.get() != this || thread != Thread.currentThread())
                throw new IllegalStateException("Foreign load scope close");
            closed = true;
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static final class Step implements AutoCloseable {
        private final Scope scope;
        private final InitStep step;
        private boolean closed;
        private Step(Scope scope, InitStep step) { this.scope = scope; this.step = step; }
        @Override public void close() {
            if (closed) return;
            if (ACTIVE.get() != scope || scope.step != step || scope.thread != Thread.currentThread())
                throw new IllegalStateException("Foreign load step close");
            scope.step = null;
            closed = true;
        }
    }

    public static final class Clear {
        private final Scope scope;
        private final GameModule root;
        private final Writer writer;
        private final NativePhysicalWorldData target;
        private final InitStep step;
        private boolean consumed, bodyReturned, handlerReturned, closed;
        private Clear(Scope scope, GameModule root, Writer writer, NativePhysicalWorldData target) {
            this.scope = scope; this.root = root; this.writer = writer; this.target = target; step = scope.step;
        }
        private void requireCurrent() {
            if (closed || ACTIVE.get() != scope || scope.closed || scope.revoked
                    || scope.thread != Thread.currentThread() || scope.step != step || !scope.used
                    || !scope.context.permitsInitialProcessSpritesRequest()
                    || NativeRewindAdapterPublication.nativeRoot(scope.world.rootGameModule()) != root
                    || WorldRewindAdapterAccess.data(MethodHandles.lookup(), scope.world) != target)
                throw new IllegalStateException("Stale or foreign clear invocation");
            // The virtual eligibility/root queries can nest a load and revoke us
            // after the first check. No callback-bearing read follows this pure
            // revalidation on the path to consume's one-use state change.
            if (closed || ACTIVE.get() != scope || scope.closed || scope.revoked
                    || scope.thread != Thread.currentThread() || scope.step != step || !scope.used)
                throw new IllegalStateException("Stale or foreign clear invocation");
        }
        private void requireWriter(MethodHandles.Lookup lookup, Writer actual) {
            Class<?> caller = EngineCallerAccess.callerOutside(Clear.class);
            if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                    || caller != writer.getClass() || actual != writer
                    || root.getGameService(Writer.class) != writer)
                throw new SecurityException("Only the exact selected native writer may dispatch");
            requireCurrent();
        }
        public NativePhysicalWorldData target(MethodHandles.Lookup lookup, Writer actual) {
            requireWriter(lookup, actual);
            if (consumed) throw new IllegalStateException("Clear already consumed");
            return target;
        }
        public void consume(MethodHandles.Lookup lookup, Object actualOwner) {
            Class<?> caller = EngineCallerAccess.callerOutside(Clear.class);
            requireCurrent();
            if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                    || caller != target.getClass() || actualOwner != target || consumed)
                throw new SecurityException("Only the exact world DATA body consumes its clear");
            consumed = true;
        }
        public void bodyReturned(MethodHandles.Lookup lookup, Object actualOwner) {
            Class<?> caller = EngineCallerAccess.callerOutside(Clear.class);
            requireCurrent();
            if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                    || caller != target.getClass() || actualOwner != target || !consumed || bodyReturned)
                throw new SecurityException("No original world clear body completion");
            bodyReturned = true;
        }
        public void handlerReturned(MethodHandles.Lookup lookup, Writer actual) {
            requireWriter(lookup, actual);
            if (!bodyReturned || handlerReturned) throw new IllegalStateException("No original handler completion");
            handlerReturned = true;
        }
    }
}
