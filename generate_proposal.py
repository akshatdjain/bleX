#!/usr/bin/env python3
"""
BleX Multi-Tenant Architecture and Bug Fix Proposal
Generates a professional PDF using reportlab
"""

import os
from datetime import datetime
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
pt = 1  # 1 point = 1 reportlab unit
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak,
    KeepTogether, PageTemplate, Frame
)
from reportlab.pdfgen import canvas

# Colors
COLOR_DARK = HexColor("#1A1A2E")
COLOR_TEAL = HexColor("#01B9C4")
COLOR_LIGHT_GRAY = HexColor("#F0F0F0")

# Margins
MARGIN = 50 * pt

# Custom page template with page numbers
class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        canvas.Canvas.__init__(self, *args, **kwargs)
        self._pagenum = 0

    def showPage(self):
        self._pagenum += 1
        self.drawPageNumber()
        canvas.Canvas.showPage(self)

    def drawPageNumber(self):
        width, height = letter
        self.setFont("Helvetica", 9)
        self.setFillColor(COLOR_DARK)
        page_text = str(self._pagenum)
        self.drawCentredString(width / 2, 30 * pt, page_text)

# Create custom styles
styles = getSampleStyleSheet()

style_title = ParagraphStyle(
    'CustomTitle',
    parent=styles['Heading1'],
    fontSize=28,
    textColor=COLOR_DARK,
    spaceAfter=6,
    fontName='Helvetica-Bold',
    alignment=0
)

style_subtitle = ParagraphStyle(
    'CustomSubtitle',
    parent=styles['Normal'],
    fontSize=12,
    textColor=COLOR_DARK,
    spaceAfter=24,
    fontName='Helvetica',
    alignment=0
)

style_section_heading = ParagraphStyle(
    'SectionHeading',
    parent=styles['Heading1'],
    fontSize=12,
    textColor=COLOR_TEAL,
    spaceAfter=12,
    spaceBefore=12,
    fontName='Helvetica-Bold',
    alignment=0
)

style_subsection_heading = ParagraphStyle(
    'SubsectionHeading',
    parent=styles['Heading2'],
    fontSize=10,
    textColor=COLOR_DARK,
    spaceAfter=10,
    spaceBefore=8,
    fontName='Helvetica-Bold',
    alignment=0
)

style_body = ParagraphStyle(
    'CustomBody',
    parent=styles['Normal'],
    fontSize=10,
    textColor=COLOR_DARK,
    spaceAfter=8,
    leading=14,
    fontName='Helvetica',
    alignment=4
)

style_bullet = ParagraphStyle(
    'Bullet',
    parent=styles['Normal'],
    fontSize=10,
    textColor=COLOR_DARK,
    spaceAfter=6,
    leading=13,
    fontName='Helvetica',
    leftIndent=20,
    alignment=4
)

# Build the document
output_path = "O:/blex/tasks/blex-multitenant-proposal.pdf"
os.makedirs(os.path.dirname(output_path), exist_ok=True)

doc = SimpleDocTemplate(
    output_path,
    pagesize=letter,
    rightMargin=MARGIN,
    leftMargin=MARGIN,
    topMargin=MARGIN,
    bottomMargin=MARGIN,
    canvasmaker=NumberedCanvas
)

story = []

# PAGE 1: COVER AND OVERVIEW
story.append(Paragraph("BleX: Multi-Tenant Architecture and Bug Fix Proposal", style_title))
story.append(Spacer(1, 6 * pt))

# Teal divider line
divider_table = Table([['']],  colWidths=[7.5 * 72 - 2 * MARGIN])
divider_table.setStyle(TableStyle([
    ('LINEBELOW', (0, 0), (0, 0), 2, COLOR_TEAL),
    ('TOPPADDING', (0, 0), (0, 0), 0),
    ('BOTTOMPADDING', (0, 0), (0, 0), 0),
]))
story.append(divider_table)
story.append(Spacer(1, 8 * pt))

story.append(Paragraph("Technical Proposal - May 2026", style_subtitle))
story.append(Spacer(1, 12 * pt))

# Background section
section = KeepTogether([
    Paragraph("Background", style_section_heading),
    Paragraph(
        "BleX is a BLE asset tracking platform. An Android tablet acts as a local hub running an embedded MQTT broker, scanning BLE beacons and forwarding data. A Python master engine on a DGX server subscribes to MQTT, applies zone logic, and stores zone-change events via a FastAPI backend in PostgreSQL. The current system is single-tenant. Three demo deployments are planned (Dave, Ehab, Raghu) and the system needs to support 20 to 200 tenants going forward.",
        style_body
    ),
])
story.append(section)
story.append(Spacer(1, 12 * pt))

