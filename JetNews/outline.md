Mention that live coding is tricky so I might need their help

Load JetNews 

Let's add analytics, create JetnewsAnalytics which takes in a NavHostController

`JetnewsAnalytics.kt`:

```kotlin
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
```

- Add it to `JetnewsApp.kt`:

```kotlin
        remember(navController) {
            JetnewsAnalytics(navController)
        }
```


Start the app.

Notice anything? No.

What do we think is going to happen as we navigate around?

Ok now let's write a UI test that navigates around.

Copy app_opensInterests then go Home and copy the check from App Launches

In `JetnewsTests.kt`


```kotlin
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
```

```bash
./gradlew app:connectedCheck "-Pandroid.testInstrumentationRunnerArguments.class=com.example.jetnews.JetnewsTests#baguette"
```

=> Profile: you can see the memory growing and app crashes => OOM

But what if I make it 10 bytes?

```
val someMetadata = LongArray(10)
```

=> it's going to take a while. Show memory profiler.

=> show how less memory slows down the app.

https://blog.p-y.wtf/freezes-anrs-check-memory-leaks

Add LeakCanary?

```groovy
    debugImplementation("com.squareup.leakcanary:leakcanary-android:3.0-alpha-8")
```

Start the app.

Doesn't find anything. LeakCanary is useless?

Explain how LeakCanary works? Talk about lifecycle and then trigger a heap dump.

What if we could look at the contents of memory, with code? Could we reuse this test?

Implement a solution with object count and just two iterations.

Show big graph picture

```kotlin
    @Test
    fun baguette() {
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
```

Show GC picture

Don't control when the GC runs, it does partial runs, tons of temporary objects created all the time.

Talk from Jesse earlier today: "GC You Later, Allocator"

```kotlin
    @Test
    fun baguette() {
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
```

VM is alive, lots of things change.

What doesn't change? The general structure of the graph. Activity has refs to views, views to bitmap, etc. Regardless of the actual activity / view instance.
Objects are nodes, references are edges. Leaks are nodes with an ever increasing number of edges.

=> need to find where the graph is changing. Diff the graph. Who knows how to diff a graph? I don't. But I know how to diff a tree. We already wrote the basis for shortest path tree.

https://github.com/plasma-umass/BLeak/raw/master/paper.pdf

object growth detection

```groovy
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-test:3.0-alpha-8")
    testImplementation("com.squareup.leakcanary:leakcanary-android-test:3.0-alpha-8")
```

```kotlin
    @Test
    fun baguette() {
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
```

https://blog.p-y.wtf/cutting-some-slack-for-leaks-and-giggles

Explain I didn’t have a debug app.
Wrote a UI automator script that switches workspace

