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

# High-Level Architectural Flow with multi-colors and icons
arch_mermaid = """graph LR
    %% Styling Definitions
    classDef edgeLayer fill:#E6FFFA,stroke:#38B2AC,stroke-width:2px,color:#234E52
    classDef hubLayer fill:#EBF8FF,stroke:#4299E1,stroke-width:2px,color:#2A4365
    classDef stateLayer fill:#FAF5FF,stroke:#9F7AEA,stroke-width:2px,color:#44337A
    classDef persistLayer fill:#F7FAFC,stroke:#718096,stroke-width:2px,color:#2D3748
    classDef uiLayer fill:#FFF5F5,stroke:#F56565,stroke-width:2px,color:#742A2A

    %% 🟢 LAYER 1: EDGE SENSING
    subgraph EDGE ["🟢 EDGE LAYER"]
        B1(("BLE Beacon")):::edgeLayer
        S1["Raspberry Pi Scanner"]:::edgeLayer
        S2["Android Hub Scanner"]:::edgeLayer
    end

    %% 🔵 LAYER 2: COMMUNICATION
    subgraph COMM ["🔵 TRANSPORT"]
        MQTT[("MQTT Broker (Mosquitto)")]:::hubLayer
    end

    %% 🟣 LAYER 3: INTELLIGENCE & STATE
    subgraph LOGIC ["🟣 HUB LOGIC (MASTER)"]
        ENGINE{"Decision Engine<br/>Triangulation"}:::stateLayer
        CACHE1[["Redis State Cache"]]:::stateLayer
        QUEUE1[("Redis Event Queue")]:::stateLayer
    end

    %% 🔘 LAYER 4: PERSISTENCE
    subgraph BACKEND ["🔘 BACKEND PERSISTENCE"]
        API["FastAPI (Asset API)"]:::persistLayer
        DB[("PostgreSQL DB")]:::persistLayer
    end

    %% 🔴 LAYER 5: VISUALIZATION
    subgraph DISPLAY ["🔴 PRESENTATION"]
        DASH["Web Dashboard"]:::uiLayer
        APP["Android App UI"]:::uiLayer
    end

    %% --- DATA FLOW ---
    B1 -- "ADV Broadcast" --> S1
    B1 -- "ADV Broadcast" --> S2
    
    S1 -- "BLE-over-JSON" --> MQTT
    S2 -- "BLE-over-JSON" --> MQTT
    
    MQTT -- "Raw Telemetry" --> ENGINE
    ENGINE -- "Read/Write" --> CACHE1
    ENGINE -- "RPUSH Movement" --> QUEUE1
    
    QUEUE1 -- "BLPOP Event" --> API
    API -- "Persistent Save" --> DB
    
    DB -.-> API
    API -- "REST API / Watch" --> DASH
    API -- "Configuration" --> APP
    
    %% Sync Path (Dashed)
    DASH -. "Map Sync" .-> API
    API -. "Notify" .-> ENGINE
"""

# Execute
output_file = 'system_architecture_diagram.png'
render_mermaid(arch_mermaid, output_file)
