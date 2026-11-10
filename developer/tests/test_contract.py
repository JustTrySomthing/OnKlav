"""Keep the published SDK reference and standalone source contents honest."""
import json
from pathlib import Path
import re
import tomllib
import unittest
ROOT=Path(__file__).resolve().parents[2]
class PublishedContract(unittest.TestCase):
    def test_capabilities_and_widgets_match_production_validation(self):
        source=(ROOT/'core/src/sdk.rs').read_text();contract=json.loads((ROOT/'developer/contract.json').read_text())
        caps=re.search(r'const CAPS:\[&str;\d+\]=\[(.*?)\];',source).group(1)
        widgets=re.search(r'if !\[(.*?)\]\.contains\(&kind\)',source).group(1)
        self.assertEqual(set(re.findall(r'"([^"]+)"',caps)),set(contract['capabilities']))
        self.assertEqual(set(re.findall(r'"([^"]+)"',widgets)),set(contract['widgets']))
        guide=(ROOT/'developer/docs/API.md').read_text()
        for item in sorted(set(contract['capabilities'])-set(contract.get('unreleased_capabilities',[])))+contract['widgets']:self.assertIn('`'+item+'`',guide)
    def test_developer_links_and_required_sources_exist(self):
        for name in ['developer/onklav.py','developer/host/Cargo.toml','developer/host/Cargo.lock','developer/host/src/main.rs','developer/templates/basic/Cargo.lock','developer/templates/basic/src/lib.rs','sdk/rust/src/lib.rs','core/Cargo.lock','core/src/bin/onklav-pack.rs']:
            self.assertTrue((ROOT/name).is_file(),name)
        for p in [ROOT/'sdk/README.md',*(ROOT/'developer').rglob('*.md')]:
            if 'target' in p.relative_to(ROOT).parts:continue
            for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text()):
                if '://' in target or target.startswith('#'):continue
                self.assertTrue((p.parent/target.split('#')[0]).exists(),f'{p}: {target}')
    def test_included_apps_use_the_public_controller_crate(self):
        for name in ('notes','files','rss','reader','browser','youtube','annotation','social','walkie'):
            wrapper=(ROOT/f'sdk/apps/{name}/src/lib.rs').read_text()
            exported=(ROOT/f'developer/components/src/reference_apps/{name}.rs').read_text()
            self.assertIn(f'onklav_ui::reference_apps::{name}::controller',wrapper,name)
            self.assertLess(len(wrapper),400,name)
            self.assertIn('pub fn controller',exported)
            deps=tomllib.loads((ROOT/f'sdk/apps/{name}/Cargo.toml').read_text())['dependencies']
            self.assertEqual('../../../developer/components',deps['onklav-ui']['path'])
        self.assertTrue((ROOT/'developer/components/Cargo.lock').is_file())
    def test_build_icon_and_catalog_inventory_match_the_runtime(self):
        source=(ROOT/'core/src/sdk.rs').read_text();contract=json.loads((ROOT/'developer/contract.json').read_text())
        symbols=re.search(r'DESKTOP_SYMBOLS:\[&str;\d+\]=\[(.*?)\];',source).group(1)
        self.assertEqual(set(re.findall(r'"([^"]+)"',symbols)),set(contract['desktop_symbols']))
        build=int(re.search(r'HOST_BUILD:u32=(\d+)',source).group(1));self.assertEqual(build,contract['reference_host_build'])
        kotlin=(ROOT/'android/app/build.gradle.kts').read_text() if (ROOT/'android/app/build.gradle.kts').exists() else ''
        # APK update codes advance independently from the SDK protocol build.
        if kotlin:self.assertGreater(int(re.search(r'versionCode=(\d+)',kotlin).group(1)),0)
        catalog=json.loads((ROOT/'developer/catalog.json').read_text());self.assertEqual(set(contract['widgets']),{i['id'] for i in catalog['components'] if i['kind']=='widget'})
        for item in catalog['components']:self.assertTrue((ROOT/'developer'/item['guide']).exists())
    def test_harness_uses_the_same_locked_runtime_dependency_versions(self):
        a=tomllib.loads((ROOT/'core/Cargo.lock').read_text())['package']
        b=tomllib.loads((ROOT/'developer/host/Cargo.lock').read_text())['package']
        versions={(p['name'],p['version'],p.get('checksum')) for p in a}
        for p in b:
            if p['name'] in ('onklav-sdk-host','onklav-ui','onklav-app'):continue
            self.assertIn((p['name'],p['version'],p.get('checksum')),versions)
if __name__=='__main__':unittest.main(verbosity=2)
