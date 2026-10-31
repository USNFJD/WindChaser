"""Compare the included modified source with the hash-verified pinned archive.

Only line endings are normalized. No upstream source files are overwritten.
"""
import difflib
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

root = Path(__file__).resolve().parent
provenance = json.loads((root / 'upstream-provenance.json').read_text(encoding='utf-8'))
with urllib.request.urlopen(provenance['archive_url'], timeout=90) as response:
    archive_bytes = response.read()
assert hashlib.sha256(archive_bytes).hexdigest() == provenance['sha256'], 'Upstream archive hash mismatch'

with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
    originals = {}
    for item in archive.infolist():
        if not item.is_dir():
            relative = item.filename.split('/', 1)[1]
            originals[relative] = archive.read(item)

excluded = {'build', '.gradle', '__pycache__', '.git', '.idea'}
modified = {
    file.relative_to(root / 'upstream').as_posix(): file.read_bytes()
    for file in (root / 'upstream').rglob('*')
    if file.is_file()
    and not any(part in excluded for part in file.relative_to(root / 'upstream').parts)
    and file.name != 'local.properties'
}
patch = []
changes = []
for name in sorted(originals.keys() | modified.keys()):
    before = originals.get(name, b'')
    after = modified.get(name, b'')
    if before == after:
        continue
    try:
        old = before.decode('utf-8').replace('\r\n', '\n').replace('\r', '\n')
        new = after.decode('utf-8').replace('\r\n', '\n').replace('\r', '\n')
    except UnicodeDecodeError:
        raise RuntimeError('Unexpected binary change: ' + name)
    if old == new:
        continue
    patch.extend(difflib.unified_diff(
        old.splitlines(keepends=True), new.splitlines(keepends=True),
        fromfile='a/' + name if name in originals else '/dev/null',
        tofile='b/' + name if name in modified else '/dev/null',
    ))
    changes.append(name)
(root / 'upstream-changes.patch').write_text(''.join(patch), encoding='utf-8', newline='\n')
print(json.dumps({'changed_files': changes, 'patch_lines': len(patch)}, indent=2))
