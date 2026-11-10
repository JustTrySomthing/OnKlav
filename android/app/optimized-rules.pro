# Retain readable class names for useful production crash reports.
# Shrinking and optimization remain enabled. NativeCore's JNI keep rule is
# supplied by the shared library. Games use the shared sandboxed web engine.
-dontobfuscate

# Mail protocol/MIME providers are discovered from the upstream META-INF registries.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }

# Video extraction executes provider JavaScript and uses library-level dynamic
# objects. Keep these adapters intact while shrinking unrelated dependencies.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }

# Rhino also ships desktop-only JIT/linker and Bean-introspection helpers.
# NewPipe sets interpretedMode=true and safe standard objects in both entrypoints.
# scripts/check_rhino_interpreter.py executes the actual JAR with these packages
# blocked. Keep other missing-class diagnostics fatal; no global warning bypass.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn jdk.dynalink.**

# POI's optional desktop drawing/debug/color-format helpers and Log4j's optional
# OSGi adapter are unavailable on Android. Exact exceptions only: actual DOC/XLS
# reading is checked by scripts/check_document_readers_android.py with desktop
# APIs blocked. Numeric formats requiring AWT fall back to their stored value.
# These annotation types have no runtime behavior. Keep other missing types fatal.
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.awt.Color
-dontwarn java.awt.Dimension
-dontwarn java.awt.Rectangle
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Dimension2D
-dontwarn java.awt.geom.Path2D
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.IndexColorModel
-dontwarn java.awt.image.PackedColorModel
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
-dontwarn org.osgi.framework.wiring.BundleRevision

# Native Phone uses AndroidViewModelFactory's Application constructor.
-keepclassmembers class app.onklav.home.PhoneModel {
    public <init>(android.app.Application);
}

# Cloud Files uses the AndroidViewModelFactory Application constructor.
-keepclassmembers class app.onklav.home.CloudModel {
    public <init>(android.app.Application);
}
