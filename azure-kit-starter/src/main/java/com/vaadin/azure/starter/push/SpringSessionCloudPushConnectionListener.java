package com.vaadin.azure.starter.push;

import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.atmosphere.cpr.AtmosphereResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.ReflectionUtils;

/**
 * Spring Session filter may not be activated when processing PUSH messages that
 * affect changes to the UI. This class tries to get and save the current
 * Session before sending the message to the client so that actual
 * VaadinSesssion changes are not lost on the remote snapshot.
 */
@SuppressWarnings("rawtypes")
public class SpringSessionCloudPushConnectionListener
        extends CloudPushConnectionListener {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(SpringSessionCloudPushConnectionListener.class);

    private final SessionRepository sessionRepository;

    public SpringSessionCloudPushConnectionListener(
            SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void onSend(AtmosphereResource resource, String message) {
        HttpSession httpSession = resource.session(false);
        if (httpSession != null) {
            Session session = getCurrentSession(httpSession);
            if (session != null) {
                sessionRepository.save(session);
            }
        }
    }

    private Session getCurrentSession(HttpSession httpSession) {
        if (httpSession == null) {
            LOGGER.error("No HttpSession for this request");
            return null;
        }

        Session session = null;
        Method sessionGetter = ReflectionUtils
                .findMethod(httpSession.getClass(), "getSession");
        if (sessionGetter != null) {
            sessionGetter.setAccessible(true);
            try {
                session = (Session) sessionGetter.invoke(httpSession);
                LOGGER.debug("Using Spring Session from HttpSession");
            } catch (InvocationTargetException | IllegalAccessException e) {
                LOGGER.debug("Cannot get Spring Session from HttpSession", e);
            }
        } else {
            sessionRepository.findById(httpSession.getId());
            LOGGER.debug("Fetching Spring Session");
        }
        LOGGER.debug("Spring Session {}", session);
        return session;
    }

}
