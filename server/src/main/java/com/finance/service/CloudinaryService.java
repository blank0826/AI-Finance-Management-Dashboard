package com.finance.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    @Value("${app.cloudinary.enabled:false}")
    private boolean enabled;

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    /**
     * Uploads file bytes to Cloudinary under finance-statements/{userId}/
     * Returns the public_id or null if Cloudinary is disabled.
     */
    public String uploadBytes(byte[] fileBytes, String originalFilename, Long userId)
            throws IOException {
        if (!enabled || fileBytes == null || fileBytes.length == 0) return null;

        String folder = "finance-statements/" + userId;

        Map<?, ?> result = cloudinary.uploader().upload(
            fileBytes,
            ObjectUtils.asMap(
                "folder",            folder,
                "resource_type",     "raw",
                "use_filename",      true,
                "unique_filename",   true,
                "overwrite",         false,
                "public_id",         sanitizeFilename(originalFilename)
            )
        );

        return (String) result.get("public_id");
    }

    /**
     * Returns a signed secure download URL valid for 1 hour.
     * Returns null if Cloudinary is disabled or publicId is null.
     */
    public String getSecureUrl(String publicId) {
        if (!enabled || publicId == null || publicId.isBlank()) return null;
        try {
            return cloudinary.url()
                .resourceType("raw")
                .signed(true)
                .generate(publicId);
        } catch (Exception e) {
            log.error("Cloudinary URL generation failed", e);
            return null;
        }
    }

    /**
     * Deletes a single file from Cloudinary.
     * Called when a user deletes an upload.
     */
    public void deleteFile(String publicId) {
        if (!enabled || publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", "raw")
            );
        } catch (Exception e) {
            log.error("Cloudinary delete failed for {}", publicId, e);
        }
    }

    /**
     * Deletes all files for a user folder.
     * Called on account deletion.
     */
    public void deleteUserFolder(Long userId) {
        if (!enabled) return;
        try {
            cloudinary.api().deleteResourcesByPrefix(
                "finance-statements/" + userId + "/",
                ObjectUtils.emptyMap()
            );
        } catch (Exception e) {
            log.error("Cloudinary folder delete failed for user {}", userId, e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sanitizes filename for use as Cloudinary public_id.
     * Removes special characters that Cloudinary doesn't allow.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "statement_" + System.currentTimeMillis();
        }
        // Remove extension and replace special chars with underscore
        String name = filename.replaceAll("\\.[^.]+$", "");
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}