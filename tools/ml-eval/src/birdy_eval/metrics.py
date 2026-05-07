from __future__ import annotations

from dataclasses import dataclass, field

from birdy_eval.runner import Prediction

# Confidence thresholds to evaluate in the sweep (0.00 → 0.95 step 0.05)
_THRESHOLDS = [round(t * 0.05, 2) for t in range(20)]


@dataclass
class FamilyResult:
    """Per-family accuracy metrics."""

    family: str
    total: int
    top1_correct: int
    top3_correct: int

    @property
    def top1_accuracy(self) -> float:
        return self.top1_correct / self.total if self.total > 0 else 0.0

    @property
    def top3_accuracy(self) -> float:
        return self.top3_correct / self.total if self.total > 0 else 0.0


@dataclass
class ThresholdPoint:
    """Accuracy + coverage at a given confidence threshold."""

    threshold: float
    coverage: float  # fraction of predictions at or above threshold
    top1_accuracy: float  # top-1 accuracy among covered predictions


@dataclass
class EvalResult:
    """Aggregate evaluation results over the entire corpus."""

    total: int
    top1_correct: int
    top3_correct: int
    per_family: list[FamilyResult] = field(default_factory=list)
    threshold_sweep: list[ThresholdPoint] = field(default_factory=list)

    @property
    def top1_accuracy(self) -> float:
        return self.top1_correct / self.total if self.total > 0 else 0.0

    @property
    def top3_accuracy(self) -> float:
        return self.top3_correct / self.total if self.total > 0 else 0.0


def _is_top1_correct(pred: Prediction, mapping: dict[int, str]) -> bool:
    """Return True if the top-1 model prediction matches ``pred.item.qid``."""
    if not pred.top_scores:
        return False
    top_idx, _ = pred.top_scores[0]
    return bool(mapping.get(top_idx) == pred.item.qid)


def _is_top3_correct(pred: Prediction, mapping: dict[int, str]) -> bool:
    """Return True if any top-3 model prediction matches ``pred.item.qid``."""
    return any(mapping.get(idx) == pred.item.qid for idx, _ in pred.top_scores)


def compute_metrics(
    predictions: list[Prediction],
    mapping: dict[int, str],
) -> EvalResult:
    """Compute top-1 / top-3 accuracy, per-family, and threshold sweep.

    Args:
        predictions: List of :class:`~birdy_eval.runner.Prediction` objects.
        mapping: Dict mapping model output index → species QID.

    Returns:
        :class:`EvalResult` with all computed metrics.
    """
    total = len(predictions)
    top1_correct = sum(1 for p in predictions if _is_top1_correct(p, mapping))
    top3_correct = sum(1 for p in predictions if _is_top3_correct(p, mapping))

    # --- Per-family breakdown ---
    family_buckets: dict[str, list[Prediction]] = {}
    for pred in predictions:
        family = pred.item.family
        family_buckets.setdefault(family, []).append(pred)

    per_family: list[FamilyResult] = []
    for family, preds in sorted(family_buckets.items()):
        f_top1 = sum(1 for p in preds if _is_top1_correct(p, mapping))
        f_top3 = sum(1 for p in preds if _is_top3_correct(p, mapping))
        per_family.append(
            FamilyResult(
                family=family,
                total=len(preds),
                top1_correct=f_top1,
                top3_correct=f_top3,
            )
        )

    # --- Threshold sweep ---
    threshold_sweep: list[ThresholdPoint] = []
    for threshold in _THRESHOLDS:
        covered = [p for p in predictions if p.top_scores and p.top_scores[0][1] >= threshold]
        coverage = len(covered) / total if total > 0 else 0.0
        t1_correct = sum(1 for p in covered if _is_top1_correct(p, mapping))
        t1_acc = t1_correct / len(covered) if covered else 0.0
        threshold_sweep.append(
            ThresholdPoint(threshold=threshold, coverage=coverage, top1_accuracy=t1_acc)
        )

    return EvalResult(
        total=total,
        top1_correct=top1_correct,
        top3_correct=top3_correct,
        per_family=per_family,
        threshold_sweep=threshold_sweep,
    )
