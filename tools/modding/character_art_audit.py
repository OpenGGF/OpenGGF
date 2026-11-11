#!/usr/bin/env python3
"""Purpose: reproducible coverage report from TestCharacterArtCoverageAudit output.
Inputs: ROM-gated JSON probe output and current example/source/disassembly metadata.
Origin: original-character art coverage research, 2026-11-09. No ROM pixels are decoded.
Run from repository root; output stays outside Git. Native labels are reference evidence,
not runtime assets. Counts are resolution obligations, not unique drawings or route tests.
"""
import argparse, collections, csv, html, json, pathlib, re
ROOT = pathlib.Path.cwd()
ART = ROOT / 'examples/mica-and-talon/art'
PRODUCTION = ROOT / 'examples/mica-and-talon/production'

def load(p): return json.loads(pathlib.Path(p).read_text())
def citation(path, label):
    p=pathlib.Path(path)
    if p.is_file():
        for n,line in enumerate(p.read_text(errors='replace').splitlines(),1):
            if label and label in line: return f'{label} ({p}:{n})'
    return f'{label} ({path}; label not located)'
def writecsv(path, rows):
    keys=list(dict.fromkeys(k for row in rows for k in row))
    with path.open('w') as f:
        w=csv.DictWriter(f,keys);w.writeheader();w.writerows(rows)
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('output',type=pathlib.Path);args=ap.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=True)
    rows=[];inventory=[];backlog=[]
    def add(c,g,s,f,status,target='',provenance='',rank='rare/cutscene',evidence='',notes=''):
        rows.append(dict(character=c.upper(),game=g.upper().replace('S3K_LOCKED_ON','S3K'),surface=s,native_frame_or_animation=str(f),status=status,target=str(target),provenance=provenance,visibility=rank,evidence=evidence,notes=notes))
    for c in ['mica','talon']:
        manifest=load(ART/c/'sheet/frames.yaml');sheet=load(ROOT/f'target/mica-and-talon/generated/{c}-sheet.yaml')
        # Provenance is attached to actual source IDs, never inferred from a directory name.
        prov=collections.defaultdict(list)
        files=list((PRODUCTION/c).rglob('native-pose*.json')) if c=='mica' else [PRODUCTION/c/'provenance.json']
        for p in files:
            d=load(p); fs=d.get('frames',[])
            if isinstance(fs,dict): fs=[dict(v,id=k) for k,v in fs.items()]
            for f in fs:
                key=str(f.get('id',f.get('file',''))).removesuffix('.png');prov[key].append(str(p.relative_to(ROOT)))
        def sourceinfo(indices):
            ids=[];ps=[]
            for i in indices:
                if 0<=i<len(manifest['frames']):
                    frame=manifest['frames'][i];key=frame['id'];ids.append(key)
                    ps.extend(prov.get(key,['older/other method; see frames.yaml notes']))
            return ' '.join(ids),'; '.join(sorted(set(ps)))
        for g in ['S1','S2','S3K']:
            p=out/f'{c}-{g}.json'
            if not p.exists():
                if c=='talon' and g=='S1': add(c,g,'native Tails','all','N/A',notes='S1 has no native Tails. Mod flight/sidekick needs donor or engine-defined contract; not evidence of art completion.')
                else: raise SystemExit(f'Missing required probe {p}')
                continue
            d=load(p);shift=d['relocation'];native=d['nativeScripts'];bound=d['resolutions'];names=d['hostNames']
            for sid,script in native.items():
                b=bound[sid]['boundScript'];frames=[] if b is None else [i-shift for i in b['frames']]
                matched=[k for k,a in d['sourceAnimations'].items() if [d['packedIndices'].get(str(i),i) for i in frames]==[s['frameIndex'] for s in a['steps']]]
                # Bound resolution is observed. Name equivalence alone cannot establish Tails pose coverage.
                native_names=names.get(sid,[]); authored=any(k in native_names for k in matched)
                family=d.get('familyBindings',{}).get(sid,{})
                phase_keys=[f"family-{str(family.get('family','')).lower().replace('_','-')}/{phase}" for phase in family.get('phases',[])]
                family_authored=bool(phase_keys) and all(key in d['nativeAliases'] for key in phase_keys)
                if family_authored:
                    authored=True
                    matched=['family:'+family['family']]
                status='authored' if authored else 'fallback'
                target,ps=sourceinfo([d['nativeAliases'][key] for key in phase_keys] if family_authored else [d['packedIndices'].get(str(i),i) for i in frames])
                rank='normal early-zone play' if set(native_names)&{'WALK','RUN','ROLL','WAIT','PUSH','LOOK_UP','DUCK','SKID','STOP','SPINDASH','BALANCE','BALANCE2','SPRING','HURT','DEATH'} else 'zone-specific / rare; see users inventory'
                add(c,g,'animation',f'{int(sid):02X} '+('/'.join(native_names) or 'unmapped native ID'),status,
                    ('/'.join(matched) or 'missing bound script')+': '+target,ps,rank,
                    f'{p.name}: nativeScripts.{sid}; resolutions.{sid}',
                    'native frames='+str(script['frames'])+'; published='+str(bound[sid]['displayedFrames'])+'; end='+str(script['endAction'])+'/'+str(script['endParam'])+
                    '; authored means supplied canonical cycle, not one drawing for every distinct native frame'+('; Tails $1F is high-speed run, $29 is transformation ($EB/$EC/$ED), absent canonical mapping' if c=='talon' and g=='S3K' and int(sid) in [31,41] else ''))
            # Every loaded mapping is inventoried independently, without claiming all are direct setters.
            for f in range(d['mappingCount']):
                users=[sid for sid,s in native.items() if f in s['frames']]
                inventory.append(dict(character=c.upper(),game=g,bank='normal-player',frame=f'{f:02X}',script_ids=' '.join(users),evidence=f'{p.name}: rawTargets.{f}',actual_raw_targets=str(d['rawTargets'][str(f)])))
            for f,pose in sorted(d['nativePoses'].items(),key=lambda x:int(x[0])):
                alias=sheet['nativeFrames'].get(pose['key']);target=d['rawTargets'][f]
                ids,ps=sourceinfo(target)
                add(c,g,'direct mapping',f'{int(f):02X} '+pose['key'],'native alias' if alias is not None else 'fallback',ids,ps,
                    'zone-specific: '+('S2 spiral/flip-angle selector' if c=='talon' and g=='S2' and pose['key'].startswith('gravity-loop/') else zone(pose['key'])),f'{p.name}: nativePoses.{f}; rawTargets.{f}',
                    'canonical default='+pose['fallback']+'; see design object-owned mapping vocabulary for consumers')
            for sid,s in d.get('superScripts',{}).items():
                row=d.get('poweredCoverage',{}).get(sid,{})
                add(c,g,'Super/Hyper animation',f'{int(sid):02X}',row.get('status','fallback'),row.get('reason','normal creator canonical sheet / owned palette'),rank='rare/powered play',evidence=f'{p.name}: poweredCoverage.{sid}',notes='native powered frames='+str(s['frames'])+'; bound frames='+str(row.get('frames',[]))+'; Hyper shares powered geometry.')
        # Source drawings are listed separately, excluding them from obligation totals.
        for i,f in enumerate(manifest['frames']):
            inventory.append(dict(character=c.upper(),game='shared',bank='authored source',frame=i,script_ids=f.get('animation',''),evidence='; '.join(prov.get(f['id'],['older/other method; frames.yaml notes'])),actual_raw_targets=f['id'],image=str((ART/c/'sheet'/f['file']).relative_to(ROOT))))
    # Presentation manifests describe supplied original frames; native Tails banks retain their own selectors.
    def surface(g,s,f,base,meta):
        image=base/f.get('file',''); label=f.get('native_source_label',meta.get('native_source_label',''))
        ev=citation(f.get('mapping_source',meta.get('mapping_source',meta.get('source',''))),label)
        records=list(base.glob('native-pose*.json'));ps='; '.join(str(x.relative_to(ROOT)) for x in records) or 'older method / manifest original indexed rows'
        sentinel=(not f.get('pieces',[1])) or f.get('native_dimensions')==[0,0]
        add(meta.get('character','mica'),g,s,f.get('id',f.get('index',f.get('surface','image'))),'N/A' if sentinel else 'authored',str(image.relative_to(ROOT)),ps,'rare/cutscene',ev,'empty native sentinel' if sentinel else '')
    for rel in ['continue/continue.yaml','portrait/portrait.yaml','snowboard/snowboard.yaml','endings/s1/s1.yaml','endings/s2/s2.yaml','endings/s3k/s3k.yaml','wave3/wave3.yaml','presentation/presentation.yaml']:
        p=PRODUCTION/'mica'/rel;d=load(p);base=p.parent
        if 'games'in d:
            for game in d['games']:
                for f in game['frames']:surface(game['game'],'continue/'+f['bank'],f,base,game)
        elif 'surfaces'in d:
            for s in d['surfaces']:
                for f in s.get('frames',[s] if 'file'in s else []):surface(s['game'],s['surface'],f,base,s)
        else:
            g=d.get('game','s3k')
            for f in d.get('frames',[]):surface(g,f.get('frame_set',rel.split('/')[0]),f,base,d)
    # Reel symbol obligations are independent of bonus-stage player poses.
    # Orientation obligations are separate from native mapping-ID coverage. Legacy
    # S3K creator selectors were already independent; their baked mirrored lighting
    # is not evidence that a final-orientation drawing is explicitly bound.
    orientation_java = ROOT/'examples/mica-and-talon/src/main/java/micaandtalon/OrientedStageArt.java'
    orientation_bindings = orientation_java.read_text() if orientation_java.exists() else ''
    for c in ['mica', 'talon']:
        manifest_path = PRODUCTION/c/'consistent-stage/native-pose-generation.json'
        supplied = load(manifest_path)['frames'] if manifest_path.exists() else []
        obligations = [('s3k', i, 'h') for i in [6, 7, 8]]
        obligations += [('s2', i, orientation) for i in range(12) for orientation in ['h', 'v', 'hv']]
        obligations += [('s2', i, 'h') for i in range(12, 16)]
        for game, selector, orientation in obligations:
            frame = next((f for f in supplied if f['game']==game and f['id']==selector and f['orientation']==orientation), None)
            surface_name = 'S2_HALF_PIPE' if game=='s2' else 'S3K_BLUE_SPHERE'
            binding = f'new Drawing("{c}", StageSpriteSet.Surface.{surface_name}, {selector}, "{orientation}",'
            authored = frame is not None and (manifest_path.parent/frame['file']).is_file() and binding in orientation_bindings
            add(c, game, 'special-stage final orientation', f'{selector:02X}/{orientation}',
                'authored' if authored else 'fallback', frame['path'] if authored else 'ordinary selector/native flip',
                str(manifest_path.relative_to(ROOT)) if authored else '', rank='normal early-zone special stage',
                evidence=citation('docs/s2disasm/s2.asm', 'SSPlayer_Animate:') if game=='s2' else citation('docs/skdisasm/sonic3k.asm', 'byte_91E8:'),
                notes='Additional orientation obligation; does not add native frame IDs. Native animation clocks/selectors unchanged.')
    for c in ['mica', 'talon']:
        for g in ['S2', 'S3K']:
            path = ART/c/'reels/normal.png'
            add(c,g,'slot reel portrait','lead/sidekick face','authored' if path.is_file() else 'fallback',
                str(path.relative_to(ROOT)),str((path.parent/'provenance.json').relative_to(ROOT)),
                evidence='SlotMachine_GetPixelRow (docs/s2disasm/s2.asm:59778-59790)' if g=='S2' else 'sub_4C77C (docs/skdisasm/sonic3k.asm:100060-100075)',
                notes='Selected character in native Sonic/Tails symbol; TALON solo owns lead and Tails symbols; rewards unchanged.')
        for g in ['S1', 'S2', 'S3K']:
            title_root = ART/c/'titles'
            manifest = PRODUCTION/c/'titles/manifest.json'
            bank = load(manifest).get('hosts', {}).get(g) if manifest.is_file() else None
            complete = bank is not None and bool(bank.get('files')) and all((manifest.parent/f).is_file() for f in bank['files'])
            image = title_root/('s1-6.png' if g=='S1' else 's2-18.png' if g=='S2' else 's3k-plane-13-left.png')
            add(c,g,'title mascot','native composition','authored' if complete else 'fallback',
                str(image.relative_to(ROOT)),str((title_root/(g.lower()+'-provenance.json')).relative_to(ROOT)),
                notes='Configured launch default owns native mascot selectors, hotspots and local palettes; S3K plane masks retain logo/emblem; native timing/occlusion remain host-owned.' if complete else 'Incomplete or missing native title bank.')
    powered=ART/'mica/reels/powered.png'
    add('mica','S3K','slot reel portrait','Super Sonic symbol 7','authored' if powered.is_file() else 'fallback',
        str(powered.relative_to(ROOT)),str((powered.parent/'powered-provenance.json').relative_to(ROOT)),
        evidence='ArtUnc_SlotOptions (docs/skdisasm/sonic3k.asm:201630)',notes='Original Gilded portrait; static symbol, native reward retained.')
    powered=ART/'talon/reels/powered.png'
    add('talon','S3K','slot reel portrait','Super Sonic symbol 7','authored' if powered.is_file() else 'fallback', str(powered.relative_to(ROOT)), str((powered.parent/'powered-provenance.json').relative_to(ROOT)), notes='Solar palette alias of generated TALON portrait when leading solo; no hedgehog spikes.')
    for rel in ['continue/continue.yaml', 'portrait/portrait.yaml', 'presentation/presentation.yaml']:
        p=PRODUCTION/'talon'/rel
        if not p.exists(): continue
        d=load(p);base=p.parent
        if 'games' in d:
            for game in d['games']:
                for f in game['frames']: surface(game['game'],'continue/'+f['bank'],f,base,dict(game,character='talon'))
        elif 'surfaces' in d:
            for entry in d['surfaces']: surface(entry['game'],entry['surface'],entry,base,dict(entry,character='talon'))
        else:
            for f in d.get('frames',[]): surface(d.get('game','s3k'),'portrait',f,base,dict(d,character='talon'))
    talon_contract=(ROOT/'examples/mica-and-talon/src/main/java/micaandtalon/TalonPresentation.java').exists()
    talon_halfpipe={f['id']:f for f in load(PRODUCTION/'talon/wave3/halfpipe.json')} if talon_contract else {}
    talon_blue={f['id']:f for f in load(PRODUCTION/'talon/wave3/bluesphere.json')} if talon_contract else {}
    for g in ['S1','S2','S3K']:
        for c in ['mica','talon']:
            add(c,g,'HUD/results names','composed text','authored' if c=='mica' or talon_contract else 'fallback','ROM glyph composition + supplements',rank='normal early-zone play',evidence='MicaPresentation.java; CharacterPresentation consumers',notes='text composition is not an animation frame')
        if g=='S1':
            for character in ['mica','talon']:
                add(character,g,'maze special stage','ROLL','native alias','canonical ROLL',rank='normal early-zone special stage',evidence='StageCharacterPresentation.playable / S1 canonical stage art')
            add('talon',g,'native presentation','all','N/A',notes='S1 has no native Tails bank; TALON supplies mod-defined Sonic-host surface selectors separately.')
        else:
            add('talon',g,'presentation contract','all','authored' if talon_contract else 'fallback','TalonPresentation.java',rank='normal early-zone play',notes='Native actor ownership selects solo Tails-role main or active follower independently from the Sonic leader.')
    add('mica','S2','ending search photos','1..3','N/A',notes='Active first sidekick owns these selectors; photo 0 remains native. MICA may own them only when selected as sidekick, outside agreed Sonic lead role.')
    def talon_ending(group, frame):
        path=ART/'talon/endings'/f'{group}-{frame:02x}.png'
        return path.is_file()
    for f in [1,2,3]:add('talon' ,'S2','ending search photos',f,'authored' if talon_ending('photos',f) else 'fallback','TalonEndings S2_PHOTOS',evidence=citation('docs/s2disasm/s2.asm','ArtNem_EndingPics'),notes='Sidekick-owned search photographs; TalonEndings awaits presentation-lane wiring.')
    add('talon','S2','Tornado pilot','native pilot sequence','native alias' if talon_ending('pilot',16) else 'fallback','native 24-step / nine-clock sequence',evidence=citation('docs/s2disasm/s2.asm','ObjB2_Animate_Pilot'),notes='S2_TORNADO_PILOT supplies cockpit crops in ending and SCZ/WFZ. Native DPLC/timing retained; presentation wiring pending.')
    add('talon','S3K','Tornado pilot','0C','native alias' if talon_ending('pilot',16) else 'fallback',
        'TalonEndings S3K_TORNADO_PILOT: S2 side-view cockpit crop; hotspot 12,8',
        evidence='EndingKnucklesFlightChildObjectInstance PILOT',notes='Native S3K/S2 pilot alpha/index geometry matches; native palettes differ; first configured sidekick owns this reunion pilot; presentation wiring pending.')
    add('mica','S3K','ICZ snowboard' ,'Tails route','N/A',notes='MICA uses Sonic rider bank; TALON uses Tails route and needs no Sonic snowboard art.')
    add('talon','S3K','ICZ snowboard','all','N/A',notes='Native Tails route skips Sonic rider choreography; do not generate 13 falcon snowboard frames by assumption.')
    # Tails-specific independent banks; native split maps establish exact selector counts.
    for g,surface_name,path,na in [
        ('S3K','Blue Sphere player','docs/skdisasm/General/Sprites/Tails/Map - SStage Tails.asm',False),
        ('S3K','Blue Sphere tail','docs/skdisasm/General/Sprites/Tails/Map - SStage Tails tails.asm',True),
        ('S3K','Blue Sphere result pose','docs/skdisasm/General/Blue Sphere/Map - Tails Pose.asm',False),
        ('S3K','competition miniature','docs/skdisasm/General/Sprites/Tails/Map - Tails 2P.asm',False),
        ('S3K','separate tails object','docs/skdisasm/General/Sprites/Tails/Map - Tails tails.asm',True),
        ('S3K','competition tail','docs/skdisasm/General/Sprites/Tails/Map - Tails tail 2P.asm',True)]:
        p=pathlib.Path(path); entries=[]
        for n,line in enumerate(p.read_text().splitlines(),1):
            m=re.search(r'dc.w\s+([\w.]+)-Map_',line)
            if m: entries.append((n,m[1]))
            elif entries:break
        for i,(n,label) in enumerate(entries):
            status,target,notes='fallback','stock Tails','Independent mapping bank; not normal player IDs'
            if na:
                status,target,notes='N/A','wings integrated in body','No separate appendage pixels; body wings own this motion'
            elif surface_name=='Blue Sphere player' and i==0:
                status,target,notes='N/A','empty native sentinel','Native mapping 0 is blank; omitted from runtime set'
            elif surface_name=='Blue Sphere result pose':
                status,target,notes='N/A','authored reserve pose','Standalone Blue Sphere completion host is not implemented; reserve original art is in art/talon/wave3'
            elif surface_name=='Blue Sphere player' and i in talon_blue:
                status,target='authored','art/talon/wave3'
            add('talon',g,surface_name,f'{i:02X}',status,target,
                rank='competition/2P only' if 'competition'in surface_name else 'rare/cutscene',
                evidence=f'{label} ({path}:{n})',notes=notes)
    # Sonic miniature bank is independent of MICA's full-size and stage mappings.
    path='docs/skdisasm/General/Sprites/Sonic/Map - Sonic 2P.asm'
    entries=[]
    for n,line in enumerate(pathlib.Path(path).read_text().splitlines(),1):
        m=re.search(r'dc.w\s+([\w.]+)-Map_',line)
        if m: entries.append((n,m[1]))
        elif entries: break
    for i,(n,label) in enumerate(entries):
        add('mica','S3K','competition miniature',f'{i:02X}','fallback','stock Sonic',
            rank='competition/2P only',evidence=f'{label} ({path}:{n})',
            notes='Independent mapping bank; not normal player IDs')
    # Competition manifests contain native miniature selectors, never full-size aliases.
    for character in ['mica','talon']:
        manifest_path=PRODUCTION/character/'competition/competition.json'
        if not manifest_path.exists(): continue
        manifest=load(manifest_path)
        by_id={f['id']:f for f in manifest['frames']}
        for row in rows:
            if row['character']!=character.upper() or row['game']!='S3K' or row['surface']!='competition miniature': continue
            frame=by_id.get(int(row['native_frame_or_animation'],16))
            if frame is None: continue
            image=manifest_path.parent/frame['file']
            if not image.is_file(): raise SystemExit(f'Missing competition image {image}')
            row['status']=frame['status']
            row['target']=str(image.relative_to(ROOT))
            row['provenance']=str((manifest_path.parent/'generation.json').relative_to(ROOT))
            row['notes']='native miniature bank, per-frame hotspot; $27 aliases $26, $00 is an empty sentinel'
    # S2 uses the shared Tails mapping bank for its separate appendage object.
    for f in [0,*range(9,14),*range(0x49,0x59),*range(0x81,0x85),*range(0x87,0x8B)]:
        add('talon','S2','separate tails object',f'{f:02X}','N/A','integrated body wings',
            evidence=citation('docs/s2disasm/s2.asm','Obj05AniData'),notes='Swish/flick, directional walk, spindash, skid, push and hanging tail cycles; body motion still required')
    for f in range(21):
        add('talon','S2','half-pipe tail',f'{f:02X}','N/A','integrated body wings',
            evidence=citation('docs/s2disasm/s2.asm','Obj88_MapUnc_34DA8'),notes='Separate special-stage appendage bank, 21 entries; body set must carry wing cues')
    for g in ['S2','S3K']:
        add('mica',g,'Tails-only actor and appendage roles','all','N/A',notes='Sonic-archetype lead does not own native Tails appendage or Tails-alone actor banks; team sidekick owns its own art')
    for i in range(18):
        add('talon','S2','half-pipe player',f'{i:02X}','authored' if i in talon_halfpipe else 'fallback','art/talon/wave3' if i in talon_halfpipe else 'stock Tails',rank='normal early-zone special stage',
            evidence=citation('docs/s2disasm/s2.asm','Obj10_MapUnc_34B3E'),notes='Body set 0..17; wings must replace separate tail motion in body poses')
    for i,file in enumerate(["roll-00", "roll-01", "roll-02", "duck-00", "family-balance-00"]):
        add('talon','S3K','slot machine player',f'{0x96+i:02X}','native alias',f'art/talon/frames/{file}.png',
            evidence=citation('docs/skdisasm/General/Sprites/Tails/Anim - Tails.asm','AniTails02:'),
            notes='Approved core aliases; three native roll phases plus two FixBugs=0 goal-fade mapping selectors, no separate tail')
    for f in [1,2,3,4,0x10]:
        add('talon','S2','Tornado pilot mapping',f'{f:02X}','authored' if talon_ending('pilot',f) else 'fallback','TalonEndings S2_TORNADO_PILOT',
            evidence=citation('docs/s2disasm/s2.asm','Tails_pilot_frames'),notes='Five generated 24x16 cockpit crops; native 24-step/nine-clock sequence retained in ending and SCZ/WFZ; TalonPresentation merge pending')
    for f in [0x18,0x19,9,10,11,15,16,17]:
        add('talon','S2','ending object bank',f'{f:02X}','authored' if talon_ending('s2',f) else 'fallback','TalonEndings S2_ENDING',
            evidence=citation('docs/s2disasm/s2.asm','ObjCE_Init'),notes='TalonEndings: solo Tails route rotation/final leap; separate ObjCF bank; presentation merge pending')
    for f in [3,4]:
        add('talon','S3K','ending reunion pose',f'{f:02X}','authored' if talon_ending('s3k',f) else 'fallback','TalonEndings S3K_NORMAL/POWERED_POSE',
            evidence=citation('docs/skdisasm/sonic3k.asm','loc_5E334'),notes='TalonEndings: Tails-alone 3 then 4; team Sonic/MICA reunion has no companion portrait; presentation merge pending')
    for g in ['S1','S2','S3K']:
        for c in ['mica','talon']:
            add(c,g,'credits gameplay','native replay','native alias' if g=='S1' else 'N/A',
                'configured gameplay team / canonical sheet' if g=='S1' else 'no gameplay demos in this native credit roll',notes='S1 EndingDemoLevelAssembly uses configured main and ordinary playable art; S2 Sonic2EndingProvider uses text slides; S3K S3kCreditsState/sub_5B18E scrolls ROM text. No separate credits sprite sheet.')
    for f in [5,6]:
        add('talon','S3K','continue team sprite',f'{f:02X}','authored' if talon_contract else 'fallback','TalonScreenFrames.java' if talon_contract else 'stock Tails',
            evidence=citation('docs/skdisasm/sonic3k.asm','loc_5C74A'),notes='Team idle bank; later run uses normal Tails WAIT/WALK scripts')
    # Competition menu identity is separate from the racing miniature selectors.
    for character in ['mica','talon']:
        for surface,kind,label in [('wheel portrait','player','Obj_CompetitionPlayerSprite'),
                ('arena select portrait','player','ObjDat_CompetitionMatchRace'),
                ('results portrait','player','Obj_Competition_B9A6'),
                ('records portrait','record','Obj_Competition_BEF8')]:
            image=ART/character/'competition-menu'/f'{kind}.png'
            add(character,'S3K','competition '+surface,kind,'authored' if image.is_file() else 'fallback',
                str(image.relative_to(ROOT)),str(image.with_name(kind+'-provenance.json').relative_to(ROOT)),
                rank='competition/2P only',evidence=citation('docs/skdisasm/sonic3k.asm',label))
        add(character,'S3K','competition character names','all menus/results','N/A',
            notes='Native menus identify characters by full-body sprites and record icons; no character-name text. Arena names and 1P/2P labels remain native.')
        add(character,'S3K','competition mode menu','portrait/name','N/A',
            notes='Mode menu has mode labels, item switch and pointers; no character image or name.')
    # Complete textual inventories of relevant split native mapping/animation banks.
    candidates=[]
    for root in ['docs/s1disasm','docs/s2disasm','docs/skdisasm']:
        for p in pathlib.Path(root).rglob('*.asm'):
            name=str(p).lower()
            if (('map' in name or 'anim' in name) and any(k in name for k in ['sonic','tails','continue','ending','obj09','objb2','objca','save screen','2p'])):candidates.append(p)
    for p in candidates:
        label='';i=0
        for n,line in enumerate(p.read_text(errors='replace').splitlines(),1):
            m=re.match(r'^([\w.]+):',line)
            if m:label=m[1]
            if re.search(r'(dc\.[bw]|mappingsTableEntry|offsetTableEntry)',line):
                inventory.append(dict(character='native',game='S1' if 's1disasm'in str(p) else 'S2' if 's2disasm'in str(p) else 'S3K',bank=str(p),frame=i,script_ids=label,evidence=f'{label} ({p}:{n})',actual_raw_targets=line.strip()));i+=1
    # Inline native animation tables and callers are preserved with labels and exact lines.
    for path in ['docs/s1disasm/sonic.asm','docs/s2disasm/s2.asm','docs/skdisasm/sonic3k.asm']:
        p=pathlib.Path(path);label=''
        for n,line in enumerate(p.read_text(errors='replace').splitlines(),1):
            m=re.match(r'^([\w.]+):',line)
            if m:label=m[1]
            if re.search(r'(SonAni_|TailsAni_|Obj05Ani_|AniSonic|AniTails|AniIDSonAni_|AniIDTailsAni_|ObjB2_Animate_Pilot|EndingPics|MapEng_EndingTailsPlane)',line):
                inventory.append(dict(character='native',game='S1' if 's1disasm'in path else 'S2' if 's2disasm'in path else 'S3K',bank='script and user evidence',frame='',script_ids=label,evidence=f'{label} ({p}:{n})',actual_raw_targets=line.strip()))
    # Exact native target unions for observed missing canonical cycles. This does not
    # include unclosed actor roles or assert that every selector is reachable.
    for c in ['mica','talon']:
        for g in ['S1','S2','S3K']:
            p=out/f'{c}-{g}.json'
            if not p.exists():continue
            d=load(p)
            for bank,key in [('normal','nativeScripts'),('powered','superScripts')]:
                gapids=[];targets=set()
                for r in rows:
                    if r['character']!=c.upper() or r['game']!=g or r['status']!='fallback':continue
                    if r['surface']!=('animation' if bank=='normal' else 'Super/Hyper animation'):continue
                    sid=str(int(r['native_frame_or_animation'].split()[0],16));script=d.get(key,{}).get(sid)
                    if script:
                        fs=sorted(set(script['frames']));gapids.append(r['native_frame_or_animation']);targets.update(fs)
                        backlog.append(dict(character=c.upper(),game=g,reference=f'{g} {"Sonic" if c=="mica" else "Tails"} {bank}',batch=r['native_frame_or_animation'],target_frames=' '.join(f'{f:02X}' for f in fs),frame_estimate=len(fs),limits='native script union; does not prove role reachability or account for slope arithmetic / normal-art reuse'))
                if targets:backlog.append(dict(character=c.upper(),game=g,reference=f'{g} {c} {bank}',batch='DEDUPLICATED UNION of missing cycles',target_frames=' '.join(f'{f:02X}' for f in sorted(targets)),frame_estimate=len(targets),limits='subset budget only; not all-scene closure'))
    writecsv(out/'backlog-native-frames.csv',backlog)
    writecsv(out/'matrix.csv',rows);writecsv(out/'native-inventory.csv',inventory)
    counts=collections.defaultdict(collections.Counter)
    for r in rows:counts[(r['character'],r['game'])][r['status']]+=1
    (out/'totals.json').write_text(json.dumps({f'{c}/{g}':dict(v) for (c,g),v in sorted(counts.items())},indent=2))
    summary=''.join('<tr><td>'+c+'</td><td>'+g+'</td>'+''.join('<td>'+str(v[k])+'</td>' for k in ['authored','native alias','fallback','N/A'])+'</tr>' for (c,g),v in sorted(counts.items()))
    table=''.join('<tr>'+''.join('<td>'+html.escape(str(r.get(k,'')))+'</td>' for k in rows[0])+'</tr>' for r in rows)
    gallery=''.join('<figure><img loading="lazy" src="'+(ROOT/r['image']).as_uri()+'"><figcaption>'+html.escape(r['character']+' '+r['actual_raw_targets']+' '+r['evidence'])+'</figcaption></figure>' for r in inventory if r.get('image'))
    backlogtext=(out/'backlog.html').read_text() if (out/'backlog.html').exists() else '<p>See design coverage audit for ordered generation batches.</p>'
    (out/'index.html').write_text('''<!doctype html><meta charset="utf-8"><title>MICA / TALON art audit 2026-11-09</title><style>body{font:15px system-ui;margin:32px;background:#faf8f2;color:#222}table{border-collapse:collapse}td,th{border:1px solid #bbb;padding:6px;vertical-align:top;overflow-wrap:anywhere}figure{display:inline-block;width:180px;vertical-align:top}img{height:96px;image-rendering:pixelated}figcaption{font-size:11px}input{padding:10px;width:60%}</style><h1>MICA / TALON coverage audit · 2026-11-09</h1><p>Resolution obligations, not unique drawings. Animation rows count cycles; direct-mapping rows count semantic byte slots; presentation rows count supplied selectors. They must not be read as percentages of all native art. Native mapping inventory is separate. Authored canonical cycles may compress native phases. TALON uses the archetype-selected native Tails pose vocabulary; raw aliases are checked against production binding. Empty sentinels are N/A. No native pixels are published.</p><table><tr><th>Character<th>Game<th>Authored<th>Alias<th>Fallback<th>N/A</tr>'''+summary+'</table><h2>Generation backlog</h2><p><a href="backlog-native-frames.csv">Exact missing-cycle native frame unions and per-cycle counts</a></p>'+backlogtext+'<h2>Matrix</h2><p><a href="matrix.csv">CSV</a> · <a href="native-inventory.csv">Native mappings, scripts and users, with file:line evidence</a></p><input placeholder="Filter rows" oninput="for(let r of document.querySelectorAll(\'#matrix tbody tr\'))r.hidden=!r.textContent.toLowerCase().includes(this.value.toLowerCase())"><table id="matrix"><thead><tr>'+''.join('<th>'+k for k in rows[0])+'</tr></thead><tbody>'+table+'</tbody></table><h2>Authored source frames (ours)</h2><p>Original source paths and provenance are shown; native reference pixels are intentionally omitted.</p>'+gallery)
    print(json.dumps({f'{c}/{g}':dict(v) for (c,g),v in sorted(counts.items())},indent=2));print('Matrix rows',len(rows),'native evidence rows',len(inventory))

def zone(key):
    group=key.split('/')[0]
    return {'swing':'AIZ/MHZ vines','tumble':'AIZ tree/CNZ cage/MGZ loop/FBZ/DEZ','twist':'HCZ column/CNZ/LBZ/DEZ/SSZ','pole':'CNZ/FBZ/MHZ','bar':'MHZ/HCZ/LRZ','bar-end':'MHZ/HCZ/LRZ','chain':'FBZ','cage-entry':'FBZ','gravity-loop':'DEZ','carry':'LBZ boss/LRZ boulder','wall-run':'LRZ','corkscrew':'LRZ/SSZ','parachute':'MHZ/SSZ/SOZ','cutscene-hurt':'MHZ','emerald':'HPZ','crane':'SSZ','hang':'AIZ/MHZ/LBZ/SSZ/SOZ'}.get(group,'see owning object table')
if __name__=='__main__':main()
