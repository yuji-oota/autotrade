package autotrade.local.actor;

import java.util.Objects;

import autotrade.local.exception.ApplicationException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SameManager {

    private static SameManager instance;
    private int todaysProfitWhenSamed;

    @Getter
    @Setter
    private RecoveryMode recoveryMode;

    enum RecoveryMode {
        NORMAL,
        FORCE,
    }

    @Setter
    @Getter
    private int profitWhenOneSideFixed;

    private SameManager(int todaysProfit) {
        this.todaysProfitWhenSamed = todaysProfit;
        this.recoveryMode = RecoveryMode.NORMAL;
    }

    public static void setProfit(int todaysProfit) {
        if (Objects.isNull(instance)) {
            instance = new SameManager(todaysProfit);
            log.info("same position started. todays profit {}", todaysProfit);
        }
    }
    public static SameManager getInstance() {
        if (Objects.isNull(instance)) {
            throw new ApplicationException("setProfit must be done.");
        }
        return instance;
    }

    public static boolean hasInstance() {
        return Objects.nonNull(instance);
    }

    public boolean isRecovered(int totalProfit) {
        if (totalProfit >= todaysProfitWhenSamed) {
            return true;
        }
        return false;
    }

    public boolean isForce() {
        return recoveryMode == RecoveryMode.FORCE;
    }

    public static void close() {
        instance = null;
    }

}
