package de.christinecoenen.code.zapp.app.mediathek.ui.detail.player

import android.content.Intent
import de.christinecoenen.code.zapp.app.player.AbstractPlayerActivity
import de.christinecoenen.code.zapp.app.player.VideoInfo
import de.christinecoenen.code.zapp.app.player.VideoInfo.Companion.fromShow
import de.christinecoenen.code.zapp.models.shows.PersistedMediathekShow
import de.christinecoenen.code.zapp.models.shows.Quality
import de.christinecoenen.code.zapp.repositories.MediathekRepository
import de.christinecoenen.code.zapp.utils.system.IntentHelper.openUrl
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

class MediathekPlayerActivity : AbstractPlayerActivity() {

	private val mediathekRepository: MediathekRepository by inject()

	private var persistedShow: PersistedMediathekShow? = null

	override val shouldShowOverlay = false


	private fun onShowLoaded(persistedMediathekShow: PersistedMediathekShow) {
		persistedShow = persistedMediathekShow

		if (supportActionBar != null) {
			title = persistedMediathekShow.mediathekShow.topic
			supportActionBar!!.subtitle = persistedMediathekShow.mediathekShow.title
		}
	}

	override fun onShareMenuItemClicked() {
		openUrl(this, persistedShow!!.mediathekShow.videoUrl)
	}

	override suspend fun getVideoInfoFromIntent(intent: Intent): VideoInfo {
		val persistedShowId = intent.getIntExtra("persisted_show_id", 0)

		val persistedMediathekShow = mediathekRepository
			.getPersistedShow(persistedShowId)
			.first()

		onShowLoaded(persistedMediathekShow)

		return fromShow(persistedMediathekShow)
	}
}
