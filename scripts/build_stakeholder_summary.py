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
r = sub.add_run("One product for the whole test cycle — manage it, automate it, then let AI do the work")
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
    ("1", "portal, whole cycle"),
    ("9", "CI frameworks in"),
    ("33", "product screens"),
    ("3", "trackers connected"),
    ("5", "AI workflows"),
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


def heading(badge, text, tagline):
    p = doc.add_paragraph()
    no_space(p)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    rb = p.add_run(" " + badge + " ")
    rb.bold = True
    rb.font.size = Pt(9.5)
    rb.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    # shade the badge run
    rPr = rb._r.get_or_add_rPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), "2B5CE6")
    rPr.append(shd)
    r = p.add_run("  " + text.upper())
    r.bold = True
    r.font.size = Pt(9.5)
    r.font.color.rgb = INK
    r = p.add_run("   " + tagline)
    r.font.size = Pt(8.5)
    r.italic = True
    r.font.color.rgb = MUTE
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


heading("1", "Foundation — own the test cycle", "plan, cover, execute, decide")
feature("Test case management, end to end",
        "author, review, and version test cases with steps, priorities, and suites — the single source of truth for what gets tested, across the whole team.")
feature("Requirements, traceability & coverage",
        "link every requirement to the tests that cover it; a live coverage matrix shows exactly what is protected and where the gaps are.")
feature("Run cycles and decide with confidence",
        "execute manual test runs, then read pass-rate trends, quality gates, and release-readiness reports to make a clear go / no-go call.")

heading("2", "Test automation — scale execution", "every run, every framework, one view")
feature("Bring every result together",
        "one pipeline captures results from 9 CI frameworks out of the box — JUnit, TestNG, Cucumber, Allure, Playwright, Postman/Newman, k6, Gatling, JMeter.")
feature("One estate, automated + manual",
        "automated suites, GitHub CI workflows, and execution history sit alongside manual tests, so leaders see the full test estate in a single portal.")
feature("Close the loop into the tools teams use",
        "flakiness scoring turns noisy CI into tracked work by auto-filing Jira tickets, with live Jira, GitHub, and Azure DevOps integrations (guided ADO onboarding).")

heading("3", "AI — do the testing work", "triage, generate, fix, focus")
feature("Failures triaged automatically",
        "every failure is classified to a root cause by an LLM, with a nightly batch that clears the backlog overnight — so mornings start with answers, not logs.")
feature("Agents that generate and fix",
        "generate test cases from requirements, produce runnable automation code, analyze a pull request's blast radius, and propose flaky-test fixes — every change gated by a human review queue.")
feature("Run only what a change touches",
        "Test Impact Analysis maps a code change to the exact tests that exercise it, cutting CI time and cost without losing coverage.")

# ---- Bottom callout: honesty line ----
doc.add_paragraph().paragraph_format.space_after = Pt(1)
ct = doc.add_table(rows=1, cols=1)
cell = ct.rows[0].cells[0]
set_bg(cell, "EAF6EF")
cp = cell.paragraphs[0]
no_space(cp)
r = cp.add_run("The bet   ")
r.bold = True
r.font.size = Pt(9)
r.font.color.rgb = GOOD
r = cp.add_run(
    "Every layer above is working product with automated test coverage and a documented cost model "
    "(~$170–230/mo compute, LLM tokens separate) — not slideware. Teams get value from day one at the "
    "foundation; the AI layer compounds it. Next: scale hardening, more connectors, and pilots with real teams."
)
r.font.size = Pt(8.5)
r.font.color.rgb = INK

doc.save("/home/user/test-automation-platform/docs/Stakeholder-Summary.docx")
print("saved docs/Stakeholder-Summary.docx")
