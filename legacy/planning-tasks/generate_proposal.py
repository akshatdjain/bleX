#!/usr/bin/env python3
"""BleX Multi-Tenant Proposal — Professional PDF Generator"""

import os
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.lib.colors import HexColor, white, Color
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_JUSTIFY, TA_CENTER
from reportlab.platypus import (
    SimpleDocTemplate, Table, TableStyle, Paragraph,
    Spacer, PageBreak, Flowable, KeepTogether
)
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# ── Colors ────────────────────────────────────────────────────────────────────
C_DARK   = HexColor("#1A1A2E")
C_TEAL   = HexColor("#01B9C4")
C_GRAY   = HexColor("#F5F5F7")
C_BORDER = HexColor("#D1D5DB")
C_MUTED  = HexColor("#6B7280")
C_WHITE  = white

W, H  = letter
MARGIN = 0.65 * inch
CW    = W - 2 * MARGIN

# ── Fonts ─────────────────────────────────────────────────────────────────────
FB, FBold, FItal = "Helvetica", "Helvetica-Bold", "Helvetica-Oblique"

for name, path in [
    ("Calibri",        "C:/Windows/Fonts/calibri.ttf"),
    ("Calibri-Bold",   "C:/Windows/Fonts/calibrib.ttf"),
    ("Calibri-Italic", "C:/Windows/Fonts/calibrii.ttf"),
]:
    try:
        pdfmetrics.registerFont(TTFont(name, path))
    except Exception:
        pass

if "Calibri" in pdfmetrics.getRegisteredFontNames():
    FB, FBold, FItal = "Calibri", "Calibri-Bold", "Calibri-Italic"
    print("Using Calibri")
else:
    print("Using Helvetica (Calibri not found)")


# ── Numbered Canvas with header/footer ───────────────────────────────────────
class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        canvas.Canvas.__init__(self, *args, **kwargs)
        self._pg = 0

    def showPage(self):
        self._pg += 1
        self._decorate()
        canvas.Canvas.showPage(self)

    def _decorate(self):
        self.saveState()
        # Top header bar (pages 2+)
        if self._pg > 1:
            self.setFillColor(C_DARK)
            self.rect(0, H - 0.38 * inch, W, 0.38 * inch, fill=1, stroke=0)
            self.setFillColor(C_TEAL)
            self.rect(0, H - 0.38 * inch, W, 0.028 * inch, fill=1, stroke=0)
            self.setFillColor(C_WHITE)
            self.setFont(FBold, 8)
            self.drawString(MARGIN, H - 0.25 * inch, "BleX: Multi-Tenant Architecture and Bug Fix Proposal")
            self.setFont(FB, 8)
            self.setFillColor(C_TEAL)
            self.drawRightString(W - MARGIN, H - 0.25 * inch, "May 2026")
        # Footer rule
        self.setStrokeColor(C_BORDER)
        self.setLineWidth(0.5)
        self.line(MARGIN, 0.50 * inch, W - MARGIN, 0.50 * inch)
        self.setFont(FB, 8)
        self.setFillColor(C_MUTED)
        self.drawString(MARGIN, 0.32 * inch, "sigmatic.ai  |  Confidential")
        self.drawCentredString(W / 2, 0.32 * inch, f"Page {self._pg}")
        self.drawRightString(W - MARGIN, 0.32 * inch, "May 2026")
        self.restoreState()


