package id.harts.videocollage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Renders the collage on-device with Media3 Transformer (hardware encoder, no FFmpeg).
 * Each video is cropped to fill its box, then all boxes are composited into one MP4.
 * The longest video sets the output length; shorter ones loop.
 */
@OptIn(UnstableApi::class)
class CollageExporter(private val context: Context) {

    interface Callback {
        fun onProgress(percent: Int)
        fun onDone(savedUri: Uri)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()

    /** Must be called on the main thread. [clips] are in box order (Video 1, 2, ...). */
    fun export(layout: CollageLayout, aspect: Aspect, clips: List<Clip>, sound: Sound, cb: Callback) {
        val outW = aspect.width
        val outH = aspect.height
        val boxes = layout.pixelCells(outW, outH)

        // Longest clip goes first: it is the "primary" sequence that sets the length; the rest loop.
        val order: List<Int> = clips.indices.sortedByDescending { clips[it].durationUs }

        val sequences = order.mapIndexed { seqIndex, box ->
            val r = boxes[box]
            val clip = clips[box]
            val keepAudio = sound.keeps(box) && !clip.isImage
            val mediaItem = if (clip.isImage)
                MediaItem.Builder().setUri(clip.uri).setImageDurationMs(PHOTO_DURATION_US / 1000).build()
            else MediaItem.fromUri(clip.uri)
            val item = EditedMediaItem.Builder(mediaItem)
                .apply { if (clip.isImage) setFrameRate(30) }
                .setRemoveAudio(!keepAudio)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                r.width(), r.height(), Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            )
                        )
                    )
                )
                .build()
            val seq = if (keepAudio) EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item))
                      else EditedMediaItemSequence.withVideoFrom(listOf(item))
            if (seqIndex == 0) seq else seq.buildUpon().setIsLooping(true).build()
        }

        val compositor = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size = Size(outW, outH)

            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                val r = boxes[order[inputId]]
                // Position = centre of the box, in GL coordinates (-1..1, y up).
                val x = r.exactCenterX() / outW * 2f - 1f
                val y = 1f - r.exactCenterY() / outH * 2f
                return StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, 0f)
                    .setBackgroundFrameAnchor(x, y)
                    .build()
            }
        }

        val composition = Composition.Builder(sequences)
            .setVideoCompositorSettings(compositor)
            .experimentalSetForceAudioTrack(sound !is Sound.Mute && clips.any { !it.isImage })
            .build()

        val tmp = File(context.cacheDir, "collage_${System.currentTimeMillis()}.mp4")

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    io.execute {
                        try {
                            val uri = saveToGallery(tmp)
                            main.post { cb.onDone(uri) }
                        } catch (e: Exception) {
                            main.post { cb.onError("Saving failed: ${e.message}") }
                        } finally {
                            tmp.delete()
                        }
                    }
                }

                override fun onError(
                    composition: Composition, exportResult: ExportResult, exportException: ExportException
                ) {
                    transformer = null
                    tmp.delete()
                    cb.onError(exportException.message ?: exportException.errorCodeName)
                }
            })
            .build()
        transformer = t
        t.start(composition, tmp.absolutePath)
        pollProgress(cb)
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    private fun pollProgress(cb: Callback) {
        main.postDelayed(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                if (t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    cb.onProgress(progressHolder.progress)
                }
                main.postDelayed(this, 300)
            }
        }, 300)
    }

    /** Copies the finished file into Movies/VideoCollage so it shows in the Gallery. */
    private fun saveToGallery(file: File): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Collage_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoCollage")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Could not create gallery entry")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
