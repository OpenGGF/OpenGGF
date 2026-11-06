#!/usr/bin/env python3
"""Observe four explicit category-runner JSON claims, without running or certifying anything.

Origin: 2026-11-05 retained partial-smoke observation task. Input schema is the
immutable e472 category runner, not normalized summaries, XML or rolling logs.
Only plan.json, preparation.json, results.json, status.json, the explicit policy
and the pinned classifier dependency are read. No directory discovery or writes.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import types

DEPENDENCY_BYTES=8564
DEPENDENCY_SHA256='89a7fb137f1ee1f63a0f848f29702cfb9d313d4dd8c54f8d615108a540c2049c'
DEPENDENCY=Path(__file__).absolute().parent.parent/'testing/classify_surefire_skips.py'
FILE_LIMITS={'status.json':16384,'preparation.json':16384,'results.json':16*1024*1024,'plan.json':2*1024*1024}
MAX_TOTAL_BYTES=20*1024*1024
MAX_POLICY_BYTES=2*1024*1024
MAX_OUTPUT_BYTES=16*1024*1024
MAX_CLASSES=10000
MAX_NODES=250000
CASE_CAP=1000
OUTPUT_CASES=50
OUTPUT_CLASSIFIED=100
SCOPE='bounded smoke suite and guards; full-suite is public-release-only; trace/native profiles are separate'
LIMITATIONS=[
 'Input records are non-atomic claims, not command completion, authenticated source, JUnit discovery or certification.',
 'The supported runner stores only the initial source fingerprint; no final after-state or normal VERDICT is observed.',
 'Partial aggregate counts can already include the next uncompleted batch; no per-batch testcase breakdown is inferred.',
 'Only retained literal identities are classified; omitted, capped or missing evidence remains explicit.',
 'Policy evidence paths, original XML, logs, source trees, jobs, processes, locks and runtime capabilities are not inspected.',
 'Rechecks may miss change-and-restore; reads can update atimes. No cache, lease, run, import, write or cleanup occurs.'
]


class ObservationError(ValueError):
    def __init__(self,message,code=2):super().__init__(message);self.code=code


def require(condition,message,code=2):
    if not condition:raise ObservationError(message,code)


def signature(info):
    return (info.st_dev,info.st_ino,info.st_mode,info.st_nlink,info.st_size,info.st_mtime_ns,info.st_ctime_ns)


def directory_identity(info):
    # Unselected sibling creation changes directory times/link counts, not path identity.
    return (info.st_dev,info.st_ino,info.st_mode)


def canonical(value,directory=False):
    require(isinstance(value,str) and 0<len(value)<=4096 and '\0' not in value,'Require a canonical absolute input path')
    p=Path(value)
    require(p.is_absolute() and str(p)==value and not any(x in ('.','..') for x in value.split('/')),
            'Require a canonical absolute input path without aliases')
    parents=[]
    for parent in reversed(p.parents):
        info=parent.lstat();require(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode),'Symlink/non-directory input parent rejected')
        parents.append((parent,directory_identity(info)))
    if directory:
        info=p.lstat();require(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode),'Require a real input directory')
        parents.append((p,directory_identity(info)))
    return p,parents


@dataclass(frozen=True)
class Snapshot:
    path:Path
    data:bytes|None
    identity:tuple|None
    parents:list


def snapshot(value,cap,optional=False):
    path,parents=canonical(value)
    try:info=path.lstat()
    except FileNotFoundError:
        if optional:return Snapshot(path,None,None,parents)
        raise ObservationError('Required input is missing')
    require(stat.S_ISREG(info.st_mode) and info.st_nlink==1,'Selected input must be regular without symlink/hardlink aliases')
    require(info.st_size<=cap,'Selected input byte budget exceeded',3)
    require(hasattr(os,'O_NOFOLLOW'),'No-follow file reads unavailable',3)
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    with os.fdopen(fd,'rb') as stream:
        require(signature(os.fstat(stream.fileno()))==signature(info),'Input changed before read',3)
        data=stream.read(cap+1)
        require(len(data)<=cap,'Selected input byte budget exceeded',3)
        require(signature(os.fstat(stream.fileno()))==signature(info),'Input changed during read',3)
    return Snapshot(path,data,signature(info),parents)


def recheck(saved):
    try:
        for path,identity in saved.parents:require(directory_identity(path.lstat())==identity,'Input parent changed during observation',3)
        if saved.data is None:
            require(not saved.path.exists() and not saved.path.is_symlink(),'Missing input appeared during observation',3)
            return
        again=snapshot(str(saved.path),len(saved.data))
        require(again.identity==saved.identity and again.data==saved.data,'Selected input changed during observation',3)
    except OSError as error:raise ObservationError('Input unavailable on re-observation',3) from error


def strict_json(data):
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,'Duplicate JSON key rejected');result[key]=value
        return result
    def constant(_):raise ObservationError('Nonfinite JSON value rejected')
    try:value=json.loads(data.decode('utf-8'),object_pairs_hook=pairs,parse_constant=constant)
    except (UnicodeError,ValueError,RecursionError) as error:
        if isinstance(error,ObservationError):raise
        raise ObservationError('Malformed/unsupported UTF-8 JSON') from error
    nodes=0
    def walk(item,depth):
        nonlocal nodes
        nodes+=1;require(nodes<=MAX_NODES,'JSON node budget exceeded',3);require(depth<=32,'JSON nesting depth unsupported')
        if isinstance(item,float):require(math.isfinite(item),'Nonfinite JSON number rejected')
        if isinstance(item,str):require(len(item)<=8192,'JSON string exceeds supported runner extent')
        if isinstance(item,dict):
            for key,val in item.items():walk(key,depth+1);walk(val,depth+1)
        elif isinstance(item,list):
            for val in item:walk(val,depth+1)
    walk(value,0);return value


def keys(value,required,optional=()):
    require(type(value)is dict and set(required)<=set(value)<=set(required)|set(optional),'Unknown or missing record fields')


def integer(value,maximum=1000000000):
    require(type(value)is int and 0<=value<=maximum,'Require a bounded nonnegative integer, never a bool');return value


def exit_code(value):
    require(type(value)is int and -(2**31)<=value<2**32,'Require a bounded signed process exit code, never a bool');return value


def number(value):
    require(type(value)in(int,float) and 0<=value<=1e12 and math.isfinite(value),'Require a finite nonnegative duration');return value


def string(value,cap=4096,empty=False):
    require(isinstance(value,str) and (empty or bool(value)) and len(value)<=cap,'Unsupported string extent');return value


def hex_value(value,width):
    require(isinstance(value,str) and re.fullmatch('[0-9a-f]{%d}'%width,value) is not None,'Invalid declared source hash')


def unique_strings(value,cap,item_cap=4096):
    require(type(value)is list,'Require a string list');require(len(value)<=cap,'List budget exceeded',3)
    for item in value:string(item,item_cap)
    require(len(set(value))==len(value),'Duplicate list entries rejected')


def validate_plan(value):
    keys(value,('schema','head','base','mode','full','categories','reasons','tests','inventory_count','guards','workers','suite','scope',
                'working_tree_fingerprint','max_minutes','ordinary_batch_classes'),('headless_gl','rom_directory'))
    require(type(value['schema'])is int and value['schema']==1,'Unsupported category plan schema')
    hex_value(value['head'],40);hex_value(value['working_tree_fingerprint'],64)
    if value['base']is not None:hex_value(value['base'],40)
    require(value['mode']in('focused','change-based') and (value['base']is None)==(value['mode']=='focused'),'Unsupported plan mode/base relation')
    require(type(value['full'])is bool and type(value['guards'])is bool,'Plan flags must be bools')
    require(type(value['workers'])is int and value['workers']in(1,2),'Unsupported worker count')
    require(value['suite']=='smoke' and value['scope']==SCOPE,'Unsupported suite/scope')
    require(type(value['ordinary_batch_classes'])is int and value['ordinary_batch_classes']==50,'Unsupported ordinary batch width')
    number(value['max_minutes']);require(value['max_minutes']>0,'Positive timeout required')
    unique_strings(value['categories'],100,256);require(bool(value['categories']),'Empty category selection')
    unique_strings(value['tests'],MAX_CLASSES);require(bool(value['tests']),'Empty candidate selection')
    for path in value['tests']:
        require(not path.startswith('/') and '\\' not in path and path.endswith('.java') and
                all(p not in('','..','.') for p in path.split('/')),'Unsupported candidate path')
    require(integer(value['inventory_count'])>=len(value['tests']),'Selection exceeds declared inventory')
    require(type(value['reasons'])is list and len(value['reasons'])<=10000,'Unsupported reasons list')
    for row in value['reasons']:
        keys(row,('path','categories','reason'));string(row['path']);string(row['reason'],2048);unique_strings(row['categories'],100,256)
    if 'headless_gl'in value:require(value['headless_gl']=='surfaceless-egl','Unsupported declared graphics backend')
    if 'rom_directory'in value:string(value['rom_directory'])


def validate_preparation(value):
    keys(value,('status','exit_code','elapsed_seconds','working_tree_fingerprint'),('reason','build_wait_seconds'))
    require(value['status']in('passed','failed','incomplete'),'Unsupported preparation status')
    hex_value(value['working_tree_fingerprint'],64);number(value['elapsed_seconds'])
    if value['exit_code']is not None:exit_code(value['exit_code'])
    if value['status']=='passed':require(value['exit_code']==0,'Passed preparation needs exit0')
    if value['status']=='failed':require(value['exit_code']is not None and value['exit_code']!=0,'Failed preparation needs nonzero exit')
    if 'reason'in value:string(value['reason'],4096,True)
    if 'build_wait_seconds'in value:number(value['build_wait_seconds'])


def case_identity(case):
    return string(case['class'],512)+'#'+string(case['test'],512)


def validate_lanes(value,plan):
    require(type(value)is list and len(value)<=2,'results.json must be a list of at most two lane aggregates')
    seen=set()
    for index,lane in enumerate(value):
        keys(lane,('lane','workers','batches_planned','batches_completed','exit_code','elapsed_seconds','status','reports','tests','failures','errors','skipped',
                   'skipped_cases','failed_cases','skipped_cases_omitted','failed_cases_omitted'),('reason','failed_batch'))
        require(lane['lane']==('ordinary' if index==0 else 'guards'),'Unsupported lane order/name')
        require(lane['status']in('passed','failed','incomplete'),'Unsupported lane status')
        require(type(lane['workers'])is int and lane['workers']in(1,2),'Unsupported lane workers')
        planned=integer(lane['batches_planned'],200);completed=integer(lane['batches_completed'],200)
        require(planned>0 and completed<=planned,'Inconsistent batch progress')
        exit_code(lane['exit_code']);number(lane['elapsed_seconds'])
        if plan is not None:
            expected=(len(plan['tests'])+49)//50 if index==0 else 1
            require(planned==expected and lane['workers']==(plan['workers']if index==0 else 1),'Lane selection/worker claim differs from plan')
            if index==1:require(plan['guards'],'Unrequested guard lane')
        if index==1:require(value[0]['batches_completed']==value[0]['batches_planned'] and value[0]['status']!='incomplete','Guard lane before ordinary completion')
        for field in('reports','tests','failures','errors','skipped','skipped_cases_omitted','failed_cases_omitted'):integer(lane[field])
        require(lane['failures']+lane['errors']+lane['skipped']<=lane['tests'],'Impossible testcase totals')
        for field in('skipped_cases','failed_cases'):
            require(type(lane[field])is list and len(lane[field])<=CASE_CAP,'Unsupported retained case list')
            omitted=lane[field+'_omitted'];require(not omitted or len(lane[field])==CASE_CAP,'Omissions contradict original retained-case cap')
        require(len(lane['skipped_cases'])+lane['skipped_cases_omitted']==lane['skipped'],'Inconsistent skipped-case omissions')
        require(len(lane['failed_cases'])+lane['failed_cases_omitted']==lane['failures']+lane['errors'],'Inconsistent failure-case omissions')
        kinds={'failure':0,'error':0}
        for case in lane['failed_cases']:
            keys(case,('class','test','kind','type','message','detail'));ident=case_identity(case)
            require(('failed',ident)not in seen and ('skip',ident)not in seen,'Duplicate retained failure identity');seen.add(('failed',ident))
            require(case['kind']in kinds,'Unsupported failure kind');kinds[case['kind']]+=1
            string(case['type'],512,True);string(case['message'],2048,True);string(case['detail'],8192,True)
        require(kinds['failure']<=lane['failures'] and kinds['error']<=lane['errors'],'Failure kinds contradict counts')
        for case in lane['skipped_cases']:
            keys(case,('class','test','reason'),('reason_projection','reason_source_chars','reason_stack_frames'));ident=case_identity(case)
            require(('skip',ident)not in seen and ('failed',ident)not in seen,'Duplicate retained skip identity');seen.add(('skip',ident))
            string(case['reason'],4096,True)
            projection=set(case)-{'class','test','reason'}
            if projection:
                require(projection=={'reason_projection','reason_source_chars','reason_stack_frames'} and
                        case['reason_projection']=='validated-junit-abort-cause' and
                        case['reason'].startswith('org.opentest4j.TestAbortedException: ') and '\n'not in case['reason'],
                        'Unsupported abort-cause projection')
                require(integer(case['reason_source_chars'])>=len(case['reason']) and integer(case['reason_stack_frames'])>0,'Inconsistent abort-cause metadata')
        if lane['status']!='incomplete':require(completed==planned,'Terminal lane omits planned batches')
        if lane['status']=='passed':require(lane['exit_code']==0 and not(lane['failures']or lane['errors']) and lane['reports']>0 and lane['tests']>lane['skipped'],'Contradictory passed lane')
        if 'failed_batch'in lane:require(1<=integer(lane['failed_batch'],planned)<=planned,'Invalid failed batch')
        if 'reason'in lane:string(lane['reason'],4096,True)


def classifier(saved,policy):
    require(len(saved.data)==DEPENDENCY_BYTES and hashlib.sha256(saved.data).hexdigest()==DEPENDENCY_SHA256,'Pinned skip-classifier dependency differs')
    module=types.ModuleType('_category_observation_skip_policy');module.__file__=str(saved.path)
    exec(compile(saved.data,str(saved.path),'exec'),module.__dict__)
    # Feed the exact existing policy validator bounded, already strict-decoded snapshot text.
    # Its private Path adapter can only return this snapshot; no second policy read or rule algorithm.
    class PolicyText:
        def __init__(self,path):require(path==str(policy.path),'Unexpected policy path')
        def read_text(self,encoding):require(encoding=='utf-8','Unexpected policy encoding');return policy.data.decode('utf-8')
    module.Path=PolicyText
    try:validated=module.load_policy(str(policy.path))
    except module.PolicyError as error:raise ObservationError(str(error))from error
    return module,validated


def observe(run_dir,policy_path,capabilities=''):
    run,parents=canonical(run_dir,directory=True)
    snapshots={};remaining=MAX_TOTAL_BYTES
    for name,cap in FILE_LIMITS.items():
        saved=snapshot(str(run/name),min(cap,remaining),True);snapshots[name]=saved;remaining-=len(saved.data or b'')
    policy=snapshot(policy_path,min(MAX_POLICY_BYTES,remaining));remaining-=len(policy.data)
    dependency=snapshot(str(DEPENDENCY),min(DEPENDENCY_BYTES,remaining))
    require(sum(len(s.data or b'')for s in snapshots.values())+len(policy.data)+len(dependency.data)<=MAX_TOTAL_BYTES,'Aggregate byte budget exceeded',3)
    files=[*snapshots.values(),policy,dependency];identities=[s.identity[:2]for s in files if s.identity is not None]
    require(len(set(identities))==len(identities),'Aliased selected input files')
    values={name:strict_json(saved.data)if saved.data is not None else None for name,saved in snapshots.items()}
    for name,saved in snapshots.items():
        require(saved.data is None or values[name]is not None,'Explicit null JSON document is unsupported')
    policy_value=strict_json(policy.data)
    require(type(policy_value)is dict and type(policy_value.get('version'))is int and policy_value['version']==1 and
            type(policy_value.get('rules'))is list,'Unsupported policy shape')
    require(len(policy_value['rules'])<=10000,'Policy rule budget exceeded',3)
    require('inputs'not in policy_value or policy_value['inputs']is None or type(policy_value['inputs'])is dict,'Unsupported policy inputs shape')
    inputs=policy_value.get('inputs')or{}
    require(type(inputs)is dict and len(inputs)<=256,'Unsupported policy inputs')
    for name,description in inputs.items():string(name,64);string(description,4096)
    for row in policy_value['rules']:
        require(type(row)is dict,'Unsupported policy rule');string(row.get('match'),2048);string(row.get('evidence'),4096)
    module,policy_value=classifier(dependency,policy)
    require(isinstance(capabilities,str) and len(capabilities)<=8192,'Capabilities exceed supported extent')
    try:caps=module.parse_capabilities(capabilities)
    except module.PolicyError as error:raise ObservationError(str(error))from error
    require(set(caps)<=set(inputs),'Capability names must be existing policy input keys')
    plan=values['plan.json'];preparation=values['preparation.json'];status=values['status.json'];lanes=values['results.json']
    if plan is not None:validate_plan(plan)
    if preparation is not None:validate_preparation(preparation)
    if status is not None:keys(status,('status',));require(status['status']in('passed','failed','incomplete'),'Unsupported run status')
    if lanes is not None:validate_lanes(lanes,plan)
    lanes=lanes or[]
    reasons=['source_after_state_unobserved']
    missing=[name for name,saved in snapshots.items()if saved.data is None]
    reasons.extend('missing_'+name for name in missing)
    if status is None or status['status']=='incomplete':reasons.append('terminal_completion_not_claimed')
    if preparation is None or preparation['status']!='passed':reasons.append('preparation_not_passed')
    if plan is not None and preparation is not None and plan['working_tree_fingerprint']!=preparation['working_tree_fingerprint']:reasons.append('declared_source_fingerprint_mismatch')
    if not lanes:reasons.append('ordinary_lane_not_started')
    for lane in lanes:
        if lane['status']=='incomplete' or lane['batches_completed']!=lane['batches_planned']:reasons.append(lane['lane']+'_lane_incomplete')
        if 'timeout'in lane.get('reason','').lower():reasons.append(lane['lane']+'_timeout_claim')
    guard=lanes[1]if len(lanes)>1 else None
    if guard is None:reasons.append('guards_not_run')
    skipped={};failures=[];reason_observations=[];skip_omitted=failed_omitted=0
    for lane in lanes:
        skip_omitted+=lane['skipped_cases_omitted'];failed_omitted+=lane['failed_cases_omitted'];failures.extend(lane['failed_cases'])
        for case in lane['skipped_cases']:
            # Exactly the existing XML reader's message projection; literal identity is untouched.
            message=case['reason'].strip().splitlines();skipped[case_identity(case)]=message[0][:200]if message else''
            if len(reason_observations)<OUTPUT_CASES:
                reason_observations.append(dict(identity=case_identity(case),reason=case['reason'][:512],
                    reason_output_omitted_chars=max(0,len(case['reason'])-512),reason_at_runner_cap=len(case['reason'])==4096,
                    reason_projection=case.get('reason_projection'),reason_source_chars=case.get('reason_source_chars'),
                    reason_stack_frames=case.get('reason_stack_frames')))
            if len(case['reason'])==4096 and 'reason_projection'not in case and 'runner_skip_reason_at_cap'not in reasons:
                reasons.append('runner_skip_reason_at_cap')
        for case in lane['failed_cases']+lane['skipped_cases']:
            if len(case['class'])==512 or len(case['test'])==512:
                if 'runner_identity_at_cap'not in reasons:reasons.append('runner_identity_at_cap')
    if skip_omitted:reasons.append('skipped_case_evidence_omitted')
    if failed_omitted:reasons.append('failed_case_evidence_omitted')
    classified=module.classify(skipped,policy_value,caps)
    classified_counts={key:len(rows)for key,rows in classified.items()}
    issues=[]
    if any(lane['failures']for lane in lanes):issues.append('declared_failures_present')
    if any(lane['errors']for lane in lanes):issues.append('declared_errors_present')
    for key in('unclassified','required_skipped','undeclared_input'):
        if classified_counts[key]:issues.append('retained_skips_'+key)
    shown_failures=[]
    for case in failures[:OUTPUT_CASES]:
        shown_failures.append({**case,'detail':case['detail'][:1024],'detail_omitted_chars':max(0,len(case['detail'])-1024),
                              'message':case['message'][:512],'message_omitted_chars':max(0,len(case['message'])-512)})
    summaries=[]
    for lane in lanes:
        completed=lane['batches_completed'];planned=lane['batches_planned']
        summaries.append({**{key:lane[key]for key in('lane','status','workers','exit_code','elapsed_seconds','reports','tests','failures','errors','skipped')},
                          'reason':lane.get('reason'),'progress':dict(planned=planned,completed=completed,uncompleted_batches=planned-completed,
                            next_uncompleted_batch=completed+1 if completed<planned else None,per_batch_testcase_breakdown_observed=False),
                          'skipped_cases_omitted':lane['skipped_cases_omitted'],'failed_cases_omitted':lane['failed_cases_omitted']})
    def provenance(saved):return dict(path=str(saved.path),bytes=len(saved.data),sha256=hashlib.sha256(saved.data).hexdigest())if saved.data is not None else None
    result=dict(version=1,status='incomplete',certification=False,source_after_state_observed=False,normal_verdict_observed=False,
        declared=dict(status=status['status']if status else None,head=plan['head']if plan else None,
                      working_tree_fingerprint=plan['working_tree_fingerprint']if plan else None,base=plan['base']if plan else None),
        selection=None if plan is None else dict(suite=plan['suite'],mode=plan['mode'],full=plan['full'],categories=plan['categories'],
            selected_classes=len(plan['tests']),inventory_count=plan['inventory_count'],candidate_paths=plan['tests'][:100],
            candidate_paths_output_omitted=max(0,len(plan['tests'])-100),changed_reasons=plan['reasons'][:20],
            changed_reasons_output_omitted=max(0,len(plan['reasons'])-20),headless_gl=plan.get('headless_gl'),rom_directory=plan.get('rom_directory')),
        issues=issues,preparation=preparation,lanes=summaries,guards=dict(requested=plan['guards']if plan else None,observed=guard is not None,
            status=guard['status']if guard else('not-started'if plan is None or plan['guards']else'not-requested')),
        counts={field:sum(lane[field]for lane in lanes)for field in('reports','tests','failures','errors','skipped')}if lanes else None,
        failures=dict(retained=len(failures),input_omitted=failed_omitted,cases=shown_failures,output_omitted=max(0,len(failures)-OUTPUT_CASES)),
        skip_classification=dict(**{key:rows[:OUTPUT_CLASSIFIED]for key,rows in classified.items()},counts=classified_counts,
            retained=len(skipped),input_omitted=skip_omitted,output_omitted={key:max(0,len(rows)-OUTPUT_CLASSIFIED)for key,rows in classified.items()},
            reason_observations=reason_observations,reason_observations_output_omitted=max(0,len(skipped)-OUTPUT_CASES),
            message_projection='Existing read_skips: stripped first line, at most200 characters; identities are unchanged',
            evidence_paths_checked=False),capabilities=caps,missing_inputs=missing,incomplete_reasons=reasons,
        inputs={**{name:provenance(saved)for name,saved in snapshots.items()},'policy':provenance(policy)},
        dependency=provenance(dependency),limits=dict(max_file_bytes=FILE_LIMITS,max_policy_bytes=MAX_POLICY_BYTES,max_total_bytes=MAX_TOTAL_BYTES,
            max_output_bytes=MAX_OUTPUT_BYTES,max_selected_classes=MAX_CLASSES,max_json_nodes=MAX_NODES,max_json_depth=32,
            max_retained_per_kind_per_lane=CASE_CAP,max_failure_cases_output=OUTPUT_CASES,max_classified_per_group_output=OUTPUT_CLASSIFIED),limitations=LIMITATIONS)
    require(len(json.dumps(result,ensure_ascii=True).encode())<=MAX_OUTPUT_BYTES,'Output byte budget exceeded',3)
    for saved in files:recheck(saved)
    for path,identity in parents:require(directory_identity(path.lstat())==identity,'Run directory changed during observation',3)
    return result


class Parser(argparse.ArgumentParser):
    def error(self,message):raise ObservationError(message)


def main(argv=None):
    parser=Parser(description=__doc__)
    parser.add_argument('--run-dir',required=True);parser.add_argument('--policy',required=True)
    parser.add_argument('--capabilities',default='')
    try:
        args=parser.parse_args(argv);result=observe(args.run_dir,args.policy,args.capabilities);code=3
    except ObservationError as error:
        code=error.code;result=dict(version=1,status='incomplete'if code==3 else'rejected',error=str(error)[:1024],certification=False,limitations=LIMITATIONS)
    except(OSError,ValueError,TypeError,RecursionError)as error:
        code=2;result=dict(version=1,status='rejected',error=type(error).__name__,certification=False,limitations=LIMITATIONS)
    print(json.dumps(result,ensure_ascii=True,sort_keys=True,separators=(',',':')))
    return code


if __name__=='__main__':sys.exit(main())
