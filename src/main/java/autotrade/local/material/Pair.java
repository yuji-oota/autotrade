package autotrade.local.material;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Pair implements Serializable {

    private String name;
    private int minSpread;
    private int marginRequirement;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal profitMagnification;

    public int getLimitLot(int margin) {
        return (int) (margin / marginRequirement * 0.9);
    }

    public boolean isHandleable(LocalTime time) {
        if (startTime.equals(endTime)) {
            return true;
        }
        if (startTime.equals(time)) {
            return true;
        }
        if (startTime.isBefore(endTime)) {
            return startTime.isBefore(time) && time.isBefore(endTime);
        }
        if (LocalTime.MIN.equals(time)) {
            return true;
        }
        return (startTime.isBefore(time) && time.isBefore(LocalTime.MAX))
                || (LocalTime.MIN.isBefore(time) && time.isBefore(endTime));
    }
    
    public String getDescription() {
        return new StringBuilder(name).insert(3, "/").toString();
    }
}
