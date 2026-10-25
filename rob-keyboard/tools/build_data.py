#!/usr/bin/env python3
"""Builds the keyboard's offline assets from public data sources.

Inputs (download into one directory, pass it as argv[1]):
  ru_full.txt, de_full.txt, en_50k.txt (ru/de_50k.txt as fallback)   hermitdave/FrequencyWords (OpenSubtitles 2018, CC BY-SA 4.0)
  de.dic                               LibreOffice de_DE_frami Hunspell dictionary (used only for noun casing)
  emoji-test.txt                       Unicode emoji-test.txt
  ann_{en,de,ru}.json, annd_*.json     Unicode CLDR annotations (keywords for emoji search)

Optional second directory (argv[2]) with Leipzig Corpora Collection sentence files (CC BY 4.0):
  deu_mixed-typical_2011_300K, rus_news_2020_300K, eng_news_2020_300K  (*-sentences.txt)
  Used for German noun capitalisation and for next-word (bigram) predictions.

Output: app/src/main/assets/{dict_*.txt,bigrams_*.txt,emoji.tsv}
"""
import glob
from collections import Counter, defaultdict
import json
import os
import re
import sys

src = sys.argv[1]
corpus_dir = sys.argv[2] if len(sys.argv) > 2 else None
CORPUS = {"de": "deu_mixed-typical_2011_300K", "ru": "rus_news_2020_300K", "en": "eng_news_2020_300K"}
TOKEN = re.compile(r"[^\W\d_]+(?:['-][^\W\d_]+)?|[.,!?;:()\"]")


def sentences(lang):
    if not corpus_dir:
        return
    for path in glob.glob(os.path.join(corpus_dir, CORPUS[lang], "*-sentences.txt")):
        with open(path, encoding="utf-8") as f:
            for line in f:
                yield line.split("\t", 1)[-1].strip()


def corpus_stats(lang):
    """Mid-sentence casing counts and word-pair counts from the corpus."""
    casing = defaultdict(Counter)
    pairs = defaultdict(Counter)
    for sent in sentences(lang):
        toks = TOKEN.findall(sent)
        prev = None
        for i, t in enumerate(toks):
            if not t[0].isalpha():
                prev = None
                continue
            low = t.lower()
            if i > 0 and toks[i - 1] not in (".", "!", "?", ":", "\""):
                casing[low][t] += 1
            if prev is not None:
                pairs[prev][low] += 1
            prev = low
    return casing, pairs


out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
os.makedirs(out, exist_ok=True)

LETTERS = {
    "ru": re.compile(r"^[а-яё]+(-[а-яё]+)?$"),
    "de": re.compile(r"^[a-zäöüß]+(-[a-zäöüß]+)?$"),
    "en": re.compile(r"^[a-z]+('[a-z]+)?$"),
}
SINGLE_OK = {"ru": set("авиксоуя"), "de": set(), "en": {"a", "i"}}
# ru/de use the full FrequencyWords lists (ru_full.txt, de_full.txt) cut at MIN_COUNT to drop subtitle typos.
MAX_WORDS = {"ru": 150000, "de": 150000, "en": 40000}
MIN_COUNT = {"ru": 20, "de": 20, "en": 0}


def freq_file(lang):
    full = os.path.join(src, f"{lang}_full.txt")
    return full if lang != "en" and os.path.exists(full) else os.path.join(src, f"{lang}_50k.txt")


def german_casing():
    cap, low = set(), set()
    with open(os.path.join(src, "de.dic"), encoding="iso-8859-1", errors="ignore") as f:
        for line in f:
            parts = line.strip().split("/")
            w = parts[0]
            if not w or w.startswith("#") or " " in w:
                continue
            if w[0].isupper():
                cap.add(w)
            elif len(parts) > 1 and not set(parts[1]) & set("ozhke"):
                # Lowercase entries flagged o/z/h/k/e (or bare) are compound fragments, not words.
                low.add(w)
    return cap, low


# Frequent words that are also nouns in the Hunspell list but are typed lowercase far more often.
DE_LOWER = {"nein", "nichts", "schön", "mal", "bitte", "danke", "morgen", "recht", "abend", "leid",
            "angst", "schuld", "heim", "spaß", "ernst", "ordnung", "gestern", "heute", "weg"}


