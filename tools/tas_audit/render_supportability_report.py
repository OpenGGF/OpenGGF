#!/usr/bin/env python3
"""Reconcile repair assessments with executed TAS verdicts; no new support verdicts.
Inputs: final verdict report and three reviewed game assessments. Origin: 2026-10-26
full test reel. Outputs: JSON, portable Markdown and self-contained interactive HTML.
"""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re

LEVELS = {'LOW': 0, 'MEDIUM': 1, 'HIGH': 2, 'VERY_HIGH': 3}
LABELS = {
    'unsigned-x-underflow': 'Unsigned horizontal level wrap',
    'offscreen-solid-dispatch': 'S1 offscreen platform slope lock',
    'pause-vint': 'Global V-int phase during Start pause',
    'hurt-launcher': 'S2 hurt-state OOZ launcher corruption',
    'arz-endpoint-routine-add': 'S2 ARZ final-hit endpoint transition',
    'special-stage-live-work-cost': 'S2 special-stage jump-dependent lag',
    'dez-vram-tile-alias': 'S2 DEZ explosion/animal tile alias',
    'intro-p2-transition': 'S2 P2 Start intro transition',
    'cutscene-slot-six-alias': 'S3K slot-six cutscene button alias',
    'water-skim-slot-lifetime': 'S3K HCZ water-skim slot reuse',
    'reset-power': 'Reset/Power movie actions and lifecycle',
    'soz-ground-response-axis': 'S3K flame-dash reverse slope zip',
    'special-stage-entry-input-latch': 'Blue Sphere pre-load input pulse',
    'mhz-proxy-repeated-damage': 'MHZ early proxy hit / rapid damage',
    's3-competition-host': 'Standalone Sonic 3 Competition mode',
    'standalone-sk-boot': 'Standalone S&K Knuckles title launch',
    'kosm-mode-corruption': 'KosM overflow and corrupted mode switch',
    'ort-backward-cursor-desync': 'ORT allocation-failure duplicate spawn',
}


def build(verdicts, assessments):
    expected = {s['id']: s for s in verdicts['scenarios'] if s['verdict'] == 'unsupported'}
    actual = {}
    groups = defaultdict(list)
    for document in assessments:
        for source in document.get('scenarios', document.get('assessments', [])):
            identifier = source['id']
            if identifier in actual or identifier not in expected:
                raise ValueError(f'Duplicate or unexpected unsupported assessment: {identifier}')
            original = expected[identifier]
            if set(source['publication_ids']) != set(original['publication_ids']):
                raise ValueError(f'Publication attribution changed: {identifier}')
            for key in ('native_owners', 'engine_owners', 'acceptance_tests', 'supportability', 'confidence'):
                if not source.get(key):
                    raise ValueError(f'Missing {key}: {identifier}')
            if source['complexity'] not in LEVELS:
                raise ValueError(f'Unknown complexity: {identifier}')
            row = dict(source)
            row.update(game=original['game'], name=original['name'], current_verdict='unsupported')
            row['repair'] = source.get('proposed_repair', source.get('repair_shape'))
            if not row['repair']:
                raise ValueError(f'Missing repair scope: {identifier}')
            actual[identifier] = row
            groups[row['remediation_group']].append(row)
    if set(actual) != set(expected):
        raise ValueError(f'Missing unsupported assessments: {sorted(set(expected) - set(actual))}')
    result = []
    for key, rows in groups.items():
        if key not in LABELS:
            raise ValueError(f'New remediation group needs a reviewed label: {key}')
        # Cross-game delivery includes the most demanding affected owner. Preserve
        # each original estimate instead of silently replacing its narrower scope.
        lead = max(rows, key=lambda r: LEVELS[r['complexity']])
        result.append({'id': key, 'name': LABELS[key], 'complexity': lead['complexity'],
                       'supportability': lead['supportability'], 'repair': lead['repair'],
                       'games': sorted({r['game'] for r in rows}), 'scenario_ids': [r['id'] for r in rows],
                       'scope_estimates': {r['id']: r['complexity'] for r in rows}})
    result.sort(key=lambda g: (LEVELS[g['complexity']], g['name']))
    return {'schema': 1, 'assessment_only': True, 'repository': verdicts['source_audit']['repository'],
            'assessment_source_commit': '94b2a5bf1daeaaad550ff7b8091a8361d6a9094e',
            'groups': result, 'scenarios': list(actual.values()),
            'counts': {'unsupported_assessments': len(actual), 'remediation_groups': len(result),
                       'complexity': dict(Counter(g['complexity'] for g in result))},
            'limits': ['No gameplay repairs were implemented in this assessment.',
                       'Complexity describes scope and interaction risk, not elapsed-time promises.',
                       'Group complexity includes the highest affected-owner estimate; individual estimates are retained.',
                       'None is demonstrated fundamentally impossible; this is feasibility assessment, not proof of a repair.',
                       'Exact CPU/RAM/VDP emulation is a larger undertaking than bounded native-consequence models.',
                       'The S2 P2 intro causal inlet remains a research prerequisite; do not invent a timer shortcut.',
                       'Existing unsupported verdicts and all full-movie synchronization limits remain unchanged.']}


