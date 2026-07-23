import SuperwallKit

extension SubscriptionStatus {
  func toJson() -> [String: Any] {
    switch self {
    case .unknown:
      return ["status": "UNKNOWN"]
    case .inactive:
      return ["status": "INACTIVE"]
    case .active(let entitlements):
      return [
        "status": "ACTIVE",
        "entitlements": entitlements.map { $0.toJson() },
      ]
    }
  }

  static func from(
    status: String,
    entitlements: [[String: Any]]
  ) -> SubscriptionStatus {
    switch status {
    case "UNKNOWN":
      return .unknown
    case "INACTIVE":
      return .inactive
    case "ACTIVE":
      let entitlementsSet: Set<Entitlement> = Set(
        entitlements.compactMap(Entitlement.fromJson)
      )
      return .active(entitlementsSet)
    default:
      return .unknown
    }
  }
}
