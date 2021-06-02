package com.greencom.android.podcasts.ui.podcast

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.addRepeatingJob
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.greencom.android.podcasts.R
import com.greencom.android.podcasts.databinding.FragmentPodcastBinding
import com.greencom.android.podcasts.ui.dialogs.UnsubscribeDialog
import com.greencom.android.podcasts.ui.podcast.PodcastViewModel.PodcastEvent
import com.greencom.android.podcasts.ui.podcast.PodcastViewModel.PodcastState
import com.greencom.android.podcasts.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

private const val FAB_TOP_THRESHOLD = 10
private const val SMOOTH_SCROLL_THRESHOLD = 100

@AndroidEntryPoint
class PodcastFragment : Fragment(), UnsubscribeDialog.UnsubscribeDialogListener {

    /** Nullable View binding. Only for inflating and cleaning. Use [binding] instead. */
    private var _binding: FragmentPodcastBinding? = null
    /** Non-null View binding. */
    private val binding get() = _binding!!

    /** PodcastViewModel. */
    private val viewModel: PodcastViewModel by viewModels()

    /** Navigation Safe Args. */
    private val args: PodcastFragmentArgs by navArgs()

    private var podcastId = ""

    /** Is the app bar expanded. `false` means it is collapsed. */
    private val isAppBarExpanded = MutableStateFlow(true)

    /** RecyclerView adapter. */
    private val adapter: PodcastWithEpisodesAdapter by lazy {
        PodcastWithEpisodesAdapter(
            viewModel.sortOrder,
            viewModel::updateSubscription,
            viewModel::changeSortOrder
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // View binding setup.
        _binding = FragmentPodcastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition(100L, TimeUnit.MILLISECONDS)

        // Restore instance state.
        savedInstanceState?.apply {
            binding.appBarLayout.setExpanded(getBoolean(STATE_IS_APP_BAR_EXPANDED), false)
            binding.scrollToTop.apply { if (getBoolean(STATE_IS_SCROLL_TO_TOP_SHOWN)) show() else hide() }
        }

        // Get the podcast ID from the navigation arguments.
        podcastId = args.podcastId
        viewModel.podcastId = podcastId

        // Load a podcast with episodes.
        viewModel.getPodcastWithEpisodes()
        viewModel.fetchEpisodes()

        setupAppBar()
        setupRecyclerView()
        setupViews()

        setObservers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.apply {
            putBoolean(STATE_IS_APP_BAR_EXPANDED, isAppBarExpanded.value)
            putBoolean(STATE_IS_SCROLL_TO_TOP_SHOWN, binding.scrollToTop.isOrWillBeShown)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel adapter coroutine scope in onDetachedFromRecyclerView().
        adapter.onDetachedFromRecyclerView(binding.list)
        // Clear View binding.
        _binding = null
    }

    // Unsubscribe from the podcast if the user confirms in the UnsubscribeDialog.
    override fun onUnsubscribeClick(podcastId: String) {
        viewModel.unsubscribe(podcastId)
    }

    /** App bar setup. */
    private fun setupAppBar() {
        // Disable AppBarLayout dragging behavior.
        if (binding.appBarLayout.layoutParams != null) {
            val appBarParams = binding.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
            val appBarBehavior = AppBarLayout.Behavior()
            appBarBehavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
                override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                    return false
                }
            })
            appBarParams.behavior = appBarBehavior
        }