# Current Issues section
section = KeepTogether([
    Paragraph("Current Issues", style_section_heading),
    Paragraph(
        "<b>Issue 1 - Scanner Setup Bug:</b> When an IT person pushes wrong WiFi credentials to a Pi scanner via the Android app, the Pi drops off the setup network and gets stuck with no connectivity. Recovery requires manually power cycling the device.",
        style_body
    ),
    Spacer(1, 8 * pt),
    Paragraph(
        "<b>Issue 2 - No Tenant Isolation:</b> All scanners publish to the same MQTT topic namespace. Two customers data would collide on a shared broker. There is no tenant concept in the database, API, or master engine.",
        style_body
    ),
])
story.append(section)
story.append(PageBreak())

# PAGE 2: FIX 1 - SCANNER PROVISIONING
story.append(Paragraph("Fix 1: Scanner WiFi Provisioning Recovery", style_section_heading))
story.append(Spacer(1, 10 * pt))

story.append(Paragraph("How setup works:", style_subsection_heading))
story.append(Paragraph("- Pi boots and connects to the tablet hotspot (\"setup\" WiFi, credentials baked into the Pi image)", style_bullet))
story.append(Paragraph("- Pi appears in the BleX app under Configurator then Scanners via UDP discovery", style_bullet))
story.append(Paragraph("- IT person selects the Pi and enters site WiFi credentials (hospital or office network)", style_bullet))
story.append(Paragraph("- App sends credentials to the Pi over HTTP on port 8888", style_bullet))
story.append(Paragraph("- Pi attempts to connect to the new network", style_bullet))
story.append(Spacer(1, 10 * pt))

story.append(Paragraph("The bug:", style_subsection_heading))
story.append(Paragraph(
    "The current code tears down the setup network connection before verifying the new one works. It fires the connect command without waiting for a result. If the new network is unreachable, the Pi has nowhere to fall back to.",
    style_body
))
story.append(Spacer(1, 10 * pt))

story.append(Paragraph("The fix:", style_subsection_heading))
story.append(Paragraph("- Keep the setup network connection active until the new WiFi connection is confirmed working", style_bullet))
story.append(Paragraph("- Start a background watchdog after pushing credentials, polling for connectivity every 5 seconds for up to 60 seconds", style_bullet))
story.append(Paragraph("- On success: confirm connected, optionally tear down setup network", style_bullet))
story.append(Paragraph("- On timeout: delete the bad WiFi profile, reconnect to setup network, report failure to the app", style_bullet))
story.append(Spacer(1, 10 * pt))

story.append(Paragraph("Status endpoint:", style_subsection_heading))
story.append(Paragraph(
    "A new GET /status endpoint on the Pi returns the current connection state (idle, connecting, connected, or failed). The Android app polls this every 3 seconds and shows live feedback to the IT person.",
    style_body
))
story.append(Spacer(1, 8 * pt))

story.append(Paragraph(
    "<b>Affected files:</b> current/scanner/provisioner_service.py, provisioner/esp32/provision_listener.py, Android HotspotTab.kt",
    style_body
))
story.append(PageBreak())

# PAGE 3: FIX 2 - MULTI-TENANT ARCHITECTURE
story.append(Paragraph("Fix 2: Multi-Tenant Architecture", style_section_heading))
story.append(Spacer(1, 10 * pt))

# MQTT Topic Namespacing
story.append(Paragraph("MQTT Topic Namespacing", style_subsection_heading))
story.append(Paragraph("Current format: ble/scanner/{mac}", style_bullet))
story.append(Paragraph("New format: ble/{tenant_id}/scanner/{mac}", style_bullet))
story.append(Paragraph("Example: ble/dave_house/scanner/AA:BB:CC:DD:EE:FF", style_bullet))
story.append(Paragraph(
    "The Pi provisioner writes the tenant_id to its local config when the IT person sets up the scanner. The Android app also prefixes its BLE scan publications with the tenant ID.",
    style_body
))
story.append(Spacer(1, 10 * pt))

# Database: Schema Per Tenant
story.append(Paragraph("Database: Schema Per Tenant", style_subsection_heading))
story.append(Paragraph(
    "Each tenant gets a dedicated PostgreSQL schema (e.g. tenant_dave_house) containing identical table structures. A shared schema holds the global tenant registry. The FastAPI API routes each request to the correct schema based on an X-Tenant-ID request header. Existing data migrates to a tenant_default schema. This design means a missed query filter cannot expose one customer's data to another.",
    style_body
))
story.append(Spacer(1, 10 * pt))

# Master Engine: Tiered Containers
story.append(Paragraph("Master Engine: Tiered Containers", style_subsection_heading))
story.append(Paragraph(
    "Two container types handle zone decision logic.",
    style_body
))
story.append(Spacer(1, 6 * pt))
story.append(Paragraph(
    "<b>Pool container:</b> one shared container subscribes to all tenants via a wildcard MQTT topic. State is partitioned in memory by tenant ID. All new tenants start here.",
    style_body
))
story.append(Spacer(1, 6 * pt))
story.append(Paragraph(
    "<b>Dedicated container:</b> one container per large tenant, subscribing only to that tenant's topic. Isolated state, restartable independently without affecting other tenants.",
    style_body
))
story.append(Spacer(1, 6 * pt))
story.append(Paragraph(
    "A cron job runs every 5 minutes and checks asset counts per tenant. Tenants with over 100 registered assets are promoted to a dedicated container automatically. Tenants that drop below 20 assets return to the pool. Zone state does not need to be transferred on promotion because scanners publish every 3 seconds and full state rebuilds within 30 seconds.",
    style_body
))
story.append(PageBreak())

