package com.example.mybible.ui.versescroll

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.PictureSize
import com.example.mybible.versescroll.ShareShape
import com.example.mybible.versescroll.SharePictureLayout
import com.example.mybible.versescroll.VerseFeedCard
import com.example.mybible.versescroll.VerseScrollController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shares [card] as a picture (backlog 53): a preview of it in the shape chosen last time, the shapes to
 * choose from, and Share (WhatsApp, Instagram and the rest) and Save to phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareSheet(card: VerseFeedCard, controller: VerseScrollController, colors: VsColors, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maker = remember(context) { SharePictureMaker(context) }
    val screen = remember(context) { screenSize(context) }
    val content = card.content
    val scene = remember(card.uid) { controller.sceneFor(card) }
    val showTelugu = controller.showTelugu
    val verse = remember(card.uid, showTelugu) {
        SharedVerse(
            lines = content.lines,
            eyebrow = genreOf(content.ref.book),
            label = content.label,
            telugu = if (showTelugu) content.lines.mapNotNull { it.telugu }.joinToString(" ") else "",
            teluguLabel = content.teluguLabel,
            teluguSize = controller.teluguSize
        )
    }
    // The card's colors: over its photo, or on the plain page.
    val words = if (scene != null) colors.onScene() else colors
    val palette = SharePalette(words.dark, words.ink.toArgb(), words.soft.toArgb(), words.gold.toArgb(), colors.bg.toArgb())
    val veil = scene?.let { if (colors.dark) it.kd else it.kl } ?: 0f

    // The photo, read afresh to draw on; read once it has been (or when there's none to read).
    var photo by remember(card.uid) { mutableStateOf<Bitmap?>(null) }
    var photoRead by remember(card.uid) { mutableStateOf(scene == null) }
    LaunchedEffect(card.uid) {
        if (scene != null) {
            photo = controller.sharePhoto(scene.id)
            photoRead = true
        }
    }

    val shape = controller.shareShape
    val size = shape.size(screen.width, screen.height)
    // The picture in the shape chosen, drawn once the photo's been read.
    var picture by remember(card.uid, showTelugu) { mutableStateOf<Pair<ShareShape, Bitmap>?>(null) }
    LaunchedEffect(shape, photoRead, verse) {
        if (!photoRead || picture?.first == shape) return@LaunchedEffect
        val drawn = withContext(Dispatchers.Default) { maker.draw(verse, shape, size, photo, veil, palette) }
        picture = shape to drawn
    }
    val shown = picture?.takeIf { it.first == shape }?.second
    val preview = remember(shown) { shown?.asImageBitmap() }

    val name = SharePictureLayout.fileName(content.label, shape)
    var sharing by remember { mutableStateOf(false) }
    var shareFailed by remember(shape) { mutableStateOf(false) }
    var saved by remember(shape) { mutableStateOf<Boolean?>(null) }

    fun share(bitmap: Bitmap) {
        sharing = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) { runCatching { SharedPictures.forSharing(context, bitmap, name) }.getOrNull() }
            sharing = false
            shareFailed = uri == null
            if (uri == null) return@launch
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(content.label, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${content.label}"))
        }
    }

    fun save(bitmap: Bitmap) {
        scope.launch {
            saved = withContext(Dispatchers.IO) { SharedPictures.save(context, bitmap, name) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            Text(
                text = "Share ${content.label}".uppercase(),
                color = colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.12.em
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(size.width.toFloat() / size.height)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.line)
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "${content.label} as a ${shape.label} picture",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = colors.gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ShareShape.entries.forEach { option ->
                    ShapeChip(option, selected = option == shape, colors = colors, modifier = Modifier.weight(1f)) {
                        controller.chooseShareShape(option)
                    }
                }
            }
            PillButton(
                label = when {
                    sharing -> "Getting it ready…"
                    shareFailed -> "Couldn’t share it. Try again"
                    else -> "Share"
                },
                primary = true,
                colors = colors,
                onClick = { if (shown != null && !sharing) share(shown) },
                modifier = Modifier.fillMaxWidth()
            )
            if (SharedPictures.canSave) {
                PillButton(
                    label = when (saved) {
                        true -> "Saved in Pictures › My Bible"
                        false -> "Couldn’t save it. Try again"
                        null -> "Save to phone"
                    },
                    primary = false,
                    colors = colors,
                    onClick = { if (shown != null && saved != true) save(shown) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** One of the shapes to share in: its proportions, and what it's for. */
@Composable
private fun ShapeChip(shape: ShareShape, selected: Boolean, colors: VsColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary else colors.bg)
            .border(1.dp, if (selected) colors.primary else colors.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 2.dp, end = 2.dp, top = 9.dp, bottom = 9.dp)
    ) {
        Text(
            text = shape.label,
            color = if (selected) colors.onPrimary else colors.ink,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1
        )
        Text(
            text = shape.use,
            color = if (selected) colors.onPrimary.copy(alpha = 0.85f) else colors.soft,
            fontFamily = WorkSansFontFamily,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

// The phone's whole screen, its bars included, for a wallpaper the shape of it.
private fun screenSize(context: Context): PictureSize {
    val windows = context.getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windows.maximumWindowMetrics.bounds.let { PictureSize(it.width(), it.height()) }
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windows.defaultDisplay.getRealMetrics(metrics)
        PictureSize(metrics.widthPixels, metrics.heightPixels)
    }
}

private val PREVIEW_HEIGHT = 320.dp
