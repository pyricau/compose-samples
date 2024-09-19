/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetnews

import android.os.Debug
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.repeatingAndroidInProcessScenario
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import shark.AndroidReferenceMatchers
import shark.AndroidReferenceReaderFactory
import shark.GrowingObjectNodes
import shark.HeapDiff
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File

@RunWith(AndroidJUnit4::class)
class JetnewsTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Using targetContext as the Context of the instrumentation code
        composeTestRule.launchJetNewsApp(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun app_launches() {
        composeTestRule.onNodeWithText("Top stories for you").assertExists()
    }

    @Test
    fun app_opensArticle() {

        println(composeTestRule.onRoot().printToString())
        composeTestRule.onAllNodes(hasText("Manuel Vivo", substring = true))[0].performClick()

        println(composeTestRule.onRoot().printToString())
        try {
            composeTestRule.onAllNodes(hasText("3 min read", substring = true))[0].assertExists()
        } catch (e: AssertionError) {
            println(composeTestRule.onRoot().printToString())
            throw e
        }
    }

    @Test
    fun app_opensInterests() {
        composeTestRule.onNodeWithContentDescription(
            label = "Open navigation drawer",
            useUnmergedTree = true
        ).performClick()
        composeTestRule.onNodeWithText("Interests").performClick()
        composeTestRule.onNodeWithText("Topics").assertExists()
    }

    @Test
    fun baguette() {
        repeat(100) {
            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Interests").performClick()
            composeTestRule.onNodeWithText("Topics").assertExists()

            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Home").performClick()
            composeTestRule.onNodeWithText("Top stories for you").assertExists()
        }
    }

    @Test
    fun baguette2() {
        val heapDumps = (1..2).map {
            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Interests").performClick()
            composeTestRule.onNodeWithText("Topics").assertExists()

            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Home").performClick()
            composeTestRule.onNodeWithText("Top stories for you").assertExists()

            File.createTempFile("dump", ".hprof").apply {
                Debug.dumpHprofData(this.absolutePath)
            }
        }

        val objectCounts = heapDumps.map { heapDump ->
            heapDump.openHeapGraph().use { graph ->
                graph.objectCount
            }
        }
        Assert.assertEquals(objectCounts[0], objectCounts[1])
    }

    @Test
    fun baguette3() {
        val heapDumps = (1..2).map {
            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Interests").performClick()
            composeTestRule.onNodeWithText("Topics").assertExists()

            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Home").performClick()
            composeTestRule.onNodeWithText("Top stories for you").assertExists()

            File.createTempFile("dump", ".hprof").apply {
                Debug.dumpHprofData(this.absolutePath)
            }
        }

        val referenceReaderFactory =
            AndroidReferenceReaderFactory(AndroidReferenceMatchers.appDefaults)
        val reachableObjects = heapDumps.map {
            it.openHeapGraph().use { graph ->
                val referenceReader = referenceReaderFactory.createFor(graph)
                val queue = mutableListOf<Long>()
                queue.addAll(graph.gcRoots.map { it.id })
                val visited = mutableMapOf<Long, String>()
                while (queue.isNotEmpty()) {
                    val objectId = queue.removeFirst()
                    if (objectId !in visited) {
                        visited += objectId to graph.findObjectById(objectId).toString()
                        val refs = referenceReader.read(graph.findObjectById(objectId))
                        queue.addAll(refs.map { it.valueObjectId }.toList())
                    }
                }
                visited
            }
        }



        if (reachableObjects[1].size != reachableObjects[0].size) {
            val remaining = reachableObjects[1].filterKeys { it !in reachableObjects[0] }
            remaining.forEach { println("BAGUETTE found ${it.value}") }
            throw AssertionError("Expected ${reachableObjects[0].size} but was ${reachableObjects[1].size} ")
        }
    }

    @Test
    fun drawer_nav_does_not_grow_heap() {
        val detector = HeapDiff.repeatingAndroidInProcessScenario()

        val heapDiff = detector.findRepeatedlyGrowingObjects(
            maxHeapDumps = 5,
            scenarioLoopsPerDump = 10
        ) {
            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Interests").performClick()
            composeTestRule.onNodeWithText("Topics").assertExists()

            composeTestRule.onNodeWithContentDescription(
                label = "Open navigation drawer",
                useUnmergedTree = true
            ).performClick()
            composeTestRule.onNodeWithText("Home").performClick()
            composeTestRule.onNodeWithText("Top stories for you").assertExists()
        }
        Assert.assertEquals(emptyList<GrowingObjectNodes>(), heapDiff.growingObjects)
    }
}
