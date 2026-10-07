#!/usr/bin/env python3
"""Diagnose actual ordinary Surefire memory, class floors and Maven/native cost.

Inputs: ordinary suite (default) or explicit --test, timeout and GC mode. Uses a
temporary listener classpath; no POM/normal-test instrumentation changes. Origin:
2026-10-07 user request to find the cause of test-run memory pressure/OOM.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

from category_artifacts import run_logged
from maven_queue import handle_termination, maven_slot
from maven_resources import tree_usage
from profile_test_memory import diagnostic_command, selectors
from run_categories import inventory, policy, rom_args, summarize


def analyse(events):
    boundaries, tests, classes, snapshots, skips = [], [], [], [], []
    end = None
    for event in events:
        kind = event.get('type')
        if kind == 'boundary': boundaries.append(event)
        elif kind == 'test': tests.append(event)
        elif kind == 'class': classes.append(event)
        elif kind == 'snapshot': snapshots.append(event)
        elif kind == 'skipped': skips.append(event)
        elif kind == 'observer-end': end = event
    prior = None
    growth = []
    for row in boundaries:
        if row.get('gcObserved'):
            if prior is not None and row.get('afterClass'):
                growth.append(dict(row, heapGrowthBytes=row['heapBytes']-prior['heapBytes'],
                                   rssGrowthBytes=(row.get('rssBytes') or 0)-(prior.get('rssBytes') or 0)))
            prior = row
    return dict(schema=1, observerFinished=end is not None and end['activeWindows']==0 and end['plans']==end['completedPlans'],
                observerEnd=end, boundaries=boundaries, tests=tests, classes=classes, skips=skips,
                snapshots=snapshots, largestHeapGrowth=sorted(growth,key=lambda x:x['heapGrowthBytes'],reverse=True),
                largestRssGrowth=sorted(growth,key=lambda x:x['rssGrowthBytes'],reverse=True))


def read_events(path):
    if not path.is_file(): return []
    result=[]
    for line in path.read_text().splitlines():
        try: result.append(json.loads(line))
        except json.JSONDecodeError: break  # A killed writer cannot create complete evidence.
    return result


def gc_summary(directory):
    """Keep bounded GC evidence before deleting the rotating raw logs."""
    rows = []
    units = {'K': 1024, 'M': 1024**2, 'G': 1024**3}
    pattern = re.compile(r'GC\((\d+)\) (.*?) (\d+)([KMG])->(\d+)([KMG])\((\d+)([KMG])\) ([\d.]+)ms')
    for path in sorted(directory.glob('test-gc.log*')):
        for line in path.read_text(errors='replace').splitlines():
            match = pattern.search(line)
            if match:
                rows.append(dict(gc=int(match[1]), kind=match[2],
                                 beforeBytes=int(match[3])*units[match[4]],
                                 afterBytes=int(match[5])*units[match[6]],
                                 committedBytes=int(match[7])*units[match[8]],
                                 pauseMillis=float(match[9]), evidence=line))
    return dict(recordedCollections=len(rows),
                highestAfterGc=sorted(rows, key=lambda r:r['afterBytes'], reverse=True)[:12],
                longestPauses=sorted(rows, key=lambda r:r['pauseMillis'], reverse=True)[:12],
                note='Rotating logs may omit early collections; MB values are rounded by HotSpot. Requested boundary GC changes this run.')


def clear_compiled_outputs(root):
    """Only Maven-regenerable class/compiler outputs, under the held tree lease."""
    paths = [root/'target'/name for name in ('classes','test-classes','maven-status')]
    # Validate the entire set before deleting anything; never follow a target link.
    if (root/'target').is_symlink() or any(p.is_symlink() or p.exists() and not p.is_dir() for p in paths):
        raise RuntimeError('Refusing linked or unexpected compiler output paths')
    for path in paths:
        if path.exists(): shutil.rmtree(path)


class ProcessObserver(threading.Thread):
    """Own descendants only; bounded per-process peaks and read-only JVM probes."""
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event=threading.Event()
        self.processes={}
        self.snapshots=[]
        self.peak_tree_rss=0
        self.peak_tree_swap=0
        self.started=time.monotonic()

    def run(self):
        seen=set()
        next_probe=0
        while not self.stop_event.is_set():
            rss=swap=0
            current=[]
            for (pid,start),(resident,_cpu) in tree_usage(os.getpid()).items():
                try:
                    args=(Path('/proc')/str(pid)/'cmdline').read_bytes().split(b'\0')
                    is_java=bool(args) and Path(os.fsdecode(args[0])).name in ('java','java.exe')
                    kind='test' if is_java and any(b'surefirebooter' in a for a in args) else 'maven' if is_java and b'org.codehaus.plexus.classworlds.launcher.Launcher' in args else 'other'
                    status=(Path('/proc')/str(pid)/'status').read_text()
                    match=re.search(r'^VmSwap:\s+(\d+)',status,re.MULTILINE)
                    swapped=int(match[1])*1024 if match else 0
                except OSError: continue
                rss+=resident;swap+=swapped
                key=f'{pid}:{start}'
                row=self.processes.setdefault(key,dict(pid=pid,start=start,kind=kind,peakRssBytes=0,peakSwapBytes=0))
                row['peakRssBytes']=max(row['peakRssBytes'],resident)
                row['peakSwapBytes']=max(row['peakSwapBytes'],swapped)
                if kind in ('maven','test'): current.append((key,pid,kind,resident,swapped))
            self.peak_tree_rss=max(self.peak_tree_rss,rss)
            self.peak_tree_swap=max(self.peak_tree_swap,swap)
            probe_due=time.monotonic()>=next_probe
            for key,pid,kind,resident,swapped in current:
                if len(self.snapshots)<80 and (key not in seen or probe_due):
                    entry=dict(pid=pid,kind=kind,elapsedSeconds=round(time.monotonic()-self.started,1),rssBytes=resident,swapBytes=swapped)
                    for cmd in (['GC.heap_info','VM.flags'] if key not in seen else ['GC.heap_info']):
                        try:
                            r=subprocess.run(['jcmd',str(pid),cmd],capture_output=True,text=True,timeout=10)
                            if cmd=='VM.flags':
                                m=re.search(r'-XX:MaxHeapSize=(\d+)',r.stdout)
                                entry['maxHeapBytes']=int(m[1]) if m else None
                            else: entry['heapInfo']=r.stdout[:2500] if r.returncode==0 else r.stderr[:500]
                        except subprocess.TimeoutExpired: entry['probeError']='jcmd timed out'
                        except OSError as error: entry['probeError']=str(error)
                    self.snapshots.append(entry);seen.add(key)
            if current and probe_due: next_probe=time.monotonic()+300
            self.stop_event.wait(1)

    def finish(self):
        self.stop_event.set();self.join(timeout=25)
        return dict(peakTreeRssBytes=self.peak_tree_rss,peakTreeSwapBytes=self.peak_tree_swap,
                    processes=list(self.processes.values()),jvmSnapshots=self.snapshots)


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--test',help='Exact comma-separated class[#method] control/reproduction')
    p.add_argument('--gc',choices=['between-class','none'],default='between-class')
    p.add_argument('--cold-compile',action='store_true',help='Rebuild only class/compiler outputs inside the measured Maven process')
    p.add_argument('--max-minutes',type=float,default=120)
    p.add_argument('--output',type=Path,default=Path('target/ordinary-memory-report.json'))
    a=p.parse_args(argv)
    if not 0<a.max_minutes<=180: p.error('max-minutes must be in (0,180]')
    root=Path.cwd().resolve()
    selected=selectors(root,a.test.split(',')) if a.test else []
    count=len(selected) if selected else len(inventory(root,policy(root)))
    print(f'Memory diagnostic: {count} candidate classes; actual ordinary Surefire selection; '
          f'one reused 3 GiB fork; GC={a.gc}. Budget {a.max_minutes:g} execution minutes; queue wait excluded.',flush=True)
    result=analyse([]);temporary=None;code=1;environment=os.environ.get('MAVEN_OPTS');observer=None;command=None
    run_commit=None
    source_hashes={str(Path(__file__).relative_to(root)):hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    try:
        with handle_termination(),maven_slot(root,exclusive=True,kind='profile:ordinary-memory',estimate=3600 if not selected else 60) as slot:
            run_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
            (root/'target').mkdir(exist_ok=True)
            temporary=Path(tempfile.mkdtemp(prefix='ordinary-memory-',dir=root/'target'))
            deadline=time.monotonic()+a.max_minutes*60
            def execute(command):
                c=run_logged(command,root,temporary/'execution.log',temporary,
                             timeout=deadline-time.monotonic(),idle_timeout=600,queue_fd=slot)
                if c: raise RuntimeError(f'Preparation failed ({c}): {command[0]}')
            execute(['mvn','-Dmse=off','dependency:build-classpath','-DincludeScope=test','-Dmdep.outputFile='+str(temporary/'classpath.txt')])
            cp=(temporary/'classpath.txt').read_text().strip()
            _command,launcher=diagnostic_command(root,temporary,cp,'none',1,100,['Unused'])
            if not launcher.is_file():
                engine=next(Path(x) for x in cp.split(os.pathsep) if Path(x).name.startswith('junit-platform-engine-'))
                execute(['mvn','-Dmse=off','dependency:copy','-Dartifact=org.junit.platform:junit-platform-launcher:'+engine.parent.name,'-DoutputDirectory='+str(temporary/'libs')])
            java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
            sources=Path(__file__).parent/'java/com/openggf/tools'
            source_hashes.update({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in (sources/'MemoryProfileLauncher.java',sources/'SuiteMemoryListener.java')})
            helper=temporary/'observer'
            execute([str(java/'javac') if java else 'javac','--release','21','-cp',cp+os.pathsep+str(launcher),'-d',str(helper),
                     str(sources/'MemoryProfileLauncher.java'),str(sources/'SuiteMemoryListener.java')])
            service=helper/'META-INF/services/org.junit.platform.launcher.TestExecutionListener'
            service.parent.mkdir(parents=True);service.write_text('com.openggf.tools.SuiteMemoryListener\n')
            if a.cold_compile: clear_compiled_outputs(root)
            line='${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g -XX:NativeMemoryTracking=summary'
            if sys.platform=='darwin': line='-XstartOnFirstThread '+line
            # GC logs rotate; neither JVM heap is enlarged for the investigation.
            line+=' -Xlog:gc*:file='+str(temporary/'test-gc.log')+':time,uptime:filecount=2,filesize=4M'
            os.environ['MAVEN_OPTS']=(environment or '')+' -XX:NativeMemoryTracking=summary'
            command=['mvn','-Dmse=off','-Dsurefire.argLine='+line,'-Dsurefire.forkCount=1','-Dsurefire.reuseForks=true',
                     '-Dsurefire.runOrder=alphabetical','-Dmaven.test.additionalClasspath='+str(helper)+','+str(launcher),
                     '-Dopenggf.memory.events='+str(temporary/'events.jsonl'),'-Dopenggf.memory.gc='+a.gc,
                     *rom_args(root),'-Dopenggf.test.tmpdir='+str(temporary/'tmp'),'-Dopenggf.test.diagnostics='+str(temporary/'diagnostics'),
                     '-Dopenggf.trace.reports='+str(temporary/'trace-reports'),'-Dopenggf.surefire.reports='+str(temporary/'reports'),
                     '-Dopenggf.artifact.root='+str(temporary/'artifacts')]
            if selected: command.append('-Dtest='+','.join(selected))
            command.append('test')
            result['command']=command
            observer=ProcessObserver();observer.start()
            code=run_logged(command,root,temporary/'execution.log',temporary,
                            timeout=deadline-time.monotonic(),idle_timeout=600,queue_fd=slot)
            command_record=result['command'];result=analyse(read_events(temporary/'events.jsonl'));result['command']=command_record
            result['surefire']=summarize(temporary/'reports')
            result['logTail']=(temporary/'execution.log').read_text(errors='replace')[-12000:]
            if not result['classes'] or not result['observerFinished']:
                result['error']='Surefire memory observer missing or incomplete; this is not a complete memory census.'
                if code == 0: code=1
    except (OSError,RuntimeError,TimeoutError,KeyboardInterrupt) as error:
        result=analyse(read_events(temporary/'events.jsonl')) if temporary else analyse([])
        result['error']=str(error) or 'Cancelled';code=1
        if temporary and (temporary/'execution.log').is_file():
            result['logTail']=(temporary/'execution.log').read_text(errors='replace')[-12000:]
        print(result['error'],file=sys.stderr)
    finally:
        if environment is None: os.environ.pop('MAVEN_OPTS',None)
        else: os.environ['MAVEN_OPTS']=environment
        if observer:result['processMemory']=observer.finish()
        if command:result['command']=command
        if temporary:
            result['gcSummary']=gc_summary(temporary)
            if 'surefire' not in result:result['surefire']=summarize(temporary/'reports')
        result.update(commandExitCode=code,candidateClasses=count,gcMode=a.gc,
                      coldCompile=a.cold_compile,
                      commit=run_commit,toolSha256=source_hashes,
                      scope='Actual ordinary Surefire invocation with diagnostic GC/listener; timing differs; not an uninstrumented certification.')
        try:
            a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
            print(f'Report: {a.output}; classes={len(result["classes"])} tests={len(result["tests"])} observerFinished={result["observerFinished"]} Maven exit={code}',flush=True)
        finally:
            if temporary:shutil.rmtree(temporary)
    return code


if __name__=='__main__':
    sys.exit(main())
