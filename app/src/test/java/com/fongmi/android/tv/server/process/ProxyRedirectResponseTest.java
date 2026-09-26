package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;

public class ProxyRedirectResponseTest {

    @Test
    public void redirectMayHaveAnEmptyBody() throws Exception {
        assertTrue(canResponseHaveEmptyBody(301));
        assertTrue(canResponseHaveEmptyBody(302));
        assertTrue(canResponseHaveEmptyBody(307));
    }

    @Test
    public void nonRedirectStillRequiresAStreamBody() throws Exception {
        assertFalse(canResponseHaveEmptyBody(200));
        assertFalse(canResponseHaveEmptyBody(204));
        assertFalse(canResponseHaveEmptyBody(400));
    }

    private static boolean canResponseHaveEmptyBody(int code) throws Exception {
        Method method = Proxy.class.getDeclaredMethod("canResponseHaveEmptyBody", int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, code);
    }
}
