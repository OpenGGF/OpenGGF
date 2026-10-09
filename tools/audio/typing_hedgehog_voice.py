#!/usr/bin/env python3
"""Generate Typing of the Hedgehog's multi-speaker voice bank offline through OpenRouter.

Purpose: original directed performances (announcer, Robotnik, Tails; each its own ElevenLabs
premade voice, delivery tag and retro processing preset), voice/preset auditions, blind word
checks, and publication of WAVs, provenance.json, the mod's audio-manifest block and the
generated typinghog.audio.VoiceLine enum. The DAC presets are artistic approximations, not
YM2612 emulation. No network or credential is used at game runtime.
Inputs: examples/typing-of-the-hedgehog/voice-bank.json; a private OPENROUTER_API_KEY in the
environment or --env-file; FFmpeg; NumPy and SciPy for rendering (publication needs neither).
Outputs: shipped WAVs under the mod's resources; paid-request receipts, raw takes, masters,
edits, transcripts and auditions stay OUTSIDE the repository (--cache, --audition).
Origin: Typing of the Hedgehog voice lane, 2026-10-09. Generalises
tools/audio/eggmans_sky_voice.py (2026-10-08), whose approved Alice recipe is untouched.
Paid requests are resumable and receipted; an uncertain request is never retried
automatically. Every run checks a reported key-usage ceiling and an estimate-based budget.
"""
import argparse
import base64
from array import array
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import urllib.error
import urllib.request
import wave

try:  # Rendering needs NumPy/SciPy; catalogue checks and publication do not.
    import numpy as np
    from scipy import signal
except ImportError:  # pragma: no cover - reported when rendering is requested
    np = signal = None

RATE = 48000
TICK_FRAMES = RATE // 60
SYNTHESIS_USD_PER_CHAR = 0.00004  # OpenRouter list price for eleven-v4 before any discount
TRANSCRIPTION_RESERVE_USD = 0.001  # Whisper turbo is ~$0.0002/min; reserve generously
API = 'https://openrouter.ai/api/v1/'
ID_PATTERN = re.compile('[a-z][a-z0-9_]*')
BEGIN_MARK = '  # BEGIN generated voice lines: tools/audio/typing_hedgehog_voice.py --publish-only\n'
END_MARK = '  # END generated voice lines\n'
# Presets the user approved after auditions, by canonical JSON SHA-256. Bank generation and
# publication refuse any other recipe; auditions may render unapproved presets.
APPROVED_PRESET_SHA256 = {}


# ---------------------------------------------------------------- catalogue

