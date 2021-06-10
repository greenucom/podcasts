package com.greencom.android.podcasts.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media2.session.*
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.Slider
import com.greencom.android.podcasts.R
import com.greencom.android.podcasts.databinding.ActivityMainBinding
import com.greencom.android.podcasts.player.PlayerService
import com.greencom.android.podcasts.ui.activity.ActivityFragment
import com.greencom.android.podcasts.ui.explore.ExploreFragment
import com.greencom.android.podcasts.ui.home.HomeFragment
import com.greencom.android.podcasts.utils.OnSwipeListener
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Saving instance state.
private const val STATE_PLAYER_BEHAVIOR = "state_player_behavior"

private const val DURATION_SLIDER_THUMB_ANIMATION = 120L

/**
 * MainActivity is the entry point for the app. This is where the Navigation component,
 * bottom navigation bar, and player bottom sheet are configured.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** [BottomSheetBehavior] plugin of the player bottom sheet. */
    private lateinit var playerBehavior: BottomSheetBehavior<FrameLayout>

    // TODO
    private lateinit var mediaController: MediaController
    private lateinit var mediaSessionToken: SessionToken

    // App bar colors.
    private var statusBarColor = 0
    private var navigationBarColorDefault = TypedValue()
    private var navigationBarColorChanged = TypedValue()

    // Slider thumb animator.
    private var thumbAnimator: ObjectAnimator? = null

    // TODO
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("serviceConnection: onServiceConnected() called")
            val binder = service as PlayerService.PlayerServiceBinder
            mediaSessionToken = binder.getSessionToken()

            mediaController = MediaController.Builder(this@MainActivity)
                .setSessionToken(mediaSessionToken)
                .setControllerCallback(Executors.newSingleThreadExecutor(), mediaControllerCallback)
                .build()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("serviceConnection: onServiceDisconnected() called")
        }
    }

    // TODO
    private val mediaControllerCallback = object : MediaController.ControllerCallback() {
        override fun onConnected(
            controller: MediaController,
            allowedCommands: SessionCommandGroup
        ) {
            Timber.d("mediaControllerCallback: onConnected() called")
            super.onConnected(controller, allowedCommands)
        }

        override fun onDisconnected(controller: MediaController) {
            Timber.d("mediaControllerCallback: onDisconnected() called")
            super.onDisconnected(controller)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // View binding setup.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        volumeControlStream = AudioManager.STREAM_MUSIC

        // TODO
        Intent(this, PlayerService::class.java).apply {
            action = MediaSessionService.SERVICE_INTERFACE
        }.also { intent -> bindService(intent, serviceConnection, BIND_AUTO_CREATE) }

        // Player bottom sheet behavior setup.
        playerBehavior = BottomSheetBehavior.from(binding.player.root).apply {
            setupBottomSheetBehavior()
        }

        initViews()
        setupNavigation()
        setPlayerListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaController.close()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        savedInstanceState.apply {
            playerBehavior.state = getInt(STATE_PLAYER_BEHAVIOR)
        }

        // Setup player content.
        val slideOffset = if (playerBehavior.state == BottomSheetBehavior.STATE_EXPANDED) 1F else 0F
        val bottomNavBarHeight = resources.getDimension(R.dimen.bottom_nav_bar_height)
        controlPlayerOnBottomSheetStateChanged(playerBehavior.state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            controlPlayerOnBottomSheetSlideV27(slideOffset, bottomNavBarHeight)
        } else {
            controlPlayerOnBottomSheetSlide(slideOffset, bottomNavBarHeight)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(STATE_PLAYER_BEHAVIOR, playerBehavior.state)
    }

    /** Make player closable on back pressed. */
    override fun onBackPressed() {
        if (playerBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            playerBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    /** Make player closable on outside click. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN &&
            playerBehavior.state != BottomSheetBehavior.STATE_COLLAPSED
        ) {

            // Create a new empty Rect with [0,0,0,0] values for [left,top,right,bottom].
            val outRect = Rect()
            // Assign to the outRect the values corresponding to the coordinates of the player
            // bottom sheet within the global coordinate system starting at the left top corner
            // (corresponding to the screen resolution).
            // For example, for Galaxy S7 the values of player's Rect are [0,172,1080,1920]
            // for [left,top,right,bottom].
            binding.player.root.getGlobalVisibleRect(outRect)

            // If user touches the screen outside the player, close it.
            // Return true, which means the event was handled.
            if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                playerBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return true
            }
        }
        // Return super.dispatchTouchEvent() to handle default behavior.
        return super.dispatchTouchEvent(ev)
    }

    /** Initialize views. */
    private fun initViews() {
        // Set text selected to run ellipsize animation.
        binding.player.collapsed.title.isSelected = true
        // Hide scrim background at start.
        binding.background.isVisible = false
        // Set expanded content alpha to zero.
        binding.player.expanded.root.alpha = 0F

        // Obtain app bar colors.
        statusBarColor = getColor(R.color.background_scrim)
        theme.resolveAttribute(R.attr.colorSurface, navigationBarColorDefault, true)
        theme.resolveAttribute(R.attr.colorBottomSheetBackground, navigationBarColorChanged, true)

        val thumbRadiusDefault = resources.getDimensionPixelSize(R.dimen.player_slider_thumb_default)
        val thumbRadiusIncreased = resources.getDimensionPixelSize(R.dimen.player_slider_thumb_increased)
        // OnSliderTouchListener is used for animating slider thumb radius.
        val onTouchListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                animateSliderThumb(thumbRadiusIncreased)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                animateSliderThumb(thumbRadiusDefault)
            }
        }
        binding.player.expanded.slider.addOnSliderTouchListener(onTouchListener)
    }

    /** Navigation component setup. */
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        // Use NavHostFragment.navController instead of findNavController() for now
        // because of FragmentContainerView bug.
        val navController = navHostFragment.navController
        binding.bottomNavBar.apply {
            // Associate the bottom nav bar items with navigation graph actions.
            setupWithNavController(navController)
            // Handle Navigation behavior when the bottom navigation item is reselected.
            setupOnBottomItemReselectedBehavior(navHostFragment, navController)
        }
    }

    /**
     * Handle behavior when the bottom navigation item is reselected.
     *
     * Check if the current fragment is the starting one. If not, navigate
     * to the starting one. Otherwise, prevent fragment reloading.
     *
     * The starting fragments are fragments associated with bottom navigation
     * items (tabs).
     */
    private fun BottomNavigationView.setupOnBottomItemReselectedBehavior(
        navHostFragment: NavHostFragment,
        navController: NavController,
    ) {
        setOnNavigationItemReselectedListener {
            val currentFragment = navHostFragment.childFragmentManager.fragments[0]
            val isNavigationNeeded = !currentFragment.isStarting()
            if (isNavigationNeeded) {
                navController.navigateToStartingFragment(it.title)
            }
        }
    }

    /**
     * Set up and add the callback to the player [BottomSheetBehavior] to control
     * the player UI behavior when [BottomSheetBehavior] state and slideOffset change.
     */
    private fun BottomSheetBehavior<FrameLayout>.setupBottomSheetBehavior() {

        val bottomNavBarHeight = resources.getDimension(R.dimen.bottom_nav_bar_height)

        /** BottomSheetCallback for API versions below 27. */
        class Callback : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                controlPlayerOnBottomSheetStateChanged(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                controlPlayerOnBottomSheetSlide(slideOffset, bottomNavBarHeight)
            }
        }

        /** BottomSheetCallback for API versions 27 and higher. */
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        class CallbackV27 : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                controlPlayerOnBottomSheetStateChanged(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                controlPlayerOnBottomSheetSlideV27(slideOffset, bottomNavBarHeight)
            }
        }

        // Add BottomSheetCallback depending on system version.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            addBottomSheetCallback(CallbackV27())
        } else {
            addBottomSheetCallback(Callback())
        }
    }

    /**
     * Control the state of the player content when the player [BottomSheetBehavior]
     * state change.
     */
    private fun controlPlayerOnBottomSheetStateChanged(newState: Int) {
        // Hide the scrim background when the player is collapsed.
        binding.background.isVisible = newState != BottomSheetBehavior.STATE_COLLAPSED
        // Disable the player collapsed content when the player is expanded.
        binding.player.collapsed.root.isClickable = newState == BottomSheetBehavior.STATE_COLLAPSED
        // Disable the player expanded content when the player is collapsed.
        binding.player.expanded.root.isClickable = newState == BottomSheetBehavior.STATE_EXPANDED
        binding.player.expanded.cover.isClickable = newState == BottomSheetBehavior.STATE_EXPANDED
        // Set text selected to run ellipsize animation.
        binding.player.collapsed.title.isSelected = newState != BottomSheetBehavior.STATE_EXPANDED
        binding.player.expanded.title.isSelected = newState != BottomSheetBehavior.STATE_COLLAPSED
    }

    /**
     * Animate the player content and background shadows when the player
     * [BottomSheetBehavior] slide offset change. Used on API versions below 27.
     */
    private fun controlPlayerOnBottomSheetSlide(
        slideOffset: Float,
        bottomNavBarHeight: Float
    ) {
        // Animate alpha of the player collapsed content.
        binding.player.collapsed.root.alpha = 1F - slideOffset * 10
        binding.player.collapsed.progressBar.alpha = 1F - slideOffset * 100

        // Animate alpha of the player expanded content.
        binding.player.expanded.root.alpha = (slideOffset * 1.5F) - 0.15F

        // Animate the displacement of the bottomNavBar along the y-axis.
        binding.bottomNavBar.translationY = slideOffset * bottomNavBarHeight * 10

        // Animate player shadow.
        binding.playerShadowExternal.alpha = 1F - slideOffset * 3
        binding.playerShadowInternal.alpha = 1F - slideOffset * 3

        // Animate alpha of the background behind player.
        binding.background.alpha = slideOffset

        // Animate status bar color.
        statusBarColor = ColorUtils
            .setAlphaComponent(statusBarColor, (slideOffset * 255 / 2).roundToInt())
        window.statusBarColor = statusBarColor
    }

    /**
     * Animate the player content and background shadows when the player
     * [BottomSheetBehavior] slide offset change. Used on API versions 27 and higher.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun controlPlayerOnBottomSheetSlideV27(
        slideOffset: Float,
        bottomNavBarHeight: Float
    ) {
        // Animate alpha of the player collapsed content.
        binding.player.collapsed.root.alpha = 1F - slideOffset * 10
        binding.player.collapsed.progressBar.alpha = 1F - slideOffset * 100

        // Animate alpha of the player expanded content.
        binding.player.expanded.root.alpha = (slideOffset * 1.5F) - 0.15F

        // Animate the displacement of the bottomNavBar along the y-axis.
        binding.bottomNavBar.translationY = slideOffset * bottomNavBarHeight * 10

        // Animate player shadow.
        binding.playerShadowExternal.alpha = 1F - slideOffset * 3
        binding.playerShadowInternal.alpha = 1F - slideOffset * 3

        // Animate alpha of the background behind player.
        binding.background.alpha = slideOffset

        // Animate status bar color.
        statusBarColor = ColorUtils
            .setAlphaComponent(statusBarColor, (slideOffset * 255 / 2).roundToInt())
        window.statusBarColor = statusBarColor

        // Animate navigation bar color.
        if (navigationBarColorDefault.data != navigationBarColorChanged.data) {
            if (slideOffset >= 0.1 && window.navigationBarColor != navigationBarColorChanged.data) {
                window.navigationBarColor = navigationBarColorChanged.data
            } else if (slideOffset < 0.1 && window.navigationBarColor != navigationBarColorDefault.data) {
                window.navigationBarColor = navigationBarColorDefault.data
            }
        }
    }

    /** Set listeners for the player's content. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setPlayerListeners() {

        // COLLAPSED
        // Expand the player on the frame click.
        binding.player.collapsed.root.setOnClickListener {
            expandPlayer()
        }
        // Expand the player on frame swipe.
        binding.player.collapsed.root.setOnTouchListener(object : OnSwipeListener(this) {
            override fun onSwipeUp() {
                expandPlayer()
            }
        })
        // Expand the player on play/pause button swipe.
        binding.player.collapsed.playPause.setOnTouchListener(object : OnSwipeListener(this) {
            override fun onSwipeUp() {
                expandPlayer()
            }
        })

        binding.player.collapsed.playPause.setOnClickListener {

        }


        // EXPANDED.
        binding.player.expanded.playPause.setOnClickListener {

        }

        binding.player.expanded.slider.addOnChangeListener { _, _, _ ->

        }

        binding.player.expanded.backward.setOnClickListener {

        }

        binding.player.expanded.forward.setOnClickListener {

        }

        // The expanded content of the player is not disabled at application start
        // (because of bug?), so prevent random click on the invisible podcast cover
        // by checking the state of player bottom sheet. If player is collapsed, expand it.
        binding.player.expanded.cover.setOnClickListener {
            if (playerBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                playerBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {

            }
        }

        binding.player.expanded.title.setOnClickListener {

        }
    }

    /** Expand the player if it is collapsed. */
    private fun expandPlayer() {
        if (playerBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            playerBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /**
     * Return `true` if the fragment is the starting one. Otherwise return `false`.
     *
     * The starting fragments are fragments associated with bottom navigation
     * items (tabs).
     */
    private fun Fragment.isStarting(): Boolean {
        return when (this) {
            is HomeFragment -> true
            is ExploreFragment -> true
            is ActivityFragment -> true
            else -> false
        }
    }

    /**
     * Navigate to the starting fragment associated with the reselected bottom
     * navigation item.
     *
     * @param title title of the reselected bottom navigation item (tab).
     */
    private fun NavController.navigateToStartingFragment(title: CharSequence) {
        when (title) {
            resources.getString(R.string.bottom_nav_home) -> navigate(R.id.action_global_homeFragment)
            resources.getString(R.string.bottom_nav_explore) -> navigate(R.id.action_global_exploreFragment)
            resources.getString(R.string.bottom_nav_activity) -> navigate(R.id.action_global_activityFragment)
        }
    }

    /** Animate slider thumb radius to a given value. */
    private fun animateSliderThumb(to: Int) {
        if (thumbAnimator != null) {
            thumbAnimator?.setIntValues(to)
        } else {
            thumbAnimator = ObjectAnimator.ofInt(
                binding.player.expanded.slider,
                "thumbRadius",
                to
            ).apply {
                duration = DURATION_SLIDER_THUMB_ANIMATION
                setAutoCancel(true)
            }
        }
        thumbAnimator?.start()
    }
}