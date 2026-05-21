import os, json
from pathlib import Path

os.environ['GRAPHIFY_VIZ_NODE_LIMIT'] = '6000'

extraction = json.loads(Path('O:/blex/graphify-out/.graphify_extract.json').read_text(encoding='utf-8'))
detection  = json.loads(Path('O:/blex/graphify-out/.graphify_detect.json').read_text(encoding='utf-8-sig'))
analysis   = json.loads(Path('O:/blex/graphify-out/.graphify_analysis.json').read_text(encoding='utf-8'))

from graphify.build   import build_from_json
from graphify.cluster import cluster, score_all
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report  import generate
from graphify.export  import to_json, to_html

G = build_from_json(extraction)

# Rebuild communities dict with int keys (analysis stores str keys)
communities = {int(k): v for k, v in analysis['communities'].items()}
cohesion    = {int(k): v for k, v in analysis.get('cohesion', {}).items()}
gods        = analysis.get('gods', [])
surprises   = analysis.get('surprises', [])
questions   = analysis.get('questions', [])
labels      = {cid: f'Community {cid}' for cid in communities}

tokens = {'input': 0, 'output': 0}

# Regenerate report
report = generate(G, communities, cohesion, labels, gods, surprises, detection, tokens,
                  'O:/blex', suggested_questions=questions)
Path('O:/blex/graphify-out/GRAPH_REPORT.md').write_text(report, encoding='utf-8')
print('GRAPH_REPORT.md written')

# Write graph.json
to_json(G, communities, 'O:/blex/graphify-out/graph.json')
print('graph.json written')

# Write graph.html
to_html(G, communities, 'O:/blex/graphify-out/graph.html', community_labels=labels)
print('graph.html written')

print(f'Done: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges, {len(communities)} communities')
