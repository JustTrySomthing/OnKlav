#!/usr/bin/env python3
"""One portable authoring smoke check; no phone, permanent key or host rebuild."""
import importlib.util,json,tempfile
from pathlib import Path
p=Path(__file__).with_name('onklav.py');spec=importlib.util.spec_from_file_location('onklav_tools',p);sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
def main():
    tools=sdk.doctor()
    with tempfile.TemporaryDirectory(prefix='onklav-adk-smoke-') as tmp:
        root=Path(tmp);original=root/'original';sdk.new_project(original,'org.example.adksmoke','ADK smoke','form')
        moved=root/'moved app';original.rename(moved);result=sdk.verify_project(moved)
        assert result['scenario']['passed']==4
        assert not result['shipping_package_created'] and not (moved/'dist').exists()
        print(json.dumps({'doctor':tools,'portable_form':result,'phone_verified':False},indent=2))
if __name__=='__main__':main()
