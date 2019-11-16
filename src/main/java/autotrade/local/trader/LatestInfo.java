package autotrade.local.trader;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LatestInfo {

    private int askLot;
    private int bidLot;
    private int askAverageRate;
    private int bidAverageRate;
    private int askProfit;
    private int bidProfit;
    private int todaysProfit;
    private Rate rate;

    public int getProfit() {
        return askProfit + bidProfit;
    }

    public int getTotalProfit() {
        return getProfit() + todaysProfit;
    }

    public PositionStatus getStatus() {

        if (askLot + bidLot == 0) {
            return PositionStatus.NONE;
        }

        if (askLot > bidLot) {
            return PositionStatus.ASK_SIDE;
        }
        if (askLot < bidLot) {
            return PositionStatus.BID_SIDE;
        }

        return PositionStatus.SAME;
    }

    public boolean isRecovering(int initialLot) {
        switch (getStatus()) {
        case ASK_SIDE:
            if (askLot > initialLot && bidLot == 0) {
                return true;
            }
            break;
        case BID_SIDE:
            if (bidLot > initialLot && askLot == 0) {
                return true;
            }
            break;

        case NONE:
        case SAME:
            break;
        }
        return false;
    }
}
