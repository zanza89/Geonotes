package and06.geonotes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

class GatherActivity : AppCompatActivity() {

    companion object {
        val NOTIZEN = "notizen"
        val INDEX_AKTUELLE_NOTIZ = "index_aktuelle_notiz"
        val PREFERENCES = "preferences"
        val ID_ZULETZT_GEOEFFNETES_PROJEKT = "zuletzt_geoeffnetes_projekt"
        val AKTUELLE_NOTIZ = "aktuelle_notiz"
        val AKTUELLES_PROJEKT = "aktuelle_projekt"
    }

    var minTime = 4000L // in Millisekunden
    var minDistance = 25.0f // in Metern
    var aktuellesProjekt = Projekt(Date().getTime(), "")
    var aktuelleNotiz: Notiz? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gather)
        requestPermissions(
            arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 0
        )
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val textView = findViewById<TextView>(R.id.textview_aktuelles_projekt)
        textView.append(aktuellesProjekt.getDescription())

        if (savedInstanceState != null) {
            aktuellesProjekt = savedInstanceState.getParcelable(AKTUELLES_PROJEKT)!!
            val notiz: Notiz? = savedInstanceState.getParcelable(AKTUELLE_NOTIZ)
            if (notiz != null) aktuelleNotiz = notiz
            return
        }

        val projektId = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(
            ID_ZULETZT_GEOEFFNETES_PROJEKT, 0
        )
        if (projektId != 0L) {
            val database = GeoNotesDatabase.getInstance(this)
            CoroutineScope(Dispatchers.Main).launch {
                var projekt: Projekt? = null
                withContext(Dispatchers.IO) {
                    projekt = database.projekteDao().getProjekt(projektId)
                }
                if (projekt != null) {
                    with(AlertDialog.Builder(this@GatherActivity)) {
                        setMessage("Projekt \"${projekt?.getDescription()}\" weiter bearbeiten?")
                        setPositiveButton("Ja", DialogInterface.OnClickListener { dialog, id ->
                            // ausgew??hltes Projekt ??bernehmen und anzeigen:
                            aktuellesProjekt = projekt!!
                            val textView = findViewById<TextView>(R.id.textview_aktuelles_projekt)
                            textView.text =
                                getString(R.string.aktuelles_projekt_prefix) + aktuellesProjekt.getDescription()
                        })
                        setNegativeButton("Nein", DialogInterface.OnClickListener { dialog, id ->
                            // ist nichts zu tun, aber setNegativeButton muss deklariert sein
                        })
                        show()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(locationListener)
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<ToggleButton>(R.id.togglebutton_lokalisierung).isChecked()) {
            val spinner = findViewById<Spinner>(R.id.spinner_provider)
            val provider = spinner.selectedItem as String
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                locationManager.requestLocationUpdates(
                    provider, minTime, minDistance, locationListener
                )
            } catch (ex: SecurityException) {
                Log.e(
                    javaClass.simpleName,
                    "Erforderliche Berechtigung ${ex.toString()} nicht erteilt"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(locationListener)
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putLong(
            ID_ZULETZT_GEOEFFNETES_PROJEKT,
            aktuellesProjekt.id
        ).apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(AKTUELLES_PROJEKT, aktuellesProjekt)
        if (aktuelleNotiz != null) {
            outState.putParcelable(AKTUELLE_NOTIZ, aktuelleNotiz)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode != 0) return
        val extras = data?.getExtras() ?: return
        val notizId = extras.getLong(NoteMapActivity.AKTUELLE_NOTIZ_ID)
        Log.d(javaClass.simpleName, "Notiz-ID: $notizId")
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notiz: Notiz? = null
            withContext(Dispatchers.IO) {
                notiz = database.notizenDao().getNotiz(notizId)
            }
            if (notiz != null) {
                aktuelleNotiz = notiz
                findViewById<TextView>(R.id.edittext_thema).text = aktuelleNotiz?.thema
                findViewById<TextView>(R.id.edittext_notiz).text = aktuelleNotiz?.notiz
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        val locationIndex = permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (grantResults[locationIndex] == PackageManager.PERMISSION_GRANTED) {
            initSpinnerProviders()
        } else {
            Toast.makeText(
                this,
                "Standortbestimmung nicht erlaubt -- nur ungeschr??nkte Funktionalit??tverf??gbar",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_gather_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.item_foot, R.id.item_bicycle, R.id.item_car, R.id.item_fast, R.id.item_stop -> {
                item.setChecked(true)
                val map = hashMapOf<Int, Pair<Long, Float>>(
                    R.id.item_foot to (9000L to 10.0f),
                    R.id.item_bicycle to (4000L to 25.0f),
                    R.id.item_car to (4000L to 50.0f),
                    R.id.item_fast to (4000L to 100.0f),
                    R.id.item_stop to (60000L to 1.0f)
                )
                val pair = map.get(id) ?: return true
                minTime = pair.first
                minDistance = pair.second
                Toast.makeText(
                    this,
                    "Neues GPS-Intervall \"${item.title}\". Bitte Lokalisierung neu starten.",
                    Toast.LENGTH_LONG
                ).show()
                return true
            }

            R.id.menu_projekt_bearbeiten -> {
                openProjektBearbeitenDialog()
            }
            R.id.menu_projekt_auswaehlen -> {
                openProjektAuswaehlenDialog()
            }
            R.id.menu_notiz_loeschen -> {
                notizLoeschen()
            }
            R.id.menu_projekt_versenden -> {
                projektVersenden()
            }
            R.id.menu_osm_oeffnen -> {
                openWebView()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openProjektAuswaehlenDialog() {
        Log.d(javaClass.simpleName, "\"Projekt ausw??hlen\" ausgew??hlt")
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var projekte: List<Projekt>? = null
            withContext(Dispatchers.IO) {
                projekte = database.projekteDao().getProjekte()
            }
            if (projekte.isNullOrEmpty()) {
                Toast.makeText(
                    this@GatherActivity, "Noch keine Projekte vorhanden", Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val items = ArrayList<CharSequence>()
            projekte?.forEach {
                items.add(it.getDescription())
            }
            with(AlertDialog.Builder(this@GatherActivity)) {
                setTitle("Projekte ausw??hlen")
                setItems(items.toArray(emptyArray()),
                    DialogInterface.OnClickListener { dialog, id ->
                        // aktuelles Projekt auf ausgew??hltes Projekt setzen:
                        aktuellesProjekt = projekte?.get(id)!!
                        val textView = findViewById<TextView>(R.id.textview_aktuelles_projekt)
                        textView.text =
                            getString(R.string.aktuelles_projekt_prefix) + aktuellesProjekt.getDescription()
                        // Notiz zum Projekt anzeigen:
                        CoroutineScope(Dispatchers.Main).launch {
                            withContext(Dispatchers.IO) {
                                val notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
                                aktuelleNotiz = notizen.last()

                            }
                            findViewById<TextView>(R.id.edittext_thema).text = aktuelleNotiz?.thema
                            findViewById<TextView>(R.id.edittext_notiz).text = aktuelleNotiz?.notiz
                        }
                    })
                show()
            }
        }
    }

    fun openProjektBearbeitenDialog() {
        Log.d(javaClass.simpleName, "\"Projekt bearbeiten\" ausgew??hlt")
        val dialogView = layoutInflater.inflate(R.layout.dialog_projekt_bearbeiten, null)
        val textView =
            dialogView.findViewById<TextView>(R.id.textview_dialog_projektname_bearbeiten)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
        textView.text = dateFormat.format(Date(aktuellesProjekt.id))
        val editText =
            dialogView.findViewById<TextView>(R.id.edittext_dialog_projektname_bearbeiten)
        editText.text = aktuellesProjekt.beschreibung
        val database = GeoNotesDatabase.getInstance(this)
        with(AlertDialog.Builder(this)) {
            setView(dialogView)
            setTitle("projektbeschreibung eingeben/??ndern")
            setPositiveButton("??bernehmen", DialogInterface.OnClickListener { dialog, id ->
                // Projektbeschreibung ??ndern:
                aktuellesProjekt.beschreibung = editText.text.toString().trim()
                findViewById<TextView>(R.id.textview_aktuelles_projekt).text =
                    getString(R.string.aktuelles_projekt_prefix) + aktuellesProjekt.getDescription()
                // Projekt-Update in datenbank:
                GlobalScope.launch {
                    database.projekteDao().updateProjekt(aktuellesProjekt)
                    Log.d(javaClass.simpleName, "Update Projekt: $aktuellesProjekt")
                    val projekte = database.projekteDao().getProjekte()
                    projekte.forEach {
                        Log.d(javaClass.simpleName, "Projekt $it")
                    }
                }
            })
            setNegativeButton("Abbrechen", DialogInterface.OnClickListener { dialog, id -> })
            show()
        }
    }

    fun notizLoeschen() {
        Log.d(javaClass.simpleName, "Button l??schen wurde get??tigt")
        val textViewThema = findViewById<TextView>(R.id.edittext_thema)
        val textViewNotiz = findViewById<TextView>(R.id.edittext_notiz)
        if (aktuelleNotiz == null) {
            return
        }
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notizen: List<Notiz>? = null
            withContext(Dispatchers.IO) {
                notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
                if (notizen!!.size > 1) {
                    database.notizenDao().deleteNotiz(aktuelleNotiz?.id!!)
                    aktuelleNotiz = null
                    textViewThema.text = ""
                    textViewNotiz.text = ""
                } else {
                    Log.d(javaClass.simpleName, "weniger als eine Notiz")
                    CoroutineScope(Dispatchers.Main).launch {
                        with(AlertDialog.Builder(this@GatherActivity)) {
                            setMessage("L??schen der letzten Notiz l??scht das Projekt. Fortfahren?")
                            setPositiveButton("OK", DialogInterface.OnClickListener { dialog, id ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    withContext(Dispatchers.IO) {
                                        database.notizenDao().deleteNotiz(aktuelleNotiz?.id!!)
                                        database.projekteDao().deleteProjekt(aktuellesProjekt.id)
                                        aktuellesProjekt = Projekt(Date().getTime(), "")
                                        aktuelleNotiz = null
                                    }
                                    val textView =
                                        findViewById<TextView>(R.id.textview_aktuelles_projekt)
                                    textViewThema.text = ""
                                    textViewNotiz.text = ""
                                    textView.text =
                                        getString(R.string.aktuelles_projekt_prefix) + aktuellesProjekt.getDescription()
                                }
                            })
                            setNegativeButton(
                                "Abbrechen",
                                DialogInterface.OnClickListener { dialog, id ->
                                    return@OnClickListener
                                })
                            show()
                        }
                    }
                }
            }
        }
    }

    fun projektVersenden() {
        // Verf??gbarkeit von External Storage ??berpr??fen (ob es gemounted ist)
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "External Storage nicht verf??gbar", Toast.LENGTH_LONG).show()
            return
        }
        val database = GeoNotesDatabase.getInstance(this)
        GlobalScope.launch {
            val notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
            if (notizen.isNullOrEmpty()) {
                Toast.makeText(this@GatherActivity, "keine Notizen vorhanden", Toast.LENGTH_LONG).show()
                return@launch
            }
            val generator = GpxGenerator()
            val uri = generator.createGpxFile(this@GatherActivity, notizen, aktuellesProjekt.getDescription())
            with(Intent(Intent.ACTION_SEND)) {
                type = "text/xml"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("xxx@test.com"))
                putExtra(Intent.EXTRA_SUBJECT, aktuellesProjekt.getDescription())
                putExtra(Intent.EXTRA_STREAM, uri)
                startActivity(Intent.createChooser(this, "Mail versenden"))
            }
        }
    }

    fun onButtonVorherigeNotizClick(view: View?) {
        val textViewThema = findViewById<TextView>(R.id.edittext_thema)
        val textViewNotiz = findViewById<TextView>(R.id.edittext_notiz)
        if (aktuelleNotiz == null) {
            if (textViewThema.text.isNotEmpty() || textViewNotiz.text.isNotEmpty()) {
                Toast.makeText(this, "Notiz wurde noch nicht gespeichert", Toast.LENGTH_LONG)
                    .show() //Fall 1
                return
            }
        }
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notizen: List<Notiz>? = null
            withContext(Dispatchers.IO) {
                if (aktuelleNotiz == null) { //Fall 2
                    notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
                } else { // Fall 3
                    if (!aktuelleNotiz?.thema.toString()
                            .contentEquals(textViewThema.text) || !aktuelleNotiz?.notiz.toString()
                            .contentEquals(textViewNotiz.text)
                    ) {
                        aktuelleNotiz?.thema = textViewThema.text.toString()
                        aktuelleNotiz?.notiz = textViewNotiz.text.toString()
                        database.notizenDao().insertNotiz(aktuelleNotiz!!)
                    }
                    notizen = database.notizenDao()
                        .getPreviousNotizen(aktuelleNotiz?.id!!, aktuellesProjekt.id)
                }
            }
            if (!notizen.isNullOrEmpty()) {
                aktuelleNotiz = notizen?.last()
                textViewThema.text = aktuelleNotiz?.thema
                textViewNotiz.text = aktuelleNotiz?.notiz
            }
        }
    }

    fun onButtonDestroyClick(view: View?) { // um onDestroy zu testen
        finish()
    }

    fun onButtonNaechsteNotizClick(view: View?) {
        val textViewThema = findViewById<TextView>(R.id.edittext_thema)
        val textViewNotiz = findViewById<TextView>(R.id.edittext_notiz)
        if (aktuelleNotiz == null) {
            if (textViewThema.text.isNotEmpty() || textViewNotiz.text.isNotEmpty()) {
                Toast.makeText(this, "Notiz wurde noch nicht gespeichert", Toast.LENGTH_LONG)
                    .show() //Fall 1
            }
            return //Fall 1 und Fall 2
        }
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notizen: List<Notiz>? = null
            withContext(Dispatchers.IO) {
                notizen =
                    database.notizenDao().getNextNotizen(aktuelleNotiz?.id!!, aktuellesProjekt.id)
            }
            if (!notizen.isNullOrEmpty()) { //Fall 3a
                aktuelleNotiz = notizen?.first()
                textViewThema.text = aktuelleNotiz?.thema
                textViewNotiz.text = aktuelleNotiz?.notiz
            } else { // Fall 3b
                aktuelleNotiz = null
                textViewThema.text = ""
                textViewNotiz.text = ""
            }
        }
    }

    fun onToggleButtonLokalisierenClick(view: View?) {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if ((view as ToggleButton).isChecked()) {
            val spinner = findViewById<Spinner>(R.id.spinner_provider)
            val provider = spinner.selectedItem as String?
            if (provider == null) {
                Toast.makeText(
                    this, "erforderliche Berechtigungen wurden nicht erteilt", Toast.LENGTH_LONG
                ).show()
                (view as ToggleButton).isChecked = false
                return
            }
            Log.d(javaClass.simpleName, showProperties(locationManager, provider))
            try {
                locationManager.requestLocationUpdates(
                    provider, minTime, minDistance, locationListener
                )
                Log.d(javaClass.simpleName, "Lokalisierung gestartet")
            } catch (ex: SecurityException) {
                Log.e(
                    javaClass.simpleName,
                    "Erforderliche Berechtigung ${ex.toString()} nicht erteilt"
                )
            }
        } else {
            locationManager.removeUpdates(locationListener)
            Log.d(javaClass.simpleName, "Lokalisierung beendet")
        }
    }

    fun onButtonNotizSpeichernClick(view: View?) {
        val themaText = findViewById<TextView>(R.id.edittext_thema).text.toString().trim()
        val notizText = findViewById<TextView>(R.id.edittext_notiz).text.toString().trim()
        if (themaText.isEmpty() || notizText.isEmpty()) {
            Toast.makeText(this, "Thema und Notiz d??rfen nicht leer sein", Toast.LENGTH_LONG).show()
            return
        }
        var lastLocation: Location? = null
        var provider = ""
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val spinner = findViewById<Spinner>(R.id.spinner_provider)
            provider = spinner.selectedItem as String
            lastLocation = locationManager.getLastKnownLocation(provider)
        } catch (ex: SecurityException) {
            Log.e(javaClass.simpleName, "erforderliche Berechtigung ${ex.toString()} nicht erteilt")
        }
        if (lastLocation == null && aktuelleNotiz == null) {
            Toast.makeText(
                this,
                "Noch keine Geoposition ermittelt. Bitte sp??ter nochmal versuchen",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // Projekt speichern:
        val database = GeoNotesDatabase.getInstance(this)
        GlobalScope.launch {
            val id = database.projekteDao().insertProjekt(aktuellesProjekt)
            Log.d(
                javaClass.simpleName,
                "Projekt $aktuellesProjekt mit id=$id in Datenbank geschrieben"
            )
            val projekte = database.projekteDao().getProjekte()
            projekte.forEach {
                Log.d(javaClass.simpleName, "Projekt: $it")
            }
            if (aktuelleNotiz == null && lastLocation != null) {
                // Location speichern:
                database.locationsDao().insertLocation(
                    Location(
                        lastLocation.latitude,
                        lastLocation.longitude,
                        lastLocation.altitude,
                        provider
                    )
                )
                // Notiz Speichern:
                aktuelleNotiz = Notiz(
                    null,
                    aktuellesProjekt.id,
                    lastLocation.latitude,
                    lastLocation.longitude,
                    themaText,
                    notizText
                )
                aktuelleNotiz?.id = database.notizenDao().insertNotiz(aktuelleNotiz!!)
                Log.d(javaClass.simpleName, "Notiz $aktuelleNotiz gespeichert")
            } else if (aktuelleNotiz != null) {
                // Notiz aktualisieren:
                aktuelleNotiz?.thema = themaText
                aktuelleNotiz?.notiz = notizText
                database.notizenDao().insertNotiz(aktuelleNotiz!!)
                Log.d(javaClass.simpleName, "Notiz $aktuelleNotiz aktualisiert")
            }
        }
        with(AlertDialog.Builder(this)) {
            setTitle("Notiz weiter bearbeiten?")
            setNegativeButton("Nein", DialogInterface.OnClickListener { dialog, id ->
                aktuelleNotiz = null
                findViewById<TextView>(R.id.edittext_thema).text = ""
                findViewById<TextView>(R.id.edittext_notiz).text = ""
            })
            setPositiveButton("ja", DialogInterface.OnClickListener { dialog, id -> })
            show()
        }
    }

    fun onButtonStandortAnzeigenClick(view: View?) {
        if (aktuelleNotiz == null) {
            Toast.makeText(this, "Bitte Notiz ausw??hlen oder speichern", Toast.LENGTH_LONG).show()
            return
        }
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notizen: List<Notiz>? = null
            withContext(Dispatchers.IO) {
                notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
            }
            if (!notizen.isNullOrEmpty()) {
                val intent = Intent(this@GatherActivity, NoteMapActivity::class.java)
                intent.putParcelableArrayListExtra(NOTIZEN, ArrayList<Notiz>(notizen!!))
                intent.putExtra(INDEX_AKTUELLE_NOTIZ, notizen?.indexOf(aktuelleNotiz!!))
                startActivityForResult(intent, 0)
            }
        }
    }

    private fun showProperties(manager: LocationManager, providerName: String): String {
        val locationProvider = manager.getProvider(providerName)
            ?: return "Kein Provider unter dem Namen $providerName"

        return String.format(
            "Provider: %s\nHorizontale Genauigkeit:" + "%s\nUnterst??tzt H??henermittlung: %s\nErfordert Satellit: %s",
            providerName,
            if (locationProvider.accuracy == 1) "FINE" else "COARSE",
            locationProvider.supportsAltitude(),
            locationProvider.requiresSatellite()
        )
    }

    fun initSpinnerProviders() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        Log.d(javaClass.simpleName, "vef??gbare Provider:")
        providers.forEach {
            Log.d(javaClass.simpleName, "Provider: $it")
        }

        val spinner = findViewById<Spinner>(R.id.spinner_provider)
        spinner.adapter = ArrayAdapter<String>(this, R.layout.list_items, providers)
        spinner.onItemSelectedListener = SpinnerProviderItemSelectedListener()
        if (providers.contains("gps")) {
            spinner.setSelection(providers.indexOf("gps"))
        }
    }

    fun openWebView() {
        if (aktuelleNotiz == null) {
            Toast.makeText(this, "Bitte Notiz ausw??hlen oder speichern", Toast.LENGTH_LONG).show()
            return
        }
        val database = GeoNotesDatabase.getInstance(this)
        CoroutineScope(Dispatchers.Main).launch {
            var notizen: List<Notiz>? = null
            withContext(Dispatchers.IO) {
                notizen = database.notizenDao().getNotizen(aktuellesProjekt.id)
            }
            notizen?.also {
                val intent = Intent(this@GatherActivity, OsmWebViewActivity::class.java)
                intent.putParcelableArrayListExtra(NOTIZEN, ArrayList<Notiz>(it))
                intent.putExtra(INDEX_AKTUELLE_NOTIZ, it.indexOf(aktuelleNotiz!!))
                startActivity(intent)
            }
        }
    }

    inner class NoteLocationListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(javaClass.simpleName, "Empfangene Geodaten:\n$location")
            val textView = findViewById<TextView>(R.id.textview_output)
            textView.append("\nEmpfangene Geodaten:\n$location")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    val locationListener = NoteLocationListener()

    inner class SpinnerProviderItemSelectedListener : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (findViewById<ToggleButton>(R.id.togglebutton_lokalisierung).isChecked) {
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.removeUpdates(locationListener)
                val provider = (view as TextView).text.toString()
                try {
                    locationManager.requestLocationUpdates(
                        provider, minTime, minDistance, locationListener
                    )
                } catch (ex: SecurityException) {
                    Log.e(
                        javaClass.simpleName,
                        "Erforderliche Berechtigung ${ex.toString()} nicht erteilt"
                    )
                }
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            TODO("Not yet implemented")
        }
    }
}