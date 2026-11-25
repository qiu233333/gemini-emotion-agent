package com.emotionagent.module.nlp.service;

import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;
import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于中文情感词典、程度副词、否定词、转折句结构与校园语境的规则法情感分析器 (RULE_V1)。
 * 可解释性强，支持混合情绪 (MIXED) 与证据不足判定 (UNCERTAIN)。
 */
@Service
public class RuleEmotionAnalyzer implements EmotionAnalyzer {

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    private static final Pattern CLAUSE_SPLIT = Pattern.compile("[。！？!?；;\\n]+");
    private static final Pattern TURN_PATTERN = Pattern.compile("(但是|可是|不过|然而|偏偏|却|但)");
    private static final Pattern VALID_KEYWORD = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z]{2,8}$");

    private static final Map<String, Double> JOY_WORDS = new LinkedHashMap<>();
    private static final Map<String, Double> ANXIETY_WORDS = new LinkedHashMap<>();
    private static final Map<String, Double> SADNESS_WORDS = new LinkedHashMap<>();
    private static final Map<String, Double> ANGER_WORDS = new LinkedHashMap<>();
    private static final Map<String, Double> CALM_WORDS = new LinkedHashMap<>();

    private static final Map<String, Double> DEGREE_WORDS = new HashMap<>();
    private static final Set<String> NEGATION_WORDS = new HashSet<>();
    private static final Set<String> STOP_WORDS = new HashSet<>();
    private static final List<String> COMPOUND_PHRASES = new ArrayList<>();
    private static final Map<String, String> PHRASE_EMOTION = new HashMap<>();
    private static final Map<String, Double> PHRASE_WEIGHT = new HashMap<>();

    static {
        // 愉悦 / 积极 (JOY)
        putAll(JOY_WORDS, 1.5, "开心", "高兴", "快乐", "愉快", "兴奋", "惊喜", "幸福", "激动", "畅快", "欢喜", "美滋滋", "太棒了", "超开心", "欣喜", "自豪", "成就感", "上岸", "如愿", "好评");
        putAll(JOY_WORDS, 1.2, "期待", "满意", "充实", "顺利", "成功", "温暖", "感动", "治愈", "喜欢", "享受", "棒", "不错", "进步", "收获", "表扬", "认可", "鼓舞", "加油", "希望", "有趣", "好玩", "精彩", "轻松", "舒服", "好运", "感谢", "感恩", "开心极了", "满足", "欣慰", "自信", "有动力", "干劲");

        // 焦虑 / 压力 / 担忧 (ANXIETY)
        putAll(ANXIETY_WORDS, 1.5, "焦虑", "恐慌", "崩溃", "失眠", "内耗", "慌张", "焦躁", "抓狂", "窒息", "压力山大", "心慌", "坐立不安", "透不过气", "焦头烂额");
        putAll(ANXIETY_WORDS, 1.2, "担心", "紧张", "害怕", "不安", "迷茫", "压力", "忐忑", "担忧", "发愁", "纠结", "犹豫", "急躁", "着急", "赶工", "熬夜", "疲惫", "累", "好累", "心累", "卷不动", "社恐", "尴尬", "怕", "挂科", "没底", "来不及", "紧迫", "压抑", "繁重", "ddl", "DDL", "答辩", "面试", "考研", "秋招", "春招", "实习", "截止", "焦虑感", "烦躁", "心烦", "茫然", "困惑", "无措");

        // 悲伤 / 低落 (SADNESS)
        putAll(SADNESS_WORDS, 1.5, "难过", "伤心", "悲伤", "痛苦", "绝望", "哭了", "大哭", "委屈", "失落", "沮丧", "心碎", "孤独", "孤单", "崩溃", "emo", "破防", "低落", "无助", "空虚", "自卑", "绝望感");
        putAll(SADNESS_WORDS, 1.2, "遗憾", "后悔", "难受", "郁闷", "消沉", "落寞", "寂寞", "想家", "失望", "灰心", "泄气", "心酸", "眼泪", "提不起劲", "没意思", "无力", "疲倦", "颓废", "摆烂", "失败", "考砸", "落榜", "被拒", "冷落", "疏远", "抱歉", "内疚", "愧疚", "自责", "沉重", "低谷", "叹气");

        // 愤怒 / 不满 (ANGER)
        putAll(ANGER_WORDS, 1.5, "愤怒", "生气", "气死了", "火大", "暴躁", "恼火", "气愤", "可恶", "讨厌", "恶心", "抓狂", "炸毛", "怒", "发火", "吵架", "冲突", "受够了", "离谱", "无语");
        putAll(ANGER_WORDS, 1.2, "烦人", "讨厌鬼", "不满", "抱怨", "委屈又生气", "不公平", "敷衍", "甩锅", "推诿", "不尊重", "冒犯", "反感", "烦死了", "真服了", "服了", "呵呵", "瞎折腾", "浪费时间", "不爽", "憋屈", "恼怒", "愤慨", "郁闷透顶", "嫌弃", "烦躁");

        // 平静 / 放松 / 稳定 (CALM)
        putAll(CALM_WORDS, 1.3, "平静", "安宁", "安心", "踏实", "放松", "淡定", "平和", "释怀", "从容", "宁静", "稳重", "心静", "安稳", "舒心", "惬意", "悠闲");
        putAll(CALM_WORDS, 1.0, "还好", "挺好", "正常", "规律", "安稳", "散步", "听歌", "看书", "休息", "冥想", "慢慢来", "顺其自然", "整理", "调整", "恢复", "平稳", "缓和", "安静", "舒适", "清净");

        // 短语级精准匹配（解决分词切碎及校园口语/固定表达）
        addPhrase("松了一口气", "CALM", 1.6);
        addPhrase("松了口气", "CALM", 1.6);
        addPhrase("压力很大", "ANXIETY", 1.8);
        addPhrase("压力有点大", "ANXIETY", 1.4);
        addPhrase("睡不着", "ANXIETY", 1.5);
        addPhrase("整晚没睡", "ANXIETY", 1.6);
        addPhrase("拿到了offer", "JOY", 2.0);
        addPhrase("拿到offer", "JOY", 2.0);
        addPhrase("通过了", "JOY", 1.4);
        addPhrase("考砸了", "SADNESS", 1.8);
        addPhrase("没考好", "SADNESS", 1.6);
        addPhrase("不想说话", "SADNESS", 1.4);
        addPhrase("提不起劲", "SADNESS", 1.5);
        addPhrase("气不打一处来", "ANGER", 1.8);
        addPhrase("太离谱了", "ANGER", 1.6);
        addPhrase("真服了", "ANGER", 1.5);
        addPhrase("笑死我了", "JOY", 1.5);
        addPhrase("心情不错", "JOY", 1.6);
        addPhrase("心情很好", "JOY", 1.8);
        addPhrase("心情很差", "SADNESS", 1.8);
        addPhrase("心情低落", "SADNESS", 1.7);
        addPhrase("有点开心", "JOY", 1.1);
        addPhrase("不太开心", "SADNESS", 1.4);
        addPhrase("并不开心", "SADNESS", 1.6);
        addPhrase("不开心", "SADNESS", 1.5);
        addPhrase("不高兴", "SADNESS", 1.5);
        addPhrase("没那么焦虑", "CALM", 1.4);
        addPhrase("不再焦虑", "CALM", 1.5);
        addPhrase("不焦虑了", "CALM", 1.5);
        addPhrase("不难过了", "CALM", 1.4);
        addPhrase("好多了", "CALM", 1.3);
        addPhrase("仍然期待", "JOY", 1.5);
        addPhrase("还是期待", "JOY", 1.5);
        addPhrase("心里踏实", "CALM", 1.6);
        addPhrase("真行啊", "ANGER", 1.3);
        addPhrase("太棒了又", "ANGER", 1.5);

        // 程度副词
        putAll(DEGREE_WORDS, 1.6, "极其", "极度", "超级", "太", "特别", "非常", "十分", "格外", "无比", "彻底", "完全", "实在", "真的", "超");
        putAll(DEGREE_WORDS, 1.25, "很", "挺", "好", "蛮", "相当", "越发", "越来越", "更加", "比较");
        putAll(DEGREE_WORDS, 0.75, "有点", "有些", "稍微", "略微", "一点", "稍稍", "勉强", "轻微");

        // 否定词
        NEGATION_WORDS.addAll(List.of(
                "不", "没", "没有", "并非", "并不", "不是", "不太", "不算", "算不上",
                "谈不上", "毫不", "毫无", "未", "未能", "难以", "别", "从未", "绝不"
        ));

        // 常见停用词
        STOP_WORDS.addAll(List.of(
                "今天", "昨天", "明天", "现在", "时候", "觉得", "感觉", "自己", "我们", "你们", "他们",
                "什么", "怎么", "因为", "所以", "但是", "可是", "不过", "虽然", "如果", "就是", "还是",
                "已经", "一直", "一下", "一点", "一些", "一个", "一样", "真的", "非常", "特别", "比较",
                "有点", "有些", "然后", "而且", "或者", "以及", "这个", "那个", "这些", "那些", "这样",
                "那样", "可以", "应该", "需要", "想要", "希望", "能够", "可能", "大概", "其实", "最后",
                "突然", "终于", "还是", "没有", "不是", "不会", "不能", "不要", "知道", "发现", "开始",
                "继续", "准备", "完成", "觉得", "上午", "下午", "晚上", "早上", "中午", "最近", "每天"
        ));
    }

    private static void putAll(Map<String, Double> map, double weight, String... words) {
        for (String w : words) {
            map.put(w, weight);
        }
    }

    private static void addPhrase(String phrase, String emotion, double weight) {
        COMPOUND_PHRASES.add(phrase);
        PHRASE_EMOTION.put(phrase, emotion);
        PHRASE_WEIGHT.put(phrase, weight);
    }

    @Override
    public String methodName() {
        return "RULE_V1";
    }

    @Override
    public EmotionAnalysisResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return EmotionAnalysisResult.builder()
                    .analyzerMethod(methodName())
                    .polarity("UNCERTAIN")
                    .primaryEmotion("UNCERTAIN")
                    .secondaryEmotions(List.of())
                    .emotionScores(zeroScores())
                    .intensityScore(0)
                    .keywords(List.of())
                    .explanation("输入文本为空，无法提取情绪特征。")
                    .build();
        }

        String normalized = text.trim();
        Map<String, Double> scores = zeroScores();
        List<String> traces = new ArrayList<>();

        String[] sentences = CLAUSE_SPLIT.split(normalized);
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) continue;
            analyzeSentence(sentence.trim(), scores, traces);
        }

        // 反讽启发式校正（如“呵呵”/“真棒啊”与负面事件同现）
        if ((normalized.contains("呵呵") || normalized.contains("真棒") || normalized.contains("太好了"))
                && (normalized.contains("挂科") || normalized.contains("加班") || normalized.contains("甩锅") || normalized.contains("又延") || normalized.contains("断网"))) {
            scores.put("ANGER", scores.get("ANGER") + 1.4);
            scores.put("JOY", Math.max(0.0, scores.get("JOY") - 1.2));
            traces.add("检测到疑似反讽语境（褒义词与负面事件共现），调高不满/愤怒权重");
        }

        double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double posScore = scores.get("JOY") + scores.get("CALM") * 0.7;
        double negScore = scores.get("ANXIETY") + scores.get("SADNESS") + scores.get("ANGER");

        // 排序各情绪维度
        List<Map.Entry<String, Double>> ranked = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0.05)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();

        String polarity;
        String primaryEmotion;
        List<String> secondary = new ArrayList<>();
        int intensity;

        if (totalScore < 0.6 || ranked.isEmpty()) {
            polarity = "UNCERTAIN";
            primaryEmotion = "UNCERTAIN";
            intensity = 20;
            traces.add("未检测到足够清晰的情绪词证据，标记为不确定（支持手动纠偏）");
        } else {
            boolean isMixed = (posScore >= 1.0 && negScore >= 0.85)
                    && (Math.min(posScore, negScore) / Math.max(posScore, negScore) >= 0.35);

            if (isMixed) {
                polarity = "MIXED";
                primaryEmotion = ranked.get(0).getKey();
                for (int i = 1; i < Math.min(3, ranked.size()); i++) {
                    if (ranked.get(i).getValue() >= 0.6) {
                        secondary.add(ranked.get(i).getKey());
                    }
                }
                if (secondary.isEmpty() && ranked.size() > 1) {
                    secondary.add(ranked.get(1).getKey());
                }
                traces.add("同时检测到积极与消极情绪信号，判定为混合情绪");
            } else if ("CALM".equals(ranked.get(0).getKey()) && negScore < 0.8 && scores.get("JOY") < 1.0) {
                polarity = "NEUTRAL";
                primaryEmotion = "CALM";
                if (ranked.size() > 1 && ranked.get(1).getValue() >= 0.6) {
                    secondary.add(ranked.get(1).getKey());
                }
            } else if (posScore >= negScore * 1.15) {
                polarity = "POSITIVE";
                primaryEmotion = scores.get("JOY") >= scores.get("CALM") ? "JOY" : "CALM";
                for (int i = 1; i < Math.min(3, ranked.size()); i++) {
                    if (ranked.get(i).getValue() >= 0.8 && !ranked.get(i).getKey().equals(primaryEmotion)) {
                        secondary.add(ranked.get(i).getKey());
                    }
                }
            } else if (negScore >= posScore * 1.15) {
                polarity = "NEGATIVE";
                primaryEmotion = ranked.get(0).getKey();
                for (int i = 1; i < Math.min(3, ranked.size()); i++) {
                    if (ranked.get(i).getValue() >= 0.8) {
                        secondary.add(ranked.get(i).getKey());
                    }
                }
            } else {
                polarity = "NEUTRAL";
                primaryEmotion = ranked.get(0).getKey();
                if (ranked.size() > 1 && ranked.get(1).getValue() >= 0.6) {
                    secondary.add(ranked.get(1).getKey());
                }
            }

            // 辅助强度分（0-100，非医学量表）
            double peak = ranked.get(0).getValue();
            double exclamations = countChar(normalized, '！') + countChar(normalized, '!');
            double rawIntensity = 30.0 + Math.min(55.0, peak * 14.0 + totalScore * 3.5) + Math.min(10.0, exclamations * 3.0);
            if ("NEUTRAL".equals(polarity) && "CALM".equals(primaryEmotion)) {
                rawIntensity = Math.min(rawIntensity, 55.0);
            }
            intensity = (int) Math.max(15, Math.min(98, Math.round(rawIntensity)));
        }

        // 四舍五入保留两位小数
        Map<String, Double> roundedScores = new LinkedHashMap<>();
        scores.forEach((k, v) -> roundedScores.put(k, Math.round(v * 100.0) / 100.0));

        List<String> keywords = extractKeywords(normalized);
        String explanation = traces.isEmpty()
                ? "基于中文情绪词典与语义规则综合计算得出。"
                : String.join("；", traces.subList(0, Math.min(6, traces.size())));

        return EmotionAnalysisResult.builder()
                .analyzerMethod(methodName())
                .polarity(polarity)
                .primaryEmotion(primaryEmotion)
                .secondaryEmotions(secondary)
                .emotionScores(roundedScores)
                .intensityScore(intensity)
                .keywords(keywords)
                .explanation(explanation)
                .build();
    }

    private void analyzeSentence(String sentence, Map<String, Double> scores, List<String> traces) {
        // 检测转折连词，拆分前后分句并差异加权
        Matcher turnMatcher = TURN_PATTERN.matcher(sentence);
        if (turnMatcher.find()) {
            int idx = turnMatcher.start();
            String turnWord = turnMatcher.group(1);
            String before = sentence.substring(0, idx).trim();
            String after = sentence.substring(turnMatcher.end()).trim();
            if (!before.isEmpty() && !after.isEmpty()) {
                traces.add("识别到转折结构“" + turnWord + "”，加重后半句权重");
                scoreClause(before, 0.80, scores, traces);
                scoreClause(after, 1.45, scores, traces);
                return;
            }
        }
        scoreClause(sentence, 1.0, scores, traces);
    }

    private void scoreClause(String clause, double clauseWeight, Map<String, Double> scores, List<String> traces) {
        String working = clause;
        // 1. 先匹配固定短语（避免被分词拆散，如“并不开心”“松了一口气”）
        for (String phrase : COMPOUND_PHRASES) {
            if (working.contains(phrase)) {
                String emo = PHRASE_EMOTION.get(phrase);
                double w = PHRASE_WEIGHT.get(phrase) * clauseWeight;
                scores.put(emo, scores.get(emo) + w);
                traces.add("短语命中[" + phrase + "→" + emo + "]");
                working = working.replace(phrase, " ");
            }
        }

        // 2. Jieba 分词 + 滑动窗口（程度副词 & 否定词）
        List<String> tokens = segmenter.sentenceProcess(working);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).trim();
            if (token.isEmpty()) continue;

            EmotionHit hit = lookupWord(token);
            if (hit == null) continue;

            double degreeMult = 1.0;
            int negCount = 0;
            int windowStart = Math.max(0, i - 3);
            for (int j = windowStart; j < i; j++) {
                String prev = tokens.get(j).trim();
                if (DEGREE_WORDS.containsKey(prev)) {
                    degreeMult *= DEGREE_WORDS.get(prev);
                }
                if (NEGATION_WORDS.contains(prev)) {
                    negCount++;
                }
            }

            String targetEmotion = hit.emotion;
            double weight = hit.weight * degreeMult * clauseWeight;

            if (negCount % 2 == 1) {
                targetEmotion = flipEmotion(hit.emotion);
                weight *= 0.9;
                traces.add("否定翻转[" + token + "→" + targetEmotion + "]");
            } else {
                traces.add("词典命中[" + token + "→" + targetEmotion + "]");
            }
            scores.put(targetEmotion, scores.get(targetEmotion) + weight);
        }
    }

    private EmotionHit lookupWord(String word) {
        if (JOY_WORDS.containsKey(word)) return new EmotionHit("JOY", JOY_WORDS.get(word));
        if (ANXIETY_WORDS.containsKey(word)) return new EmotionHit("ANXIETY", ANXIETY_WORDS.get(word));
        if (SADNESS_WORDS.containsKey(word)) return new EmotionHit("SADNESS", SADNESS_WORDS.get(word));
        if (ANGER_WORDS.containsKey(word)) return new EmotionHit("ANGER", ANGER_WORDS.get(word));
        if (CALM_WORDS.containsKey(word)) return new EmotionHit("CALM", CALM_WORDS.get(word));
        String lower = word.toLowerCase(Locale.ROOT);
        if (ANXIETY_WORDS.containsKey(lower)) return new EmotionHit("ANXIETY", ANXIETY_WORDS.get(lower));
        if (SADNESS_WORDS.containsKey(lower)) return new EmotionHit("SADNESS", SADNESS_WORDS.get(lower));
        return null;
    }

    private String flipEmotion(String original) {
        return switch (original) {
            case "JOY" -> "SADNESS";
            case "CALM" -> "ANXIETY";
            case "ANXIETY", "SADNESS", "ANGER" -> "CALM";
            default -> "UNCERTAIN";
        };
    }

    private List<String> extractKeywords(String text) {
        List<String> tokens = segmenter.sentenceProcess(text);
        Map<String, Double> kwWeights = new LinkedHashMap<>();
        for (String raw : tokens) {
            String t = raw.trim();
            if (!VALID_KEYWORD.matcher(t).matches()) continue;
            if (STOP_WORDS.contains(t) || DEGREE_WORDS.containsKey(t) || NEGATION_WORDS.contains(t)) continue;
            double boost = lookupWord(t) != null ? 1.8 : 1.0;
            if (t.length() >= 3) boost += 0.3;
            kwWeights.merge(t, boost, Double::sum);
        }
        return kwWeights.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<String, Double> zeroScores() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("JOY", 0.0);
        map.put("ANXIETY", 0.0);
        map.put("SADNESS", 0.0);
        map.put("ANGER", 0.0);
        map.put("CALM", 0.0);
        return map;
    }

    private int countChar(String s, char target) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == target) c++;
        }
        return c;
    }

    private record EmotionHit(String emotion, double weight) {}
}
