package com.attafitamim.navigation.router.android.navigator

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import androidx.fragment.app.*
import com.attafitamim.navigation.router.android.screens.*
import com.attafitamim.navigation.router.core.commands.Command
import com.attafitamim.navigation.router.core.handlers.ScreenExitHandler
import com.attafitamim.navigation.router.core.screens.platform.ScreenAdapter
import com.attafitamim.navigation.router.core.navigator.Navigator
import com.attafitamim.navigation.router.core.screens.Screen
import java.util.LinkedHashMap

abstract class BaseNavigator : Navigator {

    protected abstract val activity: FragmentActivity
    protected abstract val containerId: Int
    protected abstract val screenAdapter: ScreenAdapter<AndroidScreen>
    protected abstract val fragmentManager: FragmentManager
    protected abstract val fragmentFactory: FragmentFactory

    protected open var screenExitHandler: ScreenExitHandler? = null

    protected open val screenHistory = LinkedHashMap<String, Screen>()

    protected open val currentVisibleFragment get() =
        fragmentManager.fragments.lastOrNull()

    override val currentVisibleScreen: Screen? get() =
        screenHistory[currentVisibleFragment?.tag]

    protected abstract fun exitNavigator()

    override fun applyCommands(commands: Array<out Command>) {
        fragmentManager.executePendingTransactions()

        for (command in commands) {
            try {
                applyCommand(command)
            } catch (e: RuntimeException) {
                errorOnApplyCommand(command, e)
            }
        }
    }

    override fun setScreenExitCallbackHandler(handler: ScreenExitHandler) {
        this.screenExitHandler = handler
    }

    override fun removeScreenExitCallbackHandler() {
        this.screenExitHandler = null
    }

    /**
     * Perform transition described by the navigation command
     *
     * @param command the navigation command to apply
     */
    protected open fun applyCommand(command: Command) {
        when (command) {
            is Command.Forward -> forward(command.screen)
            is Command.Replace -> replace(command.screen)
            is Command.BackTo -> backTo(command.screen)
            is Command.Back -> back()
        }
    }

    protected open fun forward(screen: Screen) {
        when (val androidScreen = screenAdapter.createPlatformScreen(screen)) {
            is AndroidScreen.Activity -> forwardActivity(screen, androidScreen)
            is AndroidScreen.Fragment -> forwardFragment(screen, androidScreen)
            is AndroidScreen.Dialog -> forwardDialog(screen, androidScreen)
        }
    }

    protected open fun forwardActivity(screen: Screen, androidScreen: AndroidScreen.Activity) {
        checkAndStartActivity(screen, androidScreen)
    }

    protected open fun forwardFragment(screen: Screen, androidScreen: AndroidScreen.Fragment) {
        commitNewFragmentScreen(screen, androidScreen, true)
    }

    protected open fun forwardDialog(screen: Screen, androidScreen: AndroidScreen.Dialog) {
        openNewDialogScreen(screen, androidScreen)
    }

    protected open fun replace(screen: Screen) {
        when (val androidScreen = screenAdapter.createPlatformScreen(screen)) {
            is AndroidScreen.Activity -> replaceActivity(screen, androidScreen)
            is AndroidScreen.Fragment -> replaceFragment(screen, androidScreen)
            is AndroidScreen.Dialog -> replaceDialog(screen, androidScreen)
        }
    }

    protected open fun replaceActivity(screen: Screen, androidScreen: AndroidScreen.Activity) {
        checkAndStartActivity(screen, androidScreen)
        exitNavigator()
    }

    protected open fun replaceFragment(screen: Screen, androidScreen: AndroidScreen.Fragment) {
        if (screenHistory.isNotEmpty()) {
            fragmentManager.popBackStack()
            commitNewFragmentScreen(screen, androidScreen, true)
        } else {
            commitNewFragmentScreen(screen, androidScreen, false)
        }
    }

    protected open fun replaceDialog(screen: Screen, androidScreen: AndroidScreen.Dialog) {
        val visibleFragment = currentVisibleFragment
        if (visibleFragment is DialogFragment) {
            visibleFragment.dismiss()
        }

        openNewDialogScreen(screen, androidScreen)
    }

    protected open fun back() {
        val visibleScreen = currentVisibleScreen
        val canBackPress = visibleScreen == null || screenExitHandler?.canExitScreen(visibleScreen)
                ?: true

        if (canBackPress) performBack()
    }

