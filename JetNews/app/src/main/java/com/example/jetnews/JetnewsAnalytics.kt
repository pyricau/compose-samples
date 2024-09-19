package com.example.jetnews

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import java.util.LinkedList


class JetnewsAnalytics(navController: NavHostController) {

    class NavLog(
        val route: String?,
        metadata: List<Long>
    ) {
        private val metadata = LinkedList(metadata)
    }

    val navs = mutableListOf<NavLog>()

    init {
        navController.addOnDestinationChangedListener { navController: NavController, navDestination: NavDestination, bundle: Bundle? ->
            val someMetadata = LongArray(100_000)

            navs += NavLog(navDestination.route, someMetadata.toList())
        }
    }
}