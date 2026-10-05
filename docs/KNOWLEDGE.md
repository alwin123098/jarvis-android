# Knowledge packs

Jarvis can answer using your own documents. It does **not** need an embeddings
model — a lightweight BM25 retriever finds the most relevant passages and adds
them to the prompt.

## Where packs live

On the device, in the app's private storage:

```
/data/data/com.codotype.jarvis/files/knowledge/
```

Any `.txt`, `.md`, or `.jsonl` file placed here is loaded on startup (and when
you toggle the book icon → reload). `.jsonl` rows are read for a `text`,
`content`, `body`, or `passage` field.

## Building a pack from a Hugging Face dataset

Use the helper script (requires `datasets` + `pandas`):

```bash
pip install datasets pandas
python scripts/export_dataset.py \
    --dataset rag-datasets/rag-mini-wikipedia \
    --split text \
    --column text \
    --out knowledge_wikipedia.jsonl \
    --limit 5000
```

Then push it to the device:

```bash
adb push knowledge_wikipedia.jsonl \
  /data/local/tmp/knowledge_wikipedia.jsonl

# run-as works on debug builds
adb shell run-as com.codotype.jarvis mkdir -p files/knowledge
adb shell run-as com.codotype.jarvis cp \
  /data/local/tmp/knowledge_wikipedia.jsonl files/knowledge/
```

Reopen Jarvis and tap the book icon to reload the index.

## Good sources

- `rag-datasets/rag-mini-wikipedia` — small Wikipedia subset, ideal for testing.
- `wikimedia/wikipedia` — the full dump (huge; export a slice).
- Your own notes, product docs, or manuals as plain `.txt` files.

## Tips

- Keep packs focused. A few thousand well-chosen passages beat a million
  irrelevant ones.
- Passages of ~40–800 characters work best; the loader chunks longer text
  automatically with overlap.
- Retrieval returns the top 3 passages per question by default (see
  `KnowledgeBase.retrieve`).
