package and06.geonotes

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt


class NoteMapActivity : AppCompatActivity() {

    companion object {
        val AKTUELLE_NOTIZ_ID = "aktuelle_notiz_id"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notemap)
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        val basePath = File(cacheDir.absolutePath, "osmdroid")
        osmConfig.osmdroidBasePath = basePath
        val tileCache = File(osmConfig.osmdroidBasePath, "tile")
        osmConfig.osmdroidTileCache = tileCache
        val map = findViewById<MapView>(R.id.mapview)
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)

        val extras = intent.extras ?:return
        val notizen = intent.getParcelableArrayListExtra<Notiz>(GatherActivity.NOTIZEN)
        notizen?.forEach {
            val marker = Marker(map)
            marker.position = GeoPoint(it.latitude, it.longitude)
            marker.title = it.thema
            marker.subDescription = it.notiz
            marker.snippet = decimalToSexagesimal(it.latitude, it.longitude)
            // marker.icon = getDrawable(R.drawable.crosshair)
            // marker.setAnchor(0.5f, 0.5f)
            map.overlays.add(marker)

        }

        val controller = map.controller
        var indexAktuelleNotiz = extras.getInt(GatherActivity.INDEX_AKTUELLE_NOTIZ)
        val aktuelleNotiz = notizen?.get(indexAktuelleNotiz)
        controller.setCenter(GeoPoint(aktuelleNotiz!!.latitude, aktuelleNotiz.longitude))
        controller.setZoom(10.0)

        map.mapOrientation = 45.0f

        val buttonPrevious = findViewById<ImageButton>(R.id.button_previous_notiz)
        buttonPrevious.setOnClickListener {
            indexAktuelleNotiz = if (indexAktuelleNotiz == 0) notizen.size - 1
            else indexAktuelleNotiz - 1
            val notiz = notizen.get(indexAktuelleNotiz)
            controller.setCenter(GeoPoint(notiz.latitude, notiz.longitude))
        }

        val buttonNext = findViewById<ImageButton>(R.id.button_next_notiz)
        buttonNext.setOnClickListener {
            indexAktuelleNotiz = (indexAktuelleNotiz + 1) % notizen.size
            val notiz = notizen.get(indexAktuelleNotiz)
            controller.setCenter(GeoPoint(notiz.latitude, notiz.longitude))
        }

        // zurück Taste handler:
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val pushIntent = getIntent()
                pushIntent.putExtra(AKTUELLE_NOTIZ_ID, notizen.get(indexAktuelleNotiz).id)
                setResult(RESULT_OK, pushIntent)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val map = findViewById<MapView>(R.id.mapview)
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        val map = findViewById<MapView>(R.id.mapview)
        map.onPause()
    }

    fun decimalToSexagesimal(latitude: Double, longitude: Double): String {
        val latDegrees = latitude.toInt()
        val lonDegrees = longitude.toInt()
        val latTempMinutes = abs((latitude - latDegrees) * 60)
        val lonTempMinutes = abs((longitude - lonDegrees) * 60)

        val latMinutes = latTempMinutes.toInt()
        val lonMinutes = lonTempMinutes.toInt()
        val latTempSeconds = abs((latTempMinutes - latMinutes) * 60)
        val lonTempSeconds = abs((lonTempMinutes - lonMinutes) * 60)
        // auf drei Stellen runden
        val latSeconds = (latTempSeconds * 1000).roundToInt() / 1000.0
        val lonSeconds = (lonTempSeconds * 1000).roundToInt() / 1000.0

        val latDegreesString = abs(latDegrees).toString() + if (latitude < 0) "°S " else "°N "
        val lonDegreesString = abs(lonDegrees).toString() + if (longitude < 0) "°W " else "°O "

        return "$lonDegreesString$lonMinutes\' $lonSeconds\'\' / $latDegreesString$latMinutes\' $latSeconds\'\'"
    }

}