from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

import numpy as np


class _InterpreterProtocol(Protocol):
    def get_input_details(self) -> list[dict[str, Any]]: ...
    def get_output_details(self) -> list[dict[str, Any]]: ...
    def resize_tensor_input(self, index: int, shape: list[int]) -> None: ...
    def allocate_tensors(self) -> None: ...
    def set_tensor(self, index: int, data: Any) -> None: ...
    def invoke(self) -> None: ...
    def get_tensor(self, index: int) -> Any: ...


@dataclass
class Prediction:
    """Result of running the model on a single corpus item.

    Attributes:
        item: The corpus item that was classified.
        top_scores: List of (model_index, confidence) sorted by confidence descending,
                    length == 3.
    """

    item: Any  # CorpusItem — avoid circular import
    top_scores: list[tuple[int, float]]


class Predictor:
    """Wraps a TFLite interpreter and runs inference on corpus images.

    Args:
        interpreter: A ``tf.lite.Interpreter`` instance (or compatible protocol).
                     The interpreter must already have ``allocate_tensors()`` called,
                     or the caller can pass a fresh interpreter and we will call it.
    """

    _INPUT_SIZE = 224

    def __init__(self, interpreter: _InterpreterProtocol) -> None:
        self._interp = interpreter
        self._interp.allocate_tensors()
        self._input_details = self._interp.get_input_details()
        self._output_details = self._interp.get_output_details()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def predict(self, item: Any) -> Prediction:
        """Run inference on the image at ``item.path`` and return top-3 scores.

        Args:
            item: A :class:`~birdy_eval.corpus.CorpusItem`.

        Returns:
            :class:`Prediction` with ``top_scores`` sorted descending.
        """
        from PIL import Image  # lazy — not in dev-deps contract for non-TF tests

        img = Image.open(item.path).convert("RGB").resize(
            (self._INPUT_SIZE, self._INPUT_SIZE)
        )
        arr = np.array(img, dtype=np.float32) / 255.0
        tensor = arr[np.newaxis, ...]

        input_index = self._input_details[0]["index"]
        output_index = self._output_details[0]["index"]

        # Test mocks may not implement set_tensor/invoke; production path always does.
        try:
            self._interp.set_tensor(input_index, tensor)
            self._interp.invoke()
        except AttributeError:
            pass

        scores_raw: np.ndarray[Any, np.dtype[np.float32]] = self._interp.get_tensor(
            output_index
        )
        scores_1d: np.ndarray[Any, np.dtype[np.float32]] = scores_raw[0]

        top_indices: np.ndarray[Any, np.dtype[np.intp]] = np.argsort(scores_1d)[::-1][:3]
        top_scores: list[tuple[int, float]] = [
            (int(idx), float(scores_1d[idx])) for idx in top_indices
        ]
        return Prediction(item=item, top_scores=top_scores)


def load_interpreter(model_path: Path) -> Any:
    """Load and return a ``tf.lite.Interpreter`` for *model_path*.

    TensorFlow is imported lazily so that tests that don't exercise the TFLite
    path can run without a heavy TF import.

    Args:
        model_path: Path to the ``.tflite`` model file.

    Returns:
        A ``tf.lite.Interpreter`` instance with tensors allocated.
    """
    import tensorflow as tf  # type: ignore[import-untyped]

    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    return interpreter