def preset_hash(chain):
    return hashlib.sha256(json.dumps(chain, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def line_key(entry):
    return entry['speaker']+'_'+entry['id']


def load_taunts(path):
    table = set()
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#') and '|' in line:
            tag, text = line.split('|', 1)
            table.add((tag.strip(), text.strip()))
    return table


def validate(bank, catalogue_path):
    """Structural checks that need no network or DSP; raises SystemExit on any problem."""
    problems = []
    if bank.get('format_version') != 1:
        problems.append('format_version must be 1')
    speakers = bank.get('speakers', {})
    presets = bank.get('presets', {})
    for name, speaker in speakers.items():
        if not ID_PATTERN.fullmatch(name):
            problems.append(f'bad speaker name {name}')
        if not speaker.get('delivery_tag', '').startswith('['):
            problems.append(f'{name}: delivery_tag must be a bracketed audio tag')
        if speaker.get('preset') is not None and speaker['preset'] not in presets:
            problems.append(f'{name}: unknown preset {speaker["preset"]}')
        audition = speaker.get('audition', {})
        for preset in audition.get('presets', []):
            if preset not in presets:
                problems.append(f'{name}: unknown audition preset {preset}')
    keys = set()
    taunts = load_taunts(catalogue_path.parent/bank['taunt_source'])
    for entry in bank.get('entries', []):
        key = line_key(entry)
        if entry['speaker'] not in speakers:
            problems.append(f'{key}: unknown speaker')
        if not ID_PATTERN.fullmatch(entry['id']) or key in keys:
            problems.append(f'{key}: invalid or duplicate id')
        keys.add(key)
        if not 1 <= entry['priority'] <= 4 or entry['cooldown_ticks'] < 0:
            problems.append(f'{key}: priority 1..4 and non-negative cooldown required')
        if not words(entry['text'], bank['word_aliases']):
            problems.append(f'{key}: no speakable words')
        if 'taunt_tag' in entry and (entry['taunt_tag'], entry['text']) not in taunts:
            problems.append(f'{key}: taunt text/tag not found verbatim in {bank["taunt_source"]}')
    for name, speaker in speakers.items():
        for line in speaker.get('audition', {}).get('lines', []):
            if name+'_'+line not in keys:
                problems.append(f'{name}: audition line {line} is not a catalogue entry')
    if len(keys) > 300:
        problems.append('unexpected catalogue size')
    if problems:
        raise SystemExit('Catalogue problems:\n  '+'\n  '.join(problems))


def request_input(bank, entry):
    speaker = bank['speakers'][entry['speaker']]
    parts = [speaker['delivery_tag'], entry.get('direction'), entry.get('source_text', entry['text'])]
    return ' '.join(part for part in parts if part)


def synthesis_cost(text):
    return len(text)*SYNTHESIS_USD_PER_CHAR


# ---------------------------------------------------------------- word checks

NUMBERS = 'zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen'.split()
TENS = {2: 'twenty', 3: 'thirty', 4: 'forty', 5: 'fifty', 6: 'sixty', 7: 'seventy', 8: 'eighty', 9: 'ninety'}


def number_words(match):
    value = int(match.group())
    if value < 20:
        return NUMBERS[value]
    if value < 100:
        return TENS[value//10]+('' if value % 10 == 0 else ' '+NUMBERS[value % 10])
    return match.group()


def words(text, aliases):
    """Orthographic normalisation applied identically to the script and to transcripts."""
    text = re.sub(r'\d+', number_words, text.lower())
    tokens = []
    for token in re.findall(r"[a-z]+", text):
        tokens.extend(aliases.get(token, token).split())
    # Repeated letters collapse ("Nooo"/"No", "Grrr"/"Grr"); both sides are treated alike.
    return [re.sub(r'(.)\1+', r'\1', token) for token in tokens]


def plausible_seconds(text):
    """Upper bound for one theatrical reading. Whisper can collapse a repeated take into one
    utterance, even with word timestamps, so clip length is the independent repeat detector."""
    return 1.2+0.1*len(text)


def matches_words(transcript, entry, aliases):
    """True when the transcript says the script (or a reviewed alternative) literally.

    Joined-letter equality accepts spelled letters and run-together interjections
    ("A, S, D, F" / "ASDF", "Ho ho ho" / "Hohoho") while still requiring every letter in order.
    """
    heard = words(transcript, aliases)
    for candidate in [entry['text']]+entry.get('accept', []):
        expected = words(candidate, aliases)
        if heard == expected or ''.join(heard) == ''.join(expected):
            return True
    return False


# ---------------------------------------------------------------- DSP

def require_dsp():
    if np is None:
        raise SystemExit('Rendering requires NumPy and SciPy')


def rms(x):
    return float(np.sqrt(np.mean(x*x)))


def filt(x, kind, cutoff, order=2):
    return signal.sosfilt(signal.butter(order, cutoff, btype=kind, fs=RATE, output='sos'), x)


def loudness(x, step):
    blob = subprocess.check_output([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'f32le', '-ar', str(RATE), '-ac', '1',
        '-i', 'pipe:0', '-af',
        f'loudnorm=I={step["integrated_lufs"]}:TP={step["true_peak_db"]}:LRA={step["lra"]}',
        '-ar', str(RATE), '-f', 'f32le', 'pipe:1'], input=x.astype('<f4').tobytes())
    return np.frombuffer(blob, dtype='<f4').astype(float)


def dac(x, step):
    # Anti-alias filtering precedes the deliberately low-rate sample-and-hold.
    factor = RATE//step['sample_rate_hz']
    assert factor*step['sample_rate_hz'] == RATE, 'DAC rate must divide 48000'
    lower = signal.resample_poly(filt(x, 'lowpass', step['lowpass_hz']), 1, factor)
    scale = 2**(step['bits']-1)
    held = np.repeat(np.clip(np.round(lower*scale), -scale, scale-1)/scale, factor)
    held = np.pad(held, (0, max(0, len(x)-len(held))))[:len(x)]
    # Consonant recovery: a small upper band from the pre-DAC signal keeps sibilants legible.
    return step['dac_mix']*held+step['recovery_mix']*filt(x, 'highpass', step['recovery_highpass_hz'])


def drive(x, step):
    gain = 10**(step['pre_gain_db']/20)
    wet = np.tanh(gain*x)*rms(x)/max(rms(np.tanh(gain*x)), 1e-8)
    return (1-step['wet_mix'])*x+step['wet_mix']*wet


def process(master, chain, tail_ms):
    x = np.pad(master, (0, round(tail_ms*RATE/1000)))
    for step in chain:
        effect = step['effect']
        if effect == 'loudness':
            x = loudness(x, step)
        elif effect == 'drive':
            x = drive(x, step)
        elif effect == 'dac':
            x = dac(x, step)
        elif effect == 'edge_fades':
            fade = round(step['milliseconds']*RATE/1000)
            x = x.copy()
            x[:fade] *= np.linspace(0, 1, fade)
            x[-fade:] *= np.linspace(1, 0, fade)
        else:
            raise ValueError(f'Unknown effect {effect}')
    return x


def wav(path, samples):
    assert np.isfinite(samples).all()
    assert np.max(np.abs(samples)) < 1, 'Clipped source/output'
    pcm = np.round(samples*32768).astype('<i2')
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes(pcm.tobytes())
    return {'frames': len(pcm), 'seconds': len(pcm)/RATE,
            'peak_dbfs': float(20*np.log10(max(np.max(np.abs(samples)), 1e-9))),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def make_master(bank, raw, edit=None):
    settings = bank['master']
    blob = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(raw), '-ac', '1', '-ar', str(RATE),
                                    '-f', 'f32le', 'pipe:1'])
    clean = loudness(filt(np.frombuffer(blob, '<f4').astype(float), 'highpass', settings['highpass_hz']),
                     settings['loudness'])
    if edit:
        clean = clean[round(edit['start']*RATE):round(edit['end']*RATE)]
    trim = settings['trim']
    audible = np.flatnonzero(np.abs(clean) > 10**(trim['audible_threshold_dbfs']/20))
    if not len(audible):
        raise ValueError('Silent synthesis')
    start = max(0, audible[0]-round(trim['lead_ms']*RATE/1000))
    end = min(len(clean), audible[-1]+round(trim['tail_ms']*RATE/1000))
    return clean[start:end]


def render(bank, raw, targets, master_path, edit=None):
    """One shared master per take, then each (preset, target) rendering; returns reports by preset."""
    require_dsp()
    master = make_master(bank, raw, edit)
    wav(master_path, master)
    source = hashlib.sha256(raw.read_bytes()).hexdigest()
    reports = {}
    for preset, target in targets:
        report = wav(target, process(master, bank['presets'][preset], bank['master']['process_tail_pad_ms']))
        report.update(source_sha256=source, preset=preset, preset_sha256=preset_hash(bank['presets'][preset]))
        reports[preset] = report
    return reports


# ---------------------------------------------------------------- utilities

def write_json(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2)+'\n')
    temporary.replace(path)


def read_key(env_file):
    key = os.environ.get('OPENROUTER_API_KEY', '')
    if not key and env_file:
        key = next((line.split('=', 1)[1].strip().strip('"\'')
                    for line in env_file.read_text().splitlines()
                    if line.strip().startswith('OPENROUTER_API_KEY=')), '')
    return key


class Client:
    """OpenRouter access with redacted errors and an estimate-based per-run budget."""

    def __init__(self, key, budget):
        self.key = key
        self.budget = budget
        self.reserved = 0.0
        self.lock = threading.Lock()

    def reserve(self, usd):
        with self.lock:
            if self.reserved+usd > self.budget:
                raise RuntimeError(f'run budget ${self.budget:.2f} would be exceeded; no request sent')
            self.reserved += usd

    def query(self, endpoint, body=None, audio=False):
        data = json.dumps(body).encode() if body is not None else None
        headers = {'Authorization': 'Bearer '+self.key, 'User-Agent': 'OpenGGF-voice-bank/1.0'}
        if data:
            headers['Content-Type'] = 'application/json'
        try:
            with urllib.request.urlopen(urllib.request.Request(API+endpoint, data=data, headers=headers),
                                        timeout=55) as response:
                if audio:
                    blob = response.read(8*1024*1024+1)
                    if not response.headers.get('Content-Type', '').startswith('audio/') \
                            or not 100 < len(blob) <= 8*1024*1024:
                        raise ValueError('Unexpected audio response; request state preserved')
                    return blob, response.headers.get('X-Generation-Id')
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'OpenRouter HTTP {error.code}') from None

    def usage(self):
        return self.query('key')['data']['usage']

    def redact(self, text):
        return str(text).replace(self.key, '[REDACTED]') if self.key else str(text)

    def speech(self, folder, request, offline):
        """Synthesize once per receipt. Never repeats an in-flight or changed request."""
        folder.mkdir(parents=True, exist_ok=True)
        receipt, raw = folder/'request.json', folder/'raw.mp3'
        if receipt.exists():
            state = json.loads(receipt.read_text())
            if state['request'] != request or state['state'] != 'complete':
                raise ValueError(f'{folder.name}: existing uncertain/changed request; inspect before any paid retry')
            if hashlib.sha256(raw.read_bytes()).hexdigest() != state['source_sha256']:
                raise ValueError(f'{folder.name}: raw source changed')
            return raw
        if offline:
            raise ValueError(f'{folder.name}: no cached source')
        self.reserve(synthesis_cost(request['input']))
        state = {'state': 'in_flight', 'request': request}
        write_json(receipt, state)
        blob, generation = self.query('audio/speech', request, audio=True)
        raw.write_bytes(blob)
        state.update(state='complete', source_sha256=hashlib.sha256(blob).hexdigest(), generation_id=generation)
        write_json(receipt, state)
        return raw

    def transcribe(self, bank, audio_path):
        """Word-timestamped transcription. Plain json mode is not used: on a five-fold repeated
        take it returned one utterance, while verbose_json text listed all five."""
        self.reserve(TRANSCRIPTION_RESERVE_USD)
        mp3 = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(audio_path), '-f', 'mp3',
                                       '-b:a', '128k', 'pipe:1'])
        return self.query('audio/transcriptions', {
            'model': bank['transcription_model'], 'language': 'en', 'temperature': 0,
            'response_format': 'verbose_json', 'timestamp_granularities': ['word', 'segment'],
            'input_audio': {'data': base64.b64encode(mp3).decode(), 'format': 'mp3'}})


