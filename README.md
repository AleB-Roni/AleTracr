# AleTracr

Kalorien-Tracker für Android – ähnlich wie MyFitnessPal, mit KI-Erkennung per Foto oder Text.

## Funktionen

- **Tagebuch** mit Frühstück, Mittagessen, Abendessen und Snacks, Tag vor/zurück blättern
- **Kalorienring & Makros** (Eiweiß, Kohlenhydrate, Fett) mit Tageszielen
- **Produktsuche** in [Open Food Facts](https://world.openfoodfacts.org) (über 3 Mio. Produkte, sehr gute Abdeckung für Deutschland)
- **Barcode-Scanner** (Google Code Scanner, keine Kamera-Berechtigung nötig)
- **KI-Schätzung** mit Claude (Anthropic): Foto vom Teller machen und/oder beschreiben („2 Brötchen mit Nutella und ein Cappuccino“) → Lebensmittel, Mengen, Kalorien und Makros, vor dem Speichern editierbar
- **Schnelleintrag**: nur Kalorien (optional Makros und Notiz)
- **Dauerhafte Benachrichtigung** „Noch X kcal übrig“ mit Fortschrittsbalken
  - **Kalorien eintragen** direkt in der Benachrichtigung (z. B. `250 Apfel`, Mahlzeit wird nach Uhrzeit gewählt)
  - **Mehr…** öffnet den Schnelleintrag in der App
  - **Ausblenden** schaltet sie ab – in den Einstellungen wieder aktivierbar
- **Zuletzt verwendet & Favoriten**
- **Mahlzeit von gestern kopieren**
- **Wasser-Tracker** (+250 ml)
- **Fortschritt**: 7-Tage-Diagramm, Durchschnitt, Gewichtsverlauf
- **Kalorienbedarf-Rechner** (Mifflin-St Jeor) in den Einstellungen

## KI einrichten

1. API-Key auf [console.anthropic.com](https://console.anthropic.com) erstellen.
2. In der App unter **Einstellungen → KI** einfügen und Modell wählen:
   - *Claude Opus 5.5* – genaueste Schätzung (Standard)
   - *Claude Sonnet 5.5* – schneller, günstiger
   - *Claude Haiku 5.5* – am günstigsten
3. Der Key wird nur lokal auf dem Gerät gespeichert.

## Bauen

Jeder Push auf `main` baut per GitHub Actions ein Debug-APK
(**Actions → Build APK → Artifacts → AleTracr-debug-apk**).

Lokal (Android Studio oder JDK 17 + Android SDK):

```bash
./gradlew assembleDebug
```

## Technik

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · OkHttp · kotlinx.serialization ·
Anthropic Java SDK · ML Kit Code Scanner · Coil

Produktdaten: © Open Food Facts contributors, [ODbL](https://opendatacommons.org/licenses/odbl/1-0/).
