"""Signed HTTP origin declarations must fail before generating partial projects."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('sdk_http_tools',ROOT/'developer/onklav.py')
sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)

class HttpPolicy(unittest.TestCase):
    def test_generated_origins_add_the_capability_and_survive_validation(self):
        with tempfile.TemporaryDirectory()as temp:
            project=Path(temp)/'app';sdk.new_project(project,'org.example.http','HTTP app',network_origins=['https://api.example.com'])
            value=sdk.manifest(project)
            self.assertIn('network.http',value['capabilities'])
            self.assertEqual(['https://api.example.com'],value['network']['origins'])
            value['network']['origins']=['https://other.example.com/path'];(project/'manifest.json').write_text(json.dumps(value))
            with self.assertRaises(ValueError):sdk.manifest(project)
    def test_unsafe_or_missing_origins_do_not_create_a_project(self):
        with tempfile.TemporaryDirectory()as temp:
            for index,origins in enumerate([[],['http://example.com'],['https://*.example.com'],['https://api.example.com:443'],['https://127.0.0.1'],['https://user@example.com']]):
                project=Path(temp)/str(index)
                with self.assertRaises(ValueError):sdk.new_project(project,'org.example.http','HTTP app',capabilities=['network.http'],network_origins=origins)
                self.assertFalse(project.exists())
    def test_account_declarations_stay_in_signed_origins_and_reject_secrets(self):
        import copy
        provider={'id':'example','label':'Example','authorization_endpoint':'https://api.example.com/oauth/authorize','token_endpoint':'https://api.example.com/oauth/token','client_id':'public-client','scopes':['openid'],'api_origins':['https://api.example.com']}
        policy={'origins':['https://api.example.com'],'accounts':[provider]}
        sdk.validate_network(policy,True)
        for key,value in [('client_secret','forbidden'),('token_endpoint','https://other.example.com/token'),('authorization_endpoint','https://api.example.com/auth?redirect_uri=other'),('scopes',['openid','openid']),('api_origins',['https://other.example.com'])]:
            bad=copy.deepcopy(policy);bad['accounts'][0][key]=value
            with self.assertRaises(ValueError):sdk.validate_network(bad,True)
        bad=copy.deepcopy(policy);bad['accounts'].append(provider)
        with self.assertRaises(ValueError):sdk.validate_network(bad,True)

    def test_unsigned_or_unused_network_policy_is_rejected(self):
        with self.assertRaises(ValueError):sdk.validate_network({'origins':['https://api.example.com']},False)
        with self.assertRaises(ValueError):sdk.validate_network({'origins':['https://api.example.com']*2},True)

if __name__=='__main__':unittest.main()