    protected open fun performBack() {
        val visibleScreen = currentVisibleScreen
        if (visibleScreen != null && screenHistory.size > 1) removeScreen(visibleScreen)
        else exitNavigator()
    }

    protected open fun resetScreen(screen: Screen) {
        removeScreen(screen)
        screenHistory[screen.key] = screen
    }


    protected open fun removeScreen(screen: Screen) {
        val fragment = fragmentManager.findFragmentByTag(screen.key) ?: return

        when {
            fragment is DialogFragment -> {
                fragment.dismiss()
                screenHistory.remove(screen.key)
            }

            currentVisibleFragment?.tag == screen.key -> {
                fragmentManager.popBackStack()
                screenHistory.remove(screen.key)
            }

            screenHistory.containsKey(screen.key) -> {
                backTo(screen)
                fragmentManager.popBackStack()
                screenHistory.remove(screen.key)
            }
        }
    }

    protected open fun commitNewFragmentScreen(
        screen: Screen,
        fragmentScreen: AndroidScreen.Fragment,
        addToBackStack: Boolean
    ) {
        resetScreen(screen)
        val fragment = fragmentScreen.createFragment(fragmentFactory)
        val transaction = fragmentManager.beginTransaction()
        transaction.setReorderingAllowed(true)
        setupFragmentTransaction(
            fragmentScreen,
            transaction,
            fragmentManager.findFragmentById(containerId),
            fragment
        )
        if (fragmentScreen.clearContainer) {
            transaction.replace(containerId, fragment, screen.key)
        } else {
            transaction.add(containerId, fragment, screen.key)
        }

        if (addToBackStack) transaction.addToBackStack(screen.key)

        transaction.commit()
    }

    protected open fun openNewDialogScreen(screen: Screen, dialogScreen: AndroidScreen.Dialog) {
        resetScreen(screen)
        val dialog = dialogScreen.createDialog(fragmentFactory)
        dialog.showNow(fragmentManager, screen.key)

        dialog.dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                back()
                true
            } else false
        }
    }

    /**
     * Performs [Command.BackTo] command transition
     */
    protected open fun backTo(screen: Screen?) {
        val androidScreen = screen?.let(screenAdapter::createPlatformScreen)
            ?: return backToRoot()

        if (screenHistory.containsKey(screen.key)) {
            fragmentManager.popBackStack(screen.key, 0)

            while (screenHistory.values.lastOrNull() != screen) {
                screenHistory.remove(screenHistory.values.last().key)
            }
        } else {
            backToUnexisting(androidScreen)
        }
    }

    private fun backToRoot() {
        screenHistory.clear()
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /**
     * Override this method to setup fragment transaction [FragmentTransaction].
     * For example: setCustomAnimations(...), addSharedElement(...) or setReorderingAllowed(...)
     *
     * @param fragmentTransaction fragment transaction
     * @param currentFragment     current fragment in container
     *                            (for [Replace] command it will be screen previous in new chain, NOT replaced screen)
     * @param nextFragment        next screen fragment
     */
    protected open fun setupFragmentTransaction(
        screen: AndroidScreen.Fragment,
        fragmentTransaction: FragmentTransaction,
        currentFragment: Fragment?,
        nextFragment: Fragment
    ) {
        // Do nothing by default
    }

    private fun checkAndStartActivity(screen: Screen, activityScreen: AndroidScreen.Activity) {
        // Check if we can start activity
        val activityIntent = activityScreen.createIntent(activity)
        try {
            activity.startActivity(activityIntent, activityScreen.startActivityOptions)
        } catch (e: ActivityNotFoundException) {
            unexistingActivity(screen, activityScreen, activityIntent)
        }
    }

    /**
     * Called when there is no activity to open `tag`.
     *
     * @param screen         screen
     * @param activityIntent intent passed to start Activity for the `tag`
     */
    protected open fun unexistingActivity(
        screen: Screen,
        activityScreen: AndroidScreen.Activity,
        activityIntent: Intent
    ) {
        // Do nothing by default
    }

    /**
     * Called when we tried to fragmentBack to some specific screen (via [Command.BackTo] command),
     * but didn't found it.
     *
     * @param screen screen
     */
    protected open fun backToUnexisting(screen: AndroidScreen) {
        backToRoot()
    }

    /**
     * Override this method if you want to handle apply command error.
     *
     * @param command command
     * @param error   error
     */
    protected open fun errorOnApplyCommand(
        command: Command,
        error: RuntimeException
    ) {
        throw error
    }
}
