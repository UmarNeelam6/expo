package expo.modules.kotlin.viewevent

import android.view.View
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import expo.modules.core.utilities.ifNull
import expo.modules.kotlin.getUnimoduleProxy
import expo.modules.kotlin.logger
import expo.modules.kotlin.types.JSTypeConverter
import expo.modules.kotlin.types.putGeneric

fun interface ViewEventCallback<T> {
  operator fun invoke(arg: T)
}

open class ViewEvent<T>(
  private val name: String,
  private val view: View,
  private val coalescingKey: CoalescingKey<T>?
) : ViewEventCallback<T> {
  private var isValidated = false

  override operator fun invoke(arg: T) {
    val reactContext = view.context as ReactContext
    val nativeModulesProxy = reactContext.getUnimoduleProxy() ?: return
    val appContext = nativeModulesProxy.kotlinInteropModuleRegistry.appContext

    if (!isValidated) {
      val holder = appContext.hostingRuntimeContext.registry.getModuleHolder(view::class.java).ifNull {
        logger.warn("⚠️ Cannot get module holder for ${view::class.java}")
        return
      }
      val callbacks = appContext.hostingRuntimeContext.registry.getViewDefinition(holder, view::class.java)?.callbacksDefinition.ifNull {
        logger.warn("⚠️ Cannot get callbacks for ${holder.module::class.java}")
        return
      }

      if (!callbacks.names.any { it == name }) {
        logger.warn("⚠️ Event $name wasn't exported from ${holder.module::class.java}")
        return
      }

      isValidated = true
    }

    appContext
      .callbackInvoker
      ?.emit(
        view = view,
        eventName = name,
        eventBody = convertEventBody(arg),
        coalescingKey = coalescingKey?.invoke(arg)
      )
  }

  private fun convertEventBody(arg: T): WritableMap? {
    return when (val converted = JSTypeConverter.convertToJSValue(arg)) {
      is Unit, null -> null
      is WritableMap -> converted
      else -> JSTypeConverter.DefaultContainerProvider.createMap().apply {
        putGeneric("payload", converted)
      }
    }
  }
}