# ── Custom Flowables ──────────────────────────────────────────────────────────
class CoverBanner(Flowable):
    def __init__(self, width, height=150):
        Flowable.__init__(self)
        self.width  = width
        self.height = height

    def draw(self):
        c = self.canv
        # Gradient-ish two-tone background
        c.setFillColor(C_DARK)
        c.rect(0, 0, self.width, self.height, fill=1, stroke=0)
        c.setFillColor(HexColor("#0E1B3A"))
        c.rect(0, self.height * 0.55, self.width, self.height * 0.45, fill=1, stroke=0)
        # Bottom teal strip
        c.setFillColor(C_TEAL)
        c.rect(0, 0, self.width, 6, fill=1, stroke=0)
        # Left teal accent
        c.rect(0, 6, 6, self.height - 6, fill=1, stroke=0)
        # Title lines
        c.setFillColor(C_WHITE)
        c.setFont(FBold, 22)
        c.drawString(20, self.height - 50, "BleX: Multi-Tenant Architecture")
        c.drawString(20, self.height - 76, "and Bug Fix Proposal")
        # Teal divider
        c.setStrokeColor(C_TEAL)
        c.setLineWidth(0.8)
        c.line(20, self.height - 90, self.width - 20, self.height - 90)
        # Subtitle
        c.setFillColor(C_TEAL)
        c.setFont(FB, 11)
        c.drawString(20, self.height - 108, "Technical Proposal   |   May 2026")
        # Meta
        c.setFillColor(HexColor("#94A3B8"))
        c.setFont(FB, 9)
        c.drawString(20, self.height - 128, "Proposed by Akshat   |   sigmatic.ai")


class AccentHeading(Flowable):
    def __init__(self, text, width, size=12):
        Flowable.__init__(self)
        self.text  = text
        self.width = width
        self.size  = size
        self.height = size + 16

    def draw(self):
        c = self.canv
        c.setFillColor(C_TEAL)
        c.rect(0, 3, 4, self.height - 8, fill=1, stroke=0)
        c.setFillColor(C_DARK)
        c.setFont(FBold, self.size)
        c.drawString(12, self.height / 2 - self.size / 3 + 1, self.text)


class PipelineDiagram(Flowable):
    def __init__(self, steps, width, height=76, caption=""):
        Flowable.__init__(self)
        self.steps   = steps
        self.width   = width
        self.height  = height
        self.caption = caption
        n = len(steps)
        self.bw = (width - (n - 1) * 16) / n
        self.bh = 40

    def draw(self):
        c   = self.canv
        by  = self.height - self.bh - 14
        mid = by + self.bh / 2

        for i, (label, sub, color) in enumerate(self.steps):
            x = i * (self.bw + 16)
            # Box
            c.setFillColor(HexColor(color))
            c.roundRect(x, by, self.bw, self.bh, 5, fill=1, stroke=0)
            # Top highlight strip
            c.setFillColor(Color(1, 1, 1, alpha=0.12))
            c.roundRect(x, by + self.bh * 0.62, self.bw, self.bh * 0.38, 5, fill=1, stroke=0)
            # Label
            c.setFillColor(C_WHITE)
            c.setFont(FBold, 8)
            c.drawCentredString(x + self.bw / 2, by + 24, label)
            if sub:
                c.setFillColor(HexColor("#B2EEF4"))
                c.setFont(FItal, 7)
                c.drawCentredString(x + self.bw / 2, by + 12, sub)
            # Arrow
            if i < len(self.steps) - 1:
                ax = x + self.bw + 2
                c.setStrokeColor(C_BORDER)
                c.setFillColor(C_TEAL)
                c.setLineWidth(1.2)
                c.line(ax, mid, ax + 11, mid)
                p = c.beginPath()
                p.moveTo(ax + 14, mid); p.lineTo(ax + 9, mid + 4); p.lineTo(ax + 9, mid - 4); p.close()
                c.drawPath(p, fill=1, stroke=0)

        if self.caption:
            c.setFillColor(C_MUTED)
            c.setFont(FItal, 8)
            c.drawCentredString(self.width / 2, 3, self.caption)


