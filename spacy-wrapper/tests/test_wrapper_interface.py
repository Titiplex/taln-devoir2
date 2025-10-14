import importlib

import pytest

from wrapper.EndPoint import EndPoint


def test_module_imports():
    pytest.importorskip("spacy", reason="spaCy not available")
    # Adjust module path/name to your wrapper entrypoint if different
    mod = importlib.import_module("wrapper")  # e.g., spacy-wrapper/wrapper.py
    assert mod is not None


@pytest.mark.skip("Integration smoke test; enable when wrapper server is runnable")
def test_process_smoke():
    # Example: adapt to your actual Python API
    wrapper = EndPoint()
    json_str = wrapper.processSM("Alice works at OpenAI in San Francisco.")
    assert isinstance(json_str, str) and len(json_str) > 0
