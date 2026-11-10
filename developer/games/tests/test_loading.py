import importlib.util,unittest,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('loader',root/'loading.py');loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)
class LoadingTests(unittest.TestCase):
 def test_current_shell_is_idempotent_and_hooks_only_once(self):
  with zipfile.ZipFile(root/'engine-template.zip')as z:html=z.read('godot.html').decode()
  self.assertEqual(loader.theme_html(html),html)
  for marker in ['window.OnKlavLoader.prepare(engine);','window.OnKlavLoader.progress(current, total);','window.OnKlavLoader.fail();','ONKLAV_LOADER_DOM_BEGIN','ONKLAV_LOADER_SCRIPT_BEGIN']:
   self.assertEqual(html.count(marker),1,marker)
  self.assertIn('window.OnKlavLoader.finish(() => statusOverlay.remove());',html)
 def test_logo_is_the_existing_transparent_white_vector(self):
  svg=(root/'templates/shared/loading-logo.svg').read_text()
  self.assertEqual(svg.count('<path'),3);self.assertNotIn('<rect',svg);self.assertNotIn('#603D77',svg)
 def test_studio_splash_is_preserved_only_for_explicit_custom_art(self):
  import tempfile
  with tempfile.TemporaryDirectory()as d:
   p=Path(d);(p/'project.godot').write_text('[application]\nboot_splash/image="res://onklav-splash.png"\n')
   self.assertEqual(loader.configure_brand('<head></head>',p),'<head></head>')
   (p/'project.godot').write_text('[application]\nboot_splash/image="res://studio.png"\n')
   self.assertIn('content="custom"',loader.configure_brand('<head></head>',p))
