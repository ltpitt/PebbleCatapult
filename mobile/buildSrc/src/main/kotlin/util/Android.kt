package util

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * android {} block that can be used without applying specific android plugin
 */
fun Project.commonAndroid(
   block: CommonExtension.() -> Unit,
) {
   extensions.configure<CommonExtension>("android", block)
}

fun Project.isAndroidProject(): Boolean {
   return pluginManager.hasPlugin("com.android.application") ||
      pluginManager.hasPlugin("com.android.library") ||
      pluginManager.hasPlugin("com.android.test")
}

/**
 * androidComponents {} block that can be used without applying specific android plugin
 */
fun Project.commonAndroidComponents(
   block: AndroidComponentsExtension<Unit, VariantBuilder, Variant>.() -> Unit,
) {
   extensions.configure<AndroidComponentsExtension<Unit, VariantBuilder, Variant>>("androidComponents", block)
}
