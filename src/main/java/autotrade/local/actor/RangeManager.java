package autotrade.local.actor;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.ToIntFunction;

import org.springframework.stereotype.Component;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RangeManager implements Serializable {

    @Getter
    private Rate upperLimit;
    @Getter
    private Rate lowerLimit;

    private Rate upperLimitSave;
    private Rate lowerLimitSave;

    @Getter
    private boolean isExtended;

    @SuppressWarnings("unchecked")
    private ToIntFunction<Snapshot> toDiffFromUpperLimit = (ToIntFunction<Snapshot> & Serializable) s -> upperLimit
            .getAsk() - s.getRate().getAsk();
    @SuppressWarnings("unchecked")
    private ToIntFunction<Snapshot> toDiffFromLowerLimit = (ToIntFunction<Snapshot> & Serializable) s -> s.getRate()
            .getBid() - lowerLimit.getBid();

    public void reset() {
        upperLimit = null;
        lowerLimit = null;
        upperLimitSave = null;
        lowerLimitSave = null;
    }

    public boolean isWithinRange(Snapshot snapshot) {
        if (Objects.isNull(upperLimit) || Objects.isNull(lowerLimit)) {
            return false;
        }
        Rate rate = snapshot.getRate();
        if (rate.isAbobe(lowerLimit) && rate.isBelow(upperLimit)) {
            return true;
        }
        return false;
    }

    public void save(Snapshot snapshot) {

        Rate newRate = snapshot.getRate();

        if (Objects.isNull(upperLimitSave) || Objects.isNull(lowerLimitSave)) {
            upperLimitSave = newRate;
            lowerLimitSave = newRate;
            return;
        }
        if (newRate.isAbobe(upperLimitSave)) {
            upperLimitSave = newRate;
            return;
        }
        if (newRate.isBelow(lowerLimitSave)) {
            lowerLimitSave = newRate;
            return;
        }
    }

    public void apply() {
        isExtended = isSaveExtend();

        lowerLimit = lowerLimitSave;
        upperLimit = upperLimitSave;
        if (Objects.nonNull(lowerLimit) && Objects.nonNull(upperLimit)) {
            log.info("lower limit:{} upper limit:{} isExtended:{}", lowerLimit.getRawBid(), upperLimit.getRawAsk(),
                    isExtended);
        }
    }

    public boolean isSaveExtend() {
        if (Objects.isNull(upperLimit) || Objects.isNull(lowerLimit)) {
            return true;
        }
        return lowerLimitSave.isBelow(lowerLimit) || upperLimitSave.isAbobe(upperLimit);
    }

    public boolean isNearUpperLimit(Snapshot snapshot) {
        return toDiffFromUpperLimit.applyAsInt(snapshot) < toDiffFromLowerLimit.applyAsInt(snapshot);
    }

    public boolean isNearLowerLimit(Snapshot snapshot) {
        return toDiffFromUpperLimit.applyAsInt(snapshot) > toDiffFromLowerLimit.applyAsInt(snapshot);
    }
}
