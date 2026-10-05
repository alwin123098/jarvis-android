#!/usr/bin/env python3
"""Export a Hugging Face dataset split to a Jarvis knowledge pack (JSONL).

Each output line is {"text": "..."} — the format KnowledgeBase.parseJsonl reads.

Example:
    python scripts/export_dataset.py \
        --dataset rag-datasets/rag-mini-wikipedia \
        --split text --column text --out knowledge_wikipedia.jsonl --limit 5000
"""
from __future__ import annotations

import argparse
import json
import sys


def main() -> int:
    ap = argparse.ArgumentParser(description="Export an HF dataset to JSONL for Jarvis.")
    ap.add_argument("--dataset", required=True, help="e.g. rag-datasets/rag-mini-wikipedia")
    ap.add_argument("--split", default="train", help="dataset split (default: train)")
    ap.add_argument("--column", default="text", help="column to export (default: text)")
    ap.add_argument("--out", required=True, help="output .jsonl path")
    ap.add_argument("--limit", type=int, default=0, help="max rows (0 = all)")
    ap.add_argument("--min-chars", type=int, default=40, help="skip rows shorter than this")
    args = ap.parse_args()

    try:
        from datasets import load_dataset
    except ImportError:
        print("Please install the datasets library:  pip install datasets", file=sys.stderr)
        return 1

    print(f"Loading {args.dataset} [{args.split}] …", file=sys.stderr)
    ds = load_dataset(args.dataset, split=args.split)

    if args.column not in ds.column_names:
        print(
            f"Column '{args.column}' not found. Available: {ds.column_names}",
            file=sys.stderr,
        )
        return 1

    written = 0
    with open(args.out, "w", encoding="utf-8") as fh:
        for row in ds:
            text = str(row[args.column]).strip()
            if len(text) < args.min_chars:
                continue
            fh.write(json.dumps({"text": text}, ensure_ascii=False) + "\n")
            written += 1
            if args.limit and written >= args.limit:
                break
            if written % 1000 == 0:
                print(f"  {written} rows…", file=sys.stderr)

    print(f"Wrote {written} rows to {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
