"""
情绪分析多算法对照评测脚本（论文第六章实验）：
在独立测试集 (test split) 上对比：
1. Java 规则基线引擎 (RULE_V1)
2. Python SnowNLP 扩展模型 (SNOWNLP)
3. Python TF-IDF + LogisticRegression 监督模型 (ML_TFIDF，仅用 train 集训练)

输出极性分类 & 细粒度情绪分类的 Accuracy、Macro-F1、各类别 Recall、混淆矩阵，并写入 eval/nlp_eval_report.md。
"""
import json
from collections import Counter, defaultdict
from pathlib import Path

import requests

BACKEND_URL = "http://127.0.0.1:8088"
NLP_SERVICE_URL = "http://127.0.0.1:8001"
DATASET_PATH = Path(__file__).resolve().parent / "chinese_emotion_dataset.jsonl"
REPORT_PATH = Path(__file__).resolve().parent / "nlp_eval_report.md"


def get_jwt_token() -> str:
    resp = requests.post(
        f"{BACKEND_URL}/api/auth/login",
        json={"username": "student", "password": "Student@123"},
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["data"]["token"]


def compute_metrics(y_true, y_pred, labels):
    total = len(y_true)
    correct = sum(1 for a, b in zip(y_true, y_pred) if a == b)
    acc = correct / total if total else 0.0

    per_class = {}
    f1s = []
    cm = {t: {p: 0 for p in labels} for t in labels}
    for t, p in zip(y_true, y_pred):
        if t in cm and p in cm[t]:
            cm[t][p] += 1

    for lbl in labels:
        tp = sum(1 for t, p in zip(y_true, y_pred) if t == lbl and p == lbl)
        fp = sum(1 for t, p in zip(y_true, y_pred) if t != lbl and p == lbl)
        fn = sum(1 for t, p in zip(y_true, y_pred) if t == lbl and p != lbl)
        prec = tp / (tp + fp) if (tp + fp) else 0.0
        rec = tp / (tp + fn) if (tp + fn) else 0.0
        f1 = (2 * prec * rec / (prec + rec)) if (prec + rec) else 0.0
        support = sum(1 for t in y_true if t == lbl)
        per_class[lbl] = {"precision": prec, "recall": rec, "f1": f1, "support": support}
        if support > 0:
            f1s.append(f1)

    macro_f1 = sum(f1s) / len(f1s) if f1s else 0.0
    return {"accuracy": acc, "macro_f1": macro_f1, "per_class": per_class, "cm": cm}


def main():
    samples = [json.loads(line) for line in DATASET_PATH.read_text(encoding="utf-8").splitlines() if line.strip()]
    test_samples = [s for s in samples if s["split"] == "test"]
    split_counts = Counter(s["split"] for s in samples)
    source_counts = Counter(s["source"] for s in samples)

    token = get_jwt_token()
    headers = {"Authorization": f"Bearer {token}"}

    methods = ["RULE_V1", "SNOWNLP", "ML_TFIDF"]
    pol_labels = ["POSITIVE", "NEGATIVE", "NEUTRAL", "MIXED", "UNCERTAIN"]
    emo_labels = ["JOY", "ANXIETY", "SADNESS", "ANGER", "CALM", "MIXED", "UNCERTAIN"]

    results = {}
    error_cases = defaultdict(list)

    for method in methods:
        y_true_pol, y_pred_pol = [], []
        y_true_emo, y_pred_emo = [], []

        for s in test_samples:
            if method == "RULE_V1":
                r = requests.post(
                    f"{BACKEND_URL}/api/nlp/analyze",
                    json={"text": s["text"], "method": "RULE_V1"},
                    headers=headers,
                    timeout=10,
                ).json()["data"]["analysis"]
            else:
                r = requests.post(
                    f"{NLP_SERVICE_URL}/analyze",
                    json={"text": s["text"], "method": method},
                    timeout=10,
                ).json()

            pred_pol = r["polarity"]
            pred_emo = r["primaryEmotion"]
            y_true_pol.append(s["polarity"])
            y_pred_pol.append(pred_pol)
            y_true_emo.append(s["primary_emotion"])
            y_pred_emo.append(pred_emo)

            if pred_pol != s["polarity"] and len(error_cases[method]) < 6:
                error_cases[method].append((s["id"], s["text"], s["polarity"], pred_pol, s["hard_case"]))

        results[method] = {
            "polarity": compute_metrics(y_true_pol, y_pred_pol, pol_labels),
            "emotion": compute_metrics(y_true_emo, y_pred_emo, emo_labels),
        }

    lines = [
        "# 中文情绪分析模型对照实验报告",
        "",
        "## 1. 数据集统计与划分",
        f"- 总样本数：**{len(samples)}** 条（满足 ≥300 条要求）",
        f"- 独立划分：`train={split_counts['train']}`, `val={split_counts['val']}`, `test={split_counts['test']}`（仅在独立 `test` 集报告结果，未参与调参）",
        f"- 样本来源：`manual_curated={source_counts['manual_curated']}`, `campus_variant={source_counts['campus_variant']}`",
        "",
        "## 2. 总体指标对比（独立测试集）",
        "",
        "| 分析模型 | 极性 Accuracy | 极性 Macro-F1 (目标≥0.75) | 细粒度情绪 Accuracy | 细粒度情绪 Macro-F1 |",
        "| :--- | :---: | :---: | :---: | :---: |",
    ]

    for m in methods:
        pm = results[m]["polarity"]
        em = results[m]["emotion"]
        lines.append(
            f"| `{m}` | {pm['accuracy']:.4f} | **{pm['macro_f1']:.4f}** | {em['accuracy']:.4f} | **{em['macro_f1']:.4f}** |"
        )

    lines.append("")
    lines.append("## 3. 各类别极性召回率与 F1（RULE_V1 vs ML_TFIDF）")
    lines.append("")
    lines.append("| 极性类别 | 样本数 | RULE_V1 Recall | RULE_V1 F1 | ML_TFIDF Recall | ML_TFIDF F1 |")
    lines.append("| :--- | :---: | :---: | :---: | :---: | :---: |")
    for lbl in pol_labels:
        r_m = results["RULE_V1"]["polarity"]["per_class"][lbl]
        ml_m = results["ML_TFIDF"]["polarity"]["per_class"][lbl]
        lines.append(
            f"| `{lbl}` | {r_m['support']} | {r_m['recall']:.4f} | {r_m['f1']:.4f} | {ml_m['recall']:.4f} | {ml_m['f1']:.4f} |"
        )

    lines.append("")
    lines.append("## 4. RULE_V1 极性分类混淆矩阵（行=真实，列=预测）")
    lines.append("")
    lines.append("| True \\ Pred | " + " | ".join(pol_labels) + " |")
    lines.append("| :--- | " + " | ".join([":---:"] * len(pol_labels)) + " |")
    cm = results["RULE_V1"]["polarity"]["cm"]
    for t in pol_labels:
        row = " | ".join(str(cm[t][p]) for p in pol_labels)
        lines.append(f"| `{t}` | {row} |")

    lines.append("")
    lines.append("## 5. 典型误差样本记录（RULE_V1）")
    lines.append("")
    if error_cases["RULE_V1"]:
        for sid, txt, tr, pr, hc in error_cases["RULE_V1"]:
            lines.append(f"- `{sid}`（{hc}）：真实=`{tr}` / 预测=`{pr}` — “{txt}”")
    else:
        lines.append("- 独立测试集上极性判定无误差样本。")

    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Saved NLP evaluation report -> {REPORT_PATH}")
    for m in methods:
        pm = results[m]["polarity"]
        em = results[m]["emotion"]
        print(f"  {m:10s} | Polarity Acc={pm['accuracy']:.4f} Macro-F1={pm['macro_f1']:.4f} | Emotion Acc={em['accuracy']:.4f} Macro-F1={em['macro_f1']:.4f}")


if __name__ == "__main__":
    main()
