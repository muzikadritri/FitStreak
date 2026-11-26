#!/usr/bin/env python3
"""
Rauchtest der fertigen Release-APK auf einem Emulator (läuft in GitHub Actions).

Prüft ohne Test-Code in der App – also genau die Datei, die später installiert wird:
  1. Installation und Start ohne Absturz
  2. Onboarding bis zum fertigen Plan
  3. Erinnerungs-Alarme sind geplant
  4. Alle Tabs, Einstellungen, Test-Benachrichtigung
  5. Training starten + zufällige Eingaben (Monkey) ohne Absturz
  6. Nach einem Geräte-Neustart sind die Alarme wieder geplant und die Daten noch da
"""
import os
import re
import subprocess
import sys
import time

PKG = "de.leon.fitstreak"
SHOTS = "smoke-shots"


def adb(*args, check=True, timeout=120):
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} fehlgeschlagen: {result.stdout}\n{result.stderr}")
    return result.stdout


def shell(cmd, check=True, timeout=120):
    return adb("shell", cmd, check=check, timeout=timeout)


def log(msg):
    print(f"[smoke] {msg}", flush=True)


def fail(msg):
    log(f"FEHLER: {msg}")
    screenshot("fehler")
    try:
        texts = [t for t in re.findall(r'text="([^"]+)"', dump())]
        log("Sichtbare Texte: " + " | ".join(texts)[:1500])
    except Exception:
        pass
    print(adb("logcat", "-d", "-t", "300", "AndroidRuntime:E", "*:S", check=False))
    sys.exit(1)


def screenshot(name):
    os.makedirs(SHOTS, exist_ok=True)
    try:
        data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
        with open(os.path.join(SHOTS, f"{name}.png"), "wb") as f:
            f.write(data)
    except Exception as e:  # Screenshots sind nur Beiwerk
        log(f"Screenshot {name} nicht möglich: {e}")


def assert_alive(step):
    crash = adb("logcat", "-d", "AndroidRuntime:E", "*:S", check=False)
    if "FATAL EXCEPTION" in crash and PKG in crash:
        print(crash[-6000:])
        fail(f"Absturz bei: {step}")
    if not shell(f"pidof {PKG}", check=False).strip():
        fail(f"App läuft nicht mehr bei: {step}")
    log(f"ok: {step}")


def dump():
    """Liest die sichtbaren Texte samt Position (uiautomator)."""
    for _ in range(8):
        shell("uiautomator dump /sdcard/ui.xml", check=False, timeout=60)
        xml = shell("cat /sdcard/ui.xml", check=False)
        if "<node" not in xml:
            time.sleep(1)
            continue
        # Frische CI-Emulatoren melden gern „… isn't responding“ für System-Apps (Launcher,
        # System UI). Das hat nichts mit FitStreak zu tun: „Wait“ antippen und weitermachen.
        if "isn't responding" in xml or "keeps stopping" in xml:
            if PKG in xml or "FitStreak" in xml:
                return xml
            pos = find(xml, "Wait") or find(xml, "Close app") or find(xml, "OK")
            log("System-Dialog des Emulators weggeklickt")
            if pos:
                shell(f"input tap {pos[0]} {pos[1]}")
            time.sleep(3)
            continue
        return xml
    return ""


def find(xml, text, exact=True):
    for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        label = m.group(1).replace("&amp;", "&")
        if (label == text) if exact else (text in label):
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def visible(text, exact=False):
    return find(dump(), text, exact) is not None


def tap(text, exact=True, tries=8, optional=False, scroll=False):
    for _ in range(tries):
        pos = find(dump(), text, exact)
        if pos:
            shell(f"input tap {pos[0]} {pos[1]}")
            time.sleep(1.2)
            return True
        if scroll:
            # langsam nach unten scrollen und erneut suchen
            shell("input swipe 540 1500 540 900 500")
        time.sleep(1)
    if optional:
        return False
    fail(f"Text nicht gefunden: {text}")


def wait_for_boot():
    adb("wait-for-device", timeout=300)
    for _ in range(150):
        if shell("getprop sys.boot_completed", check=False).strip() == "1":
            return
        time.sleep(2)
    fail("Emulator fährt nicht hoch")


def alarms_scheduled():
    return PKG in shell("dumpsys alarm", check=False)


