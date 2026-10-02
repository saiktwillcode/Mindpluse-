package com.mindpulse;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

/** Context-aware NLP engine. Replace analyze() with a transformer/LLM call without changing the API contract. */
@Service
public class NlpService {
    public record Ev(String w, String c, String k, boolean n) {}
    public record Result(String text, int stress, int work, int frus, int eng, int wb, Map<String, Integer> emo,
                         String context, List<String> topics, List<Ev> ev, int diff, String intensity, int conf, String why) {}

    private static final Map<String, Pattern> L = new LinkedHashMap<>();
    private static final Map<String, Pattern> TOP = new LinkedHashMap<>();
    private static final Pattern SPLIT = p("(?<=[.!?;\\n])\\s+|\\s+(?=(?:but|however|although)\\b)");
    private static final Pattern BUT = p("^(but|however)\\b"), HEDGE = p("\\b(thought|expected|feared|assumed|worried that)\\b");
    private static final Pattern FLIP = p("\\b(anymore|no longer|any more)\\b"), NEG = p("(n't|\\b(not|no|never|without|hardly|barely|cannot|nothing)\\b)");
    private static final Pattern INT = p("\\b(very|extremely|so|really|too|incredibly|totally|completely|super)\\b"), DIM = p("\\b(slightly|a bit|a little|somewhat|kind of)\\b");
    private static final Pattern QTY = p("(\\d+|two|three|four|five|many|multiple|several)\\s+(\\w+\\s+)?$");
    private static final Map<String, String> WHY = Map.of(
        "Academic workload", "Workload pressure was detected because the text refers to several tasks and approaching deadlines.",
        "Technical problems", "Technical friction was detected because the text mentions platform or portal failures.",
        "Course difficulty", "Learning difficulty was detected because the text describes confusion or getting stuck on material.",
        "Positive academic experience", "A positive experience was detected because motivation and comfort cues outweigh pressure cues.",
        "Engagement decline", "Disengagement was detected because the text describes boredom or withdrawing from the course.",
        "General academic experience", "No strong academic signals were found, so the text reads as neutral.");

    private static Pattern p(String s) { return Pattern.compile(s); }
    static {
        L.put("stress", p("\\b(stress(ed|ful)?|anxious|anxiety|overwhelm(ed|ing)|panic\\w*|worr(y|ied)|pressure|exhaust(ed|ing)|burn(ed|t)? ?out|nervous)\\b"));
        L.put("work", p("\\b(assignments?|deadlines?|workload|homework|projects?|exams?|too many|backlog|behind|due|submissions?|lab reports?|problem sets?|quiz(zes)?)\\b"));
        L.put("frus", p("\\b(frustrat\\w+|annoy\\w+|confus\\w+|unfair|stuck|difficult\\w*|hard|can'?t (understand|follow|figure)|cannot (understand|follow)|struggl\\w+|lost|pointless)\\b"));
        L.put("pos", p("\\b(enjoy\\w*|interesting|excited|love[ds]?|curious|motivated|engag\\w+|fun|helpful|inspiring|great|happy|proud|confident)\\b"));
        L.put("neg", p("\\b(bored|boring|skip\\w*|unmotivated|disengag\\w+|don'?t care|dropp\\w+|absent|zone[ds]? out|give up|giving up)\\b"));
        L.put("calm", p("\\b(manageable|calm|relaxed|fine|okay|easy|comfortable|under control|balanced|coping)\\b"));
        L.put("sad", p("\\b(sad|lonely|hopeless|down|depress\\w*|discouraged)\\b"));
        L.put("tech", p("\\b(wifi|portal|login|server|crash\\w*|bug|platform|lms|upload)\\b"));
        TOP.put("Assignments", p("assignment|project|homework|lab|problem set"));
        TOP.put("Deadlines", p("deadline|\\bdue\\b|submission"));
        TOP.put("Course difficulty", p("difficult|hard|confus|stuck|lost|can'?t (understand|follow)|cannot|proof|struggl"));
        TOP.put("Exams", p("exam|quiz|test"));
        TOP.put("Technical problems", p("wifi|portal|login|server|crash|bug|platform|upload"));
        TOP.put("Engagement", p("bored|skip|motivat|interest|enjoy|engag"));
    }

    private static int cl(double x, int a, int b) { return (int) Math.max(a, Math.min(b, Math.round(x))); }
    private static double sg(double x) { return 100 * (1 - Math.exp(-x / 2.2)); }
    private static void add(Map<String, Double> m, String k, double v) { m.merge(k, v, Double::sum); }
    private static double g(Map<String, Double> m, String k) { return m.getOrDefault(k, 0.0); }
    private static String last(String s, int n) {
        String[] w = s.split("\\s+");
        return String.join(" ", Arrays.copyOfRange(w, Math.max(0, w.length - n), w.length));
    }

