#!/usr/bin/env python3
"""Purpose: standard TALON whole-character baseline/landmark/animation review.
Inputs: scratch before-manifest.json + before/*.png, previous native reference
archives beside scratch, and this directory's indexed handoff. No native art is
written to the repository. Origin: owner consistency review, 2026-11-09.
Usage: python3 examples/mica-and-talon/art/talon/consistency_review.py SCRATCH
"""
import argparse,html,json
from pathlib import Path
from PIL import Image,ImageDraw,GifImagePlugin
N=Image.Resampling.NEAREST

def review(scratch):
    root=Path(__file__).resolve().parent
    man=json.loads((root/'frames.yaml').read_text())
    old=json.loads((scratch/'before-manifest.json').read_text())
    audit=json.loads((root/'consistency-landmarks.json').read_text())
    groups={}
    for f in man['frames']:groups.setdefault((f['animation'],f['angle_set']),[]).append(f)
    def native(f):
        key=f['id'];g=key.split('-')[0];i=int(key[-2:]);r=scratch.parent/'talon-art-20261109';r2=scratch.parent/'talon-art-r2-20261109'
        if g in ('walk','run'):r=r2
        if g in ('spring','hurt','death','duck','look_up','skid'):
            i={'spring':0,'hurt':1,'death':2,'duck':3,'look_up':4,'skid':5}[g]+(i if g=='skid' else 0)
            path=r/'spring-native.png' if g=='spring' else r/f'poses-native-{i:02}.png'
        else:
            i=min(i,2) if g in ('roll','spindash') else i%2 if g in ('fly','fly_tired') else i
            path=r/f'{g}-native-{i:02}.png'
        pic=Image.open(path).convert('RGBA')
        refs=json.loads((r/'references.json').read_text())
        ref=refs['poses'][i] if g in ('spring','hurt','death','duck','look_up','skid') else refs[g][i]
        hot=[14,23] if g=='spring' else ref['hotspot']
        if f['angle_set']!='flat':
            c=Image.new('RGBA',(128,128));c.alpha_composite(pic,(64-hot[0],64-hot[1]));c=c.rotate({'up45':45,'steep':90,'inverted':135}[f['angle_set']],resample=N);box=c.getbbox();pic=c.crop(box);hot=[64-box[0],64-box[1]]
        return pic,hot
    def canvas(pic,f,air=False):
        out=Image.new('RGBA',(64,80));hot=f['hotspot'];x=32-hot[0];y=41-hot[1] if air else 60-pic.height
        out.alpha_composite(pic.convert('RGBA'),(x,y));return out
    page='''<!doctype html><meta charset="utf-8"><title>TALON consistency</title><style>body{background:#202830;color:#eee;font:16px system-ui;margin:24px}img{image-rendering:pixelated}section{overflow:auto}table{border-collapse:collapse}td,th{border:1px solid #678;padding:6px}a{color:#fdc36b}</style><h1>TALON consistency</h1><p>4× nearest neighbour. Before / after / native pose rows. Grounded frames share a floor; airborne frames share engine hotspots. Native rows use the archived Tails poses, except approved Sonic WALK/RUN. Flight native body snapshots repeat across six authored wing phases; native rotor timing is not simulated here. Green feet, amber waist, cyan eye, pink head-top guides come from approved WAIT. Guide lines remain constant through every panel.</p><p>Landmark placement is manual (approximately ±1px). Curled/blur landmarks are hidden; crouch/curl torso and pitched head distances are pose exceptions. Native per-frame anatomical landmarks remain unverified; the panel supports visual comparison, not a certified ±1px native match.</p>'''
    oldby={f['id']:f for f in old['frames']};by={f['id']:f for f in man['frames']}
    for (anim,angle),fs in groups.items():
        name=anim+'-'+angle;air=anim in ('TAILS_FLY','TAILS_FLY_TIRED','SPRING','HURT','DEATH','ROLL','SPINDASH') or angle!='flat'
        panel=Image.new('RGB',(len(fs)*256,3*360),'#888');d=ImageDraw.Draw(panel)
        for row,label in enumerate(('before','after','native')):
            for i,f in enumerate(fs):
                if row==0:pic=Image.open(scratch/'before'/Path(f['file']).name);frame=oldby[f['id']]
                elif row==1:pic=Image.open(root/f['file']);frame=f
                else:pic,hot=native(f);frame={'hotspot':hot}
                tile=canvas(pic,frame,air).resize((256,320),N);panel.paste(tile,(i*256,row*360+28),tile);d.text((i*256+3,row*360+3),label+' '+f['id'],fill='black')
            for yy,col in [(29,'#f4a0b0'),(39,'#70d0f0'),(54,'#eec16c'),(60,'#88d897')]:d.line((0,row*360+28+yy*4,panel.width,row*360+28+yy*4),fill=col)
        panel.save(scratch/f'{name}-lineup.png')
        page+=f'<h2>{name}</h2><section><img src="{name}-lineup.png"></section>'
        script=man.get('scripts',{}).get(anim,{}) if angle=='flat' else {};steps=script.get('frames',[f['id'] for f in fs]);dur=script.get('durations',[5 if anim=='WALK' else 3 if anim=='RUN' else 1 if anim in ('ROLL','SPINDASH') else 6]*len(steps));seq=[key for key,dt in zip(steps,dur) for _ in range(dt)]
        # Exact TALON palette plus backdrop, full independent frames per tick.
        colors=json.loads((root/'palette.yaml').read_text())['colors']+[[136,136,136]];palette=sum(colors,[])+[0]*(768-len(colors)*3);lookup={tuple(c):i for i,c in enumerate(colors)}
        with (scratch/f'{name}.gif').open('wb') as fp:
            for t,key in enumerate(seq):
                im=Image.new('RGB',(64,80),'#888');pic=canvas(Image.open(root/by[key]['file']),by[key],air);im.paste(pic,(0,0),pic);im=im.resize((256,320),N);idx=Image.new('P',im.size);idx.putpalette(palette);idx.putdata([lookup[c] for c in im.get_flattened_data()])
                if t==0:
                    header,_=GifImagePlugin.getheader(idx,info={'loop':0})
                    for b in header:fp.write(b)
                dt=(round((t+1)*100/60)-round(t*100/60))*10
                for b in GifImagePlugin.getdata(idx,duration=dt,disposal=2):fp.write(b)
            fp.write(b';')
        with Image.open(scratch/f'{name}.gif') as check:assert check.n_frames==len(seq)
        page+=f'<img src="{name}.gif"><p>{len(seq)} full 60Hz ticks; cumulative centisecond rounding. GIF viewers may clamp 10ms delays. No palette approximation, dropped ticks or delta diffing.</p>'
    page+='<h2>FLY / FLY_TIRED</h2><img src="TAILS_FLY-flat.gif"><img src="TAILS_FLY_TIRED-flat.gif"><h2>Landmarks before → after</h2><p>Signed eye-to-beak vertical distance; skull width excludes crest and strap. Slope values use source-flat anatomical axes.</p><table><tr><th>Frame</th><th>Skull</th><th>Eye→beak Δy</th><th>Torso</th><th>Opaque height</th><th>Exception</th></tr>'
    for key,r in audit['frames'].items():
        page+='<tr><td>'+key+'</td>'+''.join(f'<td>{r["before"][k]} → {r["after"][k]}</td>' for k in ('skull_width','eye_to_beak_dy','torso_height','opaque_height'))+f'<td>{html.escape(r["exception"] or "")}</td></tr>'
    page+='</table><h2>Limits</h2><p>SPRING uses the compact launch alternative with raised wings; it differs from native tucked-arm choreography. No runtime slope-contact certification. WAIT and FLY remain the owner-approved original identity anchors.</p>'
    (scratch/'review.html').write_text(page)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('scratch',type=Path);review(parser.parse_args().scratch)
