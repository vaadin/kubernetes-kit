package com.vaadin.azure.starter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * Quick and dirty workaround to make sure that the same Session instance is
 * accessed on every request.
 *
 * This implementation is used to verify if VaadinSession and PUSH can correctly
 * be used with Spring Session infrastructure.
 *
 * Absolutely not production ready.
 */
public class DelegateSessionRepository implements
        FindByIndexNameSessionRepository<DelegateSessionRepository.DelegateSession> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(DelegateSessionRepository.class);

    private final FindByIndexNameSessionRepository delegate;
    private final Map<String, DelegateSession> sessions = new ConcurrentHashMap<>();

    public DelegateSessionRepository(
            FindByIndexNameSessionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public DelegateSession createSession() {
        LOGGER.info("Creating new session");
        DelegateSession session = new DelegateSession(delegate.createSession());
        sessions.put(session.getId(), session);
        LOGGER.info("Session created {}", session.getId());
        return session;
    }

    @Override
    public void save(DelegateSession session) {
        LOGGER.info("Saving session {} ...", session.getId());
        delegate.save(session.remote);
        LOGGER.info("Session {} saved", session.getId());
    }

    @Override
    public DelegateSession findById(String id) {
        LOGGER.info("Searching for session {} ...", id);
        DelegateSession saved = sessions.computeIfAbsent(id, sid -> {
            LOGGER.info("Local session {} not found, fetching remote...", id);
            // try get remote
            Session remote = null;
            try {
                remote = delegate.findById(id);
            } catch (Exception ex) {
                // Remote session cannot be deserialized
                // Ignore the error since there's not much we can do
                // and return a null session that should end up with a
                // session expired scenario
                LOGGER.error("Cannot deserialize session {}. Invalidating", id);
                deleteById(id);
            }
            if (remote != null) {
                LOGGER.info("Loaded remote session {}", id);
                return new DelegateSession(remote);
            } else {
                LOGGER.info("Session {} not found", id);
            }
            return null;
        });
        if (saved != null && saved.isExpired()) {
            LOGGER.info("Session {} expired", id);
            deleteById(saved.getId());
            return null;
        }
        return saved;
    }

    @Override
    public void deleteById(String id) {
        LOGGER.info("Deleting session {} ...", id);
        sessions.remove(id);
        delegate.deleteById(id);
        LOGGER.info("Session {} deleted", id);
    }

    @Override
    public Map<String, DelegateSession> findByIndexNameAndIndexValue(
            String indexName, String indexValue) {
        return wrapMap(
                delegate.findByIndexNameAndIndexValue(indexName, indexValue));
    }

    private Map<String, DelegateSession> wrapMap(Map<String, Session> source) {
        return source.entrySet().stream().map(
                e -> Map.entry(e.getKey(), new DelegateSession(e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue));
    }

    @Override
    public Map<String, DelegateSession> findByPrincipalName(
            String principalName) {
        return wrapMap(delegate.findByPrincipalName(principalName));
    }

    /*
     * Do we really need to wrap session? Maybe we can store original Session
     * instance directly in the `sessions` map
     */
    public static class DelegateSession implements Session {
        private final Session remote;

        public DelegateSession(Session remote) {
            this.remote = remote;
        }

        @Override
        public String getId() {
            return remote.getId();
        }

        @Override
        public String changeSessionId() {
            return remote.changeSessionId();
        }

        @Override
        public <T> T getAttribute(String attributeName) {
            return remote.getAttribute(attributeName);
        }

        @Override
        public <T> T getRequiredAttribute(String name) {
            return remote.getRequiredAttribute(name);
        }

        @Override
        public <T> T getAttributeOrDefault(String name, T defaultValue) {
            return remote.getAttributeOrDefault(name, defaultValue);
        }

        @Override
        public Set<String> getAttributeNames() {
            return remote.getAttributeNames();
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            remote.setAttribute(attributeName, attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            remote.removeAttribute(attributeName);
        }

        @Override
        public Instant getCreationTime() {
            return remote.getCreationTime();
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            remote.setLastAccessedTime(lastAccessedTime);
        }

        @Override
        public Instant getLastAccessedTime() {
            return remote.getLastAccessedTime();
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            remote.setMaxInactiveInterval(interval);
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return remote.getMaxInactiveInterval();
        }

        @Override
        public boolean isExpired() {
            return remote.isExpired();
        }
    }
}
