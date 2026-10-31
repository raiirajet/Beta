# CasaMK Smart Home · Android v0.2

Modulare Android-Smart-Home-App für das Haus in Nordmazedonien.

## Jetzt verfügbar

- **Neuer Startbildschirm** mit Raumkacheln, Funktionskacheln und eigener Favoriten-Sektion
- **Kacheln sortieren** über *Bearbeiten* (↑/↓) und Favoriten über ☆ anheften; die Einstellungen werden lokal gespeichert
- Räume anlegen/umbenennen, Geräte einem Raum zuordnen und alle Geräte durchsuchen
- Schneller Überblick zu angeschalteten Lichtern, Klimazonen und Geräten
- Raum- und Funktionsfilter; individuelle Detailsteuerungen für Licht, Dimmer, Schalter, Fussbodenheizung, Fan-Coils, Rollläden, Türen, Kamerastandbilder und Szenen
- Warnung und Bestätigung für »Alle Lichter aus«; biometrische/Displaysperren-Bestätigung für Türentriegelung
- Detaillierter **Offline-Demo-Modus mit 23 Beispielgeräten** für einen ersten Test ganz ohne Hardware
- Home Assistant HTTPS REST API für echte Geräte; Tokens werden per Android Keystore verschlüsselt gespeichert
- Bereits vorbereitet für das spätere Hinzufügen von Herstellern über Home Assistant; die konkrete Integrationsfähigkeit hängt von den Geräten ab

**Wichtig:** Räume von realen Home-Assistant-Geräten können derzeit in der App manuell zugeordnet werden. Automatischer Import von Home-Assistant-Areas sowie Live-Kamerastreams sind weitere Ausbaustufen. Eine Android-APK lässt sich nicht als Sicherheits-/Heizungsregelung einsetzen.

## Installieren

1. In GitHub `Actions` den erfolgreichen Build `CasaMK Android APK` öffnen.
2. Unter `Artifacts` die ZIP mit der `app-debug.apk` herunterladen.
3. Die Debug-APK auf einem Android-11+-Telefon installieren. Diese Build-Variante ist nur zum Testen.

## Hausanbindung

Home Assistant als zentrale Steuerung im Haus einrichten. Für Fernzugriff aus der Schweiz ausschliesslich eine sichere HTTPS-Verbindung (z. B. Home Assistant Cloud) nutzen und keine ungeschützten Routerports öffnen.

In **Mehr → Verbinden** Serveradresse und persönlichen Long-Lived-Access-Token hinterlegen. Token niemals in GitHub veröffentlichen.

Die Installation vor Ort (230 V, Ventilsteuerungen, Wärmepumpe, Fan-Coils) muss von Fachpersonen ausgeführt werden; Regler für Frostschutz, Taupunkt und Pumpensicherheit bleiben unabhängig von der App.

## Build

Android Gradle Plugin 8.11.1, Gradle 8.13, Java 17, compileSdk 36. Automatischer Debug-APK-Build über `.github/workflows/android-apk.yml` bei Push auf `main`.

## Struktur

- `DashboardActivity.java` — neue Screens, Home-Kacheln, Räume, Favoriten, Geräte-Details
- `DashboardPrefs.java` — persistente lokale Ordnung, Favoriten und Raum-Zuordnung
- `CasaUi.java` — konsistente Farbpalette, Abstände, Karten und Interaktion
- `DemoRepository.java` — simulierter Testzustand
- `HomeRepository.java` — Home Assistant Kommunikation
- `SecureConfig.java` — HTTPS-Zugangsdaten verschlüsselt speichern
- `modules.json` — unterstützte Gerätekategorien

**Status:** Beta, keine ungeprüften Sicherheitsfunktionen im Dauerbetrieb einsetzen.
