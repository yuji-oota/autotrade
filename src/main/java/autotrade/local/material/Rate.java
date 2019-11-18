package autotrade.local.material;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Rate {
    private int ask;
    private int bid;
    private LocalDateTime timestamp;
}