def find_takes(timestamps, entry, aliases):
    """Word-aligned spans of complete literal utterances, left to right, non-overlapping."""
    tokens = [''.join(words(item['word'], aliases)) for item in timestamps]
    targets = {''.join(words(candidate, aliases)) for candidate in [entry['text']]+entry.get('accept', [])}
    spans, first = [], 0
    while first < len(tokens):
        joined, found = '', None
        for last in range(first, len(tokens)):
            joined += tokens[last]
            if joined in targets and tokens[first] and tokens[last]:
                found = last
            if not any(target.startswith(joined) for target in targets):
                break
        if found is None:
            first += 1
        else:
            spans.append((first, found))
            first = found+1
    return spans


def take_edit(raw, timed, entry, aliases):
    """Keep the LAST complete literal utterance (v4 sometimes rehearses or repeats a line)."""
    timestamps = timed.get('words', [])
    spans = find_takes(timestamps, entry, aliases)
    if not spans:
        raise ValueError('no complete literal utterance in raw take: '+timed.get('text', ''))
    first, last = spans[-1]
    first_word, last_word = timestamps[first], timestamps[last]
    if last_word['end']-first_word['start'] < .15*(last-first+1):
        # Seen on a five-fold repeated take: one utterance, every word stamped at the clip end.
        raise ValueError('degenerate word timestamps; inspect the raw take: '+timed.get('text', ''))
    detection = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(raw), '-af', 'silencedetect=noise=-38dB:d=0.07',
                                '-f', 'null', '-'], capture_output=True, text=True, check=True).stderr
    starts = [float(v) for v in re.findall(r'silence_start: ([0-9.]+)', detection)]
    ends = [float(v) for v in re.findall(r'silence_end: ([0-9.]+)', detection)]
    # Silence boundaries keep consonants intact even when ASR word times are loose.
    lower = timestamps[first-1]['start'] if first else 0
    before = [end for begin, end in zip(starts, ends)
              if end-begin >= .20 and lower <= end <= first_word['end'] and abs(end-first_word['start']) < 1.4]
    start = max(0, min(before, key=lambda v: abs(v-first_word['start']))-.060) if before else max(0, first_word['start']-.120)
    upper = timestamps[last+1]['end'] if last+1 < len(timestamps) else timed.get('duration', last_word['end']+1)
    after = [v for v in starts if last_word['end']-.05 <= v <= upper and abs(v-last_word['end']) < .65]
    end = min(after, key=lambda v: abs(v-last_word['end']))+.120 if after else last_word['end']+.120
    return {'start': start, 'end': end, 'whole_utterance': entry['text'], 'utterances_in_raw': len(spans),
            'method': 'last complete literal utterance, expanded to adjacent quiet gaps; final processed word check required'}


