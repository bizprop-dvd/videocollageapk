package id.harts.videocollage

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private val accent = Color.parseColor("#FF6A3D")
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private var layoutType = CollageLayout.ONE_TOP_TWO_BOTTOM
    private var aspect = Aspect.PORTRAIT
    private var sound: Sound = Sound.From(0)
    private lateinit var soundHolder: LinearLayout
    private var soundRow: View? = null
    private val clips = arrayOfNulls<Clip>(4)
    private var pickingCell = -1
    private var exporting = false

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var exporter: CollageExporter

    private lateinit var collage: CollageView
    private lateinit var exportBtn: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private val layoutIcons = mutableListOf<LayoutIconView>()
    private val controls = mutableListOf<View>()

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pickingCell >= 0) loadClip(pickingCell, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exporter = CollageExporter(this)
        setContentView(buildUi())
    }

    // ---------- UI ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
        }
        val pad = 16.dp
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(pad + b.left, pad + b.top, pad + b.right, pad + b.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "Video Collage"
            textSize = 22f; setTextColor(Color.WHITE); paint.isFakeBoldText = true
        })

        // Layout chooser
        root.addView(label("Layout"))
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (l in CollageLayout.entries) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 12.dp, 0)
            }
            val icon = LayoutIconView(this, l).apply {
                layoutParams = LinearLayout.LayoutParams(52.dp, 64.dp)
                selectedLayout = l == layoutType
                setOnClickListener { selectLayout(l) }
            }
            layoutIcons += icon; controls += icon
            cell.addView(icon)
            cell.addView(TextView(this).apply {
                text = l.label; textSize = 10f; setTextColor(Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER; width = 64.dp; setLines(2)
            })
            iconRow.addView(cell)
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(iconRow)
        })

        root.addView(radioRow("Shape", Aspect.entries.map { it.label to it }, aspect) {
            aspect = it; collage.aspect = it
        })
        soundHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(soundHolder)
        rebuildSoundRow()

        collage = CollageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { topMargin = 12.dp; bottomMargin = 8.dp }
            layout = layoutType; aspect = this@MainActivity.aspect
            onCellTap = { i -> if (!exporting) pickVideo(i) }
        }
        root.addView(collage)

        status = TextView(this).apply {
            text = "Tap each box to choose a video or photo. Shorter videos loop to match the longest."
            textSize = 13f; setTextColor(Color.parseColor("#AAAAAA")); gravity = Gravity.CENTER
        }
        root.addView(status)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE; progressTintList = ColorStateList.valueOf(accent)
        }
        root.addView(progress)

        exportBtn = Button(this).apply {
            text = "Make collage"
            setTextColor(Color.WHITE); textSize = 16f
            backgroundTintList = ColorStateList.valueOf(accent)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp)
                .apply { topMargin = 8.dp }
            setOnClickListener { if (exporting) cancelExport() else startExport() }
        }
        root.addView(exportBtn)
        return root
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#888888"))
        setPadding(0, 12.dp, 0, 6.dp)
    }

    private fun <T> radioRow(title: String, options: List<Pair<String, T>>, initial: T, onPick: (T) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp, 0, 0)
        }
        row.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(Color.parseColor("#888888")); width = 56.dp
        })
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        options.forEach { (name, value) ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId(); text = name; textSize = 13f
                setTextColor(Color.WHITE); buttonTintList = ColorStateList.valueOf(accent)
                setPadding(0, 0, 8.dp, 0)
                setOnClickListener { onPick(value) }
            }
            controls += rb
            group.addView(rb)
            if (value == initial) group.check(rb.id)
        }
        row.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(group) })
        return row
    }

    private fun selectLayout(l: CollageLayout) {
        layoutType = l
        layoutIcons.forEach { it.selectedLayout = it.layoutType == l }
        collage.layout = l
        rebuildSoundRow()
    }

    /** Sound options follow the layout: Video 1..N, Mix all, No sound. */
    private fun rebuildSoundRow() {
        val n = layoutType.count
        val s = sound
        if (s is Sound.From && s.index >= n) sound = Sound.From(0)
        val options = (0 until n).map { "Video ${it + 1}" to (Sound.From(it) as Sound) } +
            listOf("Mix all" to Sound.Mix, "No sound" to Sound.Mute)
        soundRow?.let { old -> controls.removeAll { it.parent?.parent?.parent == old || it.parent?.parent == old } }
        soundHolder.removeAllViews()
        soundRow = radioRow("Sound", options, sound) { sound = it }.also { soundHolder.addView(it) }
    }

    // ---------- Picking videos ----------

    private fun pickVideo(cell: Int) {
        pickingCell = cell
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    private fun loadClip(cell: Int, uri: Uri) {
        io.execute {
            val mime = contentResolver.getType(uri)
            val isImage = mime?.startsWith("image/") == true
            val r = MediaMetadataRetriever()
            val clip = if (isImage) try {
                val src = ImageDecoder.createSource(contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    val s = maxOf(1, maxOf(info.size.width, info.size.height) / 720)
                    dec.setTargetSize(info.size.width / s, info.size.height / s)
                }
                Clip(uri, PHOTO_DURATION_US, bmp, isImage = true, mimeType = mime)
            } catch (e: Exception) { null } finally { r.release() } else try {
                r.setDataSource(this, uri)
                val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val at = min(1_000_000L, durMs * 500L) // 1s in, or halfway for very short clips
                val thumb = r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 540, 540)
                val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                Clip(uri, durMs * 1000L, thumb, mimeType = mime, hasAudio = hasAudio)
            } catch (e: Exception) {
                null
            } finally {
                r.release()
            }
            main.post {
                if (clip == null) {
                    toast("Couldn't open that file. Try another one.")
                } else {
                    clips[cell] = clip
                    collage.setThumb(cell, clip.thumb)
                }
            }
        }
    }

    // ---------- Export ----------

    private fun startExport() {
        val n = layoutType.count
        for (i in 0 until n) {
            if (clips[i] == null) { toast("Choose a video for box ${i + 1}"); return }
        }
        setExporting(true)
        status.text = "Making collage… keep the app open."
        exporter.export(layoutType, aspect, (0 until n).map { clips[it]!! }, sound, object : CollageExporter.Callback {
            override fun onProgress(percent: Int) {
                progress.progress = percent
                status.text = "Making collage… $percent%"
            }

            override fun onDone(savedUri: Uri) {
                setExporting(false)
                status.text = "Saved to Gallery › Movies/VideoCollage"
                exportBtn.text = "Make collage"
                showResult(savedUri)
            }

            override fun onError(message: String) {
                setExporting(false)
                status.text = "Failed: $message"
            }
        })
    }

    private fun cancelExport() {
        exporter.cancel()
        setExporting(false)
        status.text = "Cancelled."
    }

    private fun setExporting(on: Boolean) {
        exporting = on
        controls.forEach { it.isEnabled = !on }
        progress.visibility = if (on) View.VISIBLE else View.GONE
        progress.progress = 0
        exportBtn.text = if (on) "Cancel" else "Make collage"
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showResult(uri: Uri) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Collage saved")
            .setMessage("Your video is in Gallery › Movies/VideoCollage.")
            .setPositiveButton("Play") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            .setNeutralButton("Share") { _, _ ->
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share collage"))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (exporting) exporter.cancel()
        io.shutdown()
    }
}
