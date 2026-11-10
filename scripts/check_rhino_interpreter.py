#!/usr/bin/env python3
"""Run NewPipe's actual interpreter with Android-unavailable desktop APIs blocked.

Uses JDK17 solely as a host verification tool. The temporary Java harness is
never compiled into OnKlav; Android application source remains Kotlin/Rust.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
import java.net.*;
import java.nio.file.*;
public class InterpreterGuard {
  public static void main(String[] args) throws Exception {
    URL[] jars={Path.of(args[0]).toUri().toURL(),Path.of(args[1]).toUri().toURL()};
    try(URLClassLoader loader=new URLClassLoader(jars,ClassLoader.getPlatformClassLoader()) {
      protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
        if(name.startsWith("jdk.dynalink.")||name.startsWith("java.beans."))
          throw new ClassNotFoundException("Unavailable on Android: "+name);
        return super.loadClass(name,resolve);
      }
    }) {
      for(String name:new String[]{"jdk.dynalink.DynamicLinker","java.beans.BeanInfo"}) {
        try {loader.loadClass(name);throw new AssertionError("Desktop API was not blocked");}
        catch(ClassNotFoundException expected) {}
      }
      Class<?> js=loader.loadClass("org.schabi.newpipe.extractor.utils.JavaScript");
      js.getMethod("compileOrThrow",String.class).invoke(null,"function f(a){return a.slice(1);}");
      Object result=js.getMethod("run",String.class,String.class,String[].class)
        .invoke(null,"function f(a){return a.split('').reverse().slice(1).join('');}","f",new String[]{"abcdef"});
      if(!"edcba".equals(result))throw new AssertionError("Interpreter output changed: "+result);
      System.out.println("NewPipe interpreter passed with desktop JIT/Bean APIs unavailable");
    }
  }
}
'''


def main():
    jdk = Path(os.environ["JAVA_HOME"]) / "bin"
    with tempfile.TemporaryDirectory(prefix="onklav-interpreter-") as tmp:
        source = Path(tmp) / "InterpreterGuard.java"
        source.write_text(HARNESS)
        subprocess.run([str(jdk / "javac"), "-d", tmp, str(source)], check=True, timeout=30)
        subprocess.run([str(jdk / "java"), "-cp", tmp, "InterpreterGuard",
                        str(ROOT / "android/app/libs/newpipe-extractor-0.26.4.jar"),
                        str(ROOT / "android/app/libs/rhino-1.8.1.jar")], check=True, timeout=30)
    print(json.dumps({"actual_newpipe_interpreter": "passed", "desktop_apis_blocked": True,
                      "android_runtime_adapter_needed": False}))


if __name__ == "__main__":
    main()