def cached_edit(client, bank, folder, raw, entry, offline):
    """Whole-utterance edit from a cached or new word-timestamped transcription of the raw take."""
    edit_path = folder/'edit.json'
    if edit_path.exists():
        return json.loads(edit_path.read_text())
    times_path = folder/'raw-transcription.json'
    if times_path.exists():
        timed = json.loads(times_path.read_text())
    elif offline:
        raise ValueError(f'{folder.name}: no cached word timestamps')
    else:
        timed = client.transcribe(bank, raw)
        write_json(times_path, timed)
    edit = take_edit(raw, timed, entry, bank['word_aliases'])
    write_json(edit_path, edit)
    return edit


# ---------------------------------------------------------------- auditions

def auditions(bank, client, args, speakers):
    require_dsp()
    entries = {line_key(e): e for e in bank['entries']}
    jobs = []
    for name in speakers:
        audition = bank['speakers'][name]['audition']
        for voice in audition['voices']:
            for line in audition['lines']:
                jobs.append((name, voice, entries[name+'_'+line], audition['presets']))
    results = []

    def job(name, voice, entry, presets):
        folder = args.cache/'auditions'/name/voice/entry['id']
        request = {'model': bank['model'], 'voice': voice, 'response_format': 'mp3',
                   'input': request_input(bank, entry)}
        raw = client.speech(folder, request, args.render_only)
        # Auditions present the shippable form: the same whole-utterance edit the bank uses.
        # A refused edit is reported and the whole raw take is auditioned instead.
        try:
            edit = cached_edit(client, bank, folder, raw, entry, args.render_only)
        except ValueError as error:
            edit = None
            print(f'{name}/{voice}/{entry["id"]}: edit refused: {error}', flush=True)
        rows = []
        targets = [(preset, args.audition/name/f'{name}-{voice}-{entry["id"]}-{preset}.wav') for preset in presets]
        reports = render(bank, raw, targets, folder/'master.wav', edit)
        for preset, target in targets:
            report = reports[preset]
            check_path = folder/f'check-{preset}.json'
            cached = json.loads(check_path.read_text()) if check_path.exists() else {}
            if cached.get('sha256') != report['sha256'] and not args.render_only:
                cached = {'sha256': report['sha256'], 'transcript': client.transcribe(bank, target).get('text', '')}
                write_json(check_path, cached)
            transcript = cached.get('transcript') if cached.get('sha256') == report['sha256'] else None
            rows.append({'speaker': name, 'voice': voice, 'line': entry['id'], 'text': entry['text'],
                         'preset': preset, 'file': str(target), 'seconds': report['seconds'],
                         'raw_utterances': edit['utterances_in_raw'] if edit else None,
                         'duration_ok': report['seconds'] <= plausible_seconds(entry['text']),
                         'transcript': transcript,
                         'words_ok': None if transcript is None else matches_words(transcript, entry, bank['word_aliases'])})
        return rows

    failures = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = {pool.submit(job, *spec): f'{spec[0]}/{spec[1]}/{spec[2]["id"]}' for spec in jobs}
        for future in as_completed(futures):
            try:
                results.extend(future.result())
                print(f'{futures[future]}: rendered', flush=True)
            except Exception as error:
                failures.append(futures[future])
                print(f'{futures[future]}: {client.redact(error)}', flush=True)
    results.sort(key=lambda r: (r['speaker'], r['voice'], r['line'], r['preset']))
    write_json(args.audition/'auditions.json', {'model': bank['model'], 'rows': results})
    return results, failures


