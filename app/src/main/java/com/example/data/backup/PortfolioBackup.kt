package com.example.data.backup

import com.example.data.local.HoldingEntity
import com.example.data.local.NetWorthSnapshotEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.UserPreferences
import com.example.domain.model.AssetClass
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.domain.model.TransactionType
import com.example.domain.model.UnitType
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class ValorisBackupPayload(
    val format: String = FORMAT_ID,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long,
    val holdings: List<BackupHolding> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val snapshots: List<BackupSnapshot> = emptyList(),
    val preferences: BackupPreferences? = null
) {
    companion object {
        const val FORMAT_ID = "valoris_backup"
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class BackupHolding(
    val id: Long = 0L,
    val symbol: String,
    val name: String,
    val assetClass: String,
    val quantity: Double,
    val unitType: String,
    val averageCostPerUnit: Double? = null,
    val nativeCurrency: String,
    val manualPriceOverride: Double? = null,
    val accountLabel: String = "",
    val buyDateMillis: Long? = null,
    val updatedAt: Long
) {
    fun toEntity(resolvedId: Long = id): HoldingEntity {
        val safeAssetClass = runCatching { AssetClass.valueOf(assetClass.trim().uppercase()) }
            .getOrDefault(AssetClass.STOCK)
        val safeUnitType = runCatching { UnitType.valueOf(unitType.trim().uppercase()) }
            .getOrDefault(UnitType.SHARES)
        val safeSymbol = symbol.trim().uppercase()
        return HoldingEntity(
            id = resolvedId,
            symbol = safeSymbol,
            name = name.trim().ifBlank { safeSymbol },
            assetClass = safeAssetClass.name,
            quantity = quantity.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            unitType = safeUnitType.name,
            averageCostPerUnit = averageCostPerUnit?.takeIf { it.isFinite() && it > 0.0 },
            nativeCurrency = nativeCurrency.trim().uppercase().ifBlank { "USD" },
            manualPriceOverride = manualPriceOverride?.takeIf { it.isFinite() && it > 0.0 },
            accountLabel = accountLabel.trim(),
            buyDateMillis = buyDateMillis?.takeIf { it > 0L },
            updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    companion object {
        fun fromEntity(entity: HoldingEntity): BackupHolding = BackupHolding(
            id = entity.id,
            symbol = entity.symbol,
            name = entity.name,
            assetClass = entity.assetClass,
            quantity = entity.quantity,
            unitType = entity.unitType,
            averageCostPerUnit = entity.averageCostPerUnit,
            nativeCurrency = entity.nativeCurrency,
            manualPriceOverride = entity.manualPriceOverride,
            accountLabel = entity.accountLabel,
            buyDateMillis = entity.buyDateMillis,
            updatedAt = entity.updatedAt
        )
    }
}

@JsonClass(generateAdapter = true)
data class BackupTransaction(
    val id: Long = 0L,
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
    fun toEntity(resolvedHoldingId: Long? = holdingId, resolvedId: Long = 0L): TransactionEntity {
        val safeAssetClass = runCatching { AssetClass.valueOf(assetClass.trim().uppercase()) }
            .getOrDefault(AssetClass.STOCK)
        val safeType = runCatching { TransactionType.valueOf(type.trim().uppercase()) }
            .getOrDefault(TransactionType.BUY)
        val safeUnitType = runCatching { UnitType.valueOf(unitType.trim().uppercase()) }
            .getOrDefault(UnitType.SHARES)
        val safeSymbol = symbol.trim().uppercase()
        return TransactionEntity(
            id = resolvedId,
            holdingId = resolvedHoldingId,
            symbol = safeSymbol,
            assetName = assetName.trim().ifBlank { safeSymbol },
            assetClass = safeAssetClass.name,
            type = safeType.name,
            quantity = quantity.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            unitType = safeUnitType.name,
            unitPriceNative = unitPriceNative.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            totalAmountNative = totalAmountNative.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            nativeCurrency = nativeCurrency.trim().uppercase().ifBlank { "USD" },
            realizedPnlNative = realizedPnlNative?.takeIf { it.isFinite() },
            depositedToCash = depositedToCash,
            timestampMillis = timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
            note = note
        )
    }

    companion object {
        fun fromEntity(entity: TransactionEntity): BackupTransaction = BackupTransaction(
            id = entity.id,
            holdingId = entity.holdingId,
            symbol = entity.symbol,
            assetName = entity.assetName,
            assetClass = entity.assetClass,
            type = entity.type,
            quantity = entity.quantity,
            unitType = entity.unitType,
            unitPriceNative = entity.unitPriceNative,
            totalAmountNative = entity.totalAmountNative,
            nativeCurrency = entity.nativeCurrency,
            realizedPnlNative = entity.realizedPnlNative,
            depositedToCash = entity.depositedToCash,
            timestampMillis = entity.timestampMillis,
            note = entity.note
        )
    }
}

@JsonClass(generateAdapter = true)
data class BackupSnapshot(
    val timestamp: Long,
    val totalValueUsd: Double
) {
    fun toEntity(): NetWorthSnapshotEntity = NetWorthSnapshotEntity(
        id = 0L,
        timestamp = timestamp,
        totalValueUsd = totalValueUsd
    )

    companion object {
        fun fromEntity(entity: NetWorthSnapshotEntity): BackupSnapshot = BackupSnapshot(
            timestamp = entity.timestamp,
            totalValueUsd = entity.totalValueUsd
        )
    }
}

@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val displayCurrency: String = SupportedCurrency.IDR.code,
    val isBalanceHidden: Boolean = false,
    val refreshIntervalMinutes: Int = RefreshInterval.FIVE_MINUTES.minutes,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val useCompactNumbers: Boolean = false,
    val showPnlOnCards: Boolean = true,
    val showExtendedHoursPrice: Boolean = false,
    val defaultChartTimeframe: String = ChartTimeframe.MONTH.name
) {
    companion object {
        fun fromUserPreferences(prefs: UserPreferences): BackupPreferences = BackupPreferences(
            displayCurrency = prefs.displayCurrency.code,
            isBalanceHidden = prefs.isBalanceHidden,
            refreshIntervalMinutes = prefs.refreshInterval.minutes,
            themeMode = prefs.themeMode.name,
            useCompactNumbers = prefs.useCompactNumbers,
            showPnlOnCards = prefs.showPnlOnCards,
            showExtendedHoursPrice = prefs.showExtendedHoursPrice,
            defaultChartTimeframe = prefs.defaultChartTimeframe.name
        )
    }
}

data class BackupPreview(
    val exportedAtMillis: Long,
    val holdingsCount: Int,
    val transactionsCount: Int,
    val snapshotsCount: Int,
    val payload: ValorisBackupPayload
)

data class RestoreResult(
    val holdingsCount: Int,
    val transactionsCount: Int,
    val snapshotsCount: Int
)

object PortfolioBackupCodec {
    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val adapter by lazy {
        moshi.adapter(ValorisBackupPayload::class.java).indent("  ")
    }

    fun encode(payload: ValorisBackupPayload): String = adapter.toJson(payload)

    fun decodeAndValidate(rawJson: String): Result<ValorisBackupPayload> = runCatching {
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "Backup file is empty." }

        val payload = adapter.fromJson(trimmed)
            ?: throw IllegalArgumentException("Could not parse backup file.")

        require(payload.format == ValorisBackupPayload.FORMAT_ID) {
            "This file is not a valid Valoris backup."
        }
        require(payload.schemaVersion in 1..ValorisBackupPayload.CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version (${payload.schemaVersion}). Please update Valoris."
        }
        payload.holdings.forEach { holding ->
            require(holding.symbol.isNotBlank()) { "Backup contains an asset with a missing symbol." }
            require(holding.quantity.isFinite() && holding.quantity >= 0.0) {
                "Backup contains an invalid quantity for ${holding.symbol}."
            }
        }
        payload.transactions.forEach { tx ->
            require(tx.symbol.isNotBlank()) { "Backup contains a transaction with a missing symbol." }
            require(tx.quantity.isFinite() && tx.quantity >= 0.0) {
                "Backup contains an invalid transaction quantity for ${tx.symbol}."
            }
        }
        payload
    }
}
