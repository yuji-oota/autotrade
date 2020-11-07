package autotrade.local.actor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LotManager {

    private int initialPositive;
    private int initialNegative;
    private BigDecimal countertradingMagnification;
    private int countertradingCount;
    private BigDecimal power;
    private Mode mode;
    private Map<String, Object> marginRequirementMap;

    @Getter
    private int limit;

    private enum Mode {
        POSITIVE,
        NEGATIVE
    }

    public LotManager() {
        initialNegative = AutoTradeProperties.getInt("autotrade.lot.negative");
        countertradingMagnification = AutoTradeProperties.getBigDecimal("autotrade.lot.countertrading.magnification");
        countertradingCount = AutoTradeProperties.getInt("autotrade.lot.countertrading.count");
        power = BigDecimal.valueOf(Math.pow(countertradingMagnification.doubleValue(), countertradingCount));
        marginRequirementMap = AutoTradeProperties.getMap("autotrade.lot.marginRequirement");
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
        return more >= getRoughLimit() ?
                more - less :
                    countertradingMagnification.multiply(BigDecimal.valueOf(more)).setScale(0, RoundingMode.UP).intValue() - less;
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
        if (snapshot.getAskLot() >= getRoughLimit()
                || snapshot.getBidLot() >= getRoughLimit()) {
            return true;
        }
        return false;
    }

    private int getRoughLimit() {
        return getInitial() * power.setScale(0, RoundingMode.DOWN).intValue();
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

    public void changeInitialLot(CurrencyPair pair, int margin) {
        int marginRequirement = Integer.parseInt(marginRequirementMap.get(pair.name()).toString());
        int roughEstimateLimit = (int) (margin / marginRequirement * 0.7);

        int initialLot = 0;
        int limitLot = 0;
        while (limitLot < roughEstimateLimit) {
            initialLot++;

            limitLot = initialLot;
            for (int i = 0; i < countertradingCount; i++) {
                limitLot = countertradingMagnification.multiply(BigDecimal.valueOf(limitLot)).setScale(0, RoundingMode.UP).intValue();
            }
        }
        initialPositive = initialLot;
        limit = limitLot;
        log.info("initialPositive changed to {}.", initialPositive);
    }

}
