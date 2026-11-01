"""Generate local credentials without overwriting an existing .env."""
import secrets
from pathlib import Path

path = Path(__file__).resolve().parents[1] / ".env"
with path.open("x", encoding="utf-8") as stream:
    stream.write(f"DB_PASSWORD={secrets.token_urlsafe(32)}\n")
    stream.write(f"MYSQL_ROOT_PASSWORD={secrets.token_urlsafe(32)}\n")
    stream.write(f"INGEST_API_KEY={secrets.token_urlsafe(40)}\n")
    stream.write("WEB_PORT=8088\nDATASET_SEED_ENABLED=true\n")
print("Generated .env. Credentials stay local and are excluded from Git.")
