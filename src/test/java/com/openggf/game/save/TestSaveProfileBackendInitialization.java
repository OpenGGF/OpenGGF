package com.openggf.game.save;

import com.openggf.game.GameId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST. Real TempDir backend operations only; author Java was UNRUN; Root qualification is recorded in the implementation plan. */
class TestSaveProfileBackendInitialization {
    @TempDir Path root;
    @Test void actualInitializationCreatesOnlyTheConfiguredNamespace() throws Exception {
        var manager=new SaveManager(root);var receipt=SaveProfileAccess.initializeBackend(manager,GameId.SCD);
        assertTrue(receipt.isFor(manager,GameId.SCD));assertTrue(Files.isDirectory(root.resolve("scd")));
        try(var entries=Files.list(root.resolve("scd"))){assertEquals(0,entries.count());}
    }
    @Test void existingNamespaceAndUnrelatedContentsSurvive() throws Exception {
        var directory=Files.createDirectories(root.resolve("scd"));var untouched=directory.resolve("unrelated");Files.writeString(untouched,"original");
        SaveProfileAccess.initializeBackend(new SaveManager(root),GameId.SCD);assertEquals("original",Files.readString(untouched));
    }
    @Test void initializationDoesNotSearchOrDecodeASlot() throws Exception {
        Files.createDirectories(root.resolve("scd"));Files.writeString(root.resolve("scd/slot1.json"),"not JSON");
        var reads=new java.util.concurrent.atomic.AtomicInteger();var manager=new SaveManager(root,path->{reads.incrementAndGet();throw new IOException("reader must not run");});
        SaveProfileAccess.initializeBackend(manager,GameId.SCD);assertEquals(0,reads.get());assertEquals("not JSON",Files.readString(root.resolve("scd/slot1.json")));
    }
    @Test void oneActualManagerCannotBorrowAnotherManagersReceipt() throws Exception {
        var one=new SaveManager(root);var two=new SaveManager(root);var receipt=SaveProfileAccess.initializeBackend(one,GameId.SCD);
        assertTrue(receipt.isFor(one,GameId.SCD));assertFalse(receipt.isFor(two,GameId.SCD));
    }
    @Test void completedOperationsHaveIndependentReceiptIdentity() throws Exception {
        var manager=new SaveManager(root);assertNotSame(SaveProfileAccess.initializeBackend(manager,GameId.SCD),SaveProfileAccess.initializeBackend(manager,GameId.SCD));
    }
    @Test void nonDirectoryNamespaceFailsWithoutReplacingIt() throws Exception {
        Files.writeString(root.resolve("scd"),"preserve");assertThrows(IOException.class,()->SaveProfileAccess.initializeBackend(new SaveManager(root),GameId.SCD));assertEquals("preserve",Files.readString(root.resolve("scd")));
    }
    @Test void nonDirectoryRootFailsWithoutSelectingAnotherBackend() throws Exception {
        var file=root.resolve("root-file");Files.writeString(file,"preserve");assertThrows(IOException.class,()->SaveProfileAccess.initializeBackend(new SaveManager(file),GameId.SCD));assertEquals("preserve",Files.readString(file));
    }
    @Test void existingWriterOrderingPrecedesInitialization() throws Exception {
        var manager=new SaveManager(root);manager.writeSlotAsync("scd",2,Map.of("order","before"));SaveProfileAccess.initializeBackend(manager,GameId.SCD);
        assertTrue(Files.isRegularFile(root.resolve("scd/slot2.json")));assertEquals("before",manager.readSlotSummary("scd",2).payload().get("order"));
    }
    @Test void interruptionReturnsNoReceiptAndDoesNotPromiseFilesystemRollback() throws Exception {
        var manager=new SaveManager(root);Thread.currentThread().interrupt();
        try{assertThrows(IOException.class,()->SaveProfileAccess.initializeBackend(manager,GameId.SCD));assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
        manager.flushPendingWrites(); // The already-submitted action may have created the directory.
        assertFalse(Files.exists(root.resolve("scd/slot1.json")));
        if(Files.isDirectory(root.resolve("scd")))try(var files=Files.list(root.resolve("scd"))){assertEquals(0,files.count());}
    }
    @Test void boundedSurfaceAndPrivateReceiptsPreserveOldEntryPoints() {
        assertEquals(List.of("createIfMissing/2","initializeBackend/2","lookup/3"),Arrays.stream(SaveProfileAccess.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertTrue(Arrays.stream(SaveProfileAccess.BackendInitialization.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        assertThrows(NullPointerException.class,()->SaveProfileAccess.initializeBackend(null,GameId.SCD));assertThrows(NullPointerException.class,()->SaveProfileAccess.initializeBackend(new SaveManager(root),null));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
