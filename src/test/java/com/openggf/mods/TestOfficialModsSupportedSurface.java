package com.openggf.mods;

import com.openggf.mods.validation.*;
import com.openggf.tools.modsdk.ProjectScaffolder;
import org.junit.jupiter.api.Test;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** ROM-free attributed compile and bytecode admission of maintained production projects. */
class TestOfficialModsSupportedSurface {
    private static final Set<String> PENDING_MIGRATION = Set.of("typing-of-the-hedgehog", "infinite-sonic",
            "sonic-survivors", "putt-putt-paradise", "starpost-valley");
    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path TEMP = ROOT.resolve("target/test-tmp");
    private record Compilation(boolean success, List<String> findings, String errors) { }

    private Compilation compile(List<Path> sources, Path output, boolean audit) throws Exception {
        Files.createDirectories(output);
        if (sources.isEmpty()) return new Compilation(true,List.of(),"");
        JavaCompiler compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "Java 25 compiler required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        String cp = Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(s -> !s.replace('\\','/').endsWith("/test-classes"))
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        // Surefire's manifest launcher may hide engine classes in its classpath. Use actual code locations.
        cp += java.io.File.pathSeparator + Path.of(SupportedModContract.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var manager=compiler.getStandardFileManager(diagnostics,null,null)) {
            boolean ok=compiler.getTask(null,manager,diagnostics,List.of("--release","25","-proc:none","-classpath",cp,
                    "-processorpath",cp,"-Xplugin:OpenGGFSupported"+(audit?" audit":""),"-d",output.toString()),
                    null,manager.getJavaFileObjectsFromPaths(sources)).call();
            for (var d : diagnostics.getDiagnostics()) if (d.getMessage(Locale.ROOT).startsWith("Unsupported engine symbol ")) {
                assertNotNull(d.getSource(), "Supported diagnostics must name a file");
                assertTrue(d.getLineNumber() > 0, "Supported diagnostics must name a source line");
                assertTrue(d.getMessage(Locale.ROOT).contains(SupportedModContract.HINT));
            }
            var findings=diagnostics.getDiagnostics().stream().map(d->d.getMessage(Locale.ROOT))
                    .filter(s->s.startsWith("Unsupported engine symbol ")).toList();
            String errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR)
                    .map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
            return new Compilation(ok,findings,errors);
        }
    }
    @Test void officialProjects() throws Exception {
        long start=System.nanoTime();
        Files.createDirectories(TEMP);
        Path work=Files.createTempDirectory(TEMP,"supported-official-");
        try {
            List<Path> projects=new ArrayList<>();
            try(var paths=Files.walk(ROOT.resolve("examples"))) {
                paths.filter(p->p.endsWith("src/main/resources/META-INF/openggf-mod.yaml"))
                        .filter(p->!p.toString().contains("/target/")).map(p->p.getParent().getParent().getParent().getParent().getParent()).sorted().forEach(projects::add);
            }
            projects.add(ROOT.resolve("racing/time-attack"));
            for(String starter:List.of("sample-character-src","sample-standalone-src"))
                projects.add(ROOT.resolve("src/test/resources/mods/"+starter+"/project"));
            for(String kind:List.of("music","reskin","object","character","zone","scene","standalone")) {
                Path project=work.resolve("template-"+kind);
                new ProjectScaffolder().scaffold(project,"guard-"+kind,"guard."+kind,kind);
                projects.add(project);
            }
            Set<String> pendingSeen=new HashSet<>();
            List<String> failures=new ArrayList<>();
            for(Path project:projects) {
                String manifest=Files.readString(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"));
                Matcher idMatcher=Pattern.compile("(?m)^id:\\s*[\"']?([\\w-]+)").matcher(manifest);
                assertTrue(idMatcher.find(),project.toString());
                String id=idMatcher.group(1);
                List<Path> sources;
                if (Files.isDirectory(project.resolve("src/main/java"))) {
                    try(var paths=Files.walk(project.resolve("src/main/java"))) { sources=paths.filter(p->p.toString().endsWith(".java")).sorted().toList(); }
                } else sources=List.of();
                Path output=work.resolve("classes-"+projects.indexOf(project));
                Compilation result=compile(sources,output,true);
                assertTrue(result.success,()->id+": "+result.errors);
                List<String> findings=new ArrayList<>(result.findings);
                Matcher entry=Pattern.compile("(?m)^entrypoint:\\s*[\"']?([\\w.]+)").matcher(manifest);
                if(entry.find()) for(var f:new ModValidator(ModValidator.ValidationMode.SUPPORTED_ONLY).validateDirectory(output,entry.group(1)).findings())
                    findings.add(f.message());
                var owners=new TreeMap<String,Integer>();
                for(String f:findings) {
                    String symbol=f.replace("Unsupported engine symbol ","").split("[;#]",2)[0].replace('/','.');
                    owners.merge(symbol,1,Integer::sum);
                }
                System.out.println("SUPPORTED "+id+" "+owners);
                if(PENDING_MIGRATION.contains(id)) { pendingSeen.add(id); if(findings.isEmpty()) failures.add(id+": remove clean project from PENDING_MIGRATION"); }
                else if(!findings.isEmpty()) failures.add(id+": "+owners);
            }
            assertEquals(PENDING_MIGRATION,pendingSeen);
            assertTrue(failures.isEmpty(),()->String.join("\n",failures));
        } finally { delete(work); System.out.printf("Supported official guard runtime %.3fs%n",(System.nanoTime()-start)/1e9); }
    }
    @Test void adversarialFixtures() throws Exception {
        Files.createDirectories(TEMP);
        Path work=Files.createTempDirectory(TEMP,"supported-fixtures-");
        try(var paths=Files.list(ROOT.resolve("src/test/resources/mods/supported-consumption"))) {
            for(Path fixture:paths.filter(p->p.toString().endsWith(".java.txt")).sorted().toList()) {
                String source=Files.readString(fixture);
                String expected=source.lines().findFirst().orElseThrow().substring("// expected: ".length());
                Path input=work.resolve("Probe.java"); Files.writeString(input,source);
                Compilation result=compile(List.of(input),work.resolve(fixture.getFileName().toString()),false);
                Set<String> actual=new TreeSet<>();
                for(String finding:result.findings) actual.add(finding.substring("Unsupported engine symbol ".length()).split(";",2)[0]);
                Set<String> wanted=expected.equals("clean")?Set.of():new TreeSet<>(List.of(expected.split(",")));
                assertEquals(wanted,actual,fixture+"\n"+result.errors);
                assertEquals(wanted.isEmpty(),result.success,fixture+"\n"+result.errors);
            }
        } finally { delete(work); }
    }
    private static void delete(Path path) throws Exception {
        try(var paths=Files.walk(path)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
}
