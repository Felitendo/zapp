package de.christinecoenen.code.zapp.utils.video

import android.animation.LayoutTransition
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.AspectRatioFrameLayout.AspectRatioListener
import androidx.media3.ui.PlayerView
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SwipeablePlayerView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : PlayerView(context, attrs), OnTouchListener, AspectRatioListener {

	companion object {
		private const val INDICATOR_WIDTH = 300
	}

	private var volumeIndicator = SwipeIndicatorView(context)
	private var brightnessIndicator = SwipeIndicatorView(context)

	private var window: Window = (context as Activity).window
	private var audioManager: AudioManager =
		context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	private var settingsRepository: SettingsRepository
	private var gestureDetector: GestureDetector
	private var scaleGestureDetector: ScaleGestureDetector

	private var hasAspectRatioMismatch = false


	private val isZoomStateCropped: Boolean
		get() = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM

	private val isZoomStateBoxed: Boolean
		get() = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT


	init {

		volumeIndicator.setIconResId(R.drawable.ic_volume_up_white_24dp)
		addView(
			volumeIndicator,
			LayoutParams(INDICATOR_WIDTH, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
		)

		brightnessIndicator.setIconResId(R.drawable.ic_brightness_6_white_24dp)
		addView(
			brightnessIndicator,
			LayoutParams(INDICATOR_WIDTH, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
		)

		gestureDetector =
			GestureDetector(context.applicationContext, WipingControlGestureListener())
		gestureDetector.setIsLongpressEnabled(false)
		scaleGestureDetector =
			ScaleGestureDetector(context.applicationContext, ScaleGestureListener())

		setOnTouchListener(this)
		setAspectRatioListener(this)

		layoutTransition = LayoutTransition()

		settingsRepository = SettingsRepository(getContext())

		if (settingsRepository.isPlayerZoomed) {
			setZoomStateCropped()
		} else {
			setZoomStateBoxed()
		}

		subtitleView?.setUserDefaultStyle()
		subtitleView?.setUserDefaultTextSize()
	}

	private fun adjustBrightness(yPercent: Float) {
		val lp = window.attributes
		lp.screenBrightness = yPercent
		window.attributes = lp
		brightnessIndicator.setValue(yPercent)
	}

	private fun adjustVolume(yPercent: Float) {
		val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
		val volume = (yPercent * maxVolume).toInt()
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
		volumeIndicator.setValue(yPercent)
	}

	private fun endScroll() {
		volumeIndicator.visibility = GONE
		brightnessIndicator.visibility = GONE
	}

	override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
		gestureDetector.onTouchEvent(motionEvent)
		scaleGestureDetector.onTouchEvent(motionEvent)

		if (motionEvent.action == MotionEvent.ACTION_UP) {
			endScroll()
		}

		return useController
	}

	private fun setZoomStateCropped() {
		resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
		settingsRepository.isPlayerZoomed = true
	}

	private fun setZoomStateBoxed() {
		resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
		settingsRepository.isPlayerZoomed = false
	}

	override fun onAspectRatioUpdated(
		targetAspectRatio: Float,
		naturalAspectRatio: Float,
		aspectRatioMismatch: Boolean
	) {
		hasAspectRatioMismatch = aspectRatioMismatch
	}

	private inner class ScaleGestureListener : SimpleOnScaleGestureListener() {

		override fun onScaleEnd(detector: ScaleGestureDetector) {
			if (!hasAspectRatioMismatch) {
				return
			}

			if (detector.scaleFactor > 1) {
				if (!isZoomStateCropped) {
					setZoomStateCropped()
					Toast.makeText(context, R.string.player_zoom_state_cropped, Toast.LENGTH_SHORT)
						.show()
				}
			} else {
				if (!isZoomStateBoxed) {
					setZoomStateBoxed()
					Toast.makeText(context, R.string.player_zoom_state_boxed, Toast.LENGTH_SHORT)
						.show()
				}
			}
		}

	}

	private inner class WipingControlGestureListener : SimpleOnGestureListener() {

		private val forbiddenAreaSizeTop = 34
		private val minVerticalMovementNeeded = 100

		private var canUseWipeControls = false
		private var maxVerticalMovement = 0f

		override fun onSingleTapUp(e: MotionEvent): Boolean {
			performClick()
			return true
		}

		override fun onDown(e: MotionEvent): Boolean {
			maxVerticalMovement = 0f
			canUseWipeControls = !isControllerFullyVisible

			return super.onDown(e)
		}

		override fun onScroll(
			e1: MotionEvent?,
			e2: MotionEvent,
			distanceX: Float,
			distanceY: Float
		): Boolean {
			if (e1 == null) {
				return false
			}

			if (!canUseWipeControls || e1.y <= forbiddenAreaSizeTop) {
				return super.onScroll(e1, e2, distanceX, distanceY)
			}

			val distanceYSinceTouchbegin = e1.y - e2.y
			maxVerticalMovement = max(maxVerticalMovement, abs(distanceYSinceTouchbegin))

			val enoughVerticalMovement = maxVerticalMovement > minVerticalMovementNeeded

			if (!enoughVerticalMovement) {
				return super.onScroll(e1, e2, distanceX, distanceY)
			}

			var yPercent = 1 - e2.y / height
			// use a wider percent range than needed to improve handling on the screen edges
			yPercent = yPercent * 1.2f - 0.1f
			// truncate to valid percent value between 0 and 1
			yPercent = min(1f, max(0f, yPercent))

			return when {
				e2.x < INDICATOR_WIDTH -> {
					adjustBrightness(yPercent)
					true
				}

				e2.x > width - INDICATOR_WIDTH -> {
					adjustVolume(yPercent)
					true
				}

				else -> {
					endScroll()
					super.onScroll(e1, e2, distanceX, distanceY)
				}
			}
		}
	}
}
