package autotrade.local.material;

import org.junit.jupiter.api.Test;

class RateTest {

    @Test
    void test() {
        System.out.println(Rate.builder().rawAsk("123.856").rawBid("123.556").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.956").rawBid("123.556").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.056").rawBid("123.556").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.156").rawBid("123.556").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.856").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.956").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.056").build().isNearDecimalPartZero());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.156").build().isNearDecimalPartZero());
    }

}
