from __future__ import annotations

from pathlib import Path
from typing import Any
from unittest.mock import MagicMock

import pytest

from birdy_eval.corpus import CorpusItem
from birdy_eval.runner import Prediction, Predictor


class _FakeInterpreter:
    """Minimal protocol implementation for tf.lite.Interpreter used in tests."""

    def __init__(self) -> None:
        self._called_set_tensor = False
        self._called_invoke = False

    def get_input_details(self) -> list[dict[str, Any]]:
        return [{"index": 0, "shape": [1, 224, 224, 3]}]

    def get_output_details(self) -> list[dict[str, Any]]:
        return [{"index": 0}]

    def resize_tensor_input(self, index: int, shape: list[int]) -> None:
        pass

    def allocate_tensors(self) -> None:
        pass

    def set_tensor(self, index: int, data: Any) -> None:
        self._called_set_tensor = True

    def invoke(self) -> None:
        self._called_invoke = True

    def get_tensor(self, index: int) -> Any:
        import numpy as np

        scores = np.zeros((1, 965), dtype=np.float32)
        scores[0, 42] = 0.9
        scores[0, 7] = 0.05
        scores[0, 100] = 0.03
        return scores


def test_predictor_returns_prediction(tmp_path: Path) -> None:
    fake = _FakeInterpreter()
    predictor = Predictor(interpreter=fake)  # type: ignore[arg-type]
    item = CorpusItem(
        path=tmp_path / "img.jpg",
        qid="Q18009",
        family="paridae",
    )
    # Create a tiny valid JPEG so PIL can open it
    try:
        from PIL import Image

        img = Image.new("RGB", (10, 10), color=(100, 150, 200))
        img.save(item.path, format="JPEG")
    except ImportError:
        pytest.skip("Pillow not installed")

    prediction = predictor.predict(item)
    assert isinstance(prediction, Prediction)
    assert prediction.item is item
    assert len(prediction.top_scores) == 3
    # Highest score is index 42 → first in sorted list
    assert prediction.top_scores[0][0] == 42


def test_predictor_top_scores_sorted(tmp_path: Path) -> None:
    fake = _FakeInterpreter()
    predictor = Predictor(interpreter=fake)  # type: ignore[arg-type]
    item = CorpusItem(path=tmp_path / "img.jpg", qid="Q99", family="test")
    try:
        from PIL import Image

        img = Image.new("RGB", (5, 5), color=(0, 0, 0))
        img.save(item.path, format="JPEG")
    except ImportError:
        pytest.skip("Pillow not installed")

    prediction = predictor.predict(item)
    scores = [s for _, s in prediction.top_scores]
    assert scores == sorted(scores, reverse=True)
