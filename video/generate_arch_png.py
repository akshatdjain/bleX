import urllib.request
import json

mermaid_code = """
graph TD
    classDef hardware fill:#1E293B,stroke:#3B82F6,stroke-width:2px,color:#F8FAFC
    classDef edge fill:#0F172A,stroke:#10B981,stroke-width:2px,color:#F8FAFC
    classDef backend fill:#334155,stroke:#8B5CF6,stroke-width:2px,color:#F8FAFC
    classDef frontend fill:#1E293B,stroke:#F43F5E,stroke-width:2px,color:#F8FAFC
    classDef db fill:#020617,stroke:#EAB308,stroke-width:2px,color:#F8FAFC
    
    subgraph S1 [Physical Assets Layer]
        B1((BLE Beacon)):::hardware
    end

    subgraph S2 [Edge Computing - Raspberry Pi]
        N1[Ceiling Scanner]:::edge
        M1{Master Hub}:::edge
        N1 -. "RSSI Signal" .-> B1
        N1 -- "Raw Data" --> M1
    end

    subgraph S3 [Central Server Layer]
        MQTT[Mosquitto Broker]:::backend
        API[FastAPI Backend]:::backend
        DB[(PostgreSQL DB)]:::db
        M1 == "MQTT Telemetry" ==> MQTT
        MQTT <--> API
        API <--> DB
    end

    subgraph S4 [Presentation Layer]
        APP(Android Fleet Manager App):::frontend
        WEB(React Web Dashboard):::frontend
        MQTT -. "Live Topic" .-> APP
        MQTT -. "Live Topic" .-> WEB
        API -- "REST API" --> APP
        API -- "REST API" --> WEB
    end
"""

payload = json.dumps({
    "diagram_source": mermaid_code,
    "diagram_type": "mermaid",
    "output_format": "png"
}).encode('utf-8')

req = urllib.request.Request("https://kroki.io/", data=payload, headers={
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
})

try:
    with urllib.request.urlopen(req) as response:
        with open(r"c:\Users\Inno\Desktop\blegod\video\BleX_Pipeline_Architecture.png", "wb") as f:
            f.write(response.read())
    print("PNG downloaded successfully.")
except Exception as e:
    print(f"Failed to generate PNG: {e}")