def audition_index(bank, results, usage, path):
    lines = ['# Typing of the Hedgehog voice auditions', '',
             f'Generated by `tools/audio/typing_hedgehog_voice.py --audition` with `{bank["model"]}` '
             'through OpenRouter (ElevenLabs premade voices; original literal performances, no cloning). '
             'Each take is rendered clean and through each candidate preset; the transcript is a blind '
             f'`{bank["transcription_model"]}` check of that rendered file.', '']
    runs = [run for run in usage if run['mode'] == 'audition']
    if runs:
        lines += [f'Key usage reported before the first audition run: ${runs[0]["reported_before"]:.6f}; '
                  f'after the latest ({len(runs)} runs; later runs re-transcribe cached takes): '
                  f'${runs[-1]["reported_after"]:.6f}. Provider usage reporting can lag.', '']
    lines += ['## Presets', '']
    for name, chain in bank['presets'].items():
        summary = '; '.join(', '.join(f'{k}={v}' for k, v in step.items() if k not in ('quantization', 'reconstruction'))
                            for step in chain)
        lines.append(f'- `{name}` ({preset_hash(chain)[:12]}): {summary}')
    for name in sorted({r['speaker'] for r in results}):
        speaker = bank['speakers'][name]
        lines += ['', f'## {name}', '', speaker['role'], '', f'Delivery tag: `{speaker["delivery_tag"]}`', '',
                  '| File | Voice | Line | Preset | Seconds | Raw take | Blind transcript | Words |',
                  '|---|---|---|---|---|---|---|---|']
        for row in (r for r in results if r['speaker'] == name):
            words_ok = {True: 'ok', False: 'CHECK', None: '-'}[row['words_ok']]
            repeats = row['raw_utterances']
            raw_take = ('edit refused; whole raw take' if repeats is None else
                        'single' if repeats == 1 else f'{repeats} repeats; last kept')
            if not row['duration_ok']:
                raw_take += '; TOO LONG (repeat?)'
            lines.append(f'| `{Path(row["file"]).relative_to(path.parent)}` | {row["voice"]} | {row["line"]} | '
                         f'{row["preset"]} | {row["seconds"]:.2f} | {raw_take} | {(row["transcript"] or "").strip()} | {words_ok} |')
    path.write_text('\n'.join(lines)+'\n')


# ---------------------------------------------------------------- bank

def require_approved(bank, speakers):
    for name in speakers:
        speaker = bank['speakers'][name]
        if not speaker.get('voice') or not speaker.get('preset'):
            raise SystemExit(f'{name}: no approved voice/preset yet; run --audition and record the user choice')
        if APPROVED_PRESET_SHA256.get(speaker['preset']) != preset_hash(bank['presets'][speaker['preset']]):
            raise SystemExit(f'{name}: preset {speaker["preset"]} is not an approved recipe; update the tool explicitly')


