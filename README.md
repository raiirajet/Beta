# CasaMK Smart Home 0.3 — Grundriss-Beta

Modulare Android-App für das Haus in Nordmazedonien. Android 11+.

## Neue Funktionen
- **Erscheinungsbild:** unter Mehr → Design & Startansicht zwischen **Vollweiss** und **OLED-Schwarz (#000000)** wechseln.
- **Drei Startoptionen:** Kacheln, direkter Hausgrundriss oder kombinierte Startseite mit Grundrissvorschau.
- **Interaktiver Grundriss:** eigene Etagen, PDF-/Foto-/Bildimport (PDF, JPEG, PNG, WEBP), PDF-Seitenauswahl, Kamera-Fotoimport, Zoom, Verschieben, Ebenenfilter.
- **2D-Grundriss-Converter (Beta):** erkennt aus dem importierten Plan Wandlinien und erstellt bearbeitbare rechteckige Raumvorschläge. Ergebnisse müssen **immer kontrolliert und gegebenenfalls korrigiert** werden. Kein zuverlässiger CAD-, 3D- oder BIM-Converter.
- **Manueller Editor:** Räume per Polygonpunkten zeichnen, umbenennen, Eckpunkte korrigieren; Marker aus der Geräteliste auf dem Grundriss platzieren, verschieben, beschriften und löschen.
- **Zustandsfarben:** grün = aktiv, grau = aus, rot = erkannter Fehler, gelb = unbekannt/nicht erreichbar. Sensoren haben eine eigene Farbe. Bei Klima gilt Sollmodus `heat` allein ausdrücklich nicht als bestätigter Heizbetrieb.
- **Lokale Sicherung:** Grundriss inklusive eingebetteter Bilder, Räume, Gerätemarker und Stockwerke als JSON exportieren/importieren.
- **Geräteintegration:** Home Assistant über HTTPS (API); Grundrisszustände werden gegenwärtig ungefähr alle 20 Sekunden erneut geladen. Diese Version hat noch **keine permanente WebSocket-Übertragung**.
- **Bisherige Funktionen:** Dashboard, Räume, Favoriten, Licht, Fussbodenheizung, Fan-Coils, Türschlösser mit Android-Sicherheitsabfrage, Kamerastandbilder.

## Grundriss verwenden
1. App öffnen → **Grundriss**.
2. Etage wählen, auf **Import** tippen → Bild/PDF auswählen oder Plan fotografieren.
3. **Konvertieren** ausführen und vorgeschlagene Wände und Räume kontrollieren.
4. Räume mit **＋ Raum** manuell ergänzen; Eckpunkte durch Tippen auf den Raum überarbeiten.
5. **＋ Gerät** auswählen und eine Position auf dem Plan antippen.
6. Marker antippen → Gerätedetails anzeigen, verschieben, neu verknüpfen oder entfernen.
7. **Export** erstellt eine Sicherung. Private Grundrisse und Zugangsdaten gehören **nicht** ins öffentliche GitHub-Repository.

## Einschränkungen
Der Converter funktioniert bei sauberen 2D-Grundrissen mit gut erkennbaren orthogonalen Wänden am besten. Fotos mit Perspektive, unvollständigen Wandlinien, Texten oder runden Räumen müssen manuell korrigiert werden; Perspektiventzerrung, automatischer Room-Registry-Import, Kamera-Livevideo und WebSocket-Events gehören zu weiteren Ausbauschritten. Ein grüner Build ist kein vollständiger Test auf dem Handy.

CasaMK ersetzt niemals die Sicherheitsregelung von Wärmepumpe, Fan-Coils oder Fussbodenheizung (Frost- und Taupunktschutz). Ein Hausschloss darf nur mit gesicherter Identitätsbestätigung entriegelt werden.

## Build / Installation
Über **GitHub → Actions → CasaMK Android APK** den neuesten erfolgreichen Build öffnen. Unter **Artifacts** die Debug-APK als ZIP herunterladen. Auf dem Handy entpacken und `app-debug.apk` installieren. Beim Wechsel zwischen unterschiedlich signierten GitHub-Debug-APKs müssen ältere Testinstallationen gegebenenfalls deinstalliert werden. **Vorher die Grundrissdaten exportieren.**

## Architektur
- `DashboardActivity`: Startseite, Räume, Gerätedetails, Einstellungen
- `CasaUi` und `Appearance`: globaler Theme-/Startansichtswechsel
- `FloorPlanActivity`: Dokumenten-, PDF- und Kameraimport, Etagen, Editor, Backup
- `FloorPlanView`: Canvas mit Zoom/Pan und zustandsbasierten Markern
- `FloorPlanStore`: private Grundrissdaten (JSON und lokale Bilder)
- `PlanConverter`: deterministischer Offline-2D-Converter
- `HomeRepository`, `SecureConfig`: sichere Hausanbindung

Version 0.3, Android Gradle Plugin 8.11.1, Gradle 8.13, Java 17, compileSdk 36.
