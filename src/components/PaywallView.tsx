import { requireNativeView } from "expo"
import { type ReactNode, useEffect, useRef, useState } from "react"
import { type NativeSyntheticEvent, StyleSheet, View, type ViewProps } from "react-native"
import type { PaywallInfo, PaywallResult, PaywallSkippedReason } from "../SuperwallExpoModule.types"

type LoadStartEvent = NativeSyntheticEvent<Record<string, never>>
type PresentEvent = NativeSyntheticEvent<{ paywallInfo: PaywallInfo }>
type DismissEvent = NativeSyntheticEvent<{
  paywallInfo: PaywallInfo
  result: PaywallResult
  shouldDismiss: boolean
}>
type SkipEvent = NativeSyntheticEvent<{ reason: PaywallSkippedReason }>
type ErrorEvent = NativeSyntheticEvent<{ message: string }>

type NativePaywallViewProps = {
  placement: string
  params?: Record<string, any>
  style?: ViewProps["style"]
  onPaywallLoadStart?: (event: LoadStartEvent) => void
  onPaywallPresent?: (event: PresentEvent) => void
  onPaywallDismiss?: (event: DismissEvent) => void
  onPaywallSkip?: (event: SkipEvent) => void
  onPaywallError?: (event: ErrorEvent) => void
}

const NativePaywallView = requireNativeView<NativePaywallViewProps>("SuperwallExpo", "PaywallView")

export type PaywallFallbackState =
  | { status: "loading" }
  | { status: "skipped"; reason: PaywallSkippedReason }
  | { status: "error"; error: string }
  | { status: "dismissed"; paywallInfo: PaywallInfo; result: PaywallResult }

type PaywallViewState = PaywallFallbackState | { status: "presented"; paywallInfo: PaywallInfo }

export interface PaywallViewProps extends Omit<ViewProps, "children"> {
  /** The placement whose paywall should be retrieved. */
  placement: string
  /** Optional parameters used to evaluate and render the placement. */
  params?: Record<string, any>
  /**
   * Renders UI while the paywall is loading or after it is skipped, fails, or dismisses.
   * Returning `null` for a terminal state removes the PaywallView from the layout.
   */
  renderFallback?: (state: PaywallFallbackState) => ReactNode
  /** Called once the paywall has been retrieved and attached to the inline host. */
  onPresent?: (paywallInfo: PaywallInfo) => void
  /** Called when the embedded paywall finishes an interaction. */
  onDismiss?: (paywallInfo: PaywallInfo, result: PaywallResult, shouldDismiss: boolean) => void
  /** Called when campaign rules intentionally skip the requested paywall. */
  onSkip?: (reason: PaywallSkippedReason) => void
  /** Called when the paywall cannot be retrieved or attached. */
  onError?: (error: string) => void
}

/**
 * Embeds a Superwall paywall inside a React Native layout.
 *
 * Give the view an explicit width/height, and unmount it when the surrounding
 * content no longer needs it. Each mounted component owns its native paywall;
 * use different placements for paywalls mounted at the same time.
 */
export function PaywallView({
  placement,
  params,
  renderFallback,
  onPresent,
  onDismiss,
  onSkip,
  onError,
  style,
  ...viewProps
}: PaywallViewProps) {
  const [state, setState] = useState<PaywallViewState>({ status: "loading" })
  const configurationKey = `${placement}:${JSON.stringify(params ?? null)}`
  const previousConfigurationKey = useRef(configurationKey)

  useEffect(() => {
    if (previousConfigurationKey.current !== configurationKey) {
      previousConfigurationKey.current = configurationKey
      setState({ status: "loading" })
    }
  }, [configurationKey])

  const fallback = state.status === "presented" ? null : renderFallback?.(state)
  const isTerminal =
    state.status === "skipped" || state.status === "error" || state.status === "dismissed"

  if (isTerminal) {
    if (fallback === null || fallback === undefined || fallback === false) return null
    return (
      <View {...viewProps} style={style}>
        {fallback}
      </View>
    )
  }

  return (
    <View {...viewProps} style={style}>
      <NativePaywallView
        placement={placement}
        params={params}
        style={StyleSheet.absoluteFill}
        onPaywallLoadStart={() => setState({ status: "loading" })}
        onPaywallPresent={(event) => {
          const { paywallInfo } = event.nativeEvent
          setState({ status: "presented", paywallInfo })
          onPresent?.(paywallInfo)
        }}
        onPaywallDismiss={(event) => {
          const { paywallInfo, result, shouldDismiss } = event.nativeEvent
          if (shouldDismiss) setState({ status: "dismissed", paywallInfo, result })
          onDismiss?.(paywallInfo, result, shouldDismiss)
        }}
        onPaywallSkip={(event) => {
          const { reason } = event.nativeEvent
          setState({ status: "skipped", reason })
          onSkip?.(reason)
        }}
        onPaywallError={(event) => {
          const { message } = event.nativeEvent
          setState({ status: "error", error: message })
          onError?.(message)
        }}
      />
      {fallback}
    </View>
  )
}