def generate(bank, client, args, entries):
    require_dsp()
    aliases = bank['word_aliases']

    def job(entry):
        key = line_key(entry)
        speaker = bank['speakers'][entry['speaker']]
        folder = args.cache/'lines'/(key+('-take-'+str(entry['take']) if 'take' in entry else ''))
        request = {'model': bank['model'], 'voice': speaker['voice'], 'response_format': 'mp3',
                   'input': request_input(bank, entry)}
        raw = client.speech(folder, request, args.render_only)
        if args.clean_takes:
            edit = cached_edit(client, bank, folder, raw, entry, args.render_only)
        else:
            edit_path = folder/'edit.json'
            edit = json.loads(edit_path.read_text()) if edit_path.exists() else None
        target = args.output/(key+'.wav')
        report = render(bank, raw, [(speaker['preset'], target)], folder/'clean.wav', edit)[speaker['preset']]
        report_path = folder/'verification.json'
        cached = json.loads(report_path.read_text()) if report_path.exists() else {}
        if cached.get('sha256') == report['sha256'] and 'transcript' in cached:
            report = cached
        elif not args.render_only:
            report['transcript'] = client.transcribe(bank, target).get('text', '')
            report['matches_words'] = matches_words(report['transcript'], entry, aliases)
            report['duration_ok'] = report['seconds'] <= plausible_seconds(entry['text'])
        write_json(report_path, report)
        verdict = 'words OK' if report.get('matches_words') else 'CHECK '+report.get('transcript', 'not transcribed')
        if report.get('duration_ok') is False:
            verdict += f'; TOO LONG for one reading (> {plausible_seconds(entry["text"]):.1f}s)'
        print(f'{key}: {report["seconds"]:.2f}s; {verdict}', flush=True)
        return report

    failures = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = {pool.submit(job, e): line_key(e) for e in entries}
        for future in as_completed(futures):
            try:
                report = future.result()
                if not args.render_only and not (report.get('matches_words') and report.get('duration_ok')):
                    failures.append(futures[future])
            except Exception as error:
                failures.append(futures[future])
                print(f'{futures[future]}: {client.redact(error)}', flush=True)
    return failures


def merge_manifest(path, block):
    """Own only the marked block inside sfx:, preserving any other tracks or SFX."""
    if not path.exists():
        path.write_text('formatVersion: 1\ntracks: []\nsfx:\n'+BEGIN_MARK+block+END_MARK)
        return
    text = path.read_text()
    if text.count(BEGIN_MARK) != 1 or text.count(END_MARK) != 1 or text.index(BEGIN_MARK) > text.index(END_MARK):
        raise SystemExit(f'{path} lacks one generated-voice block; merge the voice SFX entries manually')
    head, rest = text.split(BEGIN_MARK, 1)
    path.write_text(head+BEGIN_MARK+block+rest[rest.index(END_MARK):])


def java_string(text):
    return json.dumps(text, ensure_ascii=True)


