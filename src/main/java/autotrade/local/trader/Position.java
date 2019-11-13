package autotrade.local.trader;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Position {

    private int askLot;
    private int bidLot;
    private int askAverageRate;
    private int bidAverageRate;
    private int askProfit;
    private int bidProfit;

    public int getProfit () {
        return askProfit + bidProfit;
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
