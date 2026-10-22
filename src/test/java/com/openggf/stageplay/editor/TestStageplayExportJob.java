package com.openggf.stageplay.editor;

import com.openggf.data.RomManager;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real owned subprocesses prove cleanup even when the child never runs a finally block. */
@Timeout(20)
class TestStageplayExportJob {
    @TempDir Path directory;
    private final StageplayDocument scene=StageplayDocument.empty("s2",0,0,0,0,320,224);
    private final RomManager roms=mock(RomManager.class);

    @Test void cancellationTerminatesRealChildAndRemovesOnlyItsSnapshotAndPartialMedia() throws Exception {
        Path source=source(), output=directory.resolve("film.mkv"), keep=Files.writeString(directory.resolve("keep.txt"),"user data");
        var launched=new AtomicReference<Process>();var command=new AtomicReference<List<String>>();
        try(var job=new StageplayExportJob(args->{command.set(args);var child=launch(args,"wait");launched.set(child);return child;})) {
            job.start(scene,source,output,roms);
            Path snapshot=argument(command.get(),"--scene"), staging=argument(command.get(),"--staging-dir");
            await(()->Files.exists(staging.resolve("ready")));
            assertTrue(launched.get().isAlive());assertEquals(scene,StageplayStore.read(snapshot));
            assertTrue(Files.exists(staging.resolve("partial.mkv")));assertNull(job.poll());
            job.close();
            assertFalse(launched.get().isAlive());assertFalse(Files.exists(snapshot));assertFalse(Files.exists(staging));
            assertFalse(Files.exists(output));assertEquals(scene,StageplayStore.read(source));assertEquals("user data",Files.readString(keep));
            assertNull(job.poll());
        } finally { terminate(launched.get()); }
    }

    @Test void launchFailureRemovesBothOwnedPathsAndRetainsOriginalError() throws Exception {
        var command=new AtomicReference<List<String>>();var expected=new IOException("test launch failed");
        try(var job=new StageplayExportJob(args->{command.set(args);throw expected;})) {
            assertSame(expected,assertThrows(IOException.class,()->job.start(scene,source(),directory.resolve("failed.mkv"),roms)));
            assertFalse(Files.exists(argument(command.get(),"--scene")));
            assertFalse(Files.exists(argument(command.get(),"--staging-dir")));
            assertNull(job.poll());
        }
    }

    @Test void completedFailureAndSuccessCleanStagingAndPermitAnotherExport() throws Exception {
        Path source=source(),output=directory.resolve("film.mkv");
        var command=new AtomicReference<List<String>>();var launched=new AtomicReference<Process>();
        var mode=new AtomicReference<>("fail");
        try(var job=new StageplayExportJob(args->{command.set(args);var child=launch(args,mode.get());launched.set(child);return child;})) {
            job.start(scene,source,output,roms);
            String failure=awaitPoll(job);
            assertTrue(failure.contains("exit 7"),failure);assertTrue(failure.contains("owned child failure"),failure);
            assertOwnedFilesRemoved(command.get());assertFalse(Files.exists(output));
            mode.set("success");job.start(scene,source,output,roms);
            assertTrue(awaitPoll(job).startsWith("Exported film:"));assertOwnedFilesRemoved(command.get());
            assertEquals("completed film",Files.readString(output));assertEquals(scene,StageplayStore.read(source));
        } finally { terminate(launched.get()); }
    }

    private Path source() throws IOException {
        Path file=directory.resolve("scene.stageplay");StageplayStore.write(file,scene);return file;
    }
    private static Path argument(List<String> command,String key) {return Path.of(command.get(command.indexOf(key)+1));}
    private static void assertOwnedFilesRemoved(List<String> command) {
        assertFalse(Files.exists(argument(command,"--scene")));assertFalse(Files.exists(argument(command,"--staging-dir")));
    }
    private static Process launch(List<String> command,String mode) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx32m","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),Child.class.getName(),mode,
                argument(command,"--staging-dir").toString(),argument(command,"--out").toString()).redirectErrorStream(true).start();
    }
    private static String awaitPoll(StageplayExportJob job) throws Exception {
        var result=new AtomicReference<String>();await(()->{result.set(job.poll());return result.get()!=null;});return result.get();
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()) {
            if(System.nanoTime()>=deadline)fail("Owned subprocess did not reach its bounded checkpoint");
            Thread.sleep(10);
        }
    }
    private static void terminate(Process process) throws Exception {
        if(process!=null && process.isAlive()) {process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}
    }
    public static final class Child {
        public static void main(String[] args) throws Exception {
            Path staging=Path.of(args[1]);Files.writeString(staging.resolve("partial.mkv"),"partial");
            Files.writeString(staging.resolve("ready"),"ready");
            switch(args[0]) {
                case "wait" -> {while(true)Thread.sleep(1000);}
                case "fail" -> {System.out.println("owned child failure");System.exit(7);}
                case "success" -> Files.writeString(Path.of(args[2]),"completed film");
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
