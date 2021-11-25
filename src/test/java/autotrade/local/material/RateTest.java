package autotrade.local.material;

import org.junit.jupiter.api.Test;

class RateTest {

    @Test
    void test() {
        System.out.println("1234567".substring("1234567".length() - 3));
        System.out.println(Rate.builder().rawAsk("123.856").rawBid("123.556").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.956").rawBid("123.556").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.056").rawBid("123.556").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.156").rawBid("123.556").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.856").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.956").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.056").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.556").rawBid("123.156").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.5861").rawBid("123.1561").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.5961").rawBid("123.1561").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.5561").rawBid("123.1061").build().isNearThousand());
        System.out.println(Rate.builder().rawAsk("123.5561").rawBid("123.1161").build().isNearThousand());
    }

}
