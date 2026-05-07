from __future__ import annotations

import pytest

from birdy_eval.corpus import CorpusItem
from birdy_eval.metrics import (
    EvalResult,
    ThresholdPoint,
    compute_metrics,
)
from birdy_eval.runner import Prediction


def _item(qid: str, family: str) -> CorpusItem:
    from pathlib import Path

    return CorpusItem(path=Path(f"corpus/{family}/{qid}.jpg"), qid=qid, family=family)


def _pred(item: CorpusItem, top_indices: list[int], top_scores: list[float]) -> Prediction:
    return Prediction(
        item=item,
        top_scores=list(zip(top_indices, top_scores, strict=True)),
    )


# Mapping: model index → QID
_MAPPING: dict[int, str] = {
    0: "Q18009",  # paridae
    1: "Q13364",  # accipitridae
    2: "Q999",  # other
}

# Items
_ITEM_A = _item("Q18009", "paridae")  # correct at index 0
_ITEM_B = _item("Q13364", "accipitridae")  # correct at index 1
_ITEM_C = _item("Q000", "unknown")  # no match anywhere


def _make_predictions() -> list[Prediction]:
    return [
        # Item A: top-1 correct (index 0 → Q18009)
        _pred(_ITEM_A, [0, 2, 1], [0.8, 0.1, 0.05]),
        # Item B: top-1 wrong (index 2 → Q999), but top-3 correct (index 1 → Q13364)
        _pred(_ITEM_B, [2, 1, 0], [0.6, 0.3, 0.05]),
        # Item C: completely wrong
        _pred(_ITEM_C, [0, 1, 2], [0.5, 0.3, 0.1]),
    ]


def test_compute_metrics_top1() -> None:
    result = compute_metrics(_make_predictions(), _MAPPING)
    assert isinstance(result, EvalResult)
    # 1 out of 3 correct at top-1
    assert result.top1_accuracy == pytest.approx(1 / 3)


def test_compute_metrics_top3() -> None:
    result = compute_metrics(_make_predictions(), _MAPPING)
    # 2 out of 3 correct in top-3 (A and B; C has no match)
    assert result.top3_accuracy == pytest.approx(2 / 3)


def test_compute_metrics_per_family() -> None:
    result = compute_metrics(_make_predictions(), _MAPPING)
    families = {f.family: f for f in result.per_family}
    assert "paridae" in families
    assert families["paridae"].top1_accuracy == pytest.approx(1.0)
    assert "accipitridae" in families
    assert families["accipitridae"].top1_accuracy == pytest.approx(0.0)
    assert families["accipitridae"].top3_accuracy == pytest.approx(1.0)


def test_compute_metrics_threshold_sweep() -> None:
    result = compute_metrics(_make_predictions(), _MAPPING)
    assert len(result.threshold_sweep) > 0
    for point in result.threshold_sweep:
        assert isinstance(point, ThresholdPoint)
        assert 0.0 <= point.threshold <= 1.0
        assert 0.0 <= point.coverage <= 1.0
        assert 0.0 <= point.top1_accuracy <= 1.0
