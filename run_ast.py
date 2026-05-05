from graphify.extract import extract
from pathlib import Path
import json

detect = json.loads(Path('.graphify_detect.json').read_text())
code_files = [Path(f) for f in detect.get('files', {}).get('code', []) if Path(f).exists()]
result = extract(code_files) if code_files else {'nodes':[],'edges':[],'hyperedges':[],'input_tokens':0,'output_tokens':0}
Path('.graphify_ast.json').write_text(json.dumps(result, indent=2))
print(f'AST: {len(result["nodes"])} nodes, {len(result["edges"])} edges')

