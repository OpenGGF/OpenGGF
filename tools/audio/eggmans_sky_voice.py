#!/usr/bin/env python3
"""Generate Eggman's Sky's approved Alice bank offline through OpenRouter.
Inputs: voice-bank.json, private environment/key, FFmpeg, NumPy and SciPy.
Outputs: shipped WAVs and source/verification cache OUTSIDE the repository.
Origin: Eggman's Sky voice-bank task, 2026-10-08. Paid requests are resumable;
uncertain requests are never repeated automatically. This is an artistic DAC
approximation, not YM2612 emulation. No network or credentials at game runtime.
"""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import wave
import numpy as np
from scipy import signal

RATE=24000
FINAL_RATE=48000
SEED=20261008
APPROVED_PROCESSING_SHA256='f3ac35e8719b484fde4174c826446f81ef3f31d3ec3e0c5033484cf16e98a7d5'

def rms(x):
    return float(np.sqrt(np.mean(x*x)))


def filt(x, kind, cutoff, order=2):
    return signal.sosfilt(signal.butter(order, cutoff, btype=kind, fs=RATE, output='sos'), x)


def metallic(x):
    t = np.arange(len(x))/RATE
    out = .76*x + .24*x*np.sin(2*np.pi*73*t)
    for ms, gain in [(7.6, .14), (11.3, .10), (17.9, .06)]:
        delay = round(ms*RATE/1000)
        if delay < len(x):
            out[delay:] += gain*x[:-delay]
    return out


def vocoder_wet(x):
    t = np.arange(len(x))/RATE
    carrier = sum(np.sin(2*np.pi*185*h*t)/h for h in range(1, 38))
    carrier += .075*np.random.default_rng(SEED).normal(size=len(x))
    edges = np.geomspace(100, 6800, 25)
    envelope_filter = signal.butter(2, 65, btype='lowpass', fs=RATE, output='sos')
    wet = np.zeros(len(x))
    for low, high in zip(edges[:-1], edges[1:]):
        band = signal.butter(2, [low, high], btype='bandpass', fs=RATE, output='sos')
        mod = signal.sosfilt(band, x)
        envelope = np.sqrt(np.maximum(signal.sosfilt(envelope_filter, mod*mod), 0))
        synth = signal.sosfilt(band, carrier)
        synth /= max(rms(synth), 1e-8)
        wet += synth*envelope
    return wet*rms(x)/max(rms(wet), 1e-8)


def texture(x):
    metal = metallic(x)
    wet = vocoder_wet(x)
    wet *= rms(metal)/max(rms(wet), 1e-8)
    return .94*metal + .06*wet


def normalize(x):
    blob = subprocess.check_output([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'f32le',
        '-ar', str(RATE), '-ac', '1', '-i', 'pipe:0',
        '-af', 'loudnorm=I=-18:TP=-2.5:LRA=5', '-ar', str(RATE),
        '-f', 'f32le', 'pipe:1'], input=x.astype('<f4').tobytes())
    return np.frombuffer(blob, dtype='<f4').astype(float)


def dac(x):
    # Anti-alias filtering precedes the deliberately low-rate sample-and-hold.
    lower = signal.resample_poly(filt(x, 'lowpass', 3400), 1, 3)
    quantized = np.clip(np.round(lower*64), -64, 63)/64
    return np.repeat(quantized, 6)


def write_json(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2)+'\n')
    temporary.replace(path)


def words(text):
    # Orthographic alternatives do not change spoken words.
    text = text.lower().replace('eggman', 'egg man').replace('eggstation', 'egg station').replace('eggmobile', 'egg mobile').replace('supersonic', 'super sonic').replace('tales', 'tails')
    for digit, word in enumerate(('zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven')):
        text = text.replace(str(digit), word)
    return re.findall(r'[a-z]+', text)


