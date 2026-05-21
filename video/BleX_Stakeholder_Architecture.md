# BleX Enterprise Architecture

This document provides a high-level system architecture diagram for the BleX asset tracking platform, designed for your stakeholders. It visualizes the flow of data from physical assets up to the Fleet Manager application.

```mermaid
graph TD
    %% Styling
    classDef hardware fill:#1E293B,stroke:#3B82F6,stroke-width:2px,color:#F8FAFC
    classDef edge fill:#0F172A,stroke:#10B981,stroke-width:2px,color:#F8FAFC
    classDef backend fill:#334155,stroke:#8B5CF6,stroke-width:2px,color:#F8FAFC
    classDef frontend fill:#1E293B,stroke:#F43F5E,stroke-width:2px,color:#F8FAFC
    
    %% Hardware Layer
    subgraph Physical Infrastructure [Zero-Touch Hardware Layer]
        B1((BLE Beacon<br/>Wheelchair)):::hardware
        B2((BLE Beacon<br/>IV Pump)):::hardware
        B3((BLE Beacon<br/>Staff ID)):::hardware
    end

    %% Edge Layer
    subgraph Edge Computing Layer [Local Processing & Triangulation]
        N1[Ceiling Scanner<br/>Node A]:::edge
        N2[Ceiling Scanner<br/>Node B]:::edge
        M1{Master Hub<br/>Zone Controller}:::edge
        
        N1 & N2 -. "BLE Signals (RSSI)" .-> B1 & B2 & B3
        N1 -- "Raw Payload" --> M1
        N2 -- "Raw Payload" --> M1
    end

    %% Network / Cloud Layer
    subgraph Central Server Layer [Intelligence & Storage]
        MQTT[MQTT Broker<br/>Real-Time Pipeline]:::backend
        API[FastAPI Backend<br/>REST Endpoints]:::backend
        DB[(PostgreSQL DB<br/>Asset & Movement Logs)]:::backend
        
        M1 == "Secure Telemetry" ==> MQTT
        MQTT <--> API
        API <--> DB
    end

    %% Presentation Layer
    subgraph Presentation Layer [User Experience]
        APP(Android App<br/>Jetpack Compose):::frontend
        WEB(Web Dashboard<br/>React/TS):::frontend
        WATCH(Watchdog<br/>Proactive Alerts):::frontend
        
        MQTT -. "Live Updates" .-> APP & WEB
        API -- "REST API (CRUD)" --> APP & WEB & WATCH
    end
```

### **Key Takeaways for Stakeholders:**
1. **Zero-Touch Hardware**: Assets only broadcast passive signals. They require no manual scanning or power-hungry GPS tracking.
2. **Edge Intelligence**: The "Master Hubs" calculate movement locally before sending data, drastically reducing network bandwidth and cloud costs.
3. **Sub-Second Real-Time**: The `MQTT Broker` ensures that the moment an asset enters a new zone, the apps on the nurses' or managers' phones update instantly without needing to manually refresh the page.
