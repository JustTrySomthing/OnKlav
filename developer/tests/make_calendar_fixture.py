"""Build the signed all-day agenda/editor JNI fixture from public SDK components."""
import importlib.util, tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('calendar_sdk',HERE/'onklav.py')
sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-calendar-') as temp:
 root=Path(temp);p=root/'app'
 sdk.new_project(p,'org.example.calendarfixture','Calendar fixture',template_name='social',capabilities=['storage.private'])
 (p/'src/lib.rs').write_text((HERE/'tests/fixtures/calendar_ui_app.rs').read_text())
 key=root/'fixture.key';sdk.packer('keygen',key)
 output=root/'calendar.onklav-a';sdk.build_project(p,key,output)
 (HERE.parent/'docs/fixtures/calendar-wip.onklav-a').write_bytes(output.read_bytes())
