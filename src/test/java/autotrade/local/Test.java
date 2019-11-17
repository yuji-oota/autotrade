package autotrade.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Test {

    public static void main(String[] args) {

        Path logFile = Paths.get("log", "autotrade-local.log");
        try {
            System.out.println(ChronoUnit.MINUTES.between(Files.getLastModifiedTime(logFile).toInstant(), LocalDateTime.now()));
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }

    }

}
