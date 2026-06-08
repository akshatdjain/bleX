"""
middleware.py — cross-cutting HTTP concerns (security headers, request id, etc).

Wired in main.py via add_security_headers as @app.middleware("http"). Any new
middleware (logging, request-id, rate-limit-by-ip) goes here.
"""
from fastapi import Request


CSP_DIRECTIVES = (
    "default-src 'self'; "
    "script-src 'self'; "
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
    "font-src 'self' https://fonts.gstatic.com; "
    "img-src 'self' data: blob:; "
    "connect-src 'self' https://sigmatic-asc.tech wss://sigmatic-asc.tech; "
    "frame-ancestors 'none'; "
    "base-uri 'self'; "
    "form-action 'self'"
)


async def security_headers_middleware(request: Request, call_next):
    """Defense-in-depth headers.

    - CSP: locks down which scripts/styles/connections the browser allows.
      Even if an XSS bug slips through, the attacker can't load their own JS.
    - X-Content-Type-Options: stops MIME sniffing.
    - X-Frame-Options: prevents the site being iframed (clickjacking).
    - Referrer-Policy: don't leak full URL to third parties.
    """
    response = await call_next(request)
    response.headers["Content-Security-Policy"] = CSP_DIRECTIVES
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    return response
