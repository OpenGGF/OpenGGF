#!/usr/bin/env python3
"""Rebuild owner-directed MICA native-pose stage drawings from final indexed rows.
Purpose: reproducible art without decoded native assets or raw generation files.
Inputs: native-pose-*.json; unchanged palette and native manifests.
Origin: special/bonus/snowboard design lane, 2026-11-08.
Run --rebuild [group] or --verify. Legacy build.py/round5.py draw earlier artwork;
run this rebuild afterward to restore these accepted native-pose replacements.
"""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image
HERE=Path(__file__).resolve().parent
ART=HERE.parent
GROUPS=('half_pipe','blue_sphere','slot_machine','snowboard')

def context(group):
    folder=ART/('snowboard' if group=='snowboard' else 'wave3')
    manifest=json.loads((folder/('snowboard.yaml' if group=='snowboard' else 'wave3.yaml')).read_text())
    surface=manifest if group=='snowboard' else next(s for s in manifest['surfaces'] if s['surface']==group)
    return folder,surface

def source_path(group):
    return context(group)[0]/('native-pose-'+group+'.json')

def indexed(rows,colors):
    im=Image.new('P',(len(rows[0]),len(rows)))
    im.putpalette(sum(colors,[])+[0]*720)
    im.putdata([int(v,16) for row in rows for v in row]);im.info['transparency']=0
    return im

def rebuild(group):
    path=source_path(group)
    if not path.exists():return
    source=json.loads(path.read_text());folder,surface=context(group)
    colors=json.loads((ART/'palette.yaml').read_text())['colors']
    for f in source['frames']:
        im=indexed(f['rows'],colors);im.save(folder/f['file'],bits=4,transparency=0)
        mf=next(m for m in surface['frames'] if m['id']==f['id'])
        if 'fixed_palette_file' in mf:
            fixed=surface['fixed_palette'];q=indexed(f['rows'],fixed['colors'])
            q.putdata([fixed['index_lut'][v] for v in im.get_flattened_data()])
            q.save(folder/mf['fixed_palette_file'],bits=4,transparency=0)

def verify(group):
    folder,surface=context(group);colors=json.loads((ART/'palette.yaml').read_text())['colors']
    source=json.loads(source_path(group).read_text());count=0
    assert len(source['frames'])==len(surface['frames'])
    for f in surface['frames']:
        im=Image.open(folder/f['file']);assert im.mode=='P' and im.info['transparency']==0
        assert im.getpalette()[:48]==sum(colors,[]) and max(im.get_flattened_data())<16
        assert list(im.size)==f['dimensions'];tiles={};hx,hy=f['hotspot']
        covered=Image.new('1',im.size)
        for x,y,w,h,t,fx,fy,*_ in f['pieces']:
            covered.paste(1,(x+hx,y+hy,x+hx+w*8,y+hy+h*8))
            p=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8))
            if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            for tx in range(w):
                for ty in range(h):
                    index=t+tx*h+ty;data=p.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
                    assert index not in tiles or tiles[index]==data,(group,f['id'],'alias',index)
                    tiles[index]=data
        assert max(tiles,default=-1)<f['tile_budget']
        assert all(not v or m for v,m in zip(im.get_flattened_data(),covered.get_flattened_data()))
        if not f['pieces']:assert im.getbbox() is None
        if 'fixed_palette_file' in f:
            q=Image.open(folder/f['fixed_palette_file']);fixed=surface['fixed_palette']
            assert q.getpalette()[:48]==sum(fixed['colors'],[])
            assert list(q.get_flattened_data())==[fixed['index_lut'][v] for v in im.get_flattened_data()]
        record=next(s for s in source['frames'] if s['id']==f['id'])
        assert im.tobytes()==indexed(record['rows'],colors).tobytes()
        count+=1
    files=[folder/f['file'] for f in surface['frames']]+[folder/f['fixed_palette_file'] for f in surface['frames'] if 'fixed_palette_file' in f]
    before={p:hashlib.sha256(p.read_bytes()).hexdigest() for p in files};rebuild(group)
    assert all(hashlib.sha256(p.read_bytes()).hexdigest()==h for p,h in before.items())
    print(f'PASS {group}: {count} frames, unchanged palette, binary alpha, native geometry, piece coverage, aliases, DPLC capacity, fixed variants and byte-identical rebuild; no runtime certification')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--rebuild',nargs='?',const='all',choices=('all',)+GROUPS);p.add_argument('--verify',nargs='?',const='all',choices=('all',)+GROUPS);a=p.parse_args()
    for group in GROUPS:
        if a.rebuild in ('all',group):rebuild(group)
        if a.verify in ('all',group):verify(group)
