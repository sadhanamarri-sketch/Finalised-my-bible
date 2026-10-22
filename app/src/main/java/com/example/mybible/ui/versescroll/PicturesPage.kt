package com.example.mybible.ui.versescroll

import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.pictures.Picture
import com.example.mybible.pictures.PictureScene
import com.example.mybible.pictures.PictureScenes
import com.example.mybible.pictures.PictureShelf
import com.example.mybible.pictures.Pictures
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DsNote
import com.example.mybible.ui.components.DsOutlineAccentButton
import com.example.mybible.ui.components.DsSectionLabel
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.launch

/** A picture added to a scene by "Add several in order", or why it couldn't be. */
private data class Added(val scene: PictureScene, val picture: Picture?, val trouble: String?)

/**
 * The Pictures page, from Verse Scroll's settings: the scenes there are prompts for, book by book, each
 * with its verses and title, its picture once it has one, and the way to make and add it: copy its
 * prompt for ChatGPT, then add the picture ChatGPT made, which is shrunk, named for its verses and saved
 * in the pictures folder. "Add several in order" fills the empty scenes from one on with pictures picked
 * one after another, then shows where each went.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PicturesPage(pictures: Pictures, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val status by pictures.status.collectAsState()
    val shelf by pictures.shelf.collectAsState()
    // The scenes (once read), and the folder looked over again, for pictures put there since.
    val loaded by produceState<PictureScenes?>(null) { value = pictures.scenes() }
    val scenes = loaded ?: PictureScenes.None
    LaunchedEffect(Unit) { pictures.lookAgain() }
    var chosenBook by rememberSaveable { mutableStateOf<String?>(null) }
    val book = chosenBook?.takeIf { it in scenes.books } ?: scenes.books.firstOrNull()
    val inBook = remember(scenes, book) { book?.let(scenes::inBook).orEmpty() }
    // The scenes being given a picture just now (by name), and how a batch is going (done, of how many).
    var adding by remember { mutableStateOf(emptySet<String>()) }
    var batch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // The scene a picture is being picked for, and the scene several start from, kept through the picker.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var severalFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf<PictureScene?>(null) }
    var removing by remember { mutableStateOf<Pair<PictureScene, Picture>?>(null) }
    var added by remember { mutableStateOf<Pair<List<Added>, Int>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val thumbnails = remember { LruCache<String, ImageBitmap>(THUMBNAILS_KEPT) }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // Copied: Android 13 and later show it themselves.
    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) pictures.choose(tree)
    }

    suspend fun addTo(scene: PictureScene, from: Uri): Added {
        adding = adding + scene.name
        return try {
            Added(scene, pictures.add(scene.name, from), null)
        } catch (e: Pictures.Trouble) {
            Added(scene, null, e.message)
        } finally {
            adding = adding - scene.name
        }
    }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val name = pickingFor
        pickingFor = null
        if (uri != null && name != null) {
            scope.launch {
                val scene = pictures.scenes().scenes.firstOrNull { it.name == name } ?: return@launch
                addTo(scene, uri).trouble?.let(::say)
            }
        }
    }

    // Several pictures, into the empty scenes from [fromName] on, one after another in the order picked.
    val pickSeveral = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val fromName = severalFrom
        severalFrom = null
        if (uris.isEmpty() || fromName == null) return@rememberLauncherForActivityResult
        scope.launch {
            val all = pictures.scenes()
            val from = all.scenes.firstOrNull { it.name == fromName } ?: return@launch
            val targets = emptyScenesFrom(all.inBook(from.book), from, pictures.shelf.value).take(uris.size)
            val results = mutableListOf<Added>()
            for ((i, scene) in targets.withIndex()) {
                batch = i to targets.size
                results += addTo(scene, uris[i])
            }
            batch = null
            added = results to (uris.size - targets.size)
        }
    }

    // A picture can only be added once there's a folder to keep it in.
    fun withFolder(action: () -> Unit) {
        when {
            status.folder == null -> {
                say("Choose a folder for your pictures first.")
                pickFolder.launch(null)
            }
            status.lost -> {
                say("Choose your pictures folder again: the app can't open it any more.")
                pickFolder.launch(null)
            }
            else -> action()
        }
    }

    fun addOne(scene: PictureScene) = withFolder {
        pickingFor = scene.name
        pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun addSeveral(from: PictureScene) = withFolder {
        severalFrom = from.name
        pickSeveral.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BackHandler {
        if (previewing != null) previewing = null else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Over Verse Scroll: its cards don't feel touches meant for this page.
            .pointerInput(Unit) {}
    ) {
        Scaffold(
            topBar = { BackTopBar(title = "Pictures", onBack = onClose, backContentDescription = "Back to Verse Scroll") },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            val done = inBook.count { shelf.forPlace(it.name).isNotEmpty() }
            val nextEmpty = remember(inBook, shelf) { nextEmptyScene(inBook, shelf) }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item(key = "folder") {
                    FolderSection(status = status, count = shelf.size, onChoose = { pickFolder.launch(null) })
                }
                item(key = "how") {
                    DsNote(
                        "Each scene gets one picture. Copy its prompt, paste it into ChatGPT and save the picture it makes, " +
                            "then tap Add and pick it: the app shrinks it, names it for its verses and keeps it in your folder. " +
                            "Made several in order? Add several in order picks them all at once.",
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                if (scenes.books.size > 1) {
                    item(key = "books") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 18.dp)
                        ) {
                            for (b in scenes.books) BookChip(b, b == book) { chosenBook = b }
                        }
                    }
                }
                if (book != null) {
                    item(key = "book") {
                        Column(Modifier.padding(top = 20.dp)) {
                            Text(
                                text = book,
                                fontSize = 24.sp,
                                fontFamily = GelasioFontFamily,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "$done of ${inBook.size} scenes have a picture",
                                fontSize = 13.5.sp,
                                fontFamily = WorkSansFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            val going = batch
                            if (going != null) {
                                Text(
                                    text = "Adding ${going.first + 1} of ${going.second}…",
                                    fontSize = 13.5.sp,
                                    fontFamily = WorkSansFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { (going.first + 0.5f) / going.second },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (nextEmpty != null) {
                                Spacer(Modifier.height(14.dp))
                                DsOutlineAccentButton(text = "Add several in order", onClick = { addSeveral(nextEmpty) })
                                DsNote(
                                    "Fills the empty scenes from ${nextEmpty.verses} on, in the order you pick the pictures.",
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
                var chapter = -1
                for (scene in inBook) {
                    if (scene.chapter != chapter) {
                        chapter = scene.chapter
                        item(key = "chapter ${scene.chapter}") {
                            DsSectionLabel("Chapter ${scene.chapter}", modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    item(key = scene.name) {
                        val own = shelf.forPlace(scene.name)
                        SceneRow(
                            scene = scene,
                            own = own,
                            busy = scene.name in adding,
                            thumbnail = { picture -> Thumbnail(picture, pictures, thumbnails) },
                            onCopyPrompt = { copy("Prompt", scene.prompt) },
                            onCopyName = { copy("Name", scene.name) },
                            onAdd = { addOne(scene) },
                            onPreview = { previewing = scene },
                            onSeveral = { addSeveral(scene) },
                            onRemove = { own.firstOrNull()?.let { removing = scene to it } }
                        )
                    }
                }
                if (loaded != null && scenes.books.isEmpty()) {
                    item(key = "none") { DsNote("No scenes yet.", modifier = Modifier.padding(top = 20.dp)) }
                }
            }
        }

        val shown = previewing
        val picture = shown?.let { shelf.forPlace(it.name).firstOrNull() }
        if (shown != null && picture != null) {
            PicturePreview(
                scene = shown,
                picture = picture,
                others = shelf.forPlace(shown.name).size - 1,
                pictures = pictures,
                onChange = { addOne(shown) },
                onRemove = { removing = shown to picture },
                onClose = { previewing = null }
            )
        }
    }

    removing?.let { (scene, picture) ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this picture?") },
            text = { Text("${scene.verses} · ${scene.title}. It's deleted from your pictures folder (${picture.file}).") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        if (pictures.remove(picture)) {
                            if (shelf.forPlace(scene.name).none { it.uri != picture.uri }) previewing = null
                        } else {
                            say("That picture couldn't be removed.")
                        }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }

    added?.let { (results, left) ->
        AddedDialog(
            results = results,
            left = left,
            pictures = pictures,
            thumbnails = thumbnails,
            onUndo = { result ->
                result.picture?.let { picture ->
                    scope.launch {
                        if (pictures.remove(picture)) {
                            added = added?.let { (list, n) -> list.map { if (it === result) it.copy(picture = null, trouble = "Taken off") else it } to n }
                        }
                    }
                }
            },
            onDone = { added = null }
        )
    }
}

/**
 * Where "Add several in order" starts: the first empty scene after the last one with a picture (so a
 * batch carries on where the last left off), or the first empty one.
 */
