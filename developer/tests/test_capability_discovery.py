"""Discovery must distinguish inventory from authorization and unreleased services."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('sdk', ROOT/'developer/onklav.py')
sdk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sdk)

class Discovery(unittest.TestCase):
    def test_working_services_are_not_promoted_to_released(self):
        released = sdk.capabilities('camera.capture', 'released')
        self.assertFalse(released['capabilities'][0]['available_in_snapshot'])
        self.assertTrue(sdk.capabilities('camera.capture', 'working')['capabilities'][0]['available_in_snapshot'])
        self.assertFalse(released['permissions_checked'])
        self.assertFalse(released['device_probed'])

    def test_current_snapshot_is_available_without_claiming_device_qualification(self):
        current=sdk.capabilities('camera.capture')
        self.assertEqual('current',current['channel'])
        self.assertTrue(current['capabilities'][0]['available_in_snapshot'])
        self.assertIn('preview',current['capabilities'][0]['qualification'])
        self.assertEqual(96,current['host_protocol_build'])
        self.assertEqual(151,current['reference_apk']['version_code'])
        self.assertFalse(current['device_probed'])

    def test_unknown_permissions_and_channels_fail_closed(self):
        for name in ('bluetooth.connect', 'android.context', 'network.socket'):
            with self.assertRaisesRegex(ValueError, 'Unsupported capability'):
                sdk.capabilities(name)
        with self.assertRaises(ValueError):
            sdk.capabilities(channel='future')

    def test_all_capabilities_have_scope_and_extension_boundaries(self):
        report = sdk.capabilities(channel='working')
        contract = json.loads((ROOT/'developer/contract.json').read_text())
        self.assertEqual(set(contract['capabilities']), {row['id'] for row in report['capabilities']})
        self.assertEqual(set(contract['capabilities']), set(contract['capability_details']))
        for row in report['capabilities']:
            self.assertIn(row['channel'], ('working', 'released'))
            self.assertTrue(row['notes'])
            self.assertIsInstance(row['platform_prerequisites'], list)
        for flag in ('dynamic_native_plugins', 'arbitrary_wasm_imports', 'guest_android_context'):
            self.assertFalse(report['extensions'][flag])

    def test_native_effect_inventory_matches_production_dispatch(self):
        source = (ROOT/'core/src/sdk.rs').read_text()
        contract = json.loads((ROOT/'developer/contract.json').read_text())
        for effect in contract['host_effects']:
            self.assertIn('"'+effect+'"', source)
        for effect in ('camera.capture', 'secrets.get', 'secrets.set', 'secrets.delete',
                       'notifications.show', 'notifications.schedule', 'notifications.cancel'):
            self.assertIn(effect, contract['host_effects'])

    def test_cli_reports_working_scope_and_rejects_unknown(self):
        command = [sys.executable, str(ROOT/'developer/onklav.py'), 'capabilities']
        result = subprocess.run(command+['notifications.post', '--channel', 'working'], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertTrue(report['capabilities'][0]['available_in_snapshot'])
        bad = subprocess.run(command+['bluetooth.connect'], capture_output=True, text=True, timeout=10)
        self.assertNotEqual(bad.returncode, 0)
        self.assertIn('Unsupported capability', bad.stderr)

if __name__ == '__main__':
    unittest.main()
