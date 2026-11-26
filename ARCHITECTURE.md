# FitStreak – Architektur

Native Android-App (Kotlin, Jetpack Compose, Material 3) für Muskelaufbau zu Hause.
Komplett offline: keine `INTERNET`-Permission, kein Login, keine Tracking- oder Werbe-SDKs.

## 1. Module

| Modul | Typ | Inhalt |
|-------|-----|--------|
| `:core` | reines Kotlin/JVM | Fachlogik ohne Android: Übungsdaten, Posen-Rig, Plan-Generator, Streak, XP, Progression, Statistik-Formeln. Voll unit-getestet. |
| `:app` | Android-App | UI (Compose), Persistenz (Room, DataStore), Benachrichtigungen, Widget, Backup. |

Die Trennung sorgt dafür, dass die gesamte Trainingslogik als reine Funktionen testbar ist
(`./gradlew :core:test`) – ohne Emulator.

## 2. Pakete

```
core/src/main/kotlin/de/leon/fitstreak/core/
  model/        Muscle, Equipment, Goal, Exercise, PlanExercise, WorkoutPlan …
  anim/         Rig (Pose, Limb, IK/FK), ExerciseAnimation, Interpolation
  exercises/    ExerciseLibrary (≈45 Übungen + Aufwärm-Übungen, datengetrieben)
  plan/         PlanGenerator, Alternativen-Suche, Wochen-Rotation
  progression/  ProgressionEngine (doppelte Progression, Varianten-Leiter, Deload)
  streak/       StreakEngine (reine Funktion)
  game/         XP, Level, Erfolge
  stats/        1RM, Volumen, Erholung, Wochen-Aggregation

app/src/main/java/de/leon/fitstreak/
  FitStreakApp.kt, MainActivity.kt, AppContainer.kt   (manuelle DI, kein Hilt)
  data/
    db/         Room: Entities, FitDao, AppDatabase
    settings/   Settings (Datenklasse), SettingsStore + DataStore-Implementierung
    AppData.kt            unveränderlicher Datenstand + alle Auswertungen als reine Funktionen
    FitRepository.kt      einzige Datenquelle für ViewModels, Receiver und Widget
    Reminders.kt          Texte und Zeitpunkte der Erinnerungen (rein, getestet)
    Backup.kt             JSON-Export/-Import
  ui/
    AppViewModel.kt       Datenstand als StateFlow + Aktionen
    Platform.kt           Schnittstelle zu Android (Haptik, Ton, Berechtigungen, Datei-Dialoge)
    Navigation.kt         Navigation Compose + Bottom Navigation
    theme/      Farben (Hantelscheiben-Palette), Typografie
    components/ Figure (Canvas-Renderer), BodyMap, Charts, Konfetti, Ringe, Icons, Abzeichen
    onboarding/ today/ workout/ library/ progress/ profile/ plan/ settings/ tips/
  figure3d/     3D-Figur: Rig3D (2D-Posen → 3D-Skelett), GlbFile, Math3, FigureRenderer (Filament)
  platform/     AndroidPlatform: Implementierung von ui/Platform.kt, Figure3DView
  notify/       ReminderScheduler, ReminderReceiver, BootReceiver, Notifications
  widget/       Glance-Widget (Streak + heutiges Workout)

app/src/test/   Repository-Durchstich mit In-Memory-DAO, Backup, Erinnerungen
tools/desktop-check/   rendert Animationen und Screens ohne Android SDK (Compose Desktop)
tools/model/           bereitet das 3D-Mannequin auf (Muskelregionen, Requisiten)
```

Schichten: **UI (Compose) → ViewModel (StateFlow) → FitRepository → Room / DataStore**.
Fachentscheidungen (Plan, Progression, Streak, XP) trifft immer `:core`; das Repository
liefert nur die Daten dafür und speichert das Ergebnis.

## 3. Datenmodell

### Statische Daten (Code, `:core`)

`Exercise`: `id`, `name`, primäre/sekundäre Muskeln, benötigte Ausrüstung, Schwierigkeit,
Art (Wiederholungen / Zeit), Wdh.-Bereich, Pause, `ladder` + `ladderRank`
(Varianten-Leiter leichter ↔ schwerer), Bewegungsmuster (`Slot`) für den Plan-Generator,
Anleitung (3–4 Schritte), typische Fehler, Atmung und `ExerciseAnimation`
(Keyframe-Posen, Phasen mit Dauer + Beschriftung, Requisiten wie Hantel/Stuhl/Stange/Band).

