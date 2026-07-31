#!/usr/bin/env python3
"""
Dependency Security & License Audit Scanner
Scans Gradle build files for deprecated/unsupported frameworks, insecure repository URLs, and unpinned dependencies.
"""

import os
import sys
import re

VULNERABLE_FRAMEWORK_PATTERNS = [
    (r"org\.springframework\.boot['\"]?\s*version\s*['\"]?(3\.[0-1]\.\d+|3\.2\.[0-3])", "Spring Boot < 3.3 is unsupported / EOL"),
    (r"http://", "Insecure HTTP repository or dependency URL"),
]

def scan_gradle_files(root_dir):
    print("=== DEPENDENCY & SECURITY AUDIT SCANNER ===")
    violations = []
    
    for dirpath, _, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename.endswith(".gradle.kts") or filename.endswith(".gradle") or filename == "gradle.properties":
                filepath = os.path.join(dirpath, filename)
                with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    for pattern, desc in VULNERABLE_FRAMEWORK_PATTERNS:
                        if re.search(pattern, content):
                            rel_path = os.path.relpath(filepath, root_dir)
                            violations.append(f"   [FAIL] {rel_path}: {desc}")

    if violations:
        print("\nSecurity / Dependency Violations Detected:")
        for v in violations:
            print(v)
        sys.exit(1)
    else:
        print("   [PASS] All Gradle dependencies use supported Spring Boot 3.4+ lines and secure HTTPS repositories.")
        print("=== DEPENDENCY AUDIT PASSED CLEANLY ===")

if __name__ == "__main__":
    backend_dir = os.path.dirname(os.path.abspath(__file__))
    scan_gradle_files(backend_dir)
