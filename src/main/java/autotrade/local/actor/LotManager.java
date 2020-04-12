package autotrade.local.actor;

import java.util.HashMap;
import java.util.Map;

import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LotManager {

    private int initialPositive;
    private int initialNegative;
    private int countertradingMagnification;
    private int countertradingCount;
    private int power;
    private Mode mode;
    private CurrencyPair basePair;
    private Map<CurrencyPair, Rate> sampleRateMap;

    private enum Mode {
        POSITIVE,
        NEGATIVE
    }

    public LotManager() {
        basePair = CurrencyPair.valueOf(AutoTradeProperties.get("autotrade.lot.initial.base.pair"));
        initialPositive = AutoTradeProperties.getInt("autotrade.lot.initial.base.positive");
        initialNegative = AutoTradeProperties.getInt("autotrade.lot.initial.negative");
        countertradingMagnification = AutoTradeProperties.getInt("autotrade.lot.countertrading.magnification");
        countertradingCount = AutoTradeProperties.getInt("autotrade.lot.countertrading.count");
        power = (int) Math.pow(countertradingMagnification, countertradingCount);
        sampleRateMap = new HashMap<>();
        modePositive();
    }

    public int nextLot(Snapshot snapshot) {
        if (snapshot.isPositionNone()) {
            return getInitial();
        }
        int more = snapshot.getAskLot();
        int less = snapshot.getBidLot();
        if (snapshot.isPositionBidSide()) {
            more = snapshot.getBidLot();
            less = snapshot.getAskLot();
        }
        return more >= getlimit() ? more - less : (more * countertradingMagnification) - less;
    }
    public void modePositive() {
        if (isPositive()) {
            return;
        }
        mode = Mode.POSITIVE;
        log.info("mode is changed to positive.");
    }
    public void modeNegative() {
        if (isNegative()) {
            return;
        }
        mode = Mode.NEGATIVE;
        log.info("mode is changed to negative.");
    }
    public boolean isPositive() {
        if (Mode.POSITIVE == mode) {
            return true;
        }
        return false;
    }
    public boolean isNegative() {
        return !isPositive();
    }
    public boolean isLimit(Snapshot snapshot) {
        if (snapshot.getAskLot() >= getlimit()
                || snapshot.getBidLot() >= getlimit()) {
            return true;
        }
        return false;
    }

    private int getlimit() {
        return getInitial() * power;
    }
    public int getInitial() {
        if (isPositive()) {
            return initialPositive;
        }
        return initialNegative;
    }

    public void incrementInitialPositive() {
        initialPositive = initialPositive + 1;
        log.info("initialPositive changed to {}.", initialPositive);
    }
    public void decrementInitialPositive() {
        initialPositive = initialPositive - 1;
        log.info("initialPositive changed to {}.", initialPositive);
    }

    public void addSampleRateMap(CurrencyPair pair, Rate rate) {
        sampleRateMap.put(pair, rate);
    }

    public void changeInitialLot(CurrencyPair pair) {
        var cp = basePair;
        if ("JPY".equals(pair.name().substring(3))) {
            cp = pair;
        }
        if ("USD".equals(pair.name().substring(3))) {
            cp = CurrencyPair.valueOf(pair.name().replace("USD", "JPY"));
        }
        initialPositive = calcInitialLot(cp);
        log.info("initialPositive changed to {}.", initialPositive);
    }

    private int calcInitialLot(CurrencyPair pair) {
        var basePositive = AutoTradeProperties.getInt("autotrade.lot.initial.base.positive");
        return basePositive * sampleRateMap.get(basePair).getMiddle() / sampleRateMap.get(pair).getMiddle();
    }

}
