//
//  Entitlement+Json.swift
//  Superwall
//
//  Created by Yusuf Tör on 20/01/2025.
//

import Foundation
import SuperwallKit

extension Entitlement {
  func toJson() -> [String: Any] {
    var json: [String: Any] = [
      "id": id,
      "type": type.toJson(),
      "isActive": isActive,
      "productIds": Array(productIds),
    ]
    json["latestProductId"] = latestProductId
    json["store"] = store?.description
    json["startsAt"] = startsAt.map { Self.iso8601Formatter.string(from: $0) }
    json["renewedAt"] = renewedAt.map { Self.iso8601Formatter.string(from: $0) }
    json["expiresAt"] = expiresAt.map { Self.iso8601Formatter.string(from: $0) }
    json["isLifetime"] = isLifetime
    json["willRenew"] = willRenew
    json["state"] = state?.rawValue
    json["offerType"] = offerType?.rawValue
    return json
  }

  static func fromJson(_ json: [String: Any]) -> Entitlement? {
    guard let id = json["id"] as? String else {
      return nil
    }

    return Entitlement(
      id: id,
      type: .serviceLevel,
      isActive: json["isActive"] as? Bool ?? true,
      productIds: Set(json["productIds"] as? [String] ?? []),
      latestProductId: json["latestProductId"] as? String,
      store: productStore(from: json["store"]),
      startsAt: date(from: json["startsAt"]),
      renewedAt: date(from: json["renewedAt"]),
      expiresAt: date(from: json["expiresAt"]),
      isLifetime: json["isLifetime"] as? Bool,
      willRenew: json["willRenew"] as? Bool,
      state: (json["state"] as? String).flatMap {
        LatestSubscription.State(rawValue: $0)
      },
      offerType: (json["offerType"] as? String).flatMap {
        LatestSubscription.OfferType(rawValue: $0)
      }
    )
  }

  private static let iso8601Formatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
  }()

  private static let iso8601WithoutFractionalSeconds = ISO8601DateFormatter()

  private static func date(from value: Any?) -> Date? {
    if let value = value as? String {
      return iso8601Formatter.date(from: value)
        ?? iso8601WithoutFractionalSeconds.date(from: value)
    }
    if let milliseconds = value as? NSNumber {
      return Date(timeIntervalSince1970: milliseconds.doubleValue / 1_000)
    }
    return nil
  }

  private static func productStore(from value: Any?) -> ProductStore? {
    switch (value as? String)?.uppercased() {
    case "APP_STORE":
      return .appStore
    case "STRIPE":
      return .stripe
    case "PADDLE":
      return .paddle
    case "PLAY_STORE":
      return .playStore
    case "SUPERWALL":
      return .superwall
    case "CUSTOM":
      return .custom
    case "OTHER":
      return .other
    default:
      return nil
    }
  }
}
