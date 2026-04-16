package com.finance.service;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;

class CloudinaryServiceTest {

    @Test
    void sanitizeFilename_and_disabledBehavior() throws Exception {
        CloudinaryService svc = new CloudinaryService();
        // disabled by default -> uploadBytes should return null
        byte[] data = "abc".getBytes();
        assertNull(svc.uploadBytes(data, "file.pdf", 1L));

        // Test sanitizeFilename via reflection
        String clean = (String) ReflectionTestUtils.invokeMethod(svc, "sanitizeFilename", "my file(1).pdf");
        assertFalse(clean.contains(" "));
        assertFalse(clean.contains("("));
    }

    @Test
    void uploadAndUrl_whenEnabled() throws IOException {
        Cloudinary cloud = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        Api api = mock(Api.class);
        Url url = mock(Url.class);

        when(cloud.uploader()).thenReturn(uploader);
        when(cloud.api()).thenReturn(api);
        when(cloud.url()).thenReturn(url);

        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("public_id", "finance-statements/1/file123"));
        when(url.resourceType(anyString())).thenReturn(url);
        when(url.signed(anyBoolean())).thenReturn(url);
        when(url.generate("finance-statements/1/file123")).thenReturn("https://res.cloudinary.com/signed/url");

        CloudinaryService svc = new CloudinaryService();
        ReflectionTestUtils.setField(svc, "cloudinary", cloud);
        ReflectionTestUtils.setField(svc, "enabled", true);

        String pid = svc.uploadBytes("data".getBytes(), "file.pdf", 1L);
        assertNotNull(pid);

        String urlStr = svc.getSecureUrl(pid);
        assertEquals("https://res.cloudinary.com/signed/url", urlStr);
    }
}
