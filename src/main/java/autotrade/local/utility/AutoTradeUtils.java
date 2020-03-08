package autotrade.local.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import javafx.scene.media.AudioClip;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTradeUtils {

    private static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            throw new ApplicationException(e);
        }
    }

    public static int toInt(String numStr) {
        numStr = numStr.replace(",", "").replace(".", "");
        if (numStr.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(numStr);
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new ApplicationException(e);
        }
    }

    public static byte[] serialize(Object object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes); ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new ApplicationException(e);
        }
    }

    public static void printObject(Object object) {
        log.info("{}", object);
    }

    public static void playAudioRandom(AudioPath audioPath) {
        if(!audioPath.getPath().toFile().isDirectory()) {
            playAudio(audioPath.getPath());
        }
        try {
            List<Path> pathList = Files.list(audioPath.getPath()).collect(Collectors.toList());
            playAudio(pathList.get(new Random().nextInt(pathList.size())));
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static void playAudio(Path path) {
        AudioClip audioClip = new AudioClip(path.toUri().toString());
        audioClip.play();
        while (audioClip.isPlaying()) {
            sleep(Duration.ofMillis(100));
        }
    }

}
