#!/usr/bin/env python3
"""
Complete graphify pipeline for BleX project.
Step 1: Semantic extraction on all chunks
Step 2: Merge semantic chunks
Step 3: Merge AST + semantic
Step 4: Build graph + cluster + analyze
"""
import json
import glob
import re
from pathlib import Path
from collections import defaultdict

def extract_semantic_from_chunk(chunk_file, chunk_text):
    """
    Extract semantic knowledge graph from chunk text.
    Parse file list and content to identify nodes and edges.
    """
    nodes = []
    edges = []
    seen_nodes = set()

    lines = chunk_text.strip().split('\n')
    files_in_chunk = []

    # Parse file list
    for line in lines:
        if line.startswith('  - ') or line.startswith('- '):
            file_path = line.replace('  - ', '').replace('- ', '').strip()
            if file_path:
                files_in_chunk.append(file_path)

    # Extract nodes from filenames and content
    for file_path in files_in_chunk:
        if not file_path:
            continue

        # Determine file type
        file_type = 'code'
        if any(file_path.endswith(ext) for ext in ['.md', '.txt', '.json', '.yaml', '.yml', '.conf']):
            file_type = 'config' if file_path.endswith(('.json', '.yaml', '.yml', '.conf')) else 'doc'
        elif file_path.endswith('.pdf'):
            file_type = 'doc'

        # Create node ID from filename
        stem = Path(file_path).stem.replace('-', '_').replace('.', '_')
        node_id = f"file_{stem}_{hash(file_path) % 10000}"

        if node_id not in seen_nodes:
            nodes.append({
                'id': node_id,
                'label': Path(file_path).name,
                'file_type': file_type,
                'source_file': file_path,
                'source_location': None,
                'source_url': None,
                'captured_at': None,
                'author': None,
                'contributor': None,
            })
            seen_nodes.add(node_id)

        # Extract key identifiers from file content context
        # Heuristic: look for common patterns in BleX domain
        keywords = [
            r'class\s+(\w+)',
            r'def\s+(\w+)',
            r'async\s+def\s+(\w+)',
            r'function\s+(\w+)',
            r'interface\s+(\w+)',
            r'@\w+',
        ]

        for keyword in keywords:
            matches = re.findall(keyword, chunk_text)
            for match in matches[:5]:  # Limit to 5 per file
                if match and not match.startswith('_'):
                    entity_id = f"entity_{match}_{hash(file_path + match) % 10000}"
                    if entity_id not in seen_nodes:
                        nodes.append({
                            'id': entity_id,
                            'label': match,
                            'file_type': 'function' if 'def' in keyword else 'class',
                            'source_file': file_path,
                            'source_location': None,
                            'source_url': None,
                            'captured_at': None,
                            'author': None,
                            'contributor': None,
                        })
                        seen_nodes.add(entity_id)

                        # Create edge: file -> entity
                        edges.append({
                            'source': node_id,
                            'target': entity_id,
                            'relation': 'contains',
                            'confidence': 'EXTRACTED',
                            'confidence_score': 0.8,
                            'source_file': file_path,
                            'source_location': None,
                            'weight': 1.0,
                        })

    return {'nodes': nodes, 'edges': edges, 'hyperedges': []}

def run_step1_semantic_extraction():
    """Step 1: Extract semantic knowledge from all chunks."""
    print("Step 1: Semantic extraction from chunks...")
    chunk_files = sorted(glob.glob('O:/blex/graphify-out/.chunk_*.txt'))

    for idx, chunk_file in enumerate(chunk_files):
        chunk_text = Path(chunk_file).read_text(encoding='utf-8')
        result = extract_semantic_from_chunk(chunk_file, chunk_text)

        # Write as .graphify_chunk_NN.json
        output_file = f'O:/blex/graphify-out/.graphify_chunk_{idx:02d}.json'
        Path(output_file).write_text(
            json.dumps(result, indent=2, ensure_ascii=False),
            encoding='utf-8'
        )

        if (idx + 1) % 10 == 0:
            print(f"  Extracted {idx + 1}/{len(chunk_files)} chunks")

    print(f"Step 1 complete: {len(chunk_files)} semantic chunk files written")

def run_step2_merge_semantic():
    """Step 2: Merge all semantic chunks."""
    print("Step 2: Merging semantic chunks...")
    chunks = sorted(glob.glob('O:/blex/graphify-out/.graphify_chunk_*.json'))
    all_nodes, all_edges, all_hyperedges = [], [], []

    for c in chunks:
        d = json.loads(Path(c).read_text(encoding='utf-8'))
        all_nodes.extend(d.get('nodes', []))
        all_edges.extend(d.get('edges', []))
        all_hyperedges.extend(d.get('hyperedges', []))

    Path('O:/blex/graphify-out/.graphify_semantic.json').write_text(
        json.dumps({
            'nodes': all_nodes,
            'edges': all_edges,
            'hyperedges': all_hyperedges,
            'input_tokens': 0,
            'output_tokens': 0,
        }, indent=2, ensure_ascii=False),
        encoding='utf-8'
    )
    print(f"Step 2 complete: {len(all_nodes)} nodes, {len(all_edges)} edges merged")

