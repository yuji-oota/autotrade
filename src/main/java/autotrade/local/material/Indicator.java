package autotrade.local.material;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Indicator {
    LocalDateTime dateTime;
    String rawDate;
    String rawTime;
    String countryName;
    String indicatorName;
}