internal fun nextEmptyScene(scenes: List<PictureScene>, shelf: PictureShelf): PictureScene? {
    val lastFilled = scenes.indexOfLast { shelf.forPlace(it.name).isNotEmpty() }
    return scenes.drop(lastFilled + 1).firstOrNull { shelf.forPlace(it.name).isEmpty() }
        ?: scenes.firstOrNull { shelf.forPlace(it.name).isEmpty() }
}

/** The scenes several pictures go to, in order: [from] and those after it, the empty ones. */
internal fun emptyScenesFrom(scenes: List<PictureScene>, from: PictureScene, shelf: PictureShelf): List<PictureScene> =
    scenes.dropWhile { it != from }.filter { shelf.forPlace(it.name).isEmpty() }

@Composable
private fun FolderSection(status: Pictures.Status, count: Int, onChoose: () -> Unit) {
    Column {
        DsSectionLabel("Pictures folder", isFirst = true)
        when {
            status.folder == null -> {
                DsNote(
                    "Choose a folder to keep your pictures in, a new empty one is best (Pictures/Bible, say). They stay " +
                        "there if the app is ever reinstalled, and pictures you name yourself, like Genesis 1.3-5.png, show too."
                )
                Spacer(Modifier.height(12.dp))
                DsOutlineAccentButton(text = "Choose a folder", onClick = onChoose)
            }
            status.lost -> {
                DsNote("The app can't open “${status.folder}” any more (moved, or its access taken away). Choose it again.")
                Spacer(Modifier.height(12.dp))
                DsOutlineAccentButton(text = "Choose the folder again", onClick = onChoose)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = status.folder,
                            fontSize = 17.sp,
                            fontFamily = GelasioFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (status.looking) "Looking through it…" else if (count == 1) "1 picture" else "$count pictures",
                            fontSize = 13.5.sp,
                            fontFamily = WorkSansFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onChoose) { Text("Change") }
                }
            }
        }
    }
}

