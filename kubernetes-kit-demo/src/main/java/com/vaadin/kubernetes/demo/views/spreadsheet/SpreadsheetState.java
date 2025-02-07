package com.vaadin.kubernetes.demo.views.spreadsheet;

import java.io.Serializable;
import java.util.Locale;

public class SpreadsheetState implements Serializable {

    private byte[] excel;
    private String height;
    private String width;
    private Locale locale;

    public byte[] getExcel() {
        return excel;
    }

    public void setExcel(byte[] excel) {
        this.excel = excel;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }
}
