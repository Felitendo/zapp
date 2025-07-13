package de.christinecoenen.code.zapp.app.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.player.BackgroundPlayerService.Companion.bind
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import de.christinecoenen.code.zapp.databinding.ActivityAbstractPlayerBinding
import de.christinecoenen.code.zapp.utils.system.LifecycleOwnerHelper.launchOnCreated
import de.christinecoenen.code.zapp.utils.system.LifecycleOwnerHelper.launchOnResumed
import de.christinecoenen.code.zapp.utils.system.MultiWindowHelper
import de.christinecoenen.code.zapp.utils.system.MultiWindowHelper.isInsideMultiWindow
import de.christinecoenen.code.zapp.utils.system.MultiWindowHelper.supportsPictureInPictureMode
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

abstract class AbstractPlayerActivity :
	AppCompatActivity(),
	MenuProvider,
	PlayerView.ControllerVisibilityListener,
	SleepTimer.Listener {

	private val viewModel: AbstractPlayerActivityViewModel by viewModel()
	private val settingsRepository: SettingsRepository by inject()

	private val windowInsetsControllerCompat by lazy {
		WindowInsetsControllerCompat(window, binding.fullscreenContent)
	}
	private val powerManager by lazy {
		getSystemService(Context.POWER_SERVICE) as PowerManager
	}

	protected lateinit var binding: ActivityAbstractPlayerBinding

	protected var player: Player? = null
	protected var binder: BackgroundPlayerService.Binder? = null

	abstract val shouldShowOverlay: Boolean

	private val backgroundPlayerServiceConnection: ServiceConnection = object : ServiceConnection {

		override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
			binder = service as BackgroundPlayerService.Binder
			binder!!.setForegroundActivityIntent(intent)

			player = binder!!.getPlayer().apply {
				setView(binding.video)
				sleepTimer.addListener(this@AbstractPlayerActivity)
			}

			launchOnResumed {
				player!!.errorResourceId.collect(::onVideoError)
			}

			loadVideoFromIntent(intent)
		}

		override fun onServiceDisconnected(componentName: ComponentName) {
			binder?.getPlayer()?.sleepTimer?.apply {
				removeListener(this@AbstractPlayerActivity)
			}

			player?.pause()
			player = null
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityAbstractPlayerBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		// set to show
		parseIntent(intent)

		binding.video.setControllerVisibilityListener(this)
		binding.video.requestFocus()
		binding.error.setOnClickListener { onErrorViewClick() }

		addMenuProvider(this)

		if (settingsRepository.pictureInPictureOnBack) {
			onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() {
					MultiWindowHelper.enterPictureInPictureMode(this@AbstractPlayerActivity)
				}
			})
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)

		setIntent(intent)

		// called when coming back from picture in picture mode
		parseIntent(intent)
	}

	private fun onErrorViewClick() {
		hideError()

		launchOnResumed {
			player?.recreate()
		}
	}

	override fun onStart() {
		super.onStart()

		if (isInsideMultiWindow(this)) {
			resumeActivity()
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	override fun onResume() {
		super.onResume()

		if (!isInsideMultiWindow(this)) {
			resumeActivity()
		}

		requestedOrientation = viewModel.screenOrientation
	}

	override fun onPause() {
		super.onPause()

		if (!isInsideMultiWindow(this)) {
			pauseActivity()
		}
	}

	override fun onStop() {
		super.onStop()

		pauseActivity()
	}

	override fun onPictureInPictureModeChanged(
		isInPictureInPictureMode: Boolean,
		newConfig: Configuration
	) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

		handlePictureInPictureModeChanged(isInPictureInPictureMode)
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.activity_abstract_player, menu)

		if (!supportsPictureInPictureMode(this)) {
			menu.removeItem(R.id.menu_pip)
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.menu_share -> {
				onShareMenuItemClicked()
				true
			}

			R.id.menu_play_in_background -> {
				binder!!.movePlaybackToBackground()
				finish()
				true
			}

			R.id.menu_pip -> {
				MultiWindowHelper.enterPictureInPictureMode(this)
				true
			}

			R.id.sleep_timer -> {
				showSleepTimerBottomSheet()
				true
			}

			android.R.id.home -> {
				finish()
				true
			}

			else -> false
		}
	}

	override fun onVisibilityChanged(visibility: Int) {
		if (binding.video.isControllerFullyVisible) {
			showSystemUi()
		} else {
			hideSystemUi()
		}
	}

	override fun onTimerAlmostEnded() {
		showSleepTimerBottomSheet()
	}

	override fun onTimerEnded() {
		showSleepTimerBottomSheet()
	}

	abstract fun onShareMenuItemClicked()

	abstract suspend fun getVideoInfoFromIntent(intent: Intent): VideoInfo

	private fun parseIntent(intent: Intent) {
		if (player == null) {
			// intent will be parsed later when player service is connected
			return
		}

		loadVideoFromIntent(intent)
	}

	private fun loadVideoFromIntent(intent: Intent) {
		launchOnCreated {
			load(getVideoInfoFromIntent(intent))
		}
	}

	private suspend fun load(videoInfo: VideoInfo) {
		player!!.load(videoInfo)
		player!!.resume()

		binder!!.movePlaybackToForeground()

		val isInPipMode = MultiWindowHelper.isInPictureInPictureMode(this)
		handlePictureInPictureModeChanged(isInPipMode)
	}

	private fun onVideoError(messageResourceId: Int?) {
		if (messageResourceId == null || messageResourceId == -1) {
			hideError()
		} else {
			showError(messageResourceId)
		}
	}

	private fun pauseActivity() {
		if (!powerManager.isInteractive) {
			// resume playback in background, when screen turned off
			binder!!.movePlaybackToBackground()
		}

		try {
			unbindService(backgroundPlayerServiceConnection)
		} catch (ignored: IllegalArgumentException) {
		}
	}

	private fun resumeActivity() {
		binder?.movePlaybackToForeground()

		hideError()

		windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())

		bind(this, backgroundPlayerServiceConnection)
	}

	private fun handlePictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
		binding.video.useController = !isInPictureInPictureMode

		if (!isInPictureInPictureMode) {
			binding.video.showController()
		} else {
			hideSystemUi()
		}
	}

	private fun showError(messageResId: Int) {
		Timber.e(getString(messageResId))

		showSystemUi()

		binding.video.controllerHideOnTouch = false
		binding.error.setText(messageResId)
		binding.error.visibility = View.VISIBLE
	}

	private fun hideError() {
		binding.video.controllerHideOnTouch = true
		binding.error.visibility = View.GONE
	}

	private fun showSystemUi() {
		supportActionBar?.show()
		binding.overlay.isVisible = shouldShowOverlay
	}

	private fun hideSystemUi() {
		supportActionBar?.hide()
		binding.overlay.isVisible = false

		windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())

		// We have to call this here instead of in resumeActivity.
		// Otherwise status bar will get stuck on some devices (see issue #313)
		// when pulled down manually.
		windowInsetsControllerCompat.systemBarsBehavior =
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
	}

	private fun showSleepTimerBottomSheet() {
		if (supportFragmentManager.isDestroyed || isInPictureInPictureMode) {
			return
		}

		val existingBottomSheet =
			supportFragmentManager.findFragmentByTag(SleepTimerBottomSheet::class.java.name)

		if (existingBottomSheet == null) {
			SleepTimerBottomSheet().show(
				supportFragmentManager,
				SleepTimerBottomSheet::class.java.name
			)
		} else {
			(existingBottomSheet as SleepTimerBottomSheet).expand()
		}
	}
}