def publish(bank, cache, output, enum_path):
    """Freeze checked asset identity and derive queue leases from actual PCM duration."""
    speakers = sorted({e['speaker'] for e in bank['entries']})
    require_approved(bank, speakers)
    aliases = bank['word_aliases']
    metadata = {k: bank[k] for k in ('format_version', 'model', 'transcription_model', 'seed', 'room_reverb_mix', 'master')}
    metadata['origin'] = bank['origin']
    metadata['speakers'] = {name: {k: bank['speakers'][name][k] for k in ('voice', 'delivery_tag', 'preset')}
                            for name in speakers}
    metadata['presets'] = {bank['speakers'][n]['preset']: bank['presets'][bank['speakers'][n]['preset']] for n in speakers}
    metadata['output'] = {'sample_rate': RATE, 'channels': 1, 'container_bits': 16}
    metadata['entries'] = []
    rows, block = [], ''
    for entry in bank['entries']:
        key = line_key(entry)
        speaker = bank['speakers'][entry['speaker']]
        folder = cache/'lines'/(key+('-take-'+str(entry['take']) if 'take' in entry else ''))
        report = json.loads((folder/'verification.json').read_text())
        receipt = json.loads((folder/'request.json').read_text())
        path = output/(key+'.wav')
        assert report.get('matches_words') and matches_words(report['transcript'], entry, aliases), f'{key}: word check not passed'
        assert report.get('duration_ok') and report['seconds'] <= plausible_seconds(entry['text']), f'{key}: too long for one reading'
        assert receipt['state'] == 'complete' and receipt['request']['voice'] == speaker['voice'] \
            and receipt['request']['input'] == request_input(bank, entry), f'{key}: source request is stale'
        assert report['preset'] == speaker['preset'] and report['preset_sha256'] == preset_hash(bank['presets'][speaker['preset']]), f'{key}: stale preset'
        assert hashlib.sha256(path.read_bytes()).hexdigest() == report['sha256'], f'{key}: asset changed'
        assert hashlib.sha256((folder/'raw.mp3').read_bytes()).hexdigest() == report['source_sha256'], f'{key}: raw source changed'
        with wave.open(str(path)) as handle:
            assert (handle.getframerate(), handle.getnchannels(), handle.getsampwidth()) == (RATE, 1, 2), f'{key}: format'
            assert handle.getnframes() == report['frames'], f'{key}: frame count'
            pcm = array('h', handle.readframes(handle.getnframes()))
            assert pcm and max(abs(v) for v in pcm) < 32767 and any(pcm), f'{key}: silent or clipped'
        asset = 'voice-'+key.replace('_', '-')
        duration = (report['frames']+TICK_FRAMES-1)//TICK_FRAMES
        rows.append(f'    {key.upper()}(Speaker.{entry["speaker"].upper()}, "{asset}", {duration}, {entry["priority"]}, '
                    f'{entry["cooldown_ticks"]}, {java_string(entry["taunt_tag"]) if "taunt_tag" in entry else "null"}, '
                    f'{java_string(entry["text"])})')
        block += f'  - id: {asset}\n    assetPath: audio/voice/{key}.wav\n    gain: 1.0\n'
        edit_path = folder/'edit.json'
        metadata['entries'].append({'id': key, 'speaker': entry['speaker'], 'text': entry['text'], 'asset': asset,
            'frames': report['frames'], 'sha256': report['sha256'], 'source_sha256': report['source_sha256'],
            'generation_id': receipt.get('generation_id'), 'peak_dbfs': report['peak_dbfs'],
            'transcript': report['transcript'], 'blind_word_check': True,
            'source_edit': json.loads(edit_path.read_text()) if edit_path.exists() else None})
    write_json(output/'provenance.json', metadata)
    merge_manifest(output.parent/'audio-manifest.yaml', block)
    speaker_constants = ', '.join(name.upper() for name in sorted(bank['speakers']))
    enum_path.parent.mkdir(parents=True, exist_ok=True)
    enum_path.write_text('''package typinghog.audio;

import java.util.HashMap;
import java.util.Map;

/**
 * Generated by tools/audio/typing_hedgehog_voice.py from voice-bank.json; do not edit.
 * Durations are the shipped 48 kHz PCM frame counts rounded up to 60 Hz ticks.
 */
public enum VoiceLine {
'''+',\n'.join(rows)+''';

    /** Who performs a line. */
    public enum Speaker { '''+speaker_constants+''' }

    private static final Map<String, VoiceLine> TAUNTS = new HashMap<>();

    static {
        for (VoiceLine line : values()) {
            if (line.tauntTag != null) {
                TAUNTS.put(line.text, line);
            }
        }
    }

    public final Speaker speaker;
    public final String asset;
    public final int durationTicks;
    public final int priority;
    public final int cooldownTicks;
    /** The taunts.txt tag (any, s1, s2, s3k) of a voiced talk-back taunt; null otherwise. */
    public final String tauntTag;
    /** The literal spoken line; for taunts, identical to the on-screen prompt. */
    public final String text;

    VoiceLine(Speaker speaker, String asset, int durationTicks, int priority, int cooldownTicks,
              String tauntTag, String text) {
        this.speaker = speaker;
        this.asset = asset;
        this.durationTicks = durationTicks;
        this.priority = priority;
        this.cooldownTicks = cooldownTicks;
        this.tauntTag = tauntTag;
        this.text = text;
    }

    /** The voiced line for an exact taunts.txt text, or null when that taunt is not voiced. */
    public static VoiceLine taunt(String text) {
        return TAUNTS.get(text);
    }
}
''')
    print(f'Published {len(rows)} verified clips ({sum(e["frames"] for e in metadata["entries"])/RATE:.1f}s).')