# PAGE 4: IMPLEMENTATION PLAN
story.append(Paragraph("Implementation Phases", style_section_heading))
story.append(Spacer(1, 10 * pt))

# Implementation table
phases_data = [
    ["Phase", "Tasks", "Components"],
    ["Phase 1 - Bug Fix\n(Immediate)",
     "Fix Pi provisioner watchdog, fix ESP32 provisioner, update Android Hotspot tab with status polling",
     "provisioner_service.py, HotspotTab.kt"],
    ["Phase 2 - Tenant\nIdentity",
     "Add tenant_id to Pi MQTT topics, prefix Android scanner publications, push tenant config from provisioner to app",
     "scanner.py, MqttManager.kt, SettingsManager.kt"],
    ["Phase 3 - DGX\nBackend",
     "PostgreSQL schema migration, FastAPI tenant routing, master pool container, dedicated container mode, FIFO consumer multi-tenant, tier check cron",
     "asset_api, master_pool.py, check_tiers.py"],
    ["Phase 4 - Cleanup",
     "Slim Pi image (remove master logic from device), archive dead code",
     "Pi image build, tags_tracking"],
]

phases_table = Table(phases_data, colWidths=[1.2 * 72, 2.5 * 72, 1.5 * 72])
phases_table.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), COLOR_LIGHT_GRAY),
    ('TEXTCOLOR', (0, 0), (-1, 0), COLOR_DARK),
    ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
    ('VALIGN', (0, 0), (-1, -1), 'TOP'),
    ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
    ('FONTSIZE', (0, 0), (-1, 0), 9),
    ('BOTTOMPADDING', (0, 0), (-1, 0), 6),
    ('TOPPADDING', (0, 0), (-1, 0), 6),
    ('BACKGROUND', (0, 1), (-1, -1), '#FFFFFF'),
    ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
    ('FONTSIZE', (0, 1), (-1, -1), 9),
    ('TOPPADDING', (0, 1), (-1, -1), 6),
    ('BOTTOMPADDING', (0, 1), (-1, -1), 6),
    ('GRID', (0, 0), (-1, -1), 0.5, COLOR_DARK),
    ('ROWBACKGROUNDS', (0, 1), (-1, -1), ['#FFFFFF', '#F9F9F9']),
]))
story.append(phases_table)
story.append(Spacer(1, 14 * pt))

# Initial Tenants
story.append(Paragraph("Initial Tenants", style_section_heading))
story.append(Spacer(1, 10 * pt))

tenants_data = [
    ["Tenant ID", "Owner", "Starting Tier"],
    ["dave_house", "Dave (VP Engineering)", "Pool"],
    ["ehab_house", "Ehab", "Pool"],
    ["raghu_home", "Raghu (Manager)", "Pool"],
]

tenants_table = Table(tenants_data, colWidths=[2 * 72, 2.5 * 72, 1.2 * 72])
tenants_table.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), COLOR_LIGHT_GRAY),
    ('TEXTCOLOR', (0, 0), (-1, 0), COLOR_DARK),
    ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
    ('VALIGN', (0, 0), (-1, -1), 'TOP'),
    ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
    ('FONTSIZE', (0, 0), (-1, 0), 9),
    ('BOTTOMPADDING', (0, 0), (-1, 0), 6),
    ('TOPPADDING', (0, 0), (-1, 0), 6),
    ('BACKGROUND', (0, 1), (-1, -1), '#FFFFFF'),
    ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
    ('FONTSIZE', (0, 1), (-1, -1), 9),
    ('TOPPADDING', (0, 1), (-1, -1), 6),
    ('BOTTOMPADDING', (0, 1), (-1, -1), 6),
    ('GRID', (0, 0), (-1, -1), 0.5, COLOR_DARK),
    ('ROWBACKGROUNDS', (0, 1), (-1, -1), ['#FFFFFF', '#F9F9F9']),
]))
story.append(tenants_table)
story.append(Spacer(1, 14 * pt))

# Open Items
story.append(Paragraph("Open Items", style_section_heading))
story.append(Spacer(1, 8 * pt))
story.append(Paragraph("- MQTT ACL per tenant: currently a shared password file. Per-tenant credentials to be added later.", style_bullet))
story.append(Paragraph("- Promotion thresholds: 100 assets to promote, 20 to demote. To be tuned after first real deployments.", style_bullet))
story.append(Paragraph("- FIFO consumer: single consumer with multi-queue polling for now, scale independently if needed.", style_bullet))
story.append(Spacer(1, 20 * pt))

# Footer
story.append(Paragraph("Prepared by Akshat - May 2026", style_body))

# Build PDF
doc.build(story)

# Print confirmation
file_size = os.path.getsize(output_path)
print(f"PDF created successfully: {output_path}")
print(f"File size: {file_size:,} bytes")
