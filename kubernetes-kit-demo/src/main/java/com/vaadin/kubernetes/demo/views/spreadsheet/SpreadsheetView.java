package com.vaadin.kubernetes.demo.views.spreadsheet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;

import org.apache.poi.ss.util.CellRangeAddress;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.spreadsheet.Spreadsheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.kubernetes.demo.views.MainLayout;
import com.vaadin.kubernetes.starter.sessiontracker.UnserializableComponentWrapper;

@PageTitle("Spreadsheet")
@Route(value = "spreadsheet", layout = MainLayout.class)
@AnonymousAllowed
public class SpreadsheetView extends VerticalLayout {

    public SpreadsheetView() {
        setSizeFull();
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.createCell(1, 0, "Nicolaus");
        spreadsheet.createCell(1, 1, "Copernicus");
        configureSpreadsheet(spreadsheet);
        var wrapper = new UnserializableComponentWrapper<>(spreadsheet,
                SpreadsheetView::serializer, SpreadsheetView::deserializer);
        addAndExpand(wrapper);
    }

    private static WorkbookData serializer(Spreadsheet sheet) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            sheet.write(baos);
            String selection = Optional
                    .ofNullable(sheet.getCellSelectionManager()
                            .getSelectedCellRange())
                    .map(CellRangeAddress::formatAsString).orElse(null);
            return new WorkbookData(baos.toByteArray(), selection);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Spreadsheet deserializer(WorkbookData data) {
        try {
            Spreadsheet sheet = new Spreadsheet(
                    new ByteArrayInputStream(data.data()));
            if (data.selection() != null) {
                sheet.setSelection(data.selection());
            }
            configureSpreadsheet(sheet);
            return sheet;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void configureSpreadsheet(Spreadsheet sheet) {
        sheet.setLocale(Locale.getDefault());
        sheet.setWidth("800px");
    }

    private record WorkbookData(byte[] data,
            String selection) implements Serializable {
    }
}
