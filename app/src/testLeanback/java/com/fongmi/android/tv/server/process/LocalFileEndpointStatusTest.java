package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.github.catvod.Init;
import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LocalFileEndpointStatusTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();
    private final Local local = new Local();

    @Before
    public void setUp() throws Exception {
        Init.set(RuntimeEnvironment.getApplication());
        Path.setRootOverride(files.newFolder("root"));
    }

    @After
    public void tearDown() {
        Path.setRootOverride(null);
    }

    @Test
    public void missingLocalFileReportsNotFoundInsteadOfServerError() throws Exception {
        Response response = serve("/file/missing.srt");

        assertEquals(Response.Status.NOT_FOUND, response.getStatus());
    }

    @Test
    public void existingLocalFileStillServesItsContent() throws Exception {
        File file = new File(Path.root(), "present.srt");
        Files.write(file.toPath(), "subtitle".getBytes(StandardCharsets.UTF_8));

        Response response = serve("/file/present.srt");

        assertEquals(Response.Status.OK, response.getStatus());
        assertEquals("subtitle", read(response));
    }

    @Test
    public void directoryStillReturnsItsListing() throws Exception {
        File folder = new File(Path.root(), "folder");
        assertTrue(folder.mkdirs());

        Response response = serve("/file/folder");

        assertEquals(Response.Status.OK, response.getStatus());
        assertNotNull(read(response));
    }

    private Response serve(String url) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> parms = new HashMap<>();
        IHTTPSession session = (IHTTPSession) Proxy.newProxyInstance(
                IHTTPSession.class.getClassLoader(), new Class<?>[]{IHTTPSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeaders")) return headers;
                    if (method.getName().equals("getParms")) return parms;
                    throw new AssertionError("Unexpected session call: " + method.getName());
                });
        return local.doResponse(session, url, Collections.emptyMap());
    }

    private static String read(Response response) throws Exception {
        if (response.getData() == null) return "";
        try (InputStream input = response.getData()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
