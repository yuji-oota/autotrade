package autotrade.local.material;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Snapshot implements Serializable {

    private CurrencyPair pair;

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

    private boolean isFix;

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

    public boolean hasAskOnly() {
        return hasAsk() && hasOneSide();
    }

    public boolean hasBidOnly() {
        return hasBid() && hasOneSide();
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

    public boolean isAskGtBid() {
        return askLot > bidLot;
    }

    public boolean isAskGeBid() {
        return askLot >= bidLot;
    }

    public boolean isBidGtAsk() {
        return bidLot > askLot;
    }

    public boolean isBidGeAsk() {
        return bidLot >= askLot;
    }

    public boolean isAskLtBid() {
        return isBidGtAsk();
    }

    public boolean isAskLeBid() {
        return isBidGeAsk();
    }

    public boolean isBidLtAsk() {
        return isAskGtBid();
    }

    public boolean isBidLeAsk() {
        return isAskGeBid();
    }

    public boolean isAskLtLimit() {
        return askLot < pair.getLimitLot(Math.min(margin, effectiveMargin));
    }

    public boolean isBidLtLimit() {
        return bidLot < pair.getLimitLot(Math.min(margin, effectiveMargin));
    }

    public boolean isAskGeLimit() {
        return !isAskLtLimit();
    }

    public boolean isBidGeLimit() {
        return !isBidLtLimit();
    }

    public boolean isSpreadWiden() {
        return rate.isSpreadWiden();
    }

    public int getMoreLot() {
        return Math.max(askLot, bidLot);
    }

    public int getLessLot() {
        return Math.min(askLot, bidLot);
    }
}
