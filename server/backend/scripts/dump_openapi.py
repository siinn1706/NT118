"""Local helper for contract tests: build the live OpenAPI schema without a DB."""
from __future__ import annotations


def build_schema() -> dict:
    from app.main import app

    return app.openapi()
