#!/usr/bin/env python3
"""Rebuild and certify the one shared Godot web engine and bundled game content."""
import hashlib,json,os,shutil,subprocess,tempfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from build_inputs import game_inputs,sha

def build(binary=None):
 
 # Bundled titles pin their own turn helper; new SDK projects can opt into newer recovery behavior.
 for n in ['onklav_game.gd','game_chrome.gd']:
  if (ROOT/'developer/games/templates/shared'/n).read_bytes()!=(ROOT/'games/engine-checks'/n).read_bytes():raise RuntimeError('Shared game control source drift: '+n)
 binary=binary or os.environ.get('GODOT_BIN','godot');before={n:sha(ROOT/n)for n in game_inputs(ROOT)}
 evidence=ROOT/'docs/evidence/game-sdk';evidence.mkdir(parents=True,exist_ok=True)
 def run(name,args):
  p=subprocess.run([binary,'--headless','--path',str(ROOT/'games/engine-checks'),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',timeout=120)
  (evidence/(name+'.log')).write_text(p.stdout)
  if p.returncode or 'SCRIPT ERROR:'in p.stdout or 'ERROR:'in p.stdout:raise RuntimeError(name+': '+p.stdout[-4000:])
 version=subprocess.check_output([binary,'--version'],text=True).strip()
 if not version.startswith('4.7.2.stable.'):raise RuntimeError('Use Godot4.7.2 stable')
 run('import',['--editor','--import']);run('rules',['--script','rules_tests.gd']);run('session',['--script','session_tests.gd']);run('game-repair',['--script','game_repair_tests.gd']);run('dominos',['--script','res://dominos/tests.gd']);run('dominos-layout',['--script','res://dominos/layout_tests.gd']);run('cats-dogs',['--script','res://cat_dog/tests.gd']);run('cats-input-ai',['--script','res://cat_dog/input_ai_tests.gd']);run('blackjack',['--script','res://blackjack/tests.gd']);run('panda',['--script','res://panda/tests.gd']);run('panda-duel',['--script','res://panda/duel_tests.gd']);run('panda-audit',['--script','res://panda/audit_tests.gd']);run('panda-volley',['--script','res://panda/volley_tests.gd']);run('panda-courses',['--script','res://panda/course_tests.gd']);run('panda-performance',['--script','res://panda/performance_tests.gd']);run('naval',['--script','res://naval_battle/tests.gd']);run('hockey',['--fixed-fps','120','--script','res://air_hockey/tests.gd'])
 with tempfile.TemporaryDirectory(prefix='onklav-games-')as temp:
  export=Path(temp);run('export',['--export-release','OnKlav Web',str(export/'index.html')])
  html=export/'index.html'
  text=html.read_text()
  marker='engine.startGame({'
  if text.count(marker)!=1:raise RuntimeError('Unexpected default Godot HTML shell')
  html.write_text(text.replace(marker,marker+'onExit: function() { console.log("ONKLAV_GAME_EXIT"); },'))
  shutil.copy2(ROOT/'developer/games/onklav-controls.json',export/'onklav-controls.json')
  shared=[p for p in export.iterdir()if p.name.endswith('.js')or p.suffix=='.wasm' or p.name=='onklav-controls.json']
  contract=json.loads((ROOT/'developer/games/engine.json').read_text())
  actual={p.name:sha(p)for p in sorted(shared)}
  if actual!=contract['files_sha256']:raise RuntimeError('Export binaries differ from the shared engine contract')
  if sha(ROOT/'developer/games/engine-template.zip')!=contract['export_template_sha256']:raise RuntimeError('Export template differs from shared contract')
  assets=ROOT/'android/games/src/main/assets'
  for folder in ['godot-web','godot-builtin']:
   path=assets/folder;path.mkdir(exist_ok=True)
   for p in path.iterdir():
    if p.is_file():p.unlink()
  for p in export.iterdir():
   if p.is_file():shutil.copy2(p,assets/('godot-web'if p in shared else'godot-builtin')/p.name)
  (assets/'godot-web/engine.json').write_text(json.dumps(contract,indent=2)+'\n')
  legacy=assets/'onklav-games.pck';legacy.unlink(missing_ok=True)
 if before!={n:sha(ROOT/n)for n in game_inputs(ROOT)}:raise RuntimeError('Game source changed during build')
 outputs={p.relative_to(ROOT).as_posix():sha(p)for folder in ['godot-web','godot-builtin']for p in (assets/folder).iterdir()if p.is_file()}
 proof={'scope':'shared Godot4.7.2 web engine for bundled and user-installed games; no owner approval','engine':version,'files_sha256':before,'assets_sha256':outputs,'rules_checks':12,'session_checks':int(__import__('re').search(r'GAME_SESSION_RESULT checks=(\d+) failures=0',(evidence/'session.log').read_text()).group(1)),'hockey_checks':int(__import__('re').search(r'AIR_HOCKEY_RESULT checks=(\d+) failures=0',(evidence/'hockey.log').read_text()).group(1)),'naval_turn_checks':int(__import__('re').search(r'NAVAL_TURN_RESULT checks=(\d+) failures=0',(evidence/'naval.log').read_text()).group(1)),'cats_dogs_checks':int(__import__('re').search(r'CATS_DOGS_RESULT checks=(\d+) failures=0',(evidence/'cats-dogs.log').read_text()).group(1)),'blackjack_checks':int(__import__('re').search(r'BLACKJACK_RESULT checks=(\d+) failures=0',(evidence/'blackjack.log').read_text()).group(1)),'panda_checks':int(__import__('re').search(r'PANDA_RESULT checks=(\d+) failures=0',(evidence/'panda.log').read_text()).group(1)),'dominos_checks':int(__import__('re').search(r'NEON_DOMINOS_RESULT checks=(\d+) failures=0',(evidence/'dominos.log').read_text()).group(1)),'dominos_layout_checks':int(__import__('re').search(r'NEON_LAYOUT_RESULT checks=(\d+) failures=0',(evidence/'dominos-layout.log').read_text()).group(1)),'panda_duel_checks':int(__import__('re').search(r'PANDA_DUEL_RESULT checks=(\d+) failures=0',(evidence/'panda-duel.log').read_text()).group(1)),'panda_audit_checks':int(__import__('re').search(r'PANDA_AUDIT_RESULT checks=(\d+) failures=0',(evidence/'panda-audit.log').read_text()).group(1)),'panda_volley_checks':int(__import__('re').search(r'PANDA_VOLLEY_RESULT checks=(\d+) failures=0',(evidence/'panda-volley.log').read_text()).group(1)),'game_repair_checks':int(__import__('re').search(r'GAME_REPAIR_RESULT checks=(\d+) failures=0',(evidence/'game-repair.log').read_text()).group(1)),'panda_course_checks':int(__import__('re').search(r'PANDA_COURSE_RESULT checks=(\d+) failures=0',(evidence/'panda-courses.log').read_text()).group(1)),'cats_input_ai_checks':int(__import__('re').search(r'CATS_INPUT_AI_RESULT checks=(\d+) failures=0',(evidence/'cats-input-ai.log').read_text()).group(1)),'physical_device_tested':False}
 (ROOT/'docs/games-provenance.json').write_text(json.dumps(proof,indent=2)+'\n');return proof
if __name__=='__main__':print(json.dumps(build(),indent=2))
