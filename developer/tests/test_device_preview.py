import importlib.util,tempfile,unittest,json
from pathlib import Path
from unittest.mock import patch
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('preview_device_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
class DevicePreview(unittest.TestCase):
 def test_missing_device_tools_and_bad_serial_fail_before_building(self):
  with patch.object(sdk,'packer',side_effect=AssertionError('build started')):
   with self.assertRaises(ValueError):sdk.device_preview(Path('/unused'),'../invalid')
   with patch.object(sdk.shutil,'which',return_value=None):
    with self.assertRaisesRegex(ValueError,'adb'):sdk.device_preview(Path('/unused'),'device-one')
 def test_feedback_is_read_from_private_device_storage_without_building(self):
  calls=[]
  def execute(command,**kw):
   calls.append(command)
   return '{"phase":"waiting_for_device","service":"camera.capture"}'if'cat'in command else 'device'
  with patch.object(sdk.shutil,'which',return_value='/adb'),patch.object(sdk,'execute',side_effect=execute),patch.object(sdk,'packer',side_effect=AssertionError('build started')):
   result=sdk.device_preview(Path('/unused'),'device-one',feedback=True)
  self.assertEqual('camera.capture',result['service']);self.assertTrue(any('run-as'in c and'cat'in c for c in calls))
if __name__=='__main__':unittest.main(verbosity=2)
