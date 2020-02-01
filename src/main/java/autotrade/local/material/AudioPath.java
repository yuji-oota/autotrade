package autotrade.local.material;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AudioPath {

    Alert(Paths.get("audio/alert")),
    FixSoundEffect(Paths.get("audio/fixsoundeffect")),
    OrderSoundEffect(Paths.get("audio/ordersoundeffect")),
    ;

    private Path path;
}
