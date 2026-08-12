import { View } from "react-native"
import TestRenderer, { act } from "react-test-renderer"
import type { PaywallInfo, PaywallResult, PaywallSkippedReason } from "../SuperwallExpoModule.types"

jest.mock("expo", () => ({
  requireNativeView: () => "NativePaywallView",
}))

jest.mock("react-native", () => ({
  StyleSheet: {
    absoluteFill: { bottom: 0, left: 0, position: "absolute", right: 0, top: 0 },
  },
  View: "View",
}))

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const {
  PaywallView,
}: typeof import("../components/PaywallView") = require("../components/PaywallView")

const paywallInfo = {
  identifier: "paywall-id",
  name: "Article Paywall",
} as PaywallInfo
const declined = { type: "declined" } satisfies PaywallResult
const skipped = { type: "PlacementNotFound" } satisfies PaywallSkippedReason

function fallbackFor(state: { status: string }) {
  return <View testID={`fallback-${state.status}`} />
}

describe("PaywallView", () => {
  it("declaratively renders each fallback state and preserves lifecycle callbacks", () => {
    const onPresent = jest.fn()
    const onDismiss = jest.fn()
    const onSkip = jest.fn()
    const onError = jest.fn()

    let renderer: TestRenderer.ReactTestRenderer
    act(() => {
      renderer = TestRenderer.create(
        <PaywallView
          placement="article"
          renderFallback={fallbackFor}
          onPresent={onPresent}
          onDismiss={onDismiss}
          onSkip={onSkip}
          onError={onError}
        />,
      )
    })

    expect(renderer!.root.findByProps({ testID: "fallback-loading" })).toBeDefined()
    let nativeView = renderer!.root.findByType("NativePaywallView" as never)

    act(() => {
      nativeView.props.onPaywallPresent({ nativeEvent: { paywallInfo } })
    })
    expect(onPresent).toHaveBeenCalledWith(paywallInfo)
    expect(renderer!.root.findAllByProps({ testID: "fallback-loading" })).toHaveLength(0)

    nativeView = renderer!.root.findByType("NativePaywallView" as never)
    act(() => {
      nativeView.props.onPaywallDismiss({
        nativeEvent: { paywallInfo, result: declined, shouldDismiss: false },
      })
    })
    expect(onDismiss).toHaveBeenLastCalledWith(paywallInfo, declined, false)
    expect(renderer!.root.findByType("NativePaywallView" as never)).toBeDefined()

    act(() => {
      nativeView.props.onPaywallDismiss({
        nativeEvent: { paywallInfo, result: declined, shouldDismiss: true },
      })
    })
    expect(renderer!.root.findByProps({ testID: "fallback-dismissed" })).toBeDefined()
    expect(renderer!.root.findAllByType("NativePaywallView" as never)).toHaveLength(0)

    act(() => {
      renderer!.update(
        <PaywallView
          placement="another-article"
          renderFallback={fallbackFor}
          onSkip={onSkip}
          onError={onError}
        />,
      )
    })
    expect(renderer!.root.findByProps({ testID: "fallback-loading" })).toBeDefined()
    nativeView = renderer!.root.findByType("NativePaywallView" as never)

    act(() => {
      nativeView.props.onPaywallSkip({ nativeEvent: { reason: skipped } })
    })
    expect(onSkip).toHaveBeenCalledWith(skipped)
    expect(renderer!.root.findByProps({ testID: "fallback-skipped" })).toBeDefined()

    act(() => {
      renderer!.update(
        <PaywallView
          placement="error-article"
          renderFallback={fallbackFor}
          onSkip={onSkip}
          onError={onError}
        />,
      )
    })
    nativeView = renderer!.root.findByType("NativePaywallView" as never)
    act(() => {
      nativeView.props.onPaywallError({ nativeEvent: { message: "Network unavailable" } })
    })
    expect(onError).toHaveBeenCalledWith("Network unavailable")
    expect(renderer!.root.findByProps({ testID: "fallback-error" })).toBeDefined()
  })

  it("collapses terminal states when no fallback is provided", () => {
    let renderer: TestRenderer.ReactTestRenderer
    act(() => {
      renderer = TestRenderer.create(<PaywallView placement="article" />)
    })

    const nativeView = renderer!.root.findByType("NativePaywallView" as never)
    act(() => {
      nativeView.props.onPaywallSkip({ nativeEvent: { reason: skipped } })
    })

    expect(renderer!.toJSON()).toBeNull()
  })
})
