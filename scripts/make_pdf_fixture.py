"""Synthetic two-page PDF for read-only viewer checks; no external content."""
from pathlib import Path
from reportlab.pdfgen.canvas import Canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
root=Path(__file__).resolve().parents[1]
font=Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
if font.exists():pdfmetrics.registerFont(TTFont('FixtureSans',str(font)))
out=root/'docs/fixtures/pdf-sample.pdf'
c=Canvas(str(out),pagesize=(420,600),invariant=1,pageCompression=1)
for number,words in [(1,'needle alpha'),(2,'needle beta')]:
 c.setFont('FixtureSans' if font.exists() else 'Helvetica',18);c.drawString(36,550,'OnKlav PDF fixture')
 c.setFont('FixtureSans' if font.exists() else 'Helvetica',12);c.drawString(36,505,words);c.drawString(36,475,'Selectable text: cafe / café' if font.exists() else 'Selectable text: cafe')
 c.setStrokeColorRGB(.35,.25,.65);c.rect(36,400,348,40);c.drawString(48,416,'Read-only page rendering')
 c.setFont('Helvetica',10);c.drawString(36,28,f'Page {number} of 2');c.showPage()
c.save();print(out)
