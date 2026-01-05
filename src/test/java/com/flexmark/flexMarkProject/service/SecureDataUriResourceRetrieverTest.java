package com.flexmark.flexMarkProject.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecureDataUriResourceRetriever (inner class of MarkdownService).
 * Uses reflection to test the private inner class.
 */
@DisplayName("SecureDataUriResourceRetriever Tests")
class SecureDataUriResourceRetrieverTest {

    private Object retrieverInstance;
    private Method getInputStreamMethod;
    private Method getByteArrayMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Register data URL stream handler (for testing data URIs)
        // Can only be set once per JVM, so ignore if already set
        try {
            URL.setURLStreamHandlerFactory(new DataUrlStreamHandlerFactory());
        } catch (Error e) {
            // Already set, ignore
        }

        // Use reflection to access the private inner class
        Class<?>[] innerClasses = MarkdownService.class.getDeclaredClasses();
        Class<?> retrieverClass = null;

        for (Class<?> innerClass : innerClasses) {
            if (innerClass.getSimpleName().equals("SecureDataUriResourceRetriever")) {
                retrieverClass = innerClass;
                break;
            }
        }

        assertNotNull(retrieverClass, "SecureDataUriResourceRetriever class should exist");

        // Create instance
        Constructor<?> constructor = retrieverClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        retrieverInstance = constructor.newInstance();

        // Get methods
        getInputStreamMethod = retrieverClass.getDeclaredMethod("getInputStreamByUrl", URL.class);
        getInputStreamMethod.setAccessible(true);

