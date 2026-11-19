package ke.vaccinereminder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

// Turns a photo from the camera or the gallery into an upright, reasonably small JPEG before it is
// uploaded: phone photos are often 5-10 MB, which is slow and costly on mobile data.
object EvidenceImages {
    // Long enough edge to read a vaccination card; the server accepts up to 5 MB.
    const val MAX_SIDE = 1600
    const val MAX_BYTES = 1_500_000
    private const val MIN_SIDE = 200

    enum class Problem { NOT_A_PICTURE, TOO_SMALL, TOO_LARGE }

    class BadImage(val problem: Problem) : Exception(problem.name)

    // Slow (decodes and re-encodes the picture): call it off the main thread.
    fun prepare(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)
        if (type != null && !type.startsWith("image/")) throw BadImage(Problem.NOT_A_PICTURE)
        val open = { resolver.openInputStream(uri) ?: throw BadImage(Problem.NOT_A_PICTURE) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BadImage(Problem.NOT_A_PICTURE)
        if (bounds.outWidth < MIN_SIDE || bounds.outHeight < MIN_SIDE) throw BadImage(Problem.TOO_SMALL)

        // Decode at the smallest power-of-two reduction that still leaves at least MAX_SIDE pixels.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = try {
            open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        } catch (e: OutOfMemoryError) {
            null
        } ?: throw BadImage(Problem.NOT_A_PICTURE)

        val rotation = try {
            open().use(::exifRotation)
        } catch (e: IOException) {
            0
        }
        val upright = scaleAndRotate(decoded, rotation)

        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 45)
        upright.recycle()
        if (bytes.size > MAX_BYTES) throw BadImage(Problem.TOO_LARGE)
        return bytes
    }

    private fun exifRotation(stream: InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun scaleAndRotate(source: Bitmap, rotation: Int): Bitmap {
        val longest = max(source.width, source.height)
        val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
        if (scale == 1f && rotation == 0) return source
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (out !== source) source.recycle()
        return out
    }

    fun decode(bytes: ByteArray): ImageBitmap? =
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            null
        }

    // Camera photos go to a private folder in the app's cache and are deleted once compressed.
    fun captureFile(context: Context): File {
        val dir = File(context.cacheDir, "evidence").apply { mkdirs() }
        return File(dir, "capture-${System.currentTimeMillis()}.jpg")
    }

    fun clearCaptures(context: Context) {
        File(context.cacheDir, "evidence").listFiles()?.forEach { it.delete() }
    }

    fun sizeLabel(bytes: Int): String =
        if (bytes >= 1_000_000) "${(bytes / 100_000.0).roundToInt() / 10.0} MB" else "${(bytes / 1000.0).roundToInt()} KB"
}
