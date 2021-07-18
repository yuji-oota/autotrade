package autotrade.local.material;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Snapshot implements Serializable {

    private String pair;

    // Tostring用
    @SuppressWarnings("unused")
    private int positionProfit;
    @SuppressWarnings("unused")
    private int totalProfit;

    private int margin;
    private int effectiveMargin;

    private Rate rate;
    private int askLot;
    private int bidLot;
    private int askAverageRate;
    private int bidAverageRate;
    private int todaysProfit;

    public int getAskPipProfit() {
        return rate.getBid() - askAverageRate;
    }
    public int getBidPipProfit() {
        return bidAverageRate - rate.getAsk();
    }

    public int getAskProfit() {
        return askLot * getAskPipProfit();
    }
    public int getBidProfit() {
        return bidLot * getBidPipProfit();
    }

    public int getPositionProfit() {
        return getAskProfit() + getBidProfit();
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

    public boolean hasOneSide() {
        if ((hasAsk() && bidLot == 0)
                || (askLot == 0 && hasBid())) {
            return true;
        }
        return false;
    }
    public boolean hasBothSide() {
        if (askLot > 0 && bidLot > 0) {
            return true;
        }
        return false;
    }

    public boolean hasAsk() {
        return askLot > 0;
    }

    public boolean hasBid() {
        return bidLot > 0;
    }

    public boolean hasPosition() {
        if (hasAsk() || hasBid()) {
            return true;
        }
        return false;
    }

    public boolean isPositionNone() {
        return getStatus() == PositionStatus.NONE;
    }
    public boolean isPositionAskSide() {
        return getStatus() == PositionStatus.ASK_SIDE;
    }
    public boolean isPositionBidSide() {
        return getStatus() == PositionStatus.BID_SIDE;
    }
    public boolean isPositionSame() {
        return getStatus() == PositionStatus.SAME;
    }
}