### Room (`fitstreak.db`, Version 1)

| Entity | Zweck | Wichtige Felder |
|--------|-------|-----------------|
| `WorkoutTemplateEntity` | ein Workout des Plans oder ein eigenes Workout | `id`, `name`, `focus`, `position`, `inRotation` |
| `TemplateExerciseEntity` | Übung in einem Workout inkl. aktueller Vorgabe | `templateId`, `position`, `exerciseId`, `sets`, `repMin`, `repMax`, `weightKg`, `tempo`, `restSec`, `failStreak` |
| `SessionEntity` | ein durchgeführtes Training | `epochDay` (lokales Datum des Starts), `templateId`, `templateName`, `startedAt`, `finishedAt`, `volumeKg`, `xp`, `prCount` |
| `SetLogEntity` | ein Satz | `sessionId`, `exerciseId`, `setIndex`, `reps` (oder Sekunden), `weightKg`, `rating`, `epochDay` |
| `DayRecordEntity` | festgeschriebener Streak-Status eines Tages | `epochDay`, `kind` (TRAINIERT / RUHETAG / VERPASST / GESCHÜTZT) |
| `HabitDayEntity` | Gewohnheiten eines Tages | `epochDay`, `protein`, `enoughFood`, `water`, `noSugar`, `sleep` |
| `MeasurementEntity` | Körpermaße | `epochDay`, `upperArmCm`, `chestCm`, `waistCm` |
| `FitnessTestEntity` | Einstufungs-/Monatstest | `epochDay`, `pushups`, `plankSec`, `squats` |
| `AchievementEntity` | freigeschalteter Erfolg | `id`, `unlockedAt` |

### DataStore (`settings.json`, typisiert)

`Settings` (kotlinx.serialization): Onboarding-Status, Name, Ziele, Ausrüstung,
Hantelgewichte, Trainingstage (Wochentage), Plan-Startdatum, Erinnerungszeiten,
Ton/Haptik, Streak-Zustand (aktuell, Rekord, Schutz, zuletzt ausgewerteter Tag), XP,
Deload-Zustand.

## 4. Plan-Generator (`core/plan`)

Eingabe: Ziele, Ausrüstung (+ Hantelgewichte), Trainingstage, Einstufungstest.
Ausgabe: Liste von Workouts mit Übungen und Vorgaben.

1. **Split wählen**
   - 3 Tage: Ganzkörper A / B / A, nächste Woche B / A / B
   - 4 Tage: Oberkörper / Unterkörper / Oberkörper / Unterkörper
   - 5 Tage: Push / Pull / Beine / Oberkörper / Ganzkörper
2. **Slots füllen**: Jedes Workout ist eine Liste von Bewegungsmustern
   (z. B. Drücken horizontal, Ziehen, Schultern, Bizeps, Trizeps, Core). Pro Slot wird die
   beste verfügbare Übung gewählt: Ausrüstung vorhanden, Gewicht sinnvoll (kein Seitheben
   mit 12 kg für Anfänger), Schwierigkeit passend zum Einstufungstest, möglichst keine
   Dopplung innerhalb der Woche.
3. **Ziele gewichten**: Zielmuskeln bekommen einen zusätzlichen Satz; beim Ziel „Sixpack“
   hat jedes Workout zwei Core-Übungen mit mehr Sätzen, sonst eine.
4. **Aufwärmen**: Jede Einheit beginnt mit 3–4 Minuten geführtem Aufwärmen.

Rotation als reine Funktion: `index = (Wochen seit Planstart × Tage + Slot in der Woche) mod Anzahl Workouts`.

Bearbeiten: Übung tauschen (`alternativesFor` – gleiche Hauptmuskelgruppe, vorhandene
Ausrüstung), Sätze/Wdh./Gewicht ändern, eigene Workouts anlegen.

## 5. Progression (`core/progression`)

Reine Funktion `nextPrescription(vorgabe, leistung, bewertung, verfügbareGewichte)`:

- Alle Sätze am oberen Ende des Wdh.-Bereichs und nicht „Schwer“ →
  1. nächstschwereres vorhandenes Gewicht (Wdh. zurück ans untere Ende), sonst
  2. schwerere Variante auf der Leiter (z. B. Liegestütze → Füße erhöht), sonst
  3. langsameres Tempo (3 Sek. runter) → Pause oben → zusätzlicher Satz.
