package de.christinecoenen.code.zapp.app.search

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import de.christinecoenen.code.zapp.app.mediathek.api.IMediathekApiService
import de.christinecoenen.code.zapp.app.mediathek.api.MediathekPagingSource
import de.christinecoenen.code.zapp.app.mediathek.api.request.MediathekChannel
import de.christinecoenen.code.zapp.app.mediathek.api.request.QueryRequest
import de.christinecoenen.code.zapp.app.mediathek.api.result.QueryInfoResult
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.UiModel
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import de.christinecoenen.code.zapp.models.search.Comparison
import de.christinecoenen.code.zapp.models.search.DurationQuery
import de.christinecoenen.code.zapp.models.search.DurationQuerySet
import de.christinecoenen.code.zapp.models.shows.MediathekShow
import de.christinecoenen.code.zapp.models.shows.SortableMediathekShow
import de.christinecoenen.code.zapp.repositories.MediathekRepository
import de.christinecoenen.code.zapp.repositories.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val mediathekRepository: MediathekRepository,
	private val settingsRepository: SettingsRepository,
	private val mediathekApi: IMediathekApiService
) : ViewModel() {

	companion object {
		private const val ITEM_COUNT_PER_PAGE = 30
		private const val LAST_QUERIES_COUNT = 4
	}

	enum class SeachState {
		None,
		Query,
		Results
	}

	private val pagingConfig = PagingConfig(
		pageSize = ITEM_COUNT_PER_PAGE,
		enablePlaceholders = false
	)

	private val _searchQuery = MutableStateFlow("")
	val searchQuery = _searchQuery.asStateFlow()

	private val lastWord =
		_searchQuery.map { query -> query.split("""\s+""".toRegex()).last() }

	private val _channels = MutableStateFlow(emptySet<MediathekChannel>())
	val channels = _channels.asStateFlow()

	private val _durationQueries = MutableStateFlow(DurationQuerySet())
	val durationQuerySet = _durationQueries.asStateFlow()

	private val _submittedSearchQuery = MutableStateFlow("")
	val submittedSearchQuery = _submittedSearchQuery.asStateFlow()

	private val _submittedChannels = MutableStateFlow(emptySet<MediathekChannel>())
	val submittedChannels = _submittedChannels.asStateFlow()
	private val _submittedDurationQueries = MutableStateFlow(DurationQuerySet())
	val submittedDurationQuerySet = _submittedDurationQueries.asStateFlow()

	private val _searchState = MutableStateFlow(SeachState.None)
	val searchState = _searchState.asStateFlow()

	val channelSuggestions = combine(lastWord, _channels) { lastWord, channels ->
		if (lastWord.isEmpty()) {
			return@combine emptySet<MediathekChannel>()
		}

		val remainingChannels = MediathekChannel.entries.toSet().minus(channels)
		remainingChannels.filter { channel ->
			channel.apiId.contains(lastWord, true)
		}
	}

	val durationSuggestionSet = lastWord
		.map {
			val numeric = it.toIntOrNull()
			if (numeric == null || numeric <= 0 || it.startsWith("0"))
				DurationQuerySet()
			else
				DurationQuerySet(
					DurationQuery(Comparison.GreaterThan, numeric),
					DurationQuery(Comparison.LesserThan, numeric)
				)
		}

	val filterCount = combine(
		_channels,
		_durationQueries,
	) { channels, durationSet -> channels.size + durationSet.size }

	val suggestionCount = combine(
		channelSuggestions,
		durationSuggestionSet
	) { channels, durationSet -> channels.size + durationSet.size }

	val localSearchSuggestions = _searchQuery
		.debounce(100)
		.flatMapLatest { query ->
			if (query.isEmpty()) {
				flowOf(PagingData.empty())
			} else {
				Pager(pagingConfig) { searchRepository.getLocalSearchSuggestions(query) }.flow
			}
		}
		.cachedIn(viewModelScope)

	val lastQueries = _searchQuery
		.debounce(100)
		.flatMapLatest { query ->
			Pager(pagingConfig) { searchRepository.getLastQueries(query, LAST_QUERIES_COUNT) }.flow
		}
		.cachedIn(viewModelScope)

	val localShowsResult =
		combine(
			_submittedSearchQuery,
			_submittedChannels,
			_submittedDurationQueries
		) { query, _, _ -> query }
			.flatMapLatest { query ->
				if (query.isEmpty()) {
					flowOf(PagingData.empty())
				} else {
					Pager(pagingConfig) {
						mediathekRepository.getPersonalShows(
							query,
							_channels.value,
							_durationQueries.value.minDurationSeconds,
							_durationQueries.value.maxDurationSeconds
						)
					}.flow
				}
			}
			.map<PagingData<SortableMediathekShow>, PagingData<UiModel>> { pagingData ->
				pagingData.map { show ->
					UiModel.MediathekShowModel(show.mediathekShow, show.sortDate)
				}
			}
			.cachedIn(viewModelScope)

	private val _mediathekResultInfo = MutableStateFlow<QueryInfoResult?>(null)
	val mediathekResultInfo = _mediathekResultInfo.asLiveData()

	val mediathekResult = combine(
		_submittedSearchQuery,
		_submittedChannels,
		_submittedDurationQueries
	) { query, channels, durations ->
		val queryRequest = QueryRequest().apply {
			size = ITEM_COUNT_PER_PAGE
			minDurationSeconds = durations.minDurationSeconds ?: 0
			maxDurationSeconds = durations.maxDurationSeconds
			setQueryString(query)
			setChannels(channels.toList())
		}

		Pager(pagingConfig) {
			MediathekPagingSource(mediathekApi, queryRequest, _mediathekResultInfo)
		}
	}
		.flatMapLatest { it.flow }
		.mapLatest<PagingData<MediathekShow>, PagingData<UiModel>> { pagingData ->
			pagingData.map { show ->
				UiModel.MediathekShowModel(
					show,
					DateTime(show.timestamp.toLong() * DateUtils.SECOND_IN_MILLIS)
				)
			}
		}
		.cachedIn(viewModelScope)

	fun addChannel(channel: MediathekChannel) {
		_channels.tryEmit(_channels.value.plus(channel))

		// remove channel (part) from end of query
		_searchQuery.tryEmit(_searchQuery.value.replace("\\S+$".toRegex(), ""))
	}

	fun removeChannel(channel: MediathekChannel) {
		_channels.tryEmit(_channels.value.minus(channel))
	}

	fun addOrReplaceDurationQuery(durationQuery: DurationQuery) {
		_durationQueries.tryEmit(_durationQueries.value.addOrReplace(durationQuery))

		// remove duration query (part) from end of query
		_searchQuery.tryEmit(_searchQuery.value.replace("\\d+$".toRegex(), ""))
	}

	fun removeDurationQuery(durationQuery: DurationQuery) {
		_durationQueries.tryEmit(_durationQueries.value.remove(durationQuery))
	}

	fun setSearchQuery(query: String?) {
		if (_searchState.value != SeachState.Query) {
			Timber.w("setting search query is only allowed in query mode")
			return
		}

		_searchQuery.tryEmit(query ?: "")
	}

	fun submit() {
		exitToResults()

		_submittedSearchQuery.tryEmit(_searchQuery.value)
		_submittedChannels.tryEmit(_channels.value)
		_submittedDurationQueries.tryEmit(_durationQueries.value)

		if (settingsRepository.searchHistory) {
			viewModelScope.launch {
				searchRepository.saveQuery(_searchQuery.value)
			}
		}
	}

	fun deleteSavedSearchQuery(searchQuery: String) {
		viewModelScope.launch {
			searchRepository.deleteQuery(searchQuery)
		}
	}

	fun deleteAllSavedSearchQueries() {
		viewModelScope.launch {
			searchRepository.deleteAllQueries()
		}
	}

	fun enterLastSearch() {
		_searchState.tryEmit(SeachState.Query)
		_searchQuery.tryEmit(_submittedSearchQuery.value)
		_channels.tryEmit(_submittedChannels.value)
		_durationQueries.tryEmit(_durationQueries.value)
	}

	fun exitToNone() {
		_searchState.tryEmit(SeachState.None)
		resetData()
	}

	/**
	 * Show last results screen without updating search values.
	 */
	fun exitToResults() {
		_searchState.tryEmit(SeachState.Results)
	}

	private fun resetData() {
		_searchQuery.tryEmit("")
		_channels.tryEmit(emptySet())
		_durationQueries.tryEmit(DurationQuerySet())
		_submittedSearchQuery.tryEmit("")
		_submittedChannels.tryEmit(emptySet())
		_submittedDurationQueries.tryEmit(DurationQuerySet())
	}
}
