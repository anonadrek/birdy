from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

from birdy_eval.corpus import CorpusItem
from birdy_eval.runner import Prediction, Predictor


class _FakeInterpreter:
    """Minimal protocol implementation for tf.lite.Interpreter used in tests."""

    def __init__(
        self,
        input_dtype: Any = None,
        output_dtype: Any = None,
        output_quantization: tuple[float, int] = (0.0, 0),
        output_scores: Any = None,
    ) -> None:
        import numpy as np

        self._called_set_tensor = False
        self._called_invoke = False
        self._input_dtype = input_dtype if input_dtype is not None else np.float32
        self._output_dtype = output_dtype if output_dtype is not None else np.float32
        self._output_quantization = output_quantization
        self._output_scores = output_scores
        self.last_tensor: Any = None

    def get_input_details(self) -> list[dict[str, Any]]:
        return [{"index": 0, "shape": [1, 224, 224, 3], "dtype": self._input_dtype}]

    def get_output_details(self) -> list[dict[str, Any]]:
        return [
            {
                "index": 0,
                "dtype": self._output_dtype,
                "quantization": self._output_quantization,
            }
        ]

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

        if self._output_scores is not None:
            return self._output_scores
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


def test_predictor_dequantizes_uint8_output(tmp_path: Path) -> None:
    """When output dtype is uint8, scores must be dequantized to floats in [0, 1]."""
    import numpy as np

    raw_scores = np.zeros((1, 965), dtype=np.uint8)
    raw_scores[0, 42] = 230  # → 230 * (1/256) ≈ 0.898
    raw_scores[0, 7] = 13  # → 13 * (1/256) ≈ 0.051
    raw_scores[0, 100] = 8  # → 8 * (1/256) ≈ 0.031

    fake = _FakeInterpreter(
        output_dtype=np.uint8,
        output_quantization=(1.0 / 256.0, 0),
        output_scores=raw_scores,
    )
    predictor = Predictor(interpreter=fake)  # type: ignore[arg-type]
    item = CorpusItem(path=tmp_path / "img_uout.jpg", qid="Q3", family="test")
    try:
        from PIL import Image

        img = Image.new("RGB", (10, 10), color=(50, 100, 150))
        img.save(item.path, format="JPEG")
    except ImportError:
        pytest.skip("Pillow not installed")

    prediction = predictor.predict(item)
    # Top-N ordering preserved
    assert prediction.top_scores[0][0] == 42
    # Top score is dequantized into [0, 1], not raw uint8 [0, 255]
    top_value = prediction.top_scores[0][1]
    assert 0.0 <= top_value <= 1.0
    assert top_value == pytest.approx(230.0 / 256.0, abs=1e-6)
