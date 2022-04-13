package autotrade.local.config;

import java.io.Serializable;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import autotrade.local.actor.PairManager;
import autotrade.local.actor.RateAnalyzer;
import autotrade.local.material.Snapshot;

@SuppressWarnings("unchecked")
@Configuration
public class AutoTradeConfig {

    @Bean
    public ToIntFunction<Snapshot> toProfit(
            @Value("${autotrade.config.toProfit.denominator}") int denominator) {
        return (ToIntFunction<Snapshot> & Serializable) s -> s.getMargin() / denominator;
    }

//    @Bean
//    public ToIntFunction<Snapshot> toInitialLot(ToIntFunction<Snapshot> toProfit,
//            @Value("${autotrade.config.toInitialLot.denominator}") int denominator) {
//        return (ToIntFunction<Snapshot> & Serializable) s -> toProfit.applyAsInt(s) / denominator;
//    }
    @Bean
    public ToIntFunction<Snapshot> toInitialLot(@Value("${autotrade.config.toInitialLot.denominator}") int denominator) {
        return (ToIntFunction<Snapshot> & Serializable) s -> (s.getLimitLot() / denominator) + 1;
    }

//    @Bean
//    public ToIntFunction<Snapshot> toNextLot(ToIntFunction<Snapshot> toProfit,
//            @Value("${autotrade.config.toNextLot.denominator}") int denominator) {
//        return (ToIntFunction<Snapshot> & Serializable) s -> (s.getLimitLot() - s.getMoreLot()) / denominator + 1;
//    }
    @Bean
    public ToIntFunction<Snapshot> toNextLot() {
        return (ToIntFunction<Snapshot> & Serializable) s -> 1;
    }

//    @Bean
//    public ToIntFunction<Snapshot> toMinimumProfit() {
//        return (ToIntFunction<Snapshot> & Serializable) s -> s.getMargin() / 10000;
//    }
    @Bean
    public ToIntFunction<Snapshot> toMinimumProfit() {
        return (ToIntFunction<Snapshot> & Serializable) s -> 1;
    }

    @Bean
    public Map<String, RateAnalyzer> pairAnalyzerMap(ApplicationContext applicationContext,
            PairManager pairManager) {
        return pairManager.getPairs().stream()
                .collect(Collectors.toMap(pair -> pair.getName(),
                        pair -> applicationContext.getBean(RateAnalyzer.class)));
    }

}
