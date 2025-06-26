package de.christinecoenen.code.zapp.app.personal.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekItemViewHolder
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekShowListItemListener
import de.christinecoenen.code.zapp.databinding.MediathekListFragmentItemBinding
import de.christinecoenen.code.zapp.models.shows.MediathekShow
import de.christinecoenen.code.zapp.utils.view.MediathekShowDiffUtilCallback
import kotlinx.coroutines.launch

class MediathekShowListAdapter(
	private val scope: LifecycleCoroutineScope,
	private val listener: MediathekShowListItemListener? = null
) : RecyclerView.Adapter<MediathekItemViewHolder>() {

	private var persistedShows = mutableListOf<MediathekShow>()

	fun setShows(shows: List<MediathekShow>) {
		val diffCallback = MediathekShowDiffUtilCallback(persistedShows, shows)
		val diffResult = DiffUtil.calculateDiff(diffCallback)

		this.persistedShows.clear()
		this.persistedShows.addAll(shows)

		diffResult.dispatchUpdatesTo(this)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediathekItemViewHolder {
		val layoutInflater = LayoutInflater.from(parent.context)
		val binding = MediathekListFragmentItemBinding.inflate(layoutInflater, parent, false)
		val holder = MediathekItemViewHolder(binding, false, scope)

		binding.root.setOnClickListener {
			listener?.onShowClicked(persistedShows[holder.bindingAdapterPosition])
		}

		binding.root.setOnLongClickListener {
			listener?.onShowLongClicked(
				persistedShows[holder.bindingAdapterPosition],
				binding.root
			)
			true
		}

		return holder
	}

	override fun onBindViewHolder(holder: MediathekItemViewHolder, position: Int) {
		scope.launch {
			holder.setShow(persistedShows[holder.bindingAdapterPosition])
		}
	}

	override fun onViewRecycled(holder: MediathekItemViewHolder) {
		super.onViewRecycled(holder)
		holder.recycle()
	}

	override fun getItemCount() = persistedShows.size
}
