package com.openggf.tools;

import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/**
 * Streams a JFR file into bounded thread/test/phase/site summaries, avoiding expanded
 * event JSON and retaining counters rather than recorded events. Origin: 2026-10-08
 * SOZ test-throughput follow-up, base 5d1ff9b8206594ee1c979ae5174328dd9134ffd4.
 * Inputs: recording path and optional test-class prefixes (default com.openggf.tools.Test).
 * Run with Java 21 source-file mode and a bounded heap; no engine build is required.
 * Sample counts are observations, not CPU time; native samples include waiting.
 * Allocation sample weights estimate churn, not retained heap or leaks.
 */
public class JfrTestSummary {
    private static final Set<String> EVENTS = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample", "jdk.ObjectAllocationSample");
    private static final Map<String,Long> samples = new HashMap<>(), javaLeaves = new HashMap<>(),
            nativeLeaves = new HashMap<>(), allocation = new HashMap<>(), readbacks = new HashMap<>();
    static void add(Map<String,Long> map, String key, long n) { map.merge(key, n, Long::sum); }
    static void show(String title, Map<String,Long> map, int limit) {
        System.out.println(title);
        map.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .limit(limit).forEach(e -> System.out.println(e.getValue()+"\t"+e.getKey()));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException(
            "Usage: JfrTestSummary.java <recording.jfr> [test-class-prefix ...]");
        var prefixes=args.length==1 ? List.of("com.openggf.tools.Test")
                : Arrays.asList(Arrays.copyOfRange(args,1,args.length));
        System.out.println("Observations only: Java/native sampling periods differ; native samples include waiting.");
        System.out.println("Allocation weights estimate churn, not retained memory; unmatched stacks remain other.");
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var e = recording.readEvent();
                String type=e.getEventType().getName();
                if (!EVENTS.contains(type)) continue;
                var thread = e.hasField("sampledThread") ? e.getThread("sampledThread") : e.getThread();
                String threadName=thread==null?"unknown":thread.getJavaName();
                var stack=e.getStackTrace();
                if (stack==null || stack.getFrames().isEmpty()) continue;
                String owner="other", phase="other";
                List<String> methods=new ArrayList<>();
                for (var frame:stack.getFrames()) {
                    var method=frame.getMethod();
                    String name=method.getType().getName()+"."+method.getName();
                    methods.add(name);
                    if(prefixes.stream().anyMatch(name::startsWith))
                        owner=method.getType().getName().replace("com.openggf.tools.","");
                }
                if(methods.stream().anyMatch(n->n.startsWith("com.openggf.tools.GameplayCaptureSession.drawFrame") || n.startsWith("com.openggf.tools.GameplayCaptureSession.render"))) phase="drawing/readback";
                else if(methods.contains("com.openggf.tools.GameplayCaptureSession.step")) phase="gameplay step";
                else if(methods.stream().anyMatch(n->n.startsWith("com.openggf.game.rewind."))) phase="rewind capture/restore/compare";
                else if(methods.stream().anyMatch(n->n.startsWith("com.openggf.tools.GameplayCaptureSession.boot"))) phase="boot";
                String key=threadName+" / "+owner+" / "+phase;
                if (methods.stream().anyMatch(n->n.startsWith("com.openggf.graphics.ScreenshotCapture.")))
                    add(readbacks,type+" / "+key,type.equals("jdk.ObjectAllocationSample") ? e.getLong("weight") : 1);
                if (type.equals("jdk.ObjectAllocationSample")) add(allocation,key+" / "+methods.getFirst(),e.getLong("weight"));
                else {
                    add(samples,type+" / "+key,1);
                    if(!owner.equals("other")) add(type.equals("jdk.ExecutionSample") ? javaLeaves : nativeLeaves,
                            owner+" / "+methods.getFirst(),1);
                }
            }
        }
        show("SAMPLE COUNTS", samples,40);
        show("JAVA LEAF SAMPLE COUNTS",javaLeaves,20);
        show("NATIVE LEAF SAMPLE COUNTS (INCLUDES WAITING)",nativeLeaves,20);
        show("READBACK OBSERVATIONS (COUNTS; ALLOCATION ROWS ARE ESTIMATED BYTES)",readbacks,20);
        show("ESTIMATED ALLOCATION SAMPLE WEIGHT BY SITE (BYTES)",allocation,20);
    }
}
