"""Emit minimal build diagnostics without copying values, URLs, or logs."""
from pathlib import Path
import re
import sys

log = Path(sys.argv[1]).read_text(errors='replace')
messages = []
for line in log.splitlines():
    # Only a Kotlin file name, location, and conservative compiler category.
    m = re.search(r'([A-Za-z0-9_]+\.kt):(\d+):(\d+).*?(Unresolved reference|Type mismatch|Cannot infer|No value passed|Too many arguments|Syntax error)', line)
    if m:
        messages.append(f'Kotlin: {m.group(1)}:{m.group(2)}:{m.group(3)} {m.group(4)}')
    m = re.search(r"Execution failed for task '(:[A-Za-z0-9:_-]+)'", line)
    if m:
        messages.append(f'Gradle task failed: {m.group(1)}')
    if 'There were failing tests' in line:
        messages.append('Android unit tests failed')
    if 'Could not resolve all files for configuration' in line:
        messages.append('Dependency resolution failed')
    if 'SDK location not found' in line:
        messages.append('Android SDK location not found')
    if 'No space left on device' in line:
        messages.append('Build runner storage full')
if not messages:
    messages.append('Android build failed; detailed logs require GitHub sign-in')
for message in list(dict.fromkeys(messages))[:15]:
    print('::error::' + message)
