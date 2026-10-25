"""Build native Windows EXE and signed Android APK without downloading dependencies.
Requires Windows .NET Framework 4.8, JDK 17+, Android SDK 35/build-tools 35, Pillow.
Signing keys stay in .keys/. Keep that directory private and backed up for APK updates.
"""
import argparse
import hashlib
import math
import os
from pathlib import Path
import secrets
import shutil
import struct
import subprocess
import sys
import wave
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
DIST = ROOT / "dist"
CSC = Path(os.environ.get("WINDIR", "C:/Windows")) / "Microsoft.NET/Framework64/v4.0.30319/csc.exe"


def run(args, **kwargs):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, **kwargs)
    if result.returncode:
        raise RuntimeError(f"{Path(str(args[0])).name} failed (exit {result.returncode})")


def assets():
    from PIL import Image, ImageDraw
    destination = BUILD / "assets"
    destination.mkdir(parents=True, exist_ok=True)
    tones = {"recall": [(523.25, 0), (440, .30)], "continue": [(659.25, 0), (880, .23)], "complete": [(523.25, 0), (659.25, .25), (783.99, .50)]}
    for name, notes in tones.items():
        duration = notes[-1][1] + 1.05
        samples = []
        for i in range(int(duration * 22050)):
            t = i / 22050
            value = 0
            for frequency, onset in notes:
                age = t - onset
                if 0 <= age < 1.05:
                    envelope = min(1, age / .012) * math.exp(-age * 5) * min(1, (1.05 - age) / .1)
                    value += .35 * envelope * (math.sin(2 * math.pi * frequency * age) + .15 * math.sin(2 * math.pi * 2 * frequency * age))
            samples.append(struct.pack("<h", int(max(-.95, min(.95, value)) * 32767)))
        with wave.open(str(destination / f"{name}.wav"), "wb") as output:
            output.setparams((1, 2, 22050, 0, "NONE", "not compressed"))
            output.writeframes(b"".join(samples))
    icon = Image.new("RGBA", (256, 256), "#1c3631")
    draw = ImageDraw.Draw(icon)
    draw.line([(65, 72), (128, 94), (191, 72), (191, 177), (128, 200), (65, 177), (65, 72)], fill="#a4e5c7", width=13, joint="curve")
    draw.line([(128, 96), (128, 198)], fill="#a4e5c7", width=11)
    draw.line([(92, 45), (128, 56), (164, 45)], fill="#a4e5c7", width=10, joint="curve")
    icon.save(destination / "app.ico", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
    return destination


def windows(asset_dir):
    print("Building Windows EXE...", flush=True)
    wpf = CSC.parent / "WPF"
    refs = ["System.dll", "System.Core.dll", "System.Xaml.dll", "System.Drawing.dll", "System.Windows.Forms.dll", "System.Runtime.Serialization.dll", "System.Xml.dll"]
    refs += [str(wpf / name) for name in ["PresentationFramework.dll", "PresentationCore.dll", "WindowsBase.dll"]]
    output = DIST / "Recall-Study.exe"
    command = [CSC, "/nologo", "/target:winexe", "/optimize+", "/platform:anycpu", "/codepage:65001", "/out:" + str(output), "/win32icon:" + str(asset_dir / "app.ico"), "/win32manifest:" + str(ROOT / "windows/app.manifest")]
    command += ["/reference:" + name for name in refs]
    command += ["/resource:" + str(ROOT / "windows/MainWindow.xaml") + ",Recall.MainWindow.xaml"]
    command += ["/resource:" + str(p) + ",Recall." + p.name for p in asset_dir.iterdir()]
    command += [ROOT / "windows/App.cs", ROOT / "windows/SessionEngine.cs"]
    run(command)
    shutil.copy2(ROOT / "windows/App.config", str(output) + ".config")


def android(asset_dir):
    print("Building signed Android APK...", flush=True)
    sdk = Path(os.environ["ANDROID_HOME"])
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    tools = sdk / "build-tools/35.0.0"
    platform = sdk / "platforms/android-35/android.jar"
    output = BUILD / "android"
    classes = output / "classes"
    dex = output / "dex"
    for path in [output, classes, dex]: path.mkdir(parents=True, exist_ok=True)
    run([tools / "aapt2.exe", "compile", "--dir", ROOT / "android/res", "-o", output / "resources.zip"])
    # Pass -0 wav to allow AssetFileDescriptor access for native MediaPlayer.
    run([tools / "aapt2.exe", "link", "-o", output / "base.apk", "-I", platform, "--manifest", ROOT / "android/AndroidManifest.xml", "--java", output / "generated", "-A", asset_dir, "-0", "wav", "--auto-add-overlay", output / "resources.zip"])
    sources = list((ROOT / "android/src").rglob("*.java")) + list((output / "generated").rglob("*.java"))
    bootclasspath = str(platform) + os.pathsep + str(tools / "core-lambda-stubs.jar")
    run([java / "javac.exe", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options", "-bootclasspath", bootclasspath, "-d", classes] + sources)
    run([java / "java.exe", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", platform, "--min-api", "26", "--output", dex] + list(classes.rglob("*.class")))
    shutil.copy2(output / "base.apk", output / "unsigned.apk")
    with zipfile.ZipFile(output / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
        for path in dex.glob("*.dex"): apk.write(path, path.name)
    run([tools / "zipalign.exe", "-f", "-p", "4", output / "unsigned.apk", output / "aligned.apk"])
    keys = ROOT / ".keys"
    keys.mkdir(exist_ok=True)
    password_file = keys / "password.txt"
    if not password_file.exists(): password_file.write_text(secrets.token_hex(24), encoding="utf-8")
    env = dict(os.environ, RECALL_KEYSTORE_PASSWORD=password_file.read_text(encoding="utf-8").strip())
    key = keys / "recall-release.jks"
    if not key.exists():
        run([java / "keytool.exe", "-genkeypair", "-noprompt", "-keystore", key, "-storetype", "JKS", "-alias", "recall", "-storepass:env", "RECALL_KEYSTORE_PASSWORD", "-keypass:env", "RECALL_KEYSTORE_PASSWORD", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Recall Study, OU=Personal, O=Recall, C=CN"], env=env)
    run([java / "java.exe", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-key-alias", "recall", "--ks-pass", "env:RECALL_KEYSTORE_PASSWORD", "--key-pass", "env:RECALL_KEYSTORE_PASSWORD", "--out", DIST / "Recall-Study.apk", output / "aligned.apk"], env=env)
    run([java / "java.exe", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", DIST / "Recall-Study.apk"])


def tests():
    print("Testing both timer engines with the same behavioral traces...", flush=True)
    output = BUILD / "tests"
    output.mkdir(parents=True, exist_ok=True)
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    run([java / "javac.exe", "-encoding", "UTF-8", "-d", output, ROOT / "android/src/cn/recall/study/SessionEngine.java", ROOT / "tests/EngineTest.java"])
    run([java / "java.exe", "-cp", output, "EngineTest", ROOT / "tests/traces.txt"])
    run([CSC, "/nologo", "/target:exe", "/out:" + str(output / "EngineTest.exe"), ROOT / "windows/SessionEngine.cs", ROOT / "tests/EngineTest.cs"])
    run([output / "EngineTest.exe", ROOT / "tests/traces.txt"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=["windows", "android", "tests"])
    args = parser.parse_args()
    BUILD.mkdir(exist_ok=True); DIST.mkdir(exist_ok=True)
    try:
        tests()
        if args.only != "tests":
            asset_dir = assets()
            if args.only != "android": windows(asset_dir)
            if args.only != "windows": android(asset_dir)
            checksums = [hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name for p in sorted(DIST.iterdir()) if p.suffix in [".exe", ".apk"]]
            (DIST / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
            shutil.copy2(ROOT / "开始使用.txt", DIST / "开始使用.txt")
            if (DIST / "Recall-Study.exe").exists():
                with zipfile.ZipFile(DIST / "Recall-Windows.zip", "w", zipfile.ZIP_DEFLATED) as package:
                    for name in ["Recall-Study.exe", "Recall-Study.exe.config", "开始使用.txt"]:
                        package.write(DIST / name, name)
        print("Build and checks passed.", flush=True)
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
