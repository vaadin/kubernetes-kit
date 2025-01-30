package com.vaadin.kubernetes.demo.views.spreadsheet;

import java.io.Serializable;

public class SpreadsheetState implements Serializable {

    private byte[] excel;

    public byte[] getExcel() {
        return excel;
    }

    public void setExcel(byte[] excel) {
        this.excel = excel;
    }
}
