package and06.geonotes

import android.content.Context
import androidx.room.*
import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import java.text.DateFormat

@Parcelize
@Entity(tableName = "projekte")
data class Projekt(@PrimaryKey val id: Long, var beschreibung: String?): Parcelable{
    fun getDescription(): String {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
        val dateString = dateFormat.format(id)
        return if (beschreibung.isNullOrEmpty()) dateString else "$beschreibung ($dateString)"
    }
}

@Entity(tableName = "locations", primaryKeys = ["latitude", "longitude"])
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val provider: String
)
@Parcelize
@Entity(
    tableName = "notizen",
    indices = [Index(value = ["projektId"]), Index(value = ["latitude", "longitude"])],
    foreignKeys = [ForeignKey(
        entity = Projekt::class,
        parentColumns = ["id"],
        childColumns = ["projektId"]
    ),
        ForeignKey(
            entity = Location::class,
            parentColumns = ["latitude", "longitude"],
            childColumns = ["latitude", "longitude"]
        )]
)
data class Notiz(
    @PrimaryKey(autoGenerate = true) var id: Long? = null,
    val projektId: Long,
    val latitude: Double,
    val longitude: Double,
    var thema: String,
    var notiz: String
): Parcelable

@Dao
interface ProjekteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProjekt(projekt: Projekt): Long

    @Query("SELECT * FROM projekte")
    fun getProjekte(): List<Projekt>

    @Update
    fun updateProjekt(projekt: Projekt)

    @Query("SELECT * FROM projekte where id =:id")
    fun getProjekt(id: Long) : Projekt

    @Query("DELETE from projekte where id =:id")
    fun deleteProjekt(id: Long)
}

@Dao
interface LocationsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLocation(location: Location): Long

    @Query("SELECT * FROM locations")
    fun getLocations(): List<Location>
}

@Dao
interface NotizenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotiz(notiz: Notiz): Long

    @Query("SELECT * from notizen")
    fun getNotizen(): List<Notiz>

    @Query("SELECT * from notizen where projektId=:projektId")
    fun getNotizen(projektId: Long): List<Notiz>

    @Query("SELECT * from notizen where projektId = :projektId AND id < :notizId ORDER BY id ASC")
    fun getPreviousNotizen(notizId: Long, projektId: Long): List<Notiz>

    @Query("SELECT * from notizen where projektId = :projektId AND id > :notizId ORDER BY id ASC")
    fun getNextNotizen(notizId: Long, projektId: Long): List<Notiz>

    @Query("SELECT * from notizen where id =:id")
    fun getNotiz(id: Long): Notiz

    @Query("DELETE from notizen where id =:id")
    fun deleteNotiz(id: Long)
}

@Database(entities = [Projekt::class, Notiz::class, Location::class], version = 1)
abstract class GeoNotesDatabase : RoomDatabase() {
    companion object {
        private var INSTANCE: GeoNotesDatabase? = null
        fun getInstance(context: Context): GeoNotesDatabase {
            if (INSTANCE == null) {
                INSTANCE =
                    Room.databaseBuilder(context, GeoNotesDatabase::class.java, "GeoNotesDatabase")
                        .build()
            }
            return INSTANCE as GeoNotesDatabase
        }
    }

    abstract fun projekteDao(): ProjekteDao
    abstract fun locationsDao(): LocationsDao
    abstract fun notizenDao(): NotizenDao
}



