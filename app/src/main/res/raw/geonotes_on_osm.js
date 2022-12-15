var indexAktuelleNotiz = Android.getIndexAktuelleNotiz();
console.log("Index der aktuellen Notiz: " + indexAktuelleNotiz);
var map = new ol.Map({
    layers: [
      new ol.layer.Tile({
        source: new ol.source.OSM()
      })
    ],
    target: 'map',
    view: new ol.View({
      center: ol.proj.fromLonLat([Android.getLongitude(indexAktuelleNotiz), Android.getLatitude(indexAktuelleNotiz)]),
      zoom: 16
    })
});

var iconStyle = new ol.style.Style({
        image: new ol.style.Icon({
            anchor: [0.5, 0.5],
            anchorXUnits: 'fraction',
            anchorYUnits: 'fraction',
            opacity: 0.75,
            src: './marker.png'
        })
});

var iconFeatures=[];
var iconFeature = new ol.Feature({
    geometry: new ol.geom.Point(ol.proj.transform([Android.getLongitude(indexAktuelleNotiz), Android.getLatitude(indexAktuelleNotiz)],
        'EPSG:4326',
        'EPSG:3857')),
    name: Android.getThema(indexAktuelleNotiz),
    description: Android.getNotiz(indexAktuelleNotiz),
});
iconFeature.setStyle(iconStyle);
iconFeatures.push(iconFeature);

var layer = new ol.layer.Vector({
    source: new ol.source.Vector({
        features: iconFeatures
    }),
});
map.addLayer(layer);

var container = document.getElementById('popup');
var content = document.getElementById('popup-content');
var closer = document.getElementById('popup-closer');
var overlay = new ol.Overlay({
    element: container,
    autoPan: true,
    autoPanAnimation: {
        duration: 250
    }
});
map.addOverlay(overlay);

// Event-handler
map.on('singleclick', function (event) {
    if (map.hasFeatureAtPixel(event.pixel) === true) {
        var coordinate = event.coordinate;
        var features = map.getFeaturesAtPixel(event.pixel);
        content.innerHTML = '<b>' + features[0].get('name') + '</b><br />' + features[0].get('description');
        overlay.setPosition(coordinate);
    } else {
        overlay.setPosition(undefined);
        closer.blur();
    }
});