def wav(path, samples):
    assert np.isfinite(samples).all()
    assert np.max(np.abs(samples)) < 1, 'Clipped source/output'
    pcm = np.round(samples*32768).astype('<i2')
    with wave.open(str(path), 'wb') as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(FINAL_RATE)
        handle.writeframes(pcm.tobytes())
    return {'frames': len(pcm), 'seconds': len(pcm)/FINAL_RATE,
            'peak_dbfs': float(20*np.log10(max(np.max(np.abs(samples)), 1e-9))),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def render(raw, master, target, edit=None):
    blob = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(raw),
        '-ac', '1', '-ar', str(RATE), '-f', 'f32le', 'pipe:1'])
    clean = signal.resample_poly(normalize(filt(np.frombuffer(blob, '<f4').astype(float),
                                               'highpass', 80)), 2, 1)
    if edit:
        clean = clean[round(edit['start']*FINAL_RATE):round(edit['end']*FINAL_RATE)]
    # Keep 60 ms before the first and 120 ms after the last audible sample.
    audible = np.flatnonzero(np.abs(clean) > 10**(-48/20))
    if not len(audible):
        raise ValueError('Silent synthesis')
    start = max(0, audible[0]-round(.060*FINAL_RATE))
    end = min(len(clean), audible[-1]+round(.120*FINAL_RATE))
    clean = clean[start:end]
    clean = np.pad(clean, (0, (-len(clean)) % 6))
    wav(master, clean)
    x = signal.resample_poly(clean, 1, 2)
    x = np.pad(x, (0, round(.120*RATE)))
    result = .90*dac(normalize(texture(x))) + .10*signal.resample_poly(filt(x, 'highpass', 2400), 2, 1)
    fade = round(.005*FINAL_RATE)
    result[:fade] *= np.linspace(0, 1, fade)
    result[-fade:] *= np.linspace(1, 0, fade)
    report = wav(target, result)
    report['source_sha256'] = hashlib.sha256(raw.read_bytes()).hexdigest()
    return report


