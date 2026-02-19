#!/bin/bash

# Target Caddyfile path
TARGET="/home/raghu/sonic/Caddyfile"

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

    handle /beam* {
        uri strip_prefix /beam
        reverse_proxy host.docker.internal:4000
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
echo "🔄 Reloading Caddy service..."
cd /home/raghu/sonic
docker compose reload caddy

echo "✅ Caddy reloaded. Access /beam at https://sigmatic-asc.tech/beam"
