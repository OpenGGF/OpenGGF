package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
/** UNRUN original source ingress; data/mapper admission is not a public-controller outcome. */
@Isolated class TestCdFlipperSource {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void explicitMapperAdmitsExactlyThreeOriginalFlipperFramesAndKeepsLegacyMapperBoundary() throws Exception {
        try(var b=fixture.start(1,0x80)){
            var a=((CdGameDataSource)b.mode.getWorldSession().getDataSource()).r31aAssets().orElseThrow();var c=CdR31aObjectAssets.fromAssets(a);var s=CdR31aBumperSource.fromAssets(a);
            assertTrue(s.owns(a));assertFalse(s.owns(null));assertEquals(a.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong(29*4),s.flipperHandlerAddress());
            var mapper=CdObjectMappings.fromR31aPinballAssets(a,c);for(int frame=0;frame<3;frame++)assertFalse(mapper.resolve(0x20b0ea,false,frame).pieces().isEmpty());
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20b0ea,false,3));assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0xff20b0ea,false,0));
            assertThrows(UnsupportedOperationException.class,()->CdObjectMappings.fromR31aAssets(a,c).resolve(0x20b0ea,false,0));
            assertEquals(18,s.assets().flipperSlope(false,64));assertEquals(0,s.assets().flipperSlope(true,64));
        }
    }
    @Test void wrongProgrammeAndForeignOwnerHaveNoReceiptFactoryFromValues(){
        // R31B/C/D data may feed constructed component tests; the live factory is typed R31A.
        assertEquals(CdR31aAssets.class,java.util.Arrays.stream(CdR31aBumperSource.class.getDeclaredMethods()).filter(m->m.getName().equals("fromAssets")).findFirst().orElseThrow().getParameterTypes()[0]);
        assertTrue(CdR31PinballCall.class.isSealed());assertTrue(java.util.Arrays.stream(CdR31PinballCall.Original.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        assertTrue(java.util.Arrays.stream(CdR31PinballCall.class.getDeclaredMethods()).filter(m->m.getName().equals("fromOriginal")).noneMatch(m->m.getParameterTypes()[0]==CdPinballContext.PrimaryResult.class));
    }
}
