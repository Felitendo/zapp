package de.christinecoenen.code.zapp.tv.mediathek

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.mediathek.ui.list.MediathekListFragmentViewModel
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekLoadStateAdapter
import de.christinecoenen.code.zapp.app.mediathek.ui.list.filter.MediathekFilterViewModel
import de.christinecoenen.code.zapp.app.player.VideoInfo
import de.christinecoenen.code.zapp.databinding.TvFragmentMediathekListBinding
import de.christinecoenen.code.zapp.models.shows.MediathekShow
import de.christinecoenen.code.zapp.repositories.MediathekRepository
import de.christinecoenen.code.zapp.tv.player.PlayerActivity
import de.christinecoenen.code.zapp.utils.system.LifecycleOwnerHelper.launchOnCreated
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException


class MediathekListFragment : Fragment(),
	de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekShowListItemListener {

	private val mediathekRepository: MediathekRepository by inject()

	private val filterViewModel: MediathekFilterViewModel by viewModel()
	private val viewmodel: MediathekListFragmentViewModel by viewModel {
		parametersOf(filterViewModel.searchQuery)
	}
	private lateinit var adapter: MediathekItemAdapter

	private var _binding: TvFragmentMediathekListBinding? = null
	private val binding: TvFragmentMediathekListBinding
		get() = _binding!!

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = TvFragmentMediathekListBinding.inflate(inflater, container, false)

		val layoutManager = LinearLayoutManager(binding.root.context)
		binding.list.layoutManager = layoutManager

		// clear search field
		binding.deleteSearchButton.setOnClickListener { binding.search.editableText.clear() }

		// refresh list
		binding.refreshButton.setOnClickListener { adapter.refresh() }

		// search text change
		binding.search.addTextChangedListener {
			filterViewModel.setSearchQueryFilter(it?.toString())
		}

		// hack to get focus to the input field
		binding.searchWrapper.setOnFocusChangeListener { _, isFocused ->
			if (isFocused) {
				binding.search.requestFocus()
			}
		}

		adapter = MediathekItemAdapter(lifecycleScope, this)
		binding.list.adapter =
			adapter.withLoadStateFooter(MediathekLoadStateAdapter(retry = adapter::retry))

		viewLifecycleOwner.lifecycleScope.launch {
			viewmodel.pageFlow.collectLatest { pagingData ->
				adapter.submitData(pagingData)
			}
		}

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		viewLifecycleOwner.lifecycleScope.launch {
			adapter.loadStateFlow
				.drop(1)
				.map { it.refresh }
				.distinctUntilChanged()
				.collectLatest { refreshState ->
					binding.loader.isVisible = refreshState is LoadState.Loading
					binding.error.isVisible = refreshState is LoadState.Error
					updateNoShowsMessage(refreshState)

					when (refreshState) {
						is LoadState.Error -> onMediathekLoadErrorChanged(refreshState.error)
						is LoadState.NotLoading -> binding.list.scrollToPosition(0)
						is LoadState.Loading -> Unit
					}
				}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		_binding = null
	}

	override fun onShowClicked(show: MediathekShow) {
		launchOnCreated {
			saveAndOpenShow(show)
		}
	}

	private suspend fun saveAndOpenShow(show: MediathekShow) {
		val persistedShow = mediathekRepository.persistOrUpdateShow(show).first()

		val videoInfo = VideoInfo.fromShow(persistedShow)
		val intent = PlayerActivity.getStartIntent(requireContext(), videoInfo)
		startActivity(intent)
	}

	private fun updateNoShowsMessage(loadState: LoadState) {
		val isAdapterEmpty = adapter.itemCount == 0 && loadState is LoadState.NotLoading
		binding.noShows.isVisible = isAdapterEmpty
	}

	private fun onMediathekLoadErrorChanged(e: Throwable) {
		if (e is SSLHandshakeException || e is UnknownServiceException) {
			showError(R.string.error_mediathek_ssl_error)
		} else {
			showError(R.string.error_mediathek_info_not_available)
		}
	}

	private fun showError(messageResId: Int) {
		binding.errorMessage.setText(messageResId)
		binding.error.visibility = View.VISIBLE
	}
}
