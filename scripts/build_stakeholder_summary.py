#!/usr/bin/env python3
"""Generate a one-page stakeholder/VP summary of shipped platform features."""
from docx import Document
from docx.shared import Pt, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

INK = RGBColor(0x1A, 0x1A, 0x2E)
ACCENT = RGBColor(0x2B, 0x5C, 0xE6)
MUTE = RGBColor(0x5A, 0x5A, 0x6E)
GOOD = RGBColor(0x0E, 0x7A, 0x4B)

doc = Document()

# --- tight one-page geometry ---
sec = doc.sections[0]
sec.top_margin = Inches(0.45)
sec.bottom_margin = Inches(0.4)
sec.left_margin = Inches(0.6)
sec.right_margin = Inches(0.6)

style = doc.styles["Normal"]
style.font.name = "Calibri"
style.font.size = Pt(9.5)
style.font.color.rgb = INK
style.paragraph_format.space_after = Pt(0)
style.paragraph_format.line_spacing = 1.0


def set_bg(cell, hexcolor):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hexcolor)
    tcPr.append(shd)


def no_space(p):
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.space_before = Pt(0)
    return p


# ---- Title block ----
t = doc.add_paragraph()
no_space(t)
r = t.add_run("Test Automation Platform")
r.bold = True
r.font.size = Pt(17)
r.font.color.rgb = INK

sub = doc.add_paragraph()
no_space(sub)
r = sub.add_run("Cross-team quality intelligence — shipped capabilities at a glance")
r.font.size = Pt(10)
r.font.color.rgb = ACCENT
r.italic = True

meta = doc.add_paragraph()
no_space(meta)
r = meta.add_run("Engineering summary for leadership  ·  July 2026  ·  status: running platform, not a prototype")
r.font.size = Pt(8)
r.font.color.rgb = MUTE

# thin rule
hr = doc.add_paragraph()
no_space(hr)
hr.paragraph_format.space_before = Pt(3)
hr.paragraph_format.space_after = Pt(4)
pPr = hr._p.get_or_add_pPr()
pbdr = OxmlElement("w:pBdr")
bottom = OxmlElement("w:bottom")
bottom.set(qn("w:val"), "single")
bottom.set(qn("w:sz"), "6")
bottom.set(qn("w:space"), "1")
bottom.set(qn("w:color"), "2B5CE6")
pbdr.append(bottom)
pPr.append(pbdr)

# ---- Metric strip ----
metrics = [
    ("11", "backend services"),
    ("467", "REST APIs"),
    ("9", "test frameworks ingested"),
    ("33", "portal screens"),
    ("92", "automated test suites"),
]
mt = doc.add_table(rows=1, cols=len(metrics))
mt.autofit = True
for i, (num, lab) in enumerate(metrics):
    c = mt.rows[0].cells[i]
    set_bg(c, "F2F5FF")
    p1 = c.paragraphs[0]
    no_space(p1)
    p1.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rr = p1.add_run(num)
    rr.bold = True
    rr.font.size = Pt(15)
    rr.font.color.rgb = ACCENT
    p2 = c.add_paragraph()
    no_space(p2)
    p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rr = p2.add_run(lab)
    rr.font.size = Pt(7.5)
    rr.font.color.rgb = MUTE
doc.add_paragraph().paragraph_format.space_after = Pt(2)


def heading(text):
    p = doc.add_paragraph()
    no_space(p)
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(text.upper())
    r.bold = True
    r.font.size = Pt(9.5)
    r.font.color.rgb = INK
    # accent tick
    return p


def feature(title, body):
    p = doc.add_paragraph()
    no_space(p)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.left_indent = Inches(0.02)
    rb = p.add_run("▸ ")
    rb.font.color.rgb = ACCENT
    rb.bold = True
    rt = p.add_run(title + "  ")
    rt.bold = True
    rt.font.size = Pt(9.5)
    rd = p.add_run("— " + body)
    rd.font.size = Pt(9)
    rd.font.color.rgb = INK
    return p


heading("The headline: an AI engineer for your test suite")
feature("Autonomous failure triage",
        "every test failure is classified by an LLM (Claude/OpenAI via a gateway) into a root-cause category, with a nightly batch job that clears the backlog while the team sleeps.")
feature("Agentic workflows that write code",
        "a self-hosted Agent Hub runs multi-step LLM workflows to generate test cases from requirements, produce runnable automation code, analyze the blast radius of a pull request, and self-heal flaky tests — every proposal gated by a human review queue.")
feature("Test Impact Analysis",
        "maps code changes to the exact tests that exercise them, so CI runs the affected slice instead of the whole suite — faster feedback, lower compute.")

heading("Quality intelligence, running today")
feature("Flakiness scoring + auto-ticketing",
        "quantifies unstable tests from execution history and opens Jira tickets automatically for repeat offenders.")
feature("Quality gates, trends & release reports",
        "pass-rate trends, risk scoring, and go/no-go release readiness — the signals a release manager actually needs.")
feature("Universal ingestion",
        "one pipeline parses 9 frameworks out of the box: JUnit, TestNG, Cucumber, Allure, Playwright, Postman/Newman, k6, Gatling, and JMeter.")

heading("Enterprise-ready foundation")
feature("Full test-case management",
        "requirements, traceability, coverage matrix, suites, and manual + automated runs in one portal (33 screens).")
feature("Plugs into the toolchain",
        "live Jira, GitHub, and Azure DevOps integrations, including a guided Azure DevOps onboarding wizard.")
feature("Built for teams",
        "role-based access control, multi-org tenancy, API keys, event streaming (Kafka), and a full observability stack (Grafana/Prometheus/Loki).")

# ---- Bottom callout: honesty line ----
doc.add_paragraph().paragraph_format.space_after = Pt(1)
ct = doc.add_table(rows=1, cols=1)
cell = ct.rows[0].cells[0]
set_bg(cell, "EAF6EF")
cp = cell.paragraphs[0]
no_space(cp)
r = cp.add_run("Where it stands   ")
r.bold = True
r.font.size = Pt(9)
r.font.color.rgb = GOOD
r = cp.add_run(
    "These are working services with automated test coverage and a documented cloud cost model "
    "(~$170–230/mo compute, LLM tokens separate) — not slideware. The next mile is scale hardening, "
    "connector breadth, and production pilots with real teams."
)
r.font.size = Pt(8.5)
r.font.color.rgb = INK

doc.save("/home/user/test-automation-platform/docs/Stakeholder-Summary.docx")
print("saved docs/Stakeholder-Summary.docx")
