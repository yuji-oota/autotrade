package autotrade.local.config;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public ToIntFunction<Snapshot> toProfit() {
        return (ToIntFunction<Snapshot> & Serializable) s -> new BigDecimal(s.getMargin())
                .multiply(s.getPair().getProfitMagnification()).intValue();
    }

    @Bean
    public ToIntFunction<Snapshot> toInitialLot(ToIntFunction<Snapshot> toProfit) {
        return (ToIntFunction<Snapshot> & Serializable) s -> toProfit.applyAsInt(s) / 100;
    }

    @Bean
    public ToIntFunction<Snapshot> toMinimumProfit() {
        return (ToIntFunction<Snapshot> & Serializable) s -> s.getMargin() / 10000;
    }

    @Bean
    public ToIntFunction<Snapshot> toTargetProgress(ToIntFunction<Snapshot> toInitialLot,
            @Value("${autotrade.config.toTargetProgress.maxRatio}") int maxRatio,
            @Value("${autotrade.config.toTargetProgress.ratioRange}") BigDecimal ratioRange) {
        return (ToIntFunction<Snapshot> & Serializable) s -> {
            BigDecimal limitSubInitial = new BigDecimal(
                    s.getPair().getLimitLot(s.getEffectiveMargin()) - toInitialLot.applyAsInt(s));
            BigDecimal currentSubInitial = new BigDecimal(s.getMoreLot() - toInitialLot.applyAsInt(s));
            BigDecimal progressUnit = limitSubInitial.divide(ratioRange, 1, RoundingMode.HALF_UP);
            return maxRatio - currentSubInitial.divide(progressUnit, 0, RoundingMode.HALF_UP).intValue();
        };
    }

    @Bean
    public Map<String, RateAnalyzer> pairAnalyzerMap(ApplicationContext applicationContext,
            PairManager pairManager) {
        return pairManager.getPairs().stream()
                .collect(Collectors.toMap(pair -> pair.getName(),
                        pair -> applicationContext.getBean(RateAnalyzer.class)));
    }

}
