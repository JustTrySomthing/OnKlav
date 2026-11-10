package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R

// Internal page IDs stay stable for saved state and incoming message routes.
val homePageTitles = listOf("Messages", "Groups", "Apps", "Games", "Settings")
val homeTabs = listOf("Phone", "Camera", "Apps", "Messages", "Groups")
internal val homeDestinationIds = listOf(5,6,2,0,1)
private val homeIcons = listOf(R.drawable.ref_app_phone,R.drawable.ref_app_camera,R.drawable.ref_nav_apps,R.drawable.ref_nav_messages,R.drawable.ref_nav_groups)

@Composable
fun OriginalAction(artwork: Int, label: String, enabled: Boolean = true, tint: Color? = null, active: Boolean = false, action: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .background(if(active) MaterialTheme.colorScheme.primary.copy(alpha=.14f) else Color.Transparent,RoundedCornerShape(10.dp))
            .semantics { selected=active }
            .clickable(enabled = enabled, role = Role.Button, onClick = action)
            .testTag("action-$label"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(artwork),
            label,
            Modifier.size(24.dp),
            tint =
                if (enabled) tint ?: MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HomeHeader(
    title: String,
    back: (() -> Unit)? = null,
    gamesHint: Boolean = false,
    titleAction: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.background)
            .testTag("home-header")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(horizontal=8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else if (back != null) ClosePageButton(onClick=back)
        if (titleAction != null) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f), RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = titleAction)
                .semantics { contentDescription = "$title. View group members" }
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 22.sp, lineHeight = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ref_action_chevron), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
        } else Text(title, Modifier.weight(1f), fontSize = 22.sp, lineHeight = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (gamesHint)
            Text(
                "Play Friends or Alone",
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        actions()
    }
}

@Composable
fun HomeNavigation(tab: Int, choose: (Int) -> Unit) {
    val spec = LocalAppearanceSpec.current
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home-navigation")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(top = 4.dp, start=8.dp,end=8.dp)
    ) {
        homeTabs.forEachIndexed { at, title ->
            val selected = tab == homeDestinationIds[at]
            val ink =
                if (selected) {
                    if (spec.dark && spec.palette in listOf(2, 4, 6)) sentInk(spec)
                    else MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 68.dp)
                    .clickable(role = Role.Tab) { choose(homeDestinationIds[at]) }
                    .semantics { this.selected = selected }
                    .testTag("tab-$title")
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .then(
                        if (selected)
                            Modifier.background(sentBrush(spec), RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(8.dp),
                                )
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(homeIcons[at]), null, Modifier.size(24.dp), tint = ink)
                    Text(title, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, color = ink)
                }
            }
        }
    }
}

@Composable
internal fun ThemedButtonTabs(labels:List<String>,selectedLabel:String,tagPrefix:String,onSelect:(String)->Unit) {
    val spec = LocalAppearanceSpec.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        labels.forEach { name ->
            val selected = selectedLabel == name
            Box(
                Modifier.weight(1f)
                    .height(48.dp)
                    .clickable(role = Role.Tab) { onSelect(name) }
                    .semantics { this.selected = selected }
                    .testTag("$tagPrefix-$name")
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .then(
                        if (selected)
                            Modifier.background(sentBrush(spec), RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(8.dp),
                                )
                        else Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    fontSize = 17.sp,
                    color =
                        if (selected) {
                            if (spec.dark && spec.palette in listOf(2, 4, 6)) sentInk(spec)
                            else MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun MailboxControls(
    folder: String,
    setFolder: (String) -> Unit,
    search: String,
    setSearch: (String) -> Unit,
    selecting: Boolean,
    setSelecting: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=8.dp)) {
        ThemedButtonTabs(listOf("Inbox","Archive","Trash"),folder,"folder",setFolder)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                search,
                setSearch,
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("mailbox-search"),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { field ->
                    Box {
                        if (search.isEmpty())
                            Text(
                                "Search names, numbers or messages",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        field()
                    }
                },
            )
            Checkbox(
                selecting,
                setSelecting,
                Modifier.padding(start = 4.dp).size(48.dp).semantics {
                    contentDescription = "Select multiple conversations"
                },
            )
        }
    }
}

@Composable
fun TrashControls(enabled:Boolean,restore:()->Unit,empty:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Restore all" to restore,"Empty trash" to empty).forEach { (label,action) ->
            Surface(Modifier.weight(1f).heightIn(min=40.dp).clickable(enabled=enabled,role=Role.Button,onClick=action).testTag("trash-$label"),shape=RoundedCornerShape(9.dp),color=MaterialTheme.colorScheme.surface) {
                Box(Modifier.padding(8.dp),contentAlignment=Alignment.Center) {
                    Text(label,fontSize=17.sp,color=if(label=="Empty trash") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable internal fun ConversationTabs(selected:String,choose:(String)->Unit) {
    val spec=LocalAppearanceSpec.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("Feed", "Gallery", "Starred").forEach { name ->
                val active = selected == name
                Box(
                    Modifier.weight(1f)
                        .height(48.dp)
                        .clickable(role = Role.Tab) { choose(name) }
                        .semantics { this.selected = active }
                        .testTag("conversation-tab-$name")
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .then(
                            if (active)
                                Modifier.background(sentBrush(spec), RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp),
                                    )
                            else Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        fontSize = 17.sp,
                        color =
                            if (active) {
                                if (spec.dark && spec.palette in listOf(2, 4, 6)) sentInk(spec)
                                else MaterialTheme.colorScheme.primary
                            } else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
}
