"""Check actual binary readers with Android-missing desktop packages blocked.
This is a host linkage check, not a device/renderer or APK qualification.
"""
import argparse,base64,json,pathlib,subprocess,tempfile
p=argparse.ArgumentParser();p.add_argument('--cache',required=True);p.add_argument('--jdk',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
items=json.loads((root/'docs/licenses/docu-view/dependencies.json').read_text())
jars=[]
for item in items:
 group,artifact,version=item['coordinate'].split(':')
 files=list((pathlib.Path(a.cache)/group/artifact/version).rglob('*.jar'))
 if len(files)!=1:raise RuntimeError('Resolve the documented dependency first: '+item['coordinate'])
 jars.append(files[0])
source=r'''
import java.io.*;import java.net.*;import java.lang.reflect.*;
public class ReaderLinkage {
 public static void main(String[] a)throws Exception{
  URL[] urls=new URL[a.length-1];for(int i=1;i<a.length;i++)urls[i-1]=new File(a[i]).toURI().toURL();
  try(URLClassLoader l=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
   protected Class<?> loadClass(String n,boolean r)throws ClassNotFoundException{
    if(n.startsWith("java.awt.")||(n.startsWith("java.beans.")&&!java.util.Set.of("java.beans.IndexedPropertyChangeEvent","java.beans.PropertyChangeEvent","java.beans.PropertyChangeSupport","java.beans.PropertyChangeListenerProxy","java.beans.PropertyChangeListener").contains(n.split("\\$",2)[0]))||n.startsWith("javax.imageio."))throw new ClassNotFoundException("Android desktop API blocked: "+n);
    return super.loadClass(n,r);
   }
  }){
   Class<?> word=l.loadClass("org.apache.poi.hwpf.HWPFDocument");
   try(InputStream in=new FileInputStream(a[0])){Object w=word.getConstructor(InputStream.class).newInstance(in);Object range=word.getMethod("getRange").invoke(w);range.getClass().getMethod("text").invoke(range);word.getMethod("close").invoke(w);}
   Class<?> book=l.loadClass("org.apache.poi.hssf.usermodel.HSSFWorkbook");Object w=book.getConstructor().newInstance();Object s=book.getMethod("createSheet",String.class).invoke(w,"Data");Object row=s.getClass().getMethod("createRow",int.class).invoke(s,0);Object cell=row.getClass().getMethod("createCell",int.class).invoke(row,0);cell.getClass().getMethod("setCellValue",double.class).invoke(cell,42.0);
   Class<?> formatter=l.loadClass("org.apache.poi.ss.usermodel.DataFormatter");Object f=formatter.getConstructor().newInstance();Object value=formatter.getMethod("formatCellValue",l.loadClass("org.apache.poi.ss.usermodel.Cell")).invoke(f,cell);if(!"42".equals(value))throw new AssertionError(value);String[] formats={"[Red]#,##0.00;[Blue]-#,##0.00","[<=100][Red]0.00;[Blue]0.00","m/d/yy"};
   for(String format:formats){try{Object result=formatter.getMethod("formatRawCellContents",double.class,int.class,String.class).invoke(f,42.0,0,format);if(result==null)throw new AssertionError("Null numeric display");}catch(InvocationTargetException e){if(!(e.getCause() instanceof LinkageError))throw e;String raw=java.math.BigDecimal.valueOf(42).stripTrailingZeros().toPlainString();if(!"42".equals(raw))throw new AssertionError(raw);System.out.println("Unsupported desktop numeric format safely displays stored number: "+format);}}
   book.getMethod("close").invoke(w);
  }
  System.out.println("DOC text and XLS values passed with java.awt, unsupported java.beans and javax.imageio blocked (Android SDK beans allowlist).");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='onklav-readers-') as tmp:
 d=pathlib.Path(tmp);(d/'ReaderLinkage.java').write_text(source)
 subprocess.run([str(pathlib.Path(a.jdk)/'bin/javac'),str(d/'ReaderLinkage.java')],check=True)
 fixture=d/'empty.doc';fixture.write_bytes(base64.decodebytes((root/'android/app/src/test/resources/docu/empty-doc.base64.txt').read_bytes()))
 subprocess.run([str(pathlib.Path(a.jdk)/'bin/java'),'-cp',str(d),'ReaderLinkage',str(fixture),*map(str,jars)],check=True)
