package com.greencom.android.podcasts.ui.explore

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.button.MaterialButton
import com.greencom.android.podcasts.R
import com.greencom.android.podcasts.data.domain.PodcastShort
import com.greencom.android.podcasts.databinding.PodcastItemBinding
import com.greencom.android.podcasts.utils.PodcastDiffCallback

/** Adapter used for RecyclerView that represents a list of best podcasts. */
class ExplorePodcastAdapter(
    private val updateSubscription: (String, Boolean) -> Unit
) : ListAdapter<PodcastShort, ExplorePodcastViewHolder>(PodcastDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExplorePodcastViewHolder {
        return ExplorePodcastViewHolder.create(parent, updateSubscription)
    }

    override fun onBindViewHolder(holder: ExplorePodcastViewHolder, position: Int) {
        val podcast = getItem(position)
        holder.bind(podcast)
    }
}

/** ViewHolder that represents a single item in the best podcasts list. */
class ExplorePodcastViewHolder private constructor(
    private val binding: PodcastItemBinding,
    private val updateSubscription: (String, Boolean) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    // View context.
    private val context: Context = binding.root.context

    /** Bind ViewHolder with a given [PodcastShort]. */
    fun bind(podcast: PodcastShort) {
        binding.cover.load(podcast.image) {
            transformations(RoundedCornersTransformation(
                context.resources.getDimension(R.dimen.corner_radius_small)
            ))
            crossfade(true)
            placeholder(R.drawable.shape_placeholder)
            error(R.drawable.shape_placeholder)
        }
        binding.title.text = podcast.title
        binding.publisher.text = podcast.publisher
        // Remove all HTML tags from description.
        binding.description.text =
            HtmlCompat.fromHtml(podcast.description, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        // Show explicit content icon depending on `explicitContent` value.
        binding.explicitContent.isVisible = podcast.explicitContent

        // Update `Subscribe` button state.
        if (podcast.subscribed) {
            binding.subscribe.apply {
                isChecked = true
                text = context.getString(R.string.explore_subscribed)
                icon = ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_check_24,
                    context.theme
                )
            }
        } else {
            binding.subscribe.apply {
                isChecked = false
                text = context.getString(R.string.explore_subscribe)
                icon = ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.ic_add_24,
                    context.theme
                )
            }
        }

        // Update subscription to the podcast.
        binding.subscribe.setOnClickListener {
            updateSubscription(podcast.id, (it as MaterialButton).isChecked)
            // Keep the button checked until the user makes his choice in the UnsubscribeDialog.
            if (podcast.subscribed) binding.subscribe.isChecked = true
        }
    }

    companion object {
        /** Create a [ExplorePodcastViewHolder]. */
        fun create(
            parent: ViewGroup,
            updateSubscription: (String, Boolean) -> Unit
        ): ExplorePodcastViewHolder {
            val binding = PodcastItemBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)
            return ExplorePodcastViewHolder(binding, updateSubscription)
        }
    }
}