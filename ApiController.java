package com.mindpulse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {
    public record Req(String text) {}
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE = Pattern.compile("(?:\\+\\d{1,3}[\\s-]?)?\\b\\d{5}[\\s-]?\\d{5}\\b");
    private static final Pattern SID = Pattern.compile("\\b\\d{2}[A-Za-z]{2,4}\\d{3,5}\\b");

    private final NlpService nlp;
    private final Map<String, List<NlpService.Result>> history = new ConcurrentHashMap<>();

    public ApiController(NlpService nlp) { this.nlp = nlp; }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Req req, HttpServletRequest http) {
        if (req == null || req.text() == null || req.text().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Text must not be empty."));
        if (req.text().length() > 5000)
            return ResponseEntity.badRequest().body(Map.of("error", "Text is too long (max 5000 characters)."));
        String safe = SID.matcher(PHONE.matcher(EMAIL.matcher(req.text()).replaceAll("[EMAIL]")).replaceAll("[PHONE]")).replaceAll("[STUDENT_ID]");
        NlpService.Result r = nlp.analyze(safe);
        List<NlpService.Result> mine = history.computeIfAbsent((String) http.getAttribute("email"), k -> new CopyOnWriteArrayList<>());
        mine.add(r);
        if (mine.size() > 200) mine.remove(0);
        return ResponseEntity.ok(r);
    }

    /** A user only ever sees their own analyses. */
    @GetMapping("/analysis/history")
    public List<NlpService.Result> myHistory(HttpServletRequest http) {
        List<NlpService.Result> all = history.getOrDefault((String) http.getAttribute("email"), List.of());
        return new ArrayList<>(all.subList(Math.max(0, all.size() - 50), all.size()));
    }

    /** FACULTY/ADMIN only. Aggregates only; no text, no identities; hidden until 5+ responses. */
    @GetMapping("/dashboard/faculty")
    public Map<String, Object> faculty() {
        List<NlpService.Result> all = history.values().stream().flatMap(List::stream).toList();
        if (all.size() < 5) return Map.of("count", all.size(), "message", "Not enough responses to show aggregates safely (minimum 5).");
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("count", all.size());
        o.put("avgStress", avg(all, NlpService.Result::stress));
        o.put("avgWorkload", avg(all, NlpService.Result::work));
        o.put("avgEngagement", avg(all, NlpService.Result::eng));
        o.put("avgFrustration", avg(all, NlpService.Result::frus));
        o.put("topics", all.stream().collect(Collectors.groupingBy(r -> r.topics().get(0), TreeMap::new, Collectors.counting())));
        return o;
    }

    private static int avg(List<NlpService.Result> l, ToIntFunction<NlpService.Result> f) {
        return (int) Math.round(l.stream().mapToInt(f).average().orElse(0));
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok", "engine", "rule-based-nlp-v1"); }
}
