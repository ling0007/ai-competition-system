#!/usr/bin/env python3
"""Search knowledge metadata and titles using only the Python standard library.

Frontmatter format: one key per line, with JSON strings or string arrays as
values (a deliberately small YAML subset). See docs/knowledge/TEMPLATE.md.
"""

import argparse
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
KNOWLEDGE = ROOT / "docs" / "knowledge"
DIRECTORIES = {
    "navigation": "navigation",
    "model": "models",
    "playbook": "playbooks",
    "diagnostic": "diagnostics",
    "rationale": "decisions",
    "constraint": "constraints",
}
STATUSES = {"active", "needs-review", "deprecated"}


def read_entry(path):
    """Read only frontmatter and the first H1; never load the knowledge body."""
    metadata = {}
    with path.open(encoding="utf-8-sig") as source:
        if source.readline().strip() != "---":
            raise ValueError("missing frontmatter")
        for raw in source:
            line = raw.strip()
            if line == "---":
                break
            if not line or line.startswith("#"):
                continue
            key, separator, value = line.partition(":")
            if not separator or key in metadata:
                raise ValueError("invalid or duplicate metadata key")
            metadata[key] = json.loads(value)
        else:
            raise ValueError("unclosed frontmatter")
        for raw in source:
            if raw.startswith("# "):
                metadata["title"] = raw[2:].strip()
                break
        else:
            raise ValueError("missing H1 title")

    for key in ("id", "summary", "role", "status", "title"):
        if not isinstance(metadata.get(key), str) or not metadata[key].strip():
            raise ValueError(f"{key} must be a nonempty string")
    for key in ("scope", "paths"):
        value = metadata.get(key)
        if not isinstance(value, list) or not all(
            isinstance(item, str) and item.strip() for item in value
        ):
            raise ValueError(f"{key} must be an array of nonempty strings")
    if metadata["role"] not in DIRECTORIES:
        raise ValueError("unknown role")
    if metadata["status"] not in STATUSES:
        raise ValueError("unknown status")
    if path.relative_to(KNOWLEDGE).parts[0] != DIRECTORIES[metadata["role"]]:
        raise ValueError("role does not match directory")
    return metadata


def normalize_path(value):
    value = value.replace("\\", "/").rstrip("/").casefold()
    return value.removeprefix("./")


def paths_overlap(first, second):
    first, second = normalize_path(first), normalize_path(second)
    return first == second or first.startswith(second + "/") or second.startswith(first + "/")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("terms", nargs="*", help="OR search across metadata and H1")
    parser.add_argument("--scope", action="append", default=[], help="scope substring; repeatable")
    parser.add_argument("--path", action="append", default=[], help="repo-relative file/directory; repeatable")
    parser.add_argument("--role", choices=DIRECTORIES, action="append", default=[])
    parser.add_argument("--limit", type=int, choices=range(1, 6), default=5)
    parser.add_argument("--include-deprecated", action="store_true")
    args = parser.parse_args()
    terms = {term.casefold() for term in args.terms}
    matches, errors, ids = [], [], set()

    for directory in DIRECTORIES.values():
        for path in sorted((KNOWLEDGE / directory).rglob("*.md")):
            relative = path.relative_to(ROOT).as_posix()
            try:
                entry = read_entry(path)
                if entry["id"] in ids:
                    raise ValueError("duplicate knowledge id")
                ids.add(entry["id"])
            except (OSError, ValueError) as error:
                errors.append(f"{relative}: {error}")
                continue
            if entry["status"] == "deprecated" and not args.include_deprecated:
                continue
            if args.role and entry["role"] not in args.role:
                continue
            if args.scope and not any(
                requested.casefold() in scope.casefold()
                for requested in args.scope for scope in entry["scope"]
            ):
                continue
            if args.path and not any(
                paths_overlap(requested, known)
                for requested in args.path for known in entry["paths"]
            ):
                continue
            haystack = " ".join(
                [entry["id"], entry["title"], entry["summary"], entry["role"]]
                + entry["scope"] + entry["paths"]
            ).casefold()
            score = sum(term in haystack for term in terms)
            if terms and score == 0:
                continue
            matches.append((score, entry["id"], relative, entry))

    # Metadata errors must not silently hide potentially important knowledge.
    if errors:
        print("Invalid knowledge metadata:\n" + "\n".join(errors), file=sys.stderr)
        return 2
    for score, _, relative, entry in sorted(matches, key=lambda item: (-item[0], item[1]))[:args.limit]:
        print(f'{entry["id"]} [{entry["status"]}] {entry["role"]} (matches={score})')
        print(f'  {entry["summary"]}\n  {relative}')
    if not matches:
        print("No candidates. Continue with current code and the knowledge index.")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
