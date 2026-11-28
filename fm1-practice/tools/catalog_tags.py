#!/usr/bin/env python3
"""Derive review tags from public GitHub metadata, never from a repository name.
The tags are triage hints, not proof of firmware behavior or compatibility.
Usage: python catalog_tags.py metadata.zip output.json
"""
import json,zipfile,hashlib,re,sys
from pathlib import Path
rules={
 'Groovebox':r'\bgroove[ -]?box\b|\bbeat[ -]?making\b|\bmusic workstation\b',
 'Secuenciador / tracker':r'\b(?:sequencer|sequencing|tracker|sequenz(?:er|ierung)|séquenceur)\b',
 'Sintetizador':r'\b(?:synth(?:esizer|esiser|èse|s)?|synthesis|synthétique|synthe|sintetizador|oscillators?|wavetable|monosynth|polysynth|piano|drawbar organ|sound engine|sound engines|FM synthesis|virtual analog|physical modell?ing)\b',
 'Generativo / ambient':r'\b(?:generative|generativ\w*|generativo|ambient|drones?|soundscapes?)\b',
 'Percusión':r'\b(?:drum machine|drum synth\w*|drum kit|percussion|drum engines?|drum sounds?|drums|rhythm machine|beatbox)\b',
 'Sampler / muestras':r'\b(?:sampler|sample player|sample playback|sample upload|sample import|sample recording|sample chopping|sample tools|sample slicing|sampling|granular)\b',
 'Acordes / arpegiador':r'\b(?:chords?|chord harp|arpeggiator|arpeggios?|omnichord|harmonizer|harmoniser)\b',
 'Herramienta / desarrollo':r'\b(?:firmware downloader|firmware flasher|firmware updater|firmware installer|development kit|firmware template|firmware base|base and template|build your own|reverse engineering|SDK for|MIDI monitor|MIDI utility|diagnostic tool|editor for|companion app|android companion|firmware management)\b|^\s*(?:an?\s+)?(?:emulator|simulator|editor|flasher|MIDI controller)\b',
 'Juego':r'\b(?:game|video game|arcade|tetris|snake|doom|pong|chess|puzzle|tamagochi|tamagotchi)\b',
}
genres={
 'Techno':r'\btechno\b','Acid':r'\bacid\b|\bTB[- ]?303\b','House':r'\bhouse\b','Ambient':r'\bambient\b','Dub / reggae':r'\b(?:dub|reggae)\b','Drum & Bass':r'\b(?:drum\s*(?:and|&|n)\s*bass|dnb)\b','Breakbeat':r'\b(?:breakbeat|jungle|breaks)\b','Chiptune':r'\b(?:chiptune|C64 SID|8.bit music)\b','Dungeon synth':r'\bdungeon synth\b','Industrial':r'\bindustrial\b','Psytrance':r'\b(?:psytrance|psychedelic trance)\b','Trance':r'\btrance\b','Berlin School':r'\bberlin school\b','Jazz':r'\bjazz\b','Lo-fi':r'\blo[- ]?fi\b','Metal':r'\bmetal\b','Hip hop':r'\bhip[- ]?hop\b','Synthwave':r'\bsynthwave\b','Progressive':r'\bprogressive\s+(?:house|techno|trance)\b','Minimal':r'\bminimal\s+(?:techno|house)\b'}
def classify(metadata):
    d=(metadata.get('description') or '').strip()
    labels=[k for k,p in rules.items() if re.search(p,d,re.I)]
    if 'Juego' in labels and len(set(labels)-{'Juego','Herramienta / desarrollo'}) and not re.search(r'\b(?:game|arcade|tetris|snake|pong|chess|puzzle|tamagotchi)\b',d,re.I): labels.remove('Juego')
    declared=[k for k,p in genres.items() if re.search(p,d,re.I)]
    other=bool(re.search(r'\b(?:Pocket Amp|Donner|SMK[- ]?37|SMC[- ]?PAD)\b',d,re.I) and not re.search(r'\bFM[- ]?1\b',d,re.I))
    return {'tags':labels,'declared_genres':declared,'has_description':bool(d),'archived':bool(metadata.get('isArchived')),'fork':bool(metadata.get('isFork')),'parent':(metadata.get('parent') or {}).get('nameWithOwner',''),'other_device_warning':other}
def main(src,dst):
    with zipfile.ZipFile(src) as z:rows=json.loads(z.read('METADATA.json'))
    out={r['repository'].lower():classify(r['metadata']) for r in rows if r.get('metadata')}
    payload={'source':'GitHub GraphQL · FM1 PRACTICE catalog metadata audit','artifact_sha256':hashlib.sha256(Path(src).read_bytes()).hexdigest(),'records':out}
    Path(dst).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n')
    print('metadata',len(out),'descriptions',sum(r['has_description'] for r in out.values()),'with tags',sum(bool(r['tags']) for r in out.values()))
if __name__=='__main__':main(*sys.argv[1:])
