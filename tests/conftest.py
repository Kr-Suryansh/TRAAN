import os
from dotenv import load_dotenv

# Load the project root .env file automatically when running tests
env_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env")
load_dotenv(dotenv_path=env_path)
