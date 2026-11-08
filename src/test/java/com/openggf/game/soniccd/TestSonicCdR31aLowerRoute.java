package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original selected historical-save public input; native terrain observations never
 * feed engine state. N1ecc5f3: GetLevelBlock/FindLevelWall, R3 PlayerCheckBlock and intro. */
@Isolated class TestSonicCdR31aLowerRoute {
    @TempDir Path directory;
    final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    final Map<String,String> documents=new LinkedHashMap<>();
    private record Picture(String name,byte[] rgba,String state) { }
    final List<Picture> pictures=new ArrayList<>();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup()throws Exception{
        fixture.cleanup();String path=System.getProperty("soniccd.r31.lower.evidence.dir");
        if(path!=null)for(var e:documents.entrySet())Files.writeString(Path.of(path).resolve(e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);
        String raw=System.getProperty("soniccd.r31.lower.capture.dir");if(raw==null)return;
        Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath(),dest=Path.of(raw).toRealPath();assertTrue(dest.startsWith(root)&&!dest.equals(root));
        for(var p:pictures){var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);byte[] data=p.rgba();
            for(int y=0;y<224;y++)for(int x=0;x<320;x++){int k=((223-y)*320+x)*4;image.setRGB(x,y,((data[k+3]&255)<<24)|((data[k]&255)<<16)|((data[k+1]&255)<<8)|(data[k+2]&255));}
            try(var output=Files.newOutputStream(dest.resolve(p.name()+".png"),StandardOpenOption.CREATE_NEW)){assertTrue(javax.imageio.ImageIO.write(image,"png",output));}
            Files.writeString(dest.resolve(p.name()+".state.txt"),p.state()+"\nExported after session/GL/fixture cleanup.\n",StandardOpenOption.CREATE_NEW);
        }
    }
    private static int word(CdStageTerrain t,int x,int y,int flags){
        return assertInstanceOf(CdTerrainCollision.Available.class,CdTerrainCollision.fromStageTerrain(t).lookup(x,y,flags)).word().rawWord();
    }
    @Test void originalTerrainAndNativeProfilesExplainTheClosedIntroWall()throws Exception{
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);var host=b.module.r31WorldHost();var f=b.module.requirePlayerFrame();
            var original=b.module.r31LoadingVisit().assets().terrain();var policy=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            for(int n=1;n<=931;n++){
                TestSonicCdR31aOrdinaryRoute.step(b,policy.input(b));policy.returned(f.state());
                assertEquals(2,f.state().motion().routine());assertFalse(policy.rejected);
            }
            assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertEquals(1077,f.state().motion().centreX16_16()>>>16);
            assertEquals(908,f.state().motion().centreY16_16()>>>16);
            var current=host.currentTerrain();assertEquals(original.chunks(),current.chunks());assertEquals(original.layout().ram(),current.layout().ram());
            int flags=f.state().motion().spriteFlags();assertEquals(0x4002,word(current,1088,896,flags));
            var out=new StringBuilder("Original and current chunks/layout equal after actual returned931; no terrain mutation is inferred from equal bytes.\n");
            var actor=f.state();var environment=f.environment();
            var ids=new TreeSet<Integer>();var probes=CdPlayerTerrain.fromStageTerrain(current);
            for(int y=800;y<=1104;y+=16)for(int x=960;x<=1280;x+=16){
                var placement=CdTerrainCollision.fromStageTerrain(current).lookup(x,y,flags);
                out.append(x+","+y+" "+placement);
                if(placement instanceof CdTerrainCollision.Available a){int w=a.word().rawWord(),id=current.collision().idForBlock(w&0x7FF);ids.add(id);out.append(" id="+id+" angle="+current.collision().angle(id));}
                out.append('\n');
            }
            for(int id:ids){out.append("PROFILE "+id+" angle="+current.collision().angle(id)+" heights=");for(int i=0;i<16;i++)out.append(current.collision().height(id,i)+",");out.append(" widths=");for(int i=0;i<16;i++)out.append(current.collision().width(id,i)+",");out.append('\n');}
            for(int y=800;y<=1056;y++)out.append("WALL at1088,"+y+" "+probes.wall(1088,y,14,0,16,flags)+"\n");
            assertSame(actor,f.state());assertSame(environment,f.environment());assertSame(current,host.currentTerrain());
            out.append(TestSonicCdR31aOrdinaryRoute.cut(b,931));documents.put("terrain-audit.txt",out.toString());
        }
    }
    private void picture(TestSonicCdR31aSelectedProfileLoading.Booted b,int n,String name)throws Exception{
        byte[] rgba=TestSonicCdR31aSelectedProfileLoading.draw(b);String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(rgba));
        pictures.add(new Picture(name,rgba,TestSonicCdR31aOrdinaryRoute.cut(b,n)+"\nrgbaSHA256="+hash));
    }

    /** Source geometry: left lipX1024 has side solidity throughY975; lower floorY1024.
     * Return left off the upper ledge after Metal has retired the barriers, and turn
     * right only when the actual head is below that ceiling. No additional action tap.
     * Ordinals/captured records are diagnostics, never controller or engine values. */
    @Test void ordinaryBacktrackClosesTimePostInitBeforeTheNextGenuineOwner()throws Exception{
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false;String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=5000;n++){
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;picture(b,n,"original-lower-passage-entered");pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;picture(b,n,"original-roll-tunnel-speed");pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    springInitialised=n;break; // No new picture, retry, later endpoint or warp bypass.
                }
            }
            String partial=frontier==null?"none":frontier+"\n"+Arrays.toString(frontier.getStackTrace())+"\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            documents.put("lower-route-result.txt","Existing unchanged recipe; SOURCE-ONLY Time Post continuation: qualified intro prefix; left off upper lip; right after actual head clears ceiling. returned="+returned+" attempted="+(frontier==null?"none":returned+1)+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nLAST_CLOSED\n"+last+"\nFIRST_UNOWNED_PARTIAL(no closed snapshot/picture/retry)\n"+partial);
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Require genuine passage traversal before classifying a later frontier");assertTrue(boosted>entered);
            assertTrue(postInitialised>boosted,"Positive original Time Post Init closure is required before any later frontier; SOURCE-ONLY UNRUN, no endpoint claimed");
            assertTrue(springInitialised>postInitialised,"Actual controller must return the genuine next placed Spring Init; SOURCE-ONLY UNRUN, no completed1131 claim");
            if(frontier!=null){assertTrue(f.closed());documents.put("next-owner-source-only-observation.txt",partial);}
            else assertTrue(f.state().motion().routine()<6,"Returned death stops this recipe; never extend reload cycles as route progress");
        }finally{documents.put("lower-route.csv",csv.toString());}
    }
    // BEGIN appended after-Spring diagnostic observer, source-only 20261108.
    /** Same qualified controller/physical ingress; at most3000 original loading+ordinary+replay
     * calls. No new endpoint is known until Root executes this sole observer. */
    @Test void observeOriginalControllerAfterReturnedSpringToFirstStop()throws Exception{
        // Optional exclusive output leaf; existing cleanup alone writes documents/PNG after GL/session retirement.
        String output=System.getProperty("soniccd.r31.lower.evidence.dir"),capture=System.getProperty("soniccd.r31.lower.capture.dir");
        assertTrue(capture==null||output!=null,"Optional Spring PNG requires the same exclusive observer evidence directory");
        if(output!=null){
            Path e=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD").toRealPath();
            Path dest=afterSpringOutputPath(output,e);
            if(capture!=null){assertEquals(dest,afterSpringOutputPath(capture,e));Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath();assertEquals(e.resolve("captures/engine").toRealPath(),root);assertTrue(dest.startsWith(root)&&!dest.equals(root));}
            Files.createDirectory(dest); // exclusive creation; an existing leaf rejects, never reused/overwritten
        }
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        var heldRows=new StringBuilder("recipeOrdinal,requestedHeldNative,controllerAttemptIncludingLoadingAndReplays\n");
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false;String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=3000;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>3000)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;heldRows.append(n+","+held+","+controllerAttempts+"\n");
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;documents.put("after-spring-passage-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;documents.put("after-spring-roll-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    documents.put("after-spring-healthy-init.txt",springRow);
                    if(capture!=null){var pictureBefore=TestSonicCdR31aSelectedProfileLoading.values(b);picture(b,n,"healthy-returned-spring-init-old-selected-cpu");assertEquals(pictureBefore,TestSonicCdR31aSelectedProfileLoading.values(b),"Selected renderer is observation only");documents.put("after-spring-picture-boundary.txt","ENGINE picture selected at healthy returned Spring Init. OLD selected CPU/current-art/cameras may predate returned State8; no visible Spring, parity or current-primary-picture assertion.\n"+springRow);}
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
            }
            String outcome=frontier!=null?"UNOWNED":returnedDeath?"RETURNED_DEATH":"BOUND_REACHED";
            String partial=frontier==null?"none":afterSpringException(frontier)+"\n"+Arrays.toString(frontier.getStackTrace())+"\nPARTIAL COMPONENT DIAGNOSTICS ONLY (not closed World/selected output)\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nCorePhase="+core.state().phase()+"\nCoreCalls="+core.state().calls()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            int completedCalls=loadingReturned+returned+replayReturned;
            documents.put("after-spring-observation.txt","DIAGNOSTIC OBSERVER, NOT ROUTE SUCCESS. Constructed historical-save CCZ slot1/platform80; not natural PPZ-to-CCZ.\noutcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" attemptedRecipeOrdinal="+attempted+" controllerAttemptsIncludingLoadingAndReplays="+controllerAttempts+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nA passed observer certifies these assertions only; an UNOWNED attempted service did not return.\nLAST_ACTUALLY_RETURNED_CLOSED_ROW (may be returned death)\n"+last+"\nHEALTHY_SPRING_MILESTONE\n"+springRow+"\nFIRST_UNOWNED_PARTIAL(no closed World capture/restore/picture/retry)\n"+partial);
            if(frontier!=null&&!(firstCapture>0&&retired==firstCapture+1&&closedIntro==retired&&turned>closedIntro&&entered>turned&&boosted>entered&&postInitialised>boosted&&springInitialised>postInitialised&&intro.jumps==2))throw (UnsupportedOperationException)frontier; // original object, never an early unsupported positive
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Positive lower-passage closure before any later boundary");assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertTrue(springInitialised>postInitialised,"Early unsupported/death/bound is not a positive Spring prefix");assertEquals(2,intro.jumps);
            assertTrue(controllerAttempts<=3000);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null){assertTrue(f.closed());documents.put("after-spring-first-unowned.txt",partial);}
            else if(returnedDeath)assertTrue(f.state().motion().routine()>=6,"Stop on the first returned death, before any pending replay");
            else{assertFalse(f.closed());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());assertTrue(f.state().motion().routine()<6);assertTrue(controllerAttempts+1>3000,"The only healthy no-boundary exit exhausts the total controller budget");}

        }finally{documents.put("after-spring-route.csv",csv.toString());documents.put("after-spring-held-inputs.csv",heldRows.toString());}
    }
    /** Both evidence and capture must already name the canonical, normalized leaf used by cleanup. */
    private static Path afterSpringOutputPath(String raw,Path boundary)throws java.io.IOException{
        Path requested=Path.of(raw);assertTrue(requested.isAbsolute());
        assertEquals(requested,requested.normalize(),"Output path must already be normalized; aliases cannot select another export leaf");
        Path parent=requested.getParent().toRealPath();assertTrue(parent.startsWith(boundary)&&!requested.equals(boundary));
        assertEquals(parent,requested.getParent(),"Canonical parent, no symlink/traversal output alias");
        return requested;
    }
    private static String afterSpringException(Throwable original){
        String boundary="not advertised",whole="not advertised",value="not advertised";
        if(original instanceof SonicCdR31aObjectPass.Unowned u){boundary=u.boundary().name();whole=Integer.toString(u.wholeIndex());value=u.value()+" (0x"+Integer.toHexString(u.value())+")";}
        else if(original instanceof SonicCdR31aLoadingVisit.UnsupportedLoading u)boundary=u.boundary().name();
        else if(original instanceof CdWorld.Unowned u)boundary=u.boundary().name();
        else if(original instanceof CdWorld.Stopped u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerFrameRuntime.UnsupportedFrame u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerBody.UnsupportedBody u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerMovement.UnsupportedMotion u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerBody.UnsupportedTouchHandler u){boundary="touch kind "+u.kind().name();whole=Integer.toString(u.selectedSlot().identity().wholeIndex());value="selectedSlot.id="+u.selectedSlot().id();}
        return "OriginalThrowableClass="+original.getClass().getName()+" identityHashCode="+System.identityHashCode(original)+" boundary="+boundary+" whole="+whole+" value="+value+"\nOriginal="+original+"\nCause="+original.getCause()+"\nSuppressed="+Arrays.toString(original.getSuppressed());
    }
    // END appended after-Spring diagnostic observer.

}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