        // Track app bar state.
        binding.appBarLayout.addOnOffsetChangedListener(object : AppBarLayoutStateChangeListener() {
            override fun onStateChanged(appBarLayout: AppBarLayout, newState: AppBarLayoutState) {
                when (newState) {
                    AppBarLayoutState.EXPANDED -> isAppBarExpanded.value = true
                    AppBarLayoutState.COLLAPSED -> isAppBarExpanded.value = false
                    else -> {  }
                }
            }
        })
    }

    /** RecyclerView setup. */
    private fun setupRecyclerView() {
        val divider = CustomDividerItemDecoration(requireContext(), true)
        divider.setDrawable(
            ResourcesCompat.getDrawable(resources, R.drawable.shape_divider, context?.theme)!!
        )

        val onScrollListener = object : RecyclerView.OnScrollListener() {
            val layoutManager = binding.list.layoutManager as LinearLayoutManager
            var totalItemCount = 0
            var firstVisibleItemPosition = 0
            var lastVisibleItemPosition = 0
            var scrollToTopInitialCheckSkipped = false

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                totalItemCount = layoutManager.itemCount
                firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                // Fetch more episodes.
                if (totalItemCount >= 10 && lastVisibleItemPosition >= totalItemCount - 10 && dy > 0) {
                    viewModel.fetchMoreEpisodes()
                }

                // Show and hide the podcast title in the app bar.
                if (firstVisibleItemPosition >= 1) {
                    binding.appBarTitle.revealImmediately()
                } else {
                    binding.appBarTitle.hideCrossfade()
                }

                // Show and hide the fab. Skip the initial check to restore instance state.
                if (scrollToTopInitialCheckSkipped) {
                    binding.scrollToTop.apply {
                        if (firstVisibleItemPosition >= FAB_TOP_THRESHOLD && dy < 0) {
                            show()
                        } else {
                            hide()
                        }
                    }
                } else {
                    scrollToTopInitialCheckSkipped = true
                }
            }
        }

        binding.list.apply {
            adapter = this@PodcastFragment.adapter
            adapter?.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            addItemDecoration(divider)
            addOnScrollListener(onScrollListener)
        }

        setupSwipeToRefresh(binding.swipeToRefresh, requireContext())
    }

    /** Fragment views setup. */
    private fun setupViews() {
        hideErrorScreen()

        // Handle toolbar back button clicks.
        binding.appBarBack.setOnClickListener { findNavController().navigateUp() }

        // Force episodes fetching on swipe-to-refresh.
        binding.swipeToRefresh.setOnRefreshListener { viewModel.fetchEpisodes(true) }

        // Scroll to top.
        binding.scrollToTop.setOnClickListener {
            // Do smooth scroll only if the user has not scrolled far enough.
            if ((binding.list.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition() <= SMOOTH_SCROLL_THRESHOLD) {
                binding.list.smoothScrollToPosition(0)
                binding.appBarLayout.setExpanded(true, true)
            } else {
                binding.list.scrollToPosition(0)
                binding.appBarLayout.setExpanded(true, true)
            }
        }

        // Fetch the podcast from the error screen.
        binding.error.tryAgain.setOnClickListener { viewModel.fetchPodcast() }
    }

    /** Set observers for ViewModel observables. */
    private fun setObservers() {
        // Observe UI states.
        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.STARTED) {
            viewModel.uiState.collectLatest { state ->
                handleUiState(state)
            }
        }

        // Observe events.
        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.STARTED) {
            viewModel.event.collect { event ->
                handleEvent(event)
            }
        }

        // Observe app bar state to run title animation.
        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.STARTED) {
            isAppBarExpanded.collectLatest {
                if (it) {
                    delay(750) // Delay animation.
                    binding.appBarTitle.isSelected = true
                } else {
                    binding.appBarTitle.isSelected = false
                }
            }
        }
    }

    /** Handle UI states. */
    private fun handleUiState(state: PodcastState) {
        binding.swipeToRefresh.isVisible = state is PodcastState.Success
        binding.error.root.isVisible = state is PodcastState.Error
        binding.loading.isVisible = state is PodcastState.Loading

        when (state) {

            // Show podcast data.
            is PodcastState.Success -> {
                binding.appBarTitle.text = state.podcastWithEpisodes.podcast.title
                adapter.submitHeaderAndList(
                    state.podcastWithEpisodes.podcast,
                    state.podcastWithEpisodes.episodes
                )
                binding.list.revealCrossfade()
                hideErrorScreen()
            }

            // Show error screen.
            is PodcastState.Error -> {
                binding.error.root.revealCrossfade()
                hideSuccessScreen()
            }

            // Make `when` expression exhaustive.
            is PodcastState.Loading -> {  }
        }
    }

    /** Handle events. */
    private fun handleEvent(event: PodcastEvent) {
        binding.error.tryAgain.isEnabled = event !is PodcastEvent.Fetching
        binding.error.progressBar.isVisible = event is PodcastEvent.Fetching

        // Change 'Try again' button text.
        if (event is PodcastEvent.Fetching) {
            binding.error.tryAgain.text = getString(R.string.loading)
        } else {
            binding.error.tryAgain.text = getString(R.string.try_again)
        }

        when (event) {

            // Show a snackbar.
            is PodcastEvent.Snackbar -> showSnackbar(binding.root, event.stringRes)

            // Show UnsubscribeDialog.
            is PodcastEvent.UnsubscribeDialog ->
                UnsubscribeDialog.show(childFragmentManager, podcastId)

            // Show Loading process.
            is PodcastEvent.Fetching -> binding.error.progressBar.revealCrossfade()

            // Show episodes fetching progress bar.
            is PodcastEvent.EpisodesFetchingStarted ->
                binding.episodesProgressBar.revealImmediately()

            // Stop episodes fetching progress bar.
            is PodcastEvent.EpisodesFetchingFinished ->
                binding.episodesProgressBar.hideCrossfade()

            // Stop episodes swipe-to-refresh indicator.
            PodcastEvent.EpisodesForcedFetchingFinished ->
                binding.swipeToRefresh.isRefreshing = false
        }
    }

    /** Set alpha of the success screen to 0. */
    private fun hideSuccessScreen() {
        binding.list.alpha = 0F
    }

    /** Set alpha of the error screen to 0. */
    private fun hideErrorScreen() {
        binding.error.root.alpha = 0F
    }

    companion object {
        // Saving instance state.
        private const val STATE_IS_APP_BAR_EXPANDED = "state_is_app_bar_expanded"
        private const val STATE_IS_SCROLL_TO_TOP_SHOWN = "state_is_scroll_to_top_shown"
    }
}