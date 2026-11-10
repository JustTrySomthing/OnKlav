"""Apply the canonical animated loader without modifying shared JS/WASM engine bytes."""
from pathlib import Path
import zipfile,os,tempfile,re
ROOT=Path(__file__).resolve().parent
START='/* ONKLAV_LOADING_THEME_BEGIN */'
END='/* ONKLAV_LOADING_THEME_END */'
def theme_html(text):
    css=(ROOT/'templates/shared/loading.css').read_text()
    markup=(ROOT/'templates/shared/loading.html').read_text()
    script=(ROOT/'templates/shared/loading.js').read_text()
    text=re.sub(re.escape(START)+r'.*?'+re.escape(END)+r'\n?','',text,flags=re.S)
    text=re.sub(r'<!-- ONKLAV_LOADER_DOM_BEGIN -->.*?<!-- ONKLAV_LOADER_DOM_END -->\n?','',text,flags=re.S)
    text=re.sub(r'<!-- ONKLAV_LOADER_SCRIPT_BEGIN -->.*?<!-- ONKLAV_LOADER_SCRIPT_END -->\n?','',text,flags=re.S)
    if text.count('</style>')!=1:raise ValueError('Unexpected shared export HTML style block')
    text=text.replace('</style>',START+'\n'+css+END+'\n</style>')
    marker='<img id="status-splash"'
    if text.count(marker)!=1:raise ValueError('Missing shared splash image')
    text=text.replace(marker,'<!-- ONKLAV_LOADER_DOM_BEGIN -->\n'+markup+'<!-- ONKLAV_LOADER_DOM_END -->\n'+marker)
    marker='<script src="$GODOT_URL"></script>'
    if text.count(marker)!=1:raise ValueError('Missing shared engine script')
    text=text.replace(marker,'<!-- ONKLAV_LOADER_SCRIPT_BEGIN -->\n<script>\n'+script+'\n</script>\n<!-- ONKLAV_LOADER_SCRIPT_END -->\n'+marker)
    hook='window.OnKlavLoader.prepare(engine);'
    if hook not in text:text=text.replace('const engine = new Engine(GODOT_CONFIG);','const engine = new Engine(GODOT_CONFIG);\n'+hook)
    text=text.replace('statusOverlay.remove();','window.OnKlavLoader.finish(() => statusOverlay.remove());')if 'window.OnKlavLoader.finish('not in text else text
    marker='function displayFailureNotice(err) {'
    if 'window.OnKlavLoader.fail();'not in text:text=text.replace(marker,marker+'\n\t\twindow.OnKlavLoader.fail();')
    marker="'onProgress': function (current, total) {"
    if 'window.OnKlavLoader.progress(current, total);'not in text:text=text.replace(marker,marker+'\n\t\t\t\twindow.OnKlavLoader.progress(current, total);')
    return text

def configure_brand(text,project):
    """SDK exports with an explicit studio splash keep their original image and backdrop."""
    config=(Path(project)/'project.godot').read_text()
    match=re.search(r'^boot_splash/image="([^"]*)"',config,re.M)
    if match and match[1]not in ('res://onklav-splash.png','res://onklav-splash.svg'):
        text=text.replace('</head>','<meta name="onklav-loader" content="custom">\n</head>')
    return text

def theme_template(path):
    path=Path(path)
    with zipfile.ZipFile(path)as z:entries=[(info,z.read(info))for info in z.infolist()]
    if sum(info.filename=='godot.html'for info,_ in entries)!=1:raise ValueError('Missing export shell')
    fd,temp=tempfile.mkstemp(prefix='onklav-themed-',suffix='.zip',dir=path.parent)
    try:
        with os.fdopen(fd,'wb')as f:
            with zipfile.ZipFile(f,'w')as z:
                for info,data in entries:z.writestr(info,theme_html(data.decode()).encode()if info.filename=='godot.html'else data)
        os.replace(temp,path)
    finally:Path(temp).unlink(missing_ok=True)
if __name__=='__main__':theme_template(ROOT/'engine-template.zip')
