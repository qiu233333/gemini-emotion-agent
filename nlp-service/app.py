"""
中文情绪分析扩展服务 (FastAPI)
支持方法：
- SNOWNLP: 基于 SnowNLP 情感极性概率与细粒度关键词映射
- ML_TFIDF: 基于独立训练集拟合的中文分词 TF-IDF + LogisticRegression 分类器（与规则基线对照）
- BERT: 如已配置本地微调中文 BERT 模型路径 (BERT_MODEL_PATH) 且安装了 transformers/torch，则加载 BERT 推理；否则回退至 ML_TFIDF 并标明说明。
"""
import json
import os
import re
from pathlib import Path
from typing import Dict, List, Optional

import jieba
from fastapi import FastAPI
from pydantic import BaseModel

try:
    from snownlp import SnowNLP
except Exception:
    SnowNLP = None

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import Pipeline
except Exception:
    TfidfVectorizer = None
    LogisticRegression = None
    Pipeline = None

app = FastAPI(title="Emotion Agent NLP Extension Service", version="1.0.0")

DATASET_PATH = Path(__file__).resolve().parent.parent / "eval" / "chinese_emotion_dataset.jsonl"
STOPWORDS = {
    "今天", "昨天", "明天", "现在", "时候", "觉得", "感觉", "自己", "我们", "什么", "因为", "所以",
    "但是", "可是", "不过", "虽然", "就是", "还是", "已经", "一直", "一下", "一点", "一个", "真的",
    "非常", "特别", "比较", "有点", "有些", "然后", "而且", "这个", "那个", "可以", "应该", "需要"
}

POLARITY_MODEL: Optional[object] = None
EMOTION_MODEL: Optional[object] = None
BERT_PIPELINE = None


class AnalyzeRequest(BaseModel):
    text: str
    method: str = "ML_TFIDF"


def tokenize_zh(text: str) -> str:
    tokens = [t.strip() for t in jieba.cut(text) if t.strip()]
    return " ".join(tokens)


def extract_keywords(text: str) -> List[str]:
    counts: Dict[str, int] = {}
    for tok in jieba.cut(text):
        t = tok.strip()
        if re.fullmatch(r"[\u4e00-\u9fa5a-zA-Z]{2,8}", t) and t not in STOPWORDS:
            counts[t] = counts.get(t, 0) + 1
    ranked = sorted(counts.items(), key=lambda x: (-x[1], -len(x[0])))
    return [k for k, _ in ranked[:5]]


def train_ml_models():
    global POLARITY_MODEL, EMOTION_MODEL
    if not DATASET_PATH.exists() or TfidfVectorizer is None:
        return
    texts, polarities, emotions = [], [], []
    with DATASET_PATH.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            # 严格遵守训练/验证/测试独立划分：仅用 train 集训练模型，绝不泄露 test 集
            if item.get("split") == "train":
                texts.append(tokenize_zh(item["text"]))
                polarities.append(item["polarity"])
                emotions.append(item["primary_emotion"])

    if len(texts) < 20:
        return

    POLARITY_MODEL = Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1)),
        ("clf", LogisticRegression(max_iter=1000, C=2.0, class_weight="balanced"))
    ])
    POLARITY_MODEL.fit(texts, polarities)

    EMOTION_MODEL = Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1)),
        ("clf", LogisticRegression(max_iter=1000, C=2.0, class_weight="balanced"))
    ])
    EMOTION_MODEL.fit(texts, emotions)


def try_load_bert():
    global BERT_PIPELINE
    model_path = os.environ.get("BERT_MODEL_PATH", "").strip()
    if not model_path:
        return
    try:
        from transformers import pipeline
        BERT_PIPELINE = pipeline("text-classification", model=model_path, tokenizer=model_path)
    except Exception:
        BERT_PIPELINE = None


@app.on_event("startup")
def on_startup():
    train_ml_models()
    try_load_bert()


@app.get("/health")
def health():
    return {
        "status": "UP",
        "snownlpAvailable": SnowNLP is not None,
        "mlTfidfReady": POLARITY_MODEL is not None and EMOTION_MODEL is not None,
        "bertLoaded": BERT_PIPELINE is not None,
    }


