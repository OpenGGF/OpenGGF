"""Render reviewed TAS trick verdicts, preserving the original publication audit.

Origin: OpenGGF TAS trick verdict follow-up, 2026-10-26, base c69447d77.
Inputs: the historical audit.json and a parent-reviewed verdicts JSON. This does
not infer verdicts, execute tests, or overwrite the original audit/report.
The reviewed input has validation metadata and records keyed by family_id;
each mechanism record supplies named_scenarios with binary verdicts and proof.
"""

import argparse
import collections
import copy
import json
from pathlib import Path
import re


def build(audit, reviewed):
    validation = reviewed['validation']
    if not re.fullmatch(r'[0-9a-f]{40}', validation['validation_commit']):
        raise ValueError('validation_commit must identify the delivered source')
    records = reviewed['records']
    if isinstance(records, list):
        ids = [r['family_id'] for r in records]
        if len(ids) != len(set(ids)):
            raise ValueError('Duplicate family verdict')
        records = {r['family_id']: r for r in records}
    originals = {t['id']: (g, t) for g in audit['games'] for t in g['tricks']}
    if records.keys() != originals.keys():
        raise ValueError(f"Family coverage differs: missing={originals.keys()-records.keys()}, extra={records.keys()-originals.keys()}")
    games, scenarios, source_notes = [], [], []
    for game in audit['games']:
        publications = []
        movie_ids = {p['publication_id'] for p in game['publications']}
        for p in game['publications']:
            meta = p.get('source_metadata', {})
            publications.append({
                'id': p['publication_id'], 'game': game['key'],
                'title': p.get('title', meta.get('title', str(p['publication_id'])+'M')),
                'current': p['current'], 'url': p['publication_url'],
                'submission_url': p.get('submission_url', meta.get('submission_url')),
                'documented_family_ids': p['documented_trick_ids'],
                'notes': p.get('notes_summary', p.get('author_notes_summary', p.get('summary', ''))),
                'playback_verified': False,
            })
        games.append({'key': game['key'], 'publications': publications,
                      'source_family_count': len(game['tricks'])})
        for original in game['tricks']:
            record = records[original['id']]
            if record.get('kind') == 'source_note':
                # This historical entry was explicitly an undefined-route
                # placeholder. Do not quietly use this escape for real tricks.
                if original['id'] != 'S1_UNSPECIFIED_ROUTE' or not record.get('reason'):
                    raise ValueError('Only the reviewed undefined-route placeholder is a source note')
                source_notes.append({'game': game['key'], 'original': original,
                                     'disposition': copy.deepcopy(record)})
                continue
            children = record['named_scenarios']
            if not children:
                raise ValueError(f"No executed scenario for {original['id']}")
            for index, child in enumerate(children, 1):
                if child['verdict'] not in {'supported', 'unsupported'}:
                    raise ValueError(f"Nonbinary trick verdict in {original['id']}")
                for required in ['name', 'native_expectation', 'test_selector',
                                 'observed_result', 'evidence_source', 'publication_ids']:
                    if not child.get(required):
                        raise ValueError(f"Missing {required} for {original['id']}:{index}")
                if not set(child['publication_ids']).issubset(movie_ids):
                    raise ValueError(f"Wrong-game publication mapping in {original['id']}:{index}")
                commit = child.get('evidence_commit', record.get('evidence_commit'))
                if not commit or not re.fullmatch(r'[0-9a-f]{7,40}', commit):
                    raise ValueError(f"Missing tested commit in {original['id']}:{index}")
                scenarios.append({**copy.deepcopy(child),
                    'id': child.get('id', f"{original['id']}:{index}"),
                    'family_id': original['id'], 'family_name': original['name'],
                    'game': game['key'], 'evidence_commit': commit,
                    'edge_cases': child.get('edge_cases', record.get('edge_cases', [])),
                    'submission_links': original.get('submission_links', []),
                })
    if len({s['id'] for s in scenarios}) != len(scenarios):
        raise ValueError('Duplicate scenario ID')
    return {'validation': validation, 'source_audit': {
                'sha': audit['common']['audited_sha'],
                'date': audit['common']['audited_at'],
                'repository': audit['common']['repository'],
                'attribution': audit['common']['attribution']},
            'games': games, 'scenarios': scenarios, 'source_notes': source_notes,
            'counts': dict(collections.Counter(s['verdict'] for s in scenarios))}


