package autotrade.local;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Test {

    public static void main(String[] args) throws IOException, ClassNotFoundException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
        System.out.println(Paths.get("").toAbsolutePath().toString());
        String classpath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        System.out.println("classpath : " + classpath);
        Path path = Paths.get("audio/もうすぐ指標です.wav");
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(path.toFile())) {
            Clip clip = (Clip)AudioSystem.getLine(new DataLine.Info(Clip.class, ais.getFormat()));
            clip.open(ais);
            clip.loop(0);
            clip.flush();
            while(clip.isActive()) {
                Thread.sleep(100);
            }
        }
    }

}
