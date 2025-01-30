package com.vaadin.kubernetes.demo.views.spreadsheet;

import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.spreadsheet.Spreadsheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.kubernetes.demo.views.MainLayout;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.UnserializableComponentWrapper;

@Component
@Scope("prototype")
@Route(value = "Spreadsheet", layout = MainLayout.class)
@PageTitle("Spreadsheet")
@PermitAll
public class SpreadsheetView extends VerticalLayout {

    public SpreadsheetView() {
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setLocale(Locale.getDefault());
        spreadsheet.setHeight("600px");
        spreadsheet.setWidth("1200px");
        spreadsheet.createCell(1, 0, "Nicolaus");
        spreadsheet.createCell(1, 1, "Copernicus");

        var wrapper = UnserializableComponentWrapper
                .<SpreadsheetState, Spreadsheet>of(spreadsheet)
                .withGenerator(state -> {
                    var ss = new Spreadsheet();
                    ss.setLocale(Locale.getDefault());
                    var excel = state.getExcel();
                    var in = new ByteArrayInputStream(excel);
                    XSSFWorkbook workbook;
                    try {
                        workbook = new XSSFWorkbook(in);
                    } catch (IOException e) {
                        workbook = new XSSFWorkbook();
                    }
                    ss.setWorkbook(workbook);
                    return ss;
                }).withSaver(ss -> {
                    var out = new ByteArrayOutputStream();
                    try {
                        ss.write(out);
                    } catch (IOException e) {
                    }
                    var excel = out.toByteArray();
                    var state = new SpreadsheetState();
                    state.setExcel(excel);
                    return state;
                });
        add(wrapper);
    }
}
