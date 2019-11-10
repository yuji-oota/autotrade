package autotrade.local.trader;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Position {

    private int askLot;
    private int bidLot;
    private int profit;

    public boolean hasPosition() {
        return (askLot + bidLot) > 0;
    }

    public boolean isAskSide() {
        return bidLot < askLot;
    }
}
