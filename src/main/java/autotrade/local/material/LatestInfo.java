package autotrade.local.material;

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

    public int getPositionProfit() {
        return askProfit + bidProfit;
    }

    public int getTotalProfit() {
        return getPositionProfit() + todaysProfit;
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
}