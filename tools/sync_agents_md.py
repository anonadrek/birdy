"""Generate AGENTS.md (read by Codex) from CLAUDE.md (canonical).

CLAUDE.md is the single source of truth for project status and working
agreements on both dev machines. AGENTS.md used to be hand-maintained and
drifted (its status stalled at 2026-07-18). Run this after every CLAUDE.md
update:

    python tools/sync_agents_md.py
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "CLAUDE.md"
DST = ROOT / "AGENTS.md"

HEADER = (
    "# Birdy Bird Scanner — arbetsguide för Codex\n"
    "\n"
    "> **Genereras från CLAUDE.md (`python tools/sync_agents_md.py`) och läses "
    "automatiskt av Codex i varje session.** Redigera CLAUDE.md, inte den här filen.\n"
)


def main() -> None:
    lines = SRC.read_text(encoding="utf-8").splitlines(keepends=True)
    # CLAUDE.md starts with a title line, a blank line and a one-line intro quote.
    if not (lines[0].startswith("# ") and lines[2].startswith("> ")):
        raise SystemExit("CLAUDE.md header changed; update sync_agents_md.py")
    DST.write_text(HEADER + "".join(lines[3:]), encoding="utf-8", newline="\n")
    print(f"wrote {DST.name} from {SRC.name}")


if __name__ == "__main__":
    main()
