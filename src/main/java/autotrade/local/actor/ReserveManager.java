package autotrade.local.actor;

import autotrade.local.material.Rate;
import lombok.Data;

@Data
public class ReserveManager {

    private int limitFixAsk;
    private int limitFixBid;
    private int stopFixAsk;
    private int stopFixBid;
    private boolean isResevedLimitFixAsk;
    private boolean isResevedLimitFixBid;
    private boolean isResevedStopFixAsk;
    private boolean isResevedStopFixBid;

    public boolean isLimitFixAsk(Rate rate) {
        return rate.getBid() >= limitFixAsk;
    }
    public boolean isLimitFixBid(Rate rate) {
        return rate.getAsk() <= limitFixBid;
    }
    public boolean isStopFixAsk(Rate rate) {
        return rate.getBid() <= stopFixAsk;
    }
    public boolean isStopFixBid(Rate rate) {
        return rate.getAsk() >= stopFixBid;
    }

    public void resetReserve() {
        isResevedLimitFixAsk = false;
        isResevedLimitFixBid = false;
        isResevedStopFixAsk = false;
        isResevedStopFixBid = false;
    }

    public void reserveLimitFixAsk(int reservedValue) {
        this.limitFixAsk = reservedValue;
        isResevedLimitFixAsk = true;
    }
    public void reserveLimitFixBid(int reservedValue) {
        this.limitFixBid = reservedValue;
        isResevedLimitFixBid = true;
    }
    public void reserveStopFixAsk(int reservedValue) {
        this.stopFixAsk = reservedValue;
        isResevedStopFixAsk = true;
    }
    public void reserveStopFixBid(int reservedValue) {
        this.stopFixBid = reservedValue;
        isResevedStopFixBid = true;
    }

}
