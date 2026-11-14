package com.kodika.kodikalab.competitions.problemresolution.dto;

/** Archivo de código fuente ya validado que el practicante adjunta a su envío. */
public record SubmittedFile(String fileName, String sourceCode, String sha256) {
}