# ---------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--catalogue', type=Path, required=True)
    parser.add_argument('--cache', type=Path, help='External task directory for receipts, raw takes and checks')
    parser.add_argument('--output', type=Path, help='Shipped WAV directory (the mod resources audio/voice)')
    parser.add_argument('--env-file', type=Path)
    parser.add_argument('--budget', type=float, default=1.0, help='Per-run spend cap in USD (estimate-based; default 1.0)')
    parser.add_argument('--check', action='store_true', help='Validate the catalogue and print its synthesis estimate')
    parser.add_argument('--list-voices', action='store_true', help='Print the voices OpenRouter lists for the model')
    parser.add_argument('--usage', action='store_true', help='Print the reported key usage only')
    parser.add_argument('--audition', type=Path, help='Render catalogue auditions into this external directory')
    parser.add_argument('--speakers', nargs='+', help='Restrict auditions or generation to these speakers')
    parser.add_argument('--only', nargs='+', help='Restrict generation to these speaker_id keys')
    parser.add_argument('--render-only', action='store_true', help='Use cached sources only; no paid requests')
    parser.add_argument('--clean-takes', action='store_true', help='Select one complete utterance using blind word times and quiet gaps')
    parser.add_argument('--publish-only', action='store_true', help='Freeze checked files without rendering or paid requests')
    parser.add_argument('--enum', type=Path, help='Generated VoiceLine.java; required for --publish-only')
    args = parser.parse_args()
    bank = json.loads(args.catalogue.read_text(encoding='utf-8'))
    validate(bank, args.catalogue)
    total = sum(synthesis_cost(request_input(bank, e)) for e in bank['entries'])
    if total > bank['synthesis_estimate_cap_usd']:
        raise SystemExit(f'Catalogue synthesis estimate ${total:.2f} exceeds its cap')
    if args.check:
        print(f'{len(bank["entries"])} lines; full-bank synthesis estimate ${total:.3f} (list price)')
        return
    if args.list_voices:
        with urllib.request.urlopen(API+'models?output_modalities=speech', timeout=55) as response:
            model = next(m for m in json.load(response)['data'] if m['id'] == bank['model'])
        print(' '.join(model.get('supported_voices', [])))
        return
    speakers = args.speakers or sorted(bank['speakers'])
    unknown = set(speakers)-set(bank['speakers']) | set(args.only or [])-{line_key(e) for e in bank['entries']}
    if unknown:
        raise SystemExit('Unknown speakers/lines: '+', '.join(sorted(unknown)))
    if args.publish_only:
        if not (args.enum and args.cache and args.output):
            raise SystemExit('--publish-only requires --cache, --output and --enum')
        publish(bank, args.cache, args.output, args.enum)
        return
    key = read_key(args.env_file)
    offline = args.render_only
    if not offline and not key:
        raise SystemExit('Missing private credential')
    client = Client(key, args.budget)
    if args.usage:
        print(f'reported key usage ${client.usage():.6f}')
        return
    if not args.cache:
        raise SystemExit('--cache is required')
    args.cache.mkdir(parents=True, exist_ok=True)
    if args.audition:
        specs = [(n, e) for n in speakers for e in bank['entries'] if e['speaker'] == n
                 and e['id'] in bank['speakers'][n]['audition']['lines']]
        estimate = sum(synthesis_cost(request_input(bank, e))*len(bank['speakers'][n]['audition']['voices'])
                       + TRANSCRIPTION_RESERVE_USD*len(bank['speakers'][n]['audition']['voices'])*len(bank['speakers'][n]['audition']['presets'])
                       for n, e in specs)
    else:
        require_approved(bank, speakers)
        entries = [e for e in bank['entries'] if e['speaker'] in speakers and (not args.only or line_key(e) in args.only)]
        estimate = sum(synthesis_cost(request_input(bank, e)) + 2*TRANSCRIPTION_RESERVE_USD for e in entries)
    before = None
    if not offline:
        before = client.usage()
        if before is None:
            raise SystemExit('Key usage is not reported; refusing paid requests')
        ceiling = min(bank['usage_ceiling_usd'], before+args.budget)
        if before+estimate*2 >= ceiling:
            raise SystemExit(f'Key-usage safety ceiling: reported ${before} + 2 x estimate ${estimate:.3f} '
                             f'reaches ${ceiling:.2f}')
        print(f'Reported usage ${before:.6f}; run estimate ${estimate:.4f} (list price); billing can lag.', flush=True)
    if args.audition:
        args.audition.mkdir(parents=True, exist_ok=True)
        results, failures = auditions(bank, client, args, speakers)
    else:
        args.output.mkdir(parents=True, exist_ok=True)
        failures = generate(bank, client, args, entries)
    log_path = args.cache/'usage.json'
    if not offline:
        after = client.usage()
        log = json.loads(log_path.read_text()) if log_path.exists() else {'runs': []}
        log['runs'].append({'mode': 'audition' if args.audition else 'bank', 'reported_before': before,
                            'reported_after': after, 'estimate': estimate, 'reserved': client.reserved,
                            'note': 'Usage reporting can lag; estimate is not final billing.'})
        write_json(log_path, log)
        print(f'Reported usage after ${after:.6f}', flush=True)
    if args.audition:
        runs = json.loads(log_path.read_text())['runs'] if log_path.exists() else []
        audition_index(bank, results, runs, args.audition/'index.md')
    if failures:
        raise SystemExit('Requires inspection: '+', '.join(sorted(failures)))


if __name__ == '__main__':
    sys.exit(main())
