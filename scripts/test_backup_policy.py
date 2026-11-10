"""Private app data stays outside OS automatic backup and device transfer."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
class PrivateBackupPolicyTest(unittest.TestCase):
 def test_every_private_domain_is_excluded_from_both_automatic_modes(self):
  app=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot().find('application')
  ns='{http://schemas.android.com/apk/res/android}'
  self.assertEqual('false',app.attrib[ns+'allowBackup'])
  self.assertEqual('false',app.attrib[ns+'fullBackupContent'])
  self.assertEqual('@xml/data_extraction_rules',app.attrib[ns+'dataExtractionRules'])
  rules=ET.parse(ROOT/'android/app/src/main/res/xml/data_extraction_rules.xml').getroot()
  expected={'root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref'}
  for mode in ['cloud-backup','device-transfer']:
   branch=rules.find(mode);self.assertIsNotNone(branch);self.assertEqual([],branch.findall('include'))
   self.assertEqual(expected,{node.attrib['domain'] for node in branch.findall('exclude')})
   self.assertTrue(all(node.attrib['path']=='.' for node in branch.findall('exclude')))
