package autotrade.local.actor;

import autotrade.local.material.Rate;
import lombok.Data;

@Data
public class ReserveManager {

    private int reservedValue;
    private boolean isResevedLimitFixAsk;
    private boolean isResevedLimitFixBid;
    private boolean isResevedStopFixAsk;
    private boolean isResevedStopFixBid;

    public boolean isLimitFixAsk(Rate rate) {
        return rate.getBid() >= reservedValue;
    }
    public boolean isLimitFixBid(Rate rate) {
        return rate.getAsk() <= reservedValue;
    }
    public boolean isStopFixAsk(Rate rate) {
        return rate.getBid() <= reservedValue;
    }
    public boolean isStopFixBid(Rate rate) {
        return rate.getAsk() >= reservedValue;
    }

    public void resetReserve() {
        isResevedLimitFixAsk = false;
        isResevedLimitFixBid = false;
        isResevedStopFixAsk = false;
        isResevedStopFixBid = false;
    }

    public void reserveLimitFixAsk(int reservedValue) {
        this.reservedValue = reservedValue;
        isResevedLimitFixAsk = true;
    }
    public void reserveLimitFixBid(int reservedValue) {
        this.reservedValue = reservedValue;
        isResevedLimitFixBid = true;
    }
    public void reserveStopFixAsk(int reservedValue) {
        this.reservedValue = reservedValue;
        isResevedStopFixAsk = true;
    }
    public void reserveStopFixBid(int reservedValue) {
        this.reservedValue = reservedValue;
        isResevedStopFixBid = true;
    }

}
