package de.brentspine;

public enum Files {

    ENGLISH("data/english.txt");

    private final String fileName;

    Files(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

}
