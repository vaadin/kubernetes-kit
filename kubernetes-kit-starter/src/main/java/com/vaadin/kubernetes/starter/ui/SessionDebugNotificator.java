package com.vaadin.kubernetes.starter.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SerializationDebugRequestHandler;

import elemental.json.Json;
import elemental.json.JsonObject;

@Tag("vaadin-session-debug-notificator")
@JsModule("./components/session-debug-notificator.ts")
public class SessionDebugNotificator extends Component {

    public void publishResults(SerializationDebugRequestHandler.Result result) {
        // Add serialization result to show notifications on browser
        JsonObject propValue = Json.createObject();
        // just to make sure property is always detected as changed since when
        // closing notification value is nullified only on client side
        propValue.put("timestamp", System.nanoTime());
        if (result.getOutcomes()
                .contains(SerializationDebugRequestHandler.Outcome.SUCCESS)) {
            propValue.put("success", true);
        } else {
            propValue.put("success", false);
            propValue.put("message", result.getOutcomes().toString());
        }
        propValue.put("duration", result.getDuration());
        getElement().setPropertyJson("outcome", propValue);
    }

}