PAGE = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sonic TAS tricks — tested engine support</title><style>
:root{color-scheme:light dark;--fg:#202733;--bg:#fff;--line:#cbd3df;--muted:#596579;--good:#17733f;--bad:#ab3045;--soft:#f2f5fa}
@media(prefers-color-scheme:dark){:root{--fg:#edf1f8;--bg:#171d27;--line:#475266;--muted:#b6c1d1;--good:#80d7a1;--bad:#ffa3af;--soft:#222c3b}}
*{box-sizing:border-box}body{margin:0;font:15px/1.5 system-ui,sans-serif;color:var(--foreground,var(--fg));background:transparent}main{max-width:1080px;margin:auto;padding:20px 0}h1{font-size:27px;margin:0 0 8px}h2{font-size:20px;margin:28px 0 8px}h3{font-size:17px;margin:0}p{margin:8px 0}.muted,small{color:var(--muted-foreground,var(--muted))}a{color:var(--accent,#5989df);overflow-wrap:anywhere}.stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:20px 0}.stat,.notice{border:1px solid var(--border,var(--line));padding:15px;border-radius:10px}.stat strong{display:block;font-size:28px}.notice{background:var(--secondary,var(--soft))}.controls{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}button,input,select{font:inherit;color:inherit;background:var(--background,var(--bg));border:1px solid var(--border,var(--line));border-radius:7px;padding:8px 10px}input{min-width:140px;flex:1}button{cursor:pointer}button[aria-pressed=true]{border-color:var(--accent,#5989df);background:var(--secondary,var(--soft))}button:focus-visible,input:focus-visible,select:focus-visible,summary:focus-visible{outline:2px solid var(--accent,#5989df);outline-offset:3px}.badge{font-weight:650;font-size:13px;padding:3px 7px;border:1px solid currentColor;border-radius:5px;white-space:nowrap}.supported{color:var(--good)}.unsupported{color:var(--bad)}.head{display:flex;gap:15px;justify-content:space-between;align-items:start}.row{border-top:1px solid var(--border,var(--line));padding:16px 0}.row p,.row dd{overflow-wrap:anywhere}details{margin:10px 0}summary{cursor:pointer;font-weight:600}details>div{border-left:2px solid var(--border,var(--line));padding-left:14px;margin-top:10px}dl{margin:0}dt{font-size:12px;font-weight:650;color:var(--muted-foreground,var(--muted));margin-top:12px}dd{margin:3px 0}ul{padding-left:22px}li{margin:6px 0}code{font:12px ui-monospace,monospace;overflow-wrap:anywhere}table{border-collapse:collapse;width:100%;margin:14px 0}td,th{text-align:left;border-bottom:1px solid var(--border,var(--line));padding:9px 6px}th{font-size:13px}footer{border-top:1px solid var(--border,var(--line));margin-top:25px;padding-top:15px;font-size:12px}.pages{align-items:center}[hidden]{display:none!important}@media(max-width:500px){main{padding:12px 0}.stat{padding:10px}.stat strong{font-size:24px}.head{display:block}.badge{display:inline-block;margin-top:7px}table{font-size:13px}}
</style></head><body><main>
<h1>Sonic TAS tricks: tested engine support</h1>
<p>Each named trick below has an executed <strong>supported</strong> or <strong>unsupported</strong> verdict. Tests use representative setups; route and revision edge cases are recorded with the evidence.</p>
<div class="stats" id="stats"></div><div id="summary" class="notice"></div>
<div id="table"></div>
<details><summary>What the tests establish</summary><div id="method"></div></details>
<h2>Explore the evidence</h2><div class="controls" role="group" aria-label="View"><button id="tricks" aria-pressed="true">Named tricks</button><button id="movies" aria-pressed="false">All publications</button></div>
<div class="controls"><select id="game" aria-label="Game"><option value="all">All games</option><option value="s1">Sonic 1</option><option value="s2">Sonic 2 / KiS2</option><option value="s3k">Sonic 3 / S&amp;K / S3&amp;K</option></select><select id="status" aria-label="Support"><option value="all">Both verdicts</option><option value="supported">Supported</option><option value="unsupported">Unsupported</option></select><select id="era" aria-label="Publication status" hidden><option value="all">Current and obsolete</option><option value="current">Current</option><option value="obsolete">Obsolete</option></select><input id="query" type="search" aria-label="Search" placeholder="Find a trick, test, zone or publication…"></div>
<p class="muted" id="count" aria-live="polite"></p><section id="results"></section>
<div class="controls pages"><button id="previous">Previous</button><span id="page"></span><button id="next">Next</button></div>
<details id="notesBox"><summary>Source notes without a defined mechanism</summary><div id="notes"></div></details>
<footer id="footer"></footer></main>
<script id="data" type="application/json">__DATA__</script><script>
const D=JSON.parse(document.getElementById('data').textContent),$=id=>document.getElementById(id),N={s1:'Sonic 1',s2:'Sonic 2 / KiS2',s3k:'Sonic 3 / S&K / S3&K'};
const E=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const link=(url,text)=>'<a href="'+E(url)+'" target="_blank" rel="noopener">'+E(text)+'</a>';
function rich(value,commit){let s=String(value??''),out=[],last=0,m,rx=/(https?:\/\/[^\s<>"'`]+)|((?:src|docs|tools)\/[^\s<>"'`]+?\.(?:java|md|py|json|xml)(?::\d+(?:[-–]\d+)?)?)/g;while((m=rx.exec(s))){out.push(E(s.slice(last,m.index)));let url=m[0];if(m[2]){const parts=m[2].match(/^(.*?)(?::(\d+)(?:[-–]\d+)?)?$/);url=D.source_audit.repository+'/blob/'+commit+'/'+parts[1]+(parts[2]?'#L'+parts[2]:'')}out.push(link(url,m[0]));last=rx.lastIndex}out.push(E(s.slice(last)));return out.join('').replaceAll('\n','<br>')}
function detail(value,commit=D.validation.validation_commit){if(value==null)return '';if(Array.isArray(value))return value.length?'<ul>'+value.map(v=>'<li>'+detail(v,commit)+'</li>').join('')+'</ul>':'';if(typeof value==='object')return '<dl>'+Object.entries(value).map(([k,v])=>'<dt>'+E(k.replaceAll('_',' '))+'</dt><dd>'+detail(v,commit)+'</dd>').join('')+'</dl>';return rich(value,commit)}
const movies=D.games.flatMap(g=>g.publications),badge=v=>'<span class="badge '+v+'">'+(v==='supported'?'Supported':'Unsupported')+'</span>';
$('stats').innerHTML='<div class="stat"><strong>'+D.counts.supported+'</strong>supported scenarios</div><div class="stat"><strong>'+D.counts.unsupported+'</strong>unsupported scenarios</div><div class="stat"><strong>'+movies.length+'</strong>publications reviewed</div>';
$('summary').innerHTML='<strong>'+E(D.validation.summary)+'</strong><p>A passing limitation regression confirms the stated engine mismatch. It does not mean that trick works.</p><small>'+link(D.validation.validation_url,'Commands, results and delivery record')+' · '+link(D.source_audit.repository+'/commit/'+D.validation.validation_commit,D.validation.validation_commit.slice(0,12))+'</small>';
$('table').innerHTML='<table><thead><tr><th>Game</th><th>Supported</th><th>Unsupported</th><th>Publications</th></tr></thead><tbody>'+D.games.map(g=>'<tr><td>'+E(N[g.key])+'</td><td>'+D.scenarios.filter(s=>s.game===g.key&&s.verdict==='supported').length+'</td><td>'+D.scenarios.filter(s=>s.game===g.key&&s.verdict==='unsupported').length+'</td><td>'+g.publications.length+'</td></tr>').join('')+'</tbody></table><p class="muted">Counts are named scenario assessments within each source family, not unique mechanisms or test methods. Shared mechanisms can recur under different historical families. The original '+D.games.reduce((n,g)=>n+g.source_family_count,0)+' audit entries are preserved as family references; broad families can contain several distinct tricks.</p>';
$('method').innerHTML='<p><strong>Supported:</strong> the engine reproduces a named native mechanism from a valid representative entry through its observable exit, with a relevant control.</p><p><strong>Unsupported:</strong> an executed scenario reaches a specific native outcome the engine contradicts, or exercises a required capability that is unavailable.</p><p>Full published movies have not been replayed or certified. A mechanism verdict does not establish frame-perfect synchronization for every route or ROM revision. Publication links identify notes associated with that scenario; a family-level association does not prove that every movie uses every variant.</p>'+detail(D.validation.run_results);
let mode='tricks',offset=0;const size=10;
function trickBody(s){return '<dl><dt>Native expectation</dt><dd>'+detail(s.native_expectation,s.evidence_commit)+'</dd><dt>Observed engine result</dt><dd>'+detail(s.observed_result,s.evidence_commit)+'</dd><dt>Executed test</dt><dd>'+detail(s.test_selector,s.evidence_commit)+'</dd><dt>Evidence</dt><dd>'+detail(s.evidence_source,s.evidence_commit)+'</dd><dt>Tested commit</dt><dd>'+link(D.source_audit.repository+'/commit/'+s.evidence_commit,s.evidence_commit)+'</dd><dt>Associated publications</dt><dd>'+s.publication_ids.map(p=>link('https://tasvideos.org/'+p+'M',p+'M')).join(' · ')+'</dd><dt>Original family submission notes</dt><dd>'+s.submission_links.map(u=>link(u,u.split('/').pop())).join(' · ')+'</dd></dl>'+(s.edge_cases?.length?'<details><summary>Scope and edge cases</summary><div>'+detail(s.edge_cases,s.evidence_commit)+'</div></details>':'')}
function movieBody(p){const items=D.scenarios.filter(s=>s.game===p.game&&s.publication_ids.includes(p.id)),blocked=items.filter(s=>s.verdict==='unsupported');return '<p>'+link(p.submission_url,'Original submission and author notes')+'</p><p>'+ (blocked.length?blocked.length+' tested incompatibility assessment'+(blocked.length===1?'':'s')+' among the associated mechanisms.':items.length?'No unsupported verdict among the associated tested mechanisms.':p.documented_family_ids.length?'No named scenario is attributed to this publication in this update.':'These author notes do not define a testable mechanism.')+'</p><p>Representative mechanisms associated with these notes are listed below. Full-movie playback has not been tested.</p>'+detail(p.notes)+'<ul>'+items.map(s=>'<li>'+badge(s.verdict)+' <strong>'+E(s.name)+'</strong><br><small>'+E(s.family_id)+'</small></li>').join('')+'</ul>'+(items.length?'':'<p>No verdict is inferred from predecessor movies; the original notes and their limits remain preserved.</p>')}
function render(){const game=$('game').value,status=$('status').value,era=$('era').value,q=$('query').value.toLowerCase().trim();let rows=(mode==='tricks'?D.scenarios:movies).filter(x=>(game==='all'||x.game===game)&&(mode!=='tricks'||status==='all'||x.verdict===status)&&(mode!=='movies'||era==='all'||(x.current?'current':'obsolete')===era)&&(!q||JSON.stringify(x).toLowerCase().includes(q)));const total=rows.length;offset=Math.min(offset,Math.max(0,Math.ceil(total/size)-1)*size);$('count').textContent=total+' '+(mode==='tricks'?'named tricks':'publications');$('page').textContent=total?(offset+1)+'–'+Math.min(offset+size,total)+' of '+total:'0 results';$('previous').disabled=offset===0;$('next').disabled=offset+size>=total;rows=rows.slice(offset,offset+size);$('results').innerHTML=rows.length?rows.map(x=>'<article class="row"><div class="head"><h3>'+(mode==='tricks'?E(x.name):link(x.url,x.title))+'</h3>'+(mode==='tricks'?badge(x.verdict):'')+'</div><small>'+E(N[x.game])+' · '+E(mode==='tricks'?x.family_id+' · '+x.family_name:x.id+'M · '+(x.current?'current':'obsolete'))+'</small>'+(mode==='tricks'?'<p>'+rich(x.observed_result,x.evidence_commit)+'</p>':'')+'<details><summary>'+(mode==='tricks'?'Test, native expectation and scope':'Author notes and tested mechanisms')+'</summary><div>'+(mode==='tricks'?trickBody(x):movieBody(x))+'</div></details></article>').join(''):'<p>No matching entries.</p>'}
function setMode(value){mode=value;offset=0;$('tricks').setAttribute('aria-pressed',value==='tricks');$('movies').setAttribute('aria-pressed',value==='movies');$('status').hidden=value==='movies';$('era').hidden=value!=='movies';render()}
for(const id of ['game','status','era','query'])$(id).addEventListener(id==='query'?'input':'change',()=>{offset=0;render()});$('tricks').onclick=()=>setMode('tricks');$('movies').onclick=()=>setMode('movies');$('previous').onclick=()=>{offset-=size;render()};$('next').onclick=()=>{offset+=size;render()};
$('notesBox').hidden=!D.source_notes.length;$('notes').innerHTML=D.source_notes.map(n=>'<p><strong>'+E(n.original.name)+'</strong> · '+E(n.original.id)+'</p>'+detail(n.disposition)+'<p>'+n.original.submission_links.map(u=>link(u,u.split('/').pop())).join(' · ')+'</p>').join('');
$('footer').innerHTML='Source audit: '+link(D.source_audit.repository+'/tree/'+D.source_audit.sha,D.source_audit.sha.slice(0,12))+'. Publication census and original note records are preserved; this update changes the engine evidence.<p>'+E(D.source_audit.attribution.text)+' '+link(D.source_audit.attribution.license_url,'TASVideos site license')+'</p>';render();
</script></body></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', type=Path, required=True)
    parser.add_argument('--reviewed', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    data = build(json.loads(args.audit.read_text()), json.loads(args.reviewed.read_text()))
    payload = json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('<', '\\u003c')
    page = PAGE.replace('__DATA__', payload)
    if len(page) > 512000:
        raise ValueError('HTML exceeds the display tool budget; reduce duplicated provenance')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'verdict-report.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    (args.output_dir / 'verdict-report.html').write_text(page)
    print(f"Wrote {len(data['scenarios'])} named scenarios: {data['counts']}; {len(page)} HTML characters")


if __name__ == '__main__':
    main()
