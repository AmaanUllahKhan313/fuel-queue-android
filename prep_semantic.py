import json
from pathlib import Path

detect = json.loads(Path('.graphify_detect.json').read_text())
all_files = [f for files in detect['files'].values() for f in files if Path(f).exists()]

# Get uncached files (for now, we'll assume no cache)
uncached = all_files
Path('.graphify_uncached.txt').write_text('\n'.join(uncached))

# Get document files only for semantic extraction
docs = detect.get('files', {}).get('document', [])
print(f'Document files for semantic extraction: {len(docs)}')
print(f'Estimated semantic extraction time: ~45s (1 agent batch)')

