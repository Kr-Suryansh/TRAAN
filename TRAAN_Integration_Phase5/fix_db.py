import asyncio
import asyncpg
from app.config import settings

async def main():
    conn = await asyncpg.connect(settings.DATABASE_URL.replace("+asyncpg", ""))
    try:
        await conn.execute("ALTER TABLE authority ADD COLUMN name VARCHAR")
    except Exception as e:
        print(e)
    try:
        await conn.execute("ALTER TABLE authority ADD COLUMN agency VARCHAR")
    except Exception as e:
        print(e)
    try:
        await conn.execute("ALTER TABLE device ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE")
    except Exception as e:
        print(e)
    print("Database updated!")
    await conn.close()

if __name__ == "__main__":
    asyncio.run(main())