def markdown(data):
    lines = ['# Unsupported TAS tricks: repair scope', '',
             f"All {len(data['scenarios'])} executed unsupported assessments are accounted for in {len(data['groups'])} shared remediation groups. "
             'These are prospective repairs, not implemented support.', '',
             '**Low:** one bounded owner. **Medium:** several states/consumers with bounded parity work. '
             '**High:** timing, lifecycle, asset aliasing or new session features. '
             '**Very high:** substantial mode support or corruption compatibility.', '',
             '| Repair group | Games | Complexity | Approach |', '| --- | --- | --- | --- |']
    for group in data['groups']:
        lines.append(f"| {group['name']} | {', '.join(group['games'])} | {group['complexity'].replace('_', ' ').title()} | {group['supportability']} |")
    lines.extend(['', 'Cross-game groups use the highest scoped estimate. Unsigned wrap is locally low in S1 '
                  'but medium across shared movement. Pause advancement is locally medium, but high when '
                  'including S2 hardware/PLC admission, fades and duplicate-tick prevention.', ''])
    for group in data['groups']:
        lines.extend([f"## {group['name']}", '', group['repair'], '',
                      'Scenario IDs: ' + ', '.join(f"`{x}`" for x in group['scenario_ids']) + '.', ''])
        for row in data['scenarios']:
            if row['id'] not in group['scenario_ids']:
                continue
            lines.extend([f"- `{row['id']}`: {row['name']}. Confidence: {row['confidence']}. "
                          + 'Publications: ' + ', '.join(f'[{p}M](https://tasvideos.org/{p}M)' for p in row['publication_ids']) + '.',
                          '  Acceptance: ' + ' '.join(row['acceptance_tests'])])
            refs = []
            for owner in row['engine_owners']:
                if isinstance(owner, dict):
                    file, line = owner['file'], owner.get('line', 1)
                else:
                    match = re.match(r'([^: ]+):(\d+)', owner)
                    if not match:
                        raise ValueError(f'Unparsed engine owner: {owner}')
                    file, line = match[1], match[2]
                url = f"{data['repository']}/blob/{data['assessment_source_commit']}/{file}#L{line}"
                refs.append(f"[{Path(file).name}:{line}]({url})")
            lines.extend(['  Engine owners: ' + ', '.join(refs) + '.', ''])
    lines.extend(['', '## Limits', '', *('- ' + s for s in data['limits']), ''])
    return '\n'.join(lines)


