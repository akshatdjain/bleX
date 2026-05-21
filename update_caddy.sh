#!/bin/bash

# Target Caddyfile path
TARGET="/home/raghu/sonic/Caddyfile"

# Auto-detect the Gateway IP that the Caddy container uses to talk to the Host
# This is much more reliable than host.docker.internal on custom Linux networks
HOST_IP=$(docker inspect voice_caddy --format '{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}' | head -n1)

if [ -z "$HOST_IP" ]; then
    echo "❌ ERROR: Could not find Caddy's Gateway IP. Check if voice_caddy is running."
    exit 1
fi

echo "📍 Detected Caddy-to-Host Gateway IP: $HOST_IP"

# The desired Caddyfile content
cat <<EOF | sudo tee "$TARGET" > /dev/null
{
    debug
}

sigmatic-asc.tech, www.sigmatic-asc.tech {
    tls /etc/caddy/certs/sigmatic-asc_tech_fullchain.crt /etc/caddy/certs/sigmatic-asc.tech.pvt.key

    handle /mqtt {
        reverse_proxy host.docker.internal:9001
    }

    # --- UPDATED: Point to Host IP ---
    handle /health* {
        reverse_proxy host.docker.internal:8081
    }

    # --- UPDATED: Point to Host IP ---
    handle /process-audio* {
        reverse_proxy host.docker.internal:8081
    }

    handle /sonic* {
        reverse_proxy frontend:3000
    }

    # --- Strictly Separate Routing ---

    # 1. Background Engine (Devices & Asset Logic) -> Port 5000
    #    asset_api is now on sonic network, use container name for DNS resolution
    handle /asset* {
        reverse_proxy asset_api:8000 {
            header_up Host {upstream_hostport}
        }
    }
    
    # 2. Frontend UI API (Dashboard Data) -> Port 4000
    #    ui_api is now on sonic network, use container name for DNS resolution
    handle /api* {
        reverse_proxy ui_api:9000 {
            header_up Host {upstream_hostport}
        }
    }

    # 3. Frontend Site & Static Content -> Port 4000
    handle /beam* {
        uri strip_prefix /beam
        reverse_proxy ui_api:9000
    }
    handle /assets* {
        reverse_proxy ui_api:9000
    }
    handle /favicon.ico {
        reverse_proxy ui_api:9000
    }

    handle /ai* {
        uri strip_prefix /ai
        # Ensure the resulting path starts with a /
        rewrite * /{path} 
        reverse_proxy host.docker.internal:8003 {
            header_up Host {upstream_hostport}
            header_up X-Real-IP {remote_host}
        }
    }
    handle {
        reverse_proxy https://sigmatic.ai {
            header_up Host {upstream_hostport}
        }
    }
}
EOF

echo "✅ Successfully updated $TARGET"

# Reload Caddy
echo "🔄 Restarting Caddy service..."
cd /home/raghu/sonic
docker compose restart caddy

echo "✅ Caddy reloaded. Access /beam at https://sigmatic-asc.tech/beam"
