package org.neointegrations.ftps.internal;


import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.client.MuleFTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents an extension connection just as example (there is no real connection with anything here c:).
 */
public final class FTPSConnection implements AutoCloseable {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSConnection.class);

    private final MuleFTPSClient _client;
    private final FTPSConnectionProvider _provider;

    public FTPSConnection(final FTPSConnectionProvider provider,
                          final MuleFTPSClient client)
                            throws ConnectionException {
        this._provider = provider;
        this._client = client;
    }

    public FTPSConnectionProvider getProvider() {
        return this._provider;
    }

    @Override
    public void close() {
        _provider.disconnect(this);
    }

    public MuleFTPSClient ftpsClient() {
        return this._client;
    }

    public boolean isConnected() {
        return _client.isConnected();
    }

    public boolean isAvailable() throws ConnectionException {
        return _client.isAvailable();
    }

    public void reconnect() {
        try {
            _provider.reconnect(this);
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }
}
