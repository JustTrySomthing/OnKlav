package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(exit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("profile", 0) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", "")!!) }
    var phone by rememberSaveable { mutableStateOf(prefs.getString("phone", "")!!) }
    var birthday by rememberSaveable { mutableStateOf(prefs.getString("birthday", "")!!) }
    var photo by rememberSaveable { mutableStateOf(prefs.getString("photo", "")!!) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val dirty =
        name != prefs.getString("name", "") ||
            phone != prefs.getString("phone", "") ||
            birthday != prefs.getString("birthday", "") ||
            photo != prefs.getString("photo", "")
    fun back() {
        if (busy) return
        if (dirty) discard = true else exit()
    }
    BackHandler { back() }
    val choose =
        rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        photo =
                            withContext(Dispatchers.IO) {
                                val input =
                                    context.contentResolver.openInputStream(uri)
                                        ?: error("Could not open the photo")
                                val bytes =
                                    input.use { stream ->
                                        val out = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val count = stream.read(buffer)
                                            if (count < 0) break
                                            require(out.size() + count <= 20 * 1024 * 1024) {
                                                "Choose a photo smaller than 20 MB"
                                            }
                                            out.write(buffer, 0, count)
                                        }
                                        out.toByteArray()
                                    }
                                require(bytes.size <= 20 * 1024 * 1024) {
                                    "Choose a photo smaller than 20 MB"
                                }
                                Base64.encodeToString(profilePhoto(bytes), Base64.NO_WRAP)
                            }
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
        }
    val image =
        remember(photo) {
            runCatching {
                    if (photo.isEmpty()) null
                    else
                        Base64.decode(photo, Base64.DEFAULT).let {
                            BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
                        }
                }
                .getOrNull()
        }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .testTag("profile-screen")
        ) {
            HomeHeader("Profile", back = { back() })
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max=720.dp).fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null)
                            Image(
                                image,
                                "Profile Photo",
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        else
                            Icon(
                                painterResource(R.drawable.ref_setting_profile),
                                "Profile Photo",
                                Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        ChoiceButton(
                            "Choose Photo",
                            enabled = !busy,
                            outlined = false,
                            fontSize = 14.sp,
                            minimumHeight = 48.dp,
                        ) {
                            choose.launch(arrayOf("image/*"))
                        }
                        ChoiceButton(
                            "Remove Photo",
                            enabled = photo.isNotEmpty() && !busy,
                            outlined = false,
                            fontSize = 14.sp,
                            minimumHeight = 48.dp,
                        ) {
                            photo = ""
                        }
                    }
                }
                Text(
                    "Saved on this phone",
                    Modifier.padding(top = 14.dp, bottom = 4.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProfileField("Name", "Your Name", name, { name = it.take(80) })
                ProfileField(
                    "Phone Number",
                    "+1 555 123 4567",
                    phone,
                    { phone = phoneInput(it) },
                    KeyboardType.Phone,
                )
                ProfileField(
                    "Birthday (MM/DD)",
                    "MM/DD",
                    birthday,
                    { value ->
                        val digits = value.filter { it.isDigit() }.take(4)
                        birthday =
                            if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits
                    },
                    KeyboardType.Number,
                )
                if (busy)
                    Text(
                        "Preparing Photo…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ChoiceButton(
                    "Cancel",
                    Modifier.weight(1f),
                    enabled = !busy,
                    outlined = false,
                    fontSize = 14.sp,
                    minimumHeight = 48.dp,
                    onClick = exit,
                )
                ChoiceButton(
                    "Save",
                    Modifier.weight(1f),
                    enabled = !busy,
                    outlined = false,
                    fontSize = 14.sp,
                    minimumHeight = 48.dp,
                ) {
                    try {
                        if (phone.isNotBlank())
                            require(phone.count { it.isDigit() } in 10..15) {
                                "Enter a complete phone number"
                            }
                        if (birthday.isNotBlank()) {
                            require(birthday.matches(Regex("\\d{2}/\\d{2}"))) {
                                "Use MM/DD for your birthday"
                            }
                            java.time.MonthDay.of(
                                birthday.take(2).toInt(),
                                birthday.takeLast(2).toInt(),
                            )
                        }
                        check(
                            prefs
                                .edit()
                                .putString("name", name.trim())
                                .putString("phone", phone)
                                .putString("birthday", birthday)
                                .putString("photo", photo)
                                .commit()
                        ) {
                            "Could not save your profile"
                        }
                        exit()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }
        }
    }
    if (discard)
        ReferenceDialog(
            "Discard Profile Changes?",
            { discard = false },
            footer = {
                DialogAction("Keep Editing") { discard = false }
                DialogAction("Discard", destructive = true, onClick = exit)
            },
        ) {}
    ErrorDialog(error) { error = null }
}

