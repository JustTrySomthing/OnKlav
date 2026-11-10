import copy
import hashlib
import json
from pathlib import Path
import tempfile
import shutil
import unittest
import zipfile
from build_inputs import sha, sdk_current
from sdk_build_plan import APPS,FLAGS,inputs,plan,validate_update,app_inputs


class SelectiveSdkBuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        def put(name,data=b'source'):
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data);return p
        self.put=put;put('sdk/rust/src/lib.rs');put('sdk/rust/Cargo.toml')
        packages={}
        for name,(folder,_) in APPS.items():
            manifest={'id':'app.test.'+name.lower(),'publisher':'a'*64,'version':1}
            put(folder+'/manifest.json',json.dumps(manifest).encode());put(folder+'/src/lib.rs');put(folder+'/Cargo.toml');put(folder+'/Cargo.lock')
            wasm=b'\0asm'+name.encode();p=put(f'sdk/packages/{name}.onklav-a',b'')
            with zipfile.ZipFile(p,'w') as z:
                z.writestr('manifest.json',json.dumps(manifest));z.writestr('main.wasm',wasm);z.writestr('signature.ed25519',b's'*64)
            packages[name]={'path':p.relative_to(self.root).as_posix(),'sha256':sha(p),'wasm_sha256':hashlib.sha256(wasm).hexdigest(),'manifest':manifest}
            if name not in {'Counter','Annotation','Notes'}:put(f'android/app/src/main/assets/sdk/{name}.onklav-a',p.read_bytes())
        self.proof={'inputs_sha256':inputs(self.root),'packages':packages,'flags':FLAGS}

    def stale(self,proof=None):
        return {n for n,d in plan(self.root,proof or self.proof)[1].items() if d['build']}

    def test_unchanged_legacy_proof_reuses_every_package(self):
        self.assertEqual(set(),self.stale())

    def test_retired_annotation_is_archived_but_never_restored_to_host(self):
        self.write_proof();self.assertTrue(sdk_current(self.root))
        self.assertFalse(plan(self.root,self.proof)[1]['Annotation']['restore_bundle'])
        self.put('android/app/src/main/assets/sdk/Annotation.onklav-a',b'old bundled app')
        self.assertTrue(plan(self.root,self.proof)[1]['Annotation']['remove_bundle'])
        self.assertFalse(sdk_current(self.root));self.assertNotIn('Annotation',self.stale())

    def test_standalone_sdk_has_no_host_asset_copy_requirement(self):
        shutil.rmtree(self.root/'android')
        states=plan(self.root,self.proof)[1]
        self.assertFalse(any(x['build'] or x['restore_bundle'] for x in states.values()))

    def test_one_app_edit_rebuilds_only_that_app(self):
        self.put('sdk/apps/notes/src/lib.rs',b'edited');self.assertEqual({'Notes'},self.stale())

    def test_shared_sdk_or_unknown_source_edit_rebuilds_all(self):
        self.put('sdk/rust/src/lib.rs',b'edited');self.assertEqual(set(APPS),self.stale())
        self.put('sdk/rust/src/lib.rs');self.put('sdk/new_shared.rs');self.assertEqual(set(APPS),self.stale())

    def test_shared_gui_changes_rebuild_included_apps_without_touching_counter(self):
        self.put('developer/components/src/lib.rs',b'changed shared GUI')
        self.assertEqual(set(APPS)-{'Counter'},self.stale())

    def test_new_local_source_and_manifest_change_are_detected(self):
        self.put('sdk/apps/rss/src/new.rs');self.assertEqual({'RSS'},self.stale())
        self.put('sdk/apps/notes/manifest.json',b'{}');self.assertEqual({'RSS','Notes'},self.stale())

    def test_damaged_output_rebuilds_only_owner_but_bundle_damage_only_recopies(self):
        self.put('sdk/packages/Notes.onklav-a',b'damaged');self.assertEqual({'Notes'},self.stale())
        self.put('android/app/src/main/assets/sdk/RSS.onklav-a',b'old')
        self.assertTrue(plan(self.root,self.proof)[1]['RSS']['restore_bundle'])
        self.assertNotIn('RSS',self.stale())

    def test_partial_build_cannot_certify_another_stale_app(self):
        self.put('sdk/apps/notes/src/lib.rs',b'notes');self.put('sdk/apps/rss/src/lib.rs',b'rss')
        p=copy.deepcopy(self.proof)
        p['package_inputs_sha256']={n:app_inputs(p['inputs_sha256'],n) for n in APPS}
        p['package_inputs_sha256']['Notes']=app_inputs(inputs(self.root),'Notes')
        self.assertEqual({'RSS'},self.stale(p))

    def test_partial_flags_upgrade_keeps_other_packages_stale(self):
        p=copy.deepcopy(self.proof);p['flags']='old'
        p['package_flags']={n:'old' for n in APPS};p['flags']=FLAGS;p['package_flags']['Notes']=FLAGS
        self.assertEqual(set(APPS)-{'Notes'},self.stale(p))

    def write_proof(self, proof=None):
        self.put('docs/app-sdk-provenance.json', json.dumps(self.proof if proof is None else proof).encode())

    def test_complete_freshness_rejects_missing_package_and_new_sources(self):
        self.write_proof(); self.assertTrue(sdk_current(self.root))
        p=copy.deepcopy(self.proof);del p['packages']['Notes']
        self.write_proof(p);self.assertFalse(sdk_current(self.root))
        self.write_proof();self.put('sdk/apps/notes/src/new.rs')
        self.assertFalse(sdk_current(self.root))

    def test_complete_freshness_rejects_stale_per_package_proof_and_bundle(self):
        self.write_proof();self.assertTrue(sdk_current(self.root))
        p=copy.deepcopy(self.proof);p['package_inputs_sha256']={n:app_inputs(p['inputs_sha256'],n) for n in APPS}
        p['package_inputs_sha256']['Notes']={}
        self.write_proof(p);self.assertFalse(sdk_current(self.root))
        self.write_proof();self.put('android/app/src/main/assets/sdk/Notes.onklav-a',b'old')
        self.assertFalse(sdk_current(self.root))

    def test_complete_freshness_accepts_standalone_starter(self):
        self.write_proof();shutil.rmtree(self.root/'android')
        self.assertTrue(sdk_current(self.root))

    def test_duplicate_archive_entries_are_not_reusable(self):
        p=self.root/'sdk/packages/Notes.onklav-a'
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore',UserWarning)
            with zipfile.ZipFile(p,'a') as z:z.writestr('manifest.json',json.dumps(self.proof['packages']['Notes']['manifest']))
        self.proof['packages']['Notes']['sha256']=sha(p)
        self.assertEqual({'Notes'},self.stale())

    def test_malformed_package_records_fail_closed(self):
        self.write_proof({'packages':list(APPS),'flags':FLAGS})
        self.assertFalse(sdk_current(self.root))
        p=copy.deepcopy(self.proof);p['packages']['Notes']=None
        self.write_proof(p);self.assertFalse(sdk_current(self.root))

    def test_changed_program_requires_version_bump_and_same_publisher(self):
        old=self.proof['packages']['Notes'];m=dict(old['manifest'])
        with self.assertRaises(ValueError):validate_update(old,m,'different')
        renamed=dict(m,name='Renamed')
        with self.assertRaises(ValueError):validate_update(old,renamed,old['wasm_sha256'])
        with self.assertRaises(ValueError):validate_update(old,m,old['wasm_sha256'],'changed archive')
        m['version']=2;validate_update(old,m,'different')
        m['publisher']='b'*64
        with self.assertRaises(ValueError):validate_update(old,m,'different')


if __name__=='__main__':unittest.main()
