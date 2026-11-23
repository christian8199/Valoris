package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY updatedAt DESC")
    fun observeAllHoldings(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings")
    suspend fun getAllHoldingsOnce(): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE id = :id LIMIT 1")
    suspend fun getHoldingById(id: Long): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE assetClass = 'CASH' AND UPPER(nativeCurrency) = UPPER(:currency) LIMIT 1")
    suspend fun findCashHoldingByCurrency(currency: String): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHolding(holding: HoldingEntity): Long

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteHoldingById(id: Long)

    @Query("DELETE FROM holdings")
    suspend fun deleteAllHoldings()
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM cached_quotes")
    fun observeAllQuotes(): Flow<List<CachedQuoteEntity>>

    @Query("SELECT * FROM cached_quotes")
    suspend fun getAllQuotesOnce(): List<CachedQuoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuotes(quotes: List<CachedQuoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuote(quote: CachedQuoteEntity)
}

@Dao
interface FxRateDao {
    @Query("SELECT * FROM fx_rates ORDER BY currencyCode ASC")
    fun observeAllRates(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates")
    suspend fun getAllRatesOnce(): List<FxRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRates(rates: List<FxRateEntity>)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM (SELECT * FROM net_worth_snapshots ORDER BY timestamp DESC LIMIT 24) ORDER BY timestamp ASC")
    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>>

    @Query("SELECT * FROM net_worth_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): NetWorthSnapshotEntity?

    @Query("SELECT * FROM net_worth_snapshots ORDER BY timestamp ASC")
    suspend fun getAllSnapshotsOnce(): List<NetWorthSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: NetWorthSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<NetWorthSnapshotEntity>)

    @Query("DELETE FROM net_worth_snapshots WHERE id NOT IN (SELECT id FROM net_worth_snapshots ORDER BY timestamp DESC LIMIT 120)")
    suspend fun pruneOldSnapshots()

    @Query("DELETE FROM net_worth_snapshots")
    suspend fun deleteAllSnapshots()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis ASC, id ASC")
    suspend fun getAllTransactionsAscending(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE holdingId = :holdingId ORDER BY timestampMillis ASC, id ASC")
    suspend fun getTransactionsForHolding(holdingId: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions WHERE holdingId = :holdingId")
    suspend fun deleteTransactionsForHolding(holdingId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