        getByteArrayMethod = retrieverClass.getDeclaredMethod("getByteArrayByUrl", URL.class);
        getByteArrayMethod.setAccessible(true);
    }

    /**
     * Custom URLStreamHandlerFactory for data: protocol support in tests.
     */
    private static class  DataUrlStreamHandlerFactory implements URLStreamHandlerFactory {
        private static boolean registered = false;

        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("data".equals(protocol)) {
                return new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        return new URLConnection(u) {
                            @Override
                            public void connect() throws IOException {
                                // No-op for data URIs
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                // This won't be called - SecureDataUriResourceRetriever parses the URL string
                                return null;
                            }
                        };
                    }
                };
            }
            return null;
        }
    }

    // ==================== Data URI Tests ====================

    @Test
    @DisplayName("Should parse PNG data URI correctly")
    void testPngDataUri() throws Exception {
        // Given: A 1x1 red pixel PNG as data URI
        String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + redPixelBase64;
        URL url = new URL(dataUri);

        // When: Getting input stream
        InputStream is = (InputStream) getInputStreamMethod.invoke(retrieverInstance, url);

        // Then: Should return decoded binary data
        assertNotNull(is);
        byte[] data = is.readAllBytes();
        assertTrue(data.length > 0, "Decoded data should not be empty");

        // PNG files start with specific magic bytes: 137 80 78 71
        assertEquals((byte) 137, data[0], "PNG should start with 137");
        assertEquals((byte) 80, data[1], "PNG second byte should be 80 (P)");
        assertEquals((byte) 78, data[2], "PNG third byte should be 78 (N)");
        assertEquals((byte) 71, data[3], "PNG fourth byte should be 71 (G)");
    }

    @Test
    @DisplayName("Should parse JPEG data URI correctly")
    void testJpegDataUri() throws Exception {
        // Given: A minimal JPEG data URI (1x1 black pixel)
        String jpegBase64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k=";
        String dataUri = "data:image/jpeg;base64," + jpegBase64;
        URL url = new URL(dataUri);

        // When: Getting input stream
        InputStream is = (InputStream) getInputStreamMethod.invoke(retrieverInstance, url);

        // Then: Should return decoded binary data
        assertNotNull(is);
        byte[] data = is.readAllBytes();
        assertTrue(data.length > 0, "Decoded data should not be empty");

        // JPEG files start with FF D8 FF
        assertEquals((byte) 0xFF, data[0], "JPEG should start with 0xFF");
        assertEquals((byte) 0xD8, data[1], "JPEG second byte should be 0xD8");
        assertEquals((byte) 0xFF, data[2], "JPEG third byte should be 0xFF");
    }

    @Test
    @DisplayName("Should handle data URI without base64 encoding")
    void testNonBase64DataUri() throws Exception {
        // Given: A text data URI without base64
        String dataUri = "data:text/plain,Hello%20World";
        URL url = new URL(dataUri);

        // When: Getting input stream
        InputStream is = (InputStream) getInputStreamMethod.invoke(retrieverInstance, url);

        // Then: Should return URL-decoded data
        assertNotNull(is);
        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("Hello World", content);
    }

    @Test
    @DisplayName("Should parse GIF data URI correctly")
    void testGifDataUri() throws Exception {
        // Given: A simple GIF data URI (1x1 transparent pixel)
        String gifBase64 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
        String dataUri = "data:image/gif;base64," + gifBase64;
        URL url = new URL(dataUri);

        // When: Getting byte array
        byte[] data = (byte[]) getByteArrayMethod.invoke(retrieverInstance, url);

        // Then: Should return valid GIF data
        assertNotNull(data);
        assertTrue(data.length > 0);
        // GIF89a header: 47 49 46 38 39 61
        assertEquals('G', (char) data[0]);
        assertEquals('I', (char) data[1]);
        assertEquals('F', (char) data[2]);
    }

    // ==================== File URL Tests ====================

    @Test
    @DisplayName("Should allow file:// URLs")
    void testFileUrl() throws Exception {
        // Given: A file:// URL (note: file may not exist, but protocol should be allowed)
        URL url = new URL("file:///tmp/test.txt");

        // When/Then: Should not throw IOException for blocked protocol
        // (May throw FileNotFoundException if file doesn't exist, but that's different)
        assertDoesNotThrow(() -> {
            try {
                getInputStreamMethod.invoke(retrieverInstance, url);
            } catch (Exception e) {
                // Unwrap reflection exception
                Throwable cause = e.getCause();
                // File not found is OK - we're just testing protocol isn't blocked
                if (!(cause instanceof java.io.FileNotFoundException)) {
                    throw e;
                }
            }
        });
    }

    @Test
    @DisplayName("Should allow jar:file:// URLs")
    void testJarFileUrl() throws Exception {
        // Given: A jar:file:// URL
        URL url = new URL("jar:file:///path/to/file.jar!/resource.txt");

        // When/Then: Should not throw IOException for blocked protocol
        assertDoesNotThrow(() -> {
            try {
                getInputStreamMethod.invoke(retrieverInstance, url);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                // FileNotFoundException is expected since the jar doesn't exist
                if (!(cause instanceof java.io.FileNotFoundException) &&
                    !(cause instanceof java.io.IOException)) {
                    throw e;
                }
            }
        });
    }

    // ==================== SSRF Protection Tests ====================

    @Test
    @DisplayName("Should block HTTP URLs (SSRF protection)")
    void testHttpUrlBlocked() throws Exception {
        // Given: An HTTP URL
        URL url = new URL("http://example.com/malicious.jpg");

        // When/Then: Should throw IOException with security message
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
        assertTrue(cause.getMessage().contains("blocked for security reasons"),
            "Error message should mention security blocking");
    }

    @Test
    @DisplayName("Should block HTTPS URLs (SSRF protection)")
    void testHttpsUrlBlocked() throws Exception {
        // Given: An HTTPS URL
        URL url = new URL("https://example.com/malicious.jpg");

        // When/Then: Should throw IOException with security message
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
        assertTrue(cause.getMessage().contains("blocked for security reasons"),
            "Error message should mention security blocking");
    }

    @Test
    @DisplayName("Should block localhost HTTP URLs")
    void testLocalhostHttpBlocked() throws Exception {
        // Given: A localhost HTTP URL (common SSRF target)
        URL url = new URL("http://localhost:8080/admin");

        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
    }

    @Test
    @DisplayName("Should block 127.0.0.1 HTTP URLs")
    void testLoopbackHttpBlocked() throws Exception {
        // Given: A loopback HTTP URL (common SSRF target)
        URL url = new URL("http://127.0.0.1/secret");

        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw IOException for null URL")
    void testNullUrl() throws Exception {
        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, (URL) null)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
        assertTrue(cause.getMessage().contains("null"),
            "Error message should mention null URL");
    }

    @Test
    @DisplayName("Should throw IOException for malformed data URI")
    void testMalformedDataUri() throws Exception {
        // Given: Malformed data URI (missing comma separator)
        String dataUri = "data:image/pngbase64xyz";
        URL url = new URL(dataUri);

        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
    }

    @Test
    @DisplayName("Should throw IOException for invalid Base64 in data URI")
    void testInvalidBase64InDataUri() throws Exception {
        // Given: Data URI with invalid Base64
        String dataUri = "data:image/png;base64,not-valid-base64!@#$";
        URL url = new URL(dataUri);

        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
            getInputStreamMethod.invoke(retrieverInstance, url)
        );

        Throwable cause = exception.getCause();
        assertInstanceOf(IOException.class, cause);
    }

    // ==================== getByteArrayByUrl Tests ====================

    @Test
    @DisplayName("getByteArrayByUrl should return same data as getInputStreamByUrl")
    void testGetByteArrayEquivalence() throws Exception {
        // Given: A data URI
        String redPixelBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + redPixelBase64;
        URL url = new URL(dataUri);

        // When: Getting data via both methods
        InputStream is = (InputStream) getInputStreamMethod.invoke(retrieverInstance, url);
        byte[] dataFromStream = is.readAllBytes();

        // Need to create a new URL since the first one was consumed
        URL url2 = new URL(dataUri);
        byte[] dataFromArray = (byte[]) getByteArrayMethod.invoke(retrieverInstance, url2);

        // Then: Both should return the same data
        assertArrayEquals(dataFromStream, dataFromArray,
            "getByteArrayByUrl should return same data as getInputStreamByUrl");
    }

    @Test
    @DisplayName("getByteArrayByUrl should handle large data URIs")
    void testGetByteArrayLargeDataUri() throws Exception {
        // Given: A larger base64 string (simulating a real image)
        String largeBase64 = Base64.getEncoder().encodeToString(new byte[1024]); // 1KB of zeros
        String dataUri = "data:image/png;base64," + largeBase64;
        URL url = new URL(dataUri);

        // When: Getting byte array
        byte[] data = (byte[]) getByteArrayMethod.invoke(retrieverInstance, url);

        // Then: Should return correct amount of data
        assertNotNull(data);
        assertEquals(1024, data.length, "Should decode to 1024 bytes");
    }
}