class TierDiagram(Flowable):
    """Two fixed-coordinate cards side by side. No relative math."""
    def __init__(self, width, height=148):
        Flowable.__init__(self)
        self.width  = width
        self.height = height

    def _box(self, c, x, y, w, h, bg, header_color, title, lines):
        """Draw a card: bg rect, teal header strip, title, body lines — all text on top."""
        # Background
        c.setFillColor(HexColor(bg))
        c.roundRect(x, y, w, h, 6, fill=1, stroke=0)
        # Header strip (top 16pt)
        c.setFillColor(HexColor(header_color))
        c.roundRect(x, y + h - 16, w, 16, 4, fill=1, stroke=0)
        # Title in header
        c.setFillColor(C_DARK)
        c.setFont(FBold, 7)
        c.drawCentredString(x + w / 2, y + h - 10, title)
        # Body lines
        c.setFillColor(C_WHITE)
        c.setFont(FB, 8)
        line_h = 13
        start_y = y + h - 30
        for i, line in enumerate(lines):
            c.drawCentredString(x + w / 2, start_y - i * line_h, line)

    def draw(self):
        c   = self.canv
        W   = self.width
        H   = self.height

        # ── Fixed coordinates (bottom-left origin) ────────────────────
        # Left card: pool   — x=0,  y=22, w=card_w, h=card_h
        # Right card: dedicated — x=card_w+gap, y=22
        GAP    = 18
        CARD_W = (W - GAP) / 2
        CARD_H = 100
        CY     = 22   # cards sit 22pt from bottom (room for caption)

        # ── PASS 1: backgrounds ───────────────────────────────────────
        self._box(c, 0, CY, CARD_W, CARD_H,
                  bg="#0B3D4A", header_color="#01B9C4",
                  title="master_pool",
                  lines=["All new tenants start here",
                         "Wildcard: ble/+/scanner/#",
                         "State partitioned by tenant_id",
                         "Shared process, low overhead"])

        self._box(c, CARD_W + GAP, CY, CARD_W, CARD_H,
                  bg="#082C3A", header_color="#017A85",
                  title="master_dedicated_{tenant_id}",
                  lines=["One container per large tenant",
                         "Subscribes: ble/{id}/scanner/#",
                         "Fully isolated state",
                         "Restart without affecting others"])

        # ── PASS 2: arrow between cards ───────────────────────────────
        # Horizontal arrow at mid-height of cards
        arr_y  = CY + CARD_H / 2
        arr_x1 = CARD_W + 2
        arr_x2 = CARD_W + GAP - 2
        c.setStrokeColor(C_TEAL)
        c.setFillColor(C_TEAL)
        c.setLineWidth(1.2)
        c.line(arr_x1, arr_y, arr_x2 - 5, arr_y)
        p = c.beginPath()
        p.moveTo(arr_x2, arr_y)
        p.lineTo(arr_x2 - 5, arr_y + 4)
        p.lineTo(arr_x2 - 5, arr_y - 4)
        p.close()
        c.drawPath(p, fill=1, stroke=0)

        # ── PASS 3: labels above cards and bottom caption ─────────────
        label_y = CY + CARD_H + 8

        c.setFillColor(C_DARK)
        c.setFont(FBold, 8)
        c.drawCentredString(CARD_W / 2, label_y, "Pooled Tier  (default)")
        c.drawCentredString(CARD_W + GAP + CARD_W / 2, label_y, "Dedicated Tier  (> 100 assets)")

        # Arrow label
        c.setFillColor(C_TEAL)
        c.setFont(FBold, 6.5)
        c.drawCentredString(CARD_W + GAP / 2, arr_y + 6, "promote")

        # Caption
        c.setFillColor(C_MUTED)
        c.setFont(FItal, 8)
        c.drawCentredString(W / 2, 8, "Cron job checks asset count every 5 min. Demotes back to pool if count drops below 20.")


# ── Styles ────────────────────────────────────────────────────────────────────
def S(name, **kw):
    base = dict(fontName=FB, fontSize=10, textColor=C_DARK, leading=15)
    base.update(kw)
    return ParagraphStyle(name, **base)

st_body  = S("body",  alignment=TA_JUSTIFY, spaceAfter=6)
st_bullet= S("bullet",leftIndent=16, firstLineIndent=-10, spaceAfter=5, leading=14)
st_cell  = S("cell",  fontSize=9, leading=13)
st_cellb = S("cellb", fontSize=9, leading=13, fontName=FBold, textColor=C_WHITE)

