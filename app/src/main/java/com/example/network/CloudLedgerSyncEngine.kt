package com.example.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.CashBalanceLog
import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import com.example.util.SecureConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PendingOfflineEdit(
    val entityType: String, // "CUSTOMER", "LOAN_CYCLE", "WEEKLY_PAYMENT", "CASH_LOG"
    val entityUuid: String,
    val customerUuid: String,
    val collectionDay: String,
    val action: String,     // "UPSERT", "DELETE"
    val timestamp: Long,
    val summary: String = ""
)

data class ArchivedPastLoanItem(
    val loanUuid: String,
    val customerUuid: String,
    val customerCode: String,
    val customerName: String,
    val fromDate: Long,
    val toDate: Long,
    val loanAmount: Double,
    val interestAmount: Double,
    val deduction: Double,
    val paidAmount: Double,
    val storagePath: String,
    val markdownContent: String
)

object CloudLedgerSyncEngine {
    private const val TAG = "CloudLedgerSyncEngine"
    private const val PREFS_NAME = "weekly_finance_prefs"
    private const val KEY_LAST_SYNCED_AT = "firestore_last_synced_at_ms"
    private const val KEY_INITIAL_MIGRATION_DONE = "firestore_initial_migration_v5_done"
    private const val KEY_PENDING_OFFLINE_EDITS = "firestore_pending_offline_edits_json"
    private const val KEY_LAST_ARCHIVE_CHECK_AT = "firestore_last_archive_check_ms"
    private const val KEY_LEGACY_RTDB_PURGED = "rtdb_legacy_giant_nodes_purged_v3"
    const val PROVISIONED_DATABASE_ID = "ai-studio-android-remixrem-50ea721d-fe66-4687-ae8c-80689d204dd6"

