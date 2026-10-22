package com.openggf.stageplay.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayProcessInput {
    @Test void acceptsExactBoundsCrLfCrLfAndFinalUnterminatedRecords() throws Exception {
        try(var input=new StageplayProcessInput(new StringReader("1234\r\n\nxy\rz"))) {
            assertEquals("1234",input.readLine(4));
            assertEquals("",input.readLine(0));
            assertEquals("xy",input.readLine(2));
            assertEquals("z",input.readLine(1));
            assertNull(input.readLine(1));assertNull(input.readLine(1));
        }
    }

    @Test void bufferedChunksPreserveFollowingRecordsAndChangingBounds() throws Exception {
        String large="x".repeat(20000);
        try(var input=new StageplayProcessInput(new StringReader(large+"\nsmall\n"))) {
            assertEquals(large,input.readLine(20000));
            assertEquals("small",input.readLine(5));
            assertNull(input.readLine(0));
        }
        try(var input=new StageplayProcessInput(new StringReader("12345\n"))) {
            assertThrows(IOException.class,()->input.readLine(4));
        }
        try(var input=new StageplayProcessInput(new StringReader("x"))) {
            assertThrows(IOException.class,()->input.readLine(0));
        }
    }

    @Test void growingRecordIsRejectedWithoutReadingOrAllocatingTheRestOfTheStream() throws Exception {
        var count=new AtomicLong();
        Reader neverEnding=new Reader() {
            public int read(char[] chars,int offset,int length) {
                java.util.Arrays.fill(chars,offset,offset+length,'x');count.addAndGet(length);return length;
            }
            public void close() { }
        };
        try(var input=new StageplayProcessInput(neverEnding)) {
            assertThrows(IOException.class,()->input.readLine(10000));
            assertTrue(count.get()<=10000+8192,"At most one fixed buffer may read ahead of the line cap");
        }
    }
}