def publish(bank, cache, output, enum_path):
    """Freeze checked asset identity and derive queue leases from actual PCM duration."""
    metadata = {k: bank[k] for k in ('format_version', 'model', 'voice', 'delivery_tag',
                                   'processing', 'seed', 'room_reverb_mix')}
    metadata['origin'] = 'Original Alice performance directed through OpenRouter; no game voice cloning.'
    metadata['output'] = {'sample_rate': FINAL_RATE, 'channels': 1, 'container_bits': 16}
    metadata['entries'] = []
    rows = []
    manifest = 'formatVersion: 1\ntracks: []\nsfx:\n'
    for entry in bank['entries']:
        ident = entry['id']
        folder = cache/(ident+('-take-'+str(entry['take']) if 'take' in entry else ''))
        report = json.loads((folder/'verification.json').read_text())
        path = output/(ident+'.wav')
        assert report.get('matches_words'), f'{ident}: word check not passed'
        assert words(report['transcript']) == words(entry['text']), f'{ident}: stale text check'
        assert hashlib.sha256(path.read_bytes()).hexdigest() == report['sha256'], f'{ident}: asset changed'
        assert hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest() == report['source_sha256'], f'{ident}: raw source changed'
        with wave.open(str(path)) as handle:
            assert (handle.getframerate(), handle.getnchannels(), handle.getsampwidth()) == (FINAL_RATE, 1, 2)
            assert handle.getnframes() == report['frames']
            pcm = np.frombuffer(handle.readframes(handle.getnframes()), '<i2').astype(np.int32)
            assert np.max(np.abs(pcm)) < 32767 and np.any(pcm)
        asset = 'voice-'+ident.replace('_', '-')
        duration = (report['frames']+799)//800
        rows.append(f'    {ident.upper()}("{asset}", {duration}, {entry["priority"]}, {entry["cooldown_ticks"]})')
        manifest += f'  - id: {asset}\n    assetPath: audio/voice/{ident}.wav\n    gain: 1.0\n'
        metadata['entries'].append({'id': ident, 'text': entry['text'], 'asset': asset,
            'frames': report['frames'], 'sha256': report['sha256'], 'source_sha256': report['source_sha256'],
            'peak_dbfs': report['peak_dbfs'], 'transcript': report['transcript'],
            'blind_word_check': True, 'source_edit': json.loads((folder/'edit.json').read_text())})
    write_json(output/'provenance.json', metadata)
    (output.parent/'audio-manifest.yaml').write_text(manifest)
    enum_path.write_text('''package eggsky.core;

/** Generated by tools/audio/eggmans_sky_voice.py; leases use shipped PCM frame counts. */
public enum VoiceLine {
'''+',\n'.join(rows)+''';

    public final String asset;
    public final int durationTicks;
    public final int priority;
    public final int cooldownTicks;

    VoiceLine(String asset, int durationTicks, int priority, int cooldownTicks) {
        this.asset = asset;
        this.durationTicks = durationTicks;
        this.priority = priority;
        this.cooldownTicks = cooldownTicks;
    }
}
''')
    print(f'Published {len(rows)} verified clips ({sum(e["frames"] for e in metadata["entries"])/FINAL_RATE:.1f}s).')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalogue', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--env-file', type=Path)
    parser.add_argument('--only', nargs='+')
    parser.add_argument('--render-only', action='store_true')
    parser.add_argument('--clean-takes', action='store_true', help='Select one complete utterance from cached sources using blind word times and quiet gaps')
    parser.add_argument('--publish-only', action='store_true', help='Freeze checked files without rendering or paid requests')
    parser.add_argument('--enum', type=Path, help='Generated VoiceLine.java; required for --publish-only')
    args = parser.parse_args()
    bank = json.loads(args.catalogue.read_text())
    chain_hash = hashlib.sha256(json.dumps(bank['processing'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    if chain_hash != APPROVED_PROCESSING_SHA256 or bank['seed'] != SEED or bank['room_reverb_mix'] != 0:
        raise SystemExit('Catalogue processing changed; update the renderer explicitly before claiming that recipe')
    if len(bank['entries']) > 300:
        raise SystemExit('Unexpected catalogue size')
    estimated = sum(len(bank['delivery_tag'])+1+len(e['text']) for e in bank['entries'])*.00004
    if estimated > 2:
        raise SystemExit('Catalogue exceeds $2 synthesis estimate')
    args.cache.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    if args.publish_only:
        if not args.enum:
            raise SystemExit('--publish-only requires --enum')
        publish(bank, args.cache, args.output, args.enum)
        return
    key = os.environ.get('OPENROUTER_API_KEY', '')
    if not key and args.env_file:
        key = next(line.split('=', 1)[1].strip().strip('\"\'')
                   for line in args.env_file.read_text().splitlines()
                   if line.strip().startswith('OPENROUTER_API_KEY='))
    if not args.render_only and not key:
        raise SystemExit('Missing private credential')

    def query(endpoint, body=None, audio=False):
        data = json.dumps(body).encode() if body is not None else None
        headers = {'Authorization': 'Bearer '+key, 'User-Agent': 'OpenGGF-voice-bank/1.0'}
        if data:
            headers['Content-Type'] = 'application/json'
        try:
            with urllib.request.urlopen(urllib.request.Request(
                    'https://openrouter.ai/api/v1/'+endpoint, data=data, headers=headers), timeout=55) as response:
                if audio:
                    blob = response.read(8*1024*1024+1)
                    if not response.headers.get('Content-Type', '').startswith('audio/') or not 100 < len(blob) <= 8*1024*1024:
                        raise ValueError('Unexpected audio response; request state preserved')
                    return blob, response.headers.get('X-Generation-Id')
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'OpenRouter HTTP {error.code}') from None

    if not args.render_only:
        before = query('key')['data']['usage']
        if before is None or before + estimated*2 >= 5:
            raise SystemExit('Voice-bank $5 key-usage safety ceiling reached')
        print(f'{len(bank["entries"])} lines; synthesis estimate ${estimated:.3f}; billing can lag.', flush=True)

    def job(entry):
        ident = entry['id']
        if not re.fullmatch('[a-z_]+', ident):
            raise ValueError('Invalid line id')
        folder = args.cache/(ident+("-take-"+str(entry["take"]) if "take" in entry else ""))
        folder.mkdir(exist_ok=True)
        receipt = folder/'request.json'
        raw = folder/'raw.mp3'
        request = {'model': bank['model'], 'voice': bank['voice'], 'response_format': 'mp3',
                   'input': bank['delivery_tag']+' '+entry.get('source_text', entry['text'])}
        if receipt.exists():
            state = json.loads(receipt.read_text())
            if state['request'] != request or state['state'] != 'complete':
                raise ValueError(f'{ident}: existing uncertain/changed request; inspect before any paid retry')
            if hashlib.sha256(raw.read_bytes()).hexdigest() != state['source_sha256']:
                raise ValueError(f'{ident}: raw source changed')
        elif args.render_only:
            raise ValueError(f'{ident}: no cached source')
        else:
            state = {'state': 'in_flight', 'request': request}
            write_json(receipt, state)
            blob, generation = query('audio/speech', request, audio=True)
            raw.write_bytes(blob)
            state.update(state='complete', source_sha256=hashlib.sha256(blob).hexdigest(), generation_id=generation)
            write_json(receipt, state)
        target = args.output/(ident+'.wav')
        if args.clean_takes and not (folder/'edit.json').exists():
            times_path = folder/'raw-transcription.json'
            if times_path.exists():
                timed = json.loads(times_path.read_text())
            else:
                timed = query('audio/transcriptions', {'model': 'openai/whisper-large-v3-turbo',
                    'language': 'en', 'temperature': 0, 'response_format': 'verbose_json',
                    'timestamp_granularities': ['word', 'segment'],
                    'input_audio': {'data': base64.b64encode(raw.read_bytes()).decode(), 'format': 'mp3'}})
                write_json(times_path, timed)
            timestamps = timed.get('words', [])
            normalized = [(token, index) for index, word in enumerate(timestamps) for token in words(word['word'])]
            expected = words(entry['text'])
            matches = [index for index in range(len(normalized)-len(expected)+1)
                       if [word for word, _ in normalized[index:index+len(expected)]] == expected]
            if not matches:
                raise ValueError(f'{ident}: no complete literal utterance in raw take: '+timed.get('text', ''))
            index = matches[-1]
            first = normalized[index][1]
            last = normalized[index+len(expected)-1][1]
            first_word, last_word = timestamps[first], timestamps[last]
            detection = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(raw), '-af',
                'silencedetect=noise=-38dB:d=0.07', '-f', 'null', '-'], capture_output=True, text=True, check=True).stderr
            starts = [float(v) for v in re.findall(r'silence_start: ([0-9.]+)', detection)]
            ends = [float(v) for v in re.findall(r'silence_end: ([0-9.]+)', detection)]
            # Silence boundaries keep consonants intact even when ASR word times are loose.
            lower = timestamps[first-1]['start'] if first else 0
            before = [end for begin, end in zip(starts, ends) if end-begin >= .20 and lower <= end <= first_word['end'] and abs(end-first_word['start']) < 1.4]
            start = max(0, min(before, key=lambda v: abs(v-first_word['start']))-.060) if before else max(0, first_word['start']-.120)
            upper = timestamps[last+1]['end'] if last+1 < len(timestamps) else timed.get('duration', last_word['end']+1)
            after = [v for v in starts if last_word['end']-.05 <= v <= upper and abs(v-last_word['end']) < .65]
            end = min(after, key=lambda v: abs(v-last_word['end']))+.120 if after else last_word['end']+.120
            write_json(folder/'edit.json', {'start': start, 'end': end, 'whole_utterance': entry['text'],
                'method': 'last complete literal utterance, expanded to adjacent quiet gaps; final processed word check required'})
        edit_path = folder/'edit.json'
        edit = json.loads(edit_path.read_text()) if edit_path.exists() else None
        report = render(raw, folder/'clean.wav', target, edit)
        report_path = folder/'verification.json'
        cached = json.loads(report_path.read_text()) if report_path.exists() else {}
        if cached.get('sha256') == report['sha256'] and 'transcript' in cached:
            report = cached
        elif not args.render_only:
            mp3 = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(target),
                '-f', 'mp3', '-b:a', '128k', 'pipe:1'])
            response = query('audio/transcriptions', {'model': 'openai/whisper-large-v3-turbo',
                'language': 'en', 'temperature': 0, 'response_format': 'json',
                'input_audio': {'data': base64.b64encode(mp3).decode(), 'format': 'mp3'}})
            report['transcript'] = response.get('text', '')
            report['matches_words'] = words(report['transcript']) == words(entry['text'])
        write_json(report_path, report)
        print(f'{ident}: {report["seconds"]:.2f}s; '+('words OK' if report.get('matches_words') else 'CHECK '+report.get('transcript', 'not transcribed')), flush=True)
        return ident, report

    entries = [e for e in bank['entries'] if not args.only or e['id'] in args.only]
    failures = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        jobs = {pool.submit(job, e): e['id'] for e in entries}
        for future in as_completed(jobs):
            try:
                _, report = future.result()
                if not args.render_only and not report.get('matches_words'):
                    failures.append(jobs[future])
            except Exception as error:
                failures.append(jobs[future])
                print(f'{jobs[future]}: {str(error).replace(key, "[REDACTED]") if key else error}', flush=True)
    if not args.render_only:
        after = query('key')['data']['usage']
        write_json(args.cache/'usage.json', {'reported_before': before, 'reported_after': after,
            'synthesis_estimate': estimated, 'note': 'Usage reporting can lag; estimate is not final billing.'})
    if failures:
        raise SystemExit('Requires inspection: '+', '.join(failures))


if __name__ == '__main__':
    main()