def H1(text):  return [AccentHeading(text, CW, size=12), Spacer(1, 5)]
def H2(text):  return [AccentHeading(text, CW, size=10), Spacer(1, 4)]
def BP(text):  return Paragraph(f"- {text}", st_bullet)
def TP(text):  return Paragraph(text, st_cell)
def TPB(text): return Paragraph(text, st_cellb)


# ── Build ─────────────────────────────────────────────────────────────────────
OUT = "O:/blex/tasks/blex-multitenant-proposal.pdf"
os.makedirs(os.path.dirname(OUT), exist_ok=True)

doc = SimpleDocTemplate(
    OUT, pagesize=letter,
    leftMargin=MARGIN, rightMargin=MARGIN,
    topMargin=MARGIN,  bottomMargin=0.80 * inch,
    canvasmaker=NumberedCanvas
)

story = []

# ═══════════════════════════════════════════ PAGE 1 ══════
story.append(CoverBanner(CW))
story.append(Spacer(1, 0.20 * inch))

story += H1("Background")
story.append(Paragraph(
    "BleX is a BLE asset tracking platform for industrial and commercial deployments. "
    "An Android tablet acts as a local hub: it runs an embedded MQTT broker, scans BLE beacons "
    "directly, and bridges data to a DGX server. A Python master engine on the DGX server "
    "applies zone logic (Kalman filter, hysteresis, dwell-time) and stores zone-change events "
    "via a FastAPI backend in PostgreSQL. The platform is currently single-tenant. Three demo "
    "deployments are in progress and the system needs to support 20 to 200 tenants.",
    st_body
))
story.append(Spacer(1, 6))

story.append(PipelineDiagram(
    steps=[
        ("BLE Beacon",  "asset tag",   "#334155"),
        ("Pi / Android","scanner",     "#0E6B7A"),
        ("Tablet MQTT", "local broker","#017A85"),
        ("DGX Master",  "zone logic",  "#0E4D59"),
        ("PostgreSQL",  "storage",     "#1E3A5F"),
    ],
    width=CW, caption="Current data flow (single-tenant)"
))
story.append(Spacer(1, 10))

story += H1("Current Issues")
story.append(Paragraph(
    "<b>Issue 1 - Scanner Setup Bug.</b>  When an IT person pushes wrong WiFi credentials "
    "to a Pi scanner via the Android app, the Pi drops off the setup network and gets stuck "
    "with no connectivity. Recovery requires manually power cycling the device.",
    st_body
))
story.append(Paragraph(
    "<b>Issue 2 - No Tenant Isolation.</b>  All scanners publish to the same MQTT topic "
    "namespace. Two customers' data collide on a shared broker. There is no tenant concept "
    "in the database, API, or master engine.",
    st_body
))

story.append(PageBreak())

# ═══════════════════════════════════════════ PAGE 2 ══════
story += H1("Fix 1: Scanner WiFi Provisioning Recovery")

story += H2("How setup works")
for t in [
    "Pi boots and connects to the tablet hotspot (setup WiFi, credentials baked into the Pi image).",
    "Pi appears in the BleX app under Configurator then Scanners via UDP discovery on the shared LAN.",
    "IT person selects the Pi and enters site WiFi credentials (hospital or office network).",
    "App sends credentials to the Pi over HTTP on port 8888.",
    "Pi attempts to connect to the new network.",
]:
    story.append(BP(t))

story.append(Spacer(1, 8))
story.append(PipelineDiagram(
    steps=[
        ("App sends",  "creds",        "#334155"),
        ("Pi tries",   "new WiFi",     "#0E6B7A"),
        ("Watchdog",   "60s poll",     "#017A85"),
        ("Success",    "site network", "#0E7A5B"),
        ("Timeout",    "back to setup","#7A2E0E"),
    ],
    width=CW, caption="Watchdog confirms connection before dropping the setup fallback"
))
story.append(Spacer(1, 8))

story += H2("The bug")
story.append(Paragraph(
    "The current code tears down the setup network connection before verifying the new one works. "
    "It fires the connect command without waiting for a result. If the new network is unreachable, "
    "the Pi has nowhere to fall back to.",
    st_body
))

