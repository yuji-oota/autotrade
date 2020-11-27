package autotrade.local;


import java.io.IOException;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Test {

    private enum OrderPoint {
        THRESHOLD, AVERAGE;
        private static OrderPoint prev = THRESHOLD;
        public OrderPoint next() {
            // THRESHOLD,THRESHOLD,AVERAGE,THRESHOLD,THRESHOLD,AVERAGEの順番
            if (prev == THRESHOLD
                    && prev == this) {
                prev = this;
                return AVERAGE;
            }
            prev = this;
            return THRESHOLD;
        }
    }
    public static void main(String[] args) throws IOException, ClassNotFoundException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
//        System.out.println(Paths.get("").toAbsolutePath().toString());
//        String classpath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
//        System.out.println("classpath : " + classpath);
//        Path path = Paths.get("audio/もうすぐ指標です.wav");
//        try (AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile())) {
//            Clip clip = (Clip)AudioSystem.getLine(new DataLine.Info(Clip.class, ais.getFormat()));
//            clip.open(ais);
//            clip.loop(0);
//            clip.flush();
//            while(clip.isActive()) {
//                Thread.sleep(100);
//            }
//        }

        OrderPoint orderPoint = OrderPoint.AVERAGE;
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
        orderPoint = orderPoint.next();
        System.out.println(orderPoint);
    }

}
