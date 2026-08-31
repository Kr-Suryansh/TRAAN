"""
app/services package — business logic layer.

Sub-packages:
  ai/          — Gemini summarization (Component E owns implementation)
  optimizer/   — OR-Tools resource allocation (Component E owns implementation)
  mock_idrn/   — Mock IDRN-style resource registry seed/utilities

Direct modules (added in later phases):
  dedup.py     — UUID + geohash proximity deduplication (Phase 3)
  clustering.py— PostGIS ST_ClusterDBSCAN wrapper (Phase 4)
"""