def main():
    apk = sys.argv[1]
    sdk = int(shell("getprop ro.build.version.sdk").strip())
    log(f"Android API {sdk}")

    # „App reagiert nicht“-Dialoge des Systems (z. B. vom Launcher auf langsamen CI-Emulatoren)
    # unterdrücken – Abstürze von FitStreak erkennt der Test über Logcat und den Prozess.
    shell("settings put global hide_error_dialogs 1", check=False)
    # dem Emulator nach dem Hochfahren etwas Ruhe gönnen (Launcher, Google-Dienste)
    time.sleep(40 if sdk >= 30 else 5)
    adb("install", "-r", apk, timeout=300)
    if sdk >= 33:
        shell(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS", check=False)
    adb("logcat", "-c", check=False)

    # 1) Start
    shell(f"am start -W -n {PKG}/.MainActivity", timeout=120)
    time.sleep(6)
    assert_alive("Start")
    screenshot("01-onboarding")

    # 2) Onboarding (6 Schritte, vorbelegt mit dem Startplan)
    if not visible("Willkommen bei FitStreak"):
        fail("Onboarding wird nicht angezeigt")
    for step in range(5):
        tap("Weiter")
        assert_alive(f"Onboarding Schritt {step + 2}")
    screenshot("02-onboarding-erinnerung")
    tap("Plan erstellen")
    time.sleep(4)
    assert_alive("Plan erstellt")
    screenshot("03-heute")
    if not visible("Rekord"):
        fail("Heute-Screen wird nach dem Onboarding nicht angezeigt")

    # 3) Erinnerungen geplant?
    if not alarms_scheduled():
        fail("Keine Alarme für die Erinnerungen geplant")
    log("ok: Erinnerungs-Alarme geplant")

    # 4) Tabs + Einstellungen + Test-Benachrichtigung
    for tab in ["Übungen", "Fortschritt", "Profil"]:
        tap(tab)
        time.sleep(2)
        assert_alive(f"Tab {tab}")
        screenshot(f"04-{tab.lower()}")
    tap("Einstellungen", exact=False, scroll=True)
    time.sleep(2)
    assert_alive("Einstellungen")
    tap("Test-Benachrichtigung senden", scroll=True)
    time.sleep(3)
    notifications = shell("dumpsys notification --noredact", check=False)
    if PKG not in notifications:
        fail("Test-Benachrichtigung wurde nicht angezeigt")
    log("ok: Test-Benachrichtigung angezeigt")
    screenshot("05-einstellungen")
    shell("input keyevent KEYCODE_BACK")
    time.sleep(1)

    # 5) Training starten (an Ruhetagen über den Wochenplan)
    tap("Heute")
    time.sleep(1)
    if not tap("Training starten", optional=True, tries=3):
        tap("Trotzdem trainieren / Plan ansehen")
        tap("Starten")
    time.sleep(4)
    assert_alive("Training gestartet (Aufwärmen)")
    screenshot("06-aufwaermen")
    shell("input tap 900 200")  # ungefähr „Überspringen“ – falls daneben, übernimmt der Monkey
    time.sleep(2)
    screenshot("07-training")

    # zufällige Eingaben in der App: darf nicht abstürzen
    monkey = subprocess.run(
        ["adb", "shell", "monkey", "-p", PKG, "-s", "7", "--throttle", "120", "--pct-syskeys", "0",
         "--pct-appswitch", "0", "--pct-anyevent", "0", "--ignore-timeouts", "--ignore-security-exceptions",
         "-v", "600"],
        capture_output=True, text=True, timeout=600,
    )
    out = monkey.stdout + monkey.stderr
    if f"CRASH: {PKG}" in out or f"NOT RESPONDING: {PKG}" in out:
        print(out[-6000:])
        fail("Absturz während der Zufalls-Eingaben")
    log(f"Monkey beendet (Exit {monkey.returncode})")
    screenshot("08-nach-monkey")
    crash = adb("logcat", "-d", "AndroidRuntime:E", "*:S", check=False)
    if "FATAL EXCEPTION" in crash and PKG in crash:
        print(crash[-6000:])
        fail("Absturz während der Zufalls-Eingaben (Logcat)")
    log("ok: 600 Zufalls-Eingaben ohne Absturz")

    # 6) Neustart: Alarme müssen vom BootReceiver neu geplant werden, Daten bleiben erhalten
    log("Neustart des Emulators …")
    adb("reboot", check=False)
    time.sleep(10)
    wait_for_boot()
    for _ in range(90):
        if alarms_scheduled():
            break
        time.sleep(2)
    else:
        fail("Nach dem Neustart wurden keine Erinnerungen geplant (BootReceiver)")
    log("ok: Erinnerungen nach Neustart wieder geplant")

    adb("logcat", "-c", check=False)
    shell("input keyevent KEYCODE_WAKEUP", check=False)
    shell("wm dismiss-keyguard", check=False)
    shell(f"am start -W -n {PKG}/.MainActivity", timeout=120)
    time.sleep(8)
    assert_alive("Start nach Neustart")
    screenshot("09-nach-neustart")
    if visible("Willkommen bei FitStreak"):
        fail("Daten nach Neustart verloren (Onboarding erscheint wieder)")
    log("ok: Daten nach Neustart erhalten")
    log("Rauchtest bestanden")


if __name__ == "__main__":
    main()
