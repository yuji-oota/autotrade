package autotrade.local.actor;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class RangeManager implements Serializable {

    @Getter
    private Rate upperLimit;
    @Getter
    private Rate lowerLimit;
    @Getter
    private Rate upperLimitSave;
    @Getter
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

    public boolean isBeforeApply() {
        return Objects.isNull(upperLimit) || Objects.isNull(lowerLimit);
    }

    public boolean isAfterApply() {
        return !isBeforeApply();
    }

    public boolean isBeforeSave() {
        return Objects.isNull(upperLimitSave) || Objects.isNull(lowerLimitSave);
    }

    public boolean isAfterSave() {
        return !isBeforeSave();
    }

    public boolean isWithinRange(Snapshot snapshot) {
        if (isBeforeApply()) {
            return false;
        }
        Rate rate = snapshot.getRate();
        if (rate.isAskGt(lowerLimit) && rate.isBidLt(upperLimit)) {
            return true;
        }
        return false;
    }

    public boolean isAboveRange(Snapshot snapshot) {
        return snapshot.getRate().isAskGt(upperLimit);
    }

    public boolean isBelowRange(Snapshot snapshot) {
        return snapshot.getRate().isBidLt(lowerLimit);
    }

    public void save(Snapshot snapshot) {

        Rate newRate = snapshot.getRate();

        if (isBeforeSave()) {
            upperLimitSave = newRate;
            lowerLimitSave = newRate;
            return;
        }
        if (newRate.isAskGt(upperLimitSave)) {
            upperLimitSave = newRate;
            return;
        }
        if (newRate.isBidLt(lowerLimitSave)) {
            lowerLimitSave = newRate;
            return;
        }
    }

    public void print() {
        if (Objects.nonNull(lowerLimit) && Objects.nonNull(upperLimit)) {
            log.info("lower limit:{} middle:{} upper limit:{} isExtended:{}",
                    lowerLimit.getBid(), getMiddle(), upperLimit.getAsk(), isExtended);
        }
    }

    public void apply() {
        isExtended = isSaveExtend();

        lowerLimit = lowerLimitSave;
        upperLimit = upperLimitSave;
    }

    public boolean isSaveExtend() {
        if (isBeforeApply()) {
            return true;
        }
        return lowerLimitSave.isBidLt(lowerLimit) || upperLimitSave.isAskGt(upperLimit);
    }

    public boolean isNearUpperLimit(Snapshot snapshot) {
        return toDiffFromUpperLimit.applyAsInt(snapshot) < toDiffFromLowerLimit.applyAsInt(snapshot);
    }

    public boolean isNearLowerLimit(Snapshot snapshot) {
        return toDiffFromUpperLimit.applyAsInt(snapshot) > toDiffFromLowerLimit.applyAsInt(snapshot);
    }

    public int getRange() {
        return upperLimit.getAsk() - lowerLimit.getBid();
    }

    public int getSaveRange() {
        return upperLimitSave.getAsk() - lowerLimitSave.getBid();
    }

    public int getMiddle() {
        return (lowerLimit.getBid() + upperLimit.getAsk()) / 2;
    }

    public int getSaveMiddle() {
        return (lowerLimitSave.getBid() + upperLimitSave.getAsk()) / 2;
    }

    public boolean isFirstSave() {
        return lowerLimitSave == upperLimitSave;
    }

    public void adjustTermination(int lower, int upper) {
        if (lowerLimitSave.getBid() > lower) {
            lowerLimitSave = lowerLimitSave.toBuilder().bid(lower).ask(lower + lowerLimitSave.getSpread()).build();
        }
        if (upperLimitSave.getAsk() < upper) {
            upperLimitSave = upperLimitSave.toBuilder().bid(upper - upperLimitSave.getSpread()).ask(upper).build();
        }
    }

    public boolean isRange() {
        return !isExtended
                && !isSaveExtend();
    }

    public boolean isUpword() {
        if (isBeforeSave()) {
            return false;
        }
        return upperLimitSave.getTimestamp().isAfter(lowerLimitSave.getTimestamp());
    }

    public boolean isDownword() {
        if (isBeforeSave()) {
            return false;
        }
        return lowerLimitSave.getTimestamp().isAfter(upperLimitSave.getTimestamp());
    }
}