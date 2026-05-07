from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

from birdy_eval.corpus import CorpusItem
from birdy_eval.runner import Prediction, Predictor


class _FakeInterpreter:
    """Minimal protocol implementation for tf.lite.Interpreter used in tests."""

    def __init__(self, input_dtype: Any = None) -> None:
        import numpy as np

        self._called_set_tensor = False
        self._called_invoke = False
        self._input_dtype = input_dtype if input_dtype is not None else np.float32
        self.last_tensor: Any = None

    def get_input_details(self) -> list[dict[str, Any]]:
        return [{"index": 0, "shape": [1, 224, 224, 3], "dtype": self._input_dtype}]

    def get_output_details(self) -> list[dict[str, Any]]:
        return [{"index": 0}]

    def resize_tensor_input(self, index: int, shape: list[int]) -> None:
        pass

    def allocate_tensors(self) -> None:
        pass

    def set_tensor(self, index: int, data: Any) -> None:
        self._called_set_tensor = True
        self.last_tensor = data

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


def test_predictor_uint8_dtype(tmp_path: Path) -> None:
    """When model input dtype is uint8 the tensor passed to set_tensor must be uint8."""
    import numpy as np

    fake = _FakeInterpreter(input_dtype=np.uint8)
    predictor = Predictor(interpreter=fake)  # type: ignore[arg-type]
    item = CorpusItem(path=tmp_path / "img_u8.jpg", qid="Q1", family="test")
    try:
        from PIL import Image

        img = Image.new("RGB", (10, 10), color=(128, 64, 32))
        img.save(item.path, format="JPEG")
    except ImportError:
        pytest.skip("Pillow not installed")

    predictor.predict(item)
    assert fake.last_tensor is not None
    assert fake.last_tensor.dtype == np.uint8


def test_predictor_float32_dtype(tmp_path: Path) -> None:
    """When model input dtype is float32 the tensor must be float32 (default)."""
    import numpy as np

    fake = _FakeInterpreter(input_dtype=np.float32)
    predictor = Predictor(interpreter=fake)  # type: ignore[arg-type]
    item = CorpusItem(path=tmp_path / "img_f32.jpg", qid="Q2", family="test")
    try:
        from PIL import Image

        img = Image.new("RGB", (10, 10), color=(200, 100, 50))
        img.save(item.path, format="JPEG")
    except ImportError:
        pytest.skip("Pillow not installed")

    predictor.predict(item)
    assert fake.last_tensor is not None
    assert fake.last_tensor.dtype == np.float32
    # Values must be in [0.0, 1.0]
    assert float(fake.last_tensor.max()) <= 1.0
