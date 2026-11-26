# FitStreak 💪🔥

Native Android-App für Muskelaufbau zu Hause – komplett offline, ohne Konto, ohne Werbung.
Kotlin · Jetpack Compose · Material 3 · Room · DataStore.

## Installation auf dem Handy (ohne PC)

1. Öffne auf dem Handy die **[Releases-Seite](https://github.com/muzikadritri/FitStreak/releases)** dieses Repos
   (im Browser bei GitHub eingeloggt sein – das Repo ist privat – oder in der GitHub-App: Repo → *Releases*).
2. Tippe auf das **oberste Release** und unten bei **Assets** auf `FitStreak-v1.0.x.apk`.
3. Datei herunterladen und öffnen. Beim ersten Mal fragt Android, ob dein Browser (oder die
   GitHub-App) Apps installieren darf → **„Aus dieser Quelle zulassen“** einschalten und zurück.
4. **Installieren** tippen. Falls Play Protect nachfragt: *„Trotzdem installieren“* – die App ist
   selbst gebaut und deshalb bei Google nicht bekannt.
5. App öffnen, Onboarding durchgehen, Benachrichtigungen erlauben – fertig.

**Updates:** Einfach die neuere APK genauso herunterladen und installieren. Sie wird über die alte
Version installiert, **alle Daten bleiben erhalten** (jede APK ist mit demselben Schlüssel signiert).
Nicht vorher deinstallieren – dabei würden die Daten gelöscht. Zur Sicherheit gibt es in der App
unter *Profil → Einstellungen → Backup* einen Export als JSON-Datei.

**Tipp für zuverlässige Erinnerungen:** Manche Hersteller (Xiaomi, Huawei, Samsung …) bremsen
Apps im Hintergrund. Wenn Erinnerungen fehlen oder zu spät kommen:
*Einstellungen in der App → Akku-Einstellungen → FitStreak auf „Nicht optimieren“*.

## Was die App kann

| Bereich | Inhalt |
|---|---|
| **Onboarding** | Name, Ziele, Ausrüstung (inkl. Hantelgewichte), 3/4/5 Trainingstage + Wochentage, Einstufungstest, Erinnerungszeit |
| **Plan-Generator** | Ganzkörper A/B (3 Tage), Ober-/Unterkörper (4 Tage), Push/Pull/Beine/OK/GK (5 Tage); nur Übungen für deine Ausrüstung, Zielmuskeln mit mehr Sätzen, 1–2 Core-Übungen pro Einheit, geführtes Aufwärmen. Übungen tauschen, eigene Workouts |
| **45 Übungen** | Jede mit animierter Figur (Canvas, Keyframes), „So geht's“, typischen Fehlern, Atmung, Zielmuskeln auf der Körperkarte, Varianten-Leiter |
| **Training** | Sätze als Chips (vorausgefüllt mit dem letzten Mal), Pausen-Timer mit Ton + Vibration, Countdown für Halteübungen, Bewertung Leicht/Passt/Schwer, Zusammenfassung mit Konfetti |
| **Progression** | Doppelte Progression: mehr Gewicht → schwerere Variante → langsameres Tempo → zusätzlicher Satz. Zweimal nicht geschafft → leichter. Deload-Vorschlag nach 6+ Wochen |
| **Streak** | Ruhetage brechen die Streak nicht, Streak-Schutz (+1 je 7 Tage, max. 2) greift automatisch |
| **Erinnerungen** | Täglich passend zum Workout, Streak-Retter am Abend, „In 1 Std. erinnern“, überlebt Neustart und Updates |
| **Fortschritt** | Muskel-Übersicht + Erholung, Rekorde (inkl. geschätztem 1RM), Diagramme, 12-Wochen-Heatmap, Körpermaße, Monatstest |
| **Motivation** | XP, 6 Level, 11 Erfolge, tägliche Gewohnheiten (bewusst ohne Kalorienzählen), Tipps |
| **Extras** | Home-Screen-Widget, Dark Mode, Haptik, JSON-Backup |

## Projektaufbau

```
core/   Reine Kotlin-Logik (ohne Android): Übungen + Animationen, Plan-Generator,
        Progression, Streak, XP – mit Unit-Tests
app/    Android-App: Compose-UI, Room, DataStore, Benachrichtigungen, Glance-Widget
tools/desktop-check/   Entwickler-Werkzeug: rendert Animationen/Screens als PNG, ohne Android SDK
```

Details stehen in [ARCHITECTURE.md](ARCHITECTURE.md).

## Build

Jeder Push baut über GitHub Actions (`.github/workflows/build-apk.yml`) automatisch:

1. Unit-Tests (`:core:test`, `:app:testReleaseUnitTest`)
2. signierte Release-APK (`versionCode` = Run-Nummer)
3. GitHub Release `v1.0.<Run-Nummer>` mit der APK (außerhalb von `main` als Pre-Release)
4. Rauchtest auf Emulatoren (Android 8 und Android 15): Installation, Start, Onboarding,
   Erinnerungen, Zufalls-Eingaben, Neustart

Lokal (mit Android SDK):

```bash
./gradlew :core:test :app:testReleaseUnitTest   # Tests
./gradlew :app:assembleRelease                  # APK unter app/build/outputs/apk/release/
```

Ohne Android SDK lassen sich Logik und Oberfläche trotzdem prüfen:

```bash
cd tools/desktop-check
gradle :core:test :preview:test                       # alle Unit-Tests
gradle :preview:run --args="sheet out"                # Kontaktbögen aller Übungsanimationen
gradle :preview:run --args="screens out"              # Screenshots der Screens mit Testdaten
```

### Signierung

Der Keystore `keystore/release.jks` liegt absichtlich im (privaten) Repo, damit jede gebaute APK
dieselbe Signatur hat und Updates ohne Datenverlust funktionieren. Wer das Repo öffentlich macht,
sollte den Keystore vorher durch ein GitHub-Secret ersetzen.

## Datenschutz

Die App hat keine Internet-Berechtigung. Alle Daten liegen ausschließlich auf dem Gerät
(Room-Datenbank + DataStore) und verlassen es nur, wenn du selbst ein Backup exportierst.