PAGE = r'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font:14px/1.55 var(--font-sans,system-ui);color:var(--foreground,#202534);margin:0}h2{font-size:20px;margin:0 0 6px}p{margin:8px 0}small{color:var(--muted-foreground,#617086)}.stats{display:flex;gap:12px;flex-wrap:wrap;margin:16px 0}.stat{padding:16px;background:var(--muted,#f0f2f5);border-radius:var(--radius,8px)}.stat strong{font-size:23px;display:block}.controls{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}input,select,button{font:inherit;padding:8px;color:inherit;background:var(--background,white);border:1px solid var(--border,#bbc3ce);border-radius:6px}input{flex:1;min-width:180px}.row{border-top:1px solid var(--border,#ccd2dc);padding:14px 0}summary{cursor:pointer;font-weight:600}.badge{font-size:11px;padding:3px 7px;border-radius:4px;background:var(--accent,#dce9fa);color:var(--accent-foreground,#223955);margin-left:8px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:12px/1.5 var(--font-mono,monospace);background:var(--muted,#f3f5f8);padding:16px;border-radius:8px}a{color:var(--primary,#2363a5)}.foot{margin:16px 0;color:var(--muted-foreground,#617086)}</style></head><body>
<h2>Unsupported tricks: what would it take?</h2><p>29 tested incompatibility assessments reduce to 18 repair groups. All appear supportable in principle; several require new features or explicit compatibility models. No repairs are included here.</p>
<div class="stats" id="stats"></div>
<small>Complexity is engineering scope and risk, not a delivery estimate. Shared groups include the most demanding affected owner.</small>
<div class="controls"><input id="q" placeholder="Find a trick, game, or owner" aria-label="Search"><select id="level" aria-label="Complexity"><option value="">All complexity</option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>VERY_HIGH</option></select></div><div id="count"></div><div id="rows"></div>
<p class="foot">Low = bounded owner change. Medium = several native states or consumers. High = shared timing/lifecycle or a new session feature. Very high = substantial mode support or corruption compatibility. Full-movie playback remains unverified.</p>
<script>const D=__DATA__;const E=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));const $=id=>document.getElementById(id);
function render(){const q=$('q').value.toLowerCase(),level=$('level').value;const groups=D.groups.filter(g=>(!level||g.complexity===level)&&(!q||JSON.stringify(g).toLowerCase().includes(q)||D.scenarios.filter(s=>g.scenario_ids.includes(s.id)).some(s=>JSON.stringify(s).toLowerCase().includes(q))));$('count').textContent=groups.length+' repair groups';$('rows').innerHTML=groups.map(g=>'<section class="row"><details><summary>'+E(g.name)+'<span class="badge">'+E(g.complexity.replaceAll('_',' '))+'</span></summary><p>'+E(g.repair)+'</p><small>'+E(g.games.join(' · ').toUpperCase())+' · '+E(g.supportability)+' · '+g.scenario_ids.length+' assessment(s)</small>'+D.scenarios.filter(s=>g.scenario_ids.includes(s.id)).map(s=>'<details><summary>'+E(s.id)+' — '+E(s.name)+'</summary><p>Scoped estimate '+E(s.complexity)+'; confidence '+E(s.confidence)+'.</p><p>'+s.publication_ids.map(p=>'<a href="https://tasvideos.org/'+p+'M" target="_blank" rel="noreferrer">'+p+'M</a>').join(' · ')+'</p><pre>'+E(JSON.stringify({repair:s.repair,engine_owners:s.engine_owners,native_owners:s.native_owners,acceptance:s.acceptance_tests,dependencies:s.dependencies||s.dependencies_and_rewind,rewind:s.rewind_impact,other_games:s.other_game_impact,open_question:s.unresolved_design_question||s.unresolved_design_questions},null,2))+'</pre></details>').join('')+'</details></section>').join('')}
$('stats').innerHTML=Object.entries(D.counts.complexity).map(([level,n])=>'<div class="stat"><strong>'+n+'</strong>'+E(level.replaceAll('_',' ').toLowerCase())+'</div>').join('');$('q').addEventListener('input',render);$('level').addEventListener('change',render);render();console.log('repair report',D.scenarios.length,D.groups.length);</script></body></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verdicts', type=Path, required=True)
    parser.add_argument('--assessment', type=Path, action='append', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    data = build(json.loads(args.verdicts.read_text()), [json.loads(p.read_text()) for p in args.assessment])
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out/'supportability.json').write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n')
    (args.out/'supportability.md').write_text(markdown(data))
    payload = json.dumps(data, ensure_ascii=False).replace('<', '\\u003c')
    (args.out/'supportability.html').write_text(PAGE.replace('__DATA__', payload))
    print(json.dumps(data['counts']))


if __name__ == '__main__':
    main()
