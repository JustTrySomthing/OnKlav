package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.*

/** The catalog and runtime share one entry dialog; loading begins after a choice. */
@Composable
internal fun GameStartDialog(game:PlatformGame,friendAllowed:Boolean,onSolo:()->Unit,onFriend:()->Unit,onCancel:()->Unit) {
        androidx.compose.ui.window.Dialog(
            onCancel,
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CatalogArtwork(game, Modifier.size(100.dp, 112.dp))
                    Text(
                        game.title,
                        Modifier.padding(top = 16.dp, bottom = 8.dp),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        game.description,
                        fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.padding(top = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val startColors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xff167343),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                        )
                        val startShape = RoundedCornerShape(14.dp)
                        if (friendAllowed) {
                            Button(onClick = onFriend, modifier = Modifier.weight(1f).heightIn(min = 92.dp),
                                colors = startColors, shape = startShape) {
                                Text("Play a Friend", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Button(onClick = onSolo, modifier = Modifier.weight(1f).heightIn(min = 92.dp),
                                colors = startColors, shape = startShape) {
                                Text("Single Player", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        } else Button(
                            onClick = onSolo,
                            modifier = Modifier.widthIn(min = 144.dp, max = 204.dp).heightIn(min = 48.dp),
                            colors = startColors,
                            shape = startShape,
                        ) { Text("Start", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
                    }
                    Text(
                        if(!friendAllowed) "Single Player Only" else if(game.id.contains(".")) "Developer game · shared engine" else "Play solo or take turns with a friend.",
                        Modifier.padding(top = 12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceButton(
                        "Cancel",
                        Modifier.padding(top = 12.dp),
                        outlined = false,
                        minimumHeight = 48.dp,
                        fontSize = 14.sp,
                    ) {
                        onCancel()
                    }
                }
            }
        }
}
