package com.example.shopeevideoinstan

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.*
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            ShopeeEditor(
                onSpeak = { text, gender -> speak(text, gender) },
                onStop = { tts?.stop() },
                onExport = { uri, text, gender, done -> exportVideo(uri, text, gender, done) }
            )
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts?.language = Locale("id", "ID")
    }

    private fun chooseVoice(gender: String) {
        val engine = tts ?: return
        val idVoices = engine.voices?.filter { it.locale.language == "id" }.orEmpty()
        val terms = if (gender == "Perempuan") listOf("female", "woman") else listOf("male", "man")
        val voice = idVoices.firstOrNull { v -> terms.any { v.name.lowercase().contains(it) } }
            ?: idVoices.firstOrNull()
        if (voice != null) engine.voice = voice
    }

    private fun speak(text: String, gender: String) {
        if (!ready || text.isBlank()) return
        chooseVoice(gender)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview_voice")
    }

    private fun exportVideo(uri: Uri?, script: String, gender: String, done: (String) -> Unit) {
        if (!ready || uri == null || script.isBlank()) {
            done("Pilih video dan buat naskah dulu.")
            return
        }
        chooseVoice(gender)
        val voiceFile = File(cacheDir, "voiceover.wav")
        voiceFile.delete()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                mainHandler.post { done("Gagal membuat voice-over.") }
            }
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    try {
                        startMp4Export(uri, voiceFile, script, done)
                    } catch (e: Exception) {
                        done("Ekspor gagal: ${e.localizedMessage ?: "format video tidak didukung"}")
                    }
                }
            }
        })
        val result = tts?.synthesizeToFile(script, Bundle(), voiceFile, "export_voice")
        if (result != TextToSpeech.SUCCESS) {
            done("Suara belum bisa dibuat. Periksa mesin Text-to-Speech HP.")
            return
        }
        done("Sedang membuat suara dan menyiapkan video…")
    }

    @OptIn(UnstableApi::class)
    private fun startMp4Export(videoUri: Uri, voiceFile: File, script: String, done: (String) -> Unit) {
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val output = File(outputDir, "ShopeeVideo_${System.currentTimeMillis()}.mp4")
        val subtitleBitmap = makeSubtitleBitmap(script)

        val videoEdited = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(
                listOf(BitmapOverlay.createStaticBitmapOverlay(subtitleBitmap))
            ))))
            .build()
        val voiceEdited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(voiceFile))).build()

        val videoSequence = EditedMediaItemSequence.withVideoFrom(listOf(videoEdited))
        val audioSequence = EditedMediaItemSequence.withAudioFrom(listOf(voiceEdited))
        val composition = Composition.Builder(videoSequence)
            .addSequence(audioSequence)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                addToGallery(output)
                done("SELESAI! Video MP4 tersimpan di Galeri > Movies > Shopee Video Instan.")
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                done("Ekspor gagal: ${exportException.localizedMessage ?: "coba video lain"}")
            }
        }
        Transformer.Builder(this).addListener(listener).build().start(composition, output.absolutePath)
        done("Sedang merender video, suara, dan subtitle…")
    }

    private fun makeSubtitleBitmap(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val lines = wrapText(text, 30).take(5)
        val lineHeight = 58f
        val boxHeight = lines.size * lineHeight + 72f
        val top = 1500f - boxHeight
        paint.color = Color.argb(185, 0, 0, 0)
        canvas.drawRoundRect(70f, top, 1010f, 1515f, 28f, 28f, paint)
        paint.color = Color.WHITE
        paint.textSize = 43f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, 540f, top + 62f + (i * lineHeight), paint)
        }
        return bitmap
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (next.length > maxChars && line.isNotEmpty()) {
                result.add(line); line = word
            } else line = next
        }
        if (line.isNotBlank()) result.add(line)
        return result
    }

    private fun addToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Shopee Video Instan")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopeeEditor(
    onSpeak: (String, String) -> Unit,
    onStop: () -> Unit,
    onExport: (Uri?, String, String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var product by remember { mutableStateOf("Sepatu anak") }
    var price by remember { mutableStateOf("100 ribuan") }
    var hook by remember { mutableStateOf("POV: Cari sepatu anak yang keren, ternyata ada yang 100 ribuan!") }
    var script by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Perempuan") }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Pilih video, lalu buat naskah konten.") }
    val hooks = listOf(
        "POV: Cari sepatu anak yang keren, ternyata ada yang 100 ribuan!",
        "POV: Anak minta sepatu baru, bunda maunya harga tetap ramah di kantong.",
        "Siapa bilang sepatu anak yang keren harus mahal?",
        "Niatnya cuma lihat-lihat, eh malah nemu sepatu anak sebagus ini.",
        "Bunda, ada sepatu anak harga bersahabat yang wajib dilirik!"
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        videoUri = uri
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
    }
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Shopee Video Instan") }) }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bikin video jualan lebih praktis", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (videoUri == null) "Pilih video dari galeri" else "Ganti video")
                }
                videoUri?.let { uri ->
                    AndroidView(factory = { ctx -> VideoView(ctx).apply {
                        setVideoURI(uri); setOnPreparedListener { it.isLooping = true; start() }
                    } }, modifier = Modifier.fillMaxWidth().height(210.dp))
                }
                OutlinedTextField(product, { product = it }, label = { Text("Nama produk") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it }, label = { Text("Harga / promo") }, modifier = Modifier.fillMaxWidth())
                Text("Pilih hook", style = MaterialTheme.typography.titleSmall)
                hooks.forEach { item ->
                    OutlinedButton(onClick = { hook = item }, modifier = Modifier.fillMaxWidth()) { Text(item) }
                }
                OutlinedTextField(hook, { hook = it }, label = { Text("Edit hook") }, modifier = Modifier.fillMaxWidth())
                Text("Pengisi suara")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(gender, {}, readOnly = true, label = { Text("Pilih suara") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Perempuan", "Laki-laki").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { gender = option; expanded = false })
                        }
                    }
                }
                Button(onClick = {
                    script = "$hook\n$product dengan harga $price. Modelnya menarik dan cocok untuk dipakai sehari-hari. Yuk, cek keranjang kuning dan lihat promonya!"
                }, modifier = Modifier.fillMaxWidth()) { Text("Buat naskah otomatis") }
                if (script.isNotBlank()) {
                    OutlinedTextField(script, { script = it }, label = { Text("Naskah voice-over & subtitle") },
                        modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSpeak(script, gender) }, modifier = Modifier.weight(1f)) { Text("▶ Coba suara") }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                    Button(onClick = {
                        onExport(videoUri, script, gender) { status = it }
                    }, modifier = Modifier.fillMaxWidth()) { Text("BUAT & SIMPAN VIDEO MP4") }
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                Text("Catatan: ekspor memakai Android Media3. Suara perempuan/laki-laki mengikuti voice Indonesia yang tersedia di HP. Subtitle versi ini berupa satu panel teks ringkas di bagian bawah, bukan subtitle per-kalimat yang mengikuti waktu bicara.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
