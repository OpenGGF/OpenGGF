package com.openggf.mods.code;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Behavior checks on the maintained mod models; host/ROM qualification is separate. */
class TestSonic3kChaosModeExampleModels {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Path classes;
    @BeforeAll static void compile() throws Exception {
        classes=Files.createDirectory(temp.resolve("classes"));
        Path project=Path.of("examples/sonic3k-chaos-mode");
        var args=new ArrayList<>(List.of("--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var sources=Files.walk(project.resolve("src/main/java"))) {
            sources.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(args::add);
        }
        var errors=new ByteArrayOutputStream();
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,errors,errors,args.toArray(String[]::new)),errors.toString());
        Path checks=Files.createDirectory(temp.resolve("checks"));
        var checkArgs=new ArrayList<>(List.of("--release","25","-classpath",System.getProperty("java.class.path")+java.io.File.pathSeparator+classes,
                "-d",checks.toString(),project.resolve("src/test/java/chaos/ChaosModelChecks.java").toString(),project.resolve("src/test/java/chaos/ChaosControllerChecks.java").toString()));
        errors.reset();
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,errors,errors,checkArgs.toArray(String[]::new)),errors.toString());
        loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(),checks.toUri().toURL()},TestSonic3kChaosModeExampleModels.class.getClassLoader());
    }
    @AfterAll static void close() throws Exception {if(loader!=null)loader.close();}
    private void check(String name) throws Exception {
        check("ChaosModelChecks",name);
    }
    private void check(String type,String name) throws Exception {
        try {loader.loadClass("chaos."+type).getMethod(name).invoke(null);}
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof AssertionError a) throw a;
            if(failure.getCause() instanceof Exception e) throw e;
            throw failure;
        }
    }
    @Test void emptyAndRareOnlyPoolsPreserveRarity() throws Exception {check("emptyPoolPreservesRandomnessAndRarePoolCannotEscalate");}
    @Test void rewindReplaysActualChoicesAndExpiry() throws Exception {check("capturedScheduleReplaysChoicesAndExpiry");}
    @Test void preferenceFailurePreservesBytesAndAdmittedSettings() throws Exception {check("settingsPreserveRejectedBytesAndRetryWithoutApplyingGameplay");}
    @Test void fivePlayableSidegamesReplayAndAlwaysReleaseTheWorld() throws Exception {check("allSidegamesCaptureInputAndHaveBoundedCancellation");}
    @Test void everyCatalogueEntryPreparesBoundedSemanticPolicies() throws Exception {check("ChaosControllerChecks","factoriesAdmitEveryEffectAndPreserveFixedCapabilities");}
    @Test void nativePacingCannotRepeatOrDropSelectionAndBoundaryReleases() throws Exception {check("ChaosControllerChecks","presentationClockIgnoresNativeTickMultiplicityAndBoundaryRevokes");}
    @Test void headPulseAndMotionTimeChangeActualPolicies() throws Exception {check("ChaosControllerChecks","dynamicPoliciesReplaceIdentityAndKeepAdmittedExpiry");}
    @Test void heldGameMovesReplaysAndCancellationReleases() throws Exception {check("ChaosControllerChecks","heldSidegameCapturesAndCancellationReleasesAuthority");}
    @Test void saveFailureBlocksTitleExitAndSuccessPreservesNativeRoute() throws Exception {check("ChaosControllerChecks","titlePublishesChoiceBeforeNativeOnePlayerExit");}
    @Test void patchKeepsNativeDataSelectTeamsAndInjectedQualification() throws Exception {check("ChaosControllerChecks","moduleRetainsNativeDataSelectAndInjectedSupport");}
    @Test void nativeArtGateAndActualPongWinSubmitOneBoundedReward() throws Exception {check("ChaosControllerChecks","missingNativeArtExcludesGamesAndPongWinSubmitsOneDeclaredReward");}
    @Test void terminalReceiptsSurviveAcknowledgementAndExpiryWithoutRepeatedRewards() throws Exception {check("ChaosControllerChecks","terminalResultsSurviveLeaseRemovalAndRewardOnceAcrossReplay");}
    @Test void scenerySamplesPreparedDonorsAndExcludesNativeZone() throws Exception {check("ChaosControllerChecks","sceneryUsesPreparedDonorsAndExcludesNativeZone");}
    @Test void catalogueCopyRendersWithAvailableGlyphs() throws Exception {check("catalogueCopyIsGlyphSafeAndFitsTheDetailCard");}
    @Test void filteredMenuAddAndLayoutStayInsideViewports() throws Exception {check("ChaosControllerChecks","menuAddsOnlyShownEffectsAndDrawsInsideViewports");}
    @Test void hudSkipsSetupScreensAndAnnouncesOnceAcrossRewind() throws Exception {check("ChaosControllerChecks","hudWaitsForGameplayAndAnnouncesActivationOnce");}
    @Test void creatorPackageValidatesAndRegistersDormantFamilies() throws Exception {
        var manifest=classes.resolve("META-INF/openggf-mod.yaml");Files.createDirectories(manifest.getParent());
        Files.copy(Path.of("examples/sonic3k-chaos-mode/src/main/resources/META-INF/openggf-mod.yaml"),manifest);
        Path jar=temp.resolve("sonic3k-chaos-mode.jar");
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",jar.toString(),"--warnings","error"},System.out));
        try(var assets=com.openggf.io.ModAssetRoot.snapshotDirectory(classes,classes,com.openggf.io.ModInputLimits.production(),com.openggf.io.DirectoryAccess.TEST)) {
            var mod=loader.loadClass("chaos.ChaosMod").asSubclass(GgfMod.class).getConstructor().newInstance();
            var context=new ModContext("sonic3k-chaos-mode","s3k",assets,null,false,temp.resolve("storage"));mod.register(context);var plan=context.freeze();
            assertEquals(27,plan.mutators().size());assertEquals(1,plan.explicitPatches().size());
            assertTrue(plan.objectFactories().isEmpty(),"Host owns native object identities");
            assertTrue(plan.mutators().values().stream().allMatch(m->m.definition().enableScope()==com.openggf.mods.mutators.MutatorScope.LIVE));
            var faults=new ModFaultBoundary(Map.of("sonic3k-chaos-mode",Set.of()),new com.openggf.mods.ModRuntimeFindingStore(),
                    owners->new com.openggf.mods.ModStateSaveResult.Saved(),owners->{ });
            var host=new com.openggf.mods.mutators.TemporaryMutatorState(1,List.copyOf(plan.mutators().values()),faults);
            var catalogueType=loader.loadClass("chaos.ChaosCatalog");var catalogue=catalogueType.getConstructor().newInstance();
            var effectType=loader.loadClass("chaos.ChaosCatalog$Effect");var definitions=loader.loadClass("chaos.ChaosDefinitions");
            try(var port=host.open("sonic3k-chaos-mode",EnumSet.allOf(com.openggf.mods.mutators.MutatorCapability.class))) {
                for(Object effect:(List<?>)catalogueType.getMethod("effects").invoke(catalogue)) {
                    int duration=(Integer)effectType.getMethod("duration",int.class).invoke(effect,4);
                    Object sample=catalogueType.getMethod("sample",effectType,com.openggf.mods.state.SnapshotRandom.class)
                            .invoke(catalogue,effect,new com.openggf.mods.state.SnapshotRandom(17));
                    @SuppressWarnings("unchecked") var values=(Map<String,Object>)definitions.getMethod("options",effectType,Map.class,int.class).invoke(null,effect,sample,duration);
                    String family=(String)effectType.getMethod("family").invoke(effect);
                    var admitted=port.activate(family,values,duration);
                    assertTrue(admitted.accepted(),effectType.getMethod("id").invoke(effect)+": "+admitted.reason());
                    assertTrue(port.cancel(admitted.activationId()));
                }
            }
        }
        try(var packed=new java.util.zip.ZipFile(jar.toFile())) {
            assertTrue(packed.stream().noneMatch(e->e.getName().matches(".*\\.(gen|png|wav)$")),"Native assets must come from supplied ROMs");
        }
    }
}
