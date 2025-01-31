package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.robolectric.RuntimeEnvironment.setFontScale
import org.robolectric.RuntimeEnvironment.setQualifiers
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

@ExperimentalRoborazziApi
interface RoborazziComposeOption

@ExperimentalRoborazziApi
interface RoborazziComposeSetupOption : RoborazziComposeOption {
  fun configure()
}

@ExperimentalRoborazziApi
interface RoborazziComposeActivityScenarioOption : RoborazziComposeOption {
  fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>)
}

@ExperimentalRoborazziApi
interface RoborazziComposeComposableOption : RoborazziComposeOption {
  fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit
}

@ExperimentalRoborazziApi
class RoborazziComposeOptions private constructor(
  private val activityScenarioOptions: List<RoborazziComposeActivityScenarioOption>,
  private val composableOptions: List<RoborazziComposeComposableOption>,
  private val setupOptions: List<RoborazziComposeSetupOption>
) {
  class Builder {
    private val activityScenarioOptions =
      mutableListOf<RoborazziComposeActivityScenarioOption>()
    private val composableOptions = mutableListOf<RoborazziComposeComposableOption>()
    private val setupOptions = mutableListOf<RoborazziComposeSetupOption>()

    fun addOption(option: RoborazziComposeOption): Builder {
      if (option is RoborazziComposeActivityScenarioOption) {
        activityScenarioOptions.add(option)
      }
      if (option is RoborazziComposeComposableOption) {
        composableOptions.add(option)
      }
      if (option is RoborazziComposeSetupOption) {
        setupOptions.add(option)
      }
      return this
    }

    fun build(): RoborazziComposeOptions {
      return RoborazziComposeOptions(
        activityScenarioOptions = activityScenarioOptions,
        composableOptions = composableOptions,
        setupOptions = setupOptions
      )
    }
  }

  fun builder(): Builder {
    return Builder()
      .apply {
        activityScenarioOptions.forEach { addOption(it) }
        composableOptions.forEach { addOption(it) }
        setupOptions.forEach { addOption(it) }
      }
  }

  @ExperimentalRoborazziApi
  fun configured(
    activityScenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    setupOptions.forEach { it.configure() }
    activityScenarioOptions.forEach { it.configureWithActivityScenario(activityScenario) }
    var appliedContent = content
    composableOptions.forEach { config ->
      appliedContent = config.configureWithComposable(appliedContent)
    }
    return {
      appliedContent()
    }
  }

  companion object {
    operator fun invoke(block: Builder.() -> Unit = {}): RoborazziComposeOptions {
      return Builder().apply(block).build()
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.size(
  widthDp: Int = 0,
  heightDp: Int = 0
): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeSizeOption(widthDp, heightDp))
}

@ExperimentalRoborazziApi
data class RoborazziComposeSizeOption(val widthDp: Int, val heightDp: Int) :
  RoborazziComposeActivityScenarioOption,
  RoborazziComposeComposableOption {
  override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
    scenario.onActivity { activity ->
      activity.setDisplaySize(widthDp = widthDp, heightDp = heightDp)
    }
  }

  private fun Activity.setDisplaySize(
    widthDp: Int,
    heightDp: Int
  ) {
    if (widthDp <= 0 && heightDp <= 0) return

    val display = shadowOf(getDefaultDisplay())
    val density = resources.displayMetrics.density
    if (widthDp > 0) {
      val widthPx = (widthDp * density).roundToInt()
      display.setWidth(widthPx)
    }
    if (heightDp > 0) {
      val heightPx = (heightDp * density).roundToInt()
      display.setHeight(heightPx)
    }
    recreate()
  }

  override fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit {
    /**
     * WARNING:
     * For this to work, it requires that the Display is within the widthDp and heightDp dimensions
     * You can ensure that by calling [Activity.setDisplaySize] before
     */
    val modifier = when {
      widthDp > 0 && heightDp > 0 -> Modifier.size(widthDp.dp, heightDp.dp)
      widthDp > 0 -> Modifier.width(widthDp.dp)
      heightDp > 0 -> Modifier.height(heightDp.dp)
      else -> Modifier
    }
    return {
      Box(modifier = modifier) {
        content()
      }
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.background(
  showBackground: Boolean,
  backgroundColor: Long = 0L
): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeBackgroundOption(showBackground, backgroundColor))
}

@ExperimentalRoborazziApi
data class RoborazziComposeBackgroundOption(
  private val showBackground: Boolean,
  private val backgroundColor: Long
) : RoborazziComposeActivityScenarioOption {
  override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
    when (showBackground) {
      false -> {
        scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        }
      }

      true -> {
        val color = when (backgroundColor != 0L) {
          true -> backgroundColor.toInt()
          false -> Color.WHITE
        }
        scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(color)
        }
      }
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.uiMode(uiMode: Int): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeUiModeOption(uiMode))
}

@ExperimentalRoborazziApi
data class RoborazziComposeUiModeOption(private val uiMode: Int) :
  RoborazziComposeSetupOption {
  override fun configure() {
    val nightMode =
      when (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true -> "night"
        false -> "notnight"
      }
    setQualifiers("+$nightMode")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.locale(locale: String): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeLocaleOption(locale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeLocaleOption(private val locale: String) :
  RoborazziComposeSetupOption {
  override fun configure() {
    val localeWithFallback = locale.ifBlank { "en" }
    setQualifiers("+$localeWithFallback")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.fontScale(fontScale: Float): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeFontScaleOption(fontScale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeFontScaleOption(private val fontScale: Float) :
  RoborazziComposeSetupOption {
  init {
    require(fontScale > 0) { "fontScale must be greater than 0" }
  }

  override fun configure() {
    setFontScale(fontScale)
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.inspectionMode(
  inspectionMode: Boolean
): RoborazziComposeOptions.Builder =
  addOption(RoborazziComposeInspectionModeOption(inspectionMode))


@ExperimentalRoborazziApi
data class RoborazziComposeInspectionModeOption(private val inspectionMode: Boolean) :
  RoborazziComposeComposableOption {
  override fun configureWithComposable(
    content: @Composable () -> Unit
  ): @Composable () -> Unit = {
    CompositionLocalProvider(LocalInspectionMode provides inspectionMode) {
      content()
    }
  }
}
