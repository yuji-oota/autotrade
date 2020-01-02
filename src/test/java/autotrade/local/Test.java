package autotrade.local;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;

public class Test {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Snapshot snapshot = Snapshot.builder()
                .askAverageRate(100)
                .bidAverageRate(90)
                .askLot(160)
                .bidLot(160)
                .askPipProfit(5)
                .bidPipProfit(-50)
                .rate(Rate.builder().ask(110).bid(118).timestamp(LocalDateTime.now()).build())
                .build();
        System.out.println(snapshot);
        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(snapshot);
            bytes = baos.toByteArray();
        }
        Snapshot decerealize;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes); ObjectInputStream ois = new ObjectInputStream(bais)) {
            decerealize = (Snapshot) ois.readObject();
        }
        System.out.println(decerealize);

        String strBytes = Base64.getEncoder().encodeToString(bytes);
        System.out.println(strBytes);
        Snapshot decerealize2;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(strBytes)); ObjectInputStream ois = new ObjectInputStream(bais)) {
            decerealize2 = (Snapshot) ois.readObject();
        }
        System.out.println(decerealize2);
    }


}