story.append(Spacer(1, 4))
story += H2("The fix")
for t in [
    "Keep the setup network connection active until the new WiFi connection is confirmed working.",
    "Start a background watchdog after pushing credentials: poll for connectivity every 5 seconds for up to 60 seconds.",
    "On success: confirm connected to the app, then optionally tear down the setup network.",
    "On timeout: delete the bad WiFi profile, reconnect to the setup network, report failure to the app.",
]:
    story.append(BP(t))

story.append(Spacer(1, 6))
story += H2("Status endpoint")
story.append(Paragraph(
    "A new GET /status endpoint on the Pi returns the current state (idle, connecting, connected, "
    "or failed). The Android app polls this every 3 seconds and shows live feedback: a spinner "
    "while connecting, a success message on completion, or a retry prompt on failure.",
    st_body
))
story.append(Spacer(1, 6))
story.append(Paragraph(
    "<b>Affected files.</b>  current/scanner/provisioner_service.py   |   "
    "provisioner/esp32/provision_listener.py   |   Android HotspotTab.kt",
    st_body
))

story.append(PageBreak())

# ═══════════════════════════════════════════ PAGE 3 ══════
story += H1("Fix 2: Multi-Tenant Architecture")

story += H2("MQTT Topic Namespacing")
for t in [
    "Current format:   ble/scanner/{mac}",
    "New format:       ble/{tenant_id}/scanner/{mac}",
    "Example:          ble/dave_house/scanner/AA:BB:CC:DD:EE:FF",
]:
    story.append(BP(t))
story.append(Paragraph(
    "The Pi provisioner writes the tenant_id to local config when the IT person sets up the scanner. "
    "The Android app prefixes its own BLE scan publications with the same tenant ID. "
    "Old Pi images without a tenant_id fall back to the original topic format.",
    st_body
))
story.append(Spacer(1, 8))

story += H2("Database: Schema Per Tenant")
story.append(Paragraph(
    "Each tenant gets a dedicated PostgreSQL schema (e.g. tenant_dave_house) with identical table "
    "structures. A global shared schema holds the tenant registry. The FastAPI API routes each "
    "request to the correct schema via an X-Tenant-ID header using SQLAlchemy's schema_translate_map. "
    "Existing data migrates to a tenant_default schema. A missed query filter cannot expose "
    "one customer's data to another because schemas are physically separate.",
    st_body
))
story.append(Spacer(1, 8))

story += H2("Master Engine: Tiered Containers")
story.append(Paragraph(
    "Zone decision logic runs in two container types based on tenant size:",
    st_body
))
story.append(BP(
    "<b>Pool container.</b>  One shared container subscribes to all tenants via a wildcard MQTT topic. "
    "In-memory state is partitioned by tenant ID. All new tenants start here."
))
story.append(BP(
    "<b>Dedicated container.</b>  One container per large tenant, subscribing only to that "
    "tenant's topic. Fully isolated state, restartable independently without affecting others."
))
story.append(Spacer(1, 8))
story.append(TierDiagram(CW, height=122))
story.append(Spacer(1, 6))
story.append(Paragraph(
    "A cron job runs every 5 minutes and checks asset counts per tenant. Tenants over 100 "
    "registered assets are promoted to a dedicated container automatically. Tenants that drop "
    "below 20 return to the pool. Zone state rebuilds from live MQTT within 30 seconds of "
    "startup: no state handoff logic needed.",
    st_body
))

story.append(PageBreak())

# ═══════════════════════════════════════════ PAGE 4 ══════
story += H1("Implementation Phases")
story.append(Spacer(1, 6))

