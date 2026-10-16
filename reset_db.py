"""
Reset the Docket database by dropping all tables.

Reads connection details from .env (if present) and falls back to the
defaults in application.properties.  Requires the `psycopg2` package:

    pip install psycopg2-binary

Run:
    python reset_db.py
"""

import os
import sys
from pathlib import Path
from urllib.parse import urlparse

def load_env(env_path: Path) -> dict[str, str]:
    """Parse a .env file into a dict, ignoring comments and blank lines."""
    vals: dict[str, str] = {}
    if not env_path.exists():
        return vals
    for line in env_path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        vals[key.strip()] = value.strip()
    return vals


def main() -> None:
    try:
        import psycopg2
    except ImportError:
        print("psycopg2 is not installed. Run:  pip install psycopg2-binary")
        sys.exit(1)

    project_root = Path(__file__).resolve().parent
    env = load_env(project_root / ".env")

    db_url = env.get(
        "SPRING_DATASOURCE_URL",
        os.environ.get("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/docketdb"),
    )
    db_user = env.get(
        "SPRING_DATASOURCE_USERNAME",
        os.environ.get("SPRING_DATASOURCE_USERNAME", "docket"),
    )
    password_default = env.get("POSTGRES_PASSWORD", "docket_dev_password")
    db_password = env.get(
        "SPRING_DATASOURCE_PASSWORD",
        os.environ.get("SPRING_DATASOURCE_PASSWORD", password_default),
    )

    # Strip the "jdbc:" prefix that Spring uses so we can parse a standard URI.
    if db_url.startswith("jdbc:"):
        db_url = db_url[len("jdbc:"):]

    parsed = urlparse(db_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or 5432
    dbname = parsed.path.lstrip("/") or "docketdb"

    print(f"Connecting to {host}:{port}/{dbname} as {db_user} …")

    conn = psycopg2.connect(
        host=host,
        port=port,
        dbname=dbname,
        user=db_user,
        password=db_password,
    )
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute(
        """
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public';
        """
    )
    tables = [row[0] for row in cur.fetchall()]

    if not tables:
        print("No tables found — database is already empty.")
    else:
        print(f"Dropping {len(tables)} table(s): {', '.join(tables)}")
        cur.execute("DROP SCHEMA public CASCADE;")
        cur.execute("CREATE SCHEMA public;")
        cur.execute("GRANT ALL ON SCHEMA public TO public;")
        print("All tables dropped. Schema 'public' recreated.")

    cur.close()
    conn.close()
    print("Done. Restart the Spring Boot app to let Hibernate / Flyway recreate tables.")


if __name__ == "__main__":
    main()
