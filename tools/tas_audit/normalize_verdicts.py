"""Reconcile reviewed worker verdicts into the final report input.

Origin: OpenGGF TAS verdict follow-up, 2026-10-26.
Inputs: completed per-game JSON, repository test sources, final validation JSON.
No verdict is inferred from source presence or missing tests.
"""
import argparse, collections, copy, json, re
from pathlib import Path

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source-dir',type=Path,required=True)
    ap.add_argument('--repository',type=Path,required=True)
    ap.add_argument('--validation',type=Path,required=True)
    args=ap.parse_args()
    records=[]
    for game in ('s1','s2','s3k'):
        data=json.loads((args.source_dir/f'{game}-verdicts.json').read_text())
        families=data['families'] if isinstance(data,dict) else data
        for original in families:
            record=copy.deepcopy(original)
            if record.get('kind')=='source_note':
                record['reason']='The original audit record preserves unnamed shortcut descriptions without a defined mechanism. It remains source provenance, receives no engine verdict and contributes no tested-trick credit.'
            else:
                assert record.get('interim',False) is False, record['family_id']
                assert re.fullmatch('[0-9a-f]{40}',record['evidence_commit']),record['family_id']
                for child in record['named_scenarios']:
                    names=set(re.findall(r'\b(?:Test[A-Za-z0-9_]+|[A-Z][A-Za-z0-9_]*Test)\b',str(child['test_selector'])))
                    assert names, child['test_selector']
                    sources=[]
                    for name in sorted(names):
                        found=list((args.repository/'src/test/java').rglob(name+'.java'))
                        assert found,(record['family_id'],name)
                        sources.extend(str(f.relative_to(args.repository)) for f in found)
                    evidence=child['evidence_source']
                    if not isinstance(evidence,dict):
                        evidence={'references':evidence}
                        child['evidence_source']=evidence
                    evidence['test_sources']=sources
                    # All source and author references remain verbatim; only
                    # local proof links and the record envelope are normalized.
            records.append(record)
    ids=[r['family_id'] for r in records]
    assert len(ids)==len(set(ids))==82
    out={'validation':json.loads(args.validation.read_text()),'records':records}
    (args.source_dir/'reviewed-verdicts.json').write_text(json.dumps(out,indent=2,ensure_ascii=False)+'\n')
    for game,prefix in [('s1','S1_'),('s2','s2-'),('s3k','s3k-')]:
        chosen=[r for r in records if r['family_id'].startswith(prefix)]
        print(game,'families',dict(collections.Counter(r.get('verdict',r.get('kind')) for r in chosen)),
              'scenario assessments',dict(collections.Counter(c['verdict'] for r in chosen for c in r.get('named_scenarios',[]))))

if __name__=='__main__':
    main()
