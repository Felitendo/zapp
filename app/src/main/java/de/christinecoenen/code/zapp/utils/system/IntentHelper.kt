package de.christinecoenen.code.zapp.utils.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.core.net.toUri
import de.christinecoenen.code.zapp.R

object IntentHelper {

	/**
	 * Open the given url in a new (external) activity. If no app is found
	 * that can handle this intent, a system chooser is shown.
	 *
	 * @param context
	 * @param url
	 */
	@JvmStatic
	fun openUrl(context: Context, url: String) {
		val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())

		try {
			context.startActivity(browserIntent)
		} catch (e: ActivityNotFoundException) {
			context.startActivity(
				Intent.createChooser(
					browserIntent,
					context.getString(R.string.action_open)
				)
			)
		}
	}

	/**
	 * Opens the default mail app. If no app is found that can handle
	 * this intent, a a system chooser is shown.
	 *
	 * @param context
	 * @param mail    receiver mail address
	 * @param subject mail subject line
	 */
	fun sendMail(context: Context, mail: String, subject: String?) {
		val feedbackIntent = Intent(Intent.ACTION_SENDTO, "mailto:$mail".toUri())
		feedbackIntent.putExtra(Intent.EXTRA_SUBJECT, subject)

		try {
			context.startActivity(feedbackIntent)
		} catch (e: ActivityNotFoundException) {
			context.startActivity(
				Intent.createChooser(
					feedbackIntent,
					context.getString(R.string.action_send_mail)
				)
			)
		}
	}

	/**
	 * Shares the given url as plain text (e.g. for messengers, some players, browsers, etc.).
	 */
	fun shareLink(context: Context, url: String, title: String) {
		val playVideoIntent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_SUBJECT, title)
			putExtra(Intent.EXTRA_TEXT, url)
			putExtra(Intent.EXTRA_TITLE, title)
		}

		context.startActivity(
			Intent.createChooser(playVideoIntent, context.getString(R.string.action_share))
		)
	}

	/**
	 *
	 */
	fun playVideo(context: Context, url: String, title: String) {
		val playVideoIntent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(url.toUri(), "video/*")
			putExtra(Intent.EXTRA_TITLE, title)
		}

		context.startActivity(
			Intent.createChooser(playVideoIntent, context.getString(R.string.action_open))
		)
	}

	fun getRecognizeSpeechIntent(): Intent {
		return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
			.apply {
				putExtra(
					RecognizerIntent.EXTRA_LANGUAGE_MODEL,
					RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
				)
			}
	}

	fun supportsRecognizeSpeechIntent(packageManager: PackageManager): Boolean {
		val voiceInputIntent = getRecognizeSpeechIntent()
		return voiceInputIntent.resolveActivity(packageManager) != null
	}
}