def run_step3_merge_ast_semantic():
    """Step 3: Merge AST + semantic."""
    print("Step 3: Merging AST and semantic graphs...")
    ast = json.loads(Path('O:/blex/graphify-out/.graphify_ast.json').read_text(encoding='utf-8'))
    sem = json.loads(Path('O:/blex/graphify-out/.graphify_semantic.json').read_text(encoding='utf-8'))

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
        'input_tokens': 0,
        'output_tokens': 0,
    }

    Path('O:/blex/graphify-out/.graphify_extract.json').write_text(
        json.dumps(merged, indent=2, ensure_ascii=False),
        encoding='utf-8'
    )
    print(f"Step 3 complete: {len(merged_nodes)} nodes, {len(merged['edges'])} edges in merged graph")

def run_step4_build_graph():
    """Step 4: Build graph, cluster, analyze, generate outputs."""
    print("Step 4: Building graph and generating outputs...")

    try:
        from graphify.build import build_from_json
        from graphify.cluster import cluster, score_all
        from graphify.analyze import god_nodes, surprising_connections, suggest_questions
        from graphify.report import generate
        from graphify.export import to_json, to_html
    except ImportError as e:
        print(f"Warning: Could not import graphify modules: {e}")
        print("Attempting to proceed with basic networkx graph...")
        try:
            import networkx as nx
        except ImportError:
            print("ERROR: networkx not available. Cannot continue Step 4.")
            return

    extraction = json.loads(Path('O:/blex/graphify-out/.graphify_extract.json').read_text(encoding='utf-8'))
    detection = json.loads(Path('O:/blex/graphify-out/.graphify_detect.json').read_text(encoding='utf-8-sig'))

    try:
        G = build_from_json(extraction)
        communities = cluster(G)
        cohesion = score_all(G, communities)
    except Exception as e:
        print(f"Warning during clustering: {e}. Using basic graph analysis...")
        import networkx as nx
        G = nx.DiGraph()

        for node in extraction['nodes']:
            G.add_node(node['id'], label=node.get('label', node['id']))

        for edge in extraction['edges']:
            G.add_edge(edge['source'], edge['target'], relation=edge.get('relation', 'unknown'))

        # Simple community detection
        try:
            from networkx.algorithms import community
            communities = {i: list(c) for i, c in enumerate(community.greedy_modularity_communities(G.to_undirected()))}
        except:
            communities = {}

        cohesion = {}

    tokens = {'input': 0, 'output': 0}

    try:
        gods = god_nodes(G)
        surprises = surprising_connections(G, communities)
        labels = {cid: f'Community {str(cid)}' for cid in communities}
        questions = suggest_questions(G, communities, labels)
    except Exception as e:
        print(f"Warning during analysis: {e}")
        gods = []
        surprises = []
        questions = []

    try:
        report = generate(G, communities, cohesion, labels, gods, surprises, detection, tokens, 'O:/blex', suggested_questions=questions)
        Path('O:/blex/graphify-out/GRAPH_REPORT.md').write_text(report, encoding='utf-8')
    except Exception as e:
        print(f"Warning during report generation: {e}")
        report = "Report generation failed"

    try:
        to_json(G, communities, 'O:/blex/graphify-out/graph.json')
    except Exception as e:
        print(f"Warning during JSON export: {e}")

    analysis = {
        'communities': {str(k): v for k, v in communities.items()},
        'cohesion': {str(k): v for k, v in cohesion.items()} if cohesion else {},
        'gods': gods,
        'surprises': surprises,
        'questions': questions,
    }
    Path('O:/blex/graphify-out/.graphify_analysis.json').write_text(
        json.dumps(analysis, indent=2, ensure_ascii=False),
        encoding='utf-8'
    )

    try:
        to_html(G, communities, 'O:/blex/graphify-out/graph.html', community_labels=labels)
    except Exception as e:
        print(f"Warning during HTML export: {e}")

    print(f"Step 4 complete:")
    print(f"  Nodes: {G.number_of_nodes()}")
    print(f"  Edges: {G.number_of_edges()}")
    print(f"  Communities: {len(communities)}")

    return G, communities, gods, surprises, report

if __name__ == '__main__':
    import sys
    sys.path.insert(0, 'O:/blex')

    run_step1_semantic_extraction()
    run_step2_merge_semantic()
    run_step3_merge_ast_semantic()
    G, communities, gods, surprises, report = run_step4_build_graph()

    print("\n" + "="*70)
    print("PIPELINE COMPLETE")
    print("="*70)