def de_case(word, cap, low):
    if word in DE_LOWER or word in low or (word.endswith("e") and word + "n" in low):
        return word
    c = word[:1].upper() + word[1:]
    if c in cap:
        return c
    for suf in ("nen", "ern", "en", "es", "er", "n", "s", "e"):
        if word.endswith(suf) and len(word) - len(suf) >= 3:
            stem = word[: -len(suf)]
            if stem in low:
                return word
            if stem[:1].upper() + stem[1:] in cap:
                return c
    return word


def build_dict(lang, cap=None, low=None, casing=None):
    rows = []
    with open(freq_file(lang), encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2:
                continue
            w, n = parts[0].lower(), int(parts[1])
            if n < MIN_COUNT[lang]:
                break
            if not LETTERS[lang].match(w):
                continue
            if len(w) == 1 and w not in SINGLE_OK[lang]:
                continue
            if lang == "de":
                seen = casing.get(w) if casing else None
                if seen and sum(seen.values()) >= 3:
                    upper = seen.get(w[:1].upper() + w[1:], 0)
                    w = w[:1].upper() + w[1:] if upper > seen.get(w, 0) * 1.5 else w
                else:
                    w = de_case(w, cap, low)
            if lang == "en" and w == "i":
                w = "I"
            rows.append((w, n))
            if len(rows) >= MAX_WORDS[lang]:
                break
    with open(os.path.join(out, f"dict_{lang}.txt"), "w", encoding="utf-8") as f:
        for w, n in rows:
            f.write(f"{w}\t{n}\n")
    print(lang, len(rows))
    return rows


def build_bigrams(lang, rows, pairs):
    if not pairs:
        return
    vocab = {w.lower(): w for w, _ in rows}
    out = []
    for w, _ in rows[:8000]:
        nexts = [(n, c) for n, c in pairs.get(w.lower(), Counter()).most_common(12) if n in vocab and c >= 3]
        if nexts:
            out.append(f"{w.lower()}\t" + "|".join(vocab[n] for n, _ in nexts[:4]))
    with open(os.path.join(out_dir_assets(), f"bigrams_{lang}.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("bigrams", lang, len(out))


def out_dir_assets():
    return out


def build_emoji():
    def load(name):
        try:
            with open(os.path.join(src, name), encoding="utf-8") as f:
                data = json.load(f)
        except FileNotFoundError:
            return {}
        root = data.get("annotations") or data.get("annotationsDerived")
        return root["annotations"]

    ann = {}
    for lang in ("en", "de", "ru"):
        for name in (f"ann_{lang}.json", f"annd_{lang}.json"):
            for k, v in load(name).items():
                words = list(v.get("default", [])) + list(v.get("tts", []))
                ann.setdefault(k.replace("️", ""), []).extend(words)

    group_ids = {
        "Smileys & Emotion": "smileys",
        "People & Body": "people",
        "Animals & Nature": "nature",
        "Food & Drink": "food",
        "Travel & Places": "travel",
        "Activities": "activities",
        "Objects": "objects",
        "Symbols": "symbols",
        "Flags": "flags",
    }
    skin = re.compile("[\U0001F3FB-\U0001F3FF]")
    rows, group, seen = [], None, set()
    with open(os.path.join(src, "emoji-test.txt"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("# group:"):
                group = group_ids.get(line.split(":", 1)[1].strip())
                continue
            if not line.strip() or line.startswith("#") or group is None:
                continue
            m = re.match(r"^([0-9A-F ]+);\s*fully-qualified\s*#\s*(\S+)\s+E(\d+\.\d+)\s+(.*)$", line)
            if not m:
                continue
            emoji = "".join(chr(int(cp, 16)) for cp in m.group(1).split())
            version = m.group(3)
            # Skin-tone variants are reached via long press on the base emoji.
            base = skin.sub("", emoji)
            if base != emoji:
                if base in seen:
                    rows.append((emoji, group, version, "", base))
                continue
            seen.add(emoji)
            kw = [m.group(4)] + ann.get(emoji.replace("️", ""), [])
            kw = sorted({k.lower().replace("\t", " ").replace("|", " ") for k in kw if k})
            rows.append((emoji, group, version, "|".join(kw), ""))
    with open(os.path.join(out, "emoji.tsv"), "w", encoding="utf-8") as f:
        for r in rows:
            f.write("\t".join(r) + "\n")
    print("emoji", sum(1 for r in rows if not r[4]), "variants", sum(1 for r in rows if r[4]))


if __name__ == "__main__":
    cap, low = german_casing()
    for lang in ("ru", "de", "en"):
        casing, pairs = corpus_stats(lang)
        rows = build_dict(lang, cap, low, casing)
        build_bigrams(lang, rows, pairs)
    build_emoji()
