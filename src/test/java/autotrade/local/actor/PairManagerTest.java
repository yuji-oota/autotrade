package autotrade.local.actor;

import java.time.LocalTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import autotrade.local.material.Pair;

@ActiveProfiles("test")
@SpringBootTest
class PairManagerTest {

    @Autowired
    PairManager pairManager;

    @Test
    void test() {
        pairManager.getPairs().forEach(System.out::println);
        System.out.println(pairManager.get("EURUSD").isHandleable(LocalTime.now()));

        System.out.println(pairManager.getPairs().stream()
                .filter(pair -> pair.getName().equals("aaa"))
                .map(pair -> {
                    return new SimpleEntry<Pair, Integer>(
                            pair, 100);
                })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(SimpleEntry::getKey)
                .orElseGet(pairManager::getDefault));
    }

}
