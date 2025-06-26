package de.christinecoenen.code.zapp.utils.system

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object ImageHelper {

	private val THUMBNAIL_SIZE = Size(640, 240)

	@JvmStatic
	suspend fun loadThumbnailAsync(context: Context, filePath: String?): Bitmap =
		withContext(Dispatchers.Default) {
			loadThumbnail(context, filePath)
		}

	private fun loadThumbnail(context: Context, filePath: String?): Bitmap {
		if (filePath == null) {
			throw Exception("Could not generate thumbnail when filePath is null.")
		}

		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val contentResolver = context.contentResolver

			try {
				// with api level this high we can use content resolver to generate thumbnails
				contentResolver.loadThumbnail(filePath.toUri(), THUMBNAIL_SIZE, null)

			} catch (e: IOException) {
				try {
					// fall back to ThumbnailUtils when content resolver failed
					ThumbnailUtils.createVideoThumbnail(File(filePath), THUMBNAIL_SIZE, null)

				} catch (e: IOException) {
					// complete failure
					throw Exception("Could not generate thumbnail for file $filePath", e)
				}
			}
		} else {
			// old method of loading thumbnails
			@Suppress("DEPRECATION")
			ThumbnailUtils.createVideoThumbnail(
				filePath,
				MediaStore.Images.Thumbnails.FULL_SCREEN_KIND
			)
				?: throw Exception("Could not generate thumbnail for file $filePath")
		}
	}
}
