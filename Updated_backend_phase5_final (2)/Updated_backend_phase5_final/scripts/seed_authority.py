import asyncio
import os
import sys

# Add the project root to the sys.path to allow imports from app
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.db.engine import AsyncSessionLocal
from app.db._models.authority import Authority
from app.core.security import hash_password
from sqlalchemy import select

async def seed_authority():
    """
    Seeds a deterministic development/demo authority account for the dashboard.
    This script is idempotent.
    """
    email = os.environ.get("DEMO_AUTHORITY_EMAIL", "demo@sih.gov.in")
    password = os.environ.get("DEMO_AUTHORITY_PASSWORD", "demo1234")
    name = "Arjun Mehta"
    role = "DDMA"
    agency = "District Disaster Management Authority, Jaipur"

    async with AsyncSessionLocal() as session:
        # Check if the authority already exists
        result = await session.execute(select(Authority).where(Authority.email == email))
        existing_authority = result.scalars().first()

        if existing_authority:
            print(f"Authority with email {email} already exists. Skipping seed.")
            return

        print(f"Seeding demo authority: {email} / {role}")
        hashed = hash_password(password)
        authority = Authority(
            email=email,
            hashed_password=hashed,
            name=name,
            role=role,
            agency=agency
        )
        session.add(authority)
        await session.commit()
        print("Demo authority seeded successfully.")

if __name__ == "__main__":
    asyncio.run(seed_authority())
