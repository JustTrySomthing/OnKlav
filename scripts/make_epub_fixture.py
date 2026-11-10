"""Generate a synthetic EPUB3 fixture; no external content or signing material."""
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parents[1]
out=root/'docs/fixtures/text-profile.epub'
files={
'META-INF/container.xml':'<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="Book/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
'Book/book.opf':'<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>OnKlav sample book</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>',
'Book/nav.xhtml':'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="one.xhtml#one">Chapter One</a></li><li><a href="two.xhtml#two">Chapter Two</a></li></ol></nav></body></html>',
'Book/one.xhtml':'<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><style>p {color:red}</style></head><body><h1 id="one">Chapter One</h1><p>Alpha 😀 needle.</p></body></html>',
'Book/two.xhtml':'<html xmlns="http://www.w3.org/1999/xhtml"><body><h1 id="two">Chapter Two</h1><p>Beta needle.</p><img src="cover.png" alt="A sample cover"/></body></html>'}
with zipfile.ZipFile(out,'w') as z:
 for name,text in [('mimetype','application/epub+zip'),*files.items()]:
  i=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0));i.compress_type=zipfile.ZIP_STORED if name=='mimetype' else zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,text.encode())
print(out)
