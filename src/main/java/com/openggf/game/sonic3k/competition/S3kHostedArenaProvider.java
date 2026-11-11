package com.openggf.game.sonic3k.competition;

import com.openggf.game.arena.HostedArenaProvider;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.arena.HostedArenaSession;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.level.LevelArtInternalAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Composition of a hosted world with scratch ROM defaults and no save callback. */
public final class S3kHostedArenaProvider implements HostedArenaProvider {
    private final Function<NativeMenuHostContext, S3kCompetitionNativeHost.Views> views;

    public S3kHostedArenaProvider() { this(context -> new S3kSoloArenaViews(context.engine().graphics())); }

    public S3kHostedArenaProvider(Function<NativeMenuHostContext, S3kCompetitionNativeHost.Views> views) {
        this.views = Objects.requireNonNull(views);
    }

    @Override public List<String> characters() { return List.of("sonic", "tails", "knuckles"); }

    @Override public HostedArenaSession open(HostedArenaRequest request, NativeMenuHostContext context) {
        if (request.arenaIndex() >= S3kCompetitionSession.GRAND_PRIX_ORDER.size()
                || !characters().contains(request.character()))
            throw new IllegalArgumentException("Unsupported arena or character");
        var view = Objects.requireNonNull(views.apply(context));
        try {
            var rom = context.engine().roms().getRom();
            var resources = S3kCompetitionResources.identify(rom);
            var records = new S3kCompetitionRecords(rom.readBytes(resources.defaultRecords(),
                    S3kCompetitionRecords.BYTE_COUNT));
            var host = new S3kCompetitionNativeHost(context.engine(), context.rootModule(), context.input(),
                    context.publishMode(), records, ignored -> { }, view, LevelArtInternalAccess::initializeObjectArt);
            try {
                return new S3kHostedArenaSession(host, request, () -> closeView(view));
            } catch (RuntimeException | Error failure) {
                host.closeArena();
                throw failure;
            }
        } catch (IOException failure) {
            closeView(view);
            throw new UncheckedIOException("Cannot open hosted native arena", failure);
        } catch (RuntimeException | Error failure) {
            closeView(view);
            throw failure;
        }
    }

    private static void closeView(S3kCompetitionNativeHost.Views view) {
        if (view instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("Cannot close arena view", failure); }
        } else view.closeArena();
    }
}