@Composable
private fun ProfileField(
    label: String,
    hint: String,
    value: String,
    change: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Text(label, Modifier.padding(top = 18.dp, bottom = 6.dp), fontSize = 14.sp)
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if(keyboard==KeyboardType.Phone) PhoneVisualTransformation else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

private fun profilePhoto(bytes: ByteArray): ByteArray {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(
        options.outWidth > 0 &&
            options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= 100_000_000
    ) {
        "Choose a still photo up to 100 megapixels"
    }
    options.inJustDecodeBounds = false
    while (
        maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 768
    ) options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
    val decoded =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Could not decode photo")
    val owned = mutableSetOf(decoded)
    try {
        val rotation =
            runCatching {
                    ExifInterface(bytes.inputStream())
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                }
                .getOrDefault(1)
        val matrix =
            Matrix().apply {
                when (rotation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    6 -> setRotate(90f)
                    7 -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    8 -> setRotate(-90f)
                }
            }
        val oriented =
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                owned.add(it)
            }
        val edge = minOf(oriented.width, oriented.height)
        val cropped =
            Bitmap.createBitmap(
                    oriented,
                    (oriented.width - edge) / 2,
                    (oriented.height - edge) / 2,
                    edge,
                    edge,
                )
                .also { owned.add(it) }
        val small =
            Bitmap.createScaledBitmap(cropped, minOf(edge, 256), minOf(edge, 256), true).also {
                owned.add(it)
            }
        val flat =
            Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888).also {
                owned.add(it)
            }
        android.graphics.Canvas(flat).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(small, 0f, 0f, null)
        }
        for (quality in listOf(90, 75, 60, 40)) {
            val out = ByteArrayOutputStream()
            check(flat.compress(Bitmap.CompressFormat.JPEG, quality, out))
            if (out.size() <= 64 * 1024) return out.toByteArray()
        }
        error("Photo could not be reduced")
    } finally {
        owned.forEach { it.recycle() }
    }
}

@Composable
fun BackupsScreen(exit: () -> Unit) { BackupFilesScreen(exit) }

@Composable
fun ReadAloudSettingsDialog(close: () -> Unit) {
    val context = LocalContext.current
    val player = remember { SdkSpeechController(context, "host.messages", {}) }
    var chooser by rememberSaveable { mutableStateOf(false) }
    var refreshOnResume by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(player) { player.prepare("settings") }
    DisposableEffect(player) { onDispose { player.close() } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.stop()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && refreshOnResume) {
                refreshOnResume = false
                player.retry("settings")
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    ReferenceDialog("Read Aloud", close, covered = chooser,
        footer = { DialogAction("Done", onClick = close) }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("English voices are included. Reading works offline.",
                Modifier.padding(vertical = 8.dp), fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(player.error ?: if (player.ready) "Offline voices ready" else "Preparing included voices…",
                Modifier.padding(vertical = 8.dp), fontSize = 14.sp)
            if (player.error != null) ChoiceButton("Retry Voice Setup", outlined = false) { player.retry("settings") }
            ChoiceButton(player.voices.firstOrNull { it.id == player.voice }?.label ?: "Choose Voice",
                outlined = false, enabled = player.ready && player.voices.isNotEmpty()) { chooser = true }
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OriginalAction(R.drawable.ref_setting_read_aloud,
                    if (player.playing) "Stop Preview" else "Preview Voice", enabled = player.ready) {
                    player.play("Hello! This is how your messages will sound.", "settings")
                }
                Text(if (player.playing) "Stop Preview" else "Preview Voice", fontSize = 14.sp)
            }
            Text("Speech Speed · ${"%.2f".format(player.rate)}×", Modifier.padding(vertical = 8.dp), fontSize = 16.sp)
            AppearanceSlider(player.rate, { player.settingsRate(it) }, range = .75f..1.5f, steps = 14,
                enabled = player.ready && player.voices.isNotEmpty())
            Text("Kitten Nano FP32 · Voice model included. No cloud service or Android voice setup is needed.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)

        }
    }
    ErrorDialog(settingsError) { settingsError = null }
    if (chooser) ReferenceDialog("Choose Voice", { chooser = false },
        footer = { DialogAction("Cancel") { chooser = false } }) {
        player.voices.forEach { item ->
            SettingsChoice((if (item.id == player.voice) "✓ " else "") + item.label, "") {
                player.settingsVoice(item.id)
                chooser = false
            }
        }
    }
}
