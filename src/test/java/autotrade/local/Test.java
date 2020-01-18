package autotrade.local;


import java.io.IOException;
import java.util.Arrays;

public class Test {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String[] array = {"aaa"};
        System.out.println(Arrays.toString(Arrays.copyOfRange(array, 1, array.length)));
        String[] array2 = {"aaa", "bbb"};
        System.out.println(Arrays.toString(Arrays.copyOfRange(array2, 1, array2.length)));
        String[] array3 = {"aaa", "bbb", "ccc"};
        System.out.println(Arrays.toString(Arrays.copyOfRange(array3, 1, array3.length)));
    }


}