def analyze_snownlp(text: str) -> dict:
    if SnowNLP is None:
        return analyze_ml(text, "SNOWNLP_FALLBACK")
    s = SnowNLP(text)
    prob = float(s.sentiments)  # 0 (neg) ~ 1 (pos)
    if prob >= 0.65:
        polarity, primary = "POSITIVE", "JOY"
        intensity = int(45 + (prob - 0.65) * 140)
    elif prob <= 0.35:
        polarity = "NEGATIVE"
        if any(w in text for w in ["焦虑", "担心", "紧张", "压力", "失眠", "ddl", "答辩"]):
            primary = "ANXIETY"
        elif any(w in text for w in ["生气", "愤怒", "气死", "离谱", "甩锅", "讨厌"]):
            primary = "ANGER"
        else:
            primary = "SADNESS"
        intensity = int(45 + (0.35 - prob) * 140)
    else:
        polarity, primary = "NEUTRAL", "CALM"
        intensity = int(30 + abs(prob - 0.5) * 100)

    intensity = max(15, min(95, intensity))
    scores = {
        "JOY": round(prob if polarity == "POSITIVE" else prob * 0.3, 2),
        "CALM": round(1.0 - abs(prob - 0.5) * 2, 2),
        "ANXIETY": round((1 - prob) if primary == "ANXIETY" else (1 - prob) * 0.3, 2),
        "SADNESS": round((1 - prob) if primary == "SADNESS" else (1 - prob) * 0.3, 2),
        "ANGER": round((1 - prob) if primary == "ANGER" else (1 - prob) * 0.2, 2),
    }
    return {
        "analyzerMethod": "SNOWNLP",
        "polarity": polarity,
        "primaryEmotion": primary,
        "secondaryEmotions": [],
        "emotionScores": scores,
        "intensityScore": intensity,
        "keywords": extract_keywords(text),
        "explanation": f"基于 SnowNLP 贝叶斯情感概率 (positive_prob={prob:.3f}) 结合细粒度关键词映射判定。"
    }


def analyze_ml(text: str, method_label: str = "ML_TFIDF") -> dict:
    if POLARITY_MODEL is None or EMOTION_MODEL is None:
        train_ml_models()
    if POLARITY_MODEL is None or EMOTION_MODEL is None:
        return analyze_snownlp(text)

    tok = tokenize_zh(text)
    pol_probs = POLARITY_MODEL.predict_proba([tok])[0]
    pol_classes = list(POLARITY_MODEL.classes_)
    pol_idx = int(pol_probs.argmax())
    polarity = pol_classes[pol_idx]

    emo_probs = EMOTION_MODEL.predict_proba([tok])[0]
    emo_classes = list(EMOTION_MODEL.classes_)
    ranked = sorted(zip(emo_classes, emo_probs), key=lambda x: -x[1])
    primary = ranked[0][0]
    confidence = float(ranked[0][1])
    secondary = [c for c, p in ranked[1:3] if p >= 0.18 and c not in ("UNCERTAIN",)]

    scores = {k: 0.0 for k in ["JOY", "ANXIETY", "SADNESS", "ANGER", "CALM"]}
    for c, p in ranked:
        if c in scores:
            scores[c] = round(float(p) * 3.0, 2)

    intensity = int(max(20, min(95, round(35 + confidence * 60))))
    if primary in ("CALM", "UNCERTAIN"):
        intensity = min(intensity, 52)

    return {
        "analyzerMethod": method_label,
        "polarity": polarity,
        "primaryEmotion": primary,
        "secondaryEmotions": secondary,
        "emotionScores": scores,
        "intensityScore": intensity,
        "keywords": extract_keywords(text),
        "explanation": f"基于中文 TF-IDF + 逻辑回归监督分类模型 (置信度={confidence:.2f}, 极性={polarity})。"
    }


@app.post("/analyze")
def analyze(req: AnalyzeRequest):
    text = (req.text or "").strip()
    if not text:
        return {
            "analyzerMethod": req.method.upper(),
            "polarity": "UNCERTAIN",
            "primaryEmotion": "UNCERTAIN",
            "secondaryEmotions": [],
            "emotionScores": {"JOY": 0.0, "ANXIETY": 0.0, "SADNESS": 0.0, "ANGER": 0.0, "CALM": 0.0},
            "intensityScore": 0,
            "keywords": [],
            "explanation": "输入文本为空。"
        }

    method = (req.method or "ML_TFIDF").upper()
    if method == "SNOWNLP":
        return analyze_snownlp(text)
    if method == "BERT":
        if BERT_PIPELINE is not None:
            pred = BERT_PIPELINE(text[:512])[0]
            label = str(pred["label"]).upper()
            score = float(pred["score"])
            pol = "POSITIVE" if label in ("JOY", "POSITIVE") else ("NEUTRAL" if label in ("CALM", "NEUTRAL") else "NEGATIVE")
            return {
                "analyzerMethod": "BERT",
                "polarity": pol,
                "primaryEmotion": label if label in ("JOY", "ANXIETY", "SADNESS", "ANGER", "CALM") else "UNCERTAIN",
                "secondaryEmotions": [],
                "emotionScores": {k: (round(score * 3.0, 2) if k == label else 0.2) for k in ["JOY", "ANXIETY", "SADNESS", "ANGER", "CALM"]},
                "intensityScore": int(max(25, min(95, round(35 + score * 60)))),
                "keywords": extract_keywords(text),
                "explanation": f"基于本地微调中文 BERT 分类器推理 (置信度={score:.3f})。"
            }
        res = analyze_ml(text, "ML_TFIDF")
        res["explanation"] = "[未配置本地 BERT 权重路径，已自动切换至监督学习对照模型 ML_TFIDF] " + res["explanation"]
        return res
    return analyze_ml(text, "ML_TFIDF")
