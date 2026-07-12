package com.openggf.tools.modsdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Atomically publishes a completed same-filesystem file without a replace race. */
final class NoClobberPublisher {
    private NoClobberPublisher(){}
    static void publish(Path staging,Path output,Runnable beforePublish)throws IOException{
        Objects.requireNonNull(beforePublish).run();
        // Deliberately omit ATOMIC_MOVE and REPLACE_EXISTING: the provider must fail
        // with FileAlreadyExistsException if a peer wins the target name.
        Files.move(staging,output);
    }
}
