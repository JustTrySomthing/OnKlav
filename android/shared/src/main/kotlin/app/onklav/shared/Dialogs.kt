package app.onklav.shared

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading as accessibilityHeading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** A short centered fade/grow; no sideways or bottom travel for floating windows. */
fun Modifier.dialogMotion(progress:Float):Modifier = graphicsLayer {
    alpha=progress.coerceIn(0f,1f)
    scaleX=.94f+.06f*progress
    scaleY=scaleX
}
@Composable private fun CenteredDialogWindow() {
    val view=LocalView.current
    SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
            setGravity(android.view.Gravity.CENTER)
            setWindowAnimations(0)
        }
    }
}

private val LocalDialogAction=staticCompositionLocalOf<((()->Unit)->Unit)?>{null}
private class DialogMotion {
    val position=androidx.compose.animation.core.Animatable(0f)
    var closing=false
}
@Composable private fun rememberDialogMotion():Pair<DialogMotion,((()->Unit)->Unit)> {
    val motion=remember{DialogMotion()}
    val scope=rememberCoroutineScope()
    LaunchedEffect(motion){motion.position.animateTo(1f,androidx.compose.animation.core.tween(230,easing=androidx.compose.animation.core.FastOutSlowInEasing))}
    val action:((()->Unit)->Unit)={block->
        if(!motion.closing){motion.closing=true;scope.launch {
            motion.position.animateTo(0f,androidx.compose.animation.core.tween(180,easing=androidx.compose.animation.core.FastOutSlowInEasing))
            block()
            // Apply/validation can keep the same popup alive.
            motion.position.animateTo(1f,androidx.compose.animation.core.tween(230,easing=androidx.compose.animation.core.FastOutSlowInEasing))
            motion.closing=false
        }}
    }
    return motion to action
}

/** Shared recreation of the reference's compact floating window and inset controls. */
@Composable
fun ReferenceDialog(
    title: String,
    dismiss: () -> Unit,
    large: Boolean = false,
    covered: Boolean = false,
    heading: @Composable (RowScope.() -> Unit)? = null,
    footer: @Composable RowScope.() -> Unit,
    centerTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (motion,animateAction)=rememberDialogMotion()
    val fullHeight =
        (LocalContext.current.resources.displayMetrics.heightPixels / LocalDensity.current.density)
            .dp
    val bodyScroll = key(title) { rememberScrollState() }
    CompositionLocalProvider(LocalDialogAction provides animateAction) {
    Dialog(
        {animateAction(dismiss)},
        properties =
            DialogProperties(usePlatformDefaultWidth = !large, dismissOnClickOutside = !large),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
        val availableHeight=minOf(fullHeight * .84f, (maxHeight - 24.dp).coerceAtLeast(80.dp))
        CenteredDialogWindow()
        Surface(
            Modifier.widthIn(max = if (large) 480.dp else 560.dp)
                .fillMaxWidth()
                .padding(horizontal = if (large) 12.dp else 0.dp)
                .then(
                    if (large) Modifier.height(availableHeight)
                    else Modifier.heightIn(max = availableHeight)
                )
                .dialogMotion(motion.position.value)
                .testTag("dialog-$title").semantics {if(title.isNotBlank())paneTitle=title}
                .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(4.dp)) {
                if(title.isNotBlank()) Row(
                    Modifier.fillMaxWidth()
                        .padding(
                            start = if (heading != null) 20.dp else 24.dp,
                            end = if (heading != null) 16.dp else 24.dp,
                            top = if (heading != null) 12.dp else 20.dp,
                            bottom = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        Modifier.weight(1f).padding(vertical = if (heading != null) 8.dp else 0.dp).semantics {accessibilityHeading()},
                        fontSize = if (centerTitle) 21.sp else 22.sp,
                        textAlign = if (centerTitle) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        lineHeight = 26.sp,
                    )
                    heading?.invoke(this)
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(bodyScroll),
                    content = content,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    content = footer,
                )
            }
        }
        }
    }
    }
}

@Composable
fun DialogAction(
    label: String,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val animateAction=LocalDialogAction.current
    val dark = LocalAppearanceSpec.current.dark
    val dismissive = label in listOf("Cancel", "Close", "Done", "OK", "Back")
    val fill =
        if (destructive) MaterialTheme.colorScheme.error else if (primary && !dismissive) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, if(dark) .12f else .10f)
    val ink =
        if ((primary && !dismissive) || destructive) {
            contrastInk(fill)
        } else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = {if(animateAction!=null && label!="Refresh")animateAction(onClick)else onClick()})
            .padding(4.dp)
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 40.dp)
                .background(
                    if (enabled) fill else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                modifier=Modifier.widthIn(max=(LocalConfiguration.current.screenWidthDp.dp-112.dp).coerceIn(72.dp,280.dp)),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                color = if (enabled) ink else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChoiceButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minimumHeight: Dp = 52.dp,
    outlined: Boolean = true,
    fontSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    Surface(
        onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = minimumHeight),
        shape = RoundedCornerShape(12.dp),
        border =
            if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.25f).sp,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OnKlavAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val config = LocalConfiguration.current
    val (motion,animateAction)=rememberDialogMotion()
    CompositionLocalProvider(LocalDialogAction provides animateAction) {
    Dialog({animateAction(onDismissRequest)}) {
        CenteredDialogWindow()
        Surface(
            modifier.widthIn(max=560.dp).fillMaxWidth().imePadding().heightIn(max = config.screenHeightDp.dp * .84f).dialogMotion(motion.position.value),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
                        title?.invoke()
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 19.sp,
                        )
                    ) {
                        text?.invoke()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalDialogFooter provides true) {
                        dismissButton?.invoke()
                        Spacer(Modifier.width(8.dp))
                        CompositionLocalProvider(LocalPrimaryAction provides true) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

}

private val LocalDialogFooter = staticCompositionLocalOf { false }

private val LocalPrimaryAction = staticCompositionLocalOf { false }

@Composable
fun OnKlavTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val animateAction=LocalDialogAction.current
    val primary = LocalPrimaryAction.current
    val footer = LocalDialogFooter.current
    val dark = LocalAppearanceSpec.current.dark
    val fill =
        if (primary) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, if(dark) .12f else .10f)
    val ink =
        if (primary) {
            contrastInk(fill)
        } else if (footer) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.primary
    Surface(
        {if(footer&&animateAction!=null)animateAction(onClick)else onClick()},
        modifier = if (footer) modifier else modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (footer) fill else MaterialTheme.colorScheme.background,
        contentColor = if (enabled) ink else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(fontSize = if (footer) 14.sp else 16.sp)
        ) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = if (footer) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