- „Schwer“ oder Ziel verfehlt → Vorgabe bleibt; zweimal in Folge verfehlt → leichter
  (weniger Gewicht / leichtere Variante / Tempo zurück).
- Nach 6–8 Wochen ohne Deload schlägt die App eine Deload-Woche vor (ein Satz weniger).

## 6. Streak (`core/streak`)

Reine Funktion, die Tage der Reihe nach „festschreibt“:

- Trainingstag + trainiert → +1
- Ruhetag → +1 (nur wenn die Streak schon läuft) – Ruhetage brechen nie
- Trainingstag verpasst → Streak-Schutz wird automatisch verbraucht, sonst 0
- Streak-Schutz: +1 je 7 Tage Streak, maximal 2
- „Heute“ wird erst festgeschrieben, wenn trainiert wurde oder der Tag vorbei ist.
  Datum = lokales Datum beim Trainingsstart (Training über Mitternacht zählt zum Starttag).

## 7. Screens & Navigation

Bottom Navigation: **Heute · Übungen · Fortschritt · Profil**

- `onboarding` – Name, Ziele, Ausrüstung, Tage, Einstufungstest, Erinnerung
- `today` – Streak, heutiges Workout, Gewohnheiten, Hinweise (Deload, Streak-Schutz)
- `workout/{templateId}` – Aufwärmen → Übungen (Sätze, Pausen-Timer, Zeit-Countdown,
  Bewertung) → Zusammenfassung mit Konfetti
- `library`, `exercise/{id}` – Suche/Filter, Detail mit großer Animation, Körperkarte
- `progress` – Muskel-Übersicht, Rekorde, Diagramme, Heatmap, Körpermaße, Monatstest
- `profile` – Level/XP, Erfolge, Tipps, Plan, Einstellungen, Backup
- `plan`, `planEdit/{templateId}` – Wochenplan ansehen und bearbeiten

## 8. Übungs-Animationen

Ein Renderer für alle Übungen (`ui/components/Figure.kt`, Compose Canvas):

- **Rig** (`core/anim`): Hüftposition, Rumpfwinkel, Kopf; jede Gliedmaße entweder per
  Winkel (FK) oder per Zielpunkt (2-Knochen-IK – z. B. Hände am Boden beim Liegestütz).
  Knochenlängen bleiben dadurch immer konstant.
- 2–3 Keyframes pro Übung, Phasen mit Dauer und Beschriftung („runter – 2 Sek“),
  Ease-in-out und kurze Pause an den Umkehrpunkten.
- Seiten- oder Frontansicht, Bodenlinie, ferne Gliedmaßen heller, Requisiten
  (Hantel, Stuhl/Bank, Klimmzugstange, Band), Zielmuskeln farbig hervorgehoben.

### 3D-Figur (`figure3d`)

