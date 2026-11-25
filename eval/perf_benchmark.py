"""
并发性能测试脚本（论文第六章性能验收）：
以 20 个并发用户请求非 AI 接口（日记分页查询、统计接口、无 AI 日记保存），
统计成功率、平均耗时、P50、P95（验收目标：非 AI 接口 P95 <= 2000ms），并输出 eval/perf_report.md。
"""
import concurrent.futures
import statistics
import time
from datetime import date
from pathlib import Path

import requests

BACKEND_URL = "http://127.0.0.1:8088"
REPORT_PATH = Path(__file__).resolve().parent / "perf_report.md"


def login(username: str, password: str) -> str:
    r = requests.post(
        f"{BACKEND_URL}/api/auth/login",
        json={"username": username, "password": password},
        timeout=10,
    )
    r.raise_for_status()
    return r.json()["data"]["token"]


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = (len(sorted_vals) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def bench(name, fn, concurrency=20, total_requests=60):
    latencies = []
    errors = 0

    def worker(i):
        t0 = time.perf_counter()
        ok = fn(i)
        dt = (time.perf_counter() - t0) * 1000.0
        return ok, dt

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as ex:
        futs = [ex.submit(worker, i) for i in range(total_requests)]
        for fut in concurrent.futures.as_completed(futs):
            try:
                ok, dt = fut.result()
                if ok:
                    latencies.append(dt)
                else:
                    errors += 1
            except Exception:
                errors += 1

    latencies.sort()
    return {
        "name": name,
        "concurrency": concurrency,
        "total": total_requests,
        "success": len(latencies),
        "errors": errors,
        "avg_ms": round(statistics.mean(latencies), 1) if latencies else 0.0,
        "p50_ms": round(percentile(latencies, 50), 1),
        "p95_ms": round(percentile(latencies, 95), 1),
        "max_ms": round(max(latencies), 1) if latencies else 0.0,
    }


def main():
    token = login("student2", "Student@123")
    headers = {"Authorization": f"Bearer {token}"}
    created_ids = []

    def req_list(_):
        r = requests.get(f"{BACKEND_URL}/api/diaries?page=1&size=10", headers=headers, timeout=10)
        return r.status_code == 200

    def req_stats(_):
        r = requests.get(f"{BACKEND_URL}/api/stats/dashboard?days=30", headers=headers, timeout=10)
        return r.status_code == 200

    def req_save_no_ai(i):
        r = requests.post(
            f"{BACKEND_URL}/api/diaries",
            json={
                "title": f"并发性能测试#{i}",
                "content": "这是一条用于20并发非AI接口响应性能测试的普通记录。",
                "entryDate": str(date.today()),
                "aiConsent": False,
                "memoryIncluded": False,
            },
            headers=headers,
            timeout=10,
        )
        if r.status_code == 200:
            did = r.json()["data"]["id"]
            created_ids.append(did)
            return True
        return False

    results = [
        bench("GET /api/diaries (分页查询)", req_list, concurrency=20, total_requests=60),
        bench("GET /api/stats/dashboard (统计聚合)", req_stats, concurrency=20, total_requests=60),
        bench("POST /api/diaries (无AI落库保存)", req_save_no_ai, concurrency=20, total_requests=40),
    ]

    # 清理压测数据
    for cid in created_ids:
        try:
            requests.delete(f"{BACKEND_URL}/api/diaries/{cid}", headers=headers, timeout=5)
        except Exception:
            pass

    lines = [
        "# 20 并发性能基准测试报告",
        "",
        "验收目标：20 并发下非 AI 核心接口 P95 响应时间 ≤ 2000 ms。",
        "",
        "| 测试接口 | 并发数 | 总请求 | 成功数 | 平均耗时 (ms) | P50 (ms) | P95 (ms) | 最大耗时 (ms) | 是否达标 |",
        "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |",
    ]
    for r in results:
        passed = "PASS" if (r["errors"] == 0 and r["p95_ms"] <= 2000.0) else "FAIL"
        lines.append(
            f"| `{r['name']}` | {r['concurrency']} | {r['total']} | {r['success']} | {r['avg_ms']} | {r['p50_ms']} | **{r['p95_ms']}** | {r['max_ms']} | **{passed}** |"
        )

    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Saved perf benchmark report -> {REPORT_PATH}")
    for r in results:
        print(f"  {r['name']}: avg={r['avg_ms']}ms, P50={r['p50_ms']}ms, P95={r['p95_ms']}ms, errors={r['errors']}")


if __name__ == "__main__":
    main()
