"""Run on an isolated Android emulator with the release APK already installed.
Real three-minute lock-screen run with user-configured one-minute study/rest phases.
No timer acceleration, debug permissions, or exported test controls in the shipped app.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / "qa"
ADB = Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe"
QA.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output([str(ADB), *map(str, args)], encoding="utf-8", errors="replace")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/recall-test.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/recall-test.xml")).iter("node"))


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    assert x2 > x1 and y2 > y1, "UI element is offscreen"
    adb("shell", "input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)


def click(text, attempts=4):
    for _ in range(attempts):
        for node in nodes():
            if text in node.attrib.get("text", "") and node.attrib.get("clickable") == "true":
                tap(node)
                return
        adb("shell", "input", "swipe", "540", "1900", "540", "1000", "300")
    raise AssertionError("UI button not found: " + text)


def screenshot(name):
    (QA / name).write_bytes(subprocess.check_output([str(ADB), "exec-out", "screencap", "-p"]))


def text_all():
    return "\n".join(n.attrib.get("text", "") for n in nodes())


def log():
    return adb("logcat", "-d", "-v", "brief", "-s", "RecallTimer:I", "AndroidRuntime:E", "*:S")


print("Android UI / background integration test starting", flush=True)
adb("shell", "am", "force-stop", "cn.recall.study")
adb("logcat", "-c")
adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
adb("shell", "am", "start", "-n", "cn.recall.study/.MainActivity")
def find_field(name):
    for direction in (1, -1):
        for _ in range(6):
            for node in nodes():
                if node.attrib.get("resource-id") == "cn.recall.study:id/" + name:
                    return node
            if direction == 1: adb("shell", "input", "swipe", "540", "1850", "540", "800", "250")
            else: adb("shell", "input", "swipe", "540", "700", "540", "1900", "250")
    raise AssertionError("Input not found: " + name)


def edit_field(name, value):
    tap(find_field(name))
    time.sleep(.5)
    adb("shell", "input", "keyevent", "123")
    for _ in range(3): adb("shell", "input", "keyevent", "67")
    adb("shell", "input", "text", str(value))
    time.sleep(.2)
    adb("shell", "input", "keyevent", "4")
    assert find_field(name).attrib["text"] == str(value), "UI edit failed: " + name


# Reset prior test edits through the public UI so this test can be rerun safely.
for name,value in {"study_min":20,"study_max":30,"rest_min":5,"rest_max":8}.items():
    if find_field(name).attrib["text"] != str(value): edit_field(name,value)
assert find_field("study_min").attrib["text"] == "20"
assert find_field("study_max").attrib["text"] == "30"
assert find_field("rest_min").attrib["text"] == "5"
assert find_field("rest_max").attrib["text"] == "8"
print("PASS: default range inputs 20-30 / 5-8", flush=True)
edit_field("total_minutes",45)
screenshot("android-settings-v110.png")
# Exercise validation through the real UI before applying a short, supported custom cycle.
edit_field("study_min",31)
click("开始学习")
assert "START target=" not in log(), "invalid reversed range started a session"
print("PASS: reversed range rejected by UI", flush=True)
for name in ("study_min", "study_max", "rest_min", "rest_max"):
    edit_field(name,1)
edit_field("total_minutes",3)
# Leaving and reopening confirms settings are saved even before starting.
adb("shell", "input", "keyevent", "3")
time.sleep(1)
adb("shell", "am", "force-stop", "cn.recall.study")
adb("shell", "am", "start", "-n", "cn.recall.study/.MainActivity")
for name in ("study_min", "study_max", "rest_min", "rest_max"):
    assert find_field(name).attrib["text"] == "1", "custom range was not persisted"
print("PASS: custom settings survive process restart", flush=True)
edit_field("total_minutes",3)
click("开始学习")

# Exercise the same permission screens that a first-time user sees.
for _ in range(8):
    screen = text_all()
    if "PAUSE" in log() or "START target=180000" in log(): break
    if "com.android.permissioncontroller" in adb("shell", "dumpsys", "window", "windows") and ("Allow" in screen or "允许" in screen):
        choices = [n for n in nodes() if n.attrib.get("resource-id", "").endswith("permission_allow_button")]
        if choices: tap(choices[0]); continue
    if "设置后台运行" in screen:
        click("设置后台运行"); continue
    choices = [n for n in nodes() if n.attrib.get("resource-id", "") == "android:id/button1"]
    if "battery" in adb("shell", "dumpsys", "window", "windows").lower() and choices:
        tap(choices[0]); continue
    if "Allow" in screen or "允许" in screen:
        candidates = [n for n in nodes() if n.attrib.get("text", "") in ("Allow", "允许")]
        if candidates: tap(candidates[0]); continue
    if "开始学习" in screen:
        click("开始学习"); continue
    raise AssertionError("Unexpected permission screen: " + screen)

assert "START target=180000" in log(), "foreground session did not start"
print("PASS: notification and battery permissions; native service started", flush=True)
time.sleep(2)
click("暂停学习")
assert "PAUSE" in log()
paused = [line for line in text_all().splitlines() if "本次剩余" in line]
time.sleep(2)
assert paused == [line for line in text_all().splitlines() if "本次剩余" in line], "pause must freeze time"
screenshot("android-paused.png")
click("继续学习")
assert "RESUME" in log()
screenshot("android-learning.png")
adb("shell", "input", "keyevent", "3")
adb("shell", "input", "keyevent", "223")
assert "Now forced" in adb("shell", "dumpsys", "deviceidle", "force-idle")
print("PASS: pause/resume; Home pressed, screen off, deep idle forced", flush=True)
deadline = time.monotonic() + 220
review_seen = False
study_seen = False
while time.monotonic() < deadline:
    logs = log()
    assert "FATAL EXCEPTION" not in logs, logs
    if not review_seen and "REST elapsed=" in logs:
        review_seen = True
        elapsed = int(re.search(r"REST elapsed=(\d+)", logs).group(1))
        assert 60000 <= elapsed <= 61000, elapsed
        power = adb("shell", "dumpsys", "power")
        assert "mWakefulness=Asleep" in power or "mWakefulness=Dozing" in power, "screen should still be off"
        print("PASS: custom rest reminder at " + str(elapsed) + "ms with screen off", flush=True)
    if review_seen and not study_seen and "STUDY elapsed=" in logs:
        study_seen = True
        elapsed2 = int(re.search(r"STUDY elapsed=(\d+)", logs).group(1))
        assert 60000 <= elapsed2 - elapsed <= 61000, elapsed2 - elapsed
        print("PASS: automatic continue after " + str(elapsed2 - elapsed) + "ms", flush=True)
    if "COMPLETE" in logs:
        assert review_seen and study_seen
        break
    time.sleep(2)
else:
    raise AssertionError("session did not finish")
print("PASS: total session completed while locked", flush=True)
adb("shell", "dumpsys", "deviceidle", "unforce")
adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
adb("shell", "am", "start", "-n", "cn.recall.study/.MainActivity")
adb("shell", "input", "swipe", "540", "500", "540", "2000", "400")
click("学习记录")
assert "已完成" in text_all() and "03:00" in text_all()
screenshot("android-history.png")
(QA / "android-integration.log").write_text(log(), encoding="utf-8")
(QA / "android-result.json").write_text(json.dumps({"result": "passed", "study_duration_ms": elapsed, "rest_duration_ms": elapsed2-elapsed, "duration_minutes": 3, "device": "Android 15 API 35 emulator", "screen_off": True, "forced_deep_idle": True}, indent=2), encoding="utf-8")
print("Android integration passed", flush=True)
