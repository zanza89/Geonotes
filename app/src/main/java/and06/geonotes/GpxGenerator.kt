package and06.geonotes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element
import java.io.File
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class GpxGenerator {
    fun createGpxFile(context: Context, notizen: List<Notiz>, projektName: String): Uri? {
        val document = createGpxDocument()
        return document?.let {
            createRootElement(it)
            createMetaData(it, projektName)
            appendTrackPoints(it, notizen)
            serialize(context, it)
        }
        // Alternativ auch
//        if (document == null) return null
//        createRootElement(document)
//        appendTrackPoints(document, notizen)
//        return serialize(context, document)
    }

    private fun createGpxDocument(): Document? {
        val factory = DocumentBuilderFactory.newInstance()
        val gpxDocument = try {
            val builder = factory.newDocumentBuilder()
            builder.newDocument()
        } catch (ex: ParserConfigurationException) {
            Log.e(javaClass.simpleName, ex.toString())
            null
        }
        return gpxDocument
    }

    private fun createRootElement(document: Document) {
        with(document.createElement("gpx")) {
            setAttribute("xmlns", "https://www.topografix.com/GPX/1/1")
            setAttribute("xmlns:xsi", "https://www.w3.org/2001/XMLSchema-instance")
            setAttribute("xsi:schemaLocation", "https://www.topografix.com/GPX/1/1/gpx.xsd, https://www.topografix.com/GPX/1/1/gpx.xsd")
            setAttribute("version", "1.1")
            setAttribute("creator", "GeoNotes")
            document.appendChild(this)
        }
    }

    private fun createMetaData(document: Document, projektName: String) {
        val metadata = document.createElement("metadata")
        document.documentElement.appendChild(metadata)
        val name = document.createElement("desc")
        name.appendChild(document.createTextNode(projektName))
        metadata.appendChild(name)
    }

    private fun appendTrackPoints(document: Document, notizen: List<Notiz>) {
        val trk = document.createElement("trk")
        document.documentElement.appendChild(trk)
        val trkseg = document.createElement("trkseg")
        trk.appendChild(trkseg)
        notizen.forEach {
            with (document.createElement("trkpt")) {
                setAttribute("lat", it.latitude.toString())
                setAttribute("lon", it.longitude.toString())
                val name = document.createElement("name")
                name.appendChild(document.createTextNode(it.thema))
                appendChild(name)
                val desc = document.createElement("desc")
                desc.appendChild(document.createTextNode(it.notiz))
                appendChild(desc)
                trkseg.appendChild(this)
            }
        }
    }

    private fun serialize(context: Context, document: Document): Uri? {
        val documentsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val destFile = File(documentsDirectory, "geonotes.gpx")
        val factory = TransformerFactory.newInstance()
        val uri = try {
            val transformer = factory.newTransformer()
            transformer.transform(DOMSource(document.documentElement), StreamResult(destFile)
            )
            FileProvider.getUriForFile(context, "geonotes.fileprovider", destFile)
        } catch (ex: TransformerConfigurationException) {
            Log.d(javaClass.simpleName, ex.toString())
            null
        } catch (ex: TransformerException) {
            Log.d(javaClass.simpleName, ex.toString())
            null
        }
        return uri
    }
}