@Composable
private fun BookChip(book: String, chosen: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = book,
        fontSize = 13.5.sp,
        fontFamily = WorkSansFontFamily,
        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
        color = if (chosen) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (chosen) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.outlineVariant, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/**
 * A scene: its picture (or a space for one), its verses and title, and Copy prompt and Add (Change,
 * once it has a picture); the menu has its name to copy, Add several from here, and Remove.
 */
@Composable
private fun SceneRow(
    scene: PictureScene,
    own: List<Picture>,
    busy: Boolean,
    thumbnail: @Composable (Picture) -> Unit,
    onCopyPrompt: () -> Unit,
    onCopyName: () -> Unit,
    onAdd: () -> Unit,
    onPreview: () -> Unit,
    onSeveral: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val picture = own.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceVariant)
                .clickable(role = Role.Button, onClickLabel = if (picture != null) "See the picture" else "Add a picture") {
                    if (picture != null) onPreview() else onAdd()
                }
        ) {
            if (picture != null) {
                thumbnail(picture)
            } else {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(
                        text = scene.verses,
                        fontSize = 12.sp,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em,
                        color = colors.tertiary
                    )
                    Text(
                        text = scene.title,
                        fontSize = 17.sp,
                        lineHeight = 1.3.em,
                        fontFamily = GelasioFontFamily,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (own.size > 1) {
                        Text(
                            text = "${own.size} pictures, taking turns",
                            fontSize = 12.5.sp,
                            fontFamily = WorkSansFontFamily,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More for ${scene.verses}", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Copy name (${scene.name})") }, onClick = { menu = false; onCopyName() })
                        DropdownMenuItem(text = { Text("Add several from here") }, onClick = { menu = false; onSeveral() })
                        if (picture != null) {
                            DropdownMenuItem(text = { Text("Remove picture") }, onClick = { menu = false; onRemove() })
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                SmallButton("Copy prompt", primary = false, onClick = onCopyPrompt)
                if (busy) {
                    Text(
                        text = "Adding…",
                        fontSize = 13.sp,
                        fontFamily = WorkSansFontFamily,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    SmallButton(if (picture != null) "Change" else "Add", primary = picture == null, onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 13.5.sp,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        color = if (primary) colors.onPrimary else colors.primary,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (primary) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.primary.copy(alpha = 0.6f), CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

// A picture, small, read once and kept for a while (by its file and when it last changed).
@Composable
private fun Thumbnail(picture: Picture, pictures: Pictures, cache: LruCache<String, ImageBitmap>) {
    val key = "${picture.uri}#${picture.modified}"
    val image by produceState(cache.get(key), key) {
        value = cache.get(key) ?: pictures.thumbnail(picture.uri, THUMB_PIXELS)?.asImageBitmap()?.also { cache.put(key, it) }
    }
    image?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A scene's picture, whole, with Change and Remove. */
@Composable
private fun PicturePreview(
    scene: PictureScene,
    picture: Picture,
    others: Int,
    pictures: Pictures,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    val key = "${picture.uri}#${picture.modified}"
    val image by produceState<ImageBitmap?>(null, key) {
        value = pictures.thumbnail(picture.uri, PREVIEW_PIXELS)?.asImageBitmap()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {}
    ) {
        image?.let {
            Image(
                it,
                contentDescription = scene.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 120.dp)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "${scene.verses} · ${scene.title}",
                fontSize = 16.sp,
                fontFamily = GelasioFontFamily,
                color = Color(0xFFF2EBE3)
            )
            Text(
                text = picture.file + if (others > 0) " · and $others more, taking turns" else "",
                fontSize = 12.5.sp,
                fontFamily = WorkSansFontFamily,
                color = Color(0xFFF2EBE3).copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                TextButton(onClick = onChange) { Text("Change", color = Color(0xFFE8C27A)) }
                TextButton(onClick = onRemove) { Text("Remove", color = Color(0xFFE8C27A)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = Color(0xFFF2EBE3)) }
            }
        }
    }
}

/** Where each of several pictures went, with Undo for any that landed on the wrong scene. */
@Composable
private fun AddedDialog(
    results: List<Added>,
    left: Int,
    pictures: Pictures,
    thumbnails: LruCache<String, ImageBitmap>,
    onUndo: (Added) -> Unit,
    onDone: () -> Unit
) {
    val saved = results.count { it.picture != null }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (saved == results.size) "Added ${if (saved == 1) "1 picture" else "$saved pictures"}" else "Added $saved of ${results.size}") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results) { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(width = 32.dp, height = 57.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                result.picture?.let { Thumbnail(it, pictures, thumbnails) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${result.scene.verses} · ${result.scene.title}", fontSize = 14.sp)
                                result.trouble?.let {
                                    Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (result.picture != null) {
                                TextButton(onClick = { onUndo(result) }) { Text("Undo") }
                            }
                        }
                    }
                }
                if (left > 0) {
                    Text(
                        text = "${if (left == 1) "1 more wasn't" else "$left more weren't"} added: there were no empty scenes left after these.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
}

// A scene's picture in the list: 9:16, like a card.
private val THUMB_WIDTH = 54.dp
private val THUMB_HEIGHT = 96.dp

// Pictures are read at least this tall for the list, and for a look at one whole.
private const val THUMB_PIXELS = 200
private const val PREVIEW_PIXELS = 1400

// Pictures kept read for the list (about 130 KB each).
private const val THUMBNAILS_KEPT = 120
