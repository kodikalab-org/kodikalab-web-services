package com.kodika.kodikalab.assignments.dto;

/** Archivo adjunto tal como llega por HTTP, aún sin validar. */
public record SubmissionFile(String fileName, byte[] content) {
}
