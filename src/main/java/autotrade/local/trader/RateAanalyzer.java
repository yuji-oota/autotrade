package autotrade.local.trader;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Getter
public class RateAanalyzer {

    private Rate currentRate;
    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;

    @Setter
    private Rate lastOrderRate;

    public RateAanalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
    }

    public void add(Rate rate) {
        currentRate = rate;
        rates.add(currentRate);
        rates = rates.stream()
                .filter(r -> ChronoUnit.MINUTES.between(r.getTimestamp(), LocalDateTime.now()) <= 10)
                .collect(Collectors.toList());

        // ratesから基準値取得
        int max = rates.stream().map(Rate::getAsk).max(Comparator.naturalOrder()).get();
        int min = rates.stream().map(Rate::getBid).min(Comparator.naturalOrder()).get();
//        int half = (max + min) / 2;
//        int threeQuarters = (max + half) / 2;
//        int oneQuarter = (half + min) / 2;

        // 売買閾値設定
//        askThreshold = oneQuarter;
        askThreshold = max;
        bidThreshold = min;
//        if (oneQuarter < currentRate.getAsk()) {
//            askThreshold = half;
//            bidThreshold = oneQuarter;
//        }
//        if (half < currentRate.getAsk()) {
//            askThreshold = threeQuarters;
//            bidThreshold = half;
//        }
//        if (threeQuarters < currentRate.getAsk()) {
//            askThreshold = max;
//            bidThreshold = threeQuarters;
//        }
    }

}
