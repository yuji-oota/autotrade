package autotrade.local.actor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import autotrade.local.material.Pair;
import lombok.Data;

@Component
public class PairManager {

    @Data
    @Component
    @ConfigurationProperties("autotrade")
    static class PairProperties {
        List<Map<String, Object>> pairs;
    }

    private final static DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_TIME
            .withResolverStyle(ResolverStyle.LENIENT);

    private List<Pair> pairs;
    private Map<String, Pair> pairMap;

    public PairManager(PairProperties pairProperties) {
        pairs = pairProperties.getPairs().stream().map(m -> {
            return Pair.builder()
                    .name(m.get("name").toString())
                    .startTime(LocalTime.parse(m.get("handleStart").toString(), timeFormatter))
                    .endTime(LocalTime.parse(m.get("handleEnd").toString(), timeFormatter))
                    .minSpread((int) m.get("minSpread"))
                    .marginRequirement((int) m.get("marginRequirement"))
                    .isDefault(Boolean.valueOf(m.get("isDefault").toString()))
                    .build();
        }).toList();
        pairMap = pairs.stream().collect(Collectors.toMap(p -> p.getName(), Function.identity()));
    }

    public Pair get(String name) {
        return pairMap.get(name);
    }

    public Pair getDefault() {
        return pairs.stream().filter(Pair::isDefault).findFirst().get();
    }

    public List<Pair> getPairs() {
        return pairs;
    }

    public List<String> getDescriptions() {
        return pairs.stream().map(Pair::getDescription).toList();
    }

}