    private val LEGACY_RTDB_CUSTOMER_NODES = listOf(
        "day_branches",
        "ledger_csv",
        "days",
        "all_transactions",
        "customers",
        "loan_cycles",
        "weekly_payments",
        "loans",
        "payments",
        "edit_logs",
        "master_databases",
        "master_backup",
        "dead_letter_queue",
        "ledger",
        "rtdb_pings",
        "sync_triggers",
        "devices"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private val reconcileMutex = Mutex()
    private var debounceFlushJob: Job? = null
    private var debounceSettingsJob: Job? = null

    @Volatile
    var isApplyingRemoteSettings: Boolean = false
        private set

    var onRemoteSettingsAppliedCallback: (() -> Unit)? = null

    fun cancelActiveSyncJobs() {
        debounceFlushJob?.cancel()
        debounceFlushJob = null
        debounceSettingsJob?.cancel()
        debounceSettingsJob = null
    }

    private val _onlinePeerCount = MutableStateFlow(0)
    val onlinePeerCount = _onlinePeerCount.asStateFlow()

    private val _pendingOfflineCount = MutableStateFlow(0)
    val pendingOfflineCount = _pendingOfflineCount.asStateFlow()

    private val _isFetchingPastArchive = MutableStateFlow(false)
    val isFetchingPastArchive = _isFetchingPastArchive.asStateFlow()

    private var firestoreTriggerListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var firestoreSettingsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var lastAppliedTriggerTime: Long = 0L
    private var isPresenceInitialized = false
    private var lastPresenceStatusTime = 0L
    @Volatile
    private var anonymousAuthAttempted = false

    private fun getRtdb(): FirebaseDatabase {
        return try {
            FirebaseDatabase.getInstance(SecureConfig.firebaseDatabaseUrl)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    /**
     * Returns the provisioned Cloud Firestore database instance (`ai-studio-android-remixrem-50ea721d-fe66-4687-ae8c-80689d204dd6`),
     * falling back to `(default)` only if named instance creation is unavailable.
     */
    fun getFirestore(): FirebaseFirestore {
        return try {
            FirebaseFirestore.getInstance(PROVISIONED_DATABASE_ID)
        } catch (_: Exception) {
            FirebaseFirestore.getInstance()
        }
    }

    /**
     * Returns the `(default)` Firestore database instance so writes can also be mirrored to `(default)`
     * in the background for users viewing either database in the Firebase Console.
     */
    private fun getDefaultDatabaseOrNull(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    private fun getStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Best-effort Firebase Auth check: attempts anonymous sign-in at most once per app session if no user
     * is signed in, so disabled anonymous auth never adds latency or timeouts to Firestore operations.
     */
    private fun ensureFirestoreAuthBestEffort() {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null && !anonymousAuthAttempted) {
                anonymousAuthAttempted = true
                Tasks.await(auth.signInAnonymously(), 2, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
            // Proceed using deployed Firestore rules when logged in via Cloud App Password
        }
    }

    private fun <T> awaitQueryOrNull(task: com.google.android.gms.tasks.Task<T>, timeoutSeconds: Long = 8L): T? {
        return try {
            Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore query notice (continuing with local state): ${e.message}")
            null
        }
    }

    private fun awaitCommitOrQueue(task: com.google.android.gms.tasks.Task<*>, timeoutSeconds: Long = 8L): Boolean {
        return try {
            Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            Log.i(TAG, "Firestore write queued in local persistence for background delivery: ${e.message}")
            true
        }
    }

    /**
     * Marks that a local bulk CSV/Excel/JSON import occurred so the next reconciliation
     * performs a full UUID push to Cloud Firestore.
     */
    fun markBulkImportNeedsCloudSync(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_INITIAL_MIGRATION_DONE, false).apply()
        } catch (_: Exception) {}
    }

    /**
     * Strictly one-way cleanup of RTDB: purges any unacknowledged local RTDB writes and
     * deletes any legacy customer/ledger/ping/sync nodes from RTDB without EVER writing to RTDB.
     * Preserves `/versionId`, `/update_config`, and `/app_password` for app release checks.
     */
    fun purgeLegacyRtdbQueuesAndNodes(
        context: Context,
        removeRemoteLegacyBranches: Boolean = false,
        forceRemotePurge: Boolean = false
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("rtdb_local_disk_cache_purged_v3", false)) {
                try {
                    context.databaseList()?.forEach { dbName ->
                        if (dbName.contains("firebasedatabase", ignoreCase = true) ||
                            dbName.contains("default-rtdb", ignoreCase = true)
                        ) {
                            context.deleteDatabase(dbName)
                            Log.i(TAG, "Deleted legacy RTDB persistence database: $dbName")
                        }
                    }
                } catch (_: Exception) {}
                prefs.edit().putBoolean("rtdb_local_disk_cache_purged_v3", true).apply()
            }

            val rtdb = getRtdb()
            try {
                rtdb.purgeOutstandingWrites()
            } catch (_: Exception) {}

            if (forceRemotePurge || (removeRemoteLegacyBranches && !prefs.getBoolean(KEY_LEGACY_RTDB_PURGED, false))) {
                for (node in LEGACY_RTDB_CUSTOMER_NODES) {
                    try {
                        rtdb.getReference(node).removeValue()
                    } catch (_: Exception) {}
                }
                prefs.edit().putBoolean(KEY_LEGACY_RTDB_PURGED, true).apply()
                Log.i(TAG, "Deleted all legacy customer/ledger/sync nodes from RTDB (one-way delete only).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy RTDB purge notice: ${e.message}")
        }
    }

    private fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "Never"
        return SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).format(Date(ms))
    }

    fun getDeviceId(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "dev"
        } catch (_: Exception) {
            "dev"
        }
        val model = "${Build.MANUFACTURER}_${Build.MODEL}".replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "${model}_${androidId.take(8)}"
    }

    fun sanitizeGmailKey(email: String): String {
        val clean = email.trim().lowercase(Locale.US).ifBlank { "muneeswaran45@gmail.com" }
        return clean.replace(".", "_dot_").replace("@", "_at_").replace(Regex("[#$\\[\\]/]"), "_")
    }

    fun getCurrentUserEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString("current_email", "")?.trim().orEmpty()
        return saved.ifBlank { "muneeswaran45@gmail.com" }
    }

    fun getCurrentUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString("current_user", "")?.trim().orEmpty()
        return saved.ifBlank { "Muneeswaran" }
    }

    /**
     * Registers this device in Cloud Firestore `(default)` under `devices/{deviceId}` and listens
     * to `sync_metadata/latest_trigger` in Cloud Firestore for real-time single-document delta updates.
     * Never writes any customer, trigger, or device data to RTDB (only purges legacy RTDB nodes).
     */
    fun initializeDevicePresenceAndTriggers(
        context: Context,
        db: AppDatabase,
        onStatusUpdate: (String) -> Unit = {}
    ) {
        try {
            refreshPendingCount(context)
            purgeLegacyRtdbQueuesAndNodes(context, removeRemoteLegacyBranches = true)
            if (isPresenceInitialized && firestoreTriggerListener != null) {
                return
            }

            val firestore = getFirestore()
            val email = getCurrentUserEmail(context)
            val username = getCurrentUsername(context)
            val myDeviceId = getDeviceId(context)
            val now = System.currentTimeMillis()

            // 1. Update device presence in Cloud Firestore `(default)` -> `devices/{myDeviceId}`
            scope.launch {
                try {
                    ensureFirestoreAuthBestEffort()
                    val onlinePayload = mutableMapOf<String, Any>(
                        "username" to username,
                        "userLoginId" to email,
                        "deviceId" to myDeviceId,
                        "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "status" to "online",
                        "lastActiveSessionTime" to now,
                        "lastActiveFormatted" to formatTimestamp(now)
                    )
                    try {
                        val token = Tasks.await(FirebaseMessaging.getInstance().token, 4, TimeUnit.SECONDS)
                        if (!token.isNullOrBlank()) {
                            onlinePayload["fcmToken"] = token
                        }
                    } catch (_: Exception) {}

                    firestore.collection("devices")
                        .document(myDeviceId)
                        .set(onlinePayload, SetOptions.merge())
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore device registration notice: ${e.message}")
                }
            }

            // 2. Watch `sync_metadata/latest_trigger` in Cloud Firestore for instant single-document delta pulls
            firestoreTriggerListener?.remove()
            firestoreTriggerListener = firestore.collection("sync_metadata")
                .document("latest_trigger")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val sourceDevice = snapshot.getString("sourceDeviceId").orEmpty()
                    val triggerTime = snapshot.getLong("lastModified") ?: 0L
                    if (sourceDevice == myDeviceId || triggerTime <= lastAppliedTriggerTime) return@addSnapshotListener
                    lastAppliedTriggerTime = triggerTime
                    if (_onlinePeerCount.value == 0) {
                        _onlinePeerCount.value = 1
                    }

                    val entityType = snapshot.getString("entityType").orEmpty()
                    val entityUuid = snapshot.getString("entityUuid").orEmpty()
                    val action = snapshot.getString("action") ?: "UPSERT"

                    if (entityType.isNotBlank() && entityUuid.isNotBlank()) {
                        scope.launch {
                            try {
                                pullSingleEntityFromFirestore(context, db, entityType, entityUuid, action)
                                onStatusUpdate("Synced live change from Firestore ($entityType)")
                            } catch (e: Exception) {
                                Log.w(TAG, "Firestore delta trigger pull failed: ${e.message}")
                            }
                        }
                    }
                }
            attachUserSettingsRealtimeListener(context, onStatusUpdate)
            isPresenceInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore device presence: ${e.message}")
        }
    }

    /**
     * Marks this device as `"online"` or `"offline"` in Cloud Firestore `devices/{deviceId}` (throttled).
     */
    fun updateDeviceOnlineStatus(context: Context, isOnline: Boolean) {
        try {
            val now = System.currentTimeMillis()
            if (isOnline && (now - lastPresenceStatusTime) < 30_000L) return
            lastPresenceStatusTime = now
            val firestore = getFirestore()
            val myDeviceId = getDeviceId(context)
            val updates = mapOf(
                "deviceId" to myDeviceId,
                "status" to if (isOnline) "online" else "offline",
                "lastActiveSessionTime" to now,
                "lastActiveFormatted" to formatTimestamp(now)
            )
            firestore.collection("devices").document(myDeviceId).set(updates, SetOptions.merge())
        } catch (_: Exception) {}
    }

    // =========================================================================
    // OFFLINE EDIT QUEUE MANAGEMENT (COALESCED & DEBOUNCED)
    // =========================================================================

    private fun getPendingOfflineEdits(context: Context): MutableList<PendingOfflineEdit> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_OFFLINE_EDITS, "[]") ?: "[]"
        val list = mutableListOf<PendingOfflineEdit>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PendingOfflineEdit(
                        entityType = obj.optString("entityType"),
                        entityUuid = obj.optString("entityUuid"),
                        customerUuid = obj.optString("customerUuid"),
                        collectionDay = obj.optString("collectionDay"),
                        action = obj.optString("action", "UPSERT"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        summary = obj.optString("summary", "")
                    )
                )
            }
        } catch (_: Exception) {}
        _pendingOfflineCount.value = list.size
        return list
    }

    private fun savePendingOfflineEdits(context: Context, edits: List<PendingOfflineEdit>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        edits.forEach { edit ->
            val obj = JSONObject().apply {
                put("entityType", edit.entityType)
                put("entityUuid", edit.entityUuid)
                put("customerUuid", edit.customerUuid)
                put("collectionDay", edit.collectionDay)
                put("action", edit.action)
                put("timestamp", edit.timestamp)
                put("summary", edit.summary)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PENDING_OFFLINE_EDITS, arr.toString()).apply()
        _pendingOfflineCount.value = edits.size
    }

    fun refreshPendingCount(context: Context) {
        getPendingOfflineEdits(context)
    }

    @Synchronized
    fun enqueueOfflineOrLiveEdit(
        context: Context,
        db: AppDatabase,
        entityType: String,
        entityUuid: String,
        customerUuid: String,
        collectionDay: String,
        action: String = "UPSERT",
        timestamp: Long = System.currentTimeMillis(),
        summary: String = ""
    ) {
        if (entityUuid.isBlank()) return
        val current = getPendingOfflineEdits(context).filterNot {
            it.entityType == entityType && it.entityUuid == entityUuid
        }.toMutableList()
        current.add(
            PendingOfflineEdit(
                entityType = entityType,
                entityUuid = entityUuid,
                customerUuid = customerUuid,
                collectionDay = collectionDay,
                action = action,
                timestamp = timestamp,
                summary = summary
            )
        )
        savePendingOfflineEdits(context, current)

        // Debounce rapid consecutive edits (e.g. creating customer + loan or route order swaps) into a single batched flush
        debounceFlushJob?.cancel()
        debounceFlushJob = scope.launch {
            delay(800L)
            try {
                flushPendingEditsWithConflictCheck(context, db)
            } catch (e: Exception) {
                Log.d(TAG, "Queued edit for offline sync ($entityType / $entityUuid): ${e.message}")
            }
        }
    }

    // =========================================================================
    // 5-SCENARIO QUOTA-MINIMIZING RECONCILIATION ENGINE
    // =========================================================================

    /**
     * Executes the 5-scenario permutation check on app launch, login, or reconnection:
     * 1. Checks RTDB `devices` for any other device with `lastModified > myLastSyncedAt`.
     * 2. Short-circuits with 0 Firestore reads if no other device modified data!
     * 3. Otherwise queries `audit_logs` > `myLastSyncedAt`, resolves timestamp conflicts
     *    against local offline edits, and reads/writes only the exact changed UUIDs.
     */
    suspend fun reconcileWithCloud(
        context: Context,
        db: AppDatabase,
        forceFullMigrationCheck: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        reconcileMutex.withLock {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val myDeviceId = getDeviceId(context)
            val myLastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
            var isInitialMigrationDone = prefs.getBoolean(KEY_INITIAL_MIGRATION_DONE, false)

            ensureFirestoreAuthBestEffort()
            purgeLegacyRtdbQueuesAndNodes(context, removeRemoteLegacyBranches = true)

            val firestore = getFirestore()
            val localCustCount = try { db.collectionDao().getAllCustomersOnce().size } catch (_: Throwable) { 0 }

            // If local database has customers, verify whether Cloud Firestore is empty
            if (isInitialMigrationDone && !forceFullMigrationCheck && localCustCount > 0) {
                val existingCloudSample = awaitQueryOrNull(
                    firestore.collection("customers").limit(1).get(),
                    6L
                )
                if (existingCloudSample != null && existingCloudSample.isEmpty) {
                    Log.i(TAG, "Cloud Firestore /customers is empty while local has $localCustCount borrowers; forcing full upload.")
                    isInitialMigrationDone = false
                }
            }

            // Step 0: Run full UUID reconciliation if initial migration was never done, Firestore is empty, OR user tapped Force Full Resync
            if (!isInitialMigrationDone || forceFullMigrationCheck) {
                onProgress("Synchronizing local ledger with Cloud Firestore...")
                val summary = performFullBidirectionalUuidReconciliation(context, db, onProgress)
                prefs.edit()
                    .putBoolean(KEY_INITIAL_MIGRATION_DONE, true)
                    .putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())
                    .apply()
                return@withLock summary
            }

            // Always reconcile user_settings (SMS templates, statement table columns, QR code, theme, favorites)
            try {
                reconcileUserSettingsWithFirestore(context)
            } catch (e: Exception) {
                Log.w(TAG, "User settings sync check notice: ${e.message}")
            }

            // Step 1: Check Cloud Firestore `devices` collection to see if ANY other device modified data after myLastSyncedAt
            onProgress("Checking connected device timestamps in Firestore...")
            var maxOtherDeviceModified = 0L
            var peersOnline = 0
            val devicesSnap = awaitQueryOrNull(firestore.collection("devices").get(), 6L)
            if (devicesSnap != null) {
                for (dDoc in devicesSnap.documents) {
                    val devId = dDoc.getString("deviceId") ?: dDoc.id
                    val devStatus = dDoc.getString("status") ?: "offline"
                    val devLastMod = dDoc.getLong("lastModified") ?: 0L
                    if (devId != myDeviceId) {
                        if (devStatus.equals("online", ignoreCase = true)) {
                            peersOnline++
                        }
                        if (devLastMod > maxOtherDeviceModified) {
                            maxOtherDeviceModified = devLastMod
                        }
                    }
                }
            }
            _onlinePeerCount.value = peersOnline

            val pendingEdits = getPendingOfflineEdits(context)
            val otherDeviceModifiedSinceLastSync = maxOtherDeviceModified > myLastSyncedAt

            // PERMUTATION 1: No other device modified data AND no pending local offline edits -> 0 extra reads/writes!
            if (!otherDeviceModifiedSinceLastSync && pendingEdits.isEmpty()) {
                val totalCust = db.collectionDao().getAllCustomersOnce().size
                return@withLock "Firestore Synced ($totalCust borrowers • Up to date)"
            }

            // PERMUTATION 2: No other device modified data, BUT this device has pending offline edits -> 0 audit reads, direct batch push!
            if (!otherDeviceModifiedSinceLastSync && pendingEdits.isNotEmpty()) {
                onProgress("Uploading ${pendingEdits.size} local changes to Cloud Firestore...")
                flushPendingEditsWithConflictCheck(context, db)
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SYNCED_AT, now).apply()
                val totalCust = db.collectionDao().getAllCustomersOnce().size
                return@withLock "Firestore Synced (${pendingEdits.size} local changes uploaded • $totalCust borrowers)"
            }

            // PERMUTATION 3 & 4: Another device HAS modified data since myLastSyncedAt!
            onProgress("Reading Firestore Audit Log for delta updates...")
            val auditDocs = awaitQueryOrNull(
                firestore.collection("audit_logs")
                    .whereGreaterThan("timestamp", myLastSyncedAt)
                    .get(),
                8L
            )?.documents.orEmpty()

            data class CloudAuditPointer(
                val entityType: String,
                val entityUuid: String,
                val action: String,
                val timestamp: Long,
                val deviceId: String
            )

            val latestCloudChanges = mutableMapOf<String, CloudAuditPointer>()
            var highestAuditTime = maxOf(myLastSyncedAt, maxOtherDeviceModified)

            for (doc in auditDocs) {
                val eType = doc.getString("entityType").orEmpty()
                val eUuid = doc.getString("entityUuid").orEmpty()
                val act = doc.getString("action") ?: "UPSERT"
                val ts = doc.getLong("timestamp") ?: 0L
                val devId = doc.getString("deviceId").orEmpty()
                if (ts > highestAuditTime) highestAuditTime = ts
                if (devId == myDeviceId || eType.isBlank() || eUuid.isBlank()) continue

                val key = "$eType|$eUuid"
                val existing = latestCloudChanges[key]
                if (existing == null || ts > existing.timestamp) {
                    latestCloudChanges[key] = CloudAuditPointer(eType, eUuid, act, ts, devId)
                }
            }

            // PERMUTATION 4: Reconcile any pending local offline edits against incoming cloud audit entries
            val remainingLocalToPush = mutableListOf<PendingOfflineEdit>()
            for (localEdit in pendingEdits) {
                val key = "${localEdit.entityType}|${localEdit.entityUuid}"
                val cloudEntry = latestCloudChanges[key]
                if (cloudEntry == null || localEdit.timestamp >= cloudEntry.timestamp) {
                    remainingLocalToPush.add(localEdit)
                    latestCloudChanges.remove(key)
                } else {
                    Log.i(TAG, "Cloud audit entry for $key (${cloudEntry.timestamp}) is newer than offline edit (${localEdit.timestamp}); adopting cloud version.")
                }
            }

            if (remainingLocalToPush.isNotEmpty()) {
                onProgress("Committing ${remainingLocalToPush.size} newer local edits to Firestore...")
                savePendingOfflineEdits(context, remainingLocalToPush)
                flushPendingEditsWithConflictCheck(context, db)
            } else if (pendingEdits.isNotEmpty()) {
                savePendingOfflineEdits(context, emptyList())
            }

            val orderedCloudChanges = latestCloudChanges.values.sortedBy {
                when (it.entityType) {
                    "CUSTOMER" -> 1
                    "LOAN_CYCLE" -> 2
                    "WEEKLY_PAYMENT" -> 3
                    else -> 4
                }
            }

            if (orderedCloudChanges.isNotEmpty()) {
                onProgress("Applying ${orderedCloudChanges.size} delta updates from Cloud...")
                for (change in orderedCloudChanges) {
                    pullSingleEntityFromFirestore(context, db, change.entityType, change.entityUuid, change.action)
                }
            }

            val finalSyncStamp = maxOf(highestAuditTime, System.currentTimeMillis())
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, finalSyncStamp).apply()
            val totalCust = db.collectionDao().getAllCustomersOnce().size
            return@withLock "Synced (${orderedCloudChanges.size} cloud updates applied • $totalCust borrowers)"
        }
    }

    /**
     * Flushes queued local edits to Firestore in a single atomic WriteBatch (0 pre-read GET overhead!),
     * records each change in `audit_logs`, updates RTDB `devices/.../lastModified`,
     * and emits a lightweight `sync_triggers/latest` event (~150 bytes).
     */
    suspend fun flushPendingEditsWithConflictCheck(
        context: Context,
        db: AppDatabase
    ) = withContext(Dispatchers.IO) {
        flushMutex.withLock {
            val pending = getPendingOfflineEdits(context)
            if (pending.isEmpty()) return@withLock

            ensureFirestoreAuthBestEffort()

            val firestore = getFirestore()
            val myDeviceId = getDeviceId(context)
            val email = getCurrentUserEmail(context)
            val username = getCurrentUsername(context)

            val dao = db.collectionDao()
            val needCustomers = pending.any { it.entityType == "CUSTOMER" || it.entityType == "LOAN_CYCLE" || it.entityType == "WEEKLY_PAYMENT" }
            val needLoans = pending.any { it.entityType == "LOAN_CYCLE" || it.entityType == "WEEKLY_PAYMENT" }
            val needPayments = pending.any { it.entityType == "WEEKLY_PAYMENT" }
            val needCashLogs = pending.any { it.entityType == "CASH_LOG" }

            val customersByUuid = if (needCustomers) dao.getAllCustomersOnce().associateBy { it.uuid } else emptyMap()
            val customersById = if (needCustomers) customersByUuid.values.associateBy { it.id } else emptyMap()
            val loansByUuid = if (needLoans) dao.getAllLoanCyclesOnce().associateBy { it.uuid } else emptyMap()
            val loansById = if (needLoans) loansByUuid.values.associateBy { it.id } else emptyMap()
            val paymentsByUuid = if (needPayments) dao.getAllPaymentsOnce().associateBy { it.uuid } else emptyMap()
            val cashLogs = if (needCashLogs) dao.getAllCashBalanceLogsOnce() else emptyList()

            val remainingIfFailed = mutableListOf<PendingOfflineEdit>()
            var latestModificationTime = 0L
            var lastPushedEdit: PendingOfflineEdit? = null

            // Process in chunks of 150 edits (each edit writes 1 entity doc + 1 audit_log doc = 300 batch ops, well below Firestore's 500 limit)
            for (chunk in pending.chunked(150)) {
                try {
                    val batch = firestore.batch()
                    var opsInBatch = 0

                    for (edit in chunk) {
                        val collectionName = when (edit.entityType) {
                            "CUSTOMER" -> "customers"
                            "LOAN_CYCLE" -> "loan_cycles"
                            "WEEKLY_PAYMENT" -> "weekly_payments"
                            "CASH_LOG" -> "cash_balance_logs"
                            else -> "customers"
                        }
                        val docRef = firestore.collection(collectionName).document(edit.entityUuid)

                        when (edit.entityType) {
                            "CUSTOMER" -> {
                                val c = customersByUuid[edit.entityUuid]
                                if (edit.action == "DELETE" || c == null) {
                                    batch.set(
                                        docRef,
                                        mapOf(
                                            "uuid" to edit.entityUuid,
                                            "status" to "DELETED",
                                            "lastModified" to edit.timestamp,
                                            "lastModifiedFormatted" to formatTimestamp(edit.timestamp),
                                            "lastModifiedByDevice" to myDeviceId,
                                            "lastModifiedByUser" to email
                                        ),
                                        SetOptions.merge()
                                    )
                                    opsInBatch++
                                } else {
                                    batch.set(docRef, customerToFirestoreMap(c, myDeviceId, email))
                                    opsInBatch++
                                }
                            }
                            "LOAN_CYCLE" -> {
                                val l = loansByUuid[edit.entityUuid]
                                if (edit.action == "DELETE" || l == null) {
                                    batch.set(
                                        docRef,
                                        mapOf(
                                            "uuid" to edit.entityUuid,
                                            "status" to "DELETED",
                                            "lastModified" to edit.timestamp,
                                            "lastModifiedFormatted" to formatTimestamp(edit.timestamp),
                                            "lastModifiedByDevice" to myDeviceId,
                                            "lastModifiedByUser" to email
                                        ),
                                        SetOptions.merge()
                                    )
                                    opsInBatch++
                                } else {
                                    val parentCustomer = customersById[l.customerId]
                                    if (parentCustomer != null) {
                                        batch.set(docRef, loanCycleToFirestoreMap(l, parentCustomer, myDeviceId, email))
                                        opsInBatch++
                                    }
                                }
                            }
                            "WEEKLY_PAYMENT" -> {
                                val p = paymentsByUuid[edit.entityUuid]
                                if (edit.action == "DELETE" || p == null || p.status.equals("DELETED", ignoreCase = true)) {
                                    batch.set(
                                        docRef,
                                        mapOf(
                                            "uuid" to edit.entityUuid,
                                            "status" to "DELETED",
                                            "lastModified" to edit.timestamp,
                                            "lastModifiedFormatted" to formatTimestamp(edit.timestamp),
                                            "lastModifiedByDevice" to myDeviceId,
                                            "lastModifiedByUser" to email
                                        ),
                                        SetOptions.merge()
                                    )
                                    opsInBatch++
                                } else {
                                    val parentLoan = loansById[p.loanCycleId]
                                    val parentCustomer = parentLoan?.let { customersById[it.customerId] }
                                    if (parentLoan != null && parentCustomer != null) {
                                        batch.set(docRef, paymentToFirestoreMap(p, parentLoan, parentCustomer, myDeviceId, email))
                                        opsInBatch++
                                    }
                                }
                            }
                            "CASH_LOG" -> {
                                val logItem = cashLogs.find { "cash_${it.date}" == edit.entityUuid || it.id.toString() == edit.entityUuid }
                                    ?: cashLogs.maxByOrNull { it.date }
                                if (logItem != null) {
                                    batch.set(docRef, cashLogToFirestoreMap(logItem, edit.entityUuid, myDeviceId, email))
                                    opsInBatch++
                                }
                            }
                        }

                        // Write Firestore Audit Log entry in the same atomic batch
                        val logUuid = UUID.randomUUID().toString()
                        val auditMap = mapOf(
                            "logUuid" to logUuid,
                            "timestamp" to edit.timestamp,
                            "dateTimeFormatted" to formatTimestamp(edit.timestamp),
                            "deviceId" to myDeviceId,
                            "username" to username,
                            "userLoginId" to email,
                            "entityType" to edit.entityType,
                            "entityUuid" to edit.entityUuid,
                            "customerUuid" to edit.customerUuid,
                            "collectionDay" to edit.collectionDay,
                            "action" to edit.action,
                            "summary" to edit.summary
                        )
                        batch.set(firestore.collection("audit_logs").document(logUuid), auditMap)
                        opsInBatch++

                        if (edit.timestamp > latestModificationTime) {
                            latestModificationTime = edit.timestamp
                        }
                        lastPushedEdit = edit
                    }

                    if (opsInBatch > 0) {
                        awaitCommitOrQueue(batch.commit(), 8L)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to commit edit batch (${chunk.size} items): ${e.message}")
                    remainingIfFailed.addAll(chunk)
                }
            }

            // Preserve any newly enqueued edits that arrived while batch was committing
            val currentInPrefs = getPendingOfflineEdits(context)
            val processedKeys = pending.filterNot { it in remainingIfFailed }.map { "${it.entityType}|${it.entityUuid}|${it.timestamp}" }.toSet()
            val finalRemaining = currentInPrefs.filterNot { "${it.entityType}|${it.entityUuid}|${it.timestamp}" in processedKeys }
            savePendingOfflineEdits(context, finalRemaining)

            if (latestModificationTime > 0L) {
                val now = System.currentTimeMillis()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_SYNCED_AT, maxOf(now, latestModificationTime)).apply()

                val devUpdates = mapOf(
                    "deviceId" to myDeviceId,
                    "username" to username,
                    "userLoginId" to email,
                    "lastModified" to latestModificationTime,
                    "lastModifiedFormatted" to formatTimestamp(latestModificationTime),
                    "lastActiveSessionTime" to now,
                    "lastActiveFormatted" to formatTimestamp(now),
                    "status" to "online"
                )
                firestore.collection("devices").document(myDeviceId).set(devUpdates, SetOptions.merge())
                getDefaultDatabaseOrNull()?.collection("devices")?.document(myDeviceId)?.set(devUpdates, SetOptions.merge())

                if (lastPushedEdit != null) {
                    val triggerMap = mapOf(
                        "entityType" to lastPushedEdit.entityType,
                        "entityUuid" to lastPushedEdit.entityUuid,
                        "customerUuid" to lastPushedEdit.customerUuid,
                        "collectionDay" to lastPushedEdit.collectionDay,
                        "action" to lastPushedEdit.action,
                        "lastModified" to latestModificationTime,
                        "sourceDeviceId" to myDeviceId
                    )
                    firestore.collection("sync_metadata").document("latest_trigger").set(triggerMap)
                    getDefaultDatabaseOrNull()?.collection("sync_metadata")?.document("latest_trigger")?.set(triggerMap)
                }
            }
        }
    }

    /**
     * Pulls a single specific document from Firestore by UUID and merges it into local Room SQLite
     * if `cloud.lastModified >= local.lastModified`.
     */
    suspend fun pullSingleEntityFromFirestore(
        context: Context,
        db: AppDatabase,
        entityType: String,
        entityUuid: String,
        action: String
    ): Unit = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        val dao = db.collectionDao()

        when (entityType) {
            "CUSTOMER" -> {
                val doc = awaitQueryOrNull(firestore.collection("customers").document(entityUuid).get(), 6L) ?: return@withContext
                val existingLocal = dao.getAllCustomersOnce().find { it.uuid == entityUuid }
                if (!doc.exists() || doc.getString("status") == "DELETED" || action == "DELETE") {
                    if (existingLocal != null) {
                        dao.deleteCustomer(existingLocal)
                    }
                    return@withContext
                }
                val cloudMod = doc.getLong("lastModified") ?: 0L
                if (existingLocal != null && existingLocal.lastModified > cloudMod) {
                    return@withContext
                }
                val updatedCustomer = Customer(
                    id = existingLocal?.id ?: 0,
                    name = doc.getString("name") ?: existingLocal?.name.orEmpty(),
                    phone = doc.getString("phone") ?: existingLocal?.phone.orEmpty(),
                    phone2 = doc.getString("phone2") ?: existingLocal?.phone2.orEmpty(),
                    customOrder = (doc.getLong("customOrder") ?: existingLocal?.customOrder?.toLong() ?: 0L).toInt(),
                    collectionDay = doc.getString("collectionDay") ?: existingLocal?.collectionDay ?: "Monday",
                    createdAt = doc.getLong("createdAt") ?: existingLocal?.createdAt ?: System.currentTimeMillis(),
                    city = doc.getString("city") ?: existingLocal?.city.orEmpty(),
                    smsWeeklyReminder = doc.getBoolean("smsWeeklyReminder") ?: existingLocal?.smsWeeklyReminder ?: false,
                    smsConfirmationOfEntry = doc.getBoolean("smsConfirmationOfEntry") ?: existingLocal?.smsConfirmationOfEntry ?: false,
                    autoWeeklySms = doc.getBoolean("autoWeeklySms") ?: false,
                    autoWeeklyWhatsapp = doc.getBoolean("autoWeeklyWhatsapp") ?: false,
                    upiNameAlias = doc.getString("upiNameAlias").orEmpty(),
                    preferredLanguage = doc.getString("preferredLanguage") ?: "English",
                    uuid = entityUuid,
                    lastModified = cloudMod,
                    syncedLastSavedAt = System.currentTimeMillis(),
                    status = doc.getString("status") ?: "ACTIVE"
                )
                dao.insertCustomer(updatedCustomer)
            }

            "LOAN_CYCLE" -> {
                val doc = awaitQueryOrNull(firestore.collection("loan_cycles").document(entityUuid).get(), 6L) ?: return@withContext
                val existingLocal = dao.getAllLoanCyclesOnce().find { it.uuid == entityUuid }
                if (!doc.exists() || doc.getString("status") == "DELETED" || action == "DELETE") {
                    if (existingLocal != null) {
                        dao.deleteLoanCycle(existingLocal)
                    }
                    return@withContext
                }
                val cloudMod = doc.getLong("lastModified") ?: 0L
                if (existingLocal != null && existingLocal.lastModified > cloudMod) {
                    return@withContext
                }
                val customerUuid = doc.getString("customerUuid").orEmpty()
                var parentCustomer = dao.getAllCustomersOnce().find { it.uuid == customerUuid }
                if (parentCustomer == null && customerUuid.isNotBlank()) {
                    pullSingleEntityFromFirestore(context, db, "CUSTOMER", customerUuid, "UPSERT")
                    parentCustomer = dao.getAllCustomersOnce().find { it.uuid == customerUuid }
                }
                if (parentCustomer == null) return@withContext

                val updatedLoan = LoanCycle(
                    id = existingLocal?.id ?: 0,
                    customerId = parentCustomer.id,
                    loanAmount = doc.getDouble("loanAmount") ?: 0.0,
                    interestAmount = doc.getDouble("interestAmount") ?: 0.0,
                    weeklyAmount = doc.getDouble("weeklyAmount") ?: 0.0,
                    totalWeeks = (doc.getLong("totalWeeks") ?: 10L).toInt(),
                    startDate = doc.getLong("startDate") ?: System.currentTimeMillis(),
                    status = doc.getString("status") ?: "ACTIVE",
                    notes = doc.getString("notes").orEmpty(),
                    paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                    uuid = entityUuid,
                    lastModified = cloudMod,
                    deduction = doc.getDouble("deduction") ?: 0.0
                )
                dao.insertLoanCycle(updatedLoan)
            }

            "WEEKLY_PAYMENT" -> {
                val doc = awaitQueryOrNull(firestore.collection("weekly_payments").document(entityUuid).get(), 6L) ?: return@withContext
                val existingLocal = dao.getAllPaymentsOnce().find { it.uuid == entityUuid }
                if (!doc.exists() || doc.getString("status") == "DELETED" || action == "DELETE") {
                    if (existingLocal != null) {
                        dao.deletePaymentById(existingLocal.id)
                    }
                    return@withContext
                }
                val cloudMod = doc.getLong("lastModified") ?: 0L
                if (existingLocal != null && existingLocal.lastModified > cloudMod) {
                    return@withContext
                }
                val loanUuid = doc.getString("loanUuid").orEmpty()
                var parentLoan = dao.getAllLoanCyclesOnce().find { it.uuid == loanUuid }
                if (parentLoan == null && loanUuid.isNotBlank()) {
                    pullSingleEntityFromFirestore(context, db, "LOAN_CYCLE", loanUuid, "UPSERT")
                    parentLoan = dao.getAllLoanCyclesOnce().find { it.uuid == loanUuid }
                }
                if (parentLoan == null) return@withContext

                val amt = doc.getDouble("amountPaid") ?: 0.0
                val rawWeek = (doc.getLong("weekNumber") ?: 0L).toInt()
                val updatedPayment = WeeklyPayment(
                    id = existingLocal?.id ?: 0,
                    loanCycleId = parentLoan.id,
                    amountPaid = amt,
                    paymentDate = doc.getLong("paymentDate") ?: System.currentTimeMillis(),
                    weekNumber = if (amt <= 0.0) 0 else rawWeek,
                    notes = doc.getString("notes").orEmpty(),
                    upiTxnId = doc.getString("upiTxnId"),
                    uuid = entityUuid,
                    lastModified = cloudMod,
                    status = doc.getString("status") ?: "ACTIVE",
                    timeVerificationStatus = doc.getString("timeVerificationStatus") ?: "VERIFIED"
                )
                dao.insertPayment(updatedPayment)
            }
        }
    }

    // =========================================================================
    // FULL BIDIRECTIONAL UUID RECONCILIATION & AUTO-MIGRATION ON FIRST LOGIN
    // =========================================================================

    suspend fun performFullBidirectionalUuidReconciliation(
        context: Context,
        db: AppDatabase,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureFirestoreAuthBestEffort()
        val firestore = getFirestore()
        val defaultFirestore = getDefaultDatabaseOrNull()
        val dao = db.collectionDao()
        val myDeviceId = getDeviceId(context)
        val email = getCurrentUserEmail(context)
        val username = getCurrentUsername(context)

        // First flush any queued offline edits so Firestore has the newest local mutations
        try {
            flushPendingEditsWithConflictCheck(context, db)
        } catch (_: Exception) {}

        // 1. Customers
        onProgress("Reconciling customers by UUID with Cloud Firestore...")
        val localCustomers = dao.getAllCustomersOnce()
        val localCustByUuid = localCustomers.associateBy { it.uuid }.toMutableMap()
        val cloudCustDocs = awaitQueryOrNull(firestore.collection("customers").get(), 8L)?.documents.orEmpty()

        var uploadedCount = 0
        var downloadedCount = 0

        for (doc in cloudCustDocs) {
            val uuid = doc.getString("uuid") ?: doc.id
            val status = doc.getString("status") ?: "ACTIVE"
            val cloudMod = doc.getLong("lastModified") ?: 0L
            val local = localCustByUuid[uuid]

            if (status == "DELETED") {
                if (local != null && cloudMod >= local.lastModified) {
                    dao.deleteCustomer(local)
                    localCustByUuid.remove(uuid)
                }
                continue
            }

            if (local == null || cloudMod > local.lastModified) {
                val merged = Customer(
                    id = local?.id ?: 0,
                    name = doc.getString("name") ?: local?.name.orEmpty(),
                    phone = doc.getString("phone") ?: local?.phone.orEmpty(),
                    phone2 = doc.getString("phone2") ?: local?.phone2.orEmpty(),
                    customOrder = (doc.getLong("customOrder") ?: local?.customOrder?.toLong() ?: 0L).toInt(),
                    collectionDay = doc.getString("collectionDay") ?: local?.collectionDay ?: "Monday",
                    createdAt = doc.getLong("createdAt") ?: local?.createdAt ?: System.currentTimeMillis(),
                    city = doc.getString("city") ?: local?.city.orEmpty(),
                    smsWeeklyReminder = doc.getBoolean("smsWeeklyReminder") ?: false,
                    smsConfirmationOfEntry = doc.getBoolean("smsConfirmationOfEntry") ?: false,
                    autoWeeklySms = doc.getBoolean("autoWeeklySms") ?: false,
                    autoWeeklyWhatsapp = doc.getBoolean("autoWeeklyWhatsapp") ?: false,
                    upiNameAlias = doc.getString("upiNameAlias").orEmpty(),
                    preferredLanguage = doc.getString("preferredLanguage") ?: "English",
                    uuid = uuid,
                    lastModified = cloudMod,
                    syncedLastSavedAt = System.currentTimeMillis(),
                    status = status.ifBlank { "ACTIVE" }
                )
                dao.insertCustomer(merged)
                downloadedCount++
            }
        }

        // Batch upload local customers missing in cloud or genuinely newer than cloud
        val cloudCustModMap = cloudCustDocs.associate { (it.getString("uuid") ?: it.id) to (it.getLong("lastModified") ?: 0L) }
        val customersToUpload = dao.getAllCustomersOnce().filter { c ->
            val cMod = cloudCustModMap[c.uuid]
            cMod == null || c.lastModified > cMod + 2000L
        }
        for (chunk in customersToUpload.chunked(400)) {
            val batch = firestore.batch()
            val mirrorBatch = defaultFirestore?.batch()
            for (c in chunk) {
                val map = customerToFirestoreMap(c, myDeviceId, email)
                batch.set(firestore.collection("customers").document(c.uuid), map)
                if (defaultFirestore != null && mirrorBatch != null) {
                    mirrorBatch.set(defaultFirestore.collection("customers").document(c.uuid), map)
                }
                uploadedCount++
            }
            awaitCommitOrQueue(batch.commit(), 8L)
            mirrorBatch?.commit()
        }

        // 2. Loan Cycles
        onProgress("Reconciling loan contracts with Cloud Firestore...")
        val customersByUuid = dao.getAllCustomersOnce().associateBy { it.uuid }
        val customersById = customersByUuid.values.associateBy { it.id }
        val localLoans = dao.getAllLoanCyclesOnce()
        val localLoansByUuid = localLoans.associateBy { it.uuid }
        val cloudLoanDocs = awaitQueryOrNull(firestore.collection("loan_cycles").get(), 8L)?.documents.orEmpty()

        for (doc in cloudLoanDocs) {
            val uuid = doc.getString("uuid") ?: doc.id
            val status = doc.getString("status") ?: "ACTIVE"
            val cloudMod = doc.getLong("lastModified") ?: 0L
            val local = localLoansByUuid[uuid]

            if (status == "DELETED") {
                if (local != null && cloudMod >= local.lastModified) {
                    dao.deleteLoanCycle(local)
                }
                continue
            }

            val custUuid = doc.getString("customerUuid").orEmpty()
            val parentCustomer = customersByUuid[custUuid] ?: continue

            if (local == null || cloudMod > local.lastModified) {
                val mergedLoan = LoanCycle(
                    id = local?.id ?: 0,
                    customerId = parentCustomer.id,
                    loanAmount = doc.getDouble("loanAmount") ?: 0.0,
                    interestAmount = doc.getDouble("interestAmount") ?: 0.0,
                    weeklyAmount = doc.getDouble("weeklyAmount") ?: 0.0,
                    totalWeeks = (doc.getLong("totalWeeks") ?: 10L).toInt(),
                    startDate = doc.getLong("startDate") ?: System.currentTimeMillis(),
                    status = status,
                    notes = doc.getString("notes").orEmpty(),
                    paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                    uuid = uuid,
                    lastModified = cloudMod,
                    deduction = doc.getDouble("deduction") ?: 0.0
                )
                dao.insertLoanCycle(mergedLoan)
                downloadedCount++
            }
        }

        val cloudLoanModMap = cloudLoanDocs.associate { (it.getString("uuid") ?: it.id) to (it.getLong("lastModified") ?: 0L) }
        val loansToUpload = dao.getAllLoanCyclesOnce().filter { l ->
            val cMod = cloudLoanModMap[l.uuid]
            customersById.containsKey(l.customerId) && (cMod == null || l.lastModified > cMod + 2000L)
        }
        for (chunk in loansToUpload.chunked(400)) {
            val batch = firestore.batch()
            val mirrorBatch = defaultFirestore?.batch()
            for (l in chunk) {
                val parentCust = customersById[l.customerId] ?: continue
                val map = loanCycleToFirestoreMap(l, parentCust, myDeviceId, email)
                batch.set(firestore.collection("loan_cycles").document(l.uuid), map)
                if (defaultFirestore != null && mirrorBatch != null) {
                    mirrorBatch.set(defaultFirestore.collection("loan_cycles").document(l.uuid), map)
                }
                uploadedCount++
            }
            awaitCommitOrQueue(batch.commit(), 8L)
            mirrorBatch?.commit()
        }

        // 3. Weekly Payments
        onProgress("Reconciling installment receipts with Cloud Firestore...")
        val loansByUuidAfter = dao.getAllLoanCyclesOnce().associateBy { it.uuid }
        val loansByIdAfter = loansByUuidAfter.values.associateBy { it.id }
        val localPayments = dao.getAllPaymentsOnce()
        val localPaymentsByUuid = localPayments.associateBy { it.uuid }
        val cloudPaymentDocs = awaitQueryOrNull(firestore.collection("weekly_payments").get(), 10L)?.documents.orEmpty()

        for (doc in cloudPaymentDocs) {
            val uuid = doc.getString("uuid") ?: doc.id
            val status = doc.getString("status") ?: "ACTIVE"
            val cloudMod = doc.getLong("lastModified") ?: 0L
            val local = localPaymentsByUuid[uuid]

            if (status == "DELETED") {
                if (local != null && cloudMod >= local.lastModified) {
                    dao.deletePaymentById(local.id)
                }
                continue
            }

            val loanUuid = doc.getString("loanUuid").orEmpty()
            val parentLoan = loansByUuidAfter[loanUuid] ?: continue

            if (local == null || cloudMod > local.lastModified) {
                val amt = doc.getDouble("amountPaid") ?: 0.0
                val rawWk = (doc.getLong("weekNumber") ?: 0L).toInt()
                val mergedPay = WeeklyPayment(
                    id = local?.id ?: 0,
                    loanCycleId = parentLoan.id,
                    amountPaid = amt,
                    paymentDate = doc.getLong("paymentDate") ?: System.currentTimeMillis(),
                    weekNumber = if (amt <= 0.0) 0 else rawWk,
                    notes = doc.getString("notes").orEmpty(),
                    upiTxnId = doc.getString("upiTxnId"),
                    uuid = uuid,
                    lastModified = cloudMod,
                    status = status,
                    timeVerificationStatus = doc.getString("timeVerificationStatus") ?: "VERIFIED"
                )
                dao.insertPayment(mergedPay)
                downloadedCount++
            }
        }

        val cloudPayModMap = cloudPaymentDocs.associate { (it.getString("uuid") ?: it.id) to (it.getLong("lastModified") ?: 0L) }
        val paymentsToUpload = dao.getAllPaymentsOnce().filter { p ->
            val parentLoan = loansByIdAfter[p.loanCycleId]
            val parentCust = parentLoan?.let { customersById[it.customerId] }
            val cMod = cloudPayModMap[p.uuid]
            parentLoan != null && parentCust != null && (cMod == null || p.lastModified > cMod + 2000L)
        }
        for (chunk in paymentsToUpload.chunked(400)) {
            val batch = firestore.batch()
            val mirrorBatch = defaultFirestore?.batch()
            for (p in chunk) {
                val parentLoan = loansByIdAfter[p.loanCycleId] ?: continue
                val parentCust = customersById[parentLoan.customerId] ?: continue
                val map = paymentToFirestoreMap(p, parentLoan, parentCust, myDeviceId, email)
                batch.set(firestore.collection("weekly_payments").document(p.uuid), map)
                if (defaultFirestore != null && mirrorBatch != null) {
                    mirrorBatch.set(defaultFirestore.collection("weekly_payments").document(p.uuid), map)
                }
                uploadedCount++
            }
            awaitCommitOrQueue(batch.commit(), 10L)
            mirrorBatch?.commit()
        }

        val now = System.currentTimeMillis()
        try {
            val devMap = mapOf(
                "deviceId" to myDeviceId,
                "username" to username,
                "userLoginId" to email,
                "lastModified" to now,
                "lastModifiedFormatted" to formatTimestamp(now),
                "lastActiveSessionTime" to now,
                "lastActiveFormatted" to formatTimestamp(now),
                "status" to "online"
            )
            firestore.collection("devices").document(myDeviceId).set(devMap, SetOptions.merge())
            defaultFirestore?.collection("devices")?.document(myDeviceId)?.set(devMap, SetOptions.merge())
            if (uploadedCount > 0) {
                val logUuid = UUID.randomUUID().toString()
                val auditMap = mapOf(
                    "logUuid" to logUuid,
                    "timestamp" to now,
                    "dateTimeFormatted" to formatTimestamp(now),
                    "deviceId" to myDeviceId,
                    "username" to username,
                    "userLoginId" to email,
                    "entityType" to "LEDGER_SYNC",
                    "entityUuid" to logUuid,
                    "customerUuid" to "",
                    "collectionDay" to "ALL",
                    "action" to "FULL_RECONCILE",
                    "summary" to "Uploaded $uploadedCount records to Firestore"
                )
                firestore.collection("audit_logs").document(logUuid).set(auditMap)
                defaultFirestore?.collection("audit_logs")?.document(logUuid)?.set(auditMap)
            }
        } catch (_: Exception) {}

        // Run 6-Month Past History Archiver at most once every 7 days in background
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastArchiveCheck = prefs.getLong(KEY_LAST_ARCHIVE_CHECK_AT, 0L)
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - lastArchiveCheck >= sevenDaysMs) {
            prefs.edit().putLong(KEY_LAST_ARCHIVE_CHECK_AT, System.currentTimeMillis()).apply()
            scope.launch {
                try {
                    archiveSettledLoansOlderThan6Months(context, db)
                } catch (_: Exception) {}
            }
        }

        // Reconcile cross-device User Settings, SMS templates, Favorites & UPI QR Code
        try {
            reconcileUserSettingsWithFirestore(context)
        } catch (e: Exception) {
            Log.w(TAG, "User settings reconciliation notice: ${e.message}")
        }

        val finalCustCount = dao.getAllCustomersOnce().size
        return@withContext "Firestore Synced ($finalCustCount borrowers • ↑$uploadedCount ↓$downloadedCount)"
    }

    // =========================================================================
    // 6-MONTH SETTLED LOAN ARCHIVER (FIRESTORE -> FIREBASE STORAGE .MD)
    // =========================================================================

    suspend fun archiveSettledLoansOlderThan6Months(
        context: Context,
        db: AppDatabase
    ) = withContext(Dispatchers.IO) {
        val dao = db.collectionDao()
        val firestore = getFirestore()
        val storage = getStorage()
        val now = System.currentTimeMillis()
        val sixMonthsMs = 180L * 24L * 60L * 60L * 1000L

        val customersById = dao.getAllCustomersOnce().associateBy { it.id }
        val allLoans = dao.getAllLoanCyclesOnce()
        val allPayments = dao.getAllPaymentsOnce()
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)

        for (loan in allLoans) {
            if (loan.status != "PAID" && loan.status != "CLOSED") continue
            val customer = customersById[loan.customerId] ?: continue
            val cyclePayments = allPayments
                .filter { it.loanCycleId == loan.id && it.status.uppercase() != "DELETED" }
                .sortedBy { it.paymentDate }

            val lastPayDate = cyclePayments.maxOfOrNull { it.paymentDate } ?: loan.lastModified
            val completionTime = if (lastPayDate > 0L) lastPayDate else loan.lastModified
            if (completionTime <= 0L || (now - completionTime) < sixMonthsMs) continue

            // Check if already indexed in `past_history_index`
            val indexDocRef = firestore.collection("past_history_index").document(loan.uuid)
            val existingIdx = try {
                Tasks.await(indexDocRef.get(), 5, TimeUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
            if (existingIdx != null && existingIdx.exists()) continue

            // Build structured Markdown report
            val sb = StringBuilder()
            sb.appendLine("# MD Finance — Archived Settled Loan Report")
            sb.appendLine("- **Customer ID**: ${customer.customerCode}")
            sb.appendLine("- **Customer UUID**: ${customer.uuid}")
            sb.appendLine("- **Customer Name**: ${customer.name}")
            sb.appendLine("- **Collection Day**: ${customer.collectionDay}")
            sb.appendLine("- **Phone**: ${customer.phone}")
            sb.appendLine("- **Loan UUID**: ${loan.uuid}")
            sb.appendLine("- **Principal Amount**: ₹${loan.loanAmount.toLong()}")
            sb.appendLine("- **Interest Amount**: ₹${loan.interestAmount.toLong()}")
            sb.appendLine("- **Upfront Deduction**: ₹${loan.deduction.toLong()}")
            sb.appendLine("- **Total Paid**: ₹${loan.paidAmount.toLong()}")
            sb.appendLine("- **From Date**: ${sdf.format(Date(loan.startDate))}")
            sb.appendLine("- **Settled Date**: ${sdf.format(Date(completionTime))}")
            sb.appendLine()
            sb.appendLine("## Paid Installment Receipts")
            sb.appendLine("| Week | Date & Time | Amount Paid | Mode |")
            sb.appendLine("| :--- | :--- | :--- | :--- |")
            cyclePayments.filter { it.amountPaid > 0.0 }.forEachIndexed { idx, p ->
                val wk = if (p.weekNumber > 0) p.weekNumber else (idx + 1)
                sb.appendLine("| Week $wk | ${sdf.format(Date(p.paymentDate))} | ₹${p.amountPaid.toLong()} | ${p.notes.ifBlank { "Cash" }} |")
            }

            val markdownText = sb.toString()
            val storagePath = "past_history/${customer.customerCode}_${customer.uuid}/${loan.uuid}.md"

            try {
                val storageRef = storage.reference.child(storagePath)
                Tasks.await(storageRef.putBytes(markdownText.toByteArray(StandardCharsets.UTF_8)), 10, TimeUnit.SECONDS)

                val indexData = mapOf(
                    "loanUuid" to loan.uuid,
                    "customerUuid" to customer.uuid,
                    "customerCode" to customer.customerCode,
                    "customerName" to customer.name,
                    "collectionDay" to customer.collectionDay,
                    "fromDate" to loan.startDate,
                    "toDate" to completionTime,
                    "loanAmount" to loan.loanAmount,
                    "interestAmount" to loan.interestAmount,
                    "deduction" to loan.deduction,
                    "paidAmount" to loan.paidAmount,
                    "storagePath" to storagePath,
                    "markdownPreview" to markdownText,
                    "archivedAt" to now
                )
                Tasks.await(indexDocRef.set(indexData), 6, TimeUnit.SECONDS)

                // Prune raw payment documents for this >6-month archived loan from Firestore to save quota
                for (p in cyclePayments) {
                    try {
                        firestore.collection("weekly_payments").document(p.uuid).delete()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed archiving >6m loan ${loan.uuid}: ${e.message}")
            }
        }
    }

    /**
     * Fetches archived >6-month Past History Markdown files for a specific customer UUID from
     * Firestore `past_history_index` + Firebase Storage.
     */
    suspend fun fetchArchivedPastHistoryForCustomer(
        customerUuid: String
    ): List<ArchivedPastLoanItem> = withContext(Dispatchers.IO) {
        if (customerUuid.isBlank()) return@withContext emptyList()
        _isFetchingPastArchive.value = true
        try {
            val firestore = getFirestore()
            val storage = getStorage()
            val snap = Tasks.await(
                firestore.collection("past_history_index")
                    .whereEqualTo("customerUuid", customerUuid)
                    .get(),
                8,
                TimeUnit.SECONDS
            )
            val result = mutableListOf<ArchivedPastLoanItem>()
            for (doc in snap.documents) {
                val loanUuid = doc.getString("loanUuid") ?: doc.id
                val storagePath = doc.getString("storagePath").orEmpty()
                var mdText = doc.getString("markdownPreview").orEmpty()

                if (mdText.isBlank() && storagePath.isNotBlank()) {
                    try {
                        val bytes = Tasks.await(
                            storage.reference.child(storagePath).getBytes(512 * 1024),
                            8,
                            TimeUnit.SECONDS
                        )
                        mdText = String(bytes, StandardCharsets.UTF_8)
                    } catch (_: Exception) {}
                }

                result.add(
                    ArchivedPastLoanItem(
                        loanUuid = loanUuid,
                        customerUuid = customerUuid,
                        customerCode = doc.getString("customerCode").orEmpty(),
                        customerName = doc.getString("customerName").orEmpty(),
                        fromDate = doc.getLong("fromDate") ?: 0L,
                        toDate = doc.getLong("toDate") ?: 0L,
                        loanAmount = doc.getDouble("loanAmount") ?: 0.0,
                        interestAmount = doc.getDouble("interestAmount") ?: 0.0,
                        deduction = doc.getDouble("deduction") ?: 0.0,
                        paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                        storagePath = storagePath,
                        markdownContent = mdText
                    )
                )
            }
            result.sortedByDescending { it.fromDate }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching archived past history: ${e.message}")
            emptyList()
        } finally {
            _isFetchingPastArchive.value = false
        }
    }

    // =========================================================================
    // ENTITY -> FIRESTORE MAP SERIALIZERS (WITH PER-COLUMN lastModified SECTION)
    // =========================================================================

    private fun customerToFirestoreMap(
        c: Customer,
        deviceId: String,
        userLoginId: String
    ): Map<String, Any?> {
        return mapOf(
            "uuid" to c.uuid,
            "customerCode" to c.customerCode,
            "name" to c.name,
            "phone" to c.phone,
            "phone2" to c.phone2,
            "customOrder" to c.customOrder,
            "collectionDay" to c.collectionDay,
            "createdAt" to c.createdAt,
            "city" to c.city,
            "smsWeeklyReminder" to c.smsWeeklyReminder,
            "smsConfirmationOfEntry" to c.smsConfirmationOfEntry,
            "autoWeeklySms" to c.autoWeeklySms,
            "autoWeeklyWhatsapp" to c.autoWeeklyWhatsapp,
            "upiNameAlias" to c.upiNameAlias,
            "preferredLanguage" to c.preferredLanguage,
            "status" to c.status,
            "lastModified" to c.lastModified,
            "lastModifiedFormatted" to formatTimestamp(c.lastModified),
            "lastModifiedByDevice" to deviceId,
            "lastModifiedByUser" to userLoginId
        )
    }

    private fun loanCycleToFirestoreMap(
        l: LoanCycle,
        parentCustomer: Customer,
        deviceId: String,
        userLoginId: String
    ): Map<String, Any?> {
        return mapOf(
            "uuid" to l.uuid,
            "customerUuid" to parentCustomer.uuid,
            "customerCode" to parentCustomer.customerCode,
            "customerName" to parentCustomer.name,
            "collectionDay" to parentCustomer.collectionDay,
            "loanAmount" to l.loanAmount,
            "interestAmount" to l.interestAmount,
            "weeklyAmount" to l.weeklyAmount,
            "totalWeeks" to l.totalWeeks,
            "startDate" to l.startDate,
            "status" to l.status,
            "notes" to l.notes,
            "paidAmount" to l.paidAmount,
            "deduction" to l.deduction,
            "lastModified" to l.lastModified,
            "lastModifiedFormatted" to formatTimestamp(l.lastModified),
            "lastModifiedByDevice" to deviceId,
            "lastModifiedByUser" to userLoginId
        )
    }

    private fun paymentToFirestoreMap(
        p: WeeklyPayment,
        parentLoan: LoanCycle,
        parentCustomer: Customer,
        deviceId: String,
        userLoginId: String
    ): Map<String, Any?> {
        return mapOf(
            "uuid" to p.uuid,
            "loanUuid" to parentLoan.uuid,
            "customerUuid" to parentCustomer.uuid,
            "customerCode" to parentCustomer.customerCode,
            "collectionDay" to parentCustomer.collectionDay,
            "amountPaid" to p.amountPaid,
            "paymentDate" to p.paymentDate,
            "weekNumber" to if (p.amountPaid <= 0.0) 0 else p.weekNumber,
            "notes" to p.notes,
            "upiTxnId" to p.upiTxnId,
            "status" to p.status,
            "timeVerificationStatus" to p.timeVerificationStatus,
            "lastModified" to p.lastModified,
            "lastModifiedFormatted" to formatTimestamp(p.lastModified),
            "lastModifiedByDevice" to deviceId,
            "lastModifiedByUser" to userLoginId
        )
    }

    private fun cashLogToFirestoreMap(
        log: CashBalanceLog,
        docKey: String,
        deviceId: String,
        userLoginId: String
    ): Map<String, Any?> {
        return mapOf(
            "uuid" to docKey,
            "date" to log.date,
            "actualCash" to log.actualCash,
            "systemCash" to log.systemCash,
            "collectionAmount" to log.collectionAmount,
            "disbursalAmount" to log.disbursalAmount,
            "expenses" to log.expenses,
            "notes" to log.notes,
            "lastModified" to log.date,
            "lastModifiedFormatted" to formatTimestamp(log.date),
            "lastModifiedByDevice" to deviceId,
            "lastModifiedByUser" to userLoginId
        )
    }

    // =========================================================================
    // CROSS-DEVICE USER SETTINGS, SMS TEMPLATES, FAVORITES & QR CODE SYNC
    // =========================================================================

    private const val KEY_SETTINGS_LAST_MODIFIED = "settings_last_modified_ms"
    private const val KEY_SETTINGS_PENDING_SYNC = "settings_pending_sync"
    private const val KEY_QR_STORAGE_PATH = "qr_storage_path"
    private const val KEY_QR_UPDATED_AT = "qr_updated_at_ms"
    private const val KEY_QR_DECODED_PAYEE = "qr_decoded_payee"
    private const val KEY_FAVORITE_CUSTOMER_UUIDS = "favorite_customer_uuids"

    private val SUPPORTED_TEMPLATE_LANGUAGES = listOf("English", "Tamil", "Hindi", "Telugu")

    fun getUserSettingsDocKey(context: Context): String {
        val email = getCurrentUserEmail(context)
        return if (email.isBlank() || email == "default_shared_ledger") {
            "global_default"
        } else {
            sanitizeGmailKey(email)
        }
    }

    /**
     * Pure LWW conflict resolution helper: returns true if remote settings should overwrite local settings.
     * Latest modified timestamp wins automatically.
     */
    fun shouldApplyRemoteSettings(
        localLastModified: Long,
        remoteLastModified: Long,
        hasLocalPendingSync: Boolean
    ): Boolean {
        if (remoteLastModified <= 0L) return false
        if (remoteLastModified > localLastModified) return true
        if (!hasLocalPendingSync && localLastModified == 0L) return true
        return false
    }

    fun serializeFavoriteUuids(uuids: Set<String>): List<String> {
        return uuids.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun parseFavoriteUuids(raw: Any?): Set<String> {
        return when (raw) {
            is Collection<*> -> raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }.toSet()
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    emptySet()
                } else if (trimmed.startsWith("[")) {
                    try {
                        val arr = JSONArray(trimmed)
                        val set = mutableSetOf<String>()
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i).trim()
                            if (item.isNotBlank()) set.add(item)
                        }
                        set
                    } catch (_: Exception) {
                        trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    }
                } else {
                    trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                }
            }
            else -> emptySet()
        }
    }

    fun buildUserSettingsPayloadMap(
        prefs: android.content.SharedPreferences,
        deviceId: String,
        userEmail: String,
        nowMs: Long
    ): Map<String, Any> {
        val favRaw = prefs.getString(KEY_FAVORITE_CUSTOMER_UUIDS, "") ?: ""
        val favoriteList = serializeFavoriteUuids(parseFavoriteUuids(favRaw))

        val smsTemplatesMap = mutableMapOf<String, String>()
        for (lang in SUPPORTED_TEMPLATE_LANGUAGES) {
            val newLoanVal = if (lang == "English" && prefs.contains("sms_new_loan_template")) {
                prefs.getString("sms_new_loan_template", "") ?: ""
            } else {
                prefs.getString("sms_new_loan_template_$lang", "") ?: ""
            }
            val payVal = if (lang == "English" && prefs.contains("sms_payment_template")) {
                prefs.getString("sms_payment_template", "") ?: ""
            } else {
                prefs.getString("sms_payment_template_$lang", "") ?: ""
            }
            val remVal = if (lang == "English" && prefs.contains("sms_reminder_template")) {
                prefs.getString("sms_reminder_template", "") ?: ""
            } else {
                prefs.getString("sms_reminder_template_$lang", "") ?: ""
            }
            val waVal = if (lang == "English" && prefs.contains("whatsapp_reminder_template")) {
                prefs.getString("whatsapp_reminder_template", "") ?: ""
            } else {
                prefs.getString("whatsapp_reminder_template_$lang", "") ?: ""
            }
            if (newLoanVal.isNotBlank()) smsTemplatesMap["sms_new_loan_template_$lang"] = newLoanVal
            if (payVal.isNotBlank()) smsTemplatesMap["sms_payment_template_$lang"] = payVal
            if (remVal.isNotBlank()) smsTemplatesMap["sms_reminder_template_$lang"] = remVal
            if (waVal.isNotBlank()) smsTemplatesMap["whatsapp_reminder_template_$lang"] = waVal
        }

        return mapOf(
            "businessName" to (prefs.getString("business_name", "MD Finance") ?: "MD Finance"),
            "customAppName" to (prefs.getString("custom_app_name", "MD Finance") ?: "MD Finance"),
            "customAppLogo" to (prefs.getString("custom_app_logo", "Default") ?: "Default"),
            "upiId" to (prefs.getString("upi_id", "9440736893@ptyes") ?: "9440736893@ptyes"),
            "qrRawPayload" to (prefs.getString("qr_raw_payload", "") ?: ""),
            "qrStoragePath" to (prefs.getString(KEY_QR_STORAGE_PATH, "") ?: ""),
            "qrUpdatedAt" to prefs.getLong(KEY_QR_UPDATED_AT, 0L),
            "qrDecodedPayee" to (prefs.getString(KEY_QR_DECODED_PAYEE, "") ?: ""),
            "selectedTheme" to (prefs.getString("selected_theme", "Emerald & Royal Slate") ?: "Emerald & Royal Slate"),
            "fontSizeScale" to prefs.getFloat("font_size_scale", 1.0f).toDouble(),
            "fontStyleKey" to (prefs.getString("font_style_key", "Inter") ?: "Inter"),
            "language" to (prefs.getString("language", "English") ?: "English"),
            "showOverdueDots" to prefs.getBoolean("show_overdue_dots", true),
            "customerSortMode" to (prefs.getString("customer_sort_mode", "MANUAL") ?: "MANUAL"),
            "collectionGroups" to (prefs.getString("collection_groups_list", "Monday,Tuesday,Wednesday,Thursday,Saturday,Sunday mrg,Sunday eve") ?: "Monday,Tuesday,Wednesday,Thursday,Saturday,Sunday mrg,Sunday eve"),
            "smsPaused" to prefs.getBoolean("sms_paused", false),
            "smsReaderPaused" to prefs.getBoolean("sms_reader_paused", false),
            "autoEntryPassing" to prefs.getBoolean("auto_entry_passing", true),
            "statementHeaderTitle" to (prefs.getString("statement_header_title", "ACCOUNT STATEMENT") ?: "ACCOUNT STATEMENT"),
            "statementHeaderSubtitle" to (prefs.getString("statement_header_subtitle", "{business_name}") ?: "{business_name}"),
            "statementCustomerBoxTemplate" to (prefs.getString("statement_customer_box", "") ?: ""),
            "statementLoanBoxTemplate" to (prefs.getString("statement_loan_box", "") ?: ""),
            "statementFooterTemplate" to (prefs.getString("statement_footer_text", "") ?: ""),
            "statementTableColumns" to (prefs.getString("statement_table_columns", com.example.util.StatementGenerator.DEFAULT_STATEMENT_TABLE_COLUMNS) ?: com.example.util.StatementGenerator.DEFAULT_STATEMENT_TABLE_COLUMNS),
            "statementCustomizationCode" to (prefs.getString("statement_customization_code", "") ?: ""),
            "favoriteCustomerUuids" to favoriteList,
            "smsTemplates" to smsTemplatesMap,
            "lastModified" to nowMs,
            "lastModifiedFormatted" to formatTimestamp(nowMs),
            "updatedByDevice" to deviceId,
            "updatedByUser" to userEmail
        )
    }

    /**
     * Schedules a debounced (1.2s) push of user settings, SMS templates, statement columns, and favorite customers to Cloud Firestore.
     */
    fun scheduleUserSettingsPush(context: Context) {
        if (isApplyingRemoteSettings) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_SETTINGS_LAST_MODIFIED, now)
            .putBoolean(KEY_SETTINGS_PENDING_SYNC, true)
            .apply()

        debounceSettingsJob?.cancel()
        debounceSettingsJob = scope.launch {
            delay(1200L)
            try {
                pushUserSettingsToFirestore(appCtx)
            } catch (e: Exception) {
                Log.w(TAG, "Debounced settings push queued for retry: ${e.message}")
            }
        }
    }

    /**
     * Immediately pushes all user settings, SMS/WhatsApp templates, statement templates, transaction box columns,
     * theme, favorite customer UUIDs, and QR metadata to Cloud Firestore `user_settings/{docKey}` and `user_settings/global_default`.
     */
    suspend fun pushUserSettingsToFirestore(context: Context) = withContext(Dispatchers.IO) {
        ensureFirestoreAuthBestEffort()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val myDeviceId = getDeviceId(context)
        val email = getCurrentUserEmail(context)
        val modTime = prefs.getLong(KEY_SETTINGS_LAST_MODIFIED, System.currentTimeMillis()).let {
            if (it > 0L) it else System.currentTimeMillis()
        }
        val payload = buildUserSettingsPayloadMap(prefs, myDeviceId, email, modTime)
        val firestore = getFirestore()
        val defaultFirestore = getDefaultDatabaseOrNull()
        val docKey = getUserSettingsDocKey(context)

        val batch = firestore.batch()
        batch.set(firestore.collection("user_settings").document("global_default"), payload, SetOptions.merge())
        if (docKey != "global_default") {
            batch.set(firestore.collection("user_settings").document(docKey), payload, SetOptions.merge())
        }
        awaitCommitOrQueue(batch.commit(), 8L)

        if (defaultFirestore != null) {
            try {
                val mirrorBatch = defaultFirestore.batch()
                mirrorBatch.set(defaultFirestore.collection("user_settings").document("global_default"), payload, SetOptions.merge())
                if (docKey != "global_default") {
                    mirrorBatch.set(defaultFirestore.collection("user_settings").document(docKey), payload, SetOptions.merge())
                }
                mirrorBatch.commit()
            } catch (_: Exception) {}
        }

        prefs.edit().putBoolean(KEY_SETTINGS_PENDING_SYNC, false).apply()
    }

    /**
     * Uploads the user's custom UPI QR code PNG file to Firebase Storage and records its storage path,
     * updated timestamp, and decoded UPI credentials in Cloud Firestore `user_settings` so all peer devices
     * automatically download and cache the QR code.
     */
    fun uploadQrCodeToFirebaseStorage(
        context: Context,
        qrFile: java.io.File,
        decodedUpiId: String,
        decodedUpiLink: String = "",
        decodedPayeeName: String = "",
        rawPayload: String = "",
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val appCtx = context.applicationContext
        scope.launch {
            try {
                if (!qrFile.exists() || qrFile.length() <= 0L) {
                    onComplete(false, "QR image file does not exist")
                    return@launch
                }
                ensureFirestoreAuthBestEffort()
                val docKey = getUserSettingsDocKey(appCtx)
                val primaryStoragePath = "upi_qr_codes/$docKey/custom_payment_qr.png"
                val globalStoragePath = "upi_qr_codes/global_default/custom_payment_qr.png"
                val bytes = qrFile.readBytes()

                try {
                    val storage = getStorage()
                    Tasks.await(storage.reference.child(primaryStoragePath).putBytes(bytes), 10, TimeUnit.SECONDS)
                    if (primaryStoragePath != globalStoragePath) {
                        try {
                            Tasks.await(storage.reference.child(globalStoragePath).putBytes(bytes), 10, TimeUnit.SECONDS)
                        } catch (_: Exception) {}
                    }
                } catch (storageErr: Exception) {
                    Log.w(TAG, "Firebase Storage QR upload notice (QR payload will still sync via Firestore): ${storageErr.message}")
                }

                val now = System.currentTimeMillis()
                val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                    .putString(KEY_QR_STORAGE_PATH, primaryStoragePath)
                    .putLong(KEY_QR_UPDATED_AT, now)
                    .putLong(KEY_SETTINGS_LAST_MODIFIED, now)
                    .putBoolean(KEY_SETTINGS_PENDING_SYNC, true)

                if (decodedUpiId.isNotBlank()) editor.putString("upi_id", decodedUpiId)
                if (decodedPayeeName.isNotBlank()) editor.putString(KEY_QR_DECODED_PAYEE, decodedPayeeName)
                if (rawPayload.isNotBlank()) editor.putString("qr_raw_payload", rawPayload)
                editor.apply()

                pushUserSettingsToFirestore(appCtx)
                onComplete(true, primaryStoragePath)
            } catch (e: Exception) {
                Log.w(TAG, "Failed uploading QR code: ${e.message}")
                onComplete(false, e.localizedMessage)
            }
        }
    }

    /**
     * Applies a remote Firestore settings map to local SharedPreferences (with `isApplyingRemoteSettings = true`
     * to prevent re-triggering an upload loop) and regenerates/downloads the UPI QR code if updated.
     */
    fun applyRemoteSettingsMapToPrefs(
        context: Context,
        data: Map<String, Any?>,
        remoteLastModified: Long
    ) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val remoteQrPayload = (data["qrRawPayload"] as? String)?.trim().orEmpty()
        isApplyingRemoteSettings = true
        try {
            val editor = prefs.edit()
            (data["businessName"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("business_name", it) }
            (data["customAppName"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("custom_app_name", it) }
            (data["customAppLogo"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("custom_app_logo", it) }
            (data["upiId"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("upi_id", it) }
            (data["qrDecodedPayee"] as? String)?.let { editor.putString(KEY_QR_DECODED_PAYEE, it) }
            if (remoteQrPayload.isNotBlank()) {
                editor.putString("qr_raw_payload", remoteQrPayload)
            }
            (data["selectedTheme"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("selected_theme", it) }
            (data["fontSizeScale"] as? Number)?.toFloat()?.let { editor.putFloat("font_size_scale", it.coerceIn(0.8f, 1.4f)) }
            (data["fontStyleKey"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("font_style_key", it) }
            (data["language"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("language", it) }
            (data["showOverdueDots"] as? Boolean)?.let { editor.putBoolean("show_overdue_dots", it) }
            (data["customerSortMode"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("customer_sort_mode", it) }
            (data["collectionGroups"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("collection_groups_list", it) }
            (data["smsPaused"] as? Boolean)?.let { editor.putBoolean("sms_paused", it) }
            (data["smsReaderPaused"] as? Boolean)?.let { editor.putBoolean("sms_reader_paused", it) }
            (data["autoEntryPassing"] as? Boolean)?.let { editor.putBoolean("auto_entry_passing", it) }
            (data["statementHeaderTitle"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_header_title", it) }
            (data["statementHeaderSubtitle"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_header_subtitle", it) }
            (data["statementCustomerBoxTemplate"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_customer_box", it) }
            (data["statementLoanBoxTemplate"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_loan_box", it) }
            (data["statementFooterTemplate"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_footer_text", it) }
            (data["statementTableColumns"] as? String)?.takeIf { it.isNotBlank() }?.let { editor.putString("statement_table_columns", it) }
            (data["statementCustomizationCode"] as? String)?.let {
                if (it.isNotBlank()) editor.putString("statement_customization_code", it)
            }

            if (data.containsKey("favoriteCustomerUuids")) {
                val favSet = parseFavoriteUuids(data["favoriteCustomerUuids"])
                val jsonArr = JSONArray()
                serializeFavoriteUuids(favSet).forEach { jsonArr.put(it) }
                editor.putString(KEY_FAVORITE_CUSTOMER_UUIDS, jsonArr.toString())
            }

            val templatesMap = data["smsTemplates"] as? Map<*, *>
            if (templatesMap != null) {
                for ((k, v) in templatesMap) {
                    val keyStr = k?.toString().orEmpty()
                    val valStr = v?.toString().orEmpty()
                    if (keyStr.isNotBlank() && valStr.isNotBlank()) {
                        editor.putString(keyStr, valStr)
                        if (keyStr == "sms_new_loan_template_English") editor.putString("sms_new_loan_template", valStr)
                        if (keyStr == "sms_payment_template_English") editor.putString("sms_payment_template", valStr)
                        if (keyStr == "sms_reminder_template_English") editor.putString("sms_reminder_template", valStr)
                        if (keyStr == "whatsapp_reminder_template_English") editor.putString("whatsapp_reminder_template", valStr)
                    }
                }
            }

            if (remoteQrPayload.isNotBlank()) {
                val (generatedFile, _) = com.example.util.UpiQrCodeHelper.saveScannedQrPayload(appCtx, remoteQrPayload)
                if (generatedFile != null && generatedFile.exists()) {
                    editor.putString("qr_image_uri", android.net.Uri.fromFile(generatedFile).toString())
                }
            }

            editor.putLong(KEY_SETTINGS_LAST_MODIFIED, remoteLastModified)
            editor.putBoolean(KEY_SETTINGS_PENDING_SYNC, false)
            editor.commit()
        } finally {
            isApplyingRemoteSettings = false
        }

        onRemoteSettingsAppliedCallback?.invoke()

        // Check if remote UPI QR code image in Firebase Storage needs to be downloaded
        val remoteQrPath = (data["qrStoragePath"] as? String).orEmpty()
        val remoteQrUpdatedAt = (data["qrUpdatedAt"] as? Number)?.toLong() ?: 0L
        val localQrUpdatedAt = prefs.getLong(KEY_QR_UPDATED_AT, 0L)
        val localQrFile = java.io.File(appCtx.filesDir, "custom_payment_qr.png")

        if (remoteQrPayload.isBlank() && remoteQrPath.isNotBlank() && (remoteQrUpdatedAt > localQrUpdatedAt || !localQrFile.exists())) {
            scope.launch {
                downloadQrCodeFromFirebaseStorage(appCtx, remoteQrPath, remoteQrUpdatedAt)
            }
        }
    }

    private suspend fun downloadQrCodeFromFirebaseStorage(
        context: Context,
        storagePath: String,
        remoteQrUpdatedAt: Long
    ) = withContext(Dispatchers.IO) {
        try {
            ensureFirestoreAuthBestEffort()
            val storage = getStorage()
            val bytes = Tasks.await(
                storage.reference.child(storagePath).getBytes(2L * 1024L * 1024L),
                12,
                TimeUnit.SECONDS
            )
            if (bytes != null && bytes.isNotEmpty()) {
                val filesTarget = java.io.File(context.filesDir, "custom_payment_qr.png")
                filesTarget.writeBytes(bytes)
                val cacheTarget = java.io.File(context.cacheDir, "custom_payment_qr.png")
                cacheTarget.writeBytes(bytes)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                isApplyingRemoteSettings = true
                try {
                    prefs.edit()
                        .putString(KEY_QR_STORAGE_PATH, storagePath)
                        .putLong(KEY_QR_UPDATED_AT, remoteQrUpdatedAt)
                        .putString("qr_image_uri", android.net.Uri.fromFile(filesTarget).toString())
                        .commit()
                } finally {
                    isApplyingRemoteSettings = false
                }
                onRemoteSettingsAppliedCallback?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading QR code from Firebase Storage ($storagePath): ${e.message}")
        }
    }

    private fun attachUserSettingsRealtimeListener(
        context: Context,
        onStatusUpdate: (String) -> Unit = {}
    ) {
        try {
            val appCtx = context.applicationContext
            val firestore = getFirestore()
            val myDeviceId = getDeviceId(appCtx)
            firestoreSettingsListener?.remove()
            firestoreSettingsListener = firestore.collection("user_settings")
                .document("global_default")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val updatedByDevice = snapshot.getString("updatedByDevice").orEmpty()
                    val remoteLastModified = snapshot.getLong("lastModified") ?: 0L
                    if (updatedByDevice == myDeviceId) return@addSnapshotListener

                    val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val localLastModified = prefs.getLong(KEY_SETTINGS_LAST_MODIFIED, 0L)
                    val hasPending = prefs.getBoolean(KEY_SETTINGS_PENDING_SYNC, false)

                    if (shouldApplyRemoteSettings(localLastModified, remoteLastModified, hasPending)) {
                        val map = snapshot.data ?: return@addSnapshotListener
                        applyRemoteSettingsMapToPrefs(appCtx, map, remoteLastModified)
                        onStatusUpdate("Synced app settings & templates from Cloud")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed attaching user_settings listener: ${e.message}")
        }
    }

    /**
     * Reconciles local vs. cloud `user_settings` using Latest Modified Timestamp Wins (`lastModified`).
     */
    suspend fun reconcileUserSettingsWithFirestore(context: Context) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val firestore = getFirestore()
        val docKey = getUserSettingsDocKey(appCtx)

        val globalSnap = awaitQueryOrNull(firestore.collection("user_settings").document("global_default").get(), 6L)
        val userSnap = if (docKey != "global_default") {
            awaitQueryOrNull(firestore.collection("user_settings").document(docKey).get(), 6L)
        } else null

        val bestSnap = listOfNotNull(globalSnap, userSnap)
            .filter { it.exists() }
            .maxByOrNull { it.getLong("lastModified") ?: 0L }

        val localLastModified = prefs.getLong(KEY_SETTINGS_LAST_MODIFIED, 0L)
        val hasLocalPending = prefs.getBoolean(KEY_SETTINGS_PENDING_SYNC, false)

        if (bestSnap == null || !bestSnap.exists()) {
            // No cloud settings yet — seed Firestore with current device settings
            if (localLastModified == 0L) {
                prefs.edit().putLong(KEY_SETTINGS_LAST_MODIFIED, System.currentTimeMillis()).apply()
            }
            pushUserSettingsToFirestore(appCtx)
            // Also upload existing local QR file to Firebase Storage if not uploaded yet
            val existingQrFile = java.io.File(appCtx.filesDir, "custom_payment_qr.png")
            val existingQrStoragePath = prefs.getString(KEY_QR_STORAGE_PATH, "").orEmpty()
            if (existingQrFile.exists() && existingQrStoragePath.isBlank()) {
                uploadQrCodeToFirebaseStorage(
                    context = appCtx,
                    qrFile = existingQrFile,
                    decodedUpiId = prefs.getString("upi_id", "").orEmpty(),
                    rawPayload = prefs.getString("qr_raw_payload", "").orEmpty(),
                    decodedPayeeName = prefs.getString(KEY_QR_DECODED_PAYEE, "").orEmpty()
                )
            }
            return@withContext
        }

        val remoteMod = bestSnap.getLong("lastModified") ?: 0L
        if (shouldApplyRemoteSettings(localLastModified, remoteMod, hasLocalPending)) {
            bestSnap.data?.let { data ->
                applyRemoteSettingsMapToPrefs(appCtx, data, remoteMod)
            }
        } else if (hasLocalPending || localLastModified > remoteMod) {
            pushUserSettingsToFirestore(appCtx)
        }
    }
}

