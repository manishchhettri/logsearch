package com.lsearch.logsearch.model;

import java.util.ArrayList;
import java.util.List;

public class IndexingSummary {
    private int totalFiles;
    private int indexedFiles;
    private int skippedDuplicates;
    private List<String> indexedFileNames = new ArrayList<>();
    private List<String> skippedFileNames = new ArrayList<>();

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getIndexedFiles() {
        return indexedFiles;
    }

    public void setIndexedFiles(int indexedFiles) {
        this.indexedFiles = indexedFiles;
    }

    public int getSkippedDuplicates() {
        return skippedDuplicates;
    }

    public void setSkippedDuplicates(int skippedDuplicates) {
        this.skippedDuplicates = skippedDuplicates;
    }

    public List<String> getIndexedFileNames() {
        return indexedFileNames;
    }

    public void setIndexedFileNames(List<String> indexedFileNames) {
        this.indexedFileNames = indexedFileNames;
    }

    public List<String> getSkippedFileNames() {
        return skippedFileNames;
    }

    public void setSkippedFileNames(List<String> skippedFileNames) {
        this.skippedFileNames = skippedFileNames;
    }

    public void addIndexedFile(String fileName) {
        this.indexedFileNames.add(fileName);
    }

    public void addSkippedFile(String fileName) {
        this.skippedFileNames.add(fileName);
    }
}
