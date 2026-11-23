package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetTransaction
import com.example.domain.model.Holding
import com.example.domain.model.TransactionType
import com.example.domain.model.UnitType

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val name: String,
    val assetClass: String,
    val quantity: Double,
    val unitType: String,
    val averageCostPerUnit: Double?,
    val nativeCurrency: String,
    val manualPriceOverride: Double?,
    val accountLabel: String,
    val buyDateMillis: Long? = null,
    val updatedAt: Long
) {
    fun toDomain(): Holding = Holding(
        id = id,
        symbol = symbol,
        name = name,
        assetClass = runCatching { AssetClass.valueOf(assetClass) }.getOrDefault(AssetClass.STOCK),
        quantity = quantity,
        unitType = runCatching { UnitType.valueOf(unitType) }.getOrDefault(UnitType.SHARES),
        averageCostPerUnit = averageCostPerUnit,
        nativeCurrency = nativeCurrency,
        manualPriceOverride = manualPriceOverride,
        accountLabel = accountLabel,
        buyDateMillis = buyDateMillis,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(holding: Holding): HoldingEntity = HoldingEntity(
            id = holding.id,
            symbol = holding.symbol.trim().uppercase(),
            name = holding.name.trim(),
            assetClass = holding.assetClass.name,
            quantity = holding.quantity,
            unitType = holding.unitType.name,
            averageCostPerUnit = holding.averageCostPerUnit,
            nativeCurrency = holding.nativeCurrency.trim().uppercase(),
            manualPriceOverride = holding.manualPriceOverride,
            accountLabel = holding.accountLabel.trim(),
            buyDateMillis = holding.buyDateMillis,
            updatedAt = holding.updatedAt
        )
    }
}

@Entity(tableName = "cached_quotes")
data class CachedQuoteEntity(
    @PrimaryKey val symbol: String,
    val priceNative: Double,
    val previousCloseNative: Double,
    val extendedPriceNative: Double? = null,
    val currency: String,
    val dayChangePercent: Double,
    val marketState: String,
    val displayName: String,
    val sparklineCsv: String,
    val fetchedAt: Long
)

@Entity(tableName = "fx_rates")
data class FxRateEntity(
    @PrimaryKey val currencyCode: String,
    val ratePerUsd: Double,
    val source: String,
    val fetchedAt: Long
)

@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val totalValueUsd: Double
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["symbol"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val holdingId: Long? = null,
    val symbol: String,
    val assetName: String,
    val assetClass: String,
    val type: String,
    val quantity: Double,
    val unitType: String,
    val unitPriceNative: Double,
    val totalAmountNative: Double,
    val nativeCurrency: String,
    val realizedPnlNative: Double? = null,
    val depositedToCash: Boolean = false,
    val timestampMillis: Long,
    val note: String = ""
) {
    fun toDomain(): AssetTransaction = AssetTransaction(
        id = id,
        holdingId = holdingId,
        symbol = symbol,
        assetName = assetName,
        assetClass = runCatching { AssetClass.valueOf(assetClass) }.getOrDefault(AssetClass.STOCK),
        type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.BUY),
        quantity = quantity,
        unitType = runCatching { UnitType.valueOf(unitType) }.getOrDefault(UnitType.SHARES),
        unitPriceNative = unitPriceNative,
        totalAmountNative = totalAmountNative,
        nativeCurrency = nativeCurrency,
        realizedPnlNative = realizedPnlNative,
        depositedToCash = depositedToCash,
        timestampMillis = timestampMillis,
        note = note
    )

    companion object {
        fun fromDomain(tx: AssetTransaction): TransactionEntity = TransactionEntity(
            id = tx.id,
            holdingId = tx.holdingId,
            symbol = tx.symbol.trim().uppercase(),
            assetName = tx.assetName.trim(),
            assetClass = tx.assetClass.name,
            type = tx.type.name,
            quantity = tx.quantity,
            unitType = tx.unitType.name,
            unitPriceNative = tx.unitPriceNative,
            totalAmountNative = tx.totalAmountNative,
            nativeCurrency = tx.nativeCurrency.trim().uppercase(),
            realizedPnlNative = tx.realizedPnlNative,
            depositedToCash = tx.depositedToCash,
            timestampMillis = tx.timestampMillis,
            note = tx.note
        )
    }
}
