#!/usr/bin/env python3
"""
Run Spring Boot with environment variables loaded from a .env file.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


def parse_env_file(env_path: Path) -> dict[str, str]:
    if not env_path.exists():
        raise FileNotFoundError(f".env file not found: {env_path}")

    values: dict[str, str] = {}
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()

        if value.startswith(("'", '"')) and value.endswith(("'", '"')) and len(value) >= 2:
            value = value[1:-1]
        values[key] = value

    return values


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run Spring Boot with variables loaded from a .env file."
    )
    parser.add_argument(
        "--env-file",
        default=".env",
        help="Path to .env file (default: .env)",
    )
    parser.add_argument(
        "--maven-goal",
        default="spring-boot:run",
        help='Maven goal to execute (default: "spring-boot:run")',
    )
    parser.add_argument(
        "--working-dir",
        default=".",
        help="Project directory containing pom.xml (default: current directory)",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    working_dir = Path(args.working_dir).resolve()
    env_file = Path(args.env_file)
    if not env_file.is_absolute():
        env_file = working_dir / env_file

    try:
        file_env = parse_env_file(env_file)
    except FileNotFoundError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    child_env = os.environ.copy()
    child_env.update(file_env)

    cmd = ["mvn", args.maven_goal]
    print(f"Using env file: {env_file}")
    print(f"Running command: {' '.join(cmd)}")

    try:
        completed = subprocess.run(cmd, cwd=working_dir, env=child_env, check=False)
    except FileNotFoundError:
        print("Maven executable 'mvn' not found in PATH.", file=sys.stderr)
        return 1

    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
