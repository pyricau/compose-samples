package com.example.jetnews.utils

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController

class JetnewsNavAnalytics(navController: NavHostController) {

    private val navs = mutableListOf<Pair<NavDestination, Bundle?>>()

    init {
        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            navs += navDestination to bundle
        }
    }

    // TODO do something with analytics.
}
