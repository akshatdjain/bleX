import requests
import base64
import zlib
import os

def render_mermaid(mermaid_code, output_path):
    # Encode for Kroki
    payload = base64.urlsafe_b64encode(zlib.compress(mermaid_code.encode('utf-8'), 9)).decode('ascii')
    url = f"https://kroki.io/mermaid/png/{payload}"
    
    print(f"Generating {output_path}...")
    response = requests.get(url)
    if response.status_code == 200:
        with open(output_path, 'wb') as f:
            f.write(response.content)
        print(f"Success: {output_path}")
    else:
        print(f"Error: {response.status_code} - {response.text}")

# 1. Standard Architecture
standard_mermaid = """graph TD
    classDef hardware fill:#1E293B,stroke:#3B82F6,stroke-width:2px,color:#F8FAFC
    classDef edge fill:#0F172A,stroke:#10B981,stroke-width:2px,color:#F8FAFC
    classDef backend fill:#334155,stroke:#8B5CF6,stroke-width:2px,color:#F8FAFC
    classDef state fill:#4D1D1D,stroke:#F87171,stroke-width:2px,color:#F8FAFC
    classDef web fill:#1E293B,stroke:#F43F5E,stroke-width:2px,color:#F8FAFC

    B(("BLE Beacons")):::hardware

    subgraph SD1 ["Scanning Layer"]
        SC1["Pi Scanner"]:::edge
        SC2["ESP32 Node"]:::edge
    end

    subgraph SD2 ["Master Node - Pi"]
        MQTT[("MQTT Broker")]:::backend
        REDIS[["Redis State & Queue"]]:::state
        LOGIC{"Decision Engine"}:::state
    end

    subgraph SD3 ["Central Backend Hub"]
        API["Asset API - FastAPI"]:::backend
        DB[("PostgreSQL DB")]:::backend
    end

    UI["Web Dashboard"]:::web

    B -- "Advertising" --> SD1
    SD1 -- "MQTT" --> MQTT
    MQTT -- "Raw Stream" --> LOGIC
    LOGIC -- "Master State" --> REDIS
    LOGIC -- "REST Call" --> API
    API <--> DB
    API -- "API" --> UI"""

# 2. Hybrid Architecture
hybrid_mermaid = """graph TD
    classDef hardware fill:#1E293B,stroke:#3B82F6,stroke-width:2px,color:#F8FAFC
    classDef mobile fill:#1E1B4B,stroke:#6366F1,stroke-width:2px,color:#F8FAFC
    classDef backend fill:#334155,stroke:#8B5CF6,stroke-width:2px,color:#F8FAFC
    classDef state fill:#4D1D1D,stroke:#F87171,stroke-width:2px,color:#F8FAFC
    classDef web fill:#1E293B,stroke:#F43F5E,stroke-width:2px,color:#F8FAFC

    B(("BLE Beacons")):::hardware

    subgraph SH1 ["Scanner Nodes"]
        PI["Pi Scanner"]:::hardware
        TAB["Android Tab - BleX App"]:::mobile
    end

    subgraph SH2 ["Master Pi or Tab"]
        MBROKER[("MQTT Broker")]:::backend
        MREDIS[["Redis State & Queue"]]:::state
    end

    subgraph SH3 ["Central Storage"]
        MAPI["Asset API"]:::backend
        MDB[("PostgreSQL")]:::backend
    end

    MWEB["Web Dashboard"]:::web

    B -- "Raw Advertising" --> SH1
    SH1 -- "MQTT" --> MBROKER
    MBROKER -- "Decision Engine" --> MREDIS
    MREDIS -- "Persistence" --> MAPI
    MAPI <--> MDB
    MAPI -- "API" --> MWEB"""

# Execute
render_mermaid(standard_mermaid, 'architecture_standard.png')
render_mermaid(hybrid_mermaid, 'architecture_hybrid.png')
