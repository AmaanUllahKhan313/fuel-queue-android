import json
from pathlib import Path

# Merge AST + semantic
ast = json.loads(Path('.graphify_ast.json').read_text())
sem = json.loads(Path('.graphify_chunk_00.json').read_text())

# Deduplicate nodes
seen = {n['id'] for n in ast['nodes']}
merged_nodes = list(ast['nodes'])
for n in sem['nodes']:
    if n['id'] not in seen:
        merged_nodes.append(n)
        seen.add(n['id'])

merged = {
    'nodes': merged_nodes,
    'edges': ast['edges'] + sem['edges'],
    'hyperedges': sem.get('hyperedges', []),
    'input_tokens': sem.get('input_tokens', 0),
    'output_tokens': sem.get('output_tokens', 0),
}
Path('.graphify_extract.json').write_text(json.dumps(merged, indent=2))
print(f'Merged: {len(merged_nodes)} nodes, {len(merged["edges"])} edges ({len(ast["nodes"])} AST + {len(sem["nodes"])} semantic)')

