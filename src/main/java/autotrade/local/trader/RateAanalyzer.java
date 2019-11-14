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
    private Rate lastOrderAskRate;
    @Setter
    private Rate lastOrderBidRate;

    public RateAanalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        lastOrderAskRate = new Rate(Integer.MAX_VALUE, Integer.MIN_VALUE, LocalDateTime.now());
        lastOrderBidRate = new Rate(Integer.MAX_VALUE, Integer.MIN_VALUE, LocalDateTime.now());
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

        // 売買閾値設定
        askThreshold = max;
        bidThreshold = min;
    }

}
