package app.onklav.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalAppearance = staticCompositionLocalOf<Appearance> { error("Missing appearance") }
val LocalAppearanceSpec = staticCompositionLocalOf { AppearanceSpec() }

private val darkAccents =
    listOf(
        0xff56dea3,
        0xff72bdff,
        0xffd9933d,
        0xffc9aaff,
        0xffd974a4,
        0xffff929a,
        0xffd9be41,
        0xffd0d0d0,
        0xff64d8cb,
        0xff70d3ed,
        0xffb1b5ff,
        0xffe2a4f4,
        0xffffa79a,
        0xffe8b78f,
        0xffb1c9df,
    )
private val lightAccents =
    listOf(
        0xff08734f,
        0xff155da8,
        0xffa14200,
        0xff6935a0,
        0xffad155e,
        0xffb51d32,
        0xff705500,
        0xff505050,
        0xff006c63,
        0xff006780,
        0xff4448a8,
        0xff82319a,
        0xffa33930,
        0xff87502f,
        0xff465f75,
    )
private val darkSent =
    listOf(
        0xff154d38,
        0xff173e73,
        0xffd9811a,
        0xff49316e,
        0xffd9679c,
        0xff69242c,
        0xffd9be2e,
        0xff343434,
        0xff124e48,
        0xff164858,
        0xff333864,
        0xff583160,
        0xff663a35,
        0xff533d2f,
        0xff344758,
    )
private val lightSent =
    listOf(
        0xffc3f2dd,
        0xffc9e5ff,
        0xffffd4a3,
        0xffe3d1ff,
        0xffffcbe3,
        0xffffcbcd,
        0xffffec8b,
        0xffd0d0d0,
        0xffc1eee7,
        0xffc5edf6,
        0xffdddfff,
        0xfff0d5f6,
        0xffffd9d2,
        0xffeedcce,
        0xffd9e5ef,
    )
val swatchColors =
    listOf(
            0xff16b978,
            0xff258eff,
            0xffd97514,
            0xffad7af5,
            0xffd94088,
            0xfff03749,
            0xffd9b524,
            0xff787878,
            0xff169d91,
            0xff239cb9,
            0xff6268d9,
            0xffae50c7,
            0xffe47b70,
            0xffb67b50,
            0xff6d8499,
        )
        .map { Color(it) }

fun appearanceFamily(spec: AppearanceSpec): FontFamily =
    FontFamily(
        android.graphics.Typeface.create(
            listOf("sans-serif", "casual", "serif", "sans-serif-condensed", "monospace", "serif")[
                spec.font],
            if (spec.font == 5) android.graphics.Typeface.ITALIC
            else android.graphics.Typeface.NORMAL,
        )
    )

fun sentColor(spec: AppearanceSpec) = Color((if (spec.dark) darkSent else lightSent)[spec.palette])

fun sentInk(spec: AppearanceSpec) =
    if (spec.dark && spec.palette in listOf(2, 4, 6)) Color(0xff252129)
    else if (spec.palette == 7) {
        if (spec.dark) Color(0xfff5f5f5) else Color(0xff222222)
    } else if (spec.dark) Color(0xfff5f6fa) else Color(0xff20232a)

fun sentBrush(spec: AppearanceSpec): androidx.compose.ui.graphics.Brush {
    val base = sentColor(spec)
    if (!spec.gradient) return androidx.compose.ui.graphics.SolidColor(base)
    val ink = sentInk(spec)
    val accent = Color((if (spec.dark) darkAccents else lightAccents)[spec.palette])
    return gradientBrush(
        base,
        ink,
        if (spec.dark && spec.palette in listOf(2, 4, 6)) ink else accent,
        spec.dark,
    )
}

fun swatchBrush(spec: AppearanceSpec, id: Int): androidx.compose.ui.graphics.Brush =
    if (!spec.gradient) androidx.compose.ui.graphics.SolidColor(swatchColors[id])
    else gradientBrush(swatchColors[id], sentInk(spec), sentInk(spec), spec.dark)

private fun gradientBrush(
    base: Color,
    ink: Color,
    secondary: Color,
    dark: Boolean,
): androidx.compose.ui.graphics.Brush {
    val darkFill = dark && ink.luminance() > base.luminance()
    val highlight = if (darkFill || ink.luminance() < base.luminance()) Color.White else Color.Black
    fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    val end =
        generateSequence(if (darkFill) .14f else if (highlight == Color.White) .31f else .29f) {
                it / 2
            }
            .take(8)
            .map { lerp(base, highlight, it) }
            .firstOrNull {
                contrast(it, ink) + .001f >= minOf(4.5f, contrast(base, ink)) &&
                    contrast(it, secondary) + .001f >= minOf(4.5f, contrast(base, secondary))
            } ?: base
    val start = if (darkFill) lerp(base, Color.Black, .25f) else base
    return androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(start, end).sortedBy { it.luminance() }
    )
}

/** Choose a legible foreground for any selected theme fill. */
fun contrastInk(fill:Color):Color = if((fill.luminance()+.05f)/.05f >= 1.05f/(fill.luminance()+.05f)) Color.Black else Color.White

