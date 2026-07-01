from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

passed = 0
failed = 0


def check(name: str, condition: bool, details: str = ""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}" + (f" - {details}" if details else ""))


def has_text(path: str, *needles: str) -> bool:
    file_path = ROOT / path
    if not file_path.exists():
        return False
    text = file_path.read_text(errors="ignore")
    return all(needle in text for needle in needles)


def exists(path: str) -> bool:
    return (ROOT / path).exists()


def finish(name: str, manual_steps=None):
    print(f"\n{name}: {passed} passed, {failed} failed")
    if manual_steps:
        print("\nManual/live proof still required:")
        for step in manual_steps:
            print(f"  - {step}")
    if failed:
        sys.exit(1)