Die große Figur (Übungsdetail, Aufwärmen) ist ein echtes 3D-Modell, gerendert mit
[Filament](https://github.com/google/filament). Listen und Vorschauen bleiben bei der 2D-Zeichnung.

- **Eine Quelle für beide Darstellungen:** Es gibt keine fertigen Animationsclips. `Rig3D` rechnet
  jede 2D-Pose pro Frame auf das Skelett des Mannequins um – 2D und 3D laufen mit derselben Uhr
  und zeigen exakt dieselbe Bewegung. Neue Übungen brauchen weiterhin nur 2–3 Keyframes.
- **Umrechnung:** Seitenansicht = Y/Z-Ebene (nah = rechte Körperseite), Frontansicht = X/Y-Ebene.
  Jeder Knochen bekommt eine Drehung „Ruherichtung → Zielrichtung“ mit fester Scharnierachse
  (Ellbogen/Knie), damit sich nichts verdreht. Dazu kommt, was eine Strichfigur nicht braucht:
  Hände liegen flach auf Boden/Stuhl, Finger schließen sich um Hantel, Stange und Band, eine
  Hantel für beide Hände führt die Hände zusammen, die Füße stehen nebeneinander.
- **Tiefe:** Was aus der Bildebene herausführt, steht als `Depth` an der Pose (Oberkörper-Drehung,
  Arm seitlich schwenken) – die 2D-Figur ignoriert es. Wo die Zeichnung bewusst vereinfacht
  (nur ein Arm bewegt sich, damit man ihn von der Seite sieht), trägt die Animation eine eigene
  3D-Fassung (`ExerciseAnimation.threeD`) mit denselben Phasen.
- **Modell:** `assets/figure3d/figure.glb` ist das Mixamo-Mannequin „X Bot“. `tools/model/build_figure.py`
  entfernt die Clips und trennt die Oberfläche in Materialien je Muskelregion (`s_CHEST`, `s_QUADS` …),
  die zur Laufzeit eingefärbt werden: Zielmuskel rot, Hilfsmuskel gelb. Requisiten sind skalierte
  Einheitswürfel und -zylinder.
- **Rendering:** `FigureRenderer` zeichnet in eine durchsichtige `TextureView` (Sonne mit Schatten,
  gleichmäßiges Umgebungslicht, „PBR Neutral“-Tonemapping, 4× MSAA). Die Engine wird einmal pro
  Prozess angelegt (`Figure3DRuntime`), gezeichnet wird nur, wenn sich Pose oder Blickwinkel ändern.
  Seitlich wischen dreht die Figur, Doppeltipp stellt die Startansicht her.
- **Rückfall auf 2D:** ohne OpenGL ES 3.0, bei ausgeschaltetem Schalter (Einstellungen → Training)
  oder wenn die Engine nicht startet. Ein Start, der nie bis zum ersten Bild kommt (Treiber-Absturz),
  wird in `Figure3DGuard` vermerkt – danach bleibt die App bei 2D, bis der Schalter neu eingeschaltet wird.
- **Prüfen ohne Gerät:** `tools/desktop-check` enthält einen Software-Renderer (CPU-Skinning + Z-Buffer),
  der dieselbe `Rig3D`-Lösung und dieselben Modelldateien nutzt: `gradle :preview:run --args="sheet3d out"`.

### Gestaltung und Bewegung

- **Ruhige Flächen, ein Akzent:** neutrale Grautöne für Hintergrund, Karten und Text. Die Farben der
  Hantelscheiben tragen nur Bedeutung: Blau = Aktion/Auswahl, Rot = Streak, Gelb = XP, Grün = erledigt.
- **Signatur:** die Hantelscheibe als Statuszeichen (`PlateMark`) – Wochenleiste als Hantelstange,
  Sätze, Legenden, Abzeichen.
- **Schrift:** IBM Plex Sans (400/500/600) und IBM Plex Mono für Zahlen, in der App eingebettet.
- **Abstände** im 4-dp-Raster, Radien gestaffelt (Chips 8, Buttons 12, Karten 16, Dialoge 20).
- **Bewegung** (`ui/theme/Theme.kt`, `Motion`): eine Ease-out-Kurve und vier Dauern (120/160/220/300 ms).
  Animiert wird nur, was etwas erklärt: Druck-Feedback, Auswahl, Auf-/Zuklappen der Übungskarte,
  Screen-Wechsel in die Tiefe, Zähler in der Zusammenfassung. Timer-Ringe laufen pro Frame.
  Häufige Aktionen (Tab-Wechsel) blenden nur kurz über; bei abgeschalteten System-Animationen
  entfallen Konfetti und Auftritt.

## 9. Benachrichtigungen (`notify`)

```
Einstellungen/Boot/Update/Zeitzone ──► ReminderScheduler.scheduleAll()
        │  AlarmManager.setAndAllowWhileIdle (keine Exact-Alarm-Permission)
        ▼
ReminderReceiver (täglich · Streak-Retter · Snooze)
        │  liest Repository: heutiges Workout, Streak, schon trainiert?
        ├─► Notification (Kanal „Erinnerungen“), Aktion „In 1 Std. erinnern“
        └─► plant sich selbst für den nächsten Tag neu
```

`BootReceiver` reagiert auf `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, Zeit- und
Zeitzonenwechsel. `POST_NOTIFICATIONS` wird im Onboarding zur Laufzeit angefragt.

## 10. Build & Auslieferung

- GitHub Actions (`.github/workflows/build-apk.yml`): Tests → signierte Release-APK →
  GitHub Release `v1.0.<run_number>` (Pre-Release außerhalb von `main`) → Rauchtest der APK
  auf Emulatoren (`.github/scripts/smoke_test.py`).
- Fester Keystore `keystore/release.jks` im Repo → Updates lassen sich über die alte
  Version installieren, Daten bleiben erhalten.
- R8/Minify ist aus (keine Überraschungen zur Laufzeit).
