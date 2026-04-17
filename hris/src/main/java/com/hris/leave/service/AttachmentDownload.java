package com.hris.leave.service;

import org.springframework.core.io.InputStreamResource;

public record AttachmentDownload(
    String fileName,
    String mimeType,
    InputStreamResource resource
) {}
