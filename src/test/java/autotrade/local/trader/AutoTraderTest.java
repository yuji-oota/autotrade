package autotrade.local.trader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderTest {

    private AutoTrader autoTrader;

    @Before
    public void before() {
        autoTrader = AutoTrader.getInstance();
    }

    @Test
    public void test() {
        Method isInactiveTime = getPrivateMethod(autoTrader, "isInactiveTime");
        boolean result = invokePrivateMethod(isInactiveTime, autoTrader);
        log.info("{}", result);
    }


    @SuppressWarnings("unused")
    private Method getPrivateMethod(Object object, String methodName, @SuppressWarnings("rawtypes") Class... clazz) {
        Method method;
        try {
            method = object.getClass().getDeclaredMethod(methodName, clazz);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        method.setAccessible(true);
        return method;
    }

    private <T> T invokePrivateMethod(Method method, Object object, Object... args) {
        try {
            return (T) method.invoke(object, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