phases_data = [
    [TPB("Phase"), TPB("Tasks"), TPB("Files / Components")],
    [
        Paragraph("<b>1 - Bug Fix</b>\n(Immediate)", st_cell),
        TP("Fix Pi provisioner watchdog. Fix ESP32 provisioner. Update Android Hotspot tab with live status polling."),
        TP("provisioner_service.py\nHotspotTab.kt"),
    ],
    [
        Paragraph("<b>2 - Tenant\nIdentity</b>", st_cell),
        TP("Add tenant_id to Pi MQTT topics. Prefix Android scanner publications. Push tenant config from provisioner to app."),
        TP("scanner.py\nMqttManager.kt\nSettingsManager.kt"),
    ],
    [
        Paragraph("<b>3 - DGX\nBackend</b>", st_cell),
        TP("PostgreSQL schema migration. FastAPI tenant routing. Master pool container. Dedicated container mode. FIFO consumer multi-tenant. Tier check cron job."),
        TP("asset_api routers\nmaster_pool.py\ncheck_tiers.py\ndocker-compose.yml"),
    ],
    [
        Paragraph("<b>4 - Cleanup</b>", st_cell),
        TP("Remove master zone logic from Pi image. Archive dead code directory."),
        TP("Pi image build\ntags_tracking archive"),
    ],
]

phases_table = Table(phases_data, colWidths=[1.1 * inch, 3.05 * inch, 1.85 * inch], repeatRows=1)
phases_table.setStyle(TableStyle([
    ("BACKGROUND",    (0, 0), (-1, 0),  C_DARK),
    ("TEXTCOLOR",     (0, 0), (-1, 0),  C_WHITE),
    ("LINEBELOW",     (0, 0), (-1, 0),  1.5, C_TEAL),
    ("ROWBACKGROUNDS",(0, 1), (-1, -1), [C_WHITE, C_GRAY]),
    ("BACKGROUND",    (0, 1), (0, -1),  HexColor("#EEF9FA")),
    ("GRID",          (0, 0), (-1, -1), 0.4, C_BORDER),
    ("TOPPADDING",    (0, 0), (-1, -1), 7),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ("LEFTPADDING",   (0, 0), (-1, -1), 8),
    ("RIGHTPADDING",  (0, 0), (-1, -1), 8),
    ("VALIGN",        (0, 0), (-1, -1), "TOP"),
]))
story.append(phases_table)
story.append(Spacer(1, 16))

story += H1("Initial Tenants")
story.append(Spacer(1, 6))

tenants_data = [
    [TPB("Tenant ID"), TPB("Owner"), TPB("Starting Tier")],
    [TP("dave_house"), TP("Dave"),   TP("Pool")],
    [TP("ehab_house"), TP("Ehab"),   TP("Pool")],
    [TP("raghu_home"), TP("Raghu"),  TP("Pool")],
]

tenants_table = Table(tenants_data, colWidths=[1.9 * inch, 2.5 * inch, 1.6 * inch])
tenants_table.setStyle(TableStyle([
    ("BACKGROUND",    (0, 0), (-1, 0),  C_DARK),
    ("TEXTCOLOR",     (0, 0), (-1, 0),  C_WHITE),
    ("LINEBELOW",     (0, 0), (-1, 0),  1.5, C_TEAL),
    ("ROWBACKGROUNDS",(0, 1), (-1, -1), [C_WHITE, C_GRAY]),
    ("GRID",          (0, 0), (-1, -1), 0.4, C_BORDER),
    ("TOPPADDING",    (0, 0), (-1, -1), 6),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ("LEFTPADDING",   (0, 0), (-1, -1), 8),
    ("RIGHTPADDING",  (0, 0), (-1, -1), 8),
    ("VALIGN",        (0, 0), (-1, -1), "TOP"),
]))
story.append(tenants_table)
story.append(Spacer(1, 16))

story += H1("Open Items")
story.append(Spacer(1, 4))
for t in [
    "MQTT ACL per tenant: currently a shared password file. Per-tenant credentials to be added in a later phase.",
    "Promotion thresholds: 100 assets to promote, 20 to demote. To be tuned after first real deployments.",
    "FIFO consumer: single consumer with multi-queue polling for now, scale independently if load requires it.",
]:
    story.append(BP(t))

# Build
doc.build(story)
sz = os.path.getsize(OUT)
print(f"Created: {OUT}")
print(f"Size:    {sz:,} bytes")
