package autotrade.local.material;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AudioPath {

    IndicatorAlert(Paths.get("audio/もうすぐ指標です.wav")),
    FixProfit(Paths.get("audio/コイン01.wav")),
    ;

    private Path path;
}
