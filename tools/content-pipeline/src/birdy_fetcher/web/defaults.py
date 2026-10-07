"""Model defaults that the CLI's option declarations read, kept apart from facts_step.py so
`birdy-fetcher --help` does not import the Anthropic SDK (follow-up 7, wave A review)."""

from __future__ import annotations

# The fact sheet's model and effort (R3 chooses them). `web verify`'s V1 retry writes a
# fact sheet too: it uses the record's own `generated.facts` settings, else these.
FACTS_MODEL_KEY = "opus"
FACTS_EFFORT = "high"
EFFORTS = ("low", "medium", "high")
