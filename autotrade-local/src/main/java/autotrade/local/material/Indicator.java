package autotrade.local.material;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Indicator {

    @Getter
    @AllArgsConstructor
    private enum Importance {
        LARGE("★★★"), MIDIUM("★★"), SMALL("★"),
        ;

        private String description;
    }

    private LocalDateTime dateTime;
    private String rawDate;
    private String rawTime;
    private String countryName;
    private String indicatorName;
    private boolean isImportant;

    public void print() {
        Importance importance = isImportant ? Importance.LARGE : Importance.SMALL;
        log.info("{} {} {} {}", dateTime, countryName, importance.getDescription(), indicatorName);
    }
}
