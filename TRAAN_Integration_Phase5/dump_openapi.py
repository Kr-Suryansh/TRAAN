import json
import sys
import os

sys.path.insert(0, os.path.abspath("."))
from app.main import app

def generate_openapi():
    openapi_schema = app.openapi()
    with open('openapi_schema.json', 'w') as f:
        json.dump(openapi_schema, f, indent=2)
    print("OpenAPI schema dumped successfully.")

if __name__ == "__main__":
    generate_openapi()
