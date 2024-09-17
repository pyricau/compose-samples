package com.example.jetnews

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController

class JetnewsAnalytics(navController: NavHostController) {

    val navs = mutableListOf<Pair<NavDestination, Bundle?>>()

    init {
        navController.addOnDestinationChangedListener { navController: NavController, navDestination: NavDestination, bundle: Bundle? ->
            navs += navDestination to bundle
        }
    }
}