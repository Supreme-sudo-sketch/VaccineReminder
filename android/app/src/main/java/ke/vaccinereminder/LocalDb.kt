package ke.vaccinereminder

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

// The phone's own copy of the children it has shown, so their records still open when the server
// cannot be reached. The server stays the source of truth: every successful load replaces the copy.
//
// owner says whose copy a row is: "worker" for the health worker signed in on this phone, or
// "caregiver:<phone>|<dob>|<name>" for one remembered caregiver sign-in. The same child can be saved
// for both, so rows are keyed by owner and child id together (cacheKey).

const val WORKER_OWNER = "worker"

fun caregiverOwner(who: SavedLookup) = "caregiver:${who.phone}|${who.dob}|${who.childName.trim().lowercase()}"

fun cacheKey(owner: String, childId: Int) = "$owner#$childId"

@Entity(tableName = "children", indices = [Index("owner")])
data class ChildEntity(
    @PrimaryKey val cacheKey: String,
    val owner: String,
    val id: Int,
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val dob: String,
    val language: String,
    val facility: String,
    val nearestHospital: String,
    val consent: Boolean,
    // As the server last reported them; used for list rows saved without their doses.
    val nextDueDate: String?,
    val overdueCount: Int,
    val givenCount: Int,
    val totalCount: Int,
    // When this copy came from the server, in milliseconds since 1970.
    val savedAt: Long,
)

// One vaccine dose in a child's schedule. Its status is not stored: it is worked out from the dates
// when read, so a dose saved as "upcoming" shows as "overdue" once its date has passed.
@Entity(
    tableName = "doses",
    primaryKeys = ["childKey", "code"],
    foreignKeys = [
        ForeignKey(entity = ChildEntity::class, parentColumns = ["cacheKey"], childColumns = ["childKey"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("childKey"), Index("dueDate")],
)
data class DoseEntity(
    val childKey: String,
    val code: String,
    // Place in the schedule, to read the doses back in order.
    val position: Int,
    val name: String,
    val milestone: String,
    val dueDate: String,
    val givenDate: String?,
)

data class ChildWithDoses(
    @Embedded val child: ChildEntity,
    @Relation(parentColumn = "cacheKey", entityColumn = "childKey") val doses: List<DoseEntity>,
)

@Dao
abstract class ChildDao {
    // Upsert updates a row in place. (REPLACE would delete it first, and the delete would take the
    // child's doses with it.)
    @Upsert
    abstract suspend fun upsertChildren(children: List<ChildEntity>)

    @Upsert
    abstract suspend fun upsertDoses(doses: List<DoseEntity>)

    @Query("DELETE FROM doses WHERE childKey = :childKey")
    abstract suspend fun deleteDoses(childKey: String)

    @Query("SELECT * FROM children WHERE cacheKey = :cacheKey")
    abstract suspend fun find(cacheKey: String): ChildEntity?

    @Transaction
    @Query("SELECT * FROM children WHERE cacheKey = :cacheKey")
    abstract suspend fun withDoses(cacheKey: String): ChildWithDoses?

    @Transaction
    @Query("SELECT * FROM children WHERE owner = :owner ORDER BY childName")
    abstract suspend fun byOwner(owner: String): List<ChildWithDoses>

    @Query("SELECT COUNT(*) FROM children WHERE owner = :owner")
    abstract suspend fun count(owner: String): Int

    // The health worker's list, searched and filtered the way the server does it: by name, caregiver
    // or phone digits; "overdue" = a dose not given whose date has passed; "due" = one due in the next
    // 7 days. Newest first for "all", soonest due first otherwise.
    @Transaction
    @Query(
        """
        SELECT * FROM children
        WHERE owner = :owner
          AND (:query = ''
               OR childName LIKE '%' || :query || '%'
               OR caregiverName LIKE '%' || :query || '%'
               OR (:digits != '' AND phone LIKE '%' || :digits || '%'))
          AND (:filter = 'all'
               OR (:filter = 'overdue' AND (nextDueDate < :today OR EXISTS (
                     SELECT 1 FROM doses d WHERE d.childKey = children.cacheKey AND d.givenDate IS NULL AND d.dueDate < :today)))
               OR (:filter = 'due' AND (nextDueDate BETWEEN :today AND :weekEnd OR EXISTS (
                     SELECT 1 FROM doses d WHERE d.childKey = children.cacheKey AND d.givenDate IS NULL
                       AND d.dueDate BETWEEN :today AND :weekEnd))))
        ORDER BY CASE WHEN :filter = 'all' THEN -id ELSE 0 END, nextDueDate, id
        LIMIT 200
        """,
    )
    abstract suspend fun search(owner: String, query: String, digits: String, filter: String, today: String, weekEnd: String): List<ChildWithDoses>

    @Query("DELETE FROM children WHERE cacheKey = :cacheKey")
    abstract suspend fun delete(cacheKey: String)

    @Query("DELETE FROM children WHERE owner = :owner")
    abstract suspend fun deleteOwner(owner: String)

    // Drops the owner's children the server no longer has.
    @Query("DELETE FROM children WHERE owner = :owner AND id NOT IN (:keepIds)")
    abstract suspend fun deleteOwnerExcept(owner: String, keepIds: List<Int>)

    // Drops the copies for caregiver sign-ins this phone no longer remembers.
    @Query("DELETE FROM children WHERE owner LIKE 'caregiver:%' AND owner NOT IN (:keepOwners)")
    abstract suspend fun deleteCaregiversExcept(keepOwners: List<String>)

    @Query("DELETE FROM children")
    abstract suspend fun deleteAll()

    // A full record, with its schedule.
    @Transaction
    open suspend fun saveFull(child: ChildEntity, doses: List<DoseEntity>) {
        upsertChildren(listOf(child))
        deleteDoses(child.cacheKey)
        upsertDoses(doses)
    }

    // List rows come without doses or the nearest hospital: what a full load saved for those stays.
    @Transaction
    open suspend fun saveSummaries(children: List<ChildEntity>) {
        upsertChildren(children.map { c -> find(c.cacheKey)?.let { old -> c.copy(nearestHospital = old.nearestHospital) } ?: c })
    }

    // Replaces everything saved for one owner with what the server just sent.
    @Transaction
    open suspend fun replaceOwner(owner: String, records: List<Pair<ChildEntity, List<DoseEntity>>>) {
        deleteOwner(owner)
        records.forEach { (child, doses) -> saveFull(child, doses) }
    }
}

@Database(entities = [ChildEntity::class, DoseEntity::class], version = 1, exportSchema = true)
abstract class VaccineDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao

    companion object {
        @Volatile
        private var instance: VaccineDatabase? = null

        fun get(context: Context): VaccineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VaccineDatabase::class.java, "vaccine-cache.db")
                    // Only a copy of what the server holds, so a new version can simply start empty.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
