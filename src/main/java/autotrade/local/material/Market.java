package autotrade.local.material;

import org.joda.time.LocalTime;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum Market {
    TOKYO(LocalTime.parse("08:00"), LocalTime.parse("15:00")),
    LONDON(LocalTime.parse("15:00"), LocalTime.parse("02:00")),
    OTHER(LocalTime.parse("02:00"), LocalTime.parse("08:00")),
    ;
    
    private LocalTime start;
    private LocalTime end;
    
    public static Market now() {
        LocalTime now = LocalTime.now();
        if (TOKYO.start.isBefore(now) && now.isBefore(TOKYO.end)) {
            return TOKYO;
        }
        if (LONDON.start.isBefore(now) || now.isBefore(LONDON.end)) {
            return LONDON;
        }
        return OTHER;
    }
}
