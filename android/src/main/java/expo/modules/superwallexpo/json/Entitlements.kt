package expo.modules.superwallexpo.json

import com.superwall.sdk.models.entitlements.Entitlement
import com.superwall.sdk.models.product.Store
import com.superwall.sdk.store.abstractions.product.receipt.LatestPeriodType
import com.superwall.sdk.store.abstractions.product.receipt.LatestSubscriptionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Set<Entitlement>.toJson(): List<Map<String, Any>> {
  val array = mutableListOf<Map<String, Any>>()
  this@toJson.forEach { entitlement ->
    array.add(entitlement.toJson())
  }
  return array
}

fun Entitlement.toJson(): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  map["id"] = this.id
  map["type"] = this.type.toString()
  map["isActive"] = this.isActive
  map["productIds"] = this.productIds.toList()
  this.latestProductId?.let { map["latestProductId"] = it }
  this.store?.let { map["store"] = it.name }
  this.startsAt?.let { map["startsAt"] = it.time }
  this.renewedAt?.let { map["renewedAt"] = it.time }
  this.expiresAt?.let { map["expiresAt"] = it.time }
  this.isLifetime?.let { map["isLifetime"] = it }
  this.willRenew?.let { map["willRenew"] = it }
  this.state?.let { map["state"] = it.toBridgeValue() }
  this.offerType?.let { map["offerType"] = it.toBridgeValue() }
  return map
}

fun entitlementFromJson(json: Map<String, Any>): Entitlement? {
  val id = json["id"] as? String ?: return null
  return Entitlement(
    id = id,
    type = Entitlement.Type.SERVICE_LEVEL,
    isActive = json["isActive"] as? Boolean ?: true,
    productIds = (json["productIds"] as? List<*>)
      ?.filterIsInstance<String>()
      ?.toSet()
      ?: emptySet(),
    latestProductId = json["latestProductId"] as? String,
    startsAt = parseDate(json["startsAt"]),
    renewedAt = parseDate(json["renewedAt"]),
    expiresAt = parseDate(json["expiresAt"]),
    isLifetime = json["isLifetime"] as? Boolean,
    willRenew = json["willRenew"] as? Boolean,
    state = (json["state"] as? String)?.toSubscriptionState(),
    offerType = (json["offerType"] as? String)?.toPeriodType(),
    store = (json["store"] as? String)?.let { Store.fromValue(it) },
  )
}

private fun parseDate(value: Any?): Date? =
  when (value) {
    is Number -> Date(value.toLong())
    is String -> {
      val millisecondsPrecision = value.replace(
        Regex("(\\.\\d{3})\\d+"),
        "\$1",
      )
      runCatching {
        SimpleDateFormat(
          "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
          Locale.US,
        ).parse(millisecondsPrecision)
      }.getOrNull()
    }
    else -> null
  }

private fun String.toSubscriptionState(): LatestSubscriptionState? =
  when (this) {
    "inGracePeriod" -> LatestSubscriptionState.GRACE_PERIOD
    "subscribed" -> LatestSubscriptionState.SUBSCRIBED
    "expired" -> LatestSubscriptionState.EXPIRED
    "inBillingRetryPeriod" -> LatestSubscriptionState.BILLING_RETRY
    "revoked" -> LatestSubscriptionState.REVOKED
    else -> null
  }

private fun LatestSubscriptionState.toBridgeValue(): String =
  when (this) {
    LatestSubscriptionState.GRACE_PERIOD -> "inGracePeriod"
    LatestSubscriptionState.SUBSCRIBED -> "subscribed"
    LatestSubscriptionState.EXPIRED -> "expired"
    LatestSubscriptionState.BILLING_RETRY -> "inBillingRetryPeriod"
    LatestSubscriptionState.REVOKED -> "revoked"
    LatestSubscriptionState.UNKNOWN -> "unknown"
  }

private fun String.toPeriodType(): LatestPeriodType? =
  when (this) {
    "trial" -> LatestPeriodType.TRIAL
    "code" -> LatestPeriodType.CODE
    "promotional" -> LatestPeriodType.PROMOTIONAL
    "winback" -> LatestPeriodType.WINBACK
    else -> null
  }

private fun LatestPeriodType.toBridgeValue(): String =
  when (this) {
    LatestPeriodType.TRIAL -> "trial"
    LatestPeriodType.CODE -> "code"
    LatestPeriodType.SUBSCRIPTION -> "subscription"
    LatestPeriodType.PROMOTIONAL -> "promotional"
    LatestPeriodType.WINBACK -> "winback"
    LatestPeriodType.REVOKED -> "revoked"
  }
