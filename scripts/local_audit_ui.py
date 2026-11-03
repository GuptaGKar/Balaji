"""Small ADB UI helper restricted to the separately installed local QA applications."""

import re
import time
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ADB = "/home/linuai/gate_prep/Android/Sdk/platform-tools/adb"
AUDIT_PACKAGES = {"com.balaji.provision.audit", "com.balaji.provision.staff.audit"}


def adb(*arguments):
    result = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True)
    devices = [re.search(r"transport_id:(\d+)", line).group(1)
               for line in result.stdout.splitlines()
               if re.search(r"\sdevice\s", line) and "model:SM_A346E" in line
               and re.search(r"transport_id:(\d+)", line)]
    if not devices:
        raise RuntimeError("Authorized test device is disconnected")
    result = subprocess.run([ADB, "-t", devices[0], *arguments], capture_output=True, text=True)
    if result.returncode:
        # Arguments may contain an OTP or password: never include them in an error.
        raise RuntimeError("ADB operation failed; sensitive arguments omitted")
    return result.stdout


def snapshot():
    activity = adb("shell", "dumpsys", "activity", "activities")
    resumed = next((line for line in activity.splitlines()
                    if "topResumedActivity=" in line), "")
    if not any(package + "/" in resumed for package in AUDIT_PACKAGES):
        if "com.google.android.permissioncontroller/" not in resumed:
            raise RuntimeError("Refusing UI interaction outside the audit applications")
    # Stream the hierarchy; do not leave input values in an XML file on the device.
    output = adb("exec-out", "uiautomator", "dump", "/proc/self/fd/1")
    start, end = output.find("<hierarchy"), output.rfind("</hierarchy>")
    if start < 0 or end < 0:
        raise RuntimeError("UI hierarchy was not available")
    root = ET.fromstring(output[start:end + len("</hierarchy>")])
    if "permissioncontroller/" in resumed:
        text = " ".join(node.get("text", "") for node in root.iter("node"))
        if "Balaji Customer QA" not in text and "Balaji Staff QA" not in text:
            raise RuntimeError("Refusing an unrelated permission dialog")
    return root


def center(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        raise RuntimeError("Element has no screen bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(node):
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))


def find(root, text):
    matches = [node for node in root.iter("node")
               if node.get("text") == text or node.get("content-desc") == text]
    if not matches:
        raise RuntimeError("Requested UI label is absent")
    return matches[0]


def tap_text(text, timeout=6):
    """Wait for one QA control after an animation, then tap it exactly once."""
    deadline = time.monotonic() + timeout
    while True:
        root = snapshot()  # Foreground/security failures must propagate.
        matches = [node for node in root.iter("node")
                   if node.get("text") == text or node.get("content-desc") == text]
        if matches:
            tap(matches[0])
            return
        if time.monotonic() >= deadline:
            raise RuntimeError("Requested QA control did not appear")
        time.sleep(0.2)


def safe_labels(root):
    redaction_file = Path("/tmp/balaji-qa-ui-redactions.json")
    private_values = json.loads(redaction_file.read_text()) if redaction_file.exists() else []
    labels = []
    for node in root.iter("node"):
        if "EditText" in node.get("class", "") or node.get("password") == "true":
            continue
        text = node.get("text") or node.get("content-desc") or ""
        for value in private_values:
            text = text.replace(value, "[private detail redacted]")
        text = re.sub(r"(?<!\d)(?:\+?91)?[6-9]\d{9}(?!\d)", "[phone redacted]", text)
        text = re.sub(r"\b\d{6}\b", "[code redacted]", text)
        text = re.sub(r"\S+@\S+", "[email redacted]", text)
        if text:
            labels.append((text, node.get("bounds")))
    return labels


if __name__ == "__main__":
    print(safe_labels(snapshot()))
