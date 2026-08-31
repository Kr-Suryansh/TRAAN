import os
from google import genai
import logging

logger = logging.getLogger(__name__)

# Initialize the Gemini client
# Expects GEMINI_API_KEY environment variable to be set
try:
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        logger.warning("GEMINI_API_KEY environment variable not set. AI services will fail if called.")
    client = genai.Client(api_key=api_key)
except Exception as e:
    logger.error(f"Failed to initialize Gemini client: {e}")
    client = None

def get_client() -> genai.Client:
    return client
