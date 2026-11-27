package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LocalFileEndpointBoundaryTest {

    @Rule public TemporaryFolder files = new TemporaryFolder();
    private final Local local = new Local();
    private File root;
    private File outside;

    @Before
    public void setUp() throws Exception {
        Init.set(RuntimeEnvironment.getApplication());
        root = files.newFolder("shared");
        outside = files.newFolder("outside");
        Path.setRootOverride(root);
    }

    @After
    public void tearDown() {
        Path.setRootOverride(null);
    }

    @Test
    public void deletingWithEmptyPathKeepsTheSharedRootAndItsFiles() throws Exception {
        File kept = new File(root, "kept.srt");
        Files.write(kept.toPath(), "keep".getBytes(StandardCharsets.UTF_8));

        local.doResponse(session(Map.of("path", "")), "/delFile", files(Collections.emptyMap()));

        assertTrue("shared root must survive an empty delete path", root.isDirectory());
        assertTrue("existing file must survive", kept.isFile());
    }

    @Test
    public void deletingWithoutAnyPathParameterDoesNotCrashOrDeleteTheRoot() throws Exception {
        File kept = new File(root, "kept.srt");
        Files.write(kept.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        Map<String, String> parms = new HashMap<>();

        local.doResponse(session(parms), "/delFile", files(Collections.emptyMap()));

        assertTrue("a missing path must not delete the shared root", kept.isFile());
        assertTrue(root.isDirectory());
    }

    @Test
    public void deletingWithParentTraversalLeavesFilesOutsideTheRoot() throws Exception {
        File victim = new File(outside, "victim.srt");
        Files.write(victim.toPath(), "outside".getBytes(StandardCharsets.UTF_8));

        local.doResponse(session(Map.of("path", "../outside")), "/delFolder", files(Collections.emptyMap()));

        assertTrue("a parent path must not delete outside the shared root", victim.isFile());
    }

    @Test
    public void uploadingWithTraversalFilenameCannotEscapeTheRoot() throws Exception {
        File source = files.newFile("payload.srt");
        Files.write(source.toPath(), "payload".getBytes(StandardCharsets.UTF_8));

        local.doResponse(session(Map.of("upload", "../escaped.srt", "path", "")), "/upload",
                files(Map.of("upload", source.getAbsolutePath())));

        assertFalse("upload must not write outside the shared root", new File(outside, "escaped.srt").exists());
        assertFalse("upload must not write next to the shared root", new File(files.getRoot(), "escaped.srt").exists());
    }

    @Test
    public void creatingFolderOutsideTheRootIsRefused() throws Exception {
        local.doResponse(session(Map.of("path", "../outside", "name", "made")), "/newFolder", files(Collections.emptyMap()));

        assertFalse("folder creation must stay inside the shared root", new File(outside, "made").exists());
    }

    @Test
    public void normalSubfolderOperationsStillWork() throws Exception {
        File folder = new File(root, "shows");
        assertTrue(folder.mkdirs());
        File nested = new File(folder, "old.srt");
        Files.write(nested.toPath(), "x".getBytes(StandardCharsets.UTF_8));

        local.doResponse(session(Map.of("path", "shows")), "/delFolder", files(Collections.emptyMap()));
        assertFalse("a real subfolder is still deletable", folder.exists());
        assertTrue("shared root survives deleting a subfolder", root.isDirectory());

        local.doResponse(session(Map.of("path", "shows", "name", "created")), "/newFolder", files(Collections.emptyMap()));
        assertTrue("creating a real subfolder still works", new File(root, "shows/created").isDirectory());

        File source = files.newFile("upload.srt");
        Files.write(source.toPath(), "ok".getBytes(StandardCharsets.UTF_8));
        local.doResponse(session(Map.of("upload", "uploaded.srt", "path", "shows")), "/upload", files(Map.of("upload", source.getAbsolutePath())));
        assertEquals("ok", Files.readString(new File(root, "shows/uploaded.srt").toPath()));
    }

    private static IHTTPSession session(Map<String, String> parms) {
        Map<String, String> headers = new HashMap<>();
        return (IHTTPSession) Proxy.newProxyInstance(
                IHTTPSession.class.getClassLoader(), new Class<?>[]{IHTTPSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeaders")) return headers;
                    if (method.getName().equals("getParms")) return parms;
                    throw new AssertionError("Unexpected session call: " + method.getName());
                });
    }

    private static Map<String, String> files(Map<String, String> values) {
        return values;
    }
}
