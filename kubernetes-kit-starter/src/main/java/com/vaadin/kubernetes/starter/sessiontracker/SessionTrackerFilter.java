package com.vaadin.kubernetes.starter.sessiontracker;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.server.HandlerHelper.RequestType;
import com.vaadin.flow.shared.ApplicationConstants;

public class SessionTrackerFilter extends HttpFilter {

    private final transient SessionSerializer sessionSerializer;

    public SessionTrackerFilter(SessionSerializer sessionSerializer) {
        this.sessionSerializer = sessionSerializer;
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        SessionTrackerCookie.getValue(request).ifPresent(key -> {
            CurrentKey.set(key);
            if (request.getSession(false) == null) {
                // Cluster key set but no session, create one so it can be
                // imported if neewed
                getLogger().info("Creating session for cluster key {}", key);
                request.getSession(true);
            }
        });
        try {
            HttpSession session = request.getSession(false);

            if (session != null) {
                SessionTrackerCookie.setIfNeeded(session, request, response);
            }
            super.doFilter(request, response, chain);

            if (session != null && RequestType.UIDL.getIdentifier()
                    .equals(request.getParameter(
                            ApplicationConstants.REQUEST_TYPE_PARAMETER))) {
                sessionSerializer.serialize(session);
            }
        } finally {
            CurrentKey.clear();
        }
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }
}
