package expo.modules.superwallexpo

import android.content.Context
import android.view.ViewGroup
import com.superwall.sdk.paywall.presentation.get_paywall.builder.PaywallBuilder
import com.superwall.sdk.paywall.presentation.internal.PaywallPresentationRequestStatusReason
import com.superwall.sdk.paywall.presentation.internal.state.PaywallResult
import com.superwall.sdk.paywall.presentation.internal.state.PaywallSkippedReason
import com.superwall.sdk.paywall.view.PaywallView
import com.superwall.sdk.paywall.view.delegate.PaywallViewCallback
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.superwallexpo.json.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A React Native host view that owns one SDK PaywallView at a time. */
class SuperwallExpoPaywallView(
  context: Context,
  appContext: AppContext,
) : ExpoView(context, appContext) {
  override val shouldUseAndroidLayout = true

  val onPaywallLoadStart by EventDispatcher<Map<String, Any>>()
  val onPaywallPresent by EventDispatcher<Map<String, Any>>()
  val onPaywallDismiss by EventDispatcher<Map<String, Any>>()
  val onPaywallSkip by EventDispatcher<Map<String, Any>>()
  val onPaywallError by EventDispatcher<Map<String, Any>>()

  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var placement: String? = null
  private var params: Map<String, Any>? = null
  private var loadedPlacement: String? = null
  private var loadedParams: Map<String, Any>? = null
  private var loadGeneration = 0
  private var paywallView: PaywallView? = null

  private val delegate =
    object : PaywallViewCallback {
      override fun onFinished(
        paywall: PaywallView,
        result: PaywallResult,
        shouldDismiss: Boolean,
      ) {
        if (paywall !== paywallView) return

        onPaywallDismiss(
          mapOf(
            "paywallInfo" to paywall.info.toJson(),
            "result" to result.toJson(),
            "shouldDismiss" to shouldDismiss,
          ),
        )

        if (shouldDismiss) {
          releasePaywall()
        }
      }
    }

  fun setPlacement(placement: String) {
    this.placement = placement
  }

  fun setParams(params: Map<String, Any>?) {
    this.params = params
  }

  fun loadPaywallIfNeeded() {
    val placement = placement ?: return
    if (placement.isEmpty() || !isAttachedToWindow) return
    if (placement == loadedPlacement && params == loadedParams) return

    loadedPlacement = placement
    loadedParams = params?.toMap()
    loadGeneration += 1
    val generation = loadGeneration
    releasePaywall()
    onPaywallLoadStart(emptyMap())

    val activity = appContext.currentActivity
    if (activity == null) {
      onPaywallError(mapOf("message" to "No current Activity is available to load the paywall."))
      return
    }

    PaywallBuilder(placement)
      .params(params)
      .delegate(delegate)
      .activity(activity)
      .build(
        onSuccess = { paywall ->
          if (generation != loadGeneration || !isAttachedToWindow) {
            if (paywall.parent == null) {
              cleanupPaywall(paywall)
            }
            return@build
          }
          if (paywall.parent != null) {
            onPaywallError(
              mapOf(
                "message" to
                  "The retrieved paywall is already attached to another view. " +
                  "Use a different placement for each mounted PaywallView.",
              ),
            )
            return@build
          }

          paywallView = paywall
          addView(
            paywall,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
          )
          paywall.onViewCreated()
          onPaywallPresent(mapOf("paywallInfo" to paywall.info.toJson()))
        },
        onError = { error ->
          if (generation != loadGeneration) return@build
          if (error is PaywallSkippedReason) {
            onPaywallSkip(mapOf("reason" to error.toJson()))
          } else {
            val message =
              if (error is PaywallPresentationRequestStatusReason) {
                error.info
              } else {
                error.localizedMessage ?: error.toString()
              }
            onPaywallError(mapOf("message" to message))
          }
        },
      )
  }

  fun destroy() {
    loadGeneration += 1
    loadedPlacement = null
    loadedParams = null
    releasePaywall()
    mainScope.cancel()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    loadPaywallIfNeeded()
  }

  override fun onDetachedFromWindow() {
    loadGeneration += 1
    loadedPlacement = null
    loadedParams = null
    releasePaywall()
    super.onDetachedFromWindow()
  }

  private fun releasePaywall() {
    val paywall = paywallView
    paywallView = null
    if (paywall == null) return
    removeView(paywall)
    cleanupPaywall(paywall)
  }

  private fun cleanupPaywall(paywall: PaywallView) {
    paywall.beforeOnDestroy(forceCleanup = true)
    paywall.encapsulatingActivity = null
    mainScope.launch {
      paywall.destroyed(forceCleanup = true)
      paywall.cleanup()
    }
  }
}
