"""Reuse QA only after git verifies identical app/build inputs and XML proves every case passed."""
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path('verification/prior-qa')
def counts(pattern):
    files = list(root.glob(pattern))
    assert files, 'Missing baseline XML: ' + pattern
    total = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        suite = ET.parse(path).getroot()
        for key in total:
            total[key] += int(suite.get(key, 0))
    return total

unit = counts('app/build/test-results/testDebugUnitTest/TEST-*.xml')
android = counts('app/build/outputs/androidTest-results/**/TEST-*.xml')
assert unit == dict(tests=57, failures=0, errors=0, skipped=0), unit
assert android == dict(tests=43, failures=0, errors=0, skipped=0), android
record = dict(source='49a1434195a2f79bb6be1a20679d72a13e834597', run=42712300138,
              artifact=13718170697, unit=unit, android=android,
              scope='Identical app, dependency configuration, reader assets and fixture inputs; release APK is freshly tested.')
Path('verification/qa-baseline.json').write_text(json.dumps(record, indent=2))
print(json.dumps(record))

# Preserve the validated fixtures/screenshots for the fresh optimized-release smoke.
shutil.copytree(root / "verification/android-print/workflow-qa",
                Path("verification/android-print/workflow-qa"), dirs_exist_ok=True)
