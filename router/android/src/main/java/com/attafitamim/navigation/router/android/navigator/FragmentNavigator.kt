package com.attafitamim.navigation.router.android.navigator

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.attafitamim.navigation.router.android.handlers.FragmentTransactionProcessor
import com.attafitamim.navigation.router.android.screens.AndroidScreen
import com.attafitamim.navigation.router.core.screens.platform.ScreenAdapter

open class FragmentNavigator @JvmOverloads constructor(
    fragment: Fragment,
    containerId: Int,
    screenAdapter: ScreenAdapter<AndroidScreen>,
    fragmentManager: FragmentManager = fragment.childFragmentManager,
    fragmentFactory: FragmentFactory = fragmentManager.fragmentFactory,
    lifecycleOwner: LifecycleOwner = fragment,
    fragmentTransactionProcessor: FragmentTransactionProcessor? = null,
    keepAfterLastFragment: Boolean = false,
    private val performExit: () -> Unit = fragment.requireActivity()::onBackPressed
) : BaseNavigator(
    fragment.requireActivity(),
    containerId,
    screenAdapter,
    fragmentManager,
    fragmentFactory,
    lifecycleOwner,
    fragmentTransactionProcessor,
    keepAfterLastFragment
) {

    override fun exitNavigator() {
        performExit.invoke()
    }
}
