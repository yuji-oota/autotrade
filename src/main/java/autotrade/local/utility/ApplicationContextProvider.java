package autotrade.local.utility;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider implements ApplicationContextAware {
     
    private static ApplicationContext context = null;
 
    public static ApplicationContext getApplicationContext() {
        return context;
    }
 
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        ApplicationContextProvider.context = context;
    }
}
