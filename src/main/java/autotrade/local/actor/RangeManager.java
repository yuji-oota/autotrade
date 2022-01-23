package autotrade.local.actor;

import java.io.Serializable;
import java.util.Optional;

import org.springframework.stereotype.Component;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RangeManager implements Serializable {

    private Optional<Rate> upperLimit = Optional.empty();
    private Optional<Rate> lowerLimit = Optional.empty();

    private Optional<Rate> upperLimitSave = Optional.empty();
    private Optional<Rate> lowerLimitSave = Optional.empty();

    public void reset() {
        upperLimit = Optional.empty();
        lowerLimit = Optional.empty();
        upperLimitSave = Optional.empty();
        lowerLimitSave = Optional.empty();
    }

    public boolean isWithinRange(Snapshot snapshot) {
        if (upperLimit.isEmpty() || lowerLimit.isEmpty()) {
            return false;
        }
        Rate rate = snapshot.getRate();
        if (rate.isAbobe(lowerLimit.get()) && rate.isBelow(upperLimit.get())) {
            return true;
        }
        return false;
    }

    public void save(Snapshot snapshot) {

        Rate newRate = snapshot.getRate();

        if (upperLimitSave.isEmpty() || lowerLimitSave.isEmpty()) {
            upperLimitSave = Optional.of(newRate);
            lowerLimitSave = Optional.of(newRate);
            return;
        }
        if (snapshot.isBidLtAsk()) {
            if (newRate.isAbobe(upperLimitSave.get())) {
                upperLimitSave = Optional.of(newRate);
                return;
            }
        }
        if (snapshot.isBidGtAsk()) {
            if (newRate.isBelow(lowerLimitSave.get())) {
                lowerLimitSave = Optional.of(newRate);
                return;
            }
        }
    }

    public void apply() {
        lowerLimit = lowerLimitSave;
        upperLimit = upperLimitSave;
        if (lowerLimit.isPresent() && upperLimit.isPresent()) {
            log.info("lower limit:{} upper limit:{}", lowerLimit.get().getRawBid(), upperLimit.get().getRawAsk());
        }
    }
}