fun appearanceColors(s:AppearanceSpec):ColorScheme {
    val gray = s.palette == 7
    val accent = Color((if (s.dark) darkAccents else lightAccents)[s.palette])
    val background =
        if (s.dark) Color(if (gray) 0xff202020 else 0xff191a1e)
        else Color(if (gray) 0xfff6f6f6 else 0xfff6f6f8)
    val foreground = sentInk(s.copy(palette = if (gray) 7 else 0))
    val muted =
        if (s.dark) Color(if (gray) 0xffc7c7c7 else 0xffc2c7d0)
        else Color(if (gray) 0xff505050 else 0xff4d5561)
    val surface = if (s.dark) Color(if (gray) 0xff2b2b2b else 0xff2b2c32) else Color.White
    return (if (s.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = accent,
            onPrimary = contrastInk(accent),
            secondary = accent, onSecondary = contrastInk(accent),
            secondaryContainer = lerp(surface, accent, .18f), onSecondaryContainer = foreground,
            tertiary = accent, onTertiary = contrastInk(accent),
            tertiaryContainer = lerp(surface, accent, .18f), onTertiaryContainer = foreground,
            inversePrimary = Color((if(s.dark) lightAccents else darkAccents)[s.palette]),
            error = if(s.dark) Color(0xffffa494) else Color(0xffa43224),
            onError = contrastInk(if(s.dark) Color(0xffffa494) else Color(0xffa43224)),
            errorContainer = if(s.dark) Color(0xff5b2421) else Color(0xffffdad4),
            onErrorContainer = contrastInk(if(s.dark) Color(0xff5b2421) else Color(0xffffdad4)),
            surfaceDim = background, surfaceBright = surface,
            surfaceContainerLowest = background, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
            inverseSurface = if(s.dark) Color(if(gray)0xfff5f5f5 else 0xfff5f6fa) else Color(if(gray)0xff2b2b2b else 0xff2b2c32),
            inverseOnSurface = if(s.dark) Color(if(gray)0xff222222 else 0xff20232a) else Color(if(gray)0xfff5f5f5 else 0xfff5f6fa),
            outlineVariant = lerp(surface, muted, .35f),
            surfaceVariant = surface,
            background = background,
            surface = surface,
            onBackground = foreground,
            onSurface = foreground,
            onSurfaceVariant = muted,
            primaryContainer = sentColor(s),
            onPrimaryContainer = sentInk(s),
            outline = lerp(surface, muted, .55f),
            surfaceTint = Color.Transparent,
        )
}

@Composable
fun OnKlavTheme(
    appearance: Appearance = rememberAppearance(),
    preview: AppearanceSpec? = null,
    content: @Composable () -> Unit,
) {
    val s = preview ?: appearance.spec
    val scheme = remember(s.dark, s.palette) { appearanceColors(s) }
    val background = scheme.background
    val base = Typography()
    val family = remember(s.font) { appearanceFamily(s) }
    fun style(t: androidx.compose.ui.text.TextStyle) =
        t.copy(
            fontFamily = family,
            letterSpacing = 0.sp,
            fontStyle =
                if (s.font == 5) androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal,
        )
    val typography =
        Typography(
            displayLarge = style(base.displayLarge),
            displayMedium = style(base.displayMedium),
            displaySmall = style(base.displaySmall),
            headlineLarge = style(base.headlineLarge),
            headlineMedium = style(base.headlineMedium),
            headlineSmall = style(base.headlineSmall),
            titleLarge = style(base.titleLarge).copy(fontSize = 22.sp),
            titleMedium = style(base.titleMedium),
            titleSmall = style(base.titleSmall),
            bodyLarge = style(base.bodyLarge).copy(fontSize = 17.sp),
            bodyMedium = style(base.bodyMedium),
            bodySmall = style(base.bodySmall),
            labelLarge = style(base.labelLarge),
            labelMedium = style(base.labelMedium),
            labelSmall = style(base.labelSmall),
        )
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    if (preview == null)
        SideEffect {
            var c = context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c =
                c.baseContext
            (c as? android.app.Activity)?.window?.let { window ->
                window.context.theme.applyStyle(nativeAppearanceStyle(s), true)
                window.context.theme.applyStyle(nativeFontStyle(s), true)
                window.statusBarColor = background.toArgb()
                window.navigationBarColor = background.toArgb()
                if(android.os.Build.VERSION.SDK_INT>=29){window.isStatusBarContrastEnforced=false;window.isNavigationBarContrastEnforced=false}
                androidx.core.view.WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !s.dark
                    isAppearanceLightNavigationBars = !s.dark
                }
            }
        }
    CompositionLocalProvider(LocalAppearance provides appearance, LocalAppearanceSpec provides s) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes =
                Shapes(
                    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ),
            content = content,
        )
    }
}

@Composable
fun rememberAppearance(): Appearance {
    val context = LocalContext.current
    return remember { Appearance(context) }
}

@Composable
fun CompactAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            description,
            Modifier.size(22.dp),
            tint =
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared dismissal control; the owning page retains its close/save/exit callback. */
@Composable
fun ClosePageButton(enabled:Boolean=true,description:String="Close",onClick:()->Unit){
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.iconButtonColors(contentColor=MaterialTheme.colorScheme.primary)){
        Icon(androidx.compose.material.icons.Icons.Outlined.Close,description,Modifier.size(24.dp))
    }
}

@Composable
fun PageHeader(
    title: String,
    leading: @Composable (() -> Unit)? = null,
    titleContent: (@Composable (Modifier) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        leading?.invoke()
        val titleModifier = Modifier.weight(1f).padding(start = if (leading == null) 8.dp else 0.dp)
        if (titleContent != null) titleContent(titleModifier) else Text(
            title,
            titleModifier,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun ErrorDialog(message: String?, dismiss: () -> Unit) {
    if (message != null)
        ReferenceDialog(
            "Could Not Complete",
            dismiss,
            footer = { DialogAction("OK", onClick = dismiss) },
        ) {
            Text(message, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 16.sp)
        }
}
