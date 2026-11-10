#!/usr/bin/env python3
"""Run a fixed Sporeworks route in an instrumented, non-shipping Web export."""
import argparse
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "developer/games"))
from gamekit import export_web

PROBE = """
var trace_start := 0
var trace_last := 0
var trace_room := -1
var trace_events := []
var trace_frames := []
var trace_callback: JavaScriptObject

func trace_mark(name: String) -> void:
	if trace_start != 0:
		trace_events.append([name,(Time.get_ticks_usec()-trace_start)/1000.0])

func _process(_delta: float) -> void:
	if trace_start == 0:
		return
	var now := Time.get_ticks_usec()
	trace_frames.append([(now-trace_start)/1000.0,(now-trace_last)/1000.0])
	trace_last = now
	if phase == "combat":
		var current := expedition.room_at(player.position)
		if current != trace_room:
			trace_room = current
			trace_mark("room_%d" % current)

func install_trace() -> void:
	if not OS.has_feature("web"): return
	trace_callback = JavaScriptBridge.create_callback(func(_args): run_trace())
	JavaScriptBridge.get_interface("window").shroomTrace = trace_callback

func trace_wait(seconds: float) -> void:
	await get_tree().create_timer(seconds).timeout

func run_trace() -> void:
	if trace_start != 0:
		return
	OnKlavGame.session_started = true
	OnKlavGame.resume_game()
	phase = "upgrade"
	stage = 1
	seed_code = 14731
	expedition_mode = true
	advance(0)
	trace_start = Time.get_ticks_usec()
	trace_last = trace_start
	for frame in range(4):
		await get_tree().process_frame
	trace_mark("combat")
	begin_expedition()
	await trace_wait(1.0)
	fire_tap = true
	await trace_wait(.8)
	for goal in [4,1]:
		var route := expedition.paths.get_id_path(expedition.room_at(player.position),goal)
		for index in range(1,route.size()):
			var target := expedition.center(route[index])
			var origin := player.position
			player.rotation.y = atan2(origin.x-target.x,origin.z-target.z)
			for step in range(1,25):
				await get_tree().physics_frame
				player.position = origin.lerp(target,step/24.0)
			await trace_wait(.35)
	await trace_wait(1.7)
	for relay in objective_nodes:
		player.position = relay.position
		await trace_wait(.42)
	await trace_wait(2.0)
	trace_start = 0
	var result := {"events":trace_events,"frames":trace_frames,"render_size":[world.size.x,world.size.y],
		"objectives_complete":objectives_complete(),"phase":phase}
	JavaScriptBridge.eval("window.shroomTraceResult="+JSON.stringify(result))
"""

BROWSER = r"""
const {chromium} = require(process.argv[1]);
(async () => {
  const browser = await chromium.launch({executablePath:process.argv[2], args:[
    '--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader',
    '--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required'
  ]});
  try {
    const page = await browser.newPage({viewport:{width:960,height:540},deviceScaleFactor:2,hasTouch:true});
    const errors=[];
    page.on('pageerror',error=>errors.push(String(error)));
    page.on('console',msg=>{if(msg.text().includes('SCRIPT ERROR:'))errors.push(msg.text());});
    await page.goto(process.argv[3]);
    await page.waitForFunction('typeof shroomTrace === "function"',{},{timeout:40000});
    await page.waitForTimeout(1600);
    await page.evaluate('shroomTrace()');
    await page.waitForFunction('window.shroomTraceResult',{}, {timeout:45000});
    const result=await page.evaluate('window.shroomTraceResult');
    if(errors.length) throw Error(errors.join('\n'));
    const names=result.events.map(event=>event[0]);
    for(const event of ['shot','steam','restart']) if(!names.includes(event)) throw Error('Missing '+event+': '+names+' phase='+result.phase+' frames='+result.frames.length);
    if(names.filter(name=>name.startsWith('room_')).length<3 || !result.objectives_complete)
      throw Error('Incomplete Sporeworks route: '+names);
    process.stdout.write(JSON.stringify(result)+'\n');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
"""


def instrument(project):
    source = project / "main.gd"
    code = source.read_text()
    replacements = {
        "func _ready() -> void:\n": "func _ready() -> void:\n\tcall_deferred(\"install_trace\")\n",
        "\trounds[weapon]-=1\n": '\ttrace_mark("shot")\n\trounds[weapon]-=1\n',
        "\tsporeworks_steam_live = live\n": '\tif live and not sporeworks_steam_live: trace_mark("steam")\n\tsporeworks_steam_live = live\n',
        '\t\t\t\t\tshow_banner("VENTILATION ONLINE': '\t\t\t\t\ttrace_mark("restart")\n\t\t\t\t\tshow_banner("VENTILATION ONLINE',
    }
    for before, after in replacements.items():
        if code.count(before) != 1:
            raise ValueError(f"Cannot place a unique timing marker at {before!r}")
        code = code.replace(before, after)
    source.write_text(code + PROBE)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--godot", required=True)
    parser.add_argument("--chromium", required=True)
    parser.add_argument("--playwright", required=True, help="Path to the Node Playwright package")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--baseline-main", type=Path, help="Optional baseline main.gd for an A/B run")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="shroom-perf-") as directory:
        project = Path(directory) / "project"
        web = Path(directory) / "web"
        shutil.copytree(ROOT / "games/shroom", project, ignore=shutil.ignore_patterns(".godot"))
        if args.baseline_main:
            shutil.copyfile(args.baseline_main, project / "main.gd")
        instrument(project)
        export_web(project, web, args.godot)
        handler = functools.partial(SimpleHTTPRequestHandler, directory=web)
        with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                url = f"http://127.0.0.1:{server.server_port}/index.html"
                result = subprocess.check_output(
                    ["node", "-e", BROWSER, args.playwright, args.chromium, url], timeout=100)
                args.output.write_bytes(result)
            finally:
                server.shutdown()
                worker.join()


if __name__ == "__main__":
    main()
