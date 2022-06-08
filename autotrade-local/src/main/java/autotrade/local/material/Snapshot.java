package autotrade.local.material;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class Snapshot implements Serializable {

    private int positionProfit;
    private int totalProfit;

    private int margin;
    private int effectiveMargin;

    private Rate rate;
    private int askLot;
    private int bidLot;
    private int askAverageRate;
    private int bidAverageRate;
    private int todaysProfit;

    public Pair getPair() {
        return rate.getPair();
    }

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

        if (hasNoPosition()) {
            return PositionStatus.NO_POSITION;
        }

        if (isBidLtAsk()) {
            return PositionStatus.BID_LT_ASK;
        }
        if (isBidGtAsk()) {
            return PositionStatus.BID_GT_ASK;
        }

        return PositionStatus.BID_EQ_ASK;
    }

    public boolean hasOneSide() {
        return (hasAsk() && hasNoBid())
                || (hasNoAsk() && hasBid());
    }

    public boolean hasBothSide() {
        return hasAsk() && hasBid();
    }

    public boolean hasAsk() {
        return askLot > 0;
    }

    public boolean hasNoAsk() {
        return !hasAsk();
    }

    public boolean hasBid() {
        return bidLot > 0;
    }

    public boolean hasNoBid() {
        return !hasBid();
    }

    public boolean hasPosition() {
        return hasAsk() || hasBid();
    }

    public boolean hasNoPosition() {
        return !hasPosition();
    }

    public boolean hasAskOnly() {
        return hasAsk() && hasOneSide();
    }

    public boolean hasBidOnly() {
        return hasBid() && hasOneSide();
    }

    public boolean isBidLtAsk() {
        return bidLot < askLot;
    }

    public boolean isBidLeAsk() {
        return bidLot <= askLot;
    }

    public boolean isBidGtAsk() {
        return bidLot > askLot;
    }

    public boolean isBidGeAsk() {
        return bidLot >= askLot;
    }

    public int getLimitLot() {
        return rate.getPair().getLimitLot(Math.min(margin, effectiveMargin));
    }

    public boolean isAskLtLimit() {
        return askLot < getLimitLot();
    }

    public boolean isBidLtLimit() {
        return bidLot < getLimitLot();
    }

    public boolean isAskGeLimit() {
        return !isAskLtLimit();
    }

    public boolean isBidGeLimit() {
        return !isBidLtLimit();
    }

    public boolean isPositionGeLimit() {
        return isAskGeLimit() || isBidGeLimit();
    }

    public boolean isSpreadWiden() {
        return rate.isSpreadWiden();
    }

    public boolean isSpreadNarrow() {
        return rate.isSpreadNarrow();
    }

    public int getMoreLot() {
        return Math.max(askLot, bidLot);
    }

    public int getLessLot() {
        return Math.min(askLot, bidLot);
    }

    public boolean hasAskProfit() {
        return getAskProfit() > 0;
    }

    public boolean hasBidProfit() {
        return getBidProfit() > 0;
    }

    public boolean hasProfit() {
        return getPositionProfit() > 0;
    }
}