    public Result analyze(String text) {
        String low = text.toLowerCase();
        String[] cs = Arrays.stream(SPLIT.split(low)).filter(s -> !s.isBlank()).toArray(String[]::new);
        Map<String, Double> a = new HashMap<>();
        List<Ev> ev = new ArrayList<>();
        for (int ci = 0; ci < cs.length; ci++) {
            String c = cs[ci];
            double w = BUT.matcher(c.trim()).find() ? 1.4 : 1;
            if (HEDGE.matcher(c).find() && ci < cs.length - 1) w = .25;
            boolean fl = FLIP.matcher(c).find();
            Map<String, Double> t = new HashMap<>();
            List<Ev> clauseEv = new ArrayList<>();
            String snip = c.trim().substring(0, Math.min(110, c.trim().length()));
            for (var e : L.entrySet()) {
                String k = e.getKey();
                Matcher m = e.getValue().matcher(c);
                while (m.find()) {
                    String pre = c.substring(0, m.start()), p4 = last(pre, 4), p3 = last(pre, 3), mw = m.group();
                    double v = w;
                    if (INT.matcher(p3).find()) v *= 1.7;
                    if (DIM.matcher(p4).find()) v *= .5;
                    if (k.equals("work") && QTY.matcher(pre).find()) v *= 1.8;
                    boolean n = NEG.matcher(p4).find();
                    if (k.equals("pos") || k.equals("calm")) {
                        if (n) { add(t, k.equals("pos") ? "neg" : "stress", v * .7); clauseEv.add(new Ev("not " + mw, snip, k, true)); }
                        else { add(t, k, v); clauseEv.add(new Ev(mw, snip, k, false)); }
                    } else if (n || (fl && Set.of("stress", "frus", "sad", "neg").contains(k))) {
                        add(t, "relief", v); clauseEv.add(new Ev("not " + mw, snip, k, true));
                    } else { add(t, k, v); clauseEv.add(new Ev(mw, snip, k, false)); }
                }
            }
            if (g(t, "calm") > 0) t.put("work", g(t, "work") * .3);
            t.forEach((k, v) -> add(a, k, v));
            ev.addAll(clauseEv);
        }
        double s = g(a, "stress"), wk = g(a, "work"), fr = g(a, "frus"), po = g(a, "pos"), ne = g(a, "neg"),
               ca = g(a, "calm"), sa = g(a, "sad"), tc = g(a, "tech"), re = g(a, "relief");
        int stress = cl(12 + sg(s + .5 * wk + .4 * fr) * .88 - ca * 5 - re * 4, 5, 98);
        int work = cl(8 + sg(wk) * .9 - re * 3, 5, 98);
        int frus = cl(8 + sg(fr) * .9 - re * 3 - ca * 3, 5, 98);
        int eng = cl(55 + 14 * po + 4 * ca - 16 * ne - 6 * sa - 3 * fr, 5, 97);
        int wb = cl(100 - .3 * stress - .28 * work - .2 * frus + .1 * (eng - 50), 5, 98);

        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("happiness", po + re * .4); raw.put("calm", ca + re * .8); raw.put("neutral", .7);
        raw.put("anxiety", s + .3 * wk); raw.put("frustration", fr); raw.put("sadness", sa + ne * .5);
        raw.put("overload", Math.max(0, wk + s - 2) * .8);
        double tot = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (tot == 0) tot = 1;
        Map<String, Integer> emo = new LinkedHashMap<>();
        for (var e : raw.entrySet()) emo.put(e.getKey(), cl(e.getValue() / tot * 100, 0, 100));

        List<Map.Entry<String, Double>> ctx = new ArrayList<>(List.of(
            Map.entry("Academic workload", wk), Map.entry("Technical problems", tc * 1.2), Map.entry("Course difficulty", fr),
            Map.entry("Positive academic experience", po + ca), Map.entry("Engagement decline", ne * 1.3 + sa * .5)));
        ctx.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        String context = ctx.get(0).getValue() > 0 ? ctx.get(0).getKey() : "General academic experience";

        List<String> topics = new ArrayList<>();
        for (var e : TOP.entrySet()) if (e.getValue().matcher(low).find()) topics.add(e.getKey());
        if (topics.isEmpty()) topics.add("General");

        Set<String> seen = new HashSet<>();
        List<Ev> evu = new ArrayList<>();
        for (Ev e : ev) if (seen.add(e.w()) && evu.size() < 7) evu.add(e);
        int mx = Math.max(stress, Math.max(work, frus));
        String negated = evu.stream().filter(Ev::n).map(Ev::w).findFirst().orElse(null);
        String why = WHY.get(context) + (negated != null ? " The negated phrase \"" + negated + "\" was treated as relief, not distress." : "");
        return new Result(text, stress, work, frus, eng, wb, emo, context, topics, evu,
            cl(frus + (topics.contains("Course difficulty") ? 20 : 0), 0, 100),
            mx > 70 ? "High" : mx > 45 ? "Moderate" : "Low", evu.isEmpty() ? 52 : cl(58 + evu.size() * 7, 0, 96), why);
    }
}
