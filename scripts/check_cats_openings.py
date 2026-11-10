"""Exhaust all 103 possible first-four-drop winning patterns against the actual AI."""
from pathlib import Path
import os,tempfile,subprocess
root=Path(__file__).resolve().parents[1]
code='''extends SceneTree
const M=preload("res://cat_dog/model.gd")
var patterns:Array=[]
var failed:=0
func permutations(prefix:Array,left:Array):
 if left.is_empty():patterns.append(prefix);return
 for col in left:
  var rest:=left.duplicate();rest.erase(col);permutations(prefix+[col],rest)
func _init():call_deferred("run")
func run():
 for start in range(4):permutations([],range(start,start+4))
 for col in range(7):patterns.append([col,col,col,col])
 for sequence in patterns:
  var m=M.new()
  for col in sequence:
   if m.finished:break
   if not m.apply(0,"F"+str(col)):break
   if m.finished:break
   var before:Array=m.board.duplicate();var reply:int=m.choose_ai()
   if before!=m.board or not m.apply(1,"F"+str(reply)):failed+=1;printerr("illegal AI reply ",sequence);break
  if m.finished and m.winner==0:failed+=1;printerr("FAIL first-four opening ",sequence)
 print("CATS_OPENINGS_RESULT patterns=%d failures=%d"%[patterns.size(),failed]);quit(1 if failed else 0)
'''
with tempfile.TemporaryDirectory(prefix='cats-openings-') as d:
 script=Path(d)/'openings.gd';script.write_text(code)
 result=subprocess.run([os.environ.get('GODOT_BIN','godot'),'--headless','--path',str(root/'games/engine-checks'),'--script',str(script)],capture_output=True,text=True,timeout=120)
 print(result.stdout,result.stderr)
 if result.returncode or 'SCRIPT ERROR:' in result.stdout+result.stderr:raise SystemExit(1)
