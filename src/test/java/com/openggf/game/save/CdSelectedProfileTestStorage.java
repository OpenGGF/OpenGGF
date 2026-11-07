package com.openggf.game.save;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
/** Test-only use of the existing package-private reader seam. No production hook. */
public final class CdSelectedProfileTestStorage {
 private CdSelectedProfileTestStorage() { }
 public static SaveManager counted(Path root,AtomicInteger count){var mapper=new ObjectMapper();return new SaveManager(root,file->{count.incrementAndGet();try(var input=Files.newInputStream(file)){return mapper.readValue(input,new TypeReference<Map<String,Object>>(){});}});}
 public static SaveManager directoryAfterRead(Path root,int ordinal){var count=new AtomicInteger();var mapper=new ObjectMapper();return new SaveManager(root,file->{Map<String,Object> value;try(var input=Files.newInputStream(file)){value=mapper.readValue(input,new TypeReference<Map<String,Object>>(){});}if(count.incrementAndGet()==ordinal){Files.delete(file);Files.createDirectory(file);}return value;});}
 public static SaveManager errorOnRead(Path root,int ordinal,Error original){var count=new AtomicInteger();var mapper=new ObjectMapper();return new SaveManager(root,file->{if(count.incrementAndGet()==ordinal)throw original;try(var input=Files.newInputStream(file)){return mapper.readValue(input,new TypeReference<Map<String,Object>>(){});}});}
}
