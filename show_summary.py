import json
from pathlib import Path

d = json.loads(Path('.graphify_detect.json').read_text())
print(f'Corpus: {d.get("total_files", 0)} files - {d.get("total_words", 0):,} words')
print(f'  code: {len(d["files"].get("code",[]))} files')
print(f'  docs: {len(d["files"].get("document",[]))} files')

