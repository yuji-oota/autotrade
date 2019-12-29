package autotrade.local.material;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CurrencyPair {
    USDJPY(2),
    ;

    private int minSpread;
}
