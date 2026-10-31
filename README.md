# CasaMK Smart Home v0.1

Modulare Android-App für das Haus in Nordmazedonien. **Android 11+**, native Java-Android-App.

## Unterstützte Bereiche
- Lichtschalter und Steckdosen (Home Assistant: light, switch)
- Wassergeführte Fussbodenheizung (climate)
- Fan-Coils mit Heiz-/Kühlmodus und Lüfterstufe (climate)
- Wärmepumpe nach Anbindung an Home Assistant
- Kameras (Standbild), Türschloss mit Android-Bestätigung, Sensoren und Rollläden
- Automatische Erkennung passender Home-Assistant-Entitäten nach Einrichtung der jeweiligen Integration
- Offline-Demo ohne Hardware

## APK laden
Im GitHub-Tab **Actions** den Workflow **CasaMK Android APK** öffnen. Bei Erfolg befindet sich die Debug-APK unter **Artifacts**. Debug-APK ist nur zum Testen geeignet.

## Verbindung zur Hauszentrale
Home Assistant muss separat im Haus laufen. In der App unter **Einstellungen** eine gültige **HTTPS-Adresse** und einen Home-Assistant-Long-Lived-Access-Token eingeben. Keine Token ins Repository laden. Für später ist OAuth2 statt eines dauerhaften Tokens empfehlenswert.

**Kein offener Routerport 8123**. Nutze Home Assistant Cloud oder einen abgesicherten HTTPS-Zugang. Kamera-Livestream, Push und neue spezialisierte Gerätetypen sind noch nicht implementiert.

## Heizung
Bei der Auswahl der Wärmepumpe auf eine dokumentierte Schnittstelle (z.B. Modbus) achten; Fan-Coils, Heizkreisverteiler, Ventilantriebe und Thermostate fachgerecht abstimmen. Die Steuerungs-App ersetzt keine Frostschutz-/Sicherheitsregelung. Fussbodenkühlung braucht Kondensat-/Taupunktschutz.

Quellcode in `app/src/main/java/ch/casamk/smarthome`, Module in `assets/modules.json`.