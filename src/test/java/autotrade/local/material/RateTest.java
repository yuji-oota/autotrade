package autotrade.local.material;

import org.junit.jupiter.api.Test;

class RateTest {

    @Test
    void test() {
        System.out.println(Rate.builder().rawAsk("123.001").rawBid("123.555").build().anyMatch(".*0..", ".*9.."));
        System.out.println(Rate.builder().rawAsk("123.101").rawBid("123.555").build().anyMatch(".*0..", ".*9.."));
        System.out.println(Rate.builder().rawAsk("123.501").rawBid("123.055").build().anyMatch(".*0..", ".*9.."));
        System.out.println(Rate.builder().rawAsk("123.501").rawBid("123.955").build().anyMatch(".*0..", ".*9.."));
        System.out.println(Rate.builder().rawAsk("123.001").rawBid("123.555").build().anyMatch(".*00.", ".*99."));
        System.out.println(Rate.builder().rawAsk("123.991").rawBid("123.555").build().anyMatch(".*00.", ".*99."));
        System.out.println(Rate.builder().rawAsk("123.501").rawBid("123.005").build().anyMatch(".*00.", ".*99."));
        System.out.println(Rate.builder().rawAsk("123.501").rawBid("123.995").build().anyMatch(".*00.", ".*99."));
    }

}
