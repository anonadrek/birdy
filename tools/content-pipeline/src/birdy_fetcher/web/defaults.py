"""Model defaults that the CLI's option declarations read, kept apart from facts_step.py so
`birdy-fetcher --help` does not import the Anthropic SDK (follow-up 7, wave A review)."""

from __future__ import annotations

# The fact sheet's model and effort (R3 chooses them). `web verify`'s V1 retry writes a
# fact sheet too: it uses the record's own `generated.facts` settings, else these.
FACTS_MODEL_KEY = "opus"
FACTS_EFFORT = "high"
EFFORTS = ("low", "medium", "high")
# Every model key the paid commands accept (llm.MODELS has the ids; a test keeps the two in
# step). Opus 5.5 was added after the R3 trial (2026-10-07); the defaults stay Albin's call.
MODEL_KEYS = ("opus", "opus55", "sonnet